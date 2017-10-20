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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

import org.wwscc.storage.Database;
import org.wwscc.storage.PostgresqlDatabase;
import org.wwscc.util.Launcher;
import org.wwscc.util.Logging;
import org.wwscc.util.Resources;


public class TrayMonitor implements ActionListener
{
    private static final Logger log = Logger.getLogger(TrayMonitor.class.getName());
    private static final Image coneok, conewarn;    
    static 
    {
        coneok   = Resources.loadImage("conesmall.png");
        conewarn = Resources.loadImage("conewarn.png");
    }

    // not initialized or started until startAndWaitForThreads
    DataSyncInterface syncviewer = null;
    DockerMonitors.MachineMonitor   mmonitor;
    DockerMonitors.ContainerMonitor cmonitor;
    
    // These are initialized in the constructor
    List<Process> launched;
    Map<String, MenuItem> appMenus;
    MenuItem mBackendStatus, mMachineStatus;
    SharedState state;
    TrayIcon trayIcon;

    public TrayMonitor(String args[])
    {
        if (!SystemTray.isSupported()) 
        {
            log.severe("\bTrayIcon is not supported, unable to run Scorekeeper monitor application.");
            System.exit(-1);
        }

        appMenus = new HashMap<String, MenuItem>();
        launched = new ArrayList<Process>();
        state = new SharedState();
        
        PopupMenu trayPopup = new PopupMenu();        
        newAppItem("DataEntry",        "org.wwscc.dataentry.DataEntry",       trayPopup);
        newAppItem("Registration",     "org.wwscc.registration.Registration", trayPopup);
        newAppItem("ProTimer",         "org.wwscc.protimer.ProSoloInterface", trayPopup);
        newAppItem("ChallengeGUI",     "org.wwscc.challenge.ChallengeGUI",    trayPopup);
        newAppItem("BWTimer",          "org.wwscc.bwtimer.Timer",             trayPopup);
        trayPopup.addSeparator();
        newAppItem("Data Sync",        "datasync",     trayPopup);
        newAppItem("Debug Collection", "debugcollect", trayPopup);

        trayPopup.addSeparator();
        mBackendStatus = new MenuItem("Backend:");
        trayPopup.add(mBackendStatus);
        mMachineStatus = new MenuItem("Machine:");
        trayPopup.add(mMachineStatus);

        trayPopup.addSeparator();
        MenuItem quit = new MenuItem("Quit");
        quit.addActionListener(this);
        trayPopup.add(quit);

        Font f = UIManager.getDefaults().getFont("MenuItem.font").deriveFont(Font.ITALIC).deriveFont(14.0f);
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
            log.severe("\bFailed to create TrayIcon: " + e);
            System.exit(-2);
        }        
    }

    /**
     * This actually starts the threads in the program and then waits for
     * them to finish.  We can't used thread.join as it breaks thread.notify
     * behavior during regular runtime.
     */
    public void startAndWaitForThreads()
    {
        cmonitor = new DockerMonitors.ContainerMonitor(state);
        cmonitor.start();
        mmonitor = new DockerMonitors.MachineMonitor(state);
        mmonitor.start();

        try {
            while (mmonitor.isAlive() || cmonitor.isAlive())
                Thread.sleep(300);
        } catch (InterruptedException ie) {
            log.warning("Exiting due to interuption: " + ie);
        }
    }
    
    private void newAppItem(String initial, String cmd, Menu parent)
    {
        MenuItem m = new MenuItem(initial);
        m.setActionCommand(cmd);
        m.addActionListener(this);
        m.setEnabled(false);
        parent.add(m);
        appMenus.put(cmd, m);
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
                if (syncviewer != null)
                    syncviewer.setVisible(true);
                break;

            case "Quit":
                state.shutdownRequest();
                break;

            default:
                Process p = Launcher.launchExternal(cmd, null);
                if (p != null)
                	launched.add(p);
        }
    }

    /**
     * Encapsulate the state shared between our monitors so we can decide when or
     * when not to take further action.
     */
    class SharedState implements TrayStateInterface
    {
    	private volatile Map<String, String> _machineenv;
        private volatile Image _currentIcon;
        private volatile boolean _machineready, _portsforwarded, _applicationdone;
        public SharedState()
        {
        	_machineready    = false;
            _portsforwarded  = false;
            _applicationdone = false;
        }
        
        public boolean isApplicationDone()          { return _applicationdone; }
        public boolean arePortsForwarded()          { return _portsforwarded;  }
        public boolean isMachineReady()             { return _machineready;    }
		public Map<String, String> getMachineEnv()  { return _machineenv; }
		
        public void signalPortsReady(boolean ready)        { _portsforwarded = ready; }
		public void setMachineEnv(Map<String, String> env) { _machineenv = env; }
		public void setMachineStatus(String status)        { mMachineStatus.setLabel(status); }
		public void setBackendStatus(String status)        { mBackendStatus.setLabel(status); }
        
        public void signalMachineReady(boolean ready)
        {
            if (ready != _machineready)
            {
            	_machineready = ready;
                cmonitor.poke();
            }
        }
        
        public void signalComposeReady(boolean ready) 
        {
	        Image next = (ready && _portsforwarded) ? coneok : conewarn;                    
	        if (next != _currentIcon) // the following should only occur on state change 
	        {
	            if (next == coneok) 
	            {
	                mBackendStatus.setLabel("Backend: Waiting for DB");
	        	    PostgresqlDatabase.waitUntilUp();
		        	Database.openPublic(true);
		            syncviewer = new DataSyncInterface();
		        	for (MenuItem m : appMenus.values())
		        		m.setEnabled(true);
	            }
	            trayIcon.setImage(next); 
	            _currentIcon = next;
	        }

            if (ready)
                mBackendStatus.setLabel("Backend: Running");
        }
                
        public void shutdownRequest()
        {
            if (_applicationdone) {
                log.warning("User force quiting.");
                System.exit(-1);
            }
            
            if (JOptionPane.showConfirmDialog(null, "This will stop all applications including the database and web server.  Is that ok?", 
                "Quit Scorekeeper", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
            	return;
            	
            _applicationdone = true;
            if (syncviewer != null)
                syncviewer.shutdown();
            Database.d.close();
            mmonitor.poke();
            cmonitor.poke();
            for (Process p : launched) {
            	if (p.isAlive())
            		p.destroy();
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
