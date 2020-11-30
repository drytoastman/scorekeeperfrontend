package org.wwscc.system;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

public class Api2 {

    private static String api2(String call) throws IOException {
        CloseableHttpClient client = HttpClientBuilder.create().setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build()).build();
        HttpGet request = new HttpGet("http://127.0.0.1/api2/" + call);
        HttpResponse response = client.execute(request);
        int code = response.getStatusLine().getStatusCode();
        String body = EntityUtils.toString(response.getEntity());
        if (code != 200) throw new IOException(body);
        return body;
    }

    static public List<String> remotelist(String host) throws IOException {
        return Arrays.asList(api2(String.format("remotelist?host=%s", host)).split(","));
    }

    static public boolean passwordcheck(String host, String series, String password) throws IOException {
        String res = api2(String.format("remotecheck?host=%s&series=%s&password=%s", host, series, password));
        System.out.println(res);
        return res.equals("accepted");
    }
}
