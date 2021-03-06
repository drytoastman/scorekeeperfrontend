/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system.docker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import org.wwscc.system.docker.models.CreateContainerConfig;
import org.wwscc.system.docker.models.HostConfig;
import org.wwscc.system.docker.models.PortMap;
import org.wwscc.util.Prefs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Wraps the docker configuration details and add some state information that is
 * hidden from the JSON serializer.
 */
@JsonIgnoreProperties( { "name", "down" })
public class DockerContainer extends CreateContainerConfig
{
    private String name;
    private boolean created, up;

    public DockerContainer(String name, String image)
    {
        this(name, image, null);
    }

    public DockerContainer(String name, String image, String netname)
    {
        this.name    = name;
        this.created = true;
        this.up      = true;

        addEnvItem("UI_TIME_ZONE="+TimeZone.getDefault().getID());
        addEnvItem("LOG_LEVEL="+Prefs.getLogLevel().getPythonLevel());
        addEnvItem("ONSITE=1");
        setHostConfig(new HostConfig().autoRemove(false).portBindings(new PortMap()).binds(new ArrayList<>()));
        setExposedPorts(new HashMap<String, Object>());

        if (netname != null)
            setNetwork(netname);
        setImage(image);
        setAttachStderr(false);
        setAttachStdout(false);
        setAttachStdin(false);
    }

    public boolean isCreated()
    {
        return created;
    }

    public boolean isUp()
    {
        return up;
    }

    public void setState(String state)
    {
        created = false;
        up = false;
        if (state == null)
            return;
        switch (state) {
            case "running":
            case "restarting":
            case "removing":
                up = true;
            default:
               created = true;
        }
    }

    public String getName()
    {
        return name;
    }

    public String shortName()
    {
        String sp[] = name.split("_");
        if (sp.length > 1)
            return sp[1];
        return name;
    }

    public void changePort(String hostip, int hostport, int containerport, String proto, boolean add)
    {
        String to = containerport+"/"+proto;
        HashMap<String, String> h = new HashMap<String, String>();
        h.put("HostIP", hostip);
        h.put("HostPort", ""+hostport);

        PortMap pmap = getHostConfig().getPortBindings();
        Map<String,Object> emap = getExposedPorts();
        if (add) {
            pmap.put(to, Arrays.asList(new Object[] { h }));
            emap.put(to, new HashMap<Object,Object>());
        } else {
            pmap.remove(to);
            emap.remove(to);
        }
    }

    public void addVolume(String vol, String path)
    {
        getHostConfig().addBindsItem(vol+":"+path);
    }
}
