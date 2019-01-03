/*
G * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.storage;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.postgresql.util.PSQLException;
import org.wwscc.util.IdGenerator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** */
public abstract class SQLDataInterface implements DataInterface
{
    private static Logger log = Logger.getLogger(SQLDataInterface.class.getCanonicalName());
    private static ObjectMapper objectMapper = new ObjectMapper();

    ClassData classCache = null;
    long classCacheTimestamp = 0;

    public abstract void start() throws Exception;
    public abstract void commit() throws Exception;
    public abstract void rollback();
    public abstract void executeUpdate(String sql, List<Object> args) throws SQLException;
    public abstract void executeGroupUpdate(String sql, List<List<Object>> args) throws SQLException;
    public abstract ResultSet executeSelect(String sql, List<Object> args) throws SQLException;
    public abstract void closeLeftOvers();
    public abstract <T> List<T> executeSelect(String key, List<Object> args, Constructor<T> objc) throws SQLException;

    /**
     * Utility function to create a list for passing args
     * @param args list of objects to add to initially
     * @return the new List
     */
    static List<Object> newList(Object... args)
    {
        List<Object> l = new ArrayList<Object>();
        for (Object o : args)
            l.add(o);
        return l;
    }

    static void logError(String f, Exception e)
    {
        log.log(Level.SEVERE, "\b" + f + " failed: " + e.getMessage(), e);
    }


    @Override
    public String getVersion() throws Exception
    {
        ResultSet rs = executeSelect("select version from version", null);
        if (rs.next())
            return rs.getString("version");
        throw new SQLException("No version found");
    }


    @SuppressWarnings("unchecked")
    @Override
    public <T> T getSetting(String key, Class<T> type)
    {
        try
        {
            ResultSet setting = executeSelect("select val from settings where name=?", newList(key));
            String s = "";
            if (setting.next()) {
                s = setting.getString("val");
            };

            if (type == Integer.class) {
                try {
                    return (T) Integer.valueOf(s);
                } catch (NumberFormatException nfe) {
                    return (T) Integer.valueOf(-1);
                }
            } else if (type == Boolean.class) {
                return (T) (Boolean) s.equals("1");
            }
            return (T)s;
        } catch (Exception ioe) {
            logError("getSetting", ioe);
            return null;
        }
    }

    @Override
    public List<Event> getEvents()
    {
        try
        {
            return executeSelect("SELECT * FROM events WHERE NOT isexternal ORDER BY date", null, Event.class.getConstructor(ResultSet.class));
        }
        catch (Exception ioe)
        {
            List<Event> blank = new ArrayList<Event>();
            if (ioe instanceof PSQLException) {
                if (((PSQLException)ioe).getSQLState().equals("42P01")) {
                    return blank;
                }
            }
            logError("getEvents", ioe);
            return new ArrayList<Event>();
        }
    }

    @Override
    public boolean updateEventRuns(UUID eventid, int runs)
    {
        try
        {
            executeUpdate("update events set runs=?,modified=now() where eventid=?", newList(runs, eventid));
            return true;
        }
        catch (Exception ioe)
        {
            logError("updateEventRuns", ioe);
            return false;
        }
    }

    /**
     * Utility function for methods that loads entrants from driver/car
     * data as well as placing runs if they match.
     * @param d result data containing entrant info
     * @param r result data containing run info or null
     * @return a list of entrants
     * @throws Exception
     */
    List<Entrant> loadEntrants(ResultSet d, ResultSet r) throws Exception
    {
        List<Entrant> ret = new ArrayList<Entrant>();
        List<Run> runs = null;

        if (r != null)
        {
            runs = new ArrayList<Run>();
            while (r.next())
                runs.add(new Run(r));
        }

        while (d.next())
        {
            Entrant e = new Entrant(d);
            if (runs != null)
            {
                for (Run rx : runs)
                {
                    if (rx.getCarId().equals(e.getCarId()))
                        e.runs.put(rx.run, rx);
                }
            }

            ret.add(e);
        }

        return ret;
    }


    @Override
    public List<Entrant> getEntrantsByEvent(UUID eventid)
    {
        try
        {
            return loadEntrants(executeSelect("select distinct d.firstname as firstname,d.lastname as lastname,c.* from runs as r, cars as c, drivers as d " +
                        "where r.carid=c.carid AND c.driverid=d.driverid and r.eventid=?", newList(eventid)), null);
        }
        catch (Exception ioe)
        {
            logError("getEntrantsByEvent", ioe);
            return null;
        }
    }


    @Override
    public List<Entrant> getRegisteredEntrants(UUID eventid)
    {
        try
        {
            return loadEntrants(executeSelect("SELECT DISTINCT d.firstname, d.lastname, c.*, SUM(p.amount) AS paid " +
                        "FROM registered r LEFT JOIN payments p ON r.carid=p.carid AND r.eventid=p.eventid JOIN cars c ON r.carid=c.carid JOIN drivers d ON c.driverid=d.driverid " +
                        "WHERE r.eventid=? GROUP BY d.firstname,d.lastname,c.carid ORDER BY d.firstname,d.lastname", newList(eventid)), null);
        }
        catch (Exception ioe)
        {
            logError("getRegisteredEntrants", ioe);
            return null;
        }
    }


    @Override
    public List<Car> getRegisteredCars(UUID driverid, UUID eventid)
    {
        try
        {
            return executeSelect("select c.* from registered as x, cars as c, drivers as d " +
                        "where x.carid=c.carid AND c.driverid=d.driverid and x.eventid=? and d.driverid=?",
                        newList(eventid, driverid), Car.class.getConstructor(ResultSet.class));
        }
        catch (Exception ioe)
        {
            logError("getRegisteredCars", ioe);
            return null;
        }
    }


