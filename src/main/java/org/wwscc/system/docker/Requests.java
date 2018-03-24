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
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpRequest;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.entity.StringEntity;
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
    public static final String apiver = "/v1.35";
    private static final ObjectMapper mapper = new ObjectMapper();

    static class Wrapper<T> {
        HttpRequest request;
        Class<T> rettype;
        public Wrapper(HttpRequest request)                { this.request = request; this.rettype = null; }
        public Wrapper(HttpRequest request, Class<T> type) { this.request = request; this.rettype = type; }
    }

    static class MapRequest extends Wrapper<Void> {
        public MapRequest(HttpEntityEnclosingRequestBase req, Object ... args) throws IOException {
            super(req);
            if ((args.length % 2) != 0)
                throw new IOException("Varargs needs to be a multiple of 2");

            Map<String, Object> body = new HashMap<String, Object>();
            for (int ii = 0; ii < args.length; ii += 2)
                body.put((String)args[ii], args[ii+1]);

            ((HttpEntityEnclosingRequestBase)request).setEntity(new StringEntity(mapper.writeValueAsString(body), ContentType.APPLICATION_JSON));
        }
    }

    static class ContainerPost extends Wrapper<Void> {
        public ContainerPost(String name, String action) {
            super(new HttpPost(String.format("%s/containers/%s/%s", apiver, name, action)));
        }
    }

    static class Kill extends ContainerPost      { public Kill(String name)    { super(name, "kill"); }}
    static class Poke extends ContainerPost      { public Poke(String name)    { super(name, "kill?signal=SIGHUP"); }}
    static class Start extends ContainerPost     { public Start(String name)   { super(name, "start"); }}
    static class Stop extends ContainerPost      { public Stop(String name)    { super(name, "stop"); }}
    static class Restart extends ContainerPost   { public Restart(String name) { super(name, "restart"); }}
    static class Rm extends Wrapper<Void> { public Rm(String name) { super(new HttpDelete(String.format("%s/containers/%s", apiver, name))); }}

    static class Version extends Wrapper<String> {
        public Version() {
            super(new HttpGet(apiver+"/version"), String.class);
        }
    }

    static class GetContainers extends Wrapper<ContainerSummary> {
        public GetContainers() {
            super(new HttpGet(apiver+"/containers/json"), ContainerSummary.class);
    }}

    static class GetImages extends Wrapper<ImageSummary[]> {
        public GetImages() {
            super(new HttpGet(apiver+"/images/json"), ImageSummary[].class);
    }}

    static class GetVolumes extends Wrapper<VolumesResponse>     { public GetVolumes()    { super(new HttpGet(apiver+"/volumes")); }}
    static class GetNetworks extends Wrapper<Network[]>          { public GetNetworks()   { super(new HttpGet(apiver+"/networks")); }}

    static class PullImage extends Wrapper<Void>     { public PullImage(String name)     { super(new HttpPost(apiver+"/images/create?fromImage="+name)); }}
    static class GetNetwork extends Wrapper<Network> {
        public GetNetwork(String name) {
            super(new HttpGet(apiver+"/networks/"+name), Network.class);
    }}

    static class DeleteNetwork extends Wrapper<Void> { public DeleteNetwork(String name) { super(new HttpDelete(apiver+"/networks/"+name)); }}

    static class CreateNetwork extends MapRequest {
        public CreateNetwork(String name) throws IOException {
            super(new HttpPost(apiver+"/networks/create"), "Name", name);
    }}

    static class CreateVolume extends MapRequest {
        public CreateVolume(String name) throws IOException {
            super(new HttpPost(apiver+"/volumes/create"), "Name", name);
    }}

    static class DisconnectContainer extends MapRequest {
        public DisconnectContainer(String network, String container) throws IOException {
            super(new HttpPost(apiver+"/networks/"+network+"/disconnect"), "Container", container, "Force", true);
    }}

    static class CreateContainer extends Wrapper<Void> {
        public CreateContainer(DockerContainer config) throws IOException {
            super(new HttpPost(apiver+"/containers/create?name="+config.getName()));
            ((HttpPost)request).setEntity(new StringEntity(mapper.writeValueAsString(config), ContentType.APPLICATION_JSON));
    }}

    static class Download extends Wrapper<InputStream> {
        public Download(String container, String path) {
            super(new HttpGet(String.format("%s/containers/%s/archive?path=%s", apiver, container, path)));
    }}

    static class Upload extends Wrapper<Void> {
        public Upload(String container, String path, ContentProducer content) {
            super(new HttpPut(String.format("%s/containers/%s/archive?path=%s", apiver, container, path)));
            ((HttpPut)request).setEntity(new EntityTemplate(content));
    }}

    static class CreateExec extends Wrapper<Map<?,?>> {
        public CreateExec(String container, ExecConfig config) throws IOException {
            super(new HttpPost(String.format("%s/containers/%s/exec", apiver, container)));
            ((HttpPost)request).setEntity(new StringEntity(mapper.writeValueAsString(config), ContentType.APPLICATION_JSON));
    }}

    static class StartExec extends MapRequest {
        public StartExec(String id) throws IOException {
            super(new HttpPost(apiver+"/exec/"+id+"/start"), "Detach", true);
    }}

    static class GetExecStatus extends Wrapper<ExecStatus> {
        public GetExecStatus(String id) {
            super(new HttpGet(apiver+"/exec/"+id+"/json"));
        }
    }
}
