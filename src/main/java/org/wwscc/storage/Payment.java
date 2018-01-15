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
import java.util.UUID;

public class Payment
{
    protected UUID payid;
    protected UUID eventid;
    protected UUID carid;
    protected String refid;
    protected String txtype;
    protected String txid;
    protected Timestamp txtime;
    protected String itemname;
    protected double amount;

    public Payment()
    {
    }

    public Payment(ResultSet rs) throws SQLException
    {
        payid    = (UUID)rs.getObject("payid");
        eventid  = (UUID)rs.getObject("eventid");
        carid    = (UUID)rs.getObject("carid");
        refid    = rs.getString("refid");
        txtype   = rs.getString("txtype");
        txid     = rs.getString("txid");
        txtime   = rs.getTimestamp("txtime", Database.utc);
        itemname = rs.getString("itemname");
        amount   = rs.getDouble("amount");
    }

    public UUID getCarId()    { return carid; }
    public UUID getEventId()  { return eventid; }
    public double getAmount() { return amount; }
    public String getTxType() { return txtype; }
}
