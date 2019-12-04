/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2019 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.util;

import java.io.IOException;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Header;
import org.xbill.DNS.Message;
import org.xbill.DNS.Opcode;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.Type;

/**
 * Provide our simple name resolution on port 53 and 5353
 */
public class DNSServer
{
    private static final Logger log = Logger.getLogger(DNSServer.class.getName());
    private static volatile DNSServer singleton;

    public static final String MDNS_GROUP = "224.0.0.251";
    public static final int    MDNS_PORT  = 5353;
    public static final int    DNS_PORT   = 53;
    public static final long   DEFAULT_TIMEOUT = 10000;

    private static final int READ_TIMEOUT_MS = 1000;
    private static final int COOLOFF_MS      = 3000;
    private static final int MULTICAST_TTL   = 6;

    static class NoInternetException extends IOException {}
    static class DNSException extends IOException
    {
        int rcode;
        public DNSException(int rcode)
        {
            this.rcode = rcode;
        }
    }

    public static void start()
    {
        if (singleton == null) {
            singleton = new DNSServer();
        }
    }

    protected DNSServer()
    {
        ServerThread unicast = new ServerThread(new SocketWrapper());
        ServerThread multicast = new ServerThread(new MCSocketWrapper());
        Messenger.register(MT.NETWORK_CHANGED, (e,o) -> {
            unicast.reset = true;
            multicast.reset = true;
        });

        Thread t1 = new Thread(unicast, "UnicastDNSThread");
        t1.setDaemon(true);
        t1.start();

        Thread t2 = new Thread(multicast, "MulticastDNSThread");
        t2.setDaemon(true);
        t2.start();
    }

    protected byte[] dnsReply(byte[] in)
    {
        Message message = null;
        Header header = null;
        try {
            message = new Message(in);
        } catch (IOException ioe) {
            in[3] = Rcode.FORMERR;
            return in;
        }

        try
        {
            header = message.getHeader();
            if (header.getFlag(Flags.QR)) return null;
            Record query = message.getQuestion();

            if (header.getRcode() != Rcode.NOERROR) throw new DNSException(Rcode.FORMERR);
            if (header.getOpcode() != Opcode.QUERY) throw new DNSException(Rcode.NOTIMP);
            if ((query.getType() != Type.A) || (query.getDClass() != DClass.IN)) throw new DNSException(Rcode.NOTIMP);

            String req = query.getName().getLabelString(0);
            Record res;

            if (req.startsWith("de"))
                res = new ARecord(query.getName(), DClass.IN, 60, InetAddress.getByName("192.168.15.102"));
            else if (req.startsWith("reg"))
                res = new ARecord(query.getName(), DClass.IN, 60, InetAddress.getByName("192.168.15.102"));
            else
                throw new UnknownHostException();

            header.setFlag(Flags.QR);
            header.setFlag(Flags.RA);
            header.setFlag(Flags.AA);
            log.config(res.toString());
            message.addRecord(res, Section.ANSWER);

        } catch (UnknownHostException uhe) {
            header.setRcode(Rcode.NXDOMAIN);
        } catch (DNSException dnse) {
            header.setRcode(dnse.rcode);
        }

        return message.toWire();
    }


    static class SocketWrapper
    {
        DatagramSocket sock;

        public SocketWrapper()
        {
            sock = null;
        }

        public void reset()
        {
            if (sock != null) {
                sock.close();
                sock = null;
            }
        }

        public void checkconnect() throws IOException
        {
            if ((sock == null) || (sock.isClosed()))
            {
                InetAddress bind = Network.getPrimaryAddress();
                if (bind == null)
                    throw new NoInternetException();
                sock = new DatagramSocket(DNS_PORT);
                sock.setReuseAddress(true);
                sock.setSoTimeout(READ_TIMEOUT_MS);
            }
        }

        public void receive(DatagramPacket p) throws IOException
        {
            sock.receive(p);
        }

        public void send(DatagramPacket p) throws IOException
        {
            sock.send(p);
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
                s.setInterface(bind);
                s.joinGroup(InetAddress.getByName(MDNS_GROUP));
                sock = s;
                log.info(String.format("Joined %s on %s", MDNS_GROUP, bind));
            }
        }
    }


    class ServerThread implements Runnable
    {
        byte[] buf = new byte[512];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        SocketWrapper socket;
        boolean reset;

        public ServerThread(SocketWrapper wrapper)
        {
            socket = wrapper;
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
            while (true)
            {
                try
                {
                    if (reset) {
                        socket.reset();
                        reset = false;
                    }
                    socket.checkconnect();

                    try {
                        packet.setData(buf);
                        socket.receive(packet);

                        byte reply[] = dnsReply(buf);
                        if (reply == null) continue;

                        packet.setData(reply);
                        socket.send(packet);
                    } catch (SocketTimeoutException ste) {}
                }
                catch (NoInternetException nie)
                {
                    sleep(COOLOFF_MS);
                }
                catch (BindException be)
                {
                    log.log(Level.WARNING, "Bind Error: " + be);
                    reset = true;
                    sleep(COOLOFF_MS*10);
                }
                catch (IOException ioe)
                {
                    log.log(Level.WARNING, "IO Error in proxy thread: " + ioe, ioe);
                    reset = true;
                    sleep(COOLOFF_MS);
                }
                catch (Exception e)
                {
                    log.log(Level.WARNING, "General exception in proxy thread: " + e, e);
                }
            }
        }
    }
}
