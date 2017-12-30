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
import java.sql.Timestamp;
import java.util.LinkedList;
import java.util.UUID;

public class Registration
{
    protected UUID eventid;
    protected UUID carid;
    protected String txid;
    protected Timestamp txtime;
    protected String itemname;
    protected double amount;

    public Registration()
    {
    }

    public Registration(ResultSet rs) throws SQLException
    {
        eventid  = (UUID)rs.getObject("eventid");
        carid    = (UUID)rs.getObject("carid");
        txid     = rs.getString("txid");
        txtime   = rs.getTimestamp("txtime");
        itemname = rs.getString("itemname");
        amount   = rs.getDouble("amount");
    }

    public LinkedList<Object> getValues()
    {
        LinkedList<Object> ret = new LinkedList<Object>();
        ret.add(eventid);
        ret.add(carid);
        ret.add(txid);
        ret.add(txtime);
        ret.add(itemname);
        ret.add(amount);
        return ret;
    }

    public UUID getCarId()    { return carid; }
    public UUID getEventId()  { return eventid; }
    public double getAmount() { return amount; }
}
