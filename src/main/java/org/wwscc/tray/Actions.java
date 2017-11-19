package org.wwscc.tray;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.wwscc.actions.EventSendAction;
import org.wwscc.dialogs.SeriesSelectionDialog;
import org.wwscc.dialogs.SeriesDialog;
import org.wwscc.dialogs.SeriesSelectionDialog.HSResult;
import org.wwscc.storage.Database;
import org.wwscc.storage.MergeServer;
import org.wwscc.storage.PostgresqlDatabase;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;

public class Actions
{
    private static final Logger log = Logger.getLogger(Actions.class.getName());

    List<LaunchAppAction> apps;
    Action debugRequest, importRequest, mergeAll, mergeWith, downloadSeries, clearOld;
    Action deleteSeries, discovery, resetHash;

    public Actions()
    {
        apps = new ArrayList<LaunchAppAction>();
        apps.add(new LaunchAppAction("DataEntry",    "org.wwscc.dataentry.DataEntry"));
        apps.add(new LaunchAppAction("Registration", "org.wwscc.registration.Registration"));
        apps.add(new LaunchAppAction("ProTimer",     "org.wwscc.protimer.ProSoloInterface"));
        apps.add(new LaunchAppAction("ChallengeGUI", "org.wwscc.challenge.ChallengeGUI"));
        apps.add(new LaunchAppAction("BWTimer",      "org.wwscc.bwtimer.Timer"));
        Messenger.register(MT.BACKEND_READY, (type, data) -> { for (Action a : apps) a.setEnabled((boolean)data); } );

        debugRequest   = new EventSendAction("Collect Debug Info", MT.DEBUG_REQUEST);
        importRequest  = new EventSendAction("Import Previous Data", MT.IMPORT_REQUEST);
        mergeAll       = new MergeWithAllLocalAction();
        mergeWith      = new MergeWithAction();
        downloadSeries = new DownloadNewSeriesAction();
        clearOld       = new ClearOldDiscoveredAction();

        deleteSeries   = new DeleteLocalSeriesAction();
        discovery      = new LocalDiscoveryAction(Prefs.getAllowDiscovery());
        resetHash      = new ResetHashAction();
    }


    static class LaunchAppAction extends AbstractAction
    {
        String tolaunch;
        public LaunchAppAction(String name, String classname)
        {
            super(name);
            tolaunch = classname;
            setEnabled(false);
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            Process p = launchExternal(tolaunch);
            if (p != null)
                Messenger.sendEvent(MT.APP_LAUNCHED, p);
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

    static List<MergeServer> getActive()
    {
        List<MergeServer> data = Database.d.getMergeServers();
        ListIterator<MergeServer> iter = data.listIterator();
        while (iter.hasNext()) {
            MergeServer n = iter.next();
            if ((n.isLocalHost()) || (!n.isActive() && !n.isRemote()))
                iter.remove();
        }
        return data;
    }


    private static class _DownloadFromhHostAction extends AbstractAction
    {
        MergeServer server;
        public _DownloadFromhHostAction(MergeServer s) {
            super();
            server = s;
            if (server.getAddress().equals(""))
                putValue(NAME, server.getHostname());
            else
                putValue(NAME, server.getHostname() + "/" + server.getAddress());
        }

        public void actionPerformed(ActionEvent e) {
            SeriesSelectionDialog hd = new SeriesSelectionDialog(server);
            if (!hd.doDialog("Select Host and Series", null))
                return;
            HSResult ret = hd.getResult();
            Database.d.verifyUserAndSeries(ret.series, ret.password);
            Database.d.mergeServerUpdateNow(server.getServerId());
            Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
        }
    }

    static class DownloadNewSeriesAction extends AbstractAction
    {
        public DownloadNewSeriesAction()
        {
            super("Download New Series From ... \u2BC6");
        }
        public void actionPerformed(ActionEvent e) {
            JPopupMenu menu = new JPopupMenu();
            for (MergeServer s : getActive()) {
                menu.add(new JMenuItem(new _DownloadFromhHostAction(s)));
            }
            Component c = (Component)e.getSource();
            menu.show(c, 5, c.getHeight()-5);
        }
    }


    private static class _MergeWithHostAction extends AbstractAction
    {
        MergeServer server;
        public _MergeWithHostAction(MergeServer s) {
            super();
            server = s;
            if (server.getAddress().equals(""))
                putValue(NAME, server.getHostname());
            else
                putValue(NAME, server.getHostname() + "/" + server.getAddress());
        }

        public void actionPerformed(ActionEvent e) {
            Database.d.mergeServerUpdateNow(server.getServerId());
            Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
        }
    }

    static class MergeWithAction extends AbstractAction
    {
        public MergeWithAction() {
            super("Sync With ... \u2BC6");
        }
        public void actionPerformed(ActionEvent e) {
            JPopupMenu menu = new JPopupMenu();
            for (MergeServer s : getActive()) {
                menu.add(new JMenuItem(new _MergeWithHostAction(s)));
            }
            Component c = (Component)e.getSource();
            menu.show(c, 5, c.getHeight()-5);
        }
    }

    static class MergeWithAllLocalAction extends AbstractAction
    {
        public MergeWithAllLocalAction() {
            super("Sync All Active Now");
        }
        public void actionPerformed(ActionEvent e) {
            for (MergeServer s : Database.d.getMergeServers()) {
                if (s.isActive()) {
                    Database.d.mergeServerUpdateNow(s.getServerId());
                }
            }
            Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
        }
    }

    static class LocalDiscoveryAction extends AbstractAction
    {
        public LocalDiscoveryAction(boolean on) {
            putValue(Action.SELECTED_KEY, on);
            putValue(Action.NAME, "Local Discovery " + (on ? "On":"Off"));
        }

        public void actionPerformed(ActionEvent e) {
            boolean on = ((AbstractButton)e.getSource()).getModel().isSelected();
            putValue(Action.SELECTED_KEY, on);
            putValue(Action.NAME, "Local Discovery " + (on ? "On":"Off"));
            Prefs.setAllowDiscovery(on);
            Messenger.sendEvent(MT.DISCOVERY_CHANGE, on);
        }
    }

    static class ClearOldDiscoveredAction extends AbstractAction
    {
        public ClearOldDiscoveredAction() {
            super("Clear Old Discovered Entries");
        }
        public void actionPerformed(ActionEvent e) {
            for (MergeServer s : Database.d.getMergeServers()) {
                if (!s.isLocalHost() && !s.isActive() && !s.isRemote()) {
                    Database.d.mergeServerDelete(s.getServerId());
                }
            }
            Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
        }
    }

    static class DeleteLocalSeriesAction extends AbstractAction
    {
        public DeleteLocalSeriesAction() {
            super("Delete Local Series Copy");
        }
        public void actionPerformed(ActionEvent e) {
            SeriesDialog sd = new SeriesDialog("Select the series to remove locally", PostgresqlDatabase.getSeriesList(null).toArray(new String[0]));
            if (!sd.doDialog("Select Series", null))
                return;
            List<String> selected = sd.getResult();
            if (selected.size() > 0)
            {
                for (String s : selected)
                    Database.d.deleteUserAndSeries(s);
                if (sd.allSelected())
                    Database.d.deleteDriversTable();
            }
            Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
        }
    }

    static class ResetHashAction extends AbstractAction
    {
        public ResetHashAction() {
            super("Reset Calculations");
        }
        public void actionPerformed(ActionEvent e) {
            Database.d.mergeServerResetAll();
            Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
        }
    }

}