    /**
     * Gets all the entrants and their runs based on the current run order.  Ends up
     * being a lot faster (particular over a network) to load all of the runs for the run
     * group as one and then filter them to each entrant locally.
     * @return the list of entrants in the current run order
     */
    @Override
    public List<Entrant> getEntrantsByRunOrder(UUID eventid, int course, int rungroup)
    {
        try
        {
            ResultSet d = executeSelect("WITH r AS (SELECT x.cid,x.row from runorder, unnest(runorder.cars) WITH ORDINALITY x(cid,row) WHERE eventid=? and course=? and rungroup=?) " +
                        "SELECT d.firstname, d.lastname, c.*, SUM(p.amount) AS paid " +
                        "FROM drivers d JOIN cars c ON c.driverid=d.driverid JOIN r ON c.carid=r.cid " +
                        "LEFT JOIN registered as reg ON reg.carid=c.carid and reg.eventid=? LEFT JOIN payments p ON reg.carid=p.carid AND reg.eventid=p.eventid  " +
                        "GROUP BY d.firstname, d.lastname, c.carid, r.row ORDER BY r.row", newList(eventid, course, rungroup, eventid));
            if (d == null)
                return new ArrayList<Entrant>();
            ResultSet runs = executeSelect("select * from runs where eventid=? and course=? and carid in " +
                        "(select unnest(cars) from runorder where eventid=? AND course=? AND rungroup=?)", newList(eventid, course, eventid, course, rungroup));
            List<Entrant> ret = loadEntrants(d, runs);
            closeLeftOvers();
            return ret;
        }
        catch (Exception ioe)
        {
            logError("getEntrantsByRunOrder", ioe);
            return null;
        }
    }


    @Override
    public Entrant loadEntrant(UUID eventid, UUID carid, int course, boolean loadruns)
    {
        try
        {
            ResultSet d = executeSelect("select d.firstname, d.lastname, c.*, SUM(p.amount) as paid " +
                        "FROM drivers d JOIN cars c ON c.driverid=d.driverid LEFT JOIN registered r ON r.carid=c.carid AND r.eventid=? LEFT JOIN payments p ON p.carid=r.carid AND p.eventid=r.eventid " +
                        "WHERE c.carid=? GROUP BY d.firstname, d.lastname, c.carid ORDER BY d.firstname, d.lastname", newList(eventid, carid));
            ResultSet runs = null;
            if (loadruns)
                runs = executeSelect("SELECT * FROM runs WHERE carid=? AND eventid=? AND course=?", newList(carid, eventid, course));
            List<Entrant> e = loadEntrants(d, runs);
            closeLeftOvers();
            if (e.size() > 0)
                return e.get(0);
            return null;
        }
        catch (Exception ioe)
        {
            logError("loadEntrant", ioe);
            return null;
        }
    }


    @Override
    public Set<UUID> getCarIdsForCourse(UUID eventid, int course)
    {
        try
        {
            HashSet<UUID> ret = new HashSet<UUID>();
            ResultSet d = executeSelect("select unnest(cars) as carid from runorder where eventid=? AND course=?", newList(eventid, course));
            while (d.next())
                ret.add((UUID)d.getObject("carid"));
            closeLeftOvers();
            return ret;
        }
        catch (Exception ioe)
        {
            logError("getCarIdsForCourse", ioe);
            return null;
        }
    }

    @Override
    public List<UUID> getCarIdsForRunGroup(UUID eventid, int course, int rungroup)
    {
        try
        {
            ResultSet d = executeSelect("select unnest(cars) as carid from runorder where eventid=? AND course=? AND rungroup=?", newList(eventid, course, rungroup));
            List<UUID> ret = new ArrayList<UUID>();
            while (d.next())
                ret.add((UUID)d.getObject("carid"));
            return ret;
        }
        catch (Exception ioe)
        {
            logError("getCarIdsForRunGroup", ioe);
            return null;
        }
    }

    @Override
    public Set<UUID> activeRunOrderForEvent(UUID eventid)
    {
        try
        {
            ResultSet d = executeSelect("select distinct unnest(cars) as carid from runorder where eventid=? ", newList(eventid));
            Set<UUID> ret = new HashSet<UUID>();
            while (d.next())
                ret.add((UUID)d.getObject("carid"));
            return ret;
        }
        catch (Exception ioe)
        {
            logError("getCarIdsForRunGroup", ioe);
            return null;
        }
    }

    @Override
    public void setRunOrder(UUID eventid, int course, int rungroup, List<UUID> carids, boolean append) throws SQLException
    {
        if (rungroup <= 0) throw new SQLException("Invalid rungroup"); // Shouldn't be doing this if rungroup isn't valid

        String cararg = append ? "array_cat(runorder.cars, ?)" : "?";
        UUID cars[] = carids.toArray(new UUID[0]);
        executeUpdate("INSERT INTO runorder (eventid, course, rungroup, cars, modified) VALUES (?,?,?,?,now()) " +
                      "ON CONFLICT (eventid, course, rungroup) DO UPDATE SET cars="+cararg+", modified=now()",
                      newList(eventid, course, rungroup, cars, cars));
    }


