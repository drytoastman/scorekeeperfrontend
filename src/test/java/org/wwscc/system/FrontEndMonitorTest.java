package org.wwscc.system;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wwscc.storage.Database;
import org.wwscc.storage.FakeDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class FrontEndMonitorTest
{
    FrontEndMonitor totest;
    DatabaseShim shim;

    class DatabaseShim extends FakeDatabase {
        Set<UUID> active = new HashSet<UUID>();
        @Override
        public void mergeServerActivate(UUID serverid, String name, String ip) {
            System.out.println("set " + serverid);
            active.add(serverid);
        }

        @Override
        public void mergeServerDeactivate(UUID serverid) {
            System.out.println("unset " + serverid);
            active.remove(serverid);
        }
    }

    @Before
    public void setUp() throws Exception
    {
        shim = new DatabaseShim();
        Database.d = shim;
        totest = new FrontEndMonitor();
    }

    @After
    public void tearDown() throws Exception
    {
    }

    @Test
    public void testInterfaceChange() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        UUID uuid = UUID.fromString("abd86e8a-cf47-11e7-abc4-cec278b6b50a");
        InetAddress a1 = InetAddress.getByName("192.168.10.10");
        InetAddress a2 = InetAddress.getByName("192.168.0.100");
        ObjectNode o = mapper.createObjectNode();
        o.put("hostname", "me");

        totest.address.set(a1);
        totest.serviceChange(uuid, "DATABASE", a1, o, true);
        Assert.assertArrayEquals(new UUID[] {}, shim.active.toArray());

        totest.serviceChange(uuid, "DATABASE", a2, o, true);
        totest.serviceChange(uuid, "DATABASE", a1, o, false);
        Assert.assertArrayEquals(new UUID[] {}, shim.active.toArray());
    }
}
