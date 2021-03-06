/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system;

import java.io.File;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.FocusManager;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;

import org.wwscc.dialogs.BaseDialog;
import org.wwscc.dialogs.HoverMessage;
import org.wwscc.storage.Database;
import org.wwscc.storage.MergeServer;
import org.wwscc.system.SeriesSelectionDialog.HSResult;
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
    List<Process> launched;
    MachineMonitor   mmonitor;
    ContainerMonitor cmonitor;
    FrontEndMonitor pmonitor;
    boolean usingmachine, shutdownstarted;

    public ScorekeeperSystem()
    {
        actions  = new Actions();
        model    = new MergeServerModel();
        launched = new ArrayList<Process>();
        usingmachine    = false;
        shutdownstarted = false;

        window = new ScorekeeperStatusWindow(actions, model);
        window.setVisible(true);

        BaseDialog.MessageOnly mdiag = new BaseDialog.MessageOnly("Initial connection is taking a while, is Docker installed and running properly?");

        Messenger.register(MT.USING_MACHINE,         (t,d) -> usingmachine = (boolean)d);
        Messenger.register(MT.DEBUG_REQUEST,         (t,d) -> new DebugCollector(cmonitor).start());
        Messenger.register(MT.SYSTEMLOG_REQUEST,     (t,d) -> SystemLogViewer.show(Path.of(Prefs.getLogDirectory().toString(), "scorekeepersystem.0.log")));
        Messenger.register(MT.BACKUP_REQUEST,        (t,d) -> cmonitor.backupRequest());
        Messenger.register(MT.IMPORT_REQUEST,        (t,d) -> importRequest((File)d));
        Messenger.register(MT.LAUNCH_REQUEST,        (t,d) -> launchRequest((String)d));
        Messenger.register(MT.SHUTDOWN_REQUEST,      (t,d) -> shutdownRequest());
        Messenger.register(MT.DOWNLOAD_NEW_REQUEST,  (t,d) -> downloadNewRequest((MergeServer)d));
        Messenger.register(MT.LOAD_CERTS_REQUEST,    (t,d) -> loadCerts());
        Messenger.register(MT.DATABASE_NOTIFICATION, (t,d) -> dataUpdate((String)d));
        Messenger.register(MT.DOCKER_NOT_OK,         (t,d) -> mdiag.doDialog("Docker Check", e -> {}, window));
        Messenger.register(MT.DOCKER_OK,             (t,d) -> mdiag.close());
    }

    public void dataUpdate(String table)
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
        cmonitor.importRequest(f.toPath());
    }

    public void launchRequest(String tolaunch)
    {
        Runnable launch = () -> {
            try
            {
                ArrayList<String> cmd = new ArrayList<String>();
                cmd.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
                if (System.getProperty("os.name").contains("Windows")) // make it javaw
                    cmd.add(cmd.remove(0) + "w");
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
            int result = JOptionPane.showOptionDialog(window, "<html>" +
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
            int result = JOptionPane.showOptionDialog(window, "<html>" +
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

            // tell everyone to shutdown
            mmonitor.shutdown();
            cmonitor.shutdown();
            pmonitor.shutdown();
        };
        new Thread(shutdown, "ShutdownThread").start();
    }


    public void downloadNewRequest(MergeServer server)
    {
        SeriesSelectionDialog hd = new SeriesSelectionDialog(server.getConnectEndpoint());
        if (!hd.doDialog("Select Host and Series", null))
            return;
        HSResult ret = hd.getResult();
        new Thread() { @Override public void run()  {
            HoverMessage msg = new HoverMessage("Initializing local database for new series download");
            msg.doDialog("Series Init", e -> {});
            Database.d.verifyUserAndSeries(ret.series, ret.password);
            Database.d.mergeServerUpdateNow(server.getServerId());
            msg.close();
            Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
        }}.start();
    }

    public void loadCerts()
    {
        JFileChooser fc = new JFileChooser(System.getProperty("user.home"));
        fc.setDialogTitle("Select the new certificates archive file");
        fc.setFileFilter(new CertsFileFilter());
        if (fc.showOpenDialog(FocusManager.getCurrentManager().getActiveWindow()) != JFileChooser.APPROVE_OPTION)
            return;
        cmonitor.loadCerts(fc.getSelectedFile().toPath());
    }

    static class CertsFileFilter extends FileFilter {
        public boolean accept(File f) {
            if (f.isDirectory()) {
                return true;
            }
            for (String ext : new String[] { ".tgz", ".tar.gz", ".tar" }) {
                if (f.getName().endsWith(ext)) return true;
            }
            return false;
        }
        public String getDescription() {
            return "Certs Archive File (.tar, .tar.gz, .tgz)";
        }
    }

    /**
     * This actually starts the threads in the program and then waits for
     * them to finish.  We can't used thread.join as it breaks thread.notify
     * behavior during regular runtime.
     */
    public void startAndWaitForThreads()
    {
        pmonitor = new FrontEndMonitor();
        pmonitor.start();
        cmonitor = new ContainerMonitor();
        cmonitor.start();
        mmonitor = new MachineMonitor();
        mmonitor.start();

        try {
            // wait for two monitor threads to finish
            while (mmonitor.isAlive() || cmonitor.isAlive())
                Thread.sleep(300);
        } catch (InterruptedException ie) {
            log.warning("\bScorekeeperSystem exiting due to interuption: " + ie);
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

        new ScorekeeperSystem().startAndWaitForThreads();
        System.exit(0);
    }
}
