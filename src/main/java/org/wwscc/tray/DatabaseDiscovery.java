package org.wwscc.tray;

import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import org.wwscc.storage.Database;
import org.wwscc.util.IdGenerator;
import org.wwscc.util.Network;
import org.wwscc.util.Prefs;

/**
 * Database discovery is responsible for both discovering other live merge servers as well
 * as advertising this one.
 */
public class DatabaseDiscovery implements ServiceListener
{
    private static final Logger log = Logger.getLogger(DatabaseDiscovery.class.getName());
    public static final String DATABASE_TYPE = "_postgresql._tcp.local.";
    public static final int DATABASE_PORT = 54329;
    
    JmDNS jmdns;

    public DatabaseDiscovery()
    {
        try {
            jmdns = JmDNS.create(Network.getPrimaryAddress(), IdGenerator.generateId().toString());
            jmdns.registerService(ServiceInfo.create(DATABASE_TYPE, Prefs.getServerId().toString(), DATABASE_PORT, Network.getLocalHostName()));
            jmdns.addServiceListener(DATABASE_TYPE, this);
            Runtime.getRuntime().addShutdownHook(new Thread() {
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
            });            
        } catch (IOException ioe) {
            log.log(Level.WARNING, "Failed to start database info broadcaster.  Local sync will not work. " + ioe, ioe);
        }            
    }
    
    private void updateDatabase(ServiceEvent event, boolean up)
    {
        ServiceInfo info = event.getInfo();
        byte[] bytes = info.getTextBytes();
        String hostname = new String(bytes, 1, bytes[0]); // weird jmdns encoding
        if (up) {           
            Database.d.mergeServerActivate(UUID.fromString(info.getName()), hostname, info.getInet4Addresses()[0].getHostAddress());
        } else {
            Database.d.mergeServerDeactivate(UUID.fromString(info.getName()));
        }
    }
    
    @Override
    public void serviceAdded(ServiceEvent event) {}
    @Override
    public void serviceRemoved(ServiceEvent event) { updateDatabase(event, false); }
    @Override
    public void serviceResolved(ServiceEvent event) { updateDatabase(event, true); }
}

