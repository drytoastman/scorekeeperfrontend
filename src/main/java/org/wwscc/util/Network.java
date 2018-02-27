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
                try
                {
                    NetworkInterface ni = nie.nextElement();
                    if (ni.getHardwareAddress() == null) continue; // this filters most of the junk
                    String dname = ni.getDisplayName();
                    if (dname.contains("VirtualBox")) continue;
                    if (dname.contains("VMware")) continue;
                    if (dname.contains("Tunneling")) continue;
                    if (dname.contains("Microsoft")) continue;
                    if (!ni.isUp()) continue;
                    return ni;
                } catch (SocketException se) {}
            }
        } catch (SocketException se) {}
        return null;
    }

    /**
     * Attempt to find the InetAddress of the primary interface.
     * @param intf the interface to get an address from or null to lookup the primary interface first
     * @return an InetAddress for the primary address or null if nothing is available
     */
    public static InetAddress getPrimaryAddress(NetworkInterface intf)
    {
        if (intf == null)
            intf = getPrimaryInterface();
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
