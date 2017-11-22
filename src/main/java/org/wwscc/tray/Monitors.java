package org.wwscc.tray;

import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.wwscc.storage.Database;
import org.json.simple.JSONObject;
import org.wwscc.dialogs.StatusDialog;
import org.wwscc.util.Discovery;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.Network;
import org.wwscc.util.Prefs;
import org.wwscc.util.Discovery.DiscoveryListener;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

/**
 * The background threads that responsible for monitoring the status of our docker containers
 * and possibly the docker-machine setup (if needed).
 */
public class Monitors
{
    private static final Logger log = Logger.getLogger(Monitors.class.getName());
    public static final String RUNNING = "Running";
    public static final String NOTNEEDED = "Not Needed";


    /**
     * Abstract class to define the basic init, loop/wait and shutdown phases of our monitors
     */
    public static abstract class Monitor extends Thread
    {
        protected final Long ms;
        protected boolean quickrecheck;
        protected StateControl state;

        protected abstract boolean minit();
        protected abstract boolean mloop();
        protected abstract void mshutdown();

        public Monitor(String name, long ms, StateControl state) {
            super(name);
            this.ms = ms;
            this.state = state;
        }

        public synchronized void poke() {
            notify();
        }

        public synchronized void donefornow() {
            try {
                this.wait(ms);
            } catch (InterruptedException ie) {}
        }

        public synchronized void pause(int msec) {
            try {
                this.wait(msec);
            } catch (InterruptedException ie) {}
        }

        @Override
        public void run() {
            if (!minit())
                return;
            while (!state.isApplicationDone()) {
                mloop();
                if (!quickrecheck)
                    donefornow();
                quickrecheck = false;
            }
            mshutdown();
        }
    }

    /**
     * Thread to start machine and monitor port forwarding
     */
    public static class MachineMonitor extends Monitor
    {
        private Map<String,String> machineenv;
        private JSch jsch;
        private Session portforward, port80forward;
        private String machinehost = "192.168.99.100";
        private Pattern hostpattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.\\d+)");

        public MachineMonitor(StateControl state)
        {
            super("MachineMonitor", 10000, state);
            machineenv = null;
        }

        @Override
        protected boolean minit()
        {
            if (!DockerMachine.machinepresent())
            {
                state.signalPortsReady(true);
                state.signalMachineReady(true);
                state.setUsingMachine(false);
                Messenger.sendEvent(MT.MACHINE_STATUS, NOTNEEDED);
                return false;
            }

            state.setUsingMachine(true);
            if (!DockerMachine.machinecreated())
            {
                log.info("Creating a new docker machine.");
                Messenger.sendEvent(MT.MACHINE_STATUS, "Creating VM");
                if (!DockerMachine.createmachine())
                {
                    log.severe("\bUnable to create a docker machine.  See logs.");
                    return false;
                }
            }

            jsch = new JSch();
            return true;
        }


        @Override
        protected boolean mloop()
        {
            // Make sure machine is running
            if (!DockerMachine.machinerunning())
            {
                state.signalMachineReady(false);
                log.info("Starting the docker machine.");
                Messenger.sendEvent(MT.MACHINE_STATUS, "Restarting VM");
                if (!DockerMachine.startmachine())
                {
                    log.severe("\bUnable to start docker machine. See logs.");
                    return false;
                }
                pause(100);
                machineenv = null;
            }

            // Make sure we have a proper environment setup
            if ((machineenv == null) || !machineenv.containsKey("DOCKER_HOST") || !machineenv.containsKey("DOCKER_CERT_PATH"))
            {
                machineenv = DockerMachine.machineenv();
                state.setMachineEnv(machineenv);
                if ((machineenv == null) || !machineenv.containsKey("DOCKER_HOST") || !machineenv.containsKey("DOCKER_CERT_PATH"))
                {
                    log.warning("Unable to load machine env, will try again on next loop");
                    return false;
                }
            }

            // Machine is ready for container execution now
            state.signalMachineReady(true);

            // Make sure port forwarding is up
            try
            {
                Matcher m = hostpattern.matcher(machineenv.get("DOCKER_HOST"));
                if (m.find()) { machinehost = m.group(1); }

                if (jsch.getIdentityNames().size() == 0)
                    jsch.addIdentity(Paths.get(machineenv.get("DOCKER_CERT_PATH"), "id_rsa").toString());

                if ((portforward == null) || (!portforward.isConnected()))
                {
                    state.signalPortsReady(false);
                    Messenger.sendEvent(MT.MACHINE_STATUS, "Forwarding ports 6432,59432 ...");

                    portforward = jsch.getSession("docker", machinehost);
                    portforward.setConfig("StrictHostKeyChecking", "no");
                    portforward.setConfig("GSSAPIAuthentication",  "no");
                    portforward.setConfig("PreferredAuthentications", "publickey");
                    portforward.setPortForwardingL("*",        54329, "127.0.0.1", 54329);
                    portforward.setPortForwardingL("127.0.0.1", 6432, "127.0.0.1",  6432);
                    portforward.connect();
                    state.signalPortsReady(true);
                }

                // This one is the most likely to be blocked by another service, separate it so everything
                // else (i.e. database) can at least connect in the mean time
                if ((port80forward == null) || (!port80forward.isConnected()))
                {
                    Messenger.sendEvent(MT.MACHINE_STATUS, "Forwarding port 80 ...");
                    port80forward = jsch.getSession("docker", machinehost);
                    port80forward.setConfig("StrictHostKeyChecking", "no");
                    port80forward.setConfig("GSSAPIAuthentication",  "no");
                    port80forward.setConfig("PreferredAuthentications", "publickey");
                    port80forward.setPortForwardingL("*", 80, "127.0.0.1", 80);
                    port80forward.connect();
                }
            }
            catch (JSchException jse)
            {
                log.log(Level.INFO, "Error setting up portforwarding: " + jse, jse);
                return false;
            }

            // Everything is up at this point
            Messenger.sendEvent(MT.MACHINE_STATUS, RUNNING);
            return true;
        }