    /**
     * Gets all the orphaned cars based on the current event for cars that have runs no assigned rungroup
     * They show up in results but not in dataentry normal rungroups.
     */
    @Override
    public List<UUID> getOrphanedCars(UUID eventid, int course)
    {
        ArrayList<UUID> ret = new ArrayList<UUID>();
        try
        {
            ResultSet rs = executeSelect("SELECT DISTINCT(carid) FROM runs WHERE eventid=? AND course=? AND carid NOT IN " +
                                         "(SELECT DISTINCT(unnest(runorder.cars)) FROM runorder WHERE eventid=? AND course=?)", newList(eventid, course, eventid, course));
            while (rs.next()) {
                ret.add((UUID)rs.getObject(1));
            }
            closeLeftOvers();
        }
        catch (Exception ioe)
        {
            logError("getOrphanedCars", ioe);
        }
        return ret;
    }


    //****************************************************/

    @Override
    public DecoratedCar decorateCar(Car c, UUID eventid, int course)
    {
        try
        {
            DecoratedCar dcar = new DecoratedCar(c);

            // check if registered for this event
            ResultSet cr = executeSelect("select 1 from registered where carid=? and eventid=?", newList(c.getCarId(), eventid));
            dcar.registered = cr.next();

            // load any payments made
            ResultSet cp = executeSelect("select * from payments where carid=? and eventid=?", newList(c.getCarId(), eventid));
            while (cp.next()) {
                dcar.addPayment(new Payment(cp));
            }

            // check if in runorder for the given course
            ResultSet rr = executeSelect("select 1 from runorder where eventid=? and course=? and ?=ANY(cars) limit 1", newList(eventid, course, c.getCarId()));
            dcar.inRunOrder = rr.next();

            // check for activity outside this event
            ResultSet c1 = executeSelect("select carid from registered where carid=? and eventid!=? limit 1", newList(c.getCarId(), eventid));
            if (!c1.next()) {
                ResultSet c2 = executeSelect("select carid from runs where carid=? and eventid!=? limit 1", newList(c.getCarId(), eventid));
                if (!c2.next()) {
                    ResultSet c3 = executeSelect("select 1 from runorder where ?=ANY(cars) and eventid!=? limit 1", newList(c.getCarId(), eventid));
                    dcar.otherActivity = c3.next();
                } else {
                    dcar.otherActivity = true;
                }
            } else {
                dcar.otherActivity = true;
            }

            closeLeftOvers();
            return dcar;
        }
        catch (Exception ioe)
        {
            logError("decorateCar", ioe);
            return null;
        }
    }


    @Override
    public void newDriver(Driver d) throws Exception
    {
        executeUpdate("insert into drivers (driverid, firstname, lastname, email, username, password, barcode, optoutmail, attr) values (?,?,?,?,?,?,?,?,?)", d.getValues());
    }

    @Override
    public void updateDriver(Driver d) throws Exception
    {
        LinkedList<Object> vals = d.getValues();
        vals.add(vals.pop());
        executeUpdate("update drivers set firstname=?,lastname=?,email=?,username=?,password=?,barcode=?,optoutmail=?,attr=?,modified=now() where driverid=?", vals);
    }

    @Override
    public void deleteDriver(Driver d) throws Exception
    {
        deleteDriver(d.getDriverId());
    }

   @Override
    public void deleteDriver(UUID driverid) throws Exception
    {
        executeUpdate("delete from drivers where driverid=?", newList(driverid));
    }

    @Override
    public void deleteDrivers(Collection<Driver> list) throws Exception
    {
        try
        {
            start();
            for (Driver d : list)
                executeUpdate("delete from drivers where id=?", newList(d.driverid));
            commit();
        }
        catch (Exception sql)
        {
            rollback();
            throw sql;
        }
    }


    @Override
    public Driver getDriver(UUID driverid)
    {
        try
        {
            List<Driver> ret = executeSelect("select * from drivers where driverid=?", newList(driverid), Driver.class.getConstructor(ResultSet.class));
            if (ret.size() > 0)
                return ret.get(0);
        }
        catch (Exception ioe)
        {
            logError("getDriver", ioe);
        }
        return null;
    }

    @Override
    public List<Driver> findDriverByBarcode(String barcode)
    {
        List<Driver> ret = new ArrayList<Driver>();
        try
        {
            return executeSelect("select * from drivers where barcode like ? order by driverid", newList(barcode),
                    Driver.class.getConstructor(ResultSet.class));
        }
        catch (Exception ioe)
        {
            logError("findDriverByBarcode", ioe);
        }
        return ret;
    }

    @Override
    public Car getCar(UUID carid)
    {
        try
        {
            List<Car> ret = executeSelect("select * from cars where carid=?", newList(carid), Car.class.getConstructor(ResultSet.class));
            if (ret.size() > 0)
                return ret.get(0);
        }
        catch (Exception e)
        {
            logError("getCar", e);
        }
        return null;
    }

    @Override
    public List<Car> getCarsForDriver(UUID driverid)
    {
        try
        {
            return executeSelect("select * from cars where driverid = ? order by classcode, number",
                            newList(driverid), Car.class.getConstructor(ResultSet.class));
        }
        catch (Exception ioe)
        {
            logError("getCarsForDriver", ioe);
            return null;
        }
    }


