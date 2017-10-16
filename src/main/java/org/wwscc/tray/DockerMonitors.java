package org.wwscc.tray;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class DockerMonitors 
{
    private static final Logger log = Logger.getLogger(DockerMonitors.class.getName());

    /**
     * Abstract class to define the basic init, loop/wait and shutdown phases of our monitors
     */
    public static abstract class Monitor extends Thread
    {
        protected final Long ms;
        protected boolean quickrecheck;
        protected TrayStateInterface state;
         
        protected abstract boolean minit();
        protected abstract boolean mloop();
        protected abstract void mshutdown();
        
        public Monitor(String name, long ms, TrayStateInterface state) { 
        	super(name); 
        	this.ms = ms;
        	this.state = state;
        }
        
        public synchronized void poke() { 
        	notify(); 
        }
        
        @Override
        public void run() {
            if (!minit())
                return;
            while (!state.isApplicationDone()) {
                try {
                    mloop();
                    if (!quickrecheck)
                        synchronized (this) { this.wait(ms); }
                    quickrecheck = false;
                } catch (InterruptedException e) {}
            }
            mshutdown();
        }
    }

    /**
     * Thread to start machine and monitor port forwarding
     */
    public static class MachineMonitor extends Monitor
    {
        Map<String,String> machineenv;
        JSch jsch;
        Session portforward;

        public MachineMonitor(TrayStateInterface state)
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
                state.setMachineStatus("Machine: Not needed");
                return false;
            }

            if (!DockerMachine.machinecreated())
            {
                log.info("Creating a new docker machine.");
                state.setMachineStatus("Machine: Creating VM");
                if (!DockerMachine.createmachine())
                {
                    log.severe("Unable to create a docker machine.  See logs.");
                    return false;
                }
            }

            jsch = new JSch();
            machineenv = DockerMachine.machineenv();
            state.setMachineEnv(machineenv);
            return true;
        }

        @Override
        protected boolean mloop()
        {
            if (!DockerMachine.machinerunning())
            {
                log.info("Starting the docker machine.");
                state.setMachineStatus("Machine: Starting VM");
                if (!DockerMachine.startmachine())
                {
                    log.info("Unable to start docker machine. See logs.");
                    return false;
                }
                machineenv = DockerMachine.machineenv();
                state.setMachineEnv(machineenv);
            }

            state.signalMachineReady(true);

            try 
            {
                if ((portforward == null) || (!portforward.isConnected()))
                {
                    state.signalPortsReady(false);
                    state.setMachineStatus("Machine: Starting port forwarding ...");

                    String host = "192.168.99.100";
                    Matcher m = Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.\\d+)").matcher(machineenv.get("DOCKER_HOST"));
                    if (m.find()) { host = m.group(1); }

                    if (jsch.getIdentityNames().size() == 0)
                        jsch.addIdentity(Paths.get(machineenv.get("DOCKER_CERT_PATH"), "id_rsa").toString());            

                    portforward = jsch.getSession("docker", host);
                    portforward.setConfig("StrictHostKeyChecking", "no");
                    portforward.setConfig("GSSAPIAuthentication",  "no");
                    portforward.setConfig("PreferredAuthentications", "publickey");
                    portforward.setPortForwardingL("*",           80, "127.0.0.1",    80);
                    portforward.setPortForwardingL("*",        54329, "127.0.0.1", 54329);
                    portforward.setPortForwardingL("127.0.0.1", 6432, "127.0.0.1",  6432);
                    portforward.connect();
                    state.signalPortsReady(true);
                }
            } 
            catch (JSchException jse) 
            {
                log.log(Level.INFO, "Error setting up portforwarding: " + jse, jse);
                return false;
            }

            state.setMachineStatus("Machine: Running");
            return true;
        }

        @Override
        protected void mshutdown()
        {
        	state.setMachineStatus("Machine: Disconnecting ...");
            if (portforward != null)
                portforward.disconnect();
            state.setMachineStatus("Machine: Disconnected");
        }
    }


    /**
     * Thread to keep checking our services for status.  It pauses for 5 seconds but can
     * be woken by anyone calling notify on the class object.
     */
    public static class ContainerMonitor extends Monitor implements MessageListener
    {
        Map<String, DockerContainer> containers;
        Set<String> names;

        public ContainerMonitor(TrayStateInterface state)
        {
            super("ContainerMonitor", 5000, state);
            containers = new HashMap<String, DockerContainer>();
            containers.put("db", new DockerContainer.Db());
            containers.put("web", new DockerContainer.Web());
            containers.put("sync", new DockerContainer.Sync());
            names = new HashSet<String>();
            for (DockerContainer c : containers.values())
                names.add(c.getName());
            Messenger.register(MT.POKE_SYNC_SERVER, this);
        }

        public boolean minit() 
        {
        	state.setBackendStatus("Backend: Waiting for machine");
            while (!state.isMachineReady()) {
                try {
                    synchronized (this) { this.wait(ms); }
                } catch (InterruptedException ie) {}
            }


            for (DockerContainer c : containers.values()) {
            	state.setBackendStatus("Backend: Init " + c.getName());
                c.setMachineEnv(state.getMachineEnv());
                c.createNetsAndVolumes();
                c.start();
            }

            return true; 
        }

        public boolean mloop()
        {
            boolean ok = true;
            Set<String> dead = DockerContainer.finddown(state.getMachineEnv(), names);

            // Something isn't running, try and start them now            
            if (dead.size() > 0) {
                ok = false;
                state.setBackendStatus("Backend: (re)starting " + dead);
                for (DockerContainer c : containers.values()) {
                    if (dead.contains(c.getName())) {
                        if (!c.start()) {
                            log.info("Unable to start " + c.getName() + ". See logs.");
                        } else {
                            quickrecheck = true;
                        }
                    }
                }
            }

            state.signalComposeReady(ok);
            return ok;
        }

        public void mshutdown()
        {
        	state.setBackendStatus("Backend: Shutting down");
            if (!DockerContainer.stopAll(containers.values()))
                log.severe("\bUnable to stop the web and database services. See logs.");
            state.setBackendStatus("Backend: Stopped");
        }

		@Override
		public void event(MT type, Object data) {
			if (type == MT.POKE_SYNC_SERVER) {
				containers.get("sync").poke();
			}
		}
    }
}