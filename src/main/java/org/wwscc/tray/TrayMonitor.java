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
    
    JSch jsch;
    Session portforward;
    
    MenuItem mBackendStatus, mMachineStatus;
    MachineController mmonitor;
    ComposeController cmonitor;
    boolean readyforcompose, applicationdone;

    Image coneok, conewarn;
    TrayIcon trayIcon;
    PopupMenu trayPopup;
    String cmdline[];
    
    public TrayMonitor(String args[])
    {
        if (!SystemTray.isSupported()) 
        {
            log.severe("TrayIcon is not supported, unable to run Scorekeeper monitor application.");
            System.exit(-1);
        }
                
        cmdline = args;
        trayPopup   = new PopupMenu();
        
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

        coneok   = Resources.loadImage("conesmall.png");
        conewarn = Resources.loadImage("conewarn.png");
        
        trayIcon = new TrayIcon(conewarn, "Scorekeeper Monitor", trayPopup);
        trayIcon.setImageAutoSize(true);
        
        // Force an update check when opening the context menu
        trayIcon.addMouseListener(new MouseAdapter() {
            private void docheck(MouseEvent e) { if (e.isPopupTrigger()) { synchronized (cmonitor) { cmonitor.notify(); }}}
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

        jsch = new JSch();        
        readyforcompose = false;
        applicationdone = false;
        mmonitor = new MachineController();
        mmonitor.start();
        cmonitor = new ComposeController();
        cmonitor.start();
    }
    
    public void waitforthreads()
    {
        try {
            mmonitor.join();
            cmonitor.join();
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
                    synchronized (mmonitor) { mmonitor.notify(); }
                    synchronized (cmonitor) { cmonitor.notify(); }
                }
                break;
                
            default:
                Launcher.launchExternal(cmd, cmdline);
        }
    }    
    
    /**
     * Thread to start machine and monitor port forwarding
     */
    class MachineController extends Thread
    {
        Map<String,String> machineenv;

        public MachineController()
        {
            super("MachineController");
            machineenv = null;
            readyforcompose = false;
        }
        
        @Override
        public void run()
        {
            if (!DockerInterface.machinepresent())
            {
                signalready(true);
                mMachineStatus.setLabel("Machine: Not needed");
                mMachineStatus.setEnabled(false);
                return;
            }

            while (!applicationdone) 
            {
                try 
                {
                    checkmachine();
                    synchronized (this) { this.wait(10000); }                    
                } 
                catch (InterruptedException e) {}
            }

            mMachineStatus.setLabel("Machine: Disconnecting");
            if (portforward != null)
                portforward.disconnect();
            
            mMachineStatus.setLabel("Machine: Disconnected");
        }
        
        private void signalready(boolean ready)
        {
            if (ready != readyforcompose)
            {
                readyforcompose = ready;
                synchronized (cmonitor) { cmonitor.notify(); }
            }
        }
        
        private boolean checkmachine()
        {
            if (!DockerInterface.machinecreated())
            {
                log.info("Creating a new docker machine.");
                mMachineStatus.setLabel("Machine: Creating VM");
                if (!DockerInterface.createmachine())
                {
                    log.info("Unable to create a docker machine.  See logs.");
                    return false;
                }
            }
                    
            if (!DockerInterface.machinerunning())
            {
                log.info("Starting the docker machine.");
                mMachineStatus.setLabel("Machine: Starting VM");
                if (!DockerInterface.startmachine())
                {
                    log.info("Unable to start docker machine. See logs.");
                    return false;
                }

            }
            
            // stay up to date just in case, compose can start before port forwarding is up
            machineenv = DockerInterface.machineenv();
            log.finest("dockerenv = " + machineenv);
            signalready(true);
            
            try 
            {
                if (jsch.getIdentityNames().size() == 0)
                {
                    jsch.addIdentity(Paths.get(machineenv.get("DOCKER_CERT_PATH"), "id_rsa").toString());
                }
                
                if ((portforward == null) || (!portforward.isConnected()))
                {
                    mMachineStatus.setLabel("Machine: Starting port forwarding ...");
                    
                    String host = "192.168.99.100";
                    Matcher m = Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.\\d+)").matcher(machineenv.get("DOCKER_HOST"));
                    if (m.find()) { host = m.group(1); }
            
                    portforward = jsch.getSession("docker", host);
                    portforward.setConfig("StrictHostKeyChecking", "no");
                    portforward.setConfig("GSSAPIAuthentication", "no");
                    portforward.setConfig("PreferredAuthentications", "publickey");
                    portforward.setPortForwardingL("*",           80, "127.0.0.1",    80);
                    portforward.setPortForwardingL("*",        54329, "127.0.0.1", 54329);
                    portforward.setPortForwardingL("127.0.0.1", 6432, "127.0.0.1",  6432);
                    portforward.connect();
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
    }
    

    /**
     * Thread to keep checking our services for status.  It pauses for 5 seconds but can
     * be woken by anyone calling notify on the class object.
     */
    class ComposeController extends Thread
    {
        Image currentIcon;
        
        public ComposeController()
        {
            super("ComposeController");
            currentIcon = null;
        }
        
        @Override
        public void run()
        {
            while (!applicationdone) 
            {
                try 
                {
                    checkcompose();
                    synchronized (this) { this.wait(5000); }                    
                } 
                catch (InterruptedException e) {}
            }
            
            mBackendStatus.setLabel("Backend: Shutting down");
            if (!DockerInterface.down())
            {
                log.severe("Unable to stop the web and database services. See logs.");
            }
            
            mBackendStatus.setLabel("Backend: Stopped");
        }
        
        public void checkcompose()
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
            
                if (res[0] && res[1]) 
                {
                    ok = true;
                    mBackendStatus.setLabel("Backend: Running");
                }
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
        tm.waitforthreads();
        System.exit(0);
    }
}