    @Override
    public Map<String, Set<String>> getCarAttributes()
    {
        try
        {
            Map<String, Set<String>> ret = new HashMap<String, Set<String>>();
            HashSet<String> make  = new HashSet<String>();
            HashSet<String> model = new HashSet<String>();
            HashSet<String> color = new HashSet<String>();

            ResultSet rs = executeSelect("select attr from cars", null);
            while (rs.next())
            {
                try {
                    ObjectNode attr = (ObjectNode) objectMapper.readTree(rs.getString("attr"));
                    if (attr.has("make"))  make.add(attr.get("make").asText());
                    if (attr.has("model")) model.add(attr.get("model").asText());
                    if (attr.has("color")) color.add(attr.get("color").asText());
                } catch (IOException ioe) {
                    log.info("Unable to parse car attributes: " + rs.getString("attr"));
                }
            }

            ret.put("make",  make);
            ret.put("model", model);
            ret.put("color", color);
            return ret;
        }
        catch (Exception ioe)
        {
            logError("getCarAttributes", ioe);
            return null;
        }
    }


    @Override
    public List<Integer> getUnavailableNumbers(UUID driverid, String classcode)
    {
        List<Integer> ret = new ArrayList<Integer>();
        try
        {
            boolean superunique = getSetting("superuniquenumbers", Boolean.class);
            ResultSet rs;

            if (superunique) {
                rs = executeSelect("select distinct number from cars where number not in (select number from cars where driverid=?)", newList(driverid));
            } else {
                rs = executeSelect("select distinct number from cars where classcode=? and number not in (select number from cars where classcode=? and driverid=?)", newList(classcode, classcode, driverid));
            }

            while (rs.next())
                ret.add(rs.getInt(1));
        }
        catch (Exception e)
        {
            logError("getUnavailableNumbers", e);
        }

        return ret;
    }


    @Override
    public void registerCar(UUID eventid, UUID carid) throws Exception
    {
        executeUpdate("INSERT INTO registered (eventid, carid) VALUES (?, ?) ON CONFLICT (eventid, carid) DO NOTHING", newList(eventid, carid));
    }

    @Override
    public void unregisterCar(UUID eventid, UUID carid) throws Exception
    {
        executeUpdate("DELETE FROM registered WHERE eventid=? AND carid=?", newList(eventid, carid));
    }

    @Override
    public void registerPayment(UUID eventid, UUID carid, String txtype, double amount) throws Exception
    {
        registerCar(eventid, carid);
        executeUpdate("INSERT INTO payments (payid, eventid, carid, txtype, txtime, amount) VALUES (?, ?, ?, ?, now(), ?)",
                newList(IdGenerator.generateId(), eventid, carid, txtype, amount));
    }

    @Override
    public void movePayments(UUID eventid, UUID srccarid, UUID dstcarid) throws Exception
    {
        executeUpdate("UPDATE payments SET carid=?,modified=now() WHERE eventid=? and carid=?", newList(dstcarid, eventid, srccarid));
    }

    @Override
    public void deletePayment(UUID payid) throws Exception
    {
        executeUpdate("DELETE FROM payments WHERE payid=?", newList(payid));
    }

    @Override
    public void newCar(Car c) throws Exception
    {
        executeUpdate("insert into cars values (?,?,?,?,?,?,?)", c.getValues());
    }

    @Override
    public void updateCar(Car c) throws Exception
    {
        LinkedList<Object> vals = c.getValues();
        vals.add(vals.pop());
        executeUpdate("update cars set driverid=?,classcode=?,indexcode=?,number=?,useclsmult=?,attr=?,modified=now() where carid=?", vals);
    }

    @Override
    public void deleteCar(Car c) throws Exception
    {
        try {
            start();
            executeUpdate("delete from registered where carid=?", newList(c.carid));
            executeUpdate("delete from cars where carid=?", newList(c.carid));
            commit();
        } catch (Exception sqle) {
            rollback();
            throw sqle;
        }
    }

    @Override
    public void deleteCars(Collection<Car> list) throws Exception
    {
        try
        {
            start();
            for (Car c : list)
                executeUpdate("delete from cars where carid=?", newList(c.carid));
            commit();
        }
        catch (Exception ioe)
        {
            rollback();
            throw ioe;
        }
    }

    @Override
    public void mergeCar(Car from, Car into) throws Exception
    {
        try
        {
            start();
            List<Object> sa = newList(into.getCarId(), from.getCarId());
            List<Object> da = newList(from.getCarId());

            // can't swap carids on some tables as they are part of the primary key and that messes with our syncing process
            executeUpdate("INSERT INTO runs (eventid, carid, course, run, cones, gates, raw, status, attr) " +
                                    "(SELECT eventid,	 ?, course, run, cones, gates, raw, status, attr FROM runs WHERE carid=?)", sa);
            executeUpdate("DELETE FROM runs WHERE carid=?", da);

            executeUpdate("INSERT INTO registered (eventid, carid) " +
                                    "(SELECT eventid,	 ? FROM registered WHERE carid=?) ON CONFLICT (eventid, carid) DO NOTHING", sa);
            executeUpdate("DELETE FROM registered WHERE carid=?", da);

            executeUpdate("INSERT INTO challengeruns (challengeid, round, carid, course, reaction, sixty, raw, cones, gates, status) " +
                                             "(SELECT challengeid, round,	 ?, course, reaction, sixty, raw, cones, gates, status FROM challengeruns WHERE carid=?)", sa);
            executeUpdate("DELETE FROM challengeruns WHERE carid=?", da);

            // these we can just swap or delete
            executeUpdate("update payments set carid=?,modified=now() where carid=?", sa);
            executeUpdate("update runorder set cars=array_remove(cars, ?), modified=now()", newList(from.getCarId()));
            executeUpdate("update challengerounds set car1id=?,modified=now() where car1id=?", sa);
            executeUpdate("update challengerounds set car2id=?,modified=now() where car2id=?", sa);
            executeUpdate("delete from cars where carid=?", da);
            commit();
        }
        catch (Exception sqle)
        {
            rollback();
            throw sqle;
        }
    }


