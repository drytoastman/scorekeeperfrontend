/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system.monitors;

import java.nio.file.Paths;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.wwscc.system.docker.DockerMachine;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

/**
 * Thread to start machine and monitor port forwarding
 */
public class MachineMonitor extends Monitor
{
    private static final Logger log = Logger.getLogger(MachineMonitor.class.getName());

    private JSch jsch;
    private Session portforward, port80forward;
    private String machinehost = "192.168.99.100";
    private Pattern hostpattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.\\d+)");
    private BroadcastState<Map<String,String>> machineenv;
    private BroadcastState<Boolean> dbportsready, webportready, machineready, usingmachine;
    private BroadcastState<String> status;
    private boolean shouldStopMachine, backendready;

    public MachineMonitor()
    {
        super("MachineMonitor", 10000);
        machineenv   = new BroadcastState<Map<String,String>>(MT.MACHINE_ENV, null);
        dbportsready = new BroadcastState<Boolean>(MT.DB_PORTS_READY, false);
        webportready = new BroadcastState<Boolean>(MT.WEB_PORT_READY, false);
        machineready = new BroadcastState<Boolean>(MT.MACHINE_READY, false);
        usingmachine = new BroadcastState<Boolean>(MT.USING_MACHINE, null);
        status       = new BroadcastState<String>(MT.MACHINE_STATUS, "");
        shouldStopMachine = false;
        backendready = false;

        Messenger.register(MT.BACKEND_READY, (m, o) -> { backendready = (boolean)o; poke(); });
    }

    public void stopMachine(boolean yes)
    {
        shouldStopMachine = yes;
    }

    @Override
    protected boolean minit()
    {
        if (!DockerMachine.machinepresent())
        {
            machineenv.set(null);
            usingmachine.set(false);
            machineready.set(true);
            status.set("Not Needed");
            return false;
        }

        usingmachine.set(true);
        if (!DockerMachine.machinecreated())
        {
            log.info("Creating a new docker machine.");
            status.set("Creating VM");
            if (!DockerMachine.createmachine())
            {
                log.severe("Unable to create a docker machine");
                status.set("VM Creation Failed");
                return false;
            }
        }

        jsch = new JSch();
        return true;
    }


    @Override
    protected void mloop()
    {
        // Make sure machine is running
        if (!DockerMachine.machinerunning())
        {
            machineready.set(false);
            machineenv.set(null);
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
        if ((machineenv.get() == null) || (machineenv.get().get("DOCKER_HOST") == null) || (machineenv.get().get("DOCKER_CERT_PATH") == null))
        {
            machineenv.set(DockerMachine.machineenv());
            if ((machineenv.get() == null) || (machineenv.get().get("DOCKER_HOST") == null) || (machineenv.get().get("DOCKER_CERT_PATH") == null))
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
        try
        {
            Matcher m = hostpattern.matcher(machineenv.get().get("DOCKER_HOST"));
            if (m.find()) { machinehost = m.group(1); }

            if (jsch.getIdentityNames().size() == 0)
                jsch.addIdentity(Paths.get(machineenv.get().get("DOCKER_CERT_PATH"), "id_rsa").toString());

            if ((portforward == null) || (!portforward.isConnected()))
            {
                dbportsready.set(false);
                status.set("Forwarding ports 6432,59432");

                portforward = jsch.getSession("docker", machinehost);
                portforward.setConfig("StrictHostKeyChecking", "no");
                portforward.setConfig("GSSAPIAuthentication",  "no");
                portforward.setConfig("PreferredAuthentications", "publickey");
                portforward.setPortForwardingL("*",        54329, "127.0.0.1", 54329);
                portforward.setPortForwardingL("127.0.0.1", 6432, "127.0.0.1",  6432);
                portforward.connect();

                dbportsready.set(true);
            }

            // This one is the most likely to be blocked by another service, separate it so everything
            // else (i.e. database) can at least connect in the mean time
            if ((port80forward == null) || (!port80forward.isConnected()))
            {
                webportready.set(false);
                status.set("Forwarding port 80");

                port80forward = jsch.getSession("docker", machinehost);
                port80forward.setConfig("StrictHostKeyChecking", "no");
                port80forward.setConfig("GSSAPIAuthentication",  "no");
                port80forward.setConfig("PreferredAuthentications", "publickey");
                port80forward.setPortForwardingL("*", 80, "127.0.0.1", 80);
                port80forward.connect();

                webportready.set(true);
            }
        }
        catch (JSchException jse)
        {
            log.log(Level.INFO, "Error setting up portforwarding: " + jse, jse);
            return;
        }

        // Everything is up at this point
        status.set("Running");
    }

    @Override
    protected void mshutdown()
    {
        if (portforward != null) {
            status.set("Stopping port-forwarding");
            portforward.disconnect();
        }
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
