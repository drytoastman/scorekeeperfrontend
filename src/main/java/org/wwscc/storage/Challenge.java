/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.storage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.UUID;

/**
 */
public class Challenge
{
    //private static final Logger log = Logger.getLogger("org.wwscc.storage.Challenge");

    protected UUID challengeid;
    protected UUID eventid;
    protected String name;
    protected int depth;

    protected Challenge()
    {
    }

    public Challenge(ResultSet rs) throws SQLException
    {
        challengeid = (UUID)rs.getObject("challengeid");
        eventid     = (UUID)rs.getObject("eventid");
        name        = rs.getString("name");
        depth       = rs.getShort("depth");
    }

    public LinkedList<Object> getValues()
    {
        LinkedList<Object> ret = new LinkedList<Object>();
        ret.add(challengeid);
        ret.add(eventid);
        ret.add(name);
        ret.add(depth);
        return ret;
    }

    public UUID getChallengeId() { return challengeid; }
    public UUID getEventId() { return eventid; }
    public String getName() { return name; }
    public int getDepth() { return depth; }

    public int getBaseRounds() { return (int)Math.pow(2, depth-1); }
    public int getMinEntrantCount() { return getBaseRounds()+1; }
    public int getMaxEntrantCount() { return getBaseRounds()*2; }
    public int getFirstRoundNumber() { return getMaxEntrantCount() - 1; }

    public void setName(String s) { name = s; }

    @Override
    public String toString()
    {
        return name;
    }

    @Override
    public boolean equals(Object other)
    {
        return (other instanceof Challenge) && ((Challenge)other).getChallengeId().equals(challengeid);
    }
}
