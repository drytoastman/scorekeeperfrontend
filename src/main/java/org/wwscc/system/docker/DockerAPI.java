/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system.docker;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.BoundedInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.wwscc.system.docker.models.ContainerSummary;
import org.wwscc.system.docker.models.CreateContainerConfig;
import org.wwscc.system.docker.models.ErrorResponse;
import org.wwscc.system.docker.models.ExecConfig;
import org.wwscc.system.docker.models.ExecStatus;
import org.wwscc.system.docker.models.ImageSummary;
import org.wwscc.system.docker.models.Network;
import org.wwscc.system.docker.models.NetworkContainer;
import org.wwscc.system.docker.models.VolumesResponse;
import org.wwscc.util.AppSetup;
import org.wwscc.util.SocketFactories.UnixSocketFactory;
import org.wwscc.util.SocketFactories.WindowsPipeFactory;

/*
 * Docker API for local connections.
 */
public class DockerAPI
{
    private static final Logger log = Logger.getLogger(DockerAPI.class.getCanonicalName());
    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(Include.NON_NULL);

    public static final String prefix = "/v1.35";

    HttpClientBuilder builder;
    CloseableHttpClient client;
    HttpHost host;
    ExecutorService executor;

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

            PoolingHttpClientConnectionManager pool;
            if ((env != null) && env.containsKey("DOCKER_HOST") && (env.containsKey("DOCKER_CERT_PATH")))
            {
                SSLContext sslctx = DockerCertificates.createContext(Paths.get(env.get("DOCKER_CERT_PATH")));
                SSLConnectionSocketFactory sslf = new SSLConnectionSocketFactory(sslctx, new String[] { "TLSv2" }, null, NoopHostnameVerifier.INSTANCE);
                pool = new PoolingHttpClientConnectionManager(
                        RegistryBuilder.<ConnectionSocketFactory>create().register("http", sslf).build());
                String pieces[] = env.get("DOCKER_HOST").split(":"); // usually looks like tcp://192.168.99.100:2376
                host = new HttpHost(InetAddress.getByName(pieces[1]), Integer.parseInt(pieces[2]), "https");
            }
            else if (SystemUtils.IS_OS_WINDOWS)
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
                host = new HttpHost("127.0.0.1");
            }

            executor = Executors.newFixedThreadPool(5, (r) -> { Thread t = new Thread(r); t.setDaemon(true); return t; });
            builder  = HttpClients.custom().setConnectionManager(pool);
            client   = builder.build();
        } catch (Exception e) {
            log.warning("Failed to setup docker API, will try again later: " + e);
        }
    }

    /* Functions for getting data from docker ******************************************/

    public ImageSummary[] images()
    {
        return request(new HttpGet(prefix+"/images/json"), ImageSummary[].class);
    }

    public ContainerSummary containers()
    {
        return request(new HttpGet(prefix+"/containers/json"), ContainerSummary.class);
    }

    public List<String> alive()
    {
        return containers().stream()
                .map(i -> i.getNames())
                .flatMap(l -> l.stream())
                .distinct().map(s -> s.substring(1))
                .collect(Collectors.toList());
    }

    public VolumesResponse volumes()
    {
        return request(new HttpGet(prefix+"/volumes"), VolumesResponse.class);
    }

    public Network network(String name)
    {
        return request(new HttpGet(prefix+"/networks/"+name), Network.class);
    }


    /* Functions for changing the state of the docker environment ***********************/

    public void pull(String image)
    {
        request(new HttpPost(prefix+"/images/create?fromImage="+image), Void.class);
    }

    public void networkCreate(String netname)
    {
        requestvar(new HttpPost(prefix+"/networks/create"), Void.class, "Name", netname);
    }

    public void networkDelete(String netname)
    {
        Network net = request(new HttpGet(prefix+"/networks/"+netname), Network.class);
        if (net != null) {
            for (NetworkContainer nc : net.getContainers().values()) {
                requestvar(new HttpPost(prefix+"/networks/"+netname+"/disconnect"), Void.class, "Container", nc.getName(), "Force", true);
            }
            request(new HttpDelete(prefix+"/networks/"+netname), Void.class);
        }
    }

    public void volumeCreate(String name)
    {
        requestvar(new HttpPost(prefix+"/volumes/create"), Void.class, "Name", name);
    }

    public void create(String name, CreateContainerConfig config)
    {
        try {
            HttpPost request = new HttpPost(prefix+"/containers/create?name="+name);
            request.setEntity(new StringEntity(mapper.writeValueAsString(config), ContentType.APPLICATION_JSON));
            request(request, Void.class);
        } catch (JsonProcessingException jpe) {
            log.warning("Unable to serialize container config?");
        }
    }

    public void download(String name, String containerpath, File hostpath) throws IOException
    {
        String uri = String.format("%s/containers/%s/archive?path=%s", prefix, name, containerpath);
        try (TarArchiveInputStream in = new TarArchiveInputStream(request(new HttpGet(uri), InputStream.class)))
        {
            TarArchiveEntry e = in.getNextTarEntry(); // we only expect one entry at this time
            FileUtils.copyToFile(new BoundedInputStream(in, e.getSize()), hostpath);
            hostpath.setLastModified(e.getModTime().getTime());
        }
    }

    public boolean upload(String name, File file, String containerpath) throws IOException
    {
        ContentProducer producer = new ContentProducer() {
            @Override public void writeTo(OutputStream outstream) throws IOException {
                TarArchiveOutputStream tar = new TarArchiveOutputStream(outstream);
                TarArchiveEntry entry = new TarArchiveEntry(file.getName());
                entry.setModTime(file.lastModified());
                entry.setSize(file.length());
                tar.putArchiveEntry(entry);
                FileUtils.copyFile(file, tar);
                tar.closeArchiveEntry();
                tar.close();
            }
        };

        HttpPut request = new HttpPut(String.format("%s/containers/%s/archive?path=%s", prefix, name, containerpath));
        request.setEntity(new EntityTemplate(producer));
        return request(request, Boolean.class);
    }


    public int exec(String name, String ... cmd) throws Exception
    {
        ExecConfig config = new ExecConfig().cmd(Arrays.asList(cmd)).attachStdin(false).attachStdout(false).attachStderr(false);

        HttpPost request = new HttpPost(String.format("%s/containers/%s/exec", prefix, name));
        request.setEntity(new StringEntity(mapper.writeValueAsString(config), ContentType.APPLICATION_JSON));
        String id = (String)request(request, Map.class).get("Id");

        if (id != null) {
            requestvar(new HttpPost(prefix+"/exec/"+id+"/start"), Void.class, "Detach", true);
            long deadline = System.currentTimeMillis() + 45000;
            while (true)
            {
                ExecStatus status = request(new HttpGet(prefix+"/exec/"+id+"/json"), ExecStatus.class);
                if ((status != null) && (status.getExitCode() != null))
                    return status.getExitCode();
                Thread.sleep(500);
                if (System.currentTimeMillis() > deadline)
                    throw new IOException("Unable to retrieve exec response");
            }
        }

        throw new IOException("Got to the end of exec, how?");
    }


    public boolean poke(String ... names)    { return poke(Arrays.asList(names));    }
    public boolean kill(String ... names)    { return kill(Arrays.asList(names));    }
    public boolean stop(String ... names)    { return stop(Arrays.asList(names));    }
    public boolean start(String ... names)   { return start(Arrays.asList(names));   }
    public boolean restart(String ... names) { return restart(Arrays.asList(names)); }
    public boolean rm(String ... names)      { return rm(Arrays.asList(names));      }

    public boolean poke(Collection<String> names)    { return parallelContainerPost("kill?signal=SIGHUP", names); }
    public boolean kill(Collection<String> names)    { return parallelContainerPost("kill", names);    }
    public boolean stop(Collection<String> names)    { return parallelContainerPost("stop", names);    }
    public boolean start(Collection<String> names)   { return parallelContainerPost("start", names);   }
    public boolean restart(Collection<String> names) { return parallelContainerPost("restart", names); }
    public boolean rm(Collection<String> names)
    {
        return parallelActions(names.stream()
                .map(name -> (Callable<Void>) () -> request(new HttpDelete(String.format("%s/containers/%s", prefix, name)), Void.class))
                .collect(Collectors.toList()));
    }

    private boolean parallelContainerPost(String action, Collection<String> names)
    {
        return parallelActions(names.stream()
                .map(name -> (Callable<Void>) () -> request(new HttpPost(String.format("%s/containers/%s/%s", prefix, name, action)), Void.class))
                .collect(Collectors.toList()));
    }

    /* The utility wrappers of common functionality ***********************************/

    /**
     * Calls request with args put into simple JSON object
     */
    private <T> T requestvar(HttpEntityEnclosingRequestBase request, Class<T> rettype, Object ... args)
    {
        try {
            if ((args.length % 2) != 0)
                throw new IOException("Varargs needs to be a multiple of 2");

            Map<String, Object> body = new HashMap<String, Object>();
            for (int ii = 0; ii < args.length; ii += 2)
                body.put((String)args[ii], args[ii+1]);
            request.setEntity(new StringEntity(mapper.writeValueAsString(body), ContentType.APPLICATION_JSON));
            return request(request, rettype);
        } catch (IOException ioe) {
            return null;
        }
    }

    /**
     * Perform the request, with special handling depending on rettype.
     * @param request the request object
     * @param rettype determines how we deal with the repsonse
     * @return depending on rettype:
     *       String.class: wrap the response body in a string and return it
     *      Boolean.class: return true for response in the 200-300 range, false otherwise
     *  InputStream.class: return the body input stream for processing (must close it)
     *            default: attempt to deserialize a JSON response into the rettype
     *
     */
    @SuppressWarnings("unchecked")
    private <T> T request(HttpRequest request, Class<T> rettype)
    {
        if (client == null) {
            log.warning(request + " called with no client present");
            return null;
        }

        try {
            request.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
            HttpResponse resp = client.execute(host, request);
            if (resp.getStatusLine().getStatusCode() >= 400) {
                String error = EntityUtils.toString(resp.getEntity());
                try { // response Content-type is not always set properly, try json error first, if not, just use raw string
                    error = mapper.readValue(error, ErrorResponse.class).getMessage();
                } catch (Exception e) {}
                throw new IOException(error);
            }

            if (rettype.equals(String.class))
                return (T) EntityUtils.toString(resp.getEntity());
            if (InputStream.class.isAssignableFrom(rettype))
                return (T) resp.getEntity().getContent();
            else if ((rettype != Boolean.class) && (rettype != Void.class))
                return mapper.readValue(resp.getEntity().getContent(), rettype);

            // fall back is to close it all up here
            EntityUtils.consumeQuietly(resp.getEntity());
            return (T) ((rettype == Boolean.class) ? Boolean.TRUE : null);
        } catch (IOException e) {
            log.warning(request + ": " + e.getMessage().trim());
            return (T) ((rettype == Boolean.class) ? Boolean.FALSE : null);
        }
    }


    private boolean parallelActions(List<Callable<Void>> requests)
    {
        if (executor == null) {
            log.warning("parallelActions failure: No executor yet");
            return false;
        }

        boolean ok = true;
        try {
            for (Future<Void> f : executor.invokeAll(requests)) {
                try {
                    f.get();
                } catch (Exception e) {
                    ok = false;
                    log.warning(f.toString() + " action failure: " + e);
                }
            }
        } catch (InterruptedException ie) {
            log.warning("parallelActions failure: interupted while starting");
        }

        return ok;
    }


    /** Simple tests
     * @throws Exception */
    public static void main(String args[]) throws Exception
    {
        AppSetup.unitLogging();
        //Logger.getLogger("org.apache.http").setLevel(Level.ALL);
        //Logger.getLogger("org.apache.http.wire").setLevel(Level.ALL);
        log.info("starting");

        DockerAPI api = new DockerAPI();
        api.setup(null);
        //api.pull("drytoastman/scdb:testdb");
        System.out.println("code = " + api.exec("db", "ash", "-c", "sleep 30"));

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