    @Override
    public void setRun(Run r, String quicksync) throws Exception
    {
        try {
            executeUpdate("INSERT INTO runs (eventid, carid, course, run, cones, gates, raw, status, attr, modified) " +
                          "VALUES (?,?,?,?,?,?,?,?,?,now()) ON CONFLICT (eventid, carid, course, run) DO UPDATE " +
                          "SET cones=?,gates=?,raw=?,status=?,attr=?,modified=now()",
                          newList(r.eventid, r.carid, r.course, r.run, r.cones, r.gates, r.raw, r.status, r.attr,
                                                                       r.cones, r.gates, r.raw, r.status, r.attr));
            if (quicksync != null)
                mergeServerSetQuickRuns(quicksync);
        } catch (Exception sqle) {
            logError("setRun", sqle);
            throw sqle;
        }
    }

    @Override
    public void swapRuns(Collection<Run> runs, UUID newcarid) throws Exception
    {
        try {
            // do in a transaction so we can revert if things go south, we have to delete and reinsert to maintain primary key contract for syncing
            start();
            for (Run r : runs) {
                executeUpdate("DELETE FROM runs WHERE eventid=? AND carid=? AND course=? AND run=?", newList(r.eventid, r.carid, r.course, r.run));
                r.setCarId(newcarid);
                executeUpdate("INSERT INTO runs (eventid, carid, course, run, cones, gates, raw, status, attr, modified) values (?,?,?,?,?,?,?,?,?,now())",
                        newList(r.eventid, r.carid, r.course, r.run, r.cones, r.gates, r.raw, r.status, r.attr));
            }
            commit();
        } catch (Exception sqle) {
            rollback();
            throw sqle;
        }
    }

    @Override
    public void deleteRun(UUID eventid, UUID carid, int course, int run, String quicksync) throws Exception
    {
        try {
            executeUpdate("DELETE FROM runs WHERE eventid=? AND carid=? AND course=? AND run=?", newList(eventid, carid, course, run));
            if (quicksync != null)
                mergeServerSetQuickRuns(quicksync);
        } catch (Exception sqle){
            logError("deleteRun", sqle);
            throw sqle;
        }
    }

    @Override
    public void addTimerTime(Run r)
    {
        try {
            UUID timeid = IdGenerator.generateId();
            executeUpdate("INSERT INTO timertimes (timeid,   course,   raw,   status,   attr, modified) VALUES (?, ?, ?, ?, ?, now())",
                                           newList(timeid, r.course, r.raw, r.status, r.attr));
        } catch (Exception ioe){
            logError("addTimerTime", ioe);
        }
    }


    @Override
    public Set<UUID> getCarIdsByChallenge(UUID challengeid)
    {
        try
        {

            ResultSet rs = executeSelect("select car1id,car2id from challengerounds where challengeid=?", newList(challengeid));
            HashSet<UUID> ret = new HashSet<UUID>();
            while (rs.next())
            {
                ret.add((UUID)rs.getObject("car1id"));
                ret.add((UUID)rs.getObject("car2id"));
            }
            return ret;
        }
        catch (Exception ioe)
        {
            logError("getCarIdsByChallenge", ioe);
            return null;
        }
    }

    @Override
    public UUID newChallenge(UUID eventid, String name, int size)
    {
        try
        {
            int rounds = size - 1;
            int depth = (int)(Math.log(size)/Math.log(2));
            UUID challengeid = IdGenerator.generateId();
            start();

            executeUpdate("insert into challenges (challengeid, eventid, name, depth) values (?,?,?,?)",
                                        newList(challengeid, eventid, name, depth));

            String sql = "insert into challengerounds (challengeid,round,swappedstart) values (?,?,?)";
            List<Object> rargs = newList(challengeid, 0, false);
            for (int ii = 0; ii <= rounds; ii++)
            {
                rargs.set(1, ii);
                executeUpdate(sql, rargs);
            }
            rargs.set(1, 99);
            executeUpdate(sql, rargs);

            commit();
            return challengeid;
        }
        catch (Exception ioe)
        {
            logError("newChallenge", ioe);
            rollback();
        }

        return null;
    }

    @Override
    public void deleteChallenge(UUID challengeid)
    {
        try
        {   // This delete cascades to challengrounds and challengeruns
            executeUpdate("DELETE FROM challenges WHERE challengeid=?", newList(challengeid));
        }
        catch (Exception ioe)
        {
            logError("deleteChallenge", ioe);
        }
    }


    @Override
    public List<Challenge> getChallengesForEvent(UUID eventid)
    {
        try
        {
            return executeSelect("select * from challenges where eventid = ?", newList(eventid), Challenge.class.getConstructor(ResultSet.class));
        }
        catch (Exception ioe)
        {
            logError("getChallengesForEvent", ioe);
            return null;
        }
    }

    @Override
    public List<ChallengeRound> getRoundsForChallenge(UUID challengeid)
    {
        try
        {
            return executeSelect("select * from challengerounds where challengeid=?", newList(challengeid),
                                ChallengeRound.class.getConstructor(ResultSet.class));
        }
        catch (Exception ioe)
        {
            logError("getRoundsForChallenge", ioe);
            return null;
        }
    }

