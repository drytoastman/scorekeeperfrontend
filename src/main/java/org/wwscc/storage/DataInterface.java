/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.storage;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.wwscc.util.ApplicationState;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** */
public interface DataInterface
{
    /**
     * Closes the currently open series connection if open.
     */
    public void close();

    /**
     * Get the data type version
     * @return the version schema string
     */
    public String getVersion() throws Exception;

    /**
     * @return a list of series string names
     */
    public List<String> getSeriesList();

    /**
     * set the active series for further use
     * @param series the series string name
     */
    public void useSeries(String series);

    /**
     * @param <T>
     * @param key the setting to lookup
     * @return the string value of the setting
     */
    public <T> T getSetting(String key, Class<T> type);

    /**
     * @return a list of all events in the current series
     */
    public List<Event> getEvents();

    /**
     * update the run count for an event
     * @param eventid the id of the event
     * @param runs the number of runs to set to
     * @return true if update succeeded
     */
    public boolean updateEventRuns(UUID eventid, int runs);

    /**
     * @param eventid the id of the event
     * @return a list of all entrants participating in the current event
     */
    public List<Entrant> getEntrantsByEvent(UUID eventid);

    /**
     * @param eventid the id of the event
     * @return a list of all entrants registered for an event
     */
    public List<Entrant> getRegisteredEntrants(UUID eventid);

    /**
     * @param driverid the driver id of the cars to search for
     * @param eventid the id of the event
     * @return a list of all registered car ids for a driver
     */
    public List<Car> getRegisteredCars(UUID driverid, UUID eventid);

    /* Entrants w/ runs */
    public List<Entrant> getEntrantsByRunOrder(UUID eventid, int course, int rungroup); // get all entrants in a particular event/course/rungroup and loads their runs
    public Entrant loadEntrant(UUID eventid, UUID carid, int course, int rungroup, boolean loadruns); // load an entrant by carid and all of the associated runs if desired

    public List<UUID> getCarIdsForCourse(UUID eventid, int course); // get the participating cardids based on the course
    public List<UUID> getCarIdsForRunGroup(UUID eventid, int course, int rungroup); // get the carids based on the current run group
    public Map<Integer, Set<UUID>> activeRunOrderForEvent(UUID eventid);
    public void setRunOrder(UUID eventid, int course, int rungroup, List<UUID> carids, boolean append) throws SQLException;
    public List<UUID> getOrphanedCars(UUID eventid, int course); // get all car ids that have runs but are not in any rungroup
    public List<Integer> getProGroupings(UUID eventid, int course, int rungroup);

    public void newDriver(Driver d) throws Exception; // create a new driver from data in d and set the id variable
    public void updateDriver(Driver d) throws Exception; // update the driver values in the database
    public void deleteDriver(Driver d) throws Exception;
    public void deleteDriver(UUID driverid) throws Exception;
    public void deleteDrivers(Collection<Driver> d) throws Exception;
    public Driver getDriver(UUID driverid);
    public Driver getDriverForCarId(UUID carid);
    public List<Driver> findDriverByBarcode(String barcode);
    public List<Driver> getDriversLike(String firstname, String lastname);
    public Driver getDriverByUsername(String username);

    public WeekendMember getActiveWeekendMembership(UUID driverid);
    public void newWeekendNumber(WeekendMember in) throws Exception;
    public void deleteWeekendNumber(WeekendMember in) throws Exception;

    public Car getCar(UUID carid);
    public List<Car> getCarsForDriver(UUID driverid); // get all cars for this driverid
    public Map<String, Set<String>> getCarAttributes(); // get a unique list of possible 'attr' for the car
    public List<Integer> getUnavailableNumbers(UUID driverid, String classcode);
    public List<PaymentItem> getPaymentItemsForEvent(UUID eventid);
    public List<PaymentItem> getPaymentItemsForMembership();
    public List<Payment> getMembershipPayments(UUID driverid);
    public List<Payment> getNonEntryPayments(UUID driverid, UUID eventid);

