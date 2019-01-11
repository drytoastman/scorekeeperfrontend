package org.wwscc.dataentry.actions;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;
import org.wwscc.dataentry.actions.ReorderByNetAction.Placement;
import org.wwscc.storage.Entrant;

public class NetReorderTests
{
    @Test
    public void testBasic() throws SQLException
    {
        UUID cars[] = new UUID[50];
        for (int ii = 0; ii < cars.length; ii++)
            cars[ii] = UUID.randomUUID();

        // group1 = 2 NS3 drivers + 7 NS4 drivers
        // group2 = 4 NS5 drivers
        // group3 = 5 NS4 dual drivers + 1 NS6
        List<Entrant> orderleft  = new ArrayList<>();
        orderleft.add(Entrant.testEntrant(cars[1], "NS3")); // 0
        orderleft.add(Entrant.testEntrant(cars[3], "NS4"));
        orderleft.add(Entrant.testEntrant(cars[5], "NS4"));
        orderleft.add(Entrant.testEntrant(cars[7], "NS4"));
        orderleft.add(Entrant.testEntrant(cars[9], "NS4"));
        orderleft.add(Entrant.testEntrant(cars[2], "NS3"));
        orderleft.add(Entrant.testEntrant(cars[4], "NS4"));
        orderleft.add(Entrant.testEntrant(cars[6], "NS4"));
        orderleft.add(Entrant.testEntrant(cars[8], "NS4"));
        // ---
        orderleft.add(Entrant.testEntrant(cars[11], "NS5")); // 9
        orderleft.add(Entrant.testEntrant(cars[13], "NS5"));
        orderleft.add(Entrant.testEntrant(cars[12], "NS5"));
        orderleft.add(Entrant.testEntrant(cars[14], "NS5"));
        // ---
        orderleft.add(Entrant.testEntrant(cars[15], "NS4")); // 13
        orderleft.add(Entrant.testEntrant(cars[17], "NS4"));
        orderleft.add(Entrant.testEntrant(cars[19], "NS4"));
        orderleft.add(Entrant.testEntrant(cars[16], "NS4"));
        orderleft.add(Entrant.testEntrant(cars[18], "NS4"));
        orderleft.add(Entrant.testEntrant(cars[20], "NS6"));


        List<Entrant> orderright = new ArrayList<>();
        orderright.add(Entrant.testEntrant(cars[2], "NS3"));
        orderright.add(Entrant.testEntrant(cars[4], "NS4"));
        orderright.add(Entrant.testEntrant(cars[6], "NS4"));
        orderright.add(Entrant.testEntrant(cars[8], "NS4"));
        orderright.add(Entrant.testEntrant(cars[1], "NS3"));
        orderright.add(Entrant.testEntrant(cars[3], "NS4"));
        orderright.add(Entrant.testEntrant(cars[5], "NS4"));
        orderright.add(Entrant.testEntrant(cars[7], "NS4"));
        orderright.add(Entrant.testEntrant(cars[9], "NS4"));
        // ---
        orderright.add(Entrant.testEntrant(cars[12], "NS5"));
        orderright.add(Entrant.testEntrant(cars[14], "NS5"));
        orderright.add(Entrant.testEntrant(cars[11], "NS5"));
        orderright.add(Entrant.testEntrant(cars[13], "NS5"));
        // ---
        orderright.add(Entrant.testEntrant(cars[16], "NS4"));
        orderright.add(Entrant.testEntrant(cars[18], "NS4"));
        orderright.add(Entrant.testEntrant(cars[20], "NS6"));
        orderright.add(Entrant.testEntrant(cars[15], "NS4"));
        orderright.add(Entrant.testEntrant(cars[17], "NS4"));
        orderright.add(Entrant.testEntrant(cars[19], "NS4"));

        List<List<Placement>> ret = ReorderByNetAction.calculatePlacements(orderleft, orderright);

        // There should be no duplicates in either list
        Assert.assertEquals(ret.get(0).size(), new HashSet<>(ret.get(0)).size());
        Assert.assertEquals(ret.get(1).size(), new HashSet<>(ret.get(1)).size());

        // Check the sides for accuracy
        Placement expectedLeft[] = new Placement[] {
                new Placement("NS3", 1),
                new Placement("NS4", 1),
                new Placement("NS4", 3),
                new Placement("NS4", 5),
                new Placement("NS4", 7),
                new Placement("NS3", 2),
                new Placement("NS4", 2),
                new Placement("NS4", 4),
                new Placement("NS4", 6),
                new Placement("NS5", 1),
                new Placement("NS5", 3), // 10
                new Placement("NS5", 2),
                new Placement("NS5", 4),
                new Placement("NS4", 8),
                new Placement("NS4", 10),
                new Placement("NS4", 12),
                new Placement("NS4", 9),
                new Placement("NS4", 11),
                new Placement("NS6", 1)
        };

        Placement expectedRight[] = new Placement[] {
                new Placement("NS3", 2),
                new Placement("NS4", 2),
                new Placement("NS4", 4),
                new Placement("NS4", 6),
                new Placement("NS3", 1),
                new Placement("NS4", 1),
                new Placement("NS4", 3),
                new Placement("NS4", 5),
                new Placement("NS4", 7),
                new Placement("NS5", 2),
                new Placement("NS5", 4), // 10
                new Placement("NS5", 1),
                new Placement("NS5", 3),
                new Placement("NS4", 9),
                new Placement("NS4", 11),
                new Placement("NS6", 1),
                new Placement("NS4", 8),
                new Placement("NS4", 10),
                new Placement("NS4", 12)
        };

        Assert.assertArrayEquals(expectedLeft,  ret.get(0).toArray());
        Assert.assertArrayEquals(expectedRight, ret.get(1).toArray());
    }
}