    @Override
    public List<ChallengeRun> getRunsForChallenge(UUID challengeid)
    {
        try
        {
            return executeSelect("select * from challengeruns where challengeid=?", newList(challengeid), ChallengeRun.class.getConstructor(ResultSet.class));
        }
        catch (Exception ioe)
        {
            logError("getRunsForChallenge", ioe);
            return null;
        }
    }

    @Override
    public Dialins loadDialins(UUID eventid)
    {
        String sql = "SELECT c.classcode,c.indexcode,c.useclsmult,r.* "
                + "FROM runs AS r JOIN cars AS c ON c.carid=r.carid WHERE r.eventid=? ORDER BY r.carid,r.course";

        try
        {
            List<Event> el = executeSelect("select * from events where eventid=?", newList(eventid), Event.class.getConstructor(ResultSet.class));
            if (el.size() == 0)
                throw new Exception("No event found for eventid " + eventid);
            Event e = el.get(0);
            ResultSet rs = executeSelect(sql, newList(eventid));

            Dialins ret         = new Dialins();
            UUID currentid      = new UUID(0,0);
            String classcode    = "";
            String indexcode    = "";
            boolean useclsmult  = false;
            double index        = 1.0;
            double bestraw[]    = new double[2];
            double bestnet[]    = new double[2];

            // 1. load all runs
            // 2. calculate best raw/net/sum for each carid
            // 3. set personal dialin
            while (rs.next())
            {
                Run r = new Run(rs);
                double net = 999.999;

                if (!currentid.equals(r.getCarId()))  // next car, process previous and reset values
                {
                    ret.setEntrant(currentid, classcode, bestraw[0]+bestraw[1], bestnet[0]+bestnet[1], index);
                    currentid = r.getCarId();
                    bestraw[0] = bestraw[1] = bestnet[0] = bestnet[1] = 999.999;
                }

                classcode    = rs.getString("classcode");
                indexcode    = rs.getString("indexcode");
                useclsmult   = rs.getBoolean("useclsmult");
                index        = getClassData().getEffectiveIndex(classcode, indexcode, useclsmult).getLeft();

                if (r.isOK()) // we ignore non-OK runs
                {
                    int idx = r.course() - 1;
                    if (r.raw < bestraw[idx])
                        bestraw[idx] = r.raw;

                    net = (r.getRaw() + (e.getConePenalty() * r.getCones()) + (e.getGatePenalty() * r.getGates())) * index;
                    if (net < bestnet[idx])
                        bestnet[idx] = net;
                }
            }

            // final entrant values processing
            ret.setEntrant(currentid, classcode, bestraw[0]+bestraw[1], bestnet[0]+bestnet[1], index);

            // 4. order and set class dialins
            ret.finalizedialins();
            closeLeftOvers();
            return ret;
        }
        catch (Exception ioe)
        {
            logError("loadDialins", ioe);
            return null;
        }
    }

    @Override
    public void updateChallenge(Challenge c)
    {
        try
        {
            LinkedList<Object> vals = c.getValues();
            vals.add(vals.pop());
            executeUpdate("update challenges set eventid=?,name=?,depth=?,modified=now() where challengeid=?", vals);
        }
        catch (Exception ioe)
        {
            logError("updateChallenge", ioe);
        }
    }


    protected void _updateChallengeRound(ChallengeRound r) throws Exception
    {
        List<Object> list = newList();
        list.add(r.swappedstart);
        list.add(r.car1.carid);
        list.add(r.car1.dial);
        list.add(r.car2.carid);
        list.add(r.car2.dial);
        list.add(r.challengeid);
        list.add(r.round);
        executeUpdate("UPDATE challengerounds SET swappedstart=?,car1id=?,car1dial=?,car2id=?,car2dial=?,modified=now() WHERE challengeid=? AND round=?", list);
    }


    @Override
    public void updateChallengeRound(ChallengeRound r)
    {
        try
        {
            _updateChallengeRound(r);
        }
        catch (Exception ioe)
        {
            logError("updateChallengeRound", ioe);
        }
    }

    @Override
    public void updateChallengeRounds(List<ChallengeRound> rounds)
    {
        try
        {
            start();
            for (ChallengeRound r : rounds)
                _updateChallengeRound(r);
            commit();
        }
        catch (Exception ioe)
        {
            rollback();
            logError("updateChallengeRounds", ioe);
        }
    }

    @Override
    public void setChallengeRun(ChallengeRun r)
    {
        try {
            executeUpdate("INSERT INTO challengeruns (challengeid, round, carid, course, reaction, sixty, raw, cones, gates, status, modified) " +
                        "values (?,?,?,?,?,?,?,?,?,?,now()) ON CONFLICT (challengeid, round, carid, course) DO UPDATE " +
                        "SET reaction=?,sixty=?,raw=?,cones=?,gates=?,status=?,modified=now()",
                        newList(r.challengeid, r.round, r.carid, r.course, r.reaction, r.sixty, r.raw, r.cones, r.gates, r.status,
                                r.reaction, r.sixty, r.raw, r.cones, r.gates, r.status));
        } catch (Exception ioe){
            logError("setChallengeRun", ioe);
        }
    }

    @Override
    public void deleteChallengeRun(ChallengeRun r)
    {
        if (r == null)
            return;
        try {
            executeUpdate("DELETE FROM challengeruns where challengeid=? AND round=? AND carid=? AND course=?", newList(r.challengeid, r.round, r.carid, r.course));
        } catch (Exception ioe){
            logError("deleteChallengeRun", ioe);
        }
    }


