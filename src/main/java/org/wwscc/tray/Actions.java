package org.wwscc.tray;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.FocusManager;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import org.wwscc.actions.EventSendAction;
import org.wwscc.dialogs.SeriesSelectionDialog;
import org.wwscc.dialogs.ListDialog;
import org.wwscc.dialogs.SeriesSelectionDialog.HSResult;
import org.wwscc.storage.Database;
import org.wwscc.storage.MergeServer;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.wwscc.util.Network;
import org.wwscc.util.Prefs;
import org.wwscc.util.Resources;

public class Actions
{
    private static final Logger log = Logger.getLogger(Actions.class.getName());

    List<Action> apps;
    List<Action> others;
    Action debugRequest, backupRequest, importRequest, mergeAll, mergeWith, downloadSeries, makeActive, makeInactive, clearOld;
    Action deleteServer, addServer, initServers, deleteSeries, discovery, resetHash, quit, openStatus;

    public Actions()
    {
        apps = new ArrayList<Action>();
        others = new ArrayList<Action>();

        apps.add(new EventSendAction("DataEntry",    MT.LAUNCH_REQUEST, "org.wwscc.dataentry.DataEntry", null));
        apps.add(new EventSendAction("Registration", MT.LAUNCH_REQUEST, "org.wwscc.registration.Registration", null));
        apps.add(new EventSendAction("ProTimer",     MT.LAUNCH_REQUEST, "org.wwscc.protimer.ProSoloInterface", null));
        apps.add(new EventSendAction("ChallengeGUI", MT.LAUNCH_REQUEST, "org.wwscc.challenge.ChallengeGUI", null));
        //apps.add(new EventSendAction("BWTimer",      MT.LAUNCH_REQUEST, "org.wwscc.bwtimer.Timer", null));

        quit           = new EventSendAction("Shutdown",        MT.SHUTDOWN_REQUEST);
        openStatus     = new EventSendAction("Status Window",   MT.OPEN_STATUS_REQUEST);
        debugRequest   = new EventSendAction("Save Debug Info", MT.DEBUG_REQUEST);
        backupRequest  = new EventSendAction("Backup Database", MT.BACKUP_REQUEST);
        importRequest  = addAction(new EventSendAction("Import Backup Data", MT.IMPORT_REQUEST));

        mergeAll       = addAction(new MergeWithAllLocalAction());
        mergeWith      = addAction(new PopupMenuWithMergeServerActions(
                                    "Sync With ...",
                                    p -> p.isActive() || p.isRemote(),
                                    MergeWithHostAction.class,
                                    null));
        downloadSeries = addAction(new PopupMenuWithMergeServerActions(
                                    "Download New Series From ...",
                                    p -> p.isActive() || p.isRemote(),
                                    DownloadFromHostAction.class,
                                    null));
        makeActive     = addAction(new PopupMenuWithMergeServerActions(
                                    "Make Remote Persistant ...",
                                    p -> !p.isActive() && p.isRemote(),
                                    SetRemoteActiveAction.class,
                                    new ImageIcon(Resources.loadImage("server.png"))));
        makeInactive   = addAction(new PopupMenuWithMergeServerActions(
                                    "Deactivate Remote ...",
                                    p -> p.isActive() && p.isRemote(),
                                    SetRemoteInactiveAction.class,
                                    new ImageIcon(Resources.loadImage("server.png"))));
        clearOld       = addAction(new ClearOldDiscoveredAction());
        deleteServer   = addAction(new DeleteServerAction());
        addServer      = addAction(new AddServerAction());
        initServers    = addAction(new InitServersAction());
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

    static Predicate<MergeServer> inactiveAndRemote() { return p -> !p.isActive() && p.isRemote(); }
    static Predicate<MergeServer> activeAndRemote()   { return p -> p.isActive()  && p.isRemote(); }


    /**
     * Action that creates a popup menu population with Actions created from the list
     * of active servers to merge with given a template class.
     */
    static class PopupMenuWithMergeServerActions extends AbstractAction
    {
        Class<? extends PopupHostAction> template;
        Predicate<MergeServer> filter;

        public PopupMenuWithMergeServerActions(String title, Predicate<MergeServer> filter, Class<? extends PopupHostAction> template, Icon icon)
        {
            super(title + "\u2BC6", icon); // u2bc6 = down arrow
            this.filter = filter;
            this.template = template;
        }

        public void actionPerformed(ActionEvent e) {
            JPopupMenu menu = new JPopupMenu();
            Database.d.getMergeServers().stream().filter(filter).forEach(m -> {
                try {
                    menu.add(new JMenuItem((PopupHostAction)template.getConstructor(MergeServer.class).newInstance(m)));
                } catch (Exception e1) {
                    log.log(Level.WARNING, "\bcan't build action: " + e1, e1);
                }
            });

            if (menu.getSubElements().length == 0) {
                menu.add(new JMenuItem(""));
            }

            Component c = (Component)e.getSource();
            menu.show(c, 5, c.getHeight()-5);
        }
    }

    public static abstract class PopupHostAction extends AbstractAction
    {
        MergeServer server;
        public PopupHostAction(MergeServer s) {
            super();
            server = s;
            if (server.getAddress().equals(""))
                putValue(NAME, server.getHostname());
            else
                putValue(NAME, server.getHostname() + "/" + server.getAddress());
        }
    }

    public static class DownloadFromHostAction extends PopupHostAction
    {
        public DownloadFromHostAction(MergeServer s) { super(s); }
        public void actionPerformed(ActionEvent e) {
            SeriesSelectionDialog hd = new SeriesSelectionDialog(server);
            if (!hd.doDialog("Select Host and Series", null))
                return;
            HSResult ret = hd.getResult();
            new Thread() { @Override public void run()  {
                Database.d.verifyUserAndSeries(ret.series, ret.password);
                Database.d.mergeServerUpdateNow(server.getServerId());
                Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
            }}.start();
        }
    }

    public static class MergeWithHostAction extends PopupHostAction
    {
        public MergeWithHostAction(MergeServer s) { super(s); }
        public void actionPerformed(ActionEvent e) {
            Database.d.mergeServerUpdateNow(server.getServerId());
            Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
        }
    }

    public static class SetRemoteActiveAction extends PopupHostAction
    {
        public SetRemoteActiveAction(MergeServer s) { super(s); }
        public void actionPerformed(ActionEvent e) {
            Database.d.mergeServerActivate(server.getServerId(), server.getHostname(), server.getAddress());
            Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
        }
    }

    public static class SetRemoteInactiveAction extends PopupHostAction
    {
        public SetRemoteInactiveAction(MergeServer s) { super(s); }
        public void actionPerformed(ActionEvent e) {
            Database.d.mergeServerDeactivate(server.getServerId());
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
            super("Clear Old Peers", new ImageIcon(Resources.loadImage("group.png")));
        }
        public void actionPerformed(ActionEvent e) {
            new Thread() { @Override public void run()  {
                for (MergeServer s : Database.d.getMergeServers()) {
                    if (!s.isLocalHost() && !s.isActive() && !s.isRemote()) {
                        Database.d.mergeServerDelete(s.getServerId());
                    }
                }
                Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
            }}.start();
        }
    }

    static class DeleteServerAction extends AbstractAction
    {
        public DeleteServerAction() {
            super("Delete Remote Server");
        }
        public void actionPerformed(ActionEvent e) {
            Supplier<Stream<MergeServer>> servers = () -> Database.d.getMergeServers().stream().filter(ms -> ms.isRemote());
            ListDialog sd = new ListDialog("Select the remote servers to delete",
                    servers.get().map(ms -> ms.getHostname()).collect(Collectors.toList()),
                    "\bYou can't delete all remote servers.  There needs to be at least one host server to merge with.");
            if (!sd.doDialog("Select Series", null))
                return;
            List<String> selected = sd.getResult();
            if (selected.size() == 0)
                return;

            new Thread() { @Override public void run()  {
                for (String h : selected)
                    Database.d.mergeServerDelete(servers.get().filter(ms -> ms.getHostname().equals(h)).findFirst().get().getServerId());
                Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
            }}.start();
        }
    }

    static class AddServerAction extends AbstractAction
    {
        public AddServerAction() {
            super("Add Remote Server");
        }
        public void actionPerformed(ActionEvent e) {
            String host = JOptionPane.showInputDialog(FocusManager.getCurrentManager().getActiveWindow(), "Enter the remote host name");
            if ((host != null) && !host.trim().equals("")) {
                Database.d.mergeServerSetRemote(host, "", 10);
                Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
            }
        }
    }


    static class InitServersAction extends AbstractAction
    {
        public static final String HOME_SERVER = "scorekeeper.wwscc.org";

        public InitServersAction() {
            super("Init Server List");
        }
        public void actionPerformed(ActionEvent e) {
            doinit();
        }
        public static void doinit() {
            // Local should always be there
            InetAddress a = Network.getPrimaryAddress();
            if (a == null) a = InetAddress.getLoopbackAddress();
            Database.d.mergeServerSetLocal(Network.getLocalHostName(), a.getHostAddress(), 10);

            // And a remote home server of some type should always be there
            boolean remotepresent = false;
            for (MergeServer s : Database.d.getMergeServers()) {
                if (s.isRemote())
                    remotepresent = true;
            }
            if (!remotepresent) {
                Database.d.mergeServerSetRemote(HOME_SERVER, "", 10);
            }

            // inactive all
            Database.d.mergeServerInactivateAll();
            Messenger.sendEvent(MT.DATABASE_NOTIFICATION, new HashSet<String>(Arrays.asList("mergeservers")));
        }
    }


    static class DeleteLocalSeriesAction extends AbstractAction
    {
        public DeleteLocalSeriesAction() {
            super("Delete Local Series Copy");
        }
        public void actionPerformed(ActionEvent e) {
            ListDialog sd = new ListDialog("Select the series to remove locally", Database.d.getSeriesList());
            if (!sd.doDialog("Select Series", null))
                return;
            List<String> selected = sd.getResult();
            if (selected.size() == 0)
                return;

            new Thread() { @Override public void run()  {
                for (String s : selected)
                    if (!Database.d.deleteUserAndSeries(s))
                        return;
                if (sd.allSelected())
                    Database.d.deleteDriversTable();
                Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
            }}.start();
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
