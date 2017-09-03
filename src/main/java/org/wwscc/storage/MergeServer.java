/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.storage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.wwscc.util.IdGenerator;

public class MergeServer
{
    protected UUID       serverid;
    protected String     hostname;
    protected String     address;
    protected int        waittime;
    protected Timestamp  lastcheck;
    protected Timestamp  nextcheck;
    protected boolean    active;
    protected boolean    oneshot;
    protected Map<String, JSONObject> seriesstate;
        
    @Override
    public String toString()
    {
        return String.format("%s/%s", serverid, hostname);
    }
    
    public MergeServer(MergeServer m)
    {
        serverid    = m.serverid;
        hostname    = m.hostname;
        address     = m.address;
        waittime    = m.waittime;
        lastcheck   = m.lastcheck;
        nextcheck   = m.nextcheck;
        active      = m.active;
        oneshot     = m.oneshot;
        seriesstate = m.seriesstate;
    }
    
    public MergeServer(ResultSet rs) throws SQLException
    {
        serverid    = (UUID)rs.getObject("serverid");
        hostname    = rs.getString("hostname");
        address     = rs.getString("address");
        waittime    = rs.getInt("waittime");
        lastcheck   = rs.getTimestamp("lastcheck", Database.utc);
        nextcheck   = rs.getTimestamp("nextcheck", Database.utc);
        active      = rs.getBoolean("active");
        oneshot     = rs.getBoolean("oneshot");
        seriesstate = new HashMap<String, JSONObject>();
        try {
            JSONObject mergestate = (JSONObject)new JSONParser().parse(rs.getString("mergestate"));
            for (Object o : mergestate.keySet()) {
                seriesstate.put((String)o, (JSONObject)mergestate.get(o));
            }
        } catch (ParseException e) {
        }
    }

    public UUID getServerId()         { return serverid; }
    public String getHostname()       { return hostname; }
    public String getAddress()        { return address;  }
    public boolean isActive()         { return active;   }
    public boolean isOneShot()        { return oneshot;    }
    public Timestamp getLastCheck()   { return lastcheck;  }
    public Timestamp getNextCheck()   { return nextcheck;  }
    public int getWaitTime()          { return waittime; }
    public Set<String> getSeriesSet() { return seriesstate.keySet(); }
    public JSONObject getSeriesState(String series) { return seriesstate.get(series); }
    
    public boolean isLocalHost()      { return serverid.equals(IdGenerator.nullid); }
}