    @Override
    public List<Driver> getDriversLike(String first, String last)
    {
        if ((first == null) && (last == null))
            return new ArrayList<Driver>();
        try
        {
            Constructor<Driver> cc = Driver.class.getConstructor(ResultSet.class);
            if (first == null)
                return executeSelect("select * from drivers where lower(lastname) like ? order by firstname,lastname", newList(last.toLowerCase()+"%"), cc);
            else if (last == null)
                return executeSelect("select * from drivers where lower(firstname) like ? order by firstname,lastname", newList(first.toLowerCase()+"%"), cc);
            else
                return executeSelect("select * from drivers where lower(firstname) like ? and lower(lastname) like ? order by firstname,lastname", newList(first.toLowerCase()+"%", last.toLowerCase()+"%"), cc);
        }
        catch (Exception ioe)
        {
            logError("getDriversLike", ioe);
            return null;
        }
    }


    @Override
    public Driver getDriverByUsername(String username)
    {
        try {
            List<Driver> ret = executeSelect("select * from drivers where username=?", newList(username), Driver.class.getConstructor(ResultSet.class));
            if (ret.size() > 0) return ret.get(0);
        } catch (Exception ioe) {
            logError("getDriverByUsername", ioe);
        }
        return null;
    }


    @Override
    public WeekendMember getActiveWeekendMembership(UUID driverid)
    {
        try {
            LocalDate now = LocalDate.now();
            ResultSet rs = executeSelect("SELECT * FROM weekendmembers WHERE driverid=? AND startdate<=? and enddate>=? ORDER BY enddate DESC", newList(driverid, now, now));
            if (rs.next()) {
                return new WeekendMember(rs);
            }
        } catch (Exception ioe) {
            logError("getActiveWeekendMembership", ioe);
        }
        return null;
    }


    @Override
    public void newWeekendNumber(WeekendMember in) throws Exception
    {
        int min = getSetting("weekendmin", Integer.class);
        int max = getSetting("weekendmax", Integer.class);

        Set<Integer> current = new HashSet<Integer>();
        ResultSet rs = executeSelect("SELECT DISTINCT membership FROM weekendmembers", null);
        while (rs.next())
            current.add(rs.getInt(1));

        int pick = 0;
        for (pick = min; pick <= max; pick++) {
            if (!current.contains(pick))
                break;
        }
        if (pick > max)
            throw new SQLException("Ran out of weekend membernumbers, all alloted values have been used");

        in.membership = pick;
        in.region     = getSetting("weekendregion", String.class);
        in.area       = "autocross";
        executeUpdate("INSERT INTO weekendmembers (uniqueid, membership, driverid, startdate, enddate, issuer, issuermem, region, area) " +
                      "VALUES (?,?,?,?,?,?,?,?,?)",
                      newList(in.uniqueid, in.membership, in.driverid, in.startdate, in.enddate, in.issuer, in.issuermem, in.region, in.area));
    }

    public void deleteWeekendNumber(WeekendMember in) throws Exception
    {
        executeUpdate("DELETE FROM weekendmembers WHERE uniqueid=?", newList(in.uniqueid));
    }


    @Override
    public boolean isInOrder(UUID eventid, UUID carid, int course)
    {
        try
        {
            ResultSet rs = executeSelect("select 1 from runorder where eventid=? AND course=? AND ?=ANY(cars)", newList(eventid, course, carid));
            return rs.next();
        }
        catch (Exception ioe)
        {
            logError("isInOrder", ioe);
            return false;
        }
    }

    @Override
    public boolean isInCurrentOrder(UUID eventid, UUID carid, int course, int rungroup)
    {
        try
        {
            ResultSet rs = executeSelect("select 1 from runorder where eventid=? AND course=? AND rungroup=? AND ?=ANY(cars)", newList(eventid, course, rungroup, carid));
            return rs.next();
        }
        catch (Exception ioe)
        {
            logError("isInCurrentOrder", ioe);
            return false;
        }
    }

    @Override
    public ClassData getClassData()
    {
        try
        {
            if ((classCache != null) && (classCacheTimestamp < System.currentTimeMillis() + 2000))
                return classCache;

            ClassData ret = new ClassData();
            for (ClassData.Class cls : executeSelect("select * from classlist", null, ClassData.Class.class.getConstructor(ResultSet.class)))
                ret.add(cls);
            for (ClassData.Index idx : executeSelect("select * from indexlist", null, ClassData.Index.class.getConstructor(ResultSet.class)))
                ret.add(idx);
            classCache = ret; // save for quick lookups when doing multiple calls within a couple seconds
            classCacheTimestamp = System.currentTimeMillis();
            return classCache;
        }
        catch (Exception ioe)
        {
            logError("getClassData", ioe);
            return null;
        }
    }

    @Override
    public String getEffectiveIndexStr(Car c)
    {
        return getClassData().getEffectiveIndex(c.classcode, c.indexcode, c.useClsMult()).getRight();
    }


    @Override
    public void mergeServerSetLocal(String name, String address, int ctimeout)
    {
        _mergeServerSet(MergeServer.LOCALHOST, name, address, ctimeout);
    }

    @Override
    public void mergeServerSetRemote(String name, String address, int ctimeout)
    {
        _mergeServerSet(IdGenerator.generateV5DNSId(name), name, address, ctimeout);
    }

