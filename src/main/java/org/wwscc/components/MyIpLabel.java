/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.components;

import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.JLabel;
import org.wwscc.util.Network;

public class MyIpLabel extends JLabel
{
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public MyIpLabel()
    {
        super("");
        setHorizontalAlignment(CENTER);
        scheduler.scheduleWithFixedDelay(() -> {
            InetAddress a = Network.getPrimaryAddress();
            setText("My IP: " + ((a != null) ? a.getHostAddress() : "network down"));
         }, 0, 10, TimeUnit.SECONDS);
    }
}
