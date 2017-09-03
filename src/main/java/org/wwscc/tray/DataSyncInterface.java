/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.tray;

import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import org.wwscc.storage.Database;
import org.wwscc.util.Logging;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.Network;
import org.wwscc.util.Prefs;

import net.miginfocom.swing.MigLayout;

public class DataSyncInterface extends JFrame implements MessageListener
{
    private static final Logger log = Logger.getLogger(DataSyncInterface.class.getName());

    MergeStatusTable table;
    
    public DataSyncInterface()
    {
        super("Data Synchronization");        
        setLayout(new MigLayout("fill", "fill", "fill"));
        
        table = new MergeStatusTable();
        getContentPane().add(new JScrollPane(table), "grow");
        setBounds(Prefs.getWindowBounds("datasync"));
        setVisible(true);

        Database.d.mergeServerSetLocal(Network.getLocalHostName(), Network.getPrimaryAddress().getHostAddress());
        Messenger.register(MT.DATABASE_NOTIFICATION, this);
        new UpdaterThread().start();
        Prefs.trackWindowBounds(this, "datasync");
    }
    
    class UpdaterThread extends Thread
    {
        boolean done = false;

        @Override
        public void run()
        {
            int counter = 0;
            Set<String> tables = new HashSet<String>();
            tables.add("mergeservers");
            Messenger.sendEvent(MT.DATABASE_NOTIFICATION, tables);
            
            while (!done) {
                try {
                    Database.d.ping();
                    Thread.sleep(1000);
                    if (counter++ % 10 == 0)
                        Messenger.sendEvent(MT.DATABASE_NOTIFICATION, tables);
                } catch (Exception e) {
                    log.log(Level.WARNING, e.toString(), e);
                }
            }
        }
    }
    
    
    @SuppressWarnings("unchecked")
    @Override
    public void event(MT type, Object data) 
    {
        System.out.println("type = " + type + ", data = " + data);
        if (type == MT.DATABASE_NOTIFICATION)
        {
            Set<String> tables = (Set<String>)data;
            if (tables.contains("mergeservers")) {
                table.setData(Database.d.getMergeServers());
            }
        }
    }
    
        
    public static void main(String[] args) throws InterruptedException, NoSuchAlgorithmException
    {
        System.setProperty("swing.defaultlaf", UIManager.getSystemLookAndFeelClassName());
        System.setProperty("program.name", "DataSyncTestMain");
        Logging.logSetup("datasync");
                
        Database.openPublic();
        //Database.d.mergeServerSet(IdGenerator.generateV5DNSId("scorekeeper.wwscc.org"), "scorekeeper.wwscc.org", false, true);
        
        DataSyncInterface v = new DataSyncInterface();
        v.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        v.setVisible(true);
        while (true)
        {
            Thread.sleep(2000);
        }
    }    
}
