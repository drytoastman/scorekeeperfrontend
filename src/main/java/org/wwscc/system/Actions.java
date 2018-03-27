/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system;

import java.awt.Component;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.FocusManager;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import org.wwscc.actions.EventSendAction;
import org.wwscc.dialogs.SeriesSelectionDialog;
import org.wwscc.dialogs.HoverMessage;
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
    List<Action> actions;
    Action debugRequest, backupRequest, importRequest, mergeAll, mergeWith, downloadSeries, clearOld, makeActive, makeInactive;
    Action deleteServer, addServer, initServers, deleteSeries, discovery, resetHash, quit, openStatus;

    public Actions()
    {
        apps    = new ArrayList<Action>();
        actions = new ArrayList<Action>();

        apps.add(new EventSendAction("DataEntry",    MT.LAUNCH_REQUEST, "org.wwscc.dataentry.DataEntry", null));
        apps.add(new EventSendAction("Registration", MT.LAUNCH_REQUEST, "org.wwscc.registration.Registration", null));
        apps.add(new EventSendAction("ProTimer",     MT.LAUNCH_REQUEST, "org.wwscc.protimer.ProSoloInterface", null));
        apps.add(new EventSendAction("ChallengeGUI", MT.LAUNCH_REQUEST, "org.wwscc.challenge.ChallengeGUI", null));

        quit           = new EventSendAction("Shutdown",        MT.SHUTDOWN_REQUEST);
        openStatus     = new EventSendAction("Status Window",   MT.OPEN_STATUS_REQUEST);
        debugRequest   = new EventSendAction("Save Debug Info", MT.DEBUG_REQUEST);
        backupRequest  = addAction(new EventSendAction("Backup Database", MT.BACKUP_REQUEST));
        importRequest  = addAction(new ImportAction());

        mergeAll       = addAction(new MergeWithAllLocalAction());
        mergeWith      = addAction(new PopupMenuWithMergeServerActions("Sync With ...", p -> p.isActive() || p.isRemote(), MergeWithHostAction.class));
        downloadSeries = addAction(new PopupMenuWithMergeServerActions("Download New Series From ...", p -> p.isActive() || p.isRemote(), DownloadFromHostAction.class));
        makeActive     = addAction(new SetRemoteActiveAction());
        makeInactive   = addAction(new SetRemoteInactiveAction());
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
        actions.add(a);
        return a;
    }

    public void backendReady(boolean b)
    {
        for (Action a : apps)
            a.setEnabled(b);
        for (Action a : actions)
            a.setEnabled(b);
    }

    /**
     * Action that creates a popup menu population with Actions created from the list
     * of active servers to merge with given a template class.
     */
    static class PopupMenuWithMergeServerActions extends AbstractAction
    {
        Class<? extends PopupHostAction> template;
        Predicate<MergeServer> filter;

        public PopupMenuWithMergeServerActions(String title, Predicate<MergeServer> filter, Class<? extends PopupHostAction> template)
        {
            super(title + "\u2BC6"); // u2bc6 = down arrow
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
                HoverMessage msg = new HoverMessage("Initializing local database for new series download");
                msg.doDialog("Series Init", e -> {});
                Database.d.verifyUserAndSeries(ret.series, ret.password);
                Database.d.mergeServerUpdateNow(server.getServerId());
                msg.close();
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
            putValue(Action.NAME, "Peer Discovery " + (on ? "On":"Off"));
        }

        public void actionPerformed(ActionEvent e) {
            boolean on = ((AbstractButton)e.getSource()).getModel().isSelected();
            putValue(Action.SELECTED_KEY, on);
            putValue(Action.NAME, "Peer Discovery " + (on ? "On":"Off"));
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

    static abstract class ServerListAction extends AbstractAction
    {
        String title;
        String label;
        String selectAllWarning;
        Predicate<MergeServer> filter;

        public ServerListAction(String name, String title, String label, String selectAllWarning, Predicate<MergeServer> filter) {
            super(name);
            this.title = title;
            this.label = label;
            this.selectAllWarning = selectAllWarning;
            this.filter = filter;
        }

        public void actionPerformed(ActionEvent e) {
            ListDialog<MergeServer> sd = new ListDialog<MergeServer>(label,
                    Database.d.getMergeServers().stream().filter(filter).collect(Collectors.toList()), selectAllWarning);
            if (!sd.doDialog(title, null))
                return;
            List<MergeServer> selected = sd.getResult();
            if (selected.size() == 0)
                return;

            new Thread() { @Override public void run()  {
                processSelection(selected);
            }}.start();
        }

        protected abstract void processSelection(List<MergeServer> servers);
    }


    static class DeleteServerAction extends ServerListAction
    {
        public DeleteServerAction() {
            super("Delete Remote Server",
                    "Select Servers",
                    "Select the remote servers to delete",
                    "\bYou can't delete all remote servers.  There needs to be at least one host server to merge with.",
                    ms -> ms.isRemote());
        }

        protected void processSelection(List<MergeServer> servers) {
            for (MergeServer s : servers)
                Database.d.mergeServerDelete(s.getServerId());
            Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
        }
    }


    static class SetRemoteInactiveAction extends ServerListAction
    {
        public SetRemoteInactiveAction() {
            super("Set Remote On Demand", "Select Servers", "Select the servers to stop", null, ms -> ms.isRemote() && ms.isActive());
        }

        protected void processSelection(List<MergeServer> servers) {
            for (MergeServer s : servers)
                Database.d.mergeServerDeactivate(s.getServerId());
            Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
        }
    }


    static class SetRemoteActiveAction extends ServerListAction
    {
        public SetRemoteActiveAction() {
            super("Set Remote Persistent", "Select Servers", "Select the servers to persist", null, ms -> ms.isRemote() && !ms.isActive());
        }

        protected void processSelection(List<MergeServer> servers) {
            for (MergeServer s : servers)
                Database.d.mergeServerActivate(s.getServerId(), s.getHostname(), s.getAddress());
            Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
        }
    }


    static class ImportAction extends AbstractAction
    {
        public ImportAction() {
            super("Import Backup Data");
        }

        public void actionPerformed(ActionEvent e) {
            Window active = FocusManager.getCurrentManager().getActiveWindow();
            final JFileChooser fc = new JFileChooser() {
                @Override
                public void approveSelection(){
                    File f = getSelectedFile();
                    String pieces[] = f.getName().split("[#._]");
                    if (!pieces[0].equals("date") || !pieces[2].equals("schema")) {
                        JOptionPane.showMessageDialog(active, f.getName() + " is not a recognized backup filename of the format date_<DATE>#schema_<SCHEMA>");
                        return;
                    }

                    if (Integer.parseInt(pieces[3]) < 20180000) {
                        JOptionPane.showMessageDialog(active, "Unable to import backups with schema earlier than 2018, selected file is " + pieces[3]);
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

            Messenger.sendEvent(MT.IMPORT_REQUEST, fc.getSelectedFile());
        }
    }


    static class DeleteLocalSeriesAction extends AbstractAction
    {
        public DeleteLocalSeriesAction() {
            super("Delete Local Series");
        }
        public void actionPerformed(ActionEvent e) {
            ListDialog<String> sd = new ListDialog<String>("Select the series to remove locally", Database.d.getSeriesList());
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


    public static class InitServersAction extends AbstractAction
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
}
