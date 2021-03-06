/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2010 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.storage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.wwscc.challenge.Id;
import org.wwscc.util.IdGenerator;

/**
 * Represents the 'regular' run data for a single side run taken during
 * a challenge run
 */
public class ChallengeRun
{
    protected UUID   challengeid;
    protected UUID   carid;
    protected double reaction, sixty, raw;
    protected int    round, course, cones, gates;
    protected String status;

    public ChallengeRun()
    {
        challengeid = IdGenerator.generateId();
        carid = null;
        reaction = -1;
        sixty = -1;
        raw = -1;
        round = -1;
        course = -1;
        cones = 0;
        gates = 0;
        status = "";
    }

    public ChallengeRun(UUID challengeid, UUID carid, double reaction, double sixty, double raw, int round, int course, int cones, int gates, String status)
    {
        this.challengeid = challengeid;
        this.carid = carid;
        this.reaction = reaction;
        this.sixty = sixty;
        this.raw = raw;
        this.round = round;
        this.course = course;
        this.cones = cones;
        this.gates = gates;
        this.status = status;
    }

    public ChallengeRun(Run r)
    {
        this();
        carid    = r.carid;
        reaction = r.getReaction();
        sixty    = r.getSixty();
        raw      = r.getRaw();
        course   = r.course;
        cones    = r.cones;
        gates    = r.gates;
        status   = r.status;
    }

    public ChallengeRun(ResultSet rs) throws SQLException
    {
        challengeid  = (UUID)rs.getObject("challengeid");
        carid        = (UUID)rs.getObject("carid");
        reaction     = rs.getDouble("reaction");
        sixty        = rs.getDouble("sixty");
        raw          = rs.getDouble("raw");
        round        = rs.getInt("round");
        course       = rs.getInt("course");
        cones        = rs.getInt("cones");
        gates        = rs.getInt("gates");
        status       = rs.getString("status");
    }

    @Deprecated // This goes away when the old challenge GUI does
    public void setChallengeRound(Id.Run id)
    {
        challengeid = id.challengeid;
        round = id.round;
        course = id.isLeft() ? Run.LEFT : Run.RIGHT;
    }

    public void setChallengeRound(UUID challengeid, int round, int course)
    {
        this.challengeid = challengeid;
        this.round = round;
        this.course = course;
    }

    public UUID getChallengeId(){ return challengeid; }
    public UUID getCarId()      { return carid; }
    public int getRound()       { return round; }
    public int getCourse()      { return course; }
    public int getCones()       { return cones; }
    public int getGates()       { return gates; }
    public double getRaw()      { return raw; }
    public double getReaction() { return reaction; }
    public double getSixty()    { return sixty; }
    public String getStatus()   { return status; }

    public void setCarId(UUID u)      { carid = u; }
    public void setCourse(int c)      { course = c; }
    public void setReaction(double r) { reaction = r; }
    public void setSixty(double s)    { sixty = s; }
    public void setRaw(double r)      { raw = r; }
    public void setCones(int c)       { cones = c; }
    public void setGates(int g)       { gates = g; }
    public void setStatus(String s)   { status = s; }

    public boolean isOK()
    {
        if (status == null) return false;
        return status.equals("OK");
    }

    public boolean hasStatus()
    {
        if (status == null) return false;
        return !status.equals("");
    }

    public int statusLevel()
    {
        return statusLevel(status);
    }

    public static int statusLevel(String status)
    {
        if (status.equals("RL") || status.equals("NS")) return 2;
        if (status.endsWith("DNF") || status.equals("DNS") || status.equals("")) return 1;
        return 0;
    }
}
