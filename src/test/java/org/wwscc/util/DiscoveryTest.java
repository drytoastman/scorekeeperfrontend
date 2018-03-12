package org.wwscc.util;

import java.net.InetAddress;
import java.util.UUID;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DiscoveryTest
{
    Discovery totest;
    InetAddress activeip;
    UUID activeuuid;


    @Before
    public void setUp() throws Exception
    {
        totest = new Discovery();
        totest.setTimeout(100);
        activeip = null;
        activeuuid = null;
    }

    @After
    public void tearDown() throws Exception
    {
    }

    @Test
    public void testIPChange() throws Exception
    {
        UUID uuid = UUID.fromString("abd86e8a-cf47-11e7-abc4-cec278b6b50a");
        byte d1[] = ("[{ \"service\":\"DATABASE\", \"serverid\":\""+uuid+"\", \"data\":{\"hostname\": \"d1\"}}]").getBytes();
        InetAddress a1 = InetAddress.getByName("192.168.1.10");
        InetAddress a2 = InetAddress.getByName("192.168.1.20");

        totest.addServiceListener((serverid, service, src, jsondata, up) -> {
            System.out.println(serverid + ", " + service + ", " + jsondata + ", " + up);
            if (up) {
                activeuuid = serverid;
                activeip = src;
            } else {
                activeuuid = null;
                activeip = null;
            }
        });

        // message with a1 address
        totest.processNetworkData(a1, d1, d1.length);
        Assert.assertEquals(uuid, activeuuid);
        Assert.assertEquals(a1, activeip);

        // message with a2 address
        totest.processNetworkData(a2, d1, d1.length);
        Assert.assertEquals(uuid, activeuuid);
        Assert.assertEquals(a2, activeip);

        // now check a timeout
        Thread.sleep(200);
        Assert.assertEquals(uuid, activeuuid);
        Assert.assertEquals(a2, activeip);

        totest.checkForTimeouts();
        Assert.assertEquals(null, activeuuid);
        Assert.assertEquals(null, activeip);
    }

}
