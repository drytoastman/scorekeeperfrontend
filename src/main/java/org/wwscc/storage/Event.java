/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.storage;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.UUID;
import org.wwscc.util.EventInfo;

/**
 * Represents a single event from the database.
 */
public class Event extends AttrBase implements Serializable
{
    private static final long serialVersionUID = 3721488283732959966L;

    protected UUID      eventid;
    protected String    name;
    protected LocalDate date;
    protected int       regtype;
    protected Timestamp regopened;
    protected Timestamp regclosed;
    protected int       courses;
    protected int       runs;
    protected int       countedruns;
    protected int       segments;
    protected int       perlimit; // per person
    protected int       totlimit; // for whole event
    protected double    conepen;
    protected double    gatepen;
    protected boolean   ispro;
    protected boolean   ispractice;

    @Override
    public String toString()
    {
        return name;
    }

    public Event()
    {
        super();
    }

    public Event(ResultSet rs) throws SQLException
    {
        super(rs);
        eventid     = (UUID)rs.getObject("eventid");
        name        = rs.getString("name");
        date        = rs.getObject("date", LocalDate.class);
        regtype     = rs.getInt("regtype");
        regopened   = rs.getTimestamp("regopened", Database.utc);
        regclosed   = rs.getTimestamp("regclosed", Database.utc);
        courses     = rs.getInt("courses");
        runs		= rs.getInt("runs");
        countedruns = rs.getInt("countedruns");
        segments    = rs.getInt("segments");
        perlimit 	= rs.getInt("perlimit");
        totlimit 	= rs.getInt("totlimit");
        conepen     = rs.getDouble("conepen");
        gatepen 	= rs.getDouble("gatepen");
        ispro       = rs.getBoolean("ispro");
        ispractice  = rs.getBoolean("ispractice");
    }

    public UUID getEventId() { return eventid; }
    public int getRuns() { return runs; }
    public int getCourses() { return courses; }
    public boolean isPro() { return ispro; }
    public double getConePenalty() { return conepen; }
    public double getGatePenalty() { return gatepen; }

    public void setRuns(int r) { runs = r; }

    /**
     * Create the util based EventInfo structure (non-storage dependent)
     * @return EventInfo object based on this Event
     */
    public EventInfo toEventInfo() {
        EventInfo ret = new EventInfo();
        ret.setEventId(eventid);
        ret.setName(name);
        ret.setDate(date);
        ret.setRegType(regtype);
        ret.setCourses(courses);
        ret.setRuns(runs);
        ret.setCountedRuns(countedruns);
        ret.setConePenalty(conepen);
        ret.setGatePenalty(gatepen);
        ret.setPro(ispro);
        ret.setPractice(ispractice);
        return ret;
    }
}

