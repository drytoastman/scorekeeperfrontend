/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.tray;

import java.awt.AWTException;
import java.awt.Font;
import java.awt.Image;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

import org.wwscc.storage.Database;
import org.wwscc.util.Launcher;
import org.wwscc.util.Logging;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.Resources;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;


public class TrayMonitor implements ActionListener
{
    private static final Logger log = Logger.getLogger(TrayMonitor.class.getName());
    private static final Image coneok, conewarn;    
    static 
    {
        coneok   = Resources.loadImage("conesmall.png");
        conewarn = Resources.loadImage("conewarn.png");
    }

    // Threads to run/monitor docker-machine and docker-compose
    MachineMonitor mmonitor;
    ContainerMonitor cmonitor;
    DataSyncInterface syncviewer = null;
    DatabaseDiscovery discovery;


    // shared state between threads
    volatile TrayIcon trayIcon;
    volatile boolean readyforcontainers, portsforwarded, applicationdone;
    volatile MenuItem mBackendStatus, mMachineStatus;

    public TrayMonitor(String args[])
    {
        if (!SystemTray.isSupported()) 
        {
            log.severe("TrayIcon is not supported, unable to run Scorekeeper monitor application.");
            System.exit(-1);
        }

        PopupMenu trayPopup   = new PopupMenu();        
        newMenuItem("DataEntry",        "org.wwscc.dataentry.DataEntry",       trayPopup);
        newMenuItem("Registration",     "org.wwscc.registration.Registration", trayPopup);
        newMenuItem("ProTimer",         "org.wwscc.protimer.ProSoloInterface", trayPopup);
        newMenuItem("BWTimer",          "org.wwscc.bwtimer.Timer",             trayPopup);
        newMenuItem("ChallengeGUI",     "org.wwscc.challenge.ChallengeGUI",    trayPopup);
        newMenuItem("Data Sync",        "datasync",     trayPopup);
        newMenuItem("Debug Collection", "debugcollect", trayPopup);

        trayPopup.addSeparator();
        mBackendStatus = new MenuItem("Backend:");
        trayPopup.add(mBackendStatus);
        mMachineStatus = new MenuItem("Machine:");
        trayPopup.add(mMachineStatus);

        trayPopup.addSeparator();
        newMenuItem("Quit", "quit", trayPopup);

        Font f = UIManager.getDefaults().getFont("MenuItem.font").deriveFont(Font.ITALIC);
        mMachineStatus.setFont(f);
        mBackendStatus.setFont(f);

        trayIcon = new TrayIcon(conewarn, "Scorekeeper Monitor", trayPopup);
        trayIcon.setImageAutoSize(true);

        // Force an update check when opening the context menu
        trayIcon.addMouseListener(new MouseAdapter() {
            private void docheck(MouseEvent e) { if (e.isPopupTrigger()) { cmonitor.poke(); }}
            @Override
            public void mouseReleased(MouseEvent e) { docheck(e); }
            @Override
            public void mousePressed(MouseEvent e)  { docheck(e); }
        });

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            log.severe("Failed to create TrayIcon: " + e);
            System.exit(-2);
        }