    /**
     * Upon successful return, the provided car will be in the registered table for the current event.
     * @param eventid the eventid to register the car in
     * @param car the car to register
     * @throws Exception if an error occurs into the SQL execution
     */
    public void registerCar(UUID eventid, UUID carid, String session) throws Exception;
    public void ensureRegistration(UUID eventid, UUID carid, String session) throws Exception;
    public void unregisterCar(UUID eventid, UUID carid, String session) throws Exception; // remove this car from the current event registration
    public void registerPayment(UUID eventid, UUID driverid, UUID carid, String session, String txtype, String itemname, double amountInCents) throws Exception;
    public void movePayments(UUID eventid, UUID srccarid, UUID dstcarid) throws Exception;
    public void deletePayment(UUID payid) throws Exception;

    public void newCar(Car c) throws Exception; // create a new car entry with this data, sets the id variable
    public void updateCar(Car d) throws Exception; // update the car values in the database
    public void deleteCar(Car d) throws Exception;
    public void deleteCars(Collection<Car> d) throws Exception;
    public void mergeCar(Car from, Car into) throws Exception;
    public DecoratedCar decorateCar(Car c, ApplicationState state);

    public void setRun(Run r, String quicksync) throws Exception;
    public void swapRuns(Collection<Run> runs, UUID newcarid) throws Exception;
    public void moveRuns(Collection<Run> runs, int newrungroup) throws Exception;
    public void deleteRun(UUID eventid, UUID carid, int course, int rungroup, int run, String quicksync) throws Exception;
    public void addTimerTime(Run r);


    /* Challenge */
    public Set<UUID> getCarIdsByChallenge(UUID challengeid);
    public Challenge newChallenge(UUID eventid, String name, int size) throws SQLException;
    public void deleteChallenge(UUID challengeid);
    public List<Challenge> getChallengesForEvent(UUID eventid);
    public List<ChallengeRound> getRoundsForChallenge(UUID challengeid);
    public List<ChallengeRun> getRunsForChallenge(UUID challengeid);
    public ChallengeRun getRunForChallengeEntry(UUID challengeid, int round, UUID carid, int course);
    public ChallengeStaging getStagingForChallenge(UUID challengeid);
    public Dialins loadDialins(UUID eventid);
    public void updateChallenge(Challenge c);
    public void updateChallengeRound(ChallengeRound r);
    public void updateChallengeRound(UUID challengeid, int round, int entry, UUID carid, double dialin) throws SQLException;
    @Deprecated public void updateChallengeRounds(List<ChallengeRound> rounds);
    public void setChallengeRun(ChallengeRun r);
    public void deleteChallengeRun(ChallengeRun r);
    public void setChallengeStaging(ChallengeStaging s) throws SQLException;


    public boolean isInAnyOrder(UUID eventid, UUID carid, int course);
    public boolean isInCurrentOrder(UUID eventid, UUID carid, int course, int rungroup);
    public boolean isInOtherOrder(UUID eventid, UUID carid, int course, int rungroup);


    public ClassData getClassData();
    public String getEffectiveIndexStr(Car c);

    /* MergeServers interface */
    public void mergeServerSetLocal(String name, String address, int ctimeout);
    public void mergeServerSetRemote(String hostname, String address, int ctimeout);
    public void mergeServerDelete(UUID serverid);
    public void mergeServerInactivateAll();
    public void mergeServerSetQuickRuns(String series);
    public void mergeServerActivate(UUID serverid, String name, String ip);
    public void mergeServerDeactivate(UUID serverid);
    public void mergeServerUpdateNow(UUID serverid);
    public void mergeServerResetAll();
    public void mergeServerUpdateConfig(MergeServer m);
    public List<MergeServer> getMergeServers();

    /* Local event stream */
    public void recordEvent(String type, ObjectNode attr);
    public void recordCache(String name, String data);

    /* things requiring superuser privileges, only obtained by merge tool */
    public boolean verifyUserAndSeries(String seriesname, String password);
    public boolean changePassword(String seriesname, String password);
    public boolean deleteUserAndSeries(String seriesname);
    public boolean deletePublicTables();
}

