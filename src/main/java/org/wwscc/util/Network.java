/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Network
{
    private static final Logger log = Logger.getLogger(Network.class.getName());

    /**
     * Run through all of the network interfaces to find what should be the primary
     * external interface in use.  It has to have a hardware address.  It can't be
     * labeled with VMWare, VirtualBox, etc.  It must be listed as up.
     *
     * This should work on Windows with VirtualBox present and Linux.
     * Unknown about OS X at this point.
     *
     * @return a NetworkInterface or null if nothing matching is up
     */
    public static NetworkInterface getPrimaryInterface()
    {
        try
        {
            Enumeration<NetworkInterface> nie = NetworkInterface.getNetworkInterfaces();
            while (nie.hasMoreElements())
            {
                boolean classc = false;
                boolean ipv4 = false;
                NetworkInterface ni = nie.nextElement();

                try {
                    if (!ni.isUp() || ni.isLoopback() || ni.isPointToPoint())
                        continue;

                    if (ni.getHardwareAddress() == null)
                        continue; // this filters most of the junk

                    for (InterfaceAddress a : ni.getInterfaceAddresses()) {
                        InetAddress ia = a.getAddress();
                        if (ia instanceof Inet4Address) {
                            ipv4 = true;
                            Inet4Address a1 = (Inet4Address)ia;
                            if (a1.getAddress()[0] == -64) {
                                // 192.***
                                classc = true;
                            }
                        }
                    }

                    if (!ipv4)
                        continue;
                } catch (Throwable e) { // sometimes fails on windows
                    continue;
                }

                String dname = ni.getDisplayName();
                if (Prefs.isWindows()) {
                    if (dname.contains("VirtualBox")) continue;
                    if (dname.contains("VMware")) continue;
                    if (dname.contains("Tunneling")) continue;
                    if (dname.contains("Microsoft")) continue;
                    if (dname.contains("Hyper-V") && !classc) continue;
                } else if (Prefs.isLinux()) {
                    if (dname.startsWith("veth")) continue;
                    if (dname.startsWith("docker")) continue;
                    if (dname.startsWith("br-")) continue;
                }
                return ni;
            }
        } catch (SocketException se) {}
        return null;
    }

    /**
     * Attempt to find the InetAddress of the primary interface.
     * @return an InetAddress for the primary address or null if nothing is available
     */
    public static InetAddress getPrimaryAddress()
    {
        NetworkInterface intf = getPrimaryInterface();
        if (intf != null) {
            for (InterfaceAddress a : intf.getInterfaceAddresses()) {
                InetAddress ia = a.getAddress();
                if (ia instanceof Inet4Address) // stick to IPv4 for now
                    return (Inet4Address)ia;
            }
        }
        return null;
    }

    /**
     * Get the local hostname in a non exception method
     * @return the hostname or at least a string of unknownname if we can't find anything
     */
    public static String getLocalHostName()
    {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.log(Level.INFO, "Unable to get a localhost name?", e);
            return "unknownname";
        }
    }
}