        readyforcontainers = false;
        portsforwarded = false;
        applicationdone = false;
    }

    public void startAndWaitForThreads()
    {
        Database.openPublic(true);

        cmonitor = new ContainerMonitor();
        cmonitor.start();
        mmonitor = new MachineMonitor();
        mmonitor.start();
        new Thread(new Runnable() {
            @Override public void run() {
                discovery = new DatabaseDiscovery();
            }
         }).start();

        try {
            while (mmonitor.isAlive() || cmonitor.isAlive())
                Thread.sleep(300);
        } catch (InterruptedException ie) {
            log.warning("Exiting due to interuption: " + ie);
        }
    }

    private MenuItem newMenuItem(String initial, String cmd, Menu parent)
    {
        MenuItem m = new MenuItem(initial);
        m.setActionCommand(cmd);
        m.addActionListener(this);
        parent.add(m);
        return m;
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        String cmd = e.getActionCommand();
        switch (cmd)
        {
            case "debugcollect":
                new DebugCollector(cmonitor.containers.get("db")).start();
                break;

            case "datasync":
                if (syncviewer == null)
                    syncviewer = new DataSyncInterface();
                syncviewer.setVisible(true);
                break;

            case "quit":
                if (applicationdone) 
                {
                    log.info("User force quiting.");
                    System.exit(-1);
                }
                if (JOptionPane.showConfirmDialog(null, "This will stop the database and web server.  Is that ok?", 
                    "Quit Scorekeeper", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
                {
                    applicationdone = true;
                    if (discovery != null)
                        discovery.shutdown();
                    if (syncviewer != null)
                        syncviewer.shutdown();
                    mmonitor.poke();
                    cmonitor.poke();
                }
                break;

            default:
                Launcher.launchExternal(cmd, null);
        }
    }

    abstract class Monitor extends Thread
    {
        protected final Long ms;
        protected boolean quickrecheck;
        public Monitor(String name, long ms) { super(name); this.ms = ms; } 
        protected abstract boolean minit();
        protected abstract boolean mloop();
        protected abstract void mshutdown();
        public synchronized void poke() { notify(); }
        @Override
        public void run() {
            if (!minit())
                return;
            while (!applicationdone) {
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
    class MachineMonitor extends Monitor
    {
        Map<String,String> machineenv;
        JSch jsch;
        Session portforward;

        public MachineMonitor()
        {
            super("MachineMonitor", 10000);
            machineenv = null;
        }

        protected void signalready(boolean ready)
        {
            if (ready != readyforcontainers)
            {
                readyforcontainers = ready;
                cmonitor.poke();
            }
        }

        @Override
        protected boolean minit()
        {
            if (!DockerMachine.machinepresent())
            {
                signalready(true);
                mMachineStatus.setLabel("Machine: Not needed");
                mMachineStatus.setEnabled(false);
                portsforwarded = true; // pf not needed, flag needs to be true to remove warning icon
                return false;
            }

            if (!DockerMachine.machinecreated())
            {
                log.info("Creating a new docker machine.");
                mMachineStatus.setLabel("Machine: Creating VM");
                if (!DockerMachine.createmachine())
                {
                    log.severe("Unable to create a docker machine.  See logs.");
                    return false;
                }
            }

            jsch = new JSch();
            machineenv = DockerMachine.machineenv();
            log.finest("dockerenv = " + machineenv);
            return true;
        }

        @Override
        protected boolean mloop()
        {
            if (!DockerMachine.machinerunning())
            {
                log.info("Starting the docker machine.");
                mMachineStatus.setLabel("Machine: Starting VM");
                if (!DockerMachine.startmachine())
                {
                    log.info("Unable to start docker machine. See logs.");
                    return false;
                }
                machineenv = DockerMachine.machineenv();
                log.finest("dockerenv = " + machineenv);
            }

            signalready(true);

            try 
            {
                if ((portforward == null) || (!portforward.isConnected()))
                {
                    portsforwarded = false;
                    mMachineStatus.setLabel("Machine: Starting port forwarding ...");

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
                    portsforwarded = true;
                }
            } 
            catch (JSchException jse) 
            {
                log.log(Level.INFO, "Error setting up portforwarding: " + jse, jse);
                return false;
            }

            mMachineStatus.setLabel("Machine: Running");
            return true;
        }

        @Override
        protected void mshutdown()
        {
            mMachineStatus.setLabel("Machine: Disconnecting");
            if (portforward != null)
                portforward.disconnect();
            mMachineStatus.setLabel("Machine: Disconnected");
        }
    }


    /**
     * Thread to keep checking our services for status.  It pauses for 5 seconds but can
     * be woken by anyone calling notify on the class object.
     */
    class ContainerMonitor extends Monitor implements MessageListener
    {
        Image currentIcon;
        Map<String, DockerContainer> containers;
        Set<String> names;

        public ContainerMonitor()
        {
            super("ContainerMonitor", 5000);
            containers = new HashMap<String, DockerContainer>();
            containers.put("db", new DockerContainer.Db());
            containers.put("web", new DockerContainer.Web());
            containers.put("sync", new DockerContainer.Sync());
            names = new HashSet<String>();
            for (DockerContainer c : containers.values())
                names.add(c.getName());
            currentIcon = null;
            Messenger.register(MT.POKE_SYNC_SERVER, this);
        }

        public boolean minit() 
        {
            mBackendStatus.setLabel("Backend: Waiting for machine");
            while (!readyforcontainers) {
                try {
                    synchronized (this) { this.wait(ms); }
                } catch (InterruptedException ie) {}
            }


            for (DockerContainer c : containers.values()) {
                mBackendStatus.setLabel("Backend: Init " + c.getName());
                c.setMachineEnv(mmonitor.machineenv);
                c.createNetsAndVolumes();
                c.start();
            }

            return true; 
        }

        public boolean mloop()
        {
            boolean ok = true;
            Set<String> dead = DockerContainer.finddown(mmonitor.machineenv, names);

            // Something isn't running, try and start them now            
            if (dead.size() > 0) {
                ok = false;
                mBackendStatus.setLabel("Backend: (re)starting " + dead);
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

            if (ok)
                mBackendStatus.setLabel("Backend: Running");

            Image next = (ok & portsforwarded) ? coneok : conewarn;                    
            if (next != currentIcon) 
            {
                trayIcon.setImage(next); 
                currentIcon = next;
            }
            return ok;
        }

        public void mshutdown()
        {
            mBackendStatus.setLabel("Backend: Shutting down");
            boolean ok = true;
            for (DockerContainer c : containers.values())
                ok = ok & c.stop();
            if (!ok)
                log.severe("Unable to stop the web and database services. See logs.");
            mBackendStatus.setLabel("Backend: Stopped");
        }

		@Override
		public void event(MT type, Object data) {
			if (type == MT.POKE_SYNC_SERVER) {
				containers.get("sync").poke();
			}
		}
    }    


    /**
     * Main entry point.
     * @param args passed to any launched application, ignored otherwise
     */
    public static void main(String args[])
    {
        System.setProperty("swing.defaultlaf", UIManager.getSystemLookAndFeelClassName());
        System.setProperty("program.name", "TrayMonitor");
        Logging.logSetup("traymonitor");
        TrayMonitor tm = new TrayMonitor(args);
        tm.startAndWaitForThreads();
        System.exit(0);
    }
}
