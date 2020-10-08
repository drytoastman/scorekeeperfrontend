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
    protected UUID driverid;
    protected UUID carid;
    protected String session;
    protected String txtype;
    protected String txid;
    protected Timestamp txtime;
    protected String itemname;
    protected String accountid;
    protected boolean refunded;
    protected int amountInCents;

    public Payment()
    {
    }

    public Payment(ResultSet rs) throws SQLException
    {
        payid    = (UUID)rs.getObject("payid");
        eventid  = (UUID)rs.getObject("eventid");
        driverid = (UUID)rs.getObject("driverid");
        carid    = (UUID)rs.getObject("carid");
        session  = rs.getString("session");
        txtype   = rs.getString("txtype");
        txid     = rs.getString("txid");
        txtime   = rs.getTimestamp("txtime", Database.utc);
        itemname = rs.getString("itemname");
        accountid = rs.getString("accountid");
        refunded = rs.getBoolean("refunded");
        amountInCents = rs.getInt("amount");
    }

    public UUID getPayId()    { return payid; }
    public UUID getCarId()    { return carid; }
    public UUID getEventId()  { return eventid; }
    public int getAmountInCents() { return amountInCents; }
    public double getAmount() { return amountInCents/100.0; }
    public String getTxType() { return txtype; }
}
