/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system.docker;

import java.io.IOException;
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

import org.apache.commons.lang3.SystemUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
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
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.wwscc.system.docker.models.ContainerSummary;
import org.wwscc.system.docker.models.CreateContainerConfig;
import org.wwscc.system.docker.models.ErrorResponse;
import org.wwscc.system.docker.models.ImageSummary;
import org.wwscc.system.docker.models.Network;
import org.wwscc.system.docker.models.NetworkContainer;
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
    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(Include.NON_NULL);

    public static final String prefix = "/v1.35";
    public static final String DELETE = "DELETE";
    public static final String POST = "POST";
    public static final String GET = "GET";

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
        return requestvar(GET, prefix+"/images/json", ImageSummary[].class);
    }

    public ContainerSummary containers()
    {
        return requestvar(GET, prefix+"/containers/json", ContainerSummary.class);
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
        return requestvar(GET, prefix+"/volumes", VolumesResponse.class);
    }


    /* Functions for changing the state of the docker environment ***********************/

    public void resetNetwork(String name)
    {
        Network net = requestvar(GET, prefix+"/networks/"+name, Network.class);
        if (net != null) {
            for (NetworkContainer nc : net.getContainers().values()) {
                requestvar(POST, prefix+"/networks/"+name+"/disconnect", null, "Container", nc.getName(), "Force", true);
            }
            requestvar(DELETE, prefix+"/networks/"+name, Void.class);
        }

        requestvar(POST, prefix+"/networks/create", Void.class, "Name", name);
    }


    public Volume volumeCreate(String name)
    {
        return requestvar(POST, prefix+"/volumes/create", Volume.class, "Name", name);
    }


    public void create(String name, CreateContainerConfig config)
    {
        try {
            request(POST, prefix+"/containers/create?name="+name, Void.class, new StringEntity(mapper.writeValueAsString(config), ContentType.APPLICATION_JSON));
        } catch (JsonProcessingException jpe) {
            log.warning("Unable to serialize container config?");
        }
    }

    public boolean poke(String ... names)    { return poke(Arrays.asList(names));    }
    public boolean kill(String ... names)    { return kill(Arrays.asList(names));    }
    public boolean stop(String ... names)    { return stop(Arrays.asList(names));    }
    public boolean start(String ... names)   { return start(Arrays.asList(names));   }
    public boolean restart(String ... names) { return restart(Arrays.asList(names)); }
    public boolean rm(String ... names)      { return rm(Arrays.asList(names));      }

    public boolean poke(Collection<String> names)    { return parallelContainerPost("kill", names, "signal", "SIGHUP"); }
    public boolean kill(Collection<String> names)    { return parallelContainerPost("kill", names);    }
    public boolean stop(Collection<String> names)    { return parallelContainerPost("stop", names);    }
    public boolean start(Collection<String> names)   { return parallelContainerPost("start", names);   }
    public boolean restart(Collection<String> names) { return parallelContainerPost("restart", names); }
    public boolean rm(Collection<String> names)
    {
        return parallelActions(names.stream()
                .map(name -> (Callable<Void>) () -> requestvar(DELETE, String.format("%s/containers/%s", prefix, name), Void.class))
                .collect(Collectors.toList()));
    }

    private boolean parallelContainerPost(String action, Collection<String> names, Object ... args)
    {
        return parallelActions(names.stream()
                .map(name -> (Callable<Void>) () -> requestvar(POST, String.format("%s/containers/%s/%s", prefix, name, action), Void.class, args))
                .collect(Collectors.toList()));
    }

    /* The utility wrappers of common functionality ***********************************/

    static class JacksonProducer implements ContentProducer
    {
        @Override
        public void writeTo(OutputStream outstream) throws IOException {
            // TODO Auto-generated method stub
            EntityTemplate t;
        }
    }

    /**
     * Calls request with args put into simple JSON object
     */
    private <T> T requestvar(String method, String path, Class<T> rettype, Object ... args)
    {
        try {
            if (args.length > 0) {
                if ((args.length % 2) != 0)
                    throw new IOException("Varargs needs to be a multiple of 2");

                Map<String, Object> body = new HashMap<String, Object>();
                for (int ii = 0; ii < args.length; ii += 2)
                    body.put((String)args[ii], args[ii+1]);
                return request(method, path, rettype, new StringEntity(mapper.writeValueAsString(body), ContentType.APPLICATION_JSON));
            } else {
                return request(method, path, rettype, null);
            }
        } catch (IOException ioe) {
            return null;
        }
    }


    private <T> T request(String method, String path, Class<T> rettype, HttpEntity body)
    {
        String head = method+" "+path;
        if (client == null) {
            log.warning(head + " called with no client present");
            return null;
        }

        try {
            BasicHttpEntityEnclosingRequest req = new BasicHttpEntityEnclosingRequest(method, path);
            req.setHeader("Accept", "application/json");

            if (body != null) {
                req.setEntity(body);
                //req.setHeader("Content-type", body.getContentType());
            }

            HttpResponse resp = client.execute(host, req);
            if (resp.getStatusLine().getStatusCode() >= 400) {
                HttpEntity data = resp.getEntity();
                if (ContentType.get(data).equals(ContentType.APPLICATION_JSON))
                    throw new IOException(mapper.readValue(data.getContent(), ErrorResponse.class).getMessage());
                else
                    throw new IOException(EntityUtils.toString(resp.getEntity()));
            }

            if (rettype != Void.class)
                return mapper.readValue(resp.getEntity().getContent(), rettype);
            EntityUtils.consumeQuietly(resp.getEntity());
            return null;

        } catch (IOException e) {
            log.warning(head + ": " + e.getMessage().trim());
            return null;
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


    /** Simple tests */
    public static void main(String args[])
    {
        AppSetup.unitLogging();
        //Logger.getLogger("org.apache.http").setLevel(Level.ALL);
        //Logger.getLogger("org.apache.http.wire").setLevel(Level.ALL);
        log.info("starting");

        DockerAPI api = new DockerAPI();
        api.setup(null);
        System.out.println(api.alive());
        //api.resetNetwork("scnet");
        //api.start("sync", "web", "db");
        //System.out.println(api.volumes());
        //api.volumeCreate("testvol");
        log.warning("last line");
    }
}
