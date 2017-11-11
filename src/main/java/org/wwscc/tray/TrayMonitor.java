/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.tray;

import java.awt.AWTException;
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
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;
import org.wwscc.storage.Database;
import org.wwscc.storage.PostgresqlDatabase;
import org.wwscc.util.AppSetup;
import org.wwscc.util.Prefs;
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
    DockerMonitors.MachineMonitor   mmonitor;
    DockerMonitors.ContainerMonitor cmonitor;
    
    // These are initialized in the constructor
    List<Process> launched;
    Map<String, MenuItem> appMenus;
    MenuItem mBackendStatus, mMachineStatus;
    StateMachine state;
    DataSyncWindow syncviewer;
    TrayIcon trayIcon;
    FileLock filelock;

    public TrayMonitor(String args[])
    {
        if (!SystemTray.isSupported()) 
        {
            log.severe("\bTrayIcon is not supported, unable to run Scorekeeper monitor application.");
            System.exit(-1);
        }
        
        if (!ensureSingleton())
        {
            log.warning("Another TrayMonitor is running, quitting now.");
            System.exit(-1);
        }

        appMenus = new HashMap<String, MenuItem>();
        launched = new ArrayList<Process>();
        state = new StateMachine();
        syncviewer = new DataSyncWindow();
        
        PopupMenu trayPopup = new PopupMenu();        
        newAppItem("DataEntry",        "org.wwscc.dataentry.DataEntry",       trayPopup, false);
        newAppItem("Registration",     "org.wwscc.registration.Registration", trayPopup, false);
        newAppItem("ProTimer",         "org.wwscc.protimer.ProSoloInterface", trayPopup, false);
        newAppItem("ChallengeGUI",     "org.wwscc.challenge.ChallengeGUI",    trayPopup, false);
        newAppItem("BWTimer",          "org.wwscc.bwtimer.Timer",             trayPopup, false);
        trayPopup.addSeparator();
        newAppItem("Data Sync",        "datasync",     trayPopup, false);
        newAppItem("Debug Collection", "debugcollect", trayPopup, true);

        trayPopup.addSeparator();
        mBackendStatus = new MenuItem("Backend:");
        trayPopup.add(mBackendStatus);
        mMachineStatus = new MenuItem("Machine:");
        trayPopup.add(mMachineStatus);

        trayPopup.addSeparator();
        MenuItem quit = new MenuItem("Quit");
        quit.addActionListener(this);
        trayPopup.add(quit);

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
     * Use a local file lock to make sure that we are the only tray monitor running.
     * @return true if we are the only running traymonitor and can continue, false if we should stop
     */
    private boolean ensureSingleton()
    {
        try {
            filelock = FileChannel.open(Prefs.getLockFilePath("traymonitor"), StandardOpenOption.CREATE, StandardOpenOption.WRITE).tryLock();
            if (filelock == null) throw new IOException("File already locked");
        } catch (Exception e) {
            if (JOptionPane.showConfirmDialog(null, "<html>"+ e + "<br/><br/>" + 
                        "Unable to lock TrayMonitor access. " +  
                        "This usually indicates that another copy of TrayMonitor is<br/>already running and only one should be running at a time. " +
                        "It is also possible that TrayMonitor<br/>did not exit cleanly last time and the lock is just left over.<br/><br/>" +
                        "Click No to quit now or click Yes to start anyways.<br/>&nbsp;<br/>",
                        "Continue With Launch", JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION)
                return false;
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override public void run() {
                try {
                    if (filelock != null)
                        filelock.release();
                } catch (IOException e) {}
        }});
        
        return true;
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
            log.warning("\bTrayMonitor exiting due to interuption: " + ie);
        }
    }
    
    
    private void newAppItem(String initial, String cmd, Menu parent, boolean enabled)
    {
        MenuItem m = new MenuItem(initial);
        m.setActionCommand(cmd);
        m.addActionListener(this);
        m.setEnabled(enabled);
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
                new DebugCollector(cmonitor).start();
                break;

            case "datasync":
                syncviewer.setVisible(true);
                syncviewer.toFront();
                break;

            case "Quit":
                state.shutdownRequest();
                break;

            default:
                Process p = launchExternal(cmd);
                if (p != null)
                	launched.add(p);
        }
    }

    /**
     * Encapsulate the state shared between our monitors so we can decide when or
     * when not to take further action.
     */
    class StateMachine implements TrayStateInterface
    {
    	private volatile Map<String, String> _machineenv;
        private volatile Image _currentIcon;
        private volatile boolean _machineready, _portsforwarded, _shutdownrequested, _applicationdone, _shutdownmachine, _usingmachine;
        private volatile String _lastMachineStatus, _lastBackendStatus;
        public StateMachine()
        {
        	_machineready      = false;
            _portsforwarded    = false;
            _shutdownrequested = false;
            _applicationdone   = false;
            _shutdownmachine   = false;
            _lastMachineStatus = "";
            _lastBackendStatus = "";
        }
        
        public boolean isApplicationDone()          { return _applicationdone; }
        public boolean shouldStopMachine()          { return _shutdownmachine; }
        public boolean arePortsForwarded()          { return _portsforwarded;  }
        public boolean isMachineReady()             { return _machineready;    }
		public Map<String, String> getMachineEnv()  { return _machineenv; }
		
        public void signalPortsReady(boolean ready)        { _portsforwarded = ready; }
		public void setMachineEnv(Map<String, String> env) { _machineenv = env; }
		
		public void setUsingMachine(boolean using)         
		{ 
			_usingmachine = using;
			if (!_usingmachine) {
				mMachineStatus.getParent().remove(mMachineStatus);
			}
		}
		
	    public void setMachineStatus(String status)
	    { 
	        mMachineStatus.setLabel("Machine: " + status);
            mMachineStatus.setEnabled(!status.equals(DockerMonitors.RUNNING));
            if (!_lastMachineStatus.equals(status)) {
	            log.info("Machine status changed to " + status);
	            _lastMachineStatus = status;
	        }
	    }
	    
	    public void setBackendStatus(String status)
	    {
	        mBackendStatus.setLabel("Backend: " + status);
	        mBackendStatus.setEnabled(!status.equals(DockerMonitors.RUNNING));
            if (!_lastBackendStatus.equals(status)) {
                log.info("Backend status changed to " + status);
                _lastBackendStatus = status;
            }
	    }
        
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
	                setBackendStatus("Waiting for Database");
	        	    PostgresqlDatabase.waitUntilUp();
		        	Database.openPublic(true);
		        	syncviewer.startQueryThread();
		        	for (MenuItem m : appMenus.values())
		        		m.setEnabled(true);
	            }
	            trayIcon.setImage(next); 
	            _currentIcon = next;
	        }
        }
                
        public void shutdownRequest()
        {
            if (_shutdownrequested) 
            {   // Quit called a second time while shutting down, just quit now
                log.warning("User force quiting.");
                System.exit(-1);
            }
            
            if (_usingmachine) 
            {
                Object[] options = { "Shutdown Scorekeeper", "Shutdown Scorekeeper and VM", "Cancel" };
                int result = JOptionPane.showOptionDialog(null, "<html>" + 
                        "This will stop all applications including the database and web server.<br/>" +
                        "You can also shutdown the Virtual Machine if you are shutting down/logging off.<br/>&nbsp;", 
                        "Shutdown Scorekeeper", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
                if (result == 2)
                    return;
                _shutdownmachine = (result == 1);
            }
            else
            {            
                Object[] options = { "Shutdown Scorekeeper", "Cancel" };
                int result = JOptionPane.showOptionDialog(null, "<html>" + 
                        "This will stop all applications including the database and web server.", 
                        "Shutdown Scorekeeper", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
                if (result == 1)
                    return;
            }

            // first shutdown all the things with database connections
            _shutdownrequested = true;
            for (Process p : launched) {
                if (p.isAlive())
                    p.destroy();
            }
            syncviewer.stopQueryThread();
            Database.d.close();

            // second backup the database
            cmonitor.dumpDatabase(Prefs.getBackupDirectory().resolve(new SimpleDateFormat("yyyy-MM-dd_HH-mm").format(new Date()) + ".pgdump"));            

            // note the shutdown flag and wake up our monitors to finish up
            _applicationdone = true;
            mmonitor.poke();
            cmonitor.poke();
        }
    }
    
    
    /**
     * Called to launch an application as a new process
     * @param app the name of the class with a main to execute
     * @return the Process object for the launched application
     */
    public static Process launchExternal(String app)
    {
        try {
            ArrayList<String> cmd = new ArrayList<String>();
            if (System.getProperty("os.name").split("\\s")[0].equals("Windows"))
                cmd.add("javaw");
            else
                cmd.add("java");
            cmd.add("-cp");
            cmd.add(System.getProperty("java.class.path"));
            cmd.add(app);
            log.info(String.format("Running %s", cmd));
            ProcessBuilder starter = new ProcessBuilder(cmd);
            starter.redirectErrorStream(true);
            starter.redirectOutput(Redirect.appendTo(Prefs.getLogDirectory().resolve("jvmlaunches.log").toFile()));
            Process p = starter.start();
            Thread.sleep(1000);
            if (!p.isAlive()) {
                throw new Exception("Process not alive after 1 second");
            }
            return p;
        } catch (Exception e) {
            log.log(Level.SEVERE, String.format("\bFailed to launch %s",  app), e);
            return null;
        }
    }
    
    
    /**
     * Main entry point.
     * @param args passed to any launched application, ignored otherwise
     */
    public static void main(String args[])
    {
        AppSetup.appSetup("traymonitor");
        TrayMonitor tm = new TrayMonitor(args);
        tm.startAndWaitForThreads();
        System.exit(0);
    }
}
