/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.tray;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.net.InetAddress;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.json.simple.JSONObject;
import org.wwscc.dialogs.HostSeriesSelectionDialog;
import org.wwscc.dialogs.SeriesDialog;
import org.wwscc.dialogs.HostSeriesSelectionDialog.HSResult;
import org.wwscc.storage.Database;
import org.wwscc.storage.MergeServer;
import org.wwscc.storage.PostgresqlDatabase;
import org.wwscc.util.Discovery;
import org.wwscc.util.IdGenerator;
import org.wwscc.util.Discovery.DiscoveryListener;
import org.wwscc.util.AppSetup;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.Network;
import org.wwscc.util.Prefs;

import net.miginfocom.swing.MigLayout;

public class DataSyncWindow extends JFrame implements MessageListener, DiscoveryListener
{
    private static final Logger log = Logger.getLogger(DataSyncWindow.class.getName());
    public static enum UpdaterState { IDLE, ACTIVE, STOP };

    List<Action> actions;
    MergeServerModel model;
    MergeStatusTable activetable, inactivetable;
    UpdaterState ustate;

    public DataSyncWindow()
    {
        super("Data Synchronization");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        
        actions = new ArrayList<Action>();
        model = new MergeServerModel();
        activetable = new MergeStatusTable(model, true);
        inactivetable = new MergeStatusTable(model, false);
        JLabel aheader = new JLabel("Active Hosts");
        JLabel iheader = new JLabel("Inactive Hosts");
        aheader.setFont(aheader.getFont().deriveFont(18.0f).deriveFont(Font.BOLD));
        iheader.setFont(aheader.getFont());
        
        JPanel content = new JPanel(new MigLayout("fill", "", "[grow 0][fill]"));
        content.add(aheader, "split");
        content.add(new JButton(addAction(new MergeWithAllLocalAction())), "gapleft 10");
        content.add(new JButton(addAction(new MergeWithHomeAction())), "gapleft 10");
        content.add(new JButton(addAction(new DownloadNewSeriesAction(Prefs.getHomeServer()))), "gapleft 10, wrap");
        content.add(new JScrollPane(activetable), "grow, wrap");
        content.add(iheader, "split");
        content.add(new JButton(addAction(new ClearOldDiscoveredAction())), "gapleft 10, wrap");
        content.add(new JScrollPane(inactivetable), "grow");
        setContentPane(content);
        
        JMenu data = new JMenu("Data");
        data.add(addAction(new MergeWithAction()));
        data.add(addAction(new DownloadNewSeriesAction(null)));
        data.add(addAction(new DeleteLocalSeriesAction()));

        JMenu adv = new JMenu("Advanced");
        adv.add(new JCheckBoxMenuItem(addAction(new LocalDiscoveryAction(Prefs.getAllowDiscovery()))));
        adv.add(addAction(new ResetHashAction()));
        
        JMenuBar bar = new JMenuBar();
        bar.add(data);      
        bar.add(adv);
        setJMenuBar(bar);
        
        setBounds(Prefs.getWindowBounds("datasync"));
        Prefs.trackWindowBounds(this, "datasync");
        ustate = UpdaterState.IDLE;
        new UpdaterThread().start();
    }

    public void setUpdaterState(UpdaterState s)
    {
        ustate = s;
    }

    class UpdaterThread extends Thread
    {
        @Override
        public void run()
        {
            while (ustate != UpdaterState.STOP) {
                try {
                    if (ustate == UpdaterState.ACTIVE)
                        inner();
                    else
                        Thread.sleep(1000);
                } catch (Exception e) {}
            }
        }

        private void inner()
        {
            // These two should always be there
            Database.d.mergeServerSetLocal(Network.getLocalHostName(), Network.getPrimaryAddress().getHostAddress(), 10);
            Database.d.mergeServerSetRemote(Prefs.getHomeServer(), "", 10);
            Database.d.mergeServerInactivateAll();

            // enable menus and start discovery if its turned on
            for (Action a : actions)
                a.setEnabled(true);
            updateDiscoverySetting(Prefs.getAllowDiscovery());

            Messenger.register(MT.DATABASE_NOTIFICATION, DataSyncWindow.this);

            // force an update on start, on the event thread
            Messenger.sendEvent(MT.DATABASE_NOTIFICATION, new HashSet<String>(Arrays.asList("mergeservers")));

            // other wise, we just ping/poke the database which is the only way to receive any NOTICE events
            while (ustate == UpdaterState.ACTIVE) {
                try {
                    Database.d.ping();
                    Thread.sleep(1000);
                } catch (Exception e) {
                    log.log(Level.WARNING, e.toString(), e);
                }
            }

            Database.d.mergeServerInactivateAll();
        }
    }

