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
import java.util.Comparator;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * This represents a single run in an event.  Note that for simplicity we represent a ProSolo
 * run as default (reaction, sixty) and then use it for regular runs as well
 */
@JsonAutoDetect(fieldVisibility = Visibility.NONE, getterVisibility = Visibility.NONE,
                isGetterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Run extends AttrBase implements Cloneable
{
    public static final int LEFT = 1;
    public static final int RIGHT = 2;

    protected UUID eventid;
    protected UUID carid;

    @JsonProperty
    protected int course;
    @JsonProperty
    protected int run;
    @JsonProperty
    protected int cones;
    @JsonProperty
    protected int gates;
    @JsonProperty
    protected String status;
    @JsonProperty
    protected double raw;


    public static class WithRowId extends Run
    {
        @JsonProperty
        protected UUID rowid;  // help us to double check which run data went with what reaction, etc
        public void setRowId(UUID u) { rowid = u; }
        public UUID getRowId() { return rowid; }
        public WithRowId() { super(0.0); }
    }

    @JsonCreator
    protected Run()
    {
        this(0.0, 0, 0, "OK");
    }


    public static class NetOrder implements Comparator<Run>
    {
        Event e;
        public NetOrder(Event e)
        {
            this.e = e;
        }
        // Take cone/gate/status into account
        public int compare(Run o1, Run o2)
        {
            if (!o1.isOK() && !o2.isOK()) return 0;
            if (!o2.isOK()) return -1;
            if (!o1.isOK()) return  1;
            return (int)((o1.raw+(e.conepen*o1.cones)+(e.gatepen*o1.gates))*1000
                        - (o2.raw+(e.conepen*o2.cones)+(e.gatepen*o2.gates))*1000);
        }
    }

    /**
     * The basic Run constructor
     * @param raw   the raw time in seconds
     * @param cones the number of cones
     * @param gates the number of gates
     * @param status the status string
     */
    public Run(double raw, int cones, int gates, String status)
    {
        super();

        if (Double.isNaN(raw))
            this.raw = 999.999;
        else
            this.raw = raw;

        this.cones	= cones;
        this.gates	= gates;
        this.status	= status;

        this.carid   = null;
        this.eventid = null;
        this.course  = -1;
        this.run     = -1;
    }

    /**
     * Shortcut for a clean run
     * @param raw the raw time in seconds
     */
    public Run(double raw)
    {
        this(raw, 0, 0, "OK");
    }

    /**
     * Extract run data from a SQL result set
     * @param rs the result set pointed at the row in question
     * @throws SQLException if an error occurs extracting run data
     */
    public Run(ResultSet rs) throws SQLException
    {
        super(rs);
        eventid = (UUID)rs.getObject("eventid");
        carid   = (UUID)rs.getObject("carid");
        course  = rs.getInt("course");
        run     = rs.getInt("run");
        cones   = rs.getInt("cones");
        gates   = rs.getInt("gates");
        status  = rs.getString("status");
        raw     = rs.getDouble("raw");
    }

    @Override
    public Object clone() throws CloneNotSupportedException
    {
        return super.clone();
    }

    public int run() { return run; }
    public int course() { return course; }
    public UUID getEventId() { return eventid; }
    public UUID getCarId() { return carid; }
    public int getCones() { return cones; }
    public int getGates() { return gates; }
    public String getStatus() { return status; }

    public double getReaction()     { return getAttrD("reaction");}
    public double getSixty()        { return getAttrD("sixty"); }
    public double getSegment(int s) { return getAttrD("seg"+s); }
    public double getRaw()          { return raw; }

    public void setRunNumber(int r)         { run = r; }
    public void setCourse(int c)            { course = c; }
    public void setReaction(Double d)       { setAttrD("reaction", d); }
    public void setSixty(Double d)          { setAttrD("sixty", d); }
    public void setSegment(int s, Double d) { setAttrD("seg"+s, d); }
    public void setRaw(double d)            { raw = d; }
    public void setCones(int c)             { cones = c; }
    public void setGates(int g)             { gates = g; }
    public void setStatus(String s)         { status = s; }
    public void setCarId(UUID cid)          { carid = cid; }

    public void updateTo(UUID inEventid, UUID inCarid, int inCourse, int inRun)
    {
        this.eventid = inEventid;
        this.carid = inCarid;
        this.course = inCourse;
        this.run = inRun;
        attrCleanup();
    }

    public boolean isOK()
    {
        if (status == null) return false;
        return status.equals("OK");
    }

    @Override
    public String toString()
    {
        return "<"+carid+","+eventid+","+course+","+run+","+raw+","+status+" ("+cones+","+gates+")>";
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof Run)) return false;
        Run r = (Run)o;

        return ((r.course == course) && (r.run == run) && (r.status.equals(status)) &&
                (r.raw == raw) && (r.cones == cones) && (r.gates == gates) && (r.attr.equals(attr)));
    }

    @Override
    public int hashCode()
    {
        return course ^ run ^ new Double(raw).hashCode();
    }
}
