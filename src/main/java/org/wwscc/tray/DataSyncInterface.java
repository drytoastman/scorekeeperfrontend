/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.tray;

import java.awt.Font;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
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
        
        JPanel content = new JPanel(new MigLayout("fill", "fill", "fill"));
        
        table = new MergeStatusTable();
        
        JLabel header = new JLabel("Sync Status");
        header.setFont(header.getFont().deriveFont(18.0f).deriveFont(Font.BOLD));
        content.add(header, "wrap");
        content.add(new JScrollPane(table), "grow");
        setContentPane(content);
        setJMenuBar(new Controls());
        setBounds(Prefs.getWindowBounds("datasync"));
        setVisible(true);

        Database.openPublic(true);
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
            Set<String> tables = new HashSet<String>();
            tables.add("mergeservers");
            Messenger.sendEvent(MT.DATABASE_NOTIFICATION, tables);
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
    
        
    public static void main(String[] args) throws InterruptedException, NoSuchAlgorithmException
    {
        System.setProperty("swing.defaultlaf", UIManager.getSystemLookAndFeelClassName());
        System.setProperty("program.name", "DataSyncTestMain");
        Logging.logSetup("datasync");
                
        DockerMachine.machineenv();
        DataSyncInterface v = new DataSyncInterface();
        v.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        v.setVisible(true);
        while (true)
        {
            Thread.sleep(2000);
        }
    }    
}
