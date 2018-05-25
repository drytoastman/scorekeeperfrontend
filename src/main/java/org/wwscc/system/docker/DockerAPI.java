/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system.docker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarInputStream;
import org.kamranzafar.jtar.TarOutputStream;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.wwscc.system.docker.SocketFactories.UnixSocketFactory;
import org.wwscc.system.docker.SocketFactories.WindowsPipeFactory;
import org.wwscc.system.docker.models.ContainerSummaryInner;
import org.wwscc.system.docker.models.ErrorResponse;
import org.wwscc.system.docker.models.ExecConfig;
import org.wwscc.system.docker.models.ExecStatus;
import org.wwscc.system.docker.models.ImageSummary;
import org.wwscc.system.docker.models.Network;
import org.wwscc.system.docker.models.NetworkContainer;
import org.wwscc.system.docker.models.Volume;
import org.wwscc.system.docker.models.VolumesResponse;
import org.wwscc.util.AppSetup;
import org.wwscc.util.Prefs;

/*
 * Docker API for local connections.
 */
public class DockerAPI
{
    private static final Logger log = Logger.getLogger(DockerAPI.class.getCanonicalName());
    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(Include.NON_NULL);

    HttpClientBuilder builder;
    CloseableHttpClient client;
    HttpHost host;
    ExecutorService executor;
    Map<String, String> lastenv;

    public class DockerDownException extends IOException {
        public DockerDownException() {
            super("Docker is down");
        }
    }

    static class MutableCounter
    {
        private int value;
        public MutableCounter() { value = 0; }
        public int inc() { value += 1; return value; }
    }

    public interface DockerStatusListener {
        public void status(int tasks, int completed);
    }

    /**
     * Perform setup or re/setup with a new connection
     * @param env the current known environment variables
     */
    public void setup(Map<String, String> env)
    {
        try {

            if (executor != null) {
                executor.shutdown();
                executor = null;
            }

            if (client != null) {
                try {
                    client.close();
                } catch (IOException ioe) {}
                client = null;
            }

            if (env == null) { // expect at least a blank map, null is something wrong so we bail here
                return;
            }

            PoolingHttpClientConnectionManager pool;
            if (env.containsKey("DOCKER_HOST") && env.containsKey("DOCKER_CERT_PATH"))
            {
                SSLContext sslctx = DockerCertificates.createContext(Paths.get(env.get("DOCKER_CERT_PATH")));
                SSLConnectionSocketFactory sslf = new SSLConnectionSocketFactory(sslctx, NoopHostnameVerifier.INSTANCE);
                pool = new PoolingHttpClientConnectionManager(
                        RegistryBuilder.<ConnectionSocketFactory>create().register("http", sslf).build());
                pool.setDefaultMaxPerRoute(5);

                URI uri = new URI(env.get("DOCKER_HOST"));
                host = new HttpHost(uri.getHost(), uri.getPort());
            }
            else if (Prefs.isWindows())
            {
                pool = new PoolingHttpClientConnectionManager(
                    RegistryBuilder.<ConnectionSocketFactory>create().register("http", new WindowsPipeFactory("\\\\.\\pipe\\docker_engine")).build());
                pool.setDefaultMaxPerRoute(1);  // can't open windows pipe more than once

                host = new HttpHost("127.0.0.1");
                pool.setSocketConfig(host, SocketConfig.custom().setSoTimeout(-1).build());  // can't set timeout on filechannel
                pool.setValidateAfterInactivity(-1); // don't validate for same reason
            }
            else
            {
                pool = new PoolingHttpClientConnectionManager(
                    RegistryBuilder.<ConnectionSocketFactory>create().register("http", new UnixSocketFactory("/var/run/docker.sock")).build());
                pool.setDefaultMaxPerRoute(5);
                host = new HttpHost("127.0.0.1");
            }

            lastenv  = env;
            executor = Executors.newFixedThreadPool(5, (r) -> { Thread t = new Thread(r); t.setDaemon(true); return t; });
            builder  = HttpClients.custom().setConnectionManager(pool);
            client   = builder.build();
        } catch (Exception e) {
            log.warning("Failed to setup docker API, will try again later: " + e);
        }
    }