    private Action addAction(Action a)
    {
        actions.add(a);
        a.setEnabled(false);
        return a;
    }
    
    @SuppressWarnings("unchecked")
    private void updateDiscoverySetting(boolean up) 
    {
        if (up)
        {
            JSONObject data = new JSONObject();
            data.put("serverid", Prefs.getServerId().toString());
            data.put("hostname", Network.getLocalHostName());
            Discovery.get().addServiceListener(Discovery.DATABASE_TYPE, this);
            Discovery.get().registerService(Discovery.DATABASE_TYPE, data);
        }
        else
        {
            Discovery.get().removeServiceListener(Discovery.DATABASE_TYPE, this);
            Discovery.get().unregisterService(Discovery.DATABASE_TYPE);
        }
    }
    
    @Override
    public void serviceChange(String service, InetAddress ip, JSONObject data, boolean up)
    {
        if (ip.equals(Network.getPrimaryAddress()))
            return;
        if (up) {
            Database.d.mergeServerActivate(UUID.fromString((String)data.get("serverid")), (String)data.get("hostname"), ip.getHostAddress());
        } else {
            Database.d.mergeServerDeactivate(UUID.fromString((String)data.get("serverid")));
        }
        Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
    }
    
    
    @SuppressWarnings("unchecked")
    @Override
    public void event(MT type, Object data) 
    {
        if (type == MT.DATABASE_NOTIFICATION)
        {
            Set<String> tables = (Set<String>)data;
            if (tables.contains("mergeservers")) {
                model.setData(Database.d.getMergeServers());
            }
        }
    }
    
    static class DownloadNewSeriesAction extends AbstractAction
    {
        String host = null;
        public DownloadNewSeriesAction(String h) {
            super("Download New Series From ...");
            if (h != null) {
                host = h;
                putValue(Action.NAME, "Download New Series From " + host);
            }
        }
        public void actionPerformed(ActionEvent e) {
            HostSeriesSelectionDialog hd = new HostSeriesSelectionDialog(true);
            if (host != null)
                hd.selectHost(host);
            if (!hd.doDialog("Select Host and Series", null))
                return;

            HSResult ret = hd.getResult();
            Database.d.verifyUserAndSeries(ret.series, ret.password);
            Database.d.mergeServerUpdateNow(ret.host.getServerId());
            Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
        }
    }   
    
    static class MergeWithHomeAction extends AbstractAction
    {
        public MergeWithHomeAction() {
            super("Sync With " + Prefs.getHomeServer());
        }
        public void actionPerformed(ActionEvent e) {
            Database.d.mergeServerUpdateNow(IdGenerator.generateV5DNSId(Prefs.getHomeServer()));
            Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
        }
    }
    
    static class MergeWithAction extends AbstractAction
    {
        public MergeWithAction() {
            super("Sync With ...");
        }
        public void actionPerformed(ActionEvent e) {
            HostSeriesSelectionDialog d = new HostSeriesSelectionDialog(false);
            if (!d.doDialog("Select Host", null))
                return;
            Database.d.mergeServerUpdateNow(d.getResult().host.getServerId());
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
    
    class LocalDiscoveryAction extends AbstractAction
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
            updateDiscoverySetting(on);
        }
    }
    
    class ClearOldDiscoveredAction extends AbstractAction
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


    /** 
     * A main interface for testing datasync interface by itself
     * @param args ignored
     * @throws InterruptedException ignored
     * @throws NoSuchAlgorithmException ignored
     */
    public static void main(String[] args) throws InterruptedException, NoSuchAlgorithmException
    {
        AppSetup.appSetup("datasync");

        Database.openPublic(true);
        DataSyncWindow v = new DataSyncWindow();
        v.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        v.setVisible(true);
        while (true)
        {
            Thread.sleep(2000);
        }
    }    
}
