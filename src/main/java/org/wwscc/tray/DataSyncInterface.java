package org.wwscc.tray;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import javax.swing.JFrame;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import org.wwscc.storage.Database;
import org.wwscc.util.IdGenerator;
import org.wwscc.util.Logging;
import org.wwscc.util.Network;
import org.wwscc.util.Prefs;

import net.miginfocom.swing.MigLayout;

public class DataSyncInterface extends JFrame implements ServiceListener 
{
    private static final Logger log = Logger.getLogger(DataSyncInterface.class.getName());
    public static final String DATABASE_TYPE = "_postgresql._tcp.local.";
    public static final int DATABASE_PORT = 54329;

    JmDNS jmdns;
    public DataSyncInterface()
    {
        super("Data Synchronization");
        setLayout(new MigLayout());
        
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        DefaultTreeModel model = new DefaultTreeModel(root);
        root.add(new DefaultMutableTreeNode("nwr2017"));
        root.add(new DefaultMutableTreeNode("ww2017"));
        
        add(new JTree(model));
        pack();
        
        Database.openPublic();
        Database.d.clearMergeServers();
        Database.d.updateMergeServer(Prefs.getServerId(), Network.getLocalHostName(), "localhost", false);
    }
    
    class AutoCloseHook extends Thread
    {
        @Override
        public void run()
        {
            try {
                jmdns.unregisterAllServices();
                jmdns.close();
            } catch (Exception ioe) {
                log.info("Error shutting down database announcements");
            }
        }
    }
    
    public void openConnections()
    {
        try {
            jmdns = JmDNS.create(Network.getPrimaryAddress(), IdGenerator.generateId().toString());
            jmdns.registerService(ServiceInfo.create(DATABASE_TYPE, Prefs.getServerId().toString(), DATABASE_PORT, Network.getLocalHostName()));
            jmdns.addServiceListener(DATABASE_TYPE, this);
            Runtime.getRuntime().addShutdownHook(new AutoCloseHook());
        } catch (IOException ioe) {
            log.log(Level.WARNING, "Failed to start database info broadcaster.  Local sync will not work. " + ioe, ioe);
        }    
    }
    
    /*
    class ServerInfo
    {
        UUID   serverid;
        String name;
        String ip;
        Date   time;
        String hash;
    }
    */
    
    private void updateDatabase(ServiceEvent event, boolean up)
    {
        ServiceInfo info = event.getInfo();
        byte[] bytes = info.getTextBytes();
        String hostname = new String(bytes, 1, bytes[0]); // weird jmdns encoding
        String ip = up ? info.getInet4Addresses()[0].getHostAddress() : "";
        Database.d.updateMergeServer(UUID.fromString(info.getName()), hostname, ip, true);
    }
    
    @Override
    public void serviceAdded(ServiceEvent event) {}
    @Override
    public void serviceRemoved(ServiceEvent event) { updateDatabase(event, false); }
    @Override
    public void serviceResolved(ServiceEvent event) { updateDatabase(event, true); }
    
    public static void main(String[] args) throws InterruptedException, NoSuchAlgorithmException
    {
        System.setProperty("swing.defaultlaf", UIManager.getSystemLookAndFeelClassName());
        System.setProperty("program.name", "DataSyncTestMain");
        Logging.logSetup("datasync");
        
        System.out.println(IdGenerator.generateV5DNSId("scorekeeper.wwscc.org"));
        
        DataSyncInterface v = new DataSyncInterface();
        v.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        v.setVisible(true);
        
        try {
            String fakeid = "9a8a4620-83b2-11e7-bd70-111111111111";
            JmDNS fake = JmDNS.create(Network.getPrimaryAddress(), IdGenerator.generateId().toString());                
            fake.registerService(ServiceInfo.create(DATABASE_TYPE, fakeid, DATABASE_PORT, Network.getLocalHostName()));
        } catch (IOException ioe) {
            log.log(Level.SEVERE, "Error", ioe);
        }

        v.openConnections();
        while (true)
        {
            Thread.sleep(2000);
        }
    }
    
}
