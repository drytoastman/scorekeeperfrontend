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
import java.time.LocalDate;
import java.util.LinkedList;
import java.util.UUID;

import org.wwscc.util.IdGenerator;

public class WeekendMember extends AttrBase
{
    protected UUID uniqueid;
    protected int membership;
    protected UUID driverid;
    protected LocalDate startdate;
    protected LocalDate enddate;
    protected String issuer;
    protected String issuermem;
    protected String region;
    protected String area;

    public WeekendMember(UUID driverid, LocalDate start, LocalDate end)
    {
        this.uniqueid  = IdGenerator.generateId();
        this.membership = -1;
        this.driverid  = driverid;
        this.startdate = start;
        this.enddate   = end;
        this.issuer    = "";
        this.issuermem = "";
        this.region    = "";
        this.area      = "autocross";
    }

    public WeekendMember(ResultSet rs) throws SQLException
    {
        uniqueid   = (UUID)rs.getObject("uniqueid");
        membership = rs.getInt("membership");
        driverid   = (UUID)rs.getObject("driverid");
        startdate  = rs.getObject("startdate", LocalDate.class);
        enddate    = rs.getObject("enddate", LocalDate.class);
        issuer     = rs.getString("issuer");
        issuermem  = rs.getString("issuermem");
        region     = rs.getString("region");
        area       = rs.getString("area");
    }

    public LinkedList<Object> getValues()
    {
        LinkedList<Object> ret = new LinkedList<Object>();
        ret.add(uniqueid);
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
    public LocalDate getStartDate()   { return startdate; }
    public LocalDate getEndDate()     { return enddate; }
    public Integer getMemberId()      { return membership; }

    public void setDriverId(UUID id)   { driverid = id; }
    public void setRegion(String s)    { region = s; }
    public void setIssuer(String s)    { issuer = s; }
    public void setIssuerMem(String s) { issuermem = s; }
}
