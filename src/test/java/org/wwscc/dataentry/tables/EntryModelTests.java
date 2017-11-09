package org.wwscc.dataentry.tables;

import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wwscc.dataentry.DataEntry;
import org.wwscc.storage.Database;
import org.wwscc.storage.Entrant;
import org.wwscc.storage.PostgresqlDatabase;
import org.wwscc.tray.DockerContainer;
import org.wwscc.tray.DockerMachine;
import org.wwscc.util.MT;

public class EntryModelTests 
{
    static DockerContainer db;
    
    EntryModel model;
    UUID driver0 = UUID.fromString("8cf4ac4c-bfa0-11e7-a8b3-0c4de9c60d73");
    UUID car0 = UUID.fromString("a22a4824-bfa0-11e7-806f-0c4de9c60d73");

    @BeforeClass
    public static void setUpBeforeClass() throws Exception 
    {
        Logger.getLogger("org.postgresql.Driver").setLevel(Level.OFF);
        db = new DockerContainer("drytoastman/scdb:pro2017", "testdb");
        db.setMachineEnv(DockerMachine.machineenv());
        db.addPort("127.0.0.1:6432", "6432");
        db.addPort("54329", "5432");
        db.createNetsAndVolumes();
        db.start();
        PostgresqlDatabase.waitUntilUp();
        Database.openSeries("pro2017");
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception 
    {
        Database.d.close();
        db.stop();  // comment out to leave container running after tests
    }
    
    @Before
    public void setUp() throws Exception 
    {
        model = new EntryModel();
        DataEntry.state.setCurrentEvent(Database.d.getEvents().get(0));
        DataEntry.state.setCurrentCourse(1);
        DataEntry.state.setCurrentRunGroup(1);
        model.event(MT.EVENT_CHANGED, null);
        model.event(MT.RUNGROUP_CHANGED, null);
        
        // Sanity check our initial pro2017 data
        Entrant e = (Entrant) model.getValueAt(0, 0);
        Assert.assertEquals(driver0, e.getDriverId());
        Assert.assertEquals(car0, e.getCarId());
    }

    @After
    public void tearDown() throws Exception 
    {
    }

    @Test
    public void swapRuns() throws SQLException 
    {
        UUID eventid = DataEntry.state.getCurrentEventId();        
        UUID carnew = UUID.fromString("a2440a1e-bfa0-11e7-a7be-0c4de9c60d73"); 
        
        model.replaceCar(carnew, 0);
        
        // old runs should be gone, new runs should be present
        Assert.assertEquals(0, Database.d.loadEntrant(eventid, car0,  1, true).getRuns().size());
        Assert.assertEquals(4, Database.d.loadEntrant(eventid, carnew, 1, true).getRuns().size());
    }

}
