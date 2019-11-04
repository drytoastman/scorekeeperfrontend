package org.wwscc.dataentry.tables;

import java.sql.SQLException;
import java.util.UUID;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.wwscc.dataentry.DataEntry;
import org.wwscc.storage.Database;
import org.wwscc.storage.TestDatabaseContainer;
import org.wwscc.util.MT;

public class EntryModelTests
{
    @ClassRule
    public static TestDatabaseContainer testdb = new TestDatabaseContainer();

    EntryModel model;

    @Before
    public void setUp() throws Exception
    {
        model = new EntryModel();
        DataEntry.state.setCurrentEvent(Database.d.getEvents().get(0).toEventInfo());
        DataEntry.state.setCurrentCourse(1);
        DataEntry.state.setCurrentRunGroup(1);
        model.event(MT.EVENT_CHANGED, null);
        model.event(MT.RUNGROUP_CHANGED, null);
    }

    @After
    public void tearDown() throws Exception
    {
    }

    @SuppressWarnings("static-access")
    @Test
    public void swapRuns() throws SQLException
    {
        UUID eventid = DataEntry.state.getCurrentEventId();

        Assert.assertArrayEquals(new Object[] { testdb.carid1 }, Database.d.getRegisteredCars(testdb.driverid, testdb.eventid).stream().map(c -> c.getCarId()).toArray());
        model.replaceCar(testdb.carid2, 0);

        // old runs should be gone, new runs should be present, new car should be registered
        Assert.assertEquals(0, Database.d.loadEntrant(eventid, testdb.carid1, 1, 1, true).getRuns().size());
        Assert.assertEquals(4, Database.d.loadEntrant(eventid, testdb.carid2, 1, 1, true).getRuns().size());
        Assert.assertArrayEquals(new Object[] { testdb.carid1, testdb.carid2 }, Database.d.getRegisteredCars(testdb.driverid, testdb.eventid).stream().map(c -> c.getCarId()).toArray());
    }

}
