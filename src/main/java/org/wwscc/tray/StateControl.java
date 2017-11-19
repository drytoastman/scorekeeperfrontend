package org.wwscc.tray;

import java.awt.Window;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.swing.FocusManager;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import org.wwscc.storage.Database;
import org.wwscc.storage.PostgresqlDatabase;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;

/**
 * Encapsulate the state shared between components so we can decide when or
 * when not to take further action.
 */
public class StateControl
{
    private static final Logger log = Logger.getLogger(Monitors.class.getName());

    private volatile Map<String, String> _machineenv;
    private volatile boolean _machineready, _portsforwarded, _shutdownrequested, _applicationdone, _shutdownmachine, _usingmachine, _backendready;

    List<Process> launched;
    Monitors.MachineMonitor   mmonitor;
    Monitors.ContainerMonitor cmonitor;
    Monitors.MergeStatusMonitor pmonitor;

    public StateControl()
    {
        _machineready      = false;
        _portsforwarded    = false;
        _shutdownrequested = false;
        _applicationdone   = false;
        _shutdownmachine   = false;
        _backendready          = false;

        launched = new ArrayList<Process>();
        Messenger.register(MT.DEBUG_REQUEST,  (t,d) -> new DebugCollector(cmonitor).start());
        Messenger.register(MT.IMPORT_REQUEST, (t,d) -> importRequest());
    }

    public boolean isApplicationDone()          { return _applicationdone; }
    public boolean shouldStopMachine()          { return _shutdownmachine; }
    public boolean arePortsForwarded()          { return _portsforwarded;  }
    public boolean isMachineReady()             { return _machineready;    }
    public boolean isBackendReady()             { return _backendready;    }
    public Map<String, String> getMachineEnv()  { return _machineenv;      }

    public void signalPortsReady(boolean ready)        { _portsforwarded = ready; }
    public void setMachineEnv(Map<String, String> env) { _machineenv = env; }

    public void setUsingMachine(boolean using)
    {
        _usingmachine = using;
        // FINISH ME IF DESIRED if (!_usingmachine) {
            //mMachineStatus.getParent().remove(mMachineStatus);
        //}
    }

    public void signalMachineReady(boolean ready)
    {
        if (ready != _machineready)
        {
            _machineready = ready;
            cmonitor.poke();
        }
    }

    public void signalContainersReady(boolean ready)
    {
        boolean ok = (ready && _portsforwarded);
        if (ok != _backendready) // the following should only occur on state change
        {
            if (ok)
            {
                Messenger.sendEvent(MT.BACKEND_STATUS, "Waiting for Database");
                PostgresqlDatabase.waitUntilUp();
                Database.openPublic(true);
            }
            Messenger.sendEvent(MT.BACKEND_READY, ok);
            pmonitor.setPause(false);
            _backendready = ok;
        }
    }

    public void importRequest()
    {
        Window active = FocusManager.getCurrentManager().getActiveWindow();
        final JFileChooser fc = new JFileChooser() {
            @Override
            public void approveSelection(){
                File f = getSelectedFile();
                if (!f.getName().contains("schema")) {
                    JOptionPane.showMessageDialog(active, "This file has no schema information in its name");
                    cancelSelection();
                    return;
                }
                super.approveSelection();
            }
        };

        fc.setDialogTitle("Specify a backup file to import");
        fc.setCurrentDirectory(Prefs.getRootDir().toFile());
        int returnVal = fc.showOpenDialog(active);
        if ((returnVal != JFileChooser.APPROVE_OPTION) || (fc.getSelectedFile() == null))
            return;

        if (JOptionPane.showConfirmDialog(active, "This will overwrite any data in the current database, is that okay?",
                                            "Import Data", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.CANCEL_OPTION)
            return;

        for (Process p : launched) {
            if (p.isAlive())
                p.destroy();
        }

        pmonitor.setPause(true);
        Database.d.close();
        cmonitor.importRequest(fc.getSelectedFile().toPath());
        cmonitor.poke();
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

        pmonitor.setPause(true);
        String ver = Database.d.getVersion();
        Database.d.close();

        // second backup the database
        String date = new SimpleDateFormat("yyyyMMddHH").format(new Date());
        cmonitor.dumpDatabase(Prefs.getBackupDirectory().resolve(String.format("date_%s#schema_%s.pgdump", date, ver)), true);

        // note the shutdown flag and wake up our monitors to finish up
        _applicationdone = true;
        mmonitor.poke();
        cmonitor.poke();
        pmonitor.poke();
    }

    /**
     * This actually starts the threads in the program and then waits for
     * them to finish.  We can't used thread.join as it breaks thread.notify
     * behavior during regular runtime.
     */
    public void startAndWaitForThreads()
    {
        cmonitor = new Monitors.ContainerMonitor(this);
        cmonitor.start();
        mmonitor = new Monitors.MachineMonitor(this);
        mmonitor.start();
        pmonitor = new Monitors.MergeStatusMonitor(this);
        pmonitor.start();

        try {
            while (mmonitor.isAlive() || cmonitor.isAlive())
                Thread.sleep(300);
        } catch (InterruptedException ie) {
            log.warning("\bTrayMonitor exiting due to interuption: " + ie);
        }
    }
}


