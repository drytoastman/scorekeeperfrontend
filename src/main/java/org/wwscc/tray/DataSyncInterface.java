package org.wwscc.tray;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
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

import org.apache.commons.io.IOUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.wwscc.util.Logging;
import org.wwscc.util.Network;
import org.wwscc.util.Prefs;

import net.miginfocom.swing.MigLayout;

@SuppressWarnings("unchecked") // pretty much anything with simple-json
public class DataSyncInterface extends JFrame implements ServiceListener 
{
    private static final Logger log = Logger.getLogger(DataSyncInterface.class.getName());

    JmDNS jmdns;
    JSONObject myinfo;
    
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
        
        myinfo = new JSONObject();
        myinfo.put("x", 66);
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
            jmdns = JmDNS.create(Network.getPrimaryAddress());
            jmdns.registerService(ServiceInfo.create("_postgresql._tcp.local.", Prefs.getServerId().toString(), 54329, ""));
            jmdns.addServiceListener("_postgresql._tcp._local.", this);
            Runtime.getRuntime().addShutdownHook(new AutoCloseHook());
        } catch (IOException ioe) {
            log.log(Level.WARNING, "Failed to start database info broadcaster.  Local sync will not work. " + ioe, ioe);
        }    
    }
    
    private JSONObject doExchange()
    {
        JSONObject ret = null;
        
        try 
        {
            HttpURLConnection con = (HttpURLConnection)new URL("http://127.0.0.1").openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            
            OutputStream out = con.getOutputStream();
            IOUtils.write(myinfo.toJSONString(), out);
            out.close();
            
            int code = con.getResponseCode();
            if (code == 200) {
                ret = (JSONObject)new JSONParser().parse(IOUtils.toString(con.getInputStream(), "UTF-8"));
            } else {
                log.info("Error from sync backend: " + IOUtils.toString(con.getErrorStream(), "UTF-8"));
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Error talking to sync backend: " + e, e);
        }
        
        return ret;
    }

    class ServerInfo
    {
        UUID   serverid;
        String name;
        String ip;
        Date   time;
        String hash;
    }
    
    @Override
    public void serviceAdded(ServiceEvent event) {}
    @Override
    public void serviceRemoved(ServiceEvent event) { log.info("removed: " + event); }
    @Override
    public void serviceResolved(ServiceEvent event) { log.info("resolved: " + event);}
    
    public static void main(String[] args) throws InterruptedException
    {
        System.setProperty("swing.defaultlaf", UIManager.getSystemLookAndFeelClassName());
        System.setProperty("program.name", "DataSyncTestMain");
        Logging.logSetup("datasync");
        
        DataSyncInterface v = new DataSyncInterface();
        v.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        v.setVisible(true);
        v.openConnections();
        while (true)
        {
            Thread.sleep(1000);
            System.err.println("ret = " + v.doExchange());
        }
    }
    
}