    public boolean isReady()
    {
        return client != null;
    }


    public void dump(Writer w)
    {
        try {
            w.write("\n=== Docker Env ===\n");
            for (Map.Entry<?,?> entry : lastenv.entrySet()) {
                w.write(String.format("%20s = %s\n", entry.getKey(), entry.getValue()));
            }
            w.write("\n=== Docker Version ===\n");
            Map<?,?> m = request(new Requests.Version());
            for (Map.Entry<?,?> entry : m.entrySet()) {
                if (!(entry.getValue() instanceof List))
                    w.write(String.format("%20s = %s\n", entry.getKey(), entry.getValue()));
            }

            w.write("\n=== Docker images ===\n");
            for (ImageSummary i : request(new Requests.GetImages())) {
                if (i.getRepoTags() == null) continue;
                w.write(String.format("%28s %s\n", i.getRepoTags(), i.getId()));
            }

            w.write("\n=== Docker containers ===\n");
            for (ContainerSummaryInner s : request(new Requests.GetContainers())) {
                w.write(s.toString());
                w.write("\n");
            }

            w.write("\n=== Docker network ===\n");
            for (Network n : request(new Requests.GetNetworks())) {
                w.write(n.toString());
                w.write("\n");
            }

        } catch (IOException ioe) {
            log.warning("Failed to write status: " + ioe);
        }
    }


    public void loadState(Collection<DockerContainer> search) throws IOException
    {
        for (DockerContainer c : search) {
            c.setState(null);
        }

        for (ContainerSummaryInner info : request(new Requests.GetContainers()))
        {
            for (DockerContainer c : search) {
                if (info.getNames().contains("/"+c.getName())) {
                    log.finer(c.getName() + " state = " + info.getState());
                    c.setState(info.getState());
                }
            }
        }
    }


    public boolean networkUp(String name)
    {
        try {
            Network net = null;
            try { net = request(new Requests.GetNetwork(name)); } catch (IOException ioe) {}

            if (net != null) { // found a network matching the name
                for (NetworkContainer nc : net.getContainers().values())
                    request(new Requests.DisconnectContainer(name, nc.getName()));
                request(new Requests.DeleteNetwork(name));
            }

            request(new Requests.CreateNetwork(name));
            return true;
        } catch (IOException ioe) {
            log.warning("Failed to bring up network: " + ioe);
            return false;
        }
    }


    public boolean containersUp(Collection<DockerContainer> containers, DockerStatusListener listener)
    {
        try
        {
            ImageSummary[] images = request(new Requests.GetImages());
            if (images == null)
                images = new ImageSummary[0];
            VolumesResponse volumes = request(new Requests.GetVolumes());
            if (volumes.getVolumes() == null)
                volumes.setVolumes(new ArrayList<Volume>());

            loadState(containers);

            // Build our list of requests that need to be made
            List<List<Requests.Wrapper<Void>>> chains = new ArrayList<List<Requests.Wrapper<Void>>>();
            for (DockerContainer container : containers)
            {
                if (container.isUp())
                    continue;

                List<Requests.Wrapper<Void>> inner = new ArrayList<Requests.Wrapper<Void>>();
                chains.add(inner);

                if (Prefs.isDebug())
                    container.addEnvItem("DEBUG=1");
                else
                    container.getEnv().remove("DEBUG=1");

                // Pull image if necessary
                boolean needpull = true;
                for (ImageSummary s :images) {
                    if (s.getRepoTags() != null && s.getRepoTags().contains(container.getImage())) {
                        needpull = false;
                    }
                }
                if (needpull) {
                    log.info("Pulling " + container.getImage());
                    inner.add(new Requests.PullImage(container.getImage()));
                }

                // Create any volumes
                for (String mount : container.getHostConfig().getBinds()) {
                    String cvol = mount.split(":")[0];
                    boolean needvol = true;
                    for (Volume v : volumes.getVolumes()) {
                        if (cvol.equals(v.getName())) {
                            needvol = false;
                        }
                    }
                    if (needvol)
                        inner.add(new Requests.CreateVolume(cvol));
                }

                // Create the container
                if (!container.isCreated())
                    inner.add(new Requests.CreateContainer(container));

                // Then start it
                inner.add(new Requests.Start(container.getName()));
            }

            List<Exception> ret = parallelChains(chains, listener);
            if (ret.size() > 0) {
                log.warning("containerup errors: ");
                for (Exception e : ret) {
                    log.warning(e.getMessage());
                }
                return false;
            }

            return true;
        }
        catch (Exception e)
        {
            log.log(Level.WARNING, "Unable to bring up containers: " + e, e);
            return false;
        }
    }


