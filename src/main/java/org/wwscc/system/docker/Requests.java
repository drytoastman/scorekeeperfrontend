/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system.docker;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.wwscc.system.docker.models.ContainerSummary;
import org.wwscc.system.docker.models.ExecConfig;
import org.wwscc.system.docker.models.ExecStatus;
import org.wwscc.system.docker.models.ImageSummary;
import org.wwscc.system.docker.models.Network;
import org.wwscc.system.docker.models.VolumesResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Wrap all the various API requests and return types in classes, only showing the
 * variables, hiding the rest of the URL from the consuming code.
 */
public class Requests
{
    public static final String API_VER = "/v1.35";
    public static final String APPLICATION_JSON = "application/json";
    public static final String CONTENT_TYPE = "Content-Type";
    private static final ObjectMapper mapper = new ObjectMapper();

    /*
     *  models dir data created swagger-codegen using docker API v1.35 swagger.yaml:
     *  swagger-codegen-cli:v2.3.1 generate -i /local/swagger.yaml -o /local/out -ljava
     *     -Dlibrary=feign -Dmodels -Djava8 -DdateLibrary=java8 -DmodelPackage=org.wwscc.system.docker.models
     */

    static HttpRequest.Builder GET(String s)    { return HttpRequest.newBuilder(URI.create(s)).GET(); }
    static HttpRequest.Builder DELETE(String s) { return HttpRequest.newBuilder(URI.create(s)).DELETE(); }
    static HttpRequest.Builder POST(String s, String c, BodyPublisher b) { return HttpRequest.newBuilder(URI.create(s)).header(CONTENT_TYPE, c).POST(b); }

    static class Wrapper<T>
    {
        HttpRequest.Builder request;
        Class<T> rettype;
        public Wrapper(HttpRequest.Builder request)                { this.request = request; this.rettype = null; }
        public Wrapper(HttpRequest.Builder request, Class<T> type) { this.request = request; this.rettype = type; }
    }

    static class MapRequest extends Wrapper<Void>
    {
        public MapRequest(String url, Object ... args) throws IOException
        {
            super(HttpRequest.newBuilder(URI.create(url)));
            if ((args.length % 2) != 0)
                throw new IOException("Varargs needs to be a multiple of 2");

            Map<String, Object> body = new HashMap<String, Object>();
            for (int ii = 0; ii < args.length; ii += 2)
                body.put((String)args[ii], args[ii+1]);

            request.header(CONTENT_TYPE, APPLICATION_JSON);
            request.POST(BodyPublishers.ofString(mapper.writeValueAsString(body)));
        }
    }

    static class ContainerPost extends Wrapper<Void>
    {
        public ContainerPost(String name, String action)
        {
            super(HttpRequest.newBuilder(URI.create(String.format("%s/containers/%s/%s", API_VER, name, action))).POST(BodyPublishers.noBody()));
        }
    }

    /* The actual requests  ****************************************************************************/

    static class Kill extends ContainerPost    { public Kill(String name)    { super(name, "kill"); }}
    static class Poke extends ContainerPost    { public Poke(String name)    { super(name, "kill?signal=SIGHUP"); }}
    static class Start extends ContainerPost   { public Start(String name)   { super(name, "start"); }}
    static class Stop extends ContainerPost    { public Stop(String name)    { super(name, "stop"); }}
    static class Restart extends ContainerPost { public Restart(String name) { super(name, "restart"); }}

    static class Rm extends Wrapper<Void> {
        public Rm(String name) {
            super(DELETE(String.format("%s/containers/%s", API_VER, name)));
    }}

    @SuppressWarnings("rawtypes")
    static class Version extends Wrapper<Map> {
        public Version() {
            super(GET(API_VER+"/version"), Map.class);
        }
    }

    static class GetContainers extends Wrapper<ContainerSummary> {
        public GetContainers() {
            super(GET(API_VER+"/containers/json?all=true"), ContainerSummary.class);
    }}

    static class GetImages extends Wrapper<ImageSummary[]> {
        public GetImages() {
            super(GET(API_VER+"/images/json"), ImageSummary[].class);
    }}

    static class GetVolumes extends Wrapper<VolumesResponse> {
        public GetVolumes() {
            super(GET(API_VER+"/volumes"), VolumesResponse.class);
    }}

    static class GetNetworks extends Wrapper<Network[]> {
        public GetNetworks() {
            super(GET(API_VER+"/networks"), Network[].class);
    }}

    static class PullImage extends Wrapper<Void> {
        public PullImage(String name) {
            super(HttpRequest.newBuilder(URI.create(API_VER+"/images/create?fromImage="+name)).POST(BodyPublishers.noBody()));
    }}

    static class GetNetwork extends Wrapper<Network> {
        public GetNetwork(String name) {
            super(GET(API_VER+"/networks/"+name), Network.class);
    }}

    static class DeleteNetwork extends Wrapper<Void> {
        public DeleteNetwork(String name) {
            super(DELETE(API_VER+"/networks/"+name));
    }}

    static class CreateNetwork extends MapRequest {
        public CreateNetwork(String name) throws IOException {
            super(API_VER+"/networks/create", "Name", name);
    }}

    static class CreateVolume extends MapRequest {
        public CreateVolume(String name) throws IOException {
            super(API_VER+"/volumes/create", "Name", name);
    }}

    static class DisconnectContainer extends MapRequest {
        public DisconnectContainer(String network, String container) throws IOException {
            super(API_VER+"/networks/"+network+"/disconnect", "Container", container, "Force", true);
    }}

    static class CreateContainer extends Wrapper<Void> {
        public CreateContainer(DockerContainer config) throws IOException {
            super(POST(API_VER+"/containers/create?name="+config.getName(), APPLICATION_JSON, BodyPublishers.ofString(mapper.writeValueAsString(config))));
    }}

    static class Download extends Wrapper<InputStream> {
        public Download(String container, String path) {
            super(GET(String.format("%s/containers/%s/archive?path=%s", API_VER, container, path)), InputStream.class);
    }}

    static class Upload extends Wrapper<Void> {
        public Upload(String container, String path, Supplier<InputStream> content) {
            super(HttpRequest.newBuilder(URI.create(String.format("%s/containers/%s/archive?path=%s", API_VER, container, path))).PUT(BodyPublishers.ofInputStream(content)));
    }}

    @SuppressWarnings("rawtypes")
    static class CreateExec extends Wrapper<Map> {
        public CreateExec(String container, ExecConfig config) throws IOException {
            super(POST(String.format("%s/containers/%s/exec", API_VER, container), APPLICATION_JSON, BodyPublishers.ofString(mapper.writeValueAsString(config))));
    }}

    static class StartExec extends MapRequest {
        public StartExec(String id) throws IOException {
            super(API_VER+"/exec/"+id+"/start", "Detach", true);
    }}

    static class GetExecStatus extends Wrapper<ExecStatus> {
        public GetExecStatus(String id) {
            super(GET(API_VER+"/exec/"+id+"/json"), ExecStatus.class);
        }
    }
}
