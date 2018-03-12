/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.storage;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.wwscc.util.IdGenerator;


public class MergeServer
{
    private static Logger log = Logger.getLogger(MergeServer.class.getCanonicalName());
    private static ObjectMapper objectMapper = new ObjectMapper();

    public static enum HostState {
        ACTIVE,
        ONESHOT,
        INACTIVE,
        UNKNOWN,
    };

    protected UUID       serverid;
    protected String     hostname;
    protected String     address;
    protected Timestamp  lastcheck;
    protected Timestamp  nextcheck;
    protected int        waittime;
    protected int        ctimeout;
    protected int        cfailures;
    protected HostState  hoststate;
    protected Map<String, ObjectNode> seriesstate;

    @Override
    public String toString()
    {
        if (!hostname.equals(""))
            return hostname;
        else if (!address.equals(""))
            return address;
        else
            return serverid.toString();
    }

    public MergeServer(MergeServer m)
    {
        serverid    = m.serverid;
        hostname    = m.hostname;
        address     = m.address;
        lastcheck   = m.lastcheck;
        nextcheck   = m.nextcheck;
        waittime    = m.waittime;
        ctimeout    = m.ctimeout;
        cfailures   = m.cfailures;
        hoststate   = m.hoststate;
        seriesstate = m.seriesstate;
    }

    public MergeServer(ResultSet rs) throws SQLException
    {
        serverid    = (UUID)rs.getObject("serverid");
        hostname    = rs.getString("hostname");
        address     = rs.getString("address");
        lastcheck   = rs.getTimestamp("lastcheck", Database.utc);
        nextcheck   = rs.getTimestamp("nextcheck", Database.utc);
        waittime    = rs.getInt("waittime");
        ctimeout    = rs.getInt("ctimeout");
        cfailures   = rs.getInt("cfailures");
        String hs   = rs.getString("hoststate");
        switch (hs) {
            case "A": hoststate = HostState.ACTIVE; break;
            case "1": hoststate = HostState.ONESHOT; break;
            case "I": hoststate = HostState.INACTIVE; break;
            default:  hoststate = HostState.UNKNOWN; break;
        }
        seriesstate = new HashMap<String, ObjectNode>();
        try {
            ObjectNode mergestate = (ObjectNode) objectMapper.readTree(rs.getString("mergestate"));
            mergestate.fields().forEachRemaining(field ->  {
                seriesstate.put(field.getKey(), (ObjectNode)field.getValue());
            });
        } catch (IOException e) {
            log.warning("Failed to parse mergestate JSON: " + e);
        }
    }

    public UUID getServerId()         { return serverid;  }
    public String getHostname()       { return hostname;  }
    public String getAddress()        { return address;   }
    public Timestamp getLastCheck()   { return lastcheck; }
    public Timestamp getNextCheck()   { return nextcheck; }
    public int getWaitTime()          { return waittime;  }
    public int getConnectTimeout()    { return ctimeout;  }
    public int getConnectFailures()   { return cfailures; }
    public HostState getHostState()   { return hoststate; }
    public Set<String> getSeriesSet() { return seriesstate.keySet(); }
    public boolean isLocalHost()      { return serverid.equals(IdGenerator.nullid); }

    public boolean isActive()         { return ((hoststate == HostState.ACTIVE) || (hoststate == HostState.ONESHOT)); }
    public boolean isRemote()         { return address.equals(""); }

    public String getConnectEndpoint()
    {
        if (address.equals(""))
            return hostname;
        return address;
    }

    public ObjectNode getSeriesState(String series)
    {
        return seriesstate.get(series);
    }

    public String getDriversState()
    {
        for (ObjectNode o : seriesstate.values())
        {  // just find any active series and get the drivers table hash from there
            ObjectNode hashes = (ObjectNode)o.get("hashes");
            if (hashes.size() == 0) continue;
            return hashes.get("drivers").asText();
        }
        return "";
    }
}
