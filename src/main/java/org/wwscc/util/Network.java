package org.wwscc.util;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.logging.Logger;

public class Network 
{
    private static final Logger log = Logger.getLogger(Network.class.getName());

    /**
     * For multihomed machines (i.e. anything with Docker Machine), use the 
     * routing table default route to find what should be the primary network
     * facing interface.
     * @return an InetAddress for the primary address
     */
    public static InetAddress getPrimaryAddress()
    {
        InetAddress ret;
        try {
            DatagramSocket s = new DatagramSocket();
            s.connect(InetAddress.getByAddress(new byte[]{1,1,1,1}), 0);
            ret = s.getLocalAddress();
            s.close();
            return ret;
        } catch (SocketException | UnknownHostException se) {
            log.info("get by route failed: " + se);
        } 
        
        try {
            return InetAddress.getLocalHost();
        } catch (UnknownHostException ex) {
            log.info("getLocalHost failed: " + ex);
        }
     
        return InetAddress.getLoopbackAddress();
    }
}
