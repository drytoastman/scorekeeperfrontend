/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system.docker;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.HttpContext;

import com.fasterxml.jackson.databind.ObjectMapper;

import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;

/*
 * Docker API for local connections.
 */
public class DockerAPI
{
    private static final ObjectMapper mapper = new ObjectMapper();

    PoolingHttpClientConnectionManager poolmanager;

    public DockerAPI()
    {
    }

    public void useSSL(Path certdir) throws GeneralSecurityException, IOException
    {
        SSLContext sslctx = DockerCertificates.createContext(certdir);
        SSLConnectionSocketFactory sslf = new SSLConnectionSocketFactory(sslctx, new String[] { "TLSv2" }, null, NoopHostnameVerifier.INSTANCE);
        poolmanager = new PoolingHttpClientConnectionManager(
                RegistryBuilder.<ConnectionSocketFactory>create().register("http", sslf).build());
    }

    public void useDockerSock()
    {
        poolmanager = new PoolingHttpClientConnectionManager(
                RegistryBuilder.<ConnectionSocketFactory>create().register("http", new UnixSocketFactory()).build());
    }

    public void useHyperV()
    {
        poolmanager = new PoolingHttpClientConnectionManager(
                RegistryBuilder.<ConnectionSocketFactory>create().register("http", new WindowsPipeFactory()).build());
    }

    public void test() throws IOException
    {
        CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(poolmanager).build();
        HttpResponse resp = httpClient.execute(new HttpHost("127.0.0.1"), new BasicHttpRequest("GET", "/images/json"));
        System.out.println(mapper.readTree(resp.getEntity().getContent()));
        //String ret = Request.Get("http://127.0.0.2:2375/images/json").connectTimeout(1000).socketTimeout(1000).execute().returnContent().asString();
    }


    static class WindowsPipeFactory implements ConnectionSocketFactory
    {
        @Override
        public Socket createSocket(HttpContext context) throws IOException {
            return new WindowsPipe("\\\\.\\pipe\\docker_engine");
        }
        @Override
        public Socket connectSocket(int connectTimeout, Socket sock, HttpHost host, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context) throws IOException {
            return sock;
        }
    }

    static class UnixSocketFactory implements ConnectionSocketFactory
    {
        @Override
        public Socket createSocket(HttpContext context) throws IOException {
            return UnixSocketChannel.open(new UnixSocketAddress("/var/run/docker.sock")).socket();
        }

        @Override
        public Socket connectSocket(int connectTimeout, Socket sock, HttpHost host, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context) throws IOException {
            return sock;
        }
    }

    public static void main(String args[]) throws IOException
    {
        DockerAPI api = new DockerAPI();
        api.useHyperV();
        api.test();
    }

}
