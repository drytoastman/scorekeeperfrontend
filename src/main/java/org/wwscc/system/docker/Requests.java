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
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpRequest;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.wwscc.system.docker.models.ContainerSummary;
import org.wwscc.system.docker.models.ExecConfig;
import org.wwscc.system.docker.models.ImageSummary;
import org.wwscc.system.docker.models.Network;
import org.wwscc.system.docker.models.NetworkInspect;
import org.wwscc.system.docker.models.VolumeListResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Wrap all the various API requests and return types in classes, only showing the
 * variables, hiding the rest of the URL from the consuming code.
 */
public class Requests
{
    public static final String API_VER = "/v1.52";
    private static final ObjectMapper mapper = new ObjectMapper();

    /*
     *  models dir data created swagger-codegen using docker API v1.52 swagger.yaml:
     *  swagger-codegen-cli:v2.3.1 generate -i /local/swagger.yaml -o /local/out -ljava
     *     -Dlibrary=feign -Dmodels -Djava8 -DdateLibrary=java8 -DmodelPackage=org.wwscc.system.docker.models
     */

    static class Wrapper<T>
    {
        HttpRequest request;
        Class<T> rettype;
        public Wrapper(HttpRequest request)                { this.request = request; this.rettype = null; }
        public Wrapper(HttpRequest request, Class<T> type) { this.request = request; this.rettype = type; }
    }

    static class MapRequest extends Wrapper<Void>
    {
        public MapRequest(HttpEntityEnclosingRequestBase req, Object ... args) throws IOException
        {
            super(req);
            if ((args.length % 2) != 0)
                throw new IOException("Varargs needs to be a multiple of 2");

            Map<String, Object> body = new HashMap<String, Object>();
            for (int ii = 0; ii < args.length; ii += 2)
                body.put((String)args[ii], args[ii+1]);

            ((HttpEntityEnclosingRequestBase)request).setEntity(new StringEntity(mapper.writeValueAsString(body), ContentType.APPLICATION_JSON));
        }
    }

    static class ContainerPost extends Wrapper<Void>
    {
        public ContainerPost(String name, String action)
        {
            super(new HttpPost(String.format("%s/containers/%s/%s", API_VER, name, action)));
        }
    }

    /* The actual requests  ****************************************************************************/

    static class Kill extends ContainerPost    { public Kill(String name)    { super(name, "kill"); }}
    static class Poke extends ContainerPost    { public Poke(String name)    { super(name, "kill?signal=SIGHUP"); }}
    static class Start extends ContainerPost   { public Start(String name)   { super(name, "start"); }}
    static class Stop extends ContainerPost    { public Stop(String name)    { super(name, "stop"); }}
    static class Restart extends ContainerPost { public Restart(String name) { super(name, "restart"); }}
    static class Wait extends ContainerPost    { public Wait(String name)    { super(name, "wait"); }}

    static class Logs extends Wrapper<String> {
        public Logs(String name) {
            super(new HttpGet(String.format("%s/containers/%s/logs", API_VER)));
        }
    }

    static class Rm extends Wrapper<Void> {
        public Rm(String name) {
            super(new HttpDelete(String.format("%s/containers/%s", API_VER, name)));
    }}

    @SuppressWarnings("rawtypes")
    static class Version extends Wrapper<Map> {
        public Version() {
            super(new HttpGet(API_VER+"/version"), Map.class);
        }
    }

    static class GetContainers extends Wrapper<ContainerSummary[]> {
        public GetContainers() {
            super(new HttpGet(API_VER+"/containers/json?all=true"), ContainerSummary[].class);
    }}

    static class GetImages extends Wrapper<ImageSummary[]> {
        public GetImages() {
            super(new HttpGet(API_VER+"/images/json"), ImageSummary[].class);
    }}

    static class GetVolumes extends Wrapper<VolumeListResponse> {
        public GetVolumes() {
            super(new HttpGet(API_VER+"/volumes"), VolumeListResponse.class);
    }}

    static class GetNetworks extends Wrapper<Network[]> {
        public GetNetworks() {
            super(new HttpGet(API_VER+"/networks"), Network[].class);
    }}

    static class PullImage extends Wrapper<InputStream> {
        public PullImage(String name) {
            super(new HttpPost(API_VER+"/images/create?fromImage="+name), InputStream.class);
    }}

    static class GetNetwork extends Wrapper<NetworkInspect> {
        public GetNetwork(String name) {
            super(new HttpGet(API_VER+"/networks/"+name), NetworkInspect.class);
    }}

    static class DeleteNetwork extends Wrapper<Void> {
        public DeleteNetwork(String name) {
            super(new HttpDelete(API_VER+"/networks/"+name));
    }}

    static class CreateNetwork extends MapRequest {
        public CreateNetwork(String name) throws IOException {
            super(new HttpPost(API_VER+"/networks/create"), "Name", name);
    }}

    static class CreateVolume extends MapRequest {
        public CreateVolume(String name) throws IOException {
            super(new HttpPost(API_VER+"/volumes/create"), "Name", name);
    }}

    static class DisconnectContainer extends MapRequest {
        public DisconnectContainer(String network, String container) throws IOException {
            super(new HttpPost(API_VER+"/networks/"+network+"/disconnect"), "Container", container, "Force", true);
    }}

    static class CreateContainer extends Wrapper<Void> {
        public CreateContainer(DockerContainer config) throws IOException {
            super(new HttpPost(API_VER+"/containers/create?name="+config.getName()));
            ((HttpPost)request).setEntity(new StringEntity(mapper.writeValueAsString(config), ContentType.APPLICATION_JSON));
    }}

    static class Download extends Wrapper<InputStream> {
        public Download(String container, String path) {
            super(new HttpGet(String.format("%s/containers/%s/archive?path=%s", API_VER, container, path)), InputStream.class);
    }}

    static class Upload extends Wrapper<Void> {
        public Upload(String container, String path, File content) {
            super(new HttpPut(String.format("%s/containers/%s/archive?path=%s", API_VER, container, path)));
            ((HttpPut)request).setEntity(new FileEntity(content));
    }}

    @SuppressWarnings("rawtypes")
    static class CreateExec extends Wrapper<Map> {
        public CreateExec(String container, ExecConfig config) throws IOException {
            super(new HttpPost(String.format("%s/containers/%s/exec", API_VER, container)), Map.class);
            ((HttpPost)request).setEntity(new StringEntity(mapper.writeValueAsString(config), ContentType.APPLICATION_JSON));
    }}

    static class StartExec extends MapRequest {
        public StartExec(String id) throws IOException {
            super(new HttpPost(API_VER+"/exec/"+id+"/start"), "Detach", true);
    }}

    static class RunExec extends Wrapper<String> {
        public RunExec(String id) throws IOException {
            super(new HttpPost(API_VER+"/exec/"+id+"/start"), String.class);
            Map<String, Object> body = new HashMap<String, Object>();
            body.put("Detach", false);
            ((HttpPost)request).setEntity(new StringEntity(mapper.writeValueAsString(body), ContentType.APPLICATION_JSON));
    }}

    @SuppressWarnings("rawtypes")
    static class GetExecStatus extends Wrapper<Map> {
        public GetExecStatus(String id) {
            super(new HttpGet(API_VER+"/exec/"+id+"/json"), Map.class);
        }
    }
}
