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
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

import org.wwscc.util.Launcher;
import org.wwscc.util.Logging;
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
    ComposeMonitor cmonitor;
    DataSyncInterface syncviewer = null;
    
    // shared state between threads
    volatile TrayIcon trayIcon;
    volatile boolean readyforcompose, portsforwarded, applicationdone;
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

        readyforcompose = false;
        portsforwarded = false;
        applicationdone = false;
    }
    
    public void startAndWaitForThreads()
    {
        mmonitor = new MachineMonitor();
        mmonitor.start();
        cmonitor = new ComposeMonitor();
        cmonitor.start();
        
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
                new DebugCollector().start();
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
                if (JOptionPane.showConfirmDialog(null, "This will stop the datbase server and web server.  Is that ok?", 
                    "Quit Scorekeeper", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
                {
                    applicationdone = true;
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
                    synchronized (this) { this.wait(ms); }                    
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
            if (ready != readyforcompose)
            {
                readyforcompose = ready;
                cmonitor.poke();
            }
        }
        
        @Override
        protected boolean minit()
        {
            if (!DockerInterface.machinepresent())
            {
                signalready(true);
                mMachineStatus.setLabel("Machine: Not needed");
                mMachineStatus.setEnabled(false);
                return false;
            }

            if (!DockerInterface.machinecreated())
            {
                log.info("Creating a new docker machine.");
                mMachineStatus.setLabel("Machine: Creating VM");
                if (!DockerInterface.createmachine())
                {
                    log.severe("Unable to create a docker machine.  See logs.");
                    return false;
                }
            }
            
            jsch = new JSch();
            machineenv = DockerInterface.machineenv();
            log.finest("dockerenv = " + machineenv);
            return true;
        }
        
        @Override
        protected boolean mloop()
        {
            if (!DockerInterface.machinerunning())
            {
                log.info("Starting the docker machine.");
                mMachineStatus.setLabel("Machine: Starting VM");
                if (!DockerInterface.startmachine())
                {
                    log.info("Unable to start docker machine. See logs.");
                    return false;
                }
                machineenv = DockerInterface.machineenv();
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
    class ComposeMonitor extends Monitor
    {
        Image currentIcon;
        
        public ComposeMonitor()
        {
            super("ComposeController", 5000);
            currentIcon = null;
        }
        
        public boolean minit() 
        { 
            return true; 
        }
        
        public boolean mloop()
        {
            boolean ok = false;
            if (readyforcompose)
            {
                boolean res[] = DockerInterface.ps();
                if (!res[0] || !res[1])
                {
                    mBackendStatus.setLabel("Backend: (re)starting");
                    if (!DockerInterface.up()) 
                        log.info("Unable to start the web and database services. See logs.");
                    else
                        res = DockerInterface.ps();
                }
            
                if (res[0] && res[0])
                    mBackendStatus.setLabel("Backend: Running");
                
                if (res[0] && res[1] && portsforwarded) 
                    ok = true;
            }
            else
            {
                mBackendStatus.setLabel("Backend: Waiting for machine");
            }

            Image next =  ok ? coneok : conewarn;                    
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
            if (!DockerInterface.down())
                log.severe("Unable to stop the web and database services. See logs.");
            mBackendStatus.setLabel("Backend: Stopped");
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