        @Override
        protected void mshutdown()
        {
            Messenger.sendEvent(MT.MACHINE_STATUS, "Disconnecting ...");
            if (portforward != null)
                portforward.disconnect();
            Messenger.sendEvent(MT.MACHINE_STATUS, "Disconnected");
        }
    }



    /**
     * Thread to keep checking our services for status.  It pauses for 5 seconds but can
     * be woken by anyone calling notify on the class object.
     */
    public static class ContainerMonitor extends Monitor implements MessageListener, DataRetrievalInterface
    {
        private Map<String, DockerContainer> containers;
        private Set<String> names;
        private Path toimport;

        public ContainerMonitor(StateControl state)
        {
            super("ContainerMonitor", 5000, state);
            containers = new HashMap<String, DockerContainer>();
            containers.put("db", new DockerContainer.Db());
            containers.put("web", new DockerContainer.Web());
            containers.put("sync", new DockerContainer.Sync());
            names = new HashSet<String>();
            for (DockerContainer c : containers.values())
                names.add(c.getName());
            toimport = null;
            Messenger.register(MT.POKE_SYNC_SERVER, this);
        }

        public boolean minit()
        {
            Messenger.sendEvent(MT.BACKEND_STATUS, "Waiting for machine");
            while (!state.isMachineReady())
                donefornow();

            for (DockerContainer c : containers.values()) {
                Messenger.sendEvent(MT.BACKEND_STATUS, "Init " + c.getName());
                c.setMachineEnv(state.getMachineEnv());
                c.createNetsAndVolumes();
                c.start();
            }

            return true;
        }

        public boolean mloop()
        {
            boolean ok = true;

            if (!state.isMachineReady()) {
                Messenger.sendEvent(MT.BACKEND_STATUS, "Waiting for machine");
                state.signalContainersReady(false);
                return false;
            }

            // interrupt our regular schedule to shutdown and import data
            if (toimport != null)
                importOld();

            // If something isn't running, try and start them now
            Set<String> dead = DockerContainer.finddown(state.getMachineEnv(), names);
            if (dead.size() > 0) {
                ok = false;
                Messenger.sendEvent(MT.BACKEND_STATUS, "Restarting " + dead);
                for (DockerContainer c : containers.values()) {
                    if (dead.contains(c.getName())) {
                        if (!c.start(750)) {
                            log.severe("Unable to start " + c.getName()); // don't send to dialog, noisy
                        } else {
                            quickrecheck = true;
                        }
                    }
                }
            }

            state.signalContainersReady(ok);
            return ok;
        }

        public void mshutdown()
        {
            Messenger.sendEvent(MT.BACKEND_STATUS, "Shutting down");
            if (!DockerContainer.stopAll(containers.values()))
                log.severe("\bUnable to stop the web and database services. See logs.");
            if (state.shouldStopMachine()) {
                // we do this in the container monitor for ease of performing stopAll first
                Messenger.sendEvent(MT.BACKEND_STATUS, "Shutting down machine");
                DockerMachine.stopmachine();
            }
            Messenger.sendEvent(MT.BACKEND_STATUS, "Stopped");
        }

        public void importOld()
        {
            StatusDialog dialog = new StatusDialog();
            dialog.doDialog("Old Data Import", o -> {});
            dialog.setStatus("Preparing to import ...", -1);
            Messenger.sendEvent(MT.BACKEND_STATUS, "Preparing to import");

            List<DockerContainer> nondb = new ArrayList<DockerContainer>();
            nondb.add(containers.get("web"));
            nondb.add(containers.get("sync"));
            DockerContainer.stopAll(nondb);

            dialog.setStatus("Importing ...", -1);
            Messenger.sendEvent(MT.BACKEND_STATUS, "Importing ...");
            log.info("importing "  + toimport);
            boolean success = containers.get("db").importDatabase(toimport);
            toimport = null;

            if (success)
                dialog.setStatus("Import and conversion was successful", 100);
            else
                dialog.setStatus("Import failed, see logs", 100);
        }

        @Override
        public boolean dumpDatabase(Path file, boolean compress)
        {
            return containers.get("db").dumpDatabase(file, compress);
        }

        @Override
        public boolean copyLogs(Path dir)
        {
            return containers.get("db").copyLogs(dir);
        }

        @Override
        public void event(MT type, Object data)
        {
            if (type == MT.POKE_SYNC_SERVER)
            {
                containers.get("sync").poke();
            }
        }

        public void importRequest(Path p)
        {
            toimport = p;
        }
    }



    /**
     * Thread to keep checking pinging the database to cause notifications
     * for the discovery pieces. It can be 'paused' when the database is to be offline.
     */
    public static class MergeStatusMonitor extends Monitor implements DiscoveryListener
    {
        boolean paused;

        public MergeStatusMonitor(StateControl state)
        {
            super("MergeStatusMonitor", 1000, state);
            paused = true; // we start in the 'paused' state
            Messenger.register(MT.DISCOVERY_CHANGE, (type, data) -> updateDiscoverySetting((boolean)data));
        }

        @Override
        public boolean minit()
        {
            while (!state.isBackendReady())
                donefornow();

            // These two should always be there
            Database.d.mergeServerSetLocal(Network.getLocalHostName(), Network.getPrimaryAddress().getHostAddress(), 10);
            Database.d.mergeServerSetRemote(Prefs.getHomeServer(), "", 10);
            Database.d.mergeServerInactivateAll();

            // We only start (or not) the discovery thread once we've set our data into the database so there is something to announce
            updateDiscoverySetting(Prefs.getAllowDiscovery());

            // force an update on start, on the event thread
            Messenger.sendEvent(MT.DATABASE_NOTIFICATION, new HashSet<String>(Arrays.asList("mergeservers")));

            return true;
        }

        @Override
        public boolean mloop()
        {
            // we update with our current address which causes the database to send us a NOTICE event which causes the GUI to update
            if (!paused) {
                Database.d.mergeServerSetLocal(Network.getLocalHostName(), Network.getPrimaryAddress().getHostAddress(), 10);
            }
            return true;
        }

        @Override
        public void mshutdown()
        {
            // Database is already closed at this point, can't do anything else
        }

        public void setPause(boolean b)
        {
            paused = b;
        }

        @SuppressWarnings("unchecked")
        private void updateDiscoverySetting(boolean up)
        {
            if (up)
            {
                JSONObject data = new JSONObject();
                data.put("serverid", Prefs.getServerId().toString());
                data.put("hostname", Network.getLocalHostName());
                Discovery.get().addServiceListener(this);
                Discovery.get().registerService(Prefs.getServerId(), Discovery.DATABASE_TYPE, data);
            }
            else
            {
                Discovery.get().removeServiceListener(this);
                Discovery.get().unregisterService(Prefs.getServerId(), Discovery.DATABASE_TYPE);
            }
        }

        @Override
        public void serviceChange(UUID serverid, String service, JSONObject data, boolean up)
        {
            if (!service.equals(Discovery.DATABASE_TYPE))
                return;
            InetAddress ip = (InetAddress)data.get("ip");
            if (ip.equals(Network.getPrimaryAddress()))
                return;
            System.out.println(ip + ", " + data + ", " + up);
            if (up) {
                Database.d.mergeServerActivate(serverid, (String)data.get("hostname"), ip.getHostAddress());
            } else {
                Database.d.mergeServerDeactivate(serverid);
            }
            Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
        }
    }
}
