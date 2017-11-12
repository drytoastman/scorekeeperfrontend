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

/** */
public interface DataInterface
{	
    /**
     * Ping the server (primarily for check for notifications)
     */
    public void ping();
    
	/**
	 * Closes the currently open series connection if open.
	 */
	public void close();
	
	/**
	 * Get the data type version
	 * @return the version schema string
	 */
	public String getVersion();

	/**
	 * @param key the setting to lookup
	 * @return the string value of the setting
	 */
	public String getSetting(String key);
	
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
	 * @param eventid TODO
	 * @return a list of all entrants participating in the current event  
	 */
	public List<Entrant> getEntrantsByEvent(UUID eventid);
	
	/** 
	 * @param eventid TODO
	 * @return a list of all entrants registered for an event 
	 */
	public List<Entrant> getRegisteredEntrants(UUID eventid);
	
	/** 
	 * @param driverid the driver id of the cars to search for
	 * @param eventid TODO
	 * @return a list of all registered car ids for a driver
	 */
	public List<Car> getRegisteredCars(UUID driverid, UUID eventid);
	
	/* Entrants w/ runs */
	public List<Entrant> getEntrantsByRunOrder(UUID eventid, int course, int rungroup); // get all entrants in a particular event/course/rungroup and loads their runs
	public Entrant loadEntrant(UUID eventid, UUID carid, int course, boolean loadruns); // load an entrant by carid and all of the associated runs if desired

	public List<UUID> getCarIdsForRunGroup(UUID eventid, int course, int rungroup); // get the carids based on the current run group
	public Set<UUID> getCarIdsForCourse(UUID eventid, int course); // get the participating cardids based on the course
	public void setRunOrder(UUID eventid, int course, int rungroup, List<UUID> carids); // set the run order of the current rungroup to carids

	public void newDriver(Driver d) throws SQLException; // create a new driver from data in d and set the id variable
	public void updateDriver(Driver d) throws SQLException; // update the driver values in the database
	public void deleteDriver(Driver d) throws SQLException;
	public void deleteDriver(UUID driverid) throws SQLException;
	public void deleteDrivers(Collection<Driver> d) throws SQLException;
	public Driver getDriver(UUID driverid);
	public List<Driver> findDriverByMembership(String membership);
	public List<Driver> getDriversLike(String firstname, String lastname);

	public List<Car> getCarsForDriver(UUID driverid); // get all cars for this driverid
	public Map<String, Set<String>> getCarAttributes(); // get a unique list of possible 'attr' for the car
	public List<Double> getOnlinePaymentsForEvent(UUID driverid, UUID eventid);
	
	/**
	 * Upon successful return, the provided car will be in the registered table for the current event.  If overwrite
	 * is true, then the paid value will overwrite the current value in the database if already present, otherwise, the
	 * value in the database already will stay.  If nothing is already present, overwrite is irrelevant.
	 * @param eventid the eventid to register the car in
	 * @param car the car to register
	 * @param paid true if this registration was paid onsite
	 * @param overwrite true if we should overwrite a current registration entry (i.e. paid flag)
	 * @throws SQLException if an error occurs into the SQL execution 
	 */
	public void registerCar(UUID eventid, Car car, boolean paid, boolean overwrite) throws SQLException;
	
	public void unregisterCar(UUID eventid, Car car) throws SQLException; // remove this car from the current event registration
	public void newCar(Car c) throws SQLException; // create a new car entry with this data, sets the id variable
	public void updateCar(Car d) throws SQLException; // update the car values in the database
	public void deleteCar(Car d) throws SQLException;
	public void deleteCars(Collection<Car> d) throws SQLException;
	public void mergeCar(Car from, Car into) throws SQLException;
	public boolean isRegistered(UUID eventid, UUID carid);
	public MetaCar loadMetaCar(Car c, UUID eventid, int course);

	public void setRun(Run r) throws SQLException;
	public void swapRuns(Collection<Run> runs, UUID newcarid) throws SQLException;
	public void deleteRun(UUID eventid, UUID carid, int course, int run) throws SQLException;
	public void addTimerTime(UUID serverid, Run r);


	/* Challenge */
	public Set<UUID> getCarIdsByChallenge(UUID challengeid);
	public UUID newChallenge(UUID eventid, String name, int size);
	public void deleteChallenge(UUID challengeid);
	public List<Challenge> getChallengesForEvent(UUID eventid);
	public List<ChallengeRound> getRoundsForChallenge(UUID challengeid);
	public List<ChallengeRun> getRunsForChallenge(UUID challengeid);
	public Dialins loadDialins(UUID eventid);
	public void updateChallenge(Challenge c);
	public void updateChallengeRound(ChallengeRound r);
	public void updateChallengeRounds(List<ChallengeRound> rounds);
	public void setChallengeRun(ChallengeRun r);
	public void deleteChallengeRun(ChallengeRun r);

	
	/**
	 * Uses currentEvent, currentCourse
	 * @param eventid the eventid to check
	 * @param carid the carid to check for
	 * @param course the course to check
	 * @return true if the carid is present in any rungroup for the event/course
	 */
	public boolean isInOrder(UUID eventid, UUID carid, int course);
	
	/**
	 * Uses currentEvent, currentCourse, currentRunGroup
	 * @param eventid the event id to check
	 * @param carid the carid to check for
	 * @param course the course to check
	 * @param rungroup the rungroupo to check
	 * @return true if the carid is present in the event/course/rungroup
	 */
	public boolean isInCurrentOrder(UUID eventid, UUID carid, int course, int rungroup);
	
	public ClassData getClassData();
	public String getEffectiveIndexStr(Car c);
	
	/* MergeServers interface */
	public void mergeServerSetLocal(String name, String address, int ctimeout);
	public void mergeServerSetRemote(String hostname, String address, int ctimeout);
	public void mergeServerDelete(UUID serverid);
	public void mergeServerInactivateAll();
	public void mergeServerActivate(UUID serverid, String name, String ip);
	public void mergeServerDeactivate(UUID serverid);
	public void mergeServerUpdateNow(UUID serverid);
	public void mergeServerResetAll();
	public List<MergeServer> getMergeServers();
	
	/* things requiring superuser privileges, only obtained by merge tool */
	public boolean verifyUserAndSeries(String seriesname, String password);
	public boolean deleteUserAndSeries(String seriesname);
	public boolean deleteDriversTable();
}

