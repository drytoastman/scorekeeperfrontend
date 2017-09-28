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
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.wwscc.util.Network;
import org.wwscc.util.Prefs;

/**
 * Database discovery is responsible for both discovering other live merge servers as well
 * as advertising this one.  The constructor should only be called from a non-event thread
 * as startup can take up to 6 seconds.
 */
public class DatabaseDiscovery implements ServiceListener
{
    private static final Logger log = Logger.getLogger(DatabaseDiscovery.class.getName());
    public static final String DATABASE_TYPE = "_postgresql._tcp.local.";
    public static final int DATABASE_PORT = 54329;

    JmDNS jmdns = null;

    public DatabaseDiscovery()
    {
        try {
            log.finer("Starting JmDNS server");
            jmdns = JmDNS.create(Network.getPrimaryAddress(), IdGenerator.generateId().toString());
            jmdns.registerService(ServiceInfo.create(DATABASE_TYPE, Prefs.getServerId().toString(), DATABASE_PORT, Network.getLocalHostName()));
            jmdns.addServiceListener(DATABASE_TYPE, this);
            Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownTask()));
        } catch (IOException ioe) {
            log.log(Level.WARNING, "Failed to start database info broadcaster.  Local sync will not work. " + ioe, ioe);
        }
    }

    public void shutdown()
    {
        if (jmdns != null) {
            new Thread(new ShutdownTask()).start();
        }
    }

    private class ShutdownTask implements Runnable
    {
        @Override
        public void run()
        {
            try {
                JmDNS obj = jmdns;
                jmdns = null;  // nullify the main attribute before the long unregister operations
                if (obj != null) {
                    obj.unregisterAllServices();
                    obj.close();
                }
            } catch (Exception ioe) {
                log.info("Error shutting down database announcements");
            }
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
        Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
    }

    @Override
    public void serviceAdded(ServiceEvent event) {}
    @Override
    public void serviceRemoved(ServiceEvent event) { updateDatabase(event, false); }
    @Override
    public void serviceResolved(ServiceEvent event) { updateDatabase(event, true); }
}

