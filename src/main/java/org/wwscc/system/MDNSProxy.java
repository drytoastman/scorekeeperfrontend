/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2019 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system;

import java.io.IOException;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.wwscc.util.BroadcastState;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.wwscc.util.Network;

/**
 * Proxy MDNS UDP packets to 127.0.0.1:53/udp as docker doesn't do multicast
 */
public class MDNSProxy
{
    private static final Logger log = Logger.getLogger(MDNSProxy.class.getName());
    private static volatile MDNSProxy singleton;

    public static InetAddress  MDNS_GROUP = null;
    public static final int     MDNS_PORT = 5353;
    public static final int      DNS_PORT = 53;
    public static final int MULTICAST_TTL = 4;
    public static final int    COOLOFF_MS = 5000;

    static {
        try {
            MDNS_GROUP = InetAddress.getByName("224.0.0.251");
        } catch (UnknownHostException e) {}
    }

    ServerThread thread;

    static class NoInternetException extends IOException {}
    static class PausedException extends IOException {}

    public static void start()
    {
        if (singleton == null) {
            singleton = new MDNSProxy();
        }
        singleton.thread.pause = false;
    }

    public static void stop()
    {
        if (singleton == null)
            return;
        singleton.thread.pause = true;
    }

    protected MDNSProxy()
    {
        thread = new ServerThread();
        Messenger.register(MT.NETWORK_CHANGED, (e,o) -> {
            thread.reset   = true;
        });

        Thread t1 = new Thread(thread, "MDNSProxy");
        t1.setDaemon(true);
        t1.start();
    }


    static class SocketWrapper
    {
        DatagramSocket sock;
        public SocketWrapper() { sock = null; }
        public void receive(DatagramPacket p) throws IOException { sock.receive(p); }
        public void send(DatagramPacket p)    throws IOException { sock.send(p); }

        public void reset() {
            if (sock != null) {
                sock.close();
                sock = null;
            }
        }

        public void checkconnect() throws IOException {
            if ((sock == null) || (sock.isClosed())) {
                sock = new DatagramSocket();
                sock.setReuseAddress(true);
                sock.setSoTimeout(250); // expect fairly quick response from self
            }
        }
    }

    static class MCSocketWrapper extends SocketWrapper
    {
        @Override
        public void checkconnect() throws IOException
        {
            if ((sock == null) || (sock.isClosed()))
            {
                InetAddress bind = Network.getPrimaryAddress();
                if (bind == null)
                    throw new NoInternetException();
                MulticastSocket s = new MulticastSocket(MDNS_PORT);
                s.setTimeToLive(MULTICAST_TTL);
                // s.setInterface(bind);
                // s.joinGroup(MDNS_GROUP);
                s.joinGroup(new InetSocketAddress(MDNS_GROUP, MDNS_PORT), Network.getPrimaryInterface());
                sock = s;
                log.info(String.format("Joined %s on %s", MDNS_GROUP, bind));
            }
        }
    }


    class ServerThread implements Runnable
    {
        BroadcastState<Boolean> isUp;
        MCSocketWrapper server;
        SocketWrapper client;
        boolean reset;
        boolean pause;

        public ServerThread()
        {
            server = new MCSocketWrapper();
            client = new SocketWrapper();
            isUp = new BroadcastState<Boolean>(MT.MDNS_OK, null);
            isUp.set(false);
        }

        private void sleep(int ms)
        {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException ie) {}
        }

        @Override
        public void run()
        {
            byte[] buf = new byte[512];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            while (true)
            {
                try
                {
                    if (pause)
                        throw new PausedException();
                    if (reset) {
                        isUp.set(false);
                        server.reset();
                        client.reset();
                        reset = false;
                    }

                    server.checkconnect();
                    client.checkconnect();
                    isUp.set(true);
                    packet.setData(buf);
                    server.receive(packet); // blocking call

                    if (pause) throw new PausedException();

                    if ((packet.getLength() > 12) && ((buf[3] & 0x80) == 0)) {
                        // packet has proper length and is a query (not answer)
                        packet.setAddress(InetAddress.getLoopbackAddress());
                        packet.setPort(DNS_PORT);
                        client.send(packet);
                        client.receive(packet);

                        packet.setAddress(MDNS_GROUP);
                        packet.setPort(MDNS_PORT);
                        server.send(packet);
                    }

                } catch (SocketTimeoutException ie) {
                    log.warning("DNS Proxy client read timeout");
                } catch (NoInternetException|PausedException ie) {
                    sleep(COOLOFF_MS);
                } catch (BindException be) {
                    log.log(Level.WARNING, "Proxy bind error: " + be);
                    reset = true;
                    sleep(COOLOFF_MS*5);
                } catch (Exception e) {
                    log.log(Level.WARNING, "Exception in proxy thread: " + e, e);
                    reset = true;
                    sleep(COOLOFF_MS);
                }
            }
        }
    }
}
