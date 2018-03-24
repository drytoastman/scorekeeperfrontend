/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.wwscc.system.docker.DockerMachine;
import org.wwscc.util.BroadcastState;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

/**
 * Thread to start machine and monitor port forwarding
 */
public class MachineMonitor extends MonitorBase
{
    private static final Logger log = Logger.getLogger(MachineMonitor.class.getName());

    private JSch jsch;
    private Session ports;
    private String machinehost = "192.168.99.100";
    private Pattern hostpattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.\\d+)");
    private BroadcastState<Map<String,String>> dockerenv;
    private BroadcastState<Boolean> machineready, usingmachine;
    private BroadcastState<String> status;
    private boolean shouldStopMachine, backendready;

    public MachineMonitor()
    {
        super("MachineMonitor", 10000);
        dockerenv     = new BroadcastState<Map<String,String>>(MT.DOCKER_ENV, null);
        machineready  = new BroadcastState<Boolean>(MT.MACHINE_READY, false);
        usingmachine  = new BroadcastState<Boolean>(MT.USING_MACHINE, null);
        status        = new BroadcastState<String>(MT.MACHINE_STATUS, "");
        shouldStopMachine = false;
        backendready  = false;
        ports = null;

        Messenger.register(MT.BACKEND_READY, (m, o) -> { backendready = (boolean)o; poke(); });
    }

    public void stopMachine(boolean yes)
    {
        shouldStopMachine = yes;
    }

    private boolean envBad()
    {
        return (dockerenv.get() == null) || (dockerenv.get().get("DOCKER_HOST") == null) || (dockerenv.get().get("DOCKER_CERT_PATH") == null);
    }

    @Override
    protected boolean minit()
    {
        status.set("Checking if present");
        if (!DockerMachine.machinepresent())
        {
            dockerenv.set(new HashMap<>());
            usingmachine.set(false);
            machineready.set(true);
            status.set("Not Needed");
            return false;
        }

        usingmachine.set(true);
        status.set("Checking if created");
        if (!DockerMachine.machinecreated())
        {
            log.info("Creating a new docker machine");
            status.set("Creating VM");
            if (!DockerMachine.createmachine())
            {
                log.severe("Unable to create a docker machine");
                status.set("VM Creation Failed");
                return false;
            }
        }

        jsch = new JSch();
        status.set("Done checks");
        return true;
    }


    @Override
    protected void mloop()
    {
        // Make sure machine is running
        if (!DockerMachine.machinerunning())
        {
            machineready.set(false);
            dockerenv.set(null);
            log.info("Starting the docker machine.");
            status.set("Restarting VM");
            if (!DockerMachine.startmachine())
            {
                log.severe("Unable to restart docker machine, will try again");
                status.set("VM Paused");
                return;
            }
        }

        // Make sure we have a proper environment setup
        if (envBad())
        {
            status.set("Loading environment");
            dockerenv.set(DockerMachine.machineenv());
            if (envBad())
            {
                log.warning("Unable to load machine env, will try again");
                status.set("Waiting for Env");
                return;
            }
        }

        // Machine is ready for container execution now
        DockerMachine.settime();  // set time just in case sleeping vm drifted
        machineready.set(true);

        // Make sure port forwarding is up
        List<String> missing = missingPorts();
        try
        {
            Matcher m = hostpattern.matcher(dockerenv.get().get("DOCKER_HOST"));
            if (m.find()) { machinehost = m.group(1); }

            if (jsch.getIdentityNames().size() == 0)
                jsch.addIdentity(Paths.get(dockerenv.get().get("DOCKER_CERT_PATH"), "id_rsa").toString());

            if ((ports == null) || (!ports.isConnected())) {
                ports = jsch.getSession("docker", machinehost);
                ports.setConfig("StrictHostKeyChecking", "no");
                ports.setConfig("GSSAPIAuthentication",  "no");
                ports.setConfig("PreferredAuthentications", "publickey");
                ports.connect();
            }

            if (missing.contains("6432"))
                forwardPort("127.0.0.1", 6432);
            if (missing.contains("80"))
                forwardPort("*", 80);
            if (missing.contains("54329"))
                forwardPort("*", 54329);
            missing = missingPorts();
        }
        catch (Exception jse)
        {
            log.log(Level.INFO, "Error in portfoward check: " + jse, jse);
            return;
        }

        if (missing.size() > 0)
            status.set("Ports " + String.join(",", missing));
        else
            status.set("Running");
    }

    private void forwardPort(String host, int port)
    {
        try {
            ports.setPortForwardingL(host, port, "127.0.0.1", port);
        } catch (JSchException jse) {
            log.log(Level.INFO, "Error setting up portforwarding: " + jse);
        }
    }

    private List<String> missingPorts()
    {
        List<String> ret = new ArrayList<String>(Arrays.asList(new String[] { "6432", "80", "54329" }));
        try {
            for (String s : ports.getPortForwardingL())
                ret.remove(s.split(":")[0]);
        } catch (Exception e) {}
        return ret;
    }

    @Override
    protected void mshutdown()
    {
        status.set("Stopping port-forwarding");
        if (ports != null)
            ports.disconnect();

        if (shouldStopMachine) {
            status.set("Waiting for backend shutdown");
            while (backendready)
                donefornow();
            status.set("Shutting down machine");
            DockerMachine.stopmachine();
        }
        status.set("Done");
    }
}
