/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2019 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system;

import java.io.IOException;
import java.net.InetAddress;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.wwscc.storage.Database;
import org.wwscc.storage.MergeServer;
import org.wwscc.util.BroadcastState;
import org.wwscc.util.Discovery;
import org.wwscc.util.Discovery.DiscoveryListener;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.wwscc.util.Network;
import org.wwscc.util.Prefs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Thread to keep checking pinging the database to cause notifications
 * for the discovery pieces. It can be 'paused' when the database is to be offline.
 */
public class FrontEndMonitor extends MonitorBase implements DiscoveryListener
{
    private static ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger log = Logger.getLogger(FrontEndMonitor.class.getName());

    boolean paused;
    boolean backendready;
    BroadcastState<InetAddress> address;
    ObjectNode neighbors;

    public FrontEndMonitor()
    {
        super("FrontEndMonitor", 3000);
        paused = true; // we start in the 'paused' state
        backendready = false;
        address = new BroadcastState<InetAddress>(MT.NETWORK_CHANGED, null);
        neighbors = objectMapper.createObjectNode();

        Messenger.register(MT.DISCOVERY_CHANGE,   (type, data) -> updateDiscoverySetting((boolean)data));
        Messenger.register(MT.BACKEND_CONTAINERS, (type, data) -> {
            String s = (String)data;
            backendready = s.contains("db") && s.contains("sync");
            setPause(!backendready);
            poke();
        });
    }


    @Override
    public boolean minit()
    {
        address.set(Network.getPrimaryAddress());

        while (!backendready)
            donefornow();

        Actions.InitServersAction.doinit();

        // We only start (or not) the discovery thread once we've set our data into the database so there is something to announce
        updateDiscoverySetting(Prefs.getAllowDiscovery());

        return true;
    }

    @Override
    public void mloop()
    {
        address.set(Network.getPrimaryAddress());
        // we update with our current address which causes the database to send us a NOTICE event which causes the GUI to update
        if (!paused && (address.get() != null)) {
            Database.d.mergeServerSetLocal(Network.getLocalHostName(), address.get().getHostAddress(), 10);
        }
    }

    @Override
    public void mshutdown()
    {
        // Database is already closed at this point, can't do anything else
    }

    public void setPause(boolean b)
    {
        paused = b;
    }

    private void updateDiscoverySetting(boolean up)
    {
        try
        {
            if (up)
            {
                ObjectNode data = new ObjectNode(JsonNodeFactory.instance);
                data.put("serverid", Prefs.getServerId().toString());
                data.put("hostname", Network.getLocalHostName());
                Discovery.get().addServiceListener(this);
                Discovery.get().registerService(Prefs.getServerId(), Discovery.DATABASE_TYPE, data);
                Messenger.sendEvent(MT.DISCOVERY_OK, true);
                MDNSProxy.start();
            }
            else
            {
                MDNSProxy.stop();
                Messenger.sendEvent(MT.DISCOVERY_OK, false);
                Discovery.get().removeServiceListener(this);
                Discovery.get().unregisterService(Prefs.getServerId(), Discovery.DATABASE_TYPE);

                neighbors.removeAll();
                Database.d.recordCache("neighbors", "{}");
                for (MergeServer m : Database.d.getMergeServers()) {
                    if (!m.isRemote() && m.isActive()) {
                        Database.d.mergeServerDeactivate(m.getServerId());
                    }
                }
            }
        }
        catch (IOException ioe)
        {
            log.log(Level.WARNING, "discovery settings change failure: " + ioe, ioe);
        }
    }

    @Override
    public void serviceChange(UUID serverid, String service, InetAddress src, ObjectNode data, boolean up)
    {
        ArrayNode a = neighbors.withArray(src.getHostAddress());
        if (up)  {
            if (a.findValue(service) == null)
                a.add(service);
        } else {
            for (int ii = 0; ii < a.size(); ii++) {
                if (a.get(ii).asText().equals(service)) {
                    a.remove(ii);
                    break;
                }
            }
        }

        try {
            Database.d.recordCache("neighbors", objectMapper.writeValueAsString(neighbors));
        } catch (JsonProcessingException e) {
            log.warning("Failed to write neighbors cache: " + e);
        }

        if (!service.equals(Discovery.DATABASE_TYPE))
            return;
        if (serverid.equals(Prefs.getServerId()) || serverid.equals(MergeServer.LOCALHOST))
            return;
        if (up) {
            Database.d.mergeServerActivate(serverid, data.get("hostname").asText(), src.getHostAddress());
        } else {
            Database.d.mergeServerDeactivate(serverid);
        }
        Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
    }
}
