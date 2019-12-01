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
import java.util.TimeZone;
import org.wwscc.system.docker.models.CreateContainerConfig;
import org.wwscc.system.docker.models.HostConfig;
import org.wwscc.system.docker.models.PortMap;
import org.wwscc.util.BroadcastState;
import org.wwscc.util.MT;
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
    private boolean created;
    private BroadcastState<Boolean> up;

    public DockerContainer(String name, String image, MT event)
    {
        this(name, image, null, event);
    }

    public DockerContainer(String name, String image, String netname, MT event)
    {
        this.name    = name;
        this.created = false;
        this.up      = new BroadcastState<Boolean>(event, false);

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
        return up.get();
    }

    public void setState(String state)
    {
        created = false;
        boolean isup = false;

        if (state == null)
            return;
        switch (state) {
            case "running":
            case "restarting":
            case "removing":
                isup = true;
            default:
               created = true;
        }

        up.set(isup);
    }

    public String getName()
    {
        return name;
    }

    public void addPort(String hostip, int hostport, int containerport, String proto)
    {
        String to = containerport+"/"+proto;
        HashMap<String, String> h = new HashMap<String, String>();
        h.put("HostIP", hostip);
        h.put("HostPort", ""+hostport);
        getHostConfig().getPortBindings().put(to, Arrays.asList(new Object[] { h }));
        getExposedPorts().put(to, new HashMap<Object,Object>());
    }

    public void addVolume(String vol, String path)
    {
        getHostConfig().addBindsItem(vol+":"+path);
    }
}
