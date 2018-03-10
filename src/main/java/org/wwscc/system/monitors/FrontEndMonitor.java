/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system.monitors;

import java.net.InetAddress;
import java.util.UUID;

import org.json.simple.JSONObject;
import org.wwscc.storage.Database;
import org.wwscc.system.Actions;
import org.wwscc.util.Discovery;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.wwscc.util.Network;
import org.wwscc.util.Prefs;
import org.wwscc.util.Discovery.DiscoveryListener;

/**
 * Thread to keep checking pinging the database to cause notifications
 * for the discovery pieces. It can be 'paused' when the database is to be offline.
 */
public class FrontEndMonitor extends Monitor implements DiscoveryListener
{
    boolean paused;
    boolean backendready;
    BroadcastState<InetAddress> address;

    public FrontEndMonitor()
    {
        super("FrontEndMonitor", 1000);
        paused = true; // we start in the 'paused' state
        backendready = false;
        address = new BroadcastState<InetAddress>(MT.NETWORK_CHANGED, null);

        Messenger.register(MT.DISCOVERY_CHANGE, (type, data) -> updateDiscoverySetting((boolean)data));
        Messenger.register(MT.BACKEND_READY,    (type, data) -> { backendready = (boolean)data; setPause(!backendready); });
    }


    @Override
    public boolean minit()
    {
        while (!backendready)
            donefornow();

        address.set(Network.getPrimaryAddress());
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

    @SuppressWarnings("unchecked")
    private void updateDiscoverySetting(boolean up)
    {
        if (up)
        {
            JSONObject data = new JSONObject();
            data.put("serverid", Prefs.getServerId().toString());
            data.put("hostname", Network.getLocalHostName());
            Discovery.get().addServiceListener(this);
            Discovery.get().registerService(Prefs.getServerId(), Discovery.DATABASE_TYPE, data);
        }
        else
        {
            Discovery.get().removeServiceListener(this);
            Discovery.get().unregisterService(Prefs.getServerId(), Discovery.DATABASE_TYPE);
        }
    }

    @Override
    public void serviceChange(UUID serverid, String service, JSONObject data, boolean up)
    {
        if (!service.equals(Discovery.DATABASE_TYPE))
            return;
        InetAddress ip = (InetAddress)data.get("ip");
        if (ip.equals(address.get()))
            return;
        if (up) {
            Database.d.mergeServerActivate(serverid, (String)data.get("hostname"), ip.getHostAddress());
        } else {
            Database.d.mergeServerDeactivate(serverid);
        }
        Messenger.sendEvent(MT.POKE_SYNC_SERVER, true);
    }
}
