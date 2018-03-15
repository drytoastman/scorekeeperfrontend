/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system;

import java.awt.AWTException;
import java.awt.SystemTray;
import java.io.File;
import java.lang.ProcessBuilder.Redirect;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;

import org.wwscc.storage.Database;
import org.wwscc.storage.MergeServer;
import org.wwscc.system.monitors.ContainerMonitor;
import org.wwscc.system.monitors.FrontEndMonitor;
import org.wwscc.system.monitors.MachineMonitor;
import org.wwscc.util.AppSetup;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;
import org.wwscc.util.SingletonProcessTest;

public class ScorekeeperSystem
{
    private static final Logger log = Logger.getLogger(ScorekeeperSystem.class.getName());

    Actions actions;
    MergeServerModel model;
    ScorekeeperStatusWindow window;
    ScorekeeperTrayIcon trayicon;
    List<Process> launched;
    MachineMonitor   mmonitor;
    ContainerMonitor cmonitor;
    FrontEndMonitor pmonitor;
    boolean usingmachine, shutdownstarted;

    @SuppressWarnings("unchecked")
    public ScorekeeperSystem()
    {
        actions  = new Actions();
        model    = new MergeServerModel();
        launched = new ArrayList<Process>();
        usingmachine    = false;
        shutdownstarted = false;

        boolean usetray = false;
        if (SystemTray.isSupported()) {
            try {
                trayicon = new ScorekeeperTrayIcon(actions);
                usetray = true;
            } catch (AWTException e) {
                log.warning("Unable to install trayicon: " + e);
            }
        }

        window = new ScorekeeperStatusWindow(actions, model, usetray);
        window.setVisible(true);

        Messenger.register(MT.USING_MACHINE,         (t,d) -> usingmachine = (boolean)d);
        Messenger.register(MT.DEBUG_REQUEST,         (t,d) -> new DebugCollector(cmonitor).start());
        Messenger.register(MT.BACKUP_REQUEST,        (t,d) -> cmonitor.backupRequest(Prefs.getBackupDirectory()));
        Messenger.register(MT.IMPORT_REQUEST,        (t,d) -> importRequest((File)d));
        Messenger.register(MT.LAUNCH_REQUEST,        (t,d) -> launchRequest((String)d));
        Messenger.register(MT.SHUTDOWN_REQUEST,      (t,d) -> shutdownRequest());
        Messenger.register(MT.DATABASE_NOTIFICATION, (t,d) -> dataUpdate((Set<String>)d));
    }

    public void dataUpdate(Set<String> tables)
    {
        List<MergeServer> s = Database.d.getMergeServers();
        model.setData(s);
        actions.makeActive.setEnabled(s.stream().filter(m -> m.isRemote() && !m.isActive()).count() > 0);
        actions.makeInactive.setEnabled(s.stream().filter(m -> m.isRemote() && m.isActive()).count() > 0);
    }

    public void importRequest(File f)
    {
        // FINISH ME, should this be in a non-event thread, what is time for destroy?
        for (Process p : launched) {
            if (p.isAlive())
                p.destroy();
        }

        pmonitor.setPause(true);
        Database.d.close();
        cmonitor.importRequest(f.toPath());
    }

    public void launchRequest(String tolaunch)
    {
        Runnable launch = () -> {
            try
            {
                ArrayList<String> cmd = new ArrayList<String>();
                if (System.getProperty("os.name").split("\\s")[0].equals("Windows"))
                    cmd.add("javaw");
                else
                    cmd.add("java");
                cmd.add("-cp");
                cmd.add(System.getProperty("java.class.path"));
                cmd.add(tolaunch);
                log.info(String.format("Running %s", cmd));
                ProcessBuilder starter = new ProcessBuilder(cmd);
                starter.redirectErrorStream(true);
                starter.redirectOutput(Redirect.appendTo(Prefs.getLogDirectory().resolve("jvmlaunches.log").toFile()));
                Process p = starter.start();
                Thread.sleep(1000);
                if (!p.isAlive()) {
                    throw new Exception("Process not alive after 1 second");
                }
                launched.add(p);
            }
            catch (Exception ex)
            {
                log.log(Level.SEVERE, String.format("\bFailed to launch %s",  tolaunch), ex);
            }
        };
        new Thread(launch, "LaunchedApp").start();
    }

    public void shutdownRequest()
    {
        if (shutdownstarted)
        {   // Quit called a second time while shutting down, just quit now
            log.warning("User force quiting.");
            System.exit(-1);
        }

        if (usingmachine)
        {
            Object[] options = { "Shutdown Scorekeeper", "Shutdown Scorekeeper and VM", "Cancel" };
            int result = JOptionPane.showOptionDialog(null, "<html>" +
                    "This will stop all applications including the database and web server.<br/>" +
                    "You can also shutdown the Virtual Machine if you are shutting down/logging off.<br/>&nbsp;",
                    "Shutdown Scorekeeper", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
            if (result == 2)
                return;
            mmonitor.stopMachine(result == 1);
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

        Messenger.sendEventNow(MT.OPEN_STATUS_REQUEST, null);

        Runnable shutdown = () -> {
            // first shutdown all the things with database connections
            shutdownstarted = true;
            for (Process p : launched) {
                if (p.isAlive())
                    p.destroy();
            }

            pmonitor.setPause(true);
            cmonitor.backupNow(Prefs.getBackupDirectory(), true);
            Database.d.close();

            // tell everyone to shutdown
            mmonitor.shutdown();
            cmonitor.shutdown();
            pmonitor.shutdown();
        };
        new Thread(shutdown, "ShutdownThread").start();
    }


    /**
     * This actually starts the threads in the program and then waits for
     * them to finish.  We can't used thread.join as it breaks thread.notify
     * behavior during regular runtime.
     */
    public void startAndWaitForThreads()
    {
        cmonitor = new ContainerMonitor();
        cmonitor.start();
        mmonitor = new MachineMonitor();
        mmonitor.start();
        pmonitor = new FrontEndMonitor();
        pmonitor.start();

        try {
            while (mmonitor.isAlive() || cmonitor.isAlive())
                Thread.sleep(300);
        } catch (InterruptedException ie) {
            log.warning("\bTrayApplication exiting due to interuption: " + ie);
        }
    }


    /**
     * A main interface for testing datasync interface by itself
     * @param args ignored
     * @throws InterruptedException ignored
     * @throws NoSuchAlgorithmException ignored
     */
    public static void main(String[] args) throws InterruptedException, NoSuchAlgorithmException
    {
        AppSetup.appSetup("scorekeepersystem");
        if (!SingletonProcessTest.ensureSingleton("ScorekeeperSystem")) {
            log.warning("Another Scorekeeper instance is already running, quitting now.");
            System.exit(-1);
        }

        ScorekeeperSystem system = new ScorekeeperSystem();
        system.startAndWaitForThreads();
        System.exit(0);
    }
}