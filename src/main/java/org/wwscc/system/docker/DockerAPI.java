/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system.docker;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.SystemUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.wwscc.system.docker.models.ContainerSummary;
import org.wwscc.system.docker.models.ErrorResponse;
import org.wwscc.system.docker.models.ImageSummary;
import org.wwscc.system.docker.models.Network;
import org.wwscc.system.docker.models.Volume;
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
    private static final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    public static final String prefix = "/v1.35";

    HttpClientBuilder builder;
    CloseableHttpClient client;
    HttpHost host;
    ExecutorService executor;

    public static class DockerError extends IOException { DockerError(String s) { super(s); }}

    public DockerAPI()
    {
    }

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
            if (env.containsKey("DOCKER_HOST") && (env.containsKey("DOCKER_CERT_PATH")))
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

    public ImageSummary[] images() throws IOException
    {
        return get(prefix+"/images/json", ImageSummary[].class);
    }

    public ContainerSummary containers() throws IOException
    {
        return get(prefix+"/containers/json", ContainerSummary.class);
    }

    public VolumesResponse volumes() throws IOException
    {
        return get(prefix+"/volumes", VolumesResponse.class);
    }


    /* Functions for changing the state of the docker environment ***********************/

    public void resetNetwork(String name)
    {
        try {
        Network net = null;
        try { net = get(prefix+"/networks/"+name, Network.class); } catch (DockerError de) { /* doesn't exist yet */}

        if (net != null) {
            for (String id : net.getContainers().keySet()) {
                post(prefix+"/networks/"+name+"/disconnect", null, "Container", id, "Force", true);
            }
            delete(prefix+"/networks/"+name);
        }

        post(prefix+"/networks/create", null, "Name", name);
        } catch (IOException ioe) {
            log.warning("resetNetwork failed: " + ioe);
        }
    }

    public Volume volumeCreate(String name) throws IOException
    {
        return post(prefix+"/volumes/create", Volume.class, "Name", name);
    }

    public void start(String ... names) throws IOException, InterruptedException, ExecutionException
    {
        parallelContainerAction("start", names);
    }

    public void stop(String ... names) throws IOException
    {
        parallelContainerAction("stop", names);
    }

    public void restart(String ... names) throws IOException
    {
        parallelContainerAction("restart", names);
    }

    public void signal(String name, String signal) throws IOException
    {
        post(prefix+"/volumes/create", null, "id", name, "signal", signal);
    }


    /* The utility wrappers of common functionality ***********************************/

    private <T> T get(String path, Class<T> rettype) throws IOException
    {
        if (client == null)
            throw new DockerError("No client yet");

        HttpGet get = new HttpGet(path);
        HttpResponse resp = client.execute(host, get);
        if (resp.getStatusLine().getStatusCode() >= 400) {
            throw new DockerError(mapper.readValue(resp.getEntity().getContent(), ErrorResponse.class).getMessage());
        }

        if (rettype != null)
            return mapper.readValue(resp.getEntity().getContent(), rettype);
        EntityUtils.consumeQuietly(resp.getEntity());
        return null;
    }

    private void delete(String path) throws IOException
    {
        if (client == null)
            throw new DockerError("No client yet");

        HttpDelete delete = new HttpDelete(path);
        HttpResponse resp = client.execute(host, delete);
        if (resp.getStatusLine().getStatusCode() >= 400) {
            throw new DockerError(mapper.readValue(resp.getEntity().getContent(), ErrorResponse.class).getMessage());
        }
        EntityUtils.consumeQuietly(resp.getEntity());
    }

    private <T> T post(String path, Class<T> rettype, Object ... args) throws IOException
    {
        if (client == null)
            throw new DockerError("No client yet");

        HttpPost post = new HttpPost(path);
        if (args.length > 0)
        {
            if ((args.length % 2) != 0)
                throw new IOException("Must use an even number of arguments for _postn");

            Map<String, Object> req = new HashMap<String, Object>();
            for (int ii = 0; ii < args.length; ii += 2)
                req.put((String)args[ii], args[ii+1]);

            post.setEntity(new StringEntity(mapper.writeValueAsString(req)));
            post.setHeader("Content-type", "application/json");
        }

        HttpResponse resp = client.execute(host, post);
        if (resp.getStatusLine().getStatusCode() >= 400) {
            throw new DockerError(mapper.readValue(resp.getEntity().getContent(), ErrorResponse.class).getMessage());
        }

        if (rettype != null)
            return mapper.readValue(resp.getEntity().getContent(), rettype);
        EntityUtils.consumeQuietly(resp.getEntity());
        return null;
    }


    /**
     * Do any common container actions (start, stop, restart) in parallel and collect any exceptions.
     * Only currently intended for empty body in request and response
     * @param action the action string
     * @param names the list of container names
     * @throws DockerError
     */
    private void parallelContainerAction(String action, String ... names) throws DockerError
    {
        if (executor == null) {
            throw new DockerError("No executor yet");
        }

        List<Future<Void>> torun = new ArrayList<Future<Void>>();
        StringBuilder builder = new StringBuilder();

        for (String name : names)
            torun.add(executor.submit(() -> post(String.format("%s/containers/%s/%s", prefix, name, action), null)));

        for (Future<Void> f : torun) {
            try {
                f.get();
            } catch (Exception e) {
                builder.append(e.getMessage()).append("\n");
            }
        }

        if (builder.length() > 0)
            throw new DockerError(builder.toString());
    }


    /** Simple tests
     * @throws InterruptedException */
    public static void main(String args[]) throws IOException, InterruptedException
    {
        AppSetup.unitLogging();
        //Logger.getLogger("org.apache.http").setLevel(Level.ALL);
        //Logger.getLogger("org.apache.http.wire").setLevel(Level.ALL);
        log.info("starting");

        try {
            DockerAPI api = new DockerAPI();
            api.start("sync", "web", "db");
            //api.resetNetwork("scnet");
            //System.out.println(api.volumes());
            //api.volumeCreate("testvol");
        } catch (Exception ioe) {
            System.err.println(ioe.getMessage());
        }

        //Thread.sleep(1000); // make sure logging gets out
        log.warning("last line");
    }
}
