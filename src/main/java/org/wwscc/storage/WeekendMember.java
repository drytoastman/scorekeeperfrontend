/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.storage;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.UUID;

public class WeekendMember extends AttrBase
{
    protected int membership;
    protected UUID driverid;
    protected Date startdate;
    protected Date enddate;
    protected String issuer;
    protected String issuermem;
    protected String region;
    protected String area;

    public WeekendMember(UUID driverid, Date start, Date end, String issuer, String issuermem, String region, String area)
    {
        this.membership = -1;
        this.driverid  = driverid;
        this.startdate = start;
        this.enddate   = end;
        this.issuer    = issuer;
        this.issuermem = issuermem;
        this.region    = region;
        this.area      = area;
    }

    public WeekendMember(ResultSet rs) throws SQLException
    {
        super(rs);
        membership = rs.getInt("membership");
        driverid   = (UUID)rs.getObject("driverid");
        startdate  = rs.getDate("startdate");
        enddate    = rs.getDate("enddate");
        issuer     = rs.getString("issuer");
        issuermem  = rs.getString("issuermem");
        region     = rs.getString("region");
        area       = rs.getString("area");
    }

    public LinkedList<Object> getValues()
    {
        LinkedList<Object> ret = new LinkedList<Object>();
        ret.add(membership);
        ret.add(driverid);
        ret.add(startdate);
        ret.add(enddate);
        ret.add(issuer);
        ret.add(issuermem);
        ret.add(region);
        ret.add(area);
        return ret;
    }

    public UUID getDriverId()         { return driverid; }
    public void setDriverId(UUID id)  { driverid = id; }
}
