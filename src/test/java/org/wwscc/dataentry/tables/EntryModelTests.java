package org.wwscc.dataentry.tables;

import java.sql.SQLException;
import java.util.UUID;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.wwscc.dataentry.DataEntry;
import org.wwscc.storage.Database;
import org.wwscc.storage.Entrant;
import org.wwscc.storage.TestDatabaseContainer;
import org.wwscc.util.MT;

public class EntryModelTests
{
    @ClassRule
    public static TestDatabaseContainer db = new TestDatabaseContainer();

    EntryModel model;
    UUID driver0 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID car0 = UUID.fromString("00000000-0000-0000-0000-000000000002");

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
        Assert.assertEquals("invalid testseries database", driver0, e.getDriverId());
        Assert.assertEquals("invalid testseries database", car0, e.getCarId());
    }

    @After
    public void tearDown() throws Exception
    {
    }

    @Ignore("need to fill with test data as testdb is gone")
    @Test
    public void swapRuns() throws SQLException
    {
        UUID eventid = DataEntry.state.getCurrentEventId();
        UUID carnew = UUID.fromString("00000000-0000-0000-0000-000000000003");

        model.replaceCar(carnew, 0);

        // old runs should be gone, new runs should be present
        Assert.assertEquals(0, Database.d.loadEntrant(eventid, car0,  1, true).getRuns().size());
        Assert.assertEquals(4, Database.d.loadEntrant(eventid, carnew, 1, true).getRuns().size());
    }

}
