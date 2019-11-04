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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Entrant
{
    protected UUID driverid;
    protected String firstname;
    protected String lastname;
    protected Car car;
    protected Map<Integer, Run> runs;
    protected double paid = 0.0;
    protected String session = "";

    public Entrant()
    {
        runs = new HashMap<Integer,Run>();
    }

    public Entrant(ResultSet rs) throws SQLException
    {
        this();
        car       = new Car(rs);
        driverid  = (UUID)rs.getObject("driverid");
        firstname = rs.getString("firstname");
        lastname  = rs.getString("lastname");
        try {
            paid  = rs.getDouble("paid");
        } catch (SQLException pse) {}
        try {
            session = rs.getString("session");
        } catch (SQLException sse) {}
    }

    static public Entrant testEntrant(UUID carid, String classcode)
    {
        Entrant ret = new Entrant();
        ret.car = new Car();
        ret.car.setCarId(carid);
        ret.car.setClassCode(classcode);
        return ret;
    }

    static public class NumOrder implements Comparator<Entrant>
    {
        public int compare(Entrant e1, Entrant e2) { return e1.car.number - e2.car.number; }
    }

    public UUID getDriverId() { return driverid; }
    public String getName() { return firstname + " " + lastname; }
    public String getFirstName() { return firstname; }
    public String getLastName() { return lastname; }

    public Car  getCar() { return car; }
    public UUID getCarId() { return car.carid; }
    public String getCarModel() { return car.getModel(); }
    public String getCarColor() { return car.getColor(); }
    public String getCarDesc() { return car.getYear() + " " + car.getModel() + " " + car.getColor(); }
    public String getClassCode() { return car.classcode; }
    public int getNumber() { return car.number; }
    public boolean isPaid() { return paid > 0; }
    public String getQuickEntryId() { return car.getQuickEntryId(); }
    public String getSession() { return session; }


    /*
     * @return Get a run based on its run number
     */
    public Run getRun(int num)
    {
        return runs.get(num);
    }

    /*
     * @return a collection of the runs for this entrant
     */
    public Collection<Run> getRuns()
    {
        return runs.values();
    }

    /**
     * @return true if this entrant has any runs entered at all
     */
    public boolean hasRuns()
    {
        return (runs.size() > 0);
    }

    /**
     * @return the number of actual recorded runs (not the max run number recorded)
     */
    public int runCount()
    {
        return runs.size();
    }

    public void setRun(Run r)
    {
        runs.put(r.run, r);
    }

    public void setRuns(Collection<Run> c)
    {
        for (Run r : c)
            runs.put(r.run, r);
    }

    public void deleteRun(int num)
    {
        runs.remove(num);
    }

    @Override
    public int hashCode() {
        return car.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Entrant other = (Entrant) obj;
        if (car == null || !car.carid.equals(other.car.carid)) {
            return false;
        }
        return true;
    }

    public boolean fullCompare(Entrant o)
    {
        return driverid.equals(o.driverid) &&
                firstname.equals(o.firstname) &&
                lastname.equals(o.lastname) &&
                car.fullCompare(o.car) &&
                runs.equals(o.runs) &&
                paid == o.paid;
        // NOTE: runs comparison does not look at eventid or carid but that shouldn't matter in our use case
        //  carid is already compared in car and eventid is set when doing database lookups
    }
}