    private void _mergeServerSet(UUID serverid, String name, String address, int ctimeout)
    {
        try {
            executeUpdate("INSERT INTO mergeservers (serverid, hostname, address, ctimeout) VALUES (?, ?, ?, ?) " +
                          "ON CONFLICT (serverid) DO UPDATE SET hostname=?, address=?, ctimeout=?", newList(serverid, name, address, ctimeout, name, address, ctimeout));
        } catch (Exception ioe) {
            log.log(Level.WARNING, "_mergeServerSet: " + ioe, ioe);
        }
    }

    @Override
    public void mergeServerDelete(UUID serverid)
    {
        try {
            executeUpdate("DELETE FROM mergeservers WHERE serverid=?", newList(serverid));
        } catch (Exception ioe) {
            log.log(Level.WARNING, "mergeServerDelete: " + ioe, ioe);
        }
    }

    @Override
    public void mergeServerInactivateAll()
    {
        try {
            executeUpdate("UPDATE mergeservers SET hoststate='I'", null);
        } catch (Exception ioe) {
            log.log(Level.WARNING, "mergeServerInactivateAll: " + ioe, ioe);
        }
    }

    @Override
    public void mergeServerSetQuickRuns(String series)
    {
        try {
            executeUpdate("UPDATE mergeservers SET quickruns=? WHERE hoststate='A'", newList(series));
        } catch (Exception ioe) {
            log.log(Level.WARNING, "mergeServerSetQuickRuns: " + ioe, ioe);
        }
    }

    @Override
    public void mergeServerActivate(UUID serverid, String name, String ip)
    {
        try {
            executeUpdate("INSERT INTO mergeservers (serverid, hostname, address, nextcheck, hoststate) VALUES (?, ?, ?, now(), 'A') " +
                          "ON CONFLICT (serverid) DO UPDATE SET hostname=?, address=?, nextcheck=now(), hoststate='A'",
                          newList(serverid, name, ip, name, ip));
        } catch (Exception ioe) {
            log.log(Level.WARNING, "mergeServerActivate: " + ioe, ioe);
        }
    }

    @Override
    public void mergeServerDeactivate(UUID serverid)
    {
        try {
            executeUpdate("UPDATE mergeservers SET hoststate='I', nextcheck='epoch' WHERE serverid=?", newList(serverid));
        } catch (Exception ioe) {
            log.log(Level.WARNING, "mergeServerDeactivate: " + ioe, ioe);
        }
    }

    @Override
    public void mergeServerUpdateNow(UUID serverid)
    {
        try {
            executeUpdate("UPDATE mergeservers SET hoststate=(CASE hoststate WHEN 'I' THEN '1' ELSE hoststate END), nextcheck=now()::timestamp WHERE serverid=?", newList(serverid));
        } catch (Exception ioe) {
            log.log(Level.WARNING, "mergeServerUpdateNow: " + ioe, ioe);
        }
    }

    @Override
    public void mergeServerResetAll()
    {
        try {
            executeUpdate("UPDATE mergeservers set mergestate='{}'", null);
        } catch (Exception ioe) {
            logError("mergeServerResetAll", ioe);
        }
    }

    @Override
    public void mergeServerUpdateConfig(MergeServer m)
    {
        try {
            executeUpdate("UPDATE mergeservers set waittime=?, ctimeout=? where serverid=?",
                            newList(m.getWaitTime(), m.getConnectTimeout(), m.getServerId()));
        } catch (Exception ioe) {
            logError("mergeServerUpdateConfig", ioe);
        }
    }

    @Override
    public List<MergeServer> getMergeServers()
    {
        try
        {
            List<MergeServer> ret = new ArrayList<MergeServer>();
            for (MergeServer cls : executeSelect("select * from mergeservers", null, MergeServer.class.getConstructor(ResultSet.class)))
                ret.add(cls);
            return ret;
        }
        catch (Exception ioe)
        {
            logError("getMergeServers", ioe);
            return null;
        }
    }

    public void recordEvent(String type, ObjectNode attr)
    {
        try {
            executeUpdate("INSERT INTO localeventstream (etype, event, time) VALUES (?, ?, now())", newList(type, attr));
        } catch (Exception ioe) {
            log.log(Level.WARNING, "recordEvent: " + ioe, ioe);
        }
    }

    @Override
    public boolean verifyUserAndSeries(String seriesname, String password)
    {
        try
        {
            ResultSet a = executeSelect("select verify_user(?, ?)", newList(seriesname, password));
            if (a.next() && a.getBoolean(1))
            {
                ResultSet b = executeSelect("select verify_series(?)", newList(seriesname));
                if (b.next() && b.getBoolean(1))
                    return true;
            }
        }
        catch (Exception ioe)
        {
            logError("verifyUserAndSeries", ioe);
        }
        return false;
    }


    @Override
    public boolean deleteUserAndSeries(String seriesname)
    {
        try
        {
            start();
            executeUpdate("DROP SCHEMA " + seriesname + " CASCADE", null);
            executeUpdate("DELETE FROM results WHERE series=?", newList(seriesname));
            executeUpdate("DROP USER " + seriesname, null);
            commit();
            return true;
        }
        catch (Exception ioe)
        {
            rollback();
            logError("deleteUserAndSeries", ioe);
        }
        return false;
    }

    @Override
    public boolean deletePublicTables()
    {
        try
        {
            start();
            executeUpdate("DELETE FROM weekendmembers", null);
            executeUpdate("DELETE FROM drivers", null);
            executeUpdate("DELETE FROM publiclog", null);
            commit();
            return true;
        }
        catch (Exception ioe)
        {
            rollback();
            logError("deleteDriversTable", ioe);
        }
        return false;
    }
}
