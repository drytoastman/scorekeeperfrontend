package org.wwscc.tray;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
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

    List<Action> apps;
    List<Action> others;
    Action debugRequest, importRequest, mergeAll, mergeWith, downloadSeries, clearOld;
    Action deleteSeries, discovery, resetHash, quit, openStatus;

    public Actions()
    {
        apps = new ArrayList<Action>();
        others = new ArrayList<Action>();

        apps.add(new EventSendAction("DataEntry",    MT.LAUNCH_REQUEST, "org.wwscc.dataentry.DataEntry", null));
        apps.add(new EventSendAction("Registration", MT.LAUNCH_REQUEST, "org.wwscc.registration.Registration", null));
        apps.add(new EventSendAction("ProTimer",     MT.LAUNCH_REQUEST, "org.wwscc.protimer.ProSoloInterface", null));
        apps.add(new EventSendAction("ChallengeGUI", MT.LAUNCH_REQUEST, "org.wwscc.challenge.ChallengeGUI", null));
        apps.add(new EventSendAction("BWTimer",      MT.LAUNCH_REQUEST, "org.wwscc.bwtimer.Timer", null));

        quit           = new EventSendAction("Quit",               MT.SHUTDOWN_REQUEST);
        openStatus     = new EventSendAction("Status Window",      MT.OPEN_STATUS_REQUEST);
        debugRequest   = new EventSendAction("Collect Debug Info", MT.DEBUG_REQUEST);

        importRequest  = addAction(new EventSendAction("Import Previous Data", MT.IMPORT_REQUEST));
        mergeAll       = addAction(new MergeWithAllLocalAction());
        mergeWith      = addAction(new PopupMenuWithMergeServerActions("Sync With ...", MergeWithHostAction.class));
        downloadSeries = addAction(new PopupMenuWithMergeServerActions("Download New Series From ...", DownloadFromHostAction.class));
        clearOld       = addAction(new ClearOldDiscoveredAction());
        deleteSeries   = addAction(new DeleteLocalSeriesAction());
        discovery      = addAction(new LocalDiscoveryAction());
        resetHash      = addAction(new ResetHashAction());

        backendReady(false);
        Messenger.register(MT.BACKEND_READY, (type, data) -> backendReady((boolean)data));
    }

    public Action addAction(Action a)
    {
        others.add(a);
        return a;
    }

    public void backendReady(boolean b)
    {
        for (Action a : apps)
            a.setEnabled(b);
        for (Action a : others)
            a.setEnabled(b);
    }


    /**
     * Action that creates a popup menu population with Actions created from the list
     * of active servers to merge with given a template class.
     */
    static class PopupMenuWithMergeServerActions extends AbstractAction
    {
        Class<? extends Action> template;
        public PopupMenuWithMergeServerActions(String s, Class<? extends Action> c)
        {
            super(s + "\u2BC6"); // down arrow
            template = c;
        }

        public void actionPerformed(ActionEvent e) {
            JPopupMenu menu = new JPopupMenu();
            for (MergeServer server : Database.d.getMergeServers())
            {
                if ((server.isLocalHost()) || (!server.isActive() && !server.isRemote()))
                    continue;
                try {
                    menu.add(new JMenuItem((Action)template.getConstructor(MergeServer.class).newInstance(server)));
                } catch (Exception ex) {
                    log.log(Level.WARNING, "can't build action: " + ex, ex);
                }
            }

            Component c = (Component)e.getSource();
            menu.show(c, 5, c.getHeight()-5);
        }
    }


    public static class DownloadFromHostAction extends AbstractAction
    {
        MergeServer server;
        public DownloadFromHostAction(MergeServer s) {
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


    public static class MergeWithHostAction extends AbstractAction
    {
        MergeServer server;
        public MergeWithHostAction(MergeServer s) {
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
        public LocalDiscoveryAction() {
            boolean on = Prefs.getAllowDiscovery();
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