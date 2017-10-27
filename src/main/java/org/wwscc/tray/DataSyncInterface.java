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
import javax.swing.JSeparator;
import javax.swing.UIManager;

import org.json.simple.JSONObject;
import org.wwscc.dialogs.HostSeriesSelectionDialog;
import org.wwscc.dialogs.SeriesDialog;
import org.wwscc.dialogs.HostSeriesSelectionDialog.HSResult;
import org.wwscc.storage.Database;
import org.wwscc.storage.MergeServer;
import org.wwscc.storage.PostgresqlDatabase;
import org.wwscc.util.Discovery;
import org.wwscc.util.Discovery.DiscoveryListener;
import org.wwscc.util.Logging;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.Network;
import org.wwscc.util.Prefs;

import net.miginfocom.swing.MigLayout;

public class DataSyncInterface extends JFrame implements MessageListener, DiscoveryListener
{
    private static final Logger log = Logger.getLogger(DataSyncInterface.class.getName());

    MergeStatusTable table;
    boolean done;

    public DataSyncInterface()
    {
        super("Data Synchronization");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        table = new MergeStatusTable();
        Action syncallnow = new MergeWithAllLocalAction();
        JLabel header = new JLabel("Sync Status");
        header.setFont(header.getFont().deriveFont(18.0f).deriveFont(Font.BOLD));
        
        JPanel content = new JPanel(new MigLayout("fill", "", "[grow 0][fill]"));
        content.add(header, "split");
        content.add(new JButton(syncallnow), "gapleft 10, wrap");
        content.add(new JScrollPane(table), "grow");
        setContentPane(content);
        
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        bar.add(file);      
        file.add(new MergeWithAction());
        file.add(syncallnow);
        file.add(new JSeparator());
        file.add(new DownloadNewSeriesAction());
        file.add(new DeleteLocalSeriesAction());

        JMenu adv = new JMenu("Advanced");
        bar.add(adv);
        adv.add(new JCheckBoxMenuItem(new LocalDiscoveryAction()));
        adv.add(new ResetHashAction());
        
        setJMenuBar(bar);
        setBounds(Prefs.getWindowBounds("datasync"));
        Prefs.trackWindowBounds(this, "datasync");
        discoveryChange(Prefs.getAllowDiscovery());
        new UpdaterThread().start();
    }

    public void shutdown()
    {
        done = true;
    }

    class UpdaterThread extends Thread
    {
        @Override
        public void run()
        {
            done = false;
            // These two should always be there
            Database.d.mergeServerSetLocal(Network.getLocalHostName(), Network.getPrimaryAddress().getHostAddress(), 10);
            Database.d.mergeServerSetRemote("scorekeeper.wwscc.org", "", 10);

            Messenger.register(MT.DATABASE_NOTIFICATION, DataSyncInterface.this);

            // force an update on start, on the event thread
            Messenger.sendEvent(MT.DATABASE_NOTIFICATION, new HashSet<String>(Arrays.asList("mergeservers")));

            // other wise, we just ping/poke the database which is the only way to receive any NOTICE events
            while (!done) {
                try {
                    Database.d.ping();
                    Thread.sleep(1000);
                } catch (Exception e) {
                    log.log(Level.WARNING, e.toString(), e);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void discoveryChange(boolean up) 
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
                table.setData(Database.d.getMergeServers());
            }
        }
    }
    
    static class DownloadNewSeriesAction extends AbstractAction
    {
        public DownloadNewSeriesAction() {
            super("Download New Series From");
        }
        public void actionPerformed(ActionEvent e) {
            HostSeriesSelectionDialog hd = new HostSeriesSelectionDialog(true);
            if (!hd.doDialog("Select Host and Series", null))
                return;

            HSResult ret = hd.getResult();
            Database.d.verifyUserAndSeries(ret.series, ret.password);
            Database.d.mergeServerUpdateNow(ret.host.getServerId());
            Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
        }
    }   
    
    static class MergeWithAction extends AbstractAction
    {
        public MergeWithAction() {
            super("Sync With Host Now");
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
        public LocalDiscoveryAction() {
            setNewState(Prefs.getAllowDiscovery());
        }
        public void actionPerformed(ActionEvent e) {
            setNewState(((AbstractButton)e.getSource()).getModel().isSelected());
        }
        private void setNewState(boolean on) {
            putValue(Action.SELECTED_KEY, on);
            putValue(Action.NAME, "Local Discovery " + (on ? "On":"Off"));
            Prefs.setAllowDiscovery(on);
            discoveryChange(on);
        }
    }

    static class DeleteLocalSeriesAction extends AbstractAction
    {
        public DeleteLocalSeriesAction() {
            super("Delete Local Series Copy");
        }
        public void actionPerformed(ActionEvent e) {
            SeriesDialog sd = new SeriesDialog("Select the local series to delete", PostgresqlDatabase.getSeriesList(null).toArray(new String[0]));
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
        System.setProperty("swing.defaultlaf", UIManager.getSystemLookAndFeelClassName());
        System.setProperty("program.name", "DataSyncTestMain");
        Logging.logSetup("datasync");

        Database.openPublic(true);
        DataSyncInterface v = new DataSyncInterface();
        v.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        v.setVisible(true);
        while (true)
        {
            Thread.sleep(2000);
        }
    }    
}
