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
import java.net.SocketTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.wwscc.util.BroadcastState;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.xbill.DNS.Message;
import org.xbill.DNS.SimpleResolver;

/**
 * We can't easily forward UDP packets over VBox interface to get to our backend services.
 * In that case just run the server on the front and use a TCP/DNS client to talk to the
 * backend.
 */
public class VBoxDNSServer
{
    private static final Logger log = Logger.getLogger(VBoxDNSServer.class.getName());
    private static volatile VBoxDNSServer singleton;

    public static final int   DNS_PORT = 53;
    public static final int COOLOFF_MS = 5000;

    ServerThread server;

    static class NoInternetException extends IOException {}
    static class PausedException extends IOException {}

    public static void start()
    {
        if (singleton == null) {
            singleton = new VBoxDNSServer();
        }
        singleton.server.pause = false;
    }

    public static void stop()
    {
        if (singleton == null)
            return;
        singleton.server.pause = true;
    }

    protected VBoxDNSServer()
    {
        server = new ServerThread();
        Messenger.register(MT.NETWORK_CHANGED, (e,o) -> {
            server.reset   = true;
        });

        Thread t1 = new Thread(server, "TCPDNSProxy");
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
                sock = new DatagramSocket(DNS_PORT);
                sock.setReuseAddress(true);
            }
        }
    }


    class ServerThread implements Runnable
    {
        BroadcastState<Boolean> isUp;
        SocketWrapper socket;
        boolean reset;
        boolean pause;

        public ServerThread()
        {
            isUp = new BroadcastState<Boolean>(MT.VBOX_HACK_OK, null);
            socket = new SocketWrapper();
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
            SimpleResolver resolver = null;

            while (true)
            {
                try
                {
                    if (resolver == null) {
                        resolver = new SimpleResolver("127.0.0.1");
                        resolver.setTCP(true);
                    }

                    if (pause)
                        throw new PausedException();
                    if (reset) {
                        isUp.set(false);
                        socket.reset();
                        reset = false;
                    }

                    socket.checkconnect();
                    isUp.set(true);
                    packet.setData(buf);
                    socket.receive(packet); // blocking call

                    if (pause) throw new PausedException();

                    Message message = new Message(buf);
                    Message response = resolver.send(message);
                    if (response == null) continue;

                    packet.setData(response.toWire());
                    socket.send(packet);

                } catch (SocketTimeoutException ie) {
                    log.warning("VBox DNS Proxy client read timeout");
                } catch (NoInternetException|PausedException ie) {
                    sleep(COOLOFF_MS);
                } catch (BindException be) {
                    log.log(Level.WARNING, socket.getClass().getName() + ": " + be);
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