    public void downloadTo(String name, String containerpath, Path hostdir) throws IOException
    {
        try (TarInputStream in = new TarInputStream(request(new Requests.Download(name, containerpath)))) {
            TarEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory())
                    continue;
                File dest = hostdir.resolve(Paths.get(entry.getName()).getFileName()).toFile();
                try (FileOutputStream dout = new FileOutputStream(dest)) {
                    IOUtils.copy(in, dout);
                }
                dest.setLastModified(entry.getModTime().getTime());
            }
        }
    }


    public void uploadFile(String name, File file, String containerpath) throws IOException
    {
        ContentProducer producer = new ContentProducer() {
            @Override public void writeTo(OutputStream outstream) throws IOException {
                TarOutputStream tar = new TarOutputStream(outstream);
                TarEntry entry = new TarEntry(file, file.getName());
                tar.putNextEntry(entry);
                FileUtils.copyFile(file, tar);
                tar.close();
            }
        };

        request(new Requests.Upload(name, containerpath, producer));
    }


    @SuppressWarnings("rawtypes")
    public int exec(String name, String ... cmd) throws Exception
    {
        ExecConfig config = new ExecConfig().cmd(Arrays.asList(cmd)).attachStdin(false).attachStdout(false).attachStderr(false);
        Map ret   = request(new Requests.CreateExec(name, config));
        String id = (String)ret.get("Id");

        if (id != null) {
            request(new Requests.StartExec(id));
            long deadline = System.currentTimeMillis() + 45000;
            while (true)
            {
                ExecStatus status = request(new Requests.GetExecStatus(id));
                if ((status != null) && (status.getExitCode() != null))
                    return status.getExitCode();
                Thread.sleep(500);
                if (System.currentTimeMillis() > deadline)
                    throw new IOException("Unable to retrieve exec response");
            }
        }

        throw new IOException("Got to the end of exec, how?");
    }


    public void poke(DockerContainer cont)
    {
        requestIgnore(new Requests.Poke(cont.getName()));
    }


    public void restart(DockerContainer cont)
    {
        requestIgnore(new Requests.Restart(cont.getName()));
    }


    public void kill(DockerContainer cont)
    {
        requestIgnore(new Requests.Kill(cont.getName()));
    }


    public void stop(Collection<DockerContainer> containers, DockerStatusListener listener)
    {
        List<List<Requests.Wrapper<Void>>> outer = new ArrayList<List<Requests.Wrapper<Void>>>();
        for (DockerContainer c : containers)
            outer.add(Arrays.asList(new Requests.Stop(c.getName())));
        parallelAndReport("stop", outer, listener);
    }


    public void teardown(Collection<DockerContainer> containers, DockerStatusListener listener)
    {
        List<List<Requests.Wrapper<Void>>> outer = new ArrayList<List<Requests.Wrapper<Void>>>();
        for (DockerContainer c : containers) {
            List<Requests.Wrapper<Void>> inner = new ArrayList<Requests.Wrapper<Void>>();
            inner.add(new Requests.Stop(c.getName()));
            inner.add(new Requests.Rm(c.getName()));
            outer.add(inner);
        }

        parallelAndReport("teardown", outer, listener);
    }


    /* The utility wrappers of common functionality ***********************************/

    /**
     * Perform the request, with special handling depending on rettype.
     * @param request the request object
     * @param rettype determines how we deal with the repsonse
     * @return depending on rettype:
     *       String.class: wrap the response body in a string and return it
     *  InputStream.class: return the body input stream for processing (must close it)
     *         Void.class: ignore the return data
     *            default: attempt to deserialize a JSON response into the rettype
     * @throws DockerDownException
     * @throws IOException
     *
     */
    @SuppressWarnings("unchecked")
    private <T> T request(Requests.Wrapper<T> wrap) throws IOException
    {
        if (client == null) {
            log.warning(wrap.request + " called with no client present");
            throw new DockerDownException();
        }

        wrap.request.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());

        log.log(Level.FINE, "{0}", wrap.request);
        if (log.isLoggable(Level.FINER) && wrap.request instanceof HttpEntityEnclosingRequestBase) {
            HttpEntity he = ((HttpEntityEnclosingRequestBase)wrap.request).getEntity();
            if (he instanceof StringEntity)
                log.log(Level.FINER, EntityUtils.toString(he));
        }

        HttpResponse resp = client.execute(host, wrap.request);
        if (resp.getStatusLine().getStatusCode() >= 400) {
            String error = EntityUtils.toString(resp.getEntity());
            try { // response Content-type is not always set properly, try json error first, if not, just use raw string
                error = mapper.readValue(error, ErrorResponse.class).getMessage();
            } catch (Exception e) {}
            throw new IOException(error);
        }

        if (wrap.rettype == null) {
            EntityUtils.consumeQuietly(resp.getEntity());
            return null;
        }

        if (wrap.rettype == String.class)
            return (T) EntityUtils.toString(resp.getEntity());
        if (InputStream.class.isAssignableFrom(wrap.rettype))
            return (T) resp.getEntity().getContent();

        return mapper.readValue(resp.getEntity().getContent(), wrap.rettype);
    }

    /**
     * Perform a request and ignore all body, returns or exceptions
     */
    private <T> void requestIgnore(Requests.Wrapper<T> wrap)
    {
        try {
            request(wrap);
        } catch (IOException ioe) {
            log.warning("request failed: " + ioe);
        }
    }


    /**
     * Submit a double list.
     * Each outer list can run in parallel.
     * Each inner list is meant to run synchronously
     * @param chains
     * @throws DockerDownException
     * @throws IOException
     */
    private List<Exception> parallelChains(List<List<Requests.Wrapper<Void>>> chains, DockerStatusListener listener)
    {
        List<Exception> ret = new ArrayList<Exception>();

        if ((executor == null) || (client == null)) {
            log.warning("parallelActions failure: No executor yet");
            ret.add(new DockerDownException());
            return ret;
        }

        final MutableCounter count = new MutableCounter();
        final int total = chains.stream().mapToInt(e -> e.size()).sum();
        if (listener != null)
            listener.status(0, total);

        List<Future<Void>> futures = new ArrayList<Future<Void>>();
        for (List<Requests.Wrapper<Void>> list : chains) {
            futures.add(executor.submit(() -> {
                for (Requests.Wrapper<Void> wrap : list) {
                    try {
                        request(wrap);
                    } finally {
                        if (listener != null)
                            listener.status(count.inc(), total);
                    }
                }
                return null;
            }));
        }

        for (Future<Void> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                ret.add(e);
            }
        }

        return ret;
    }


    private void parallelAndReport(String function, List<List<Requests.Wrapper<Void>>> chains, DockerStatusListener listener)
    {
        List<Exception> ret = parallelChains(chains, listener);
        if (ret.size() > 0) {
            log.warning(function + " errors: ");
            for (Exception e : ret) {
                log.warning(e.getMessage());
            }
        }
    }


    /** Simple tests
     * @throws Exception */
    public static void main(String args[]) throws Exception
    {
        AppSetup.unitLogging();
        log.info("starting");

        DockerAPI api = new DockerAPI();
        api.setup(new HashMap<>());
        //api.pull("drytoastman/scdb:latest");
        //System.out.println(api.alive());
        //api.download("db", "/root/.ash_history", new File("mytestfile"));
        //api.upload("db", new File("mytestfile"), "/root");
        //api.resetNetwork("scnet");
        //api.start("sync", "web", "db");
        //System.out.println(api.volumes());
        //api.volumeCreate("testvol");
        log.warning("last line");
    }
}
