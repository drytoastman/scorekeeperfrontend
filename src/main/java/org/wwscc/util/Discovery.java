package org.wwscc.util;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Simple presence and info detection.  Assumes that each IP will only be hosting one
 * of each type of service string at this point.  Maybe more later but there doesn't
 * seem to be any need to support that.
 */
@SuppressWarnings("unchecked")
public class Discovery
{
    private static final Logger log = Logger.getLogger(Discovery.class.getName());
    private static volatile Discovery singleton;

    public static final String DISCOVERY_GROUP = "224.0.0.251";
    public static final int    DISCOVERY_PORT  = 5454;
    public static final long   DEFAULT_TIMEOUT = 10000;

    public static final String BWTIMER_TYPE    = "BWTIMER";
    public static final String PROTIMER_TYPE   = "PROTIMER";
    public static final String DATABASE_TYPE   = "DATABASE";

    public interface DiscoveryListener
    {
        public void serviceChange(UUID serverid, String service, JSONObject data, boolean up);
    }

    public static Discovery get()
    {
        if (singleton == null) {
            singleton = new Discovery();
            singleton.startThreads();
        }
        return singleton;
    }

    private MulticastSocket socket;
    private JSONArray announcements;
    private byte[] annoucementBytes;
    private Map<DiscoveryListener, StateAwareListener> listeners;
    private JSONParser parser;
    private long timeoutms;

    protected Discovery()
    {
        announcements = new JSONArray();
        annoucementBytes = new byte[0];
        listeners = new HashMap<DiscoveryListener, StateAwareListener>();
        parser = new JSONParser();
        timeoutms = DEFAULT_TIMEOUT;
    }

    protected void startThreads()
    {
        new Thread(new ReceiverThread(), "DiscoveryReceiver").start();
        new Thread(new SenderThread(), "DiscoverySender").start();
    }

    protected void setTimeout(long ms)
    {
        timeoutms = ms;
    }

    public void addServiceListener(DiscoveryListener listener)
    {
        if (listeners.containsKey(listener)) {
            log.warning("addServiceListener called while already listening: " + listener);
        } else {
            listeners.put(listener, new StateAwareListener(listener));
        }
    }

    public void removeServiceListener(DiscoveryListener listener)
    {
        listeners.remove(listener);
    }


    private static Predicate<JSONObject> matchesKey(UUID serverid, String service)
    {
        return obj -> obj.get("serverid").equals(serverid.toString()) && obj.get("service").equals(service);
    }

    public void registerService(UUID serverid, String service, JSONObject data)
    {
        announcements.removeIf(matchesKey(serverid, service));
        JSONObject serv = new JSONObject();
        serv.put("serverid", serverid.toString());
        serv.put("service", service);
        serv.put("data", data);
        announcements.add(serv);
        annoucementBytes = announcements.toJSONString().getBytes();
    }

    public void unregisterService(UUID serverid, String service)
    {
        announcements.removeIf(matchesKey(serverid, service));
        annoucementBytes = announcements.toJSONString().getBytes();
    }

    /**
     * Process new data from the network.  The actual JSON data looks like:
     * @throws ParseException
     */
    protected void processNetworkData(InetAddress addr, byte buf[], int len) throws ParseException
    {
        JSONArray data = (JSONArray)parser.parse(new String(buf, 0, len));
        for (StateAwareListener l : listeners.values())
            l.newData(addr, data);
    }

    /**
     * Check for anything we haven't heard from in a while
     */
    protected void checkForTimeouts()
    {
        for (StateAwareListener l : listeners.values()) {
            l.checkTimeouts();
        }
    }

    class ReceivedData
    {
        JSONObject data;
        long time;
        public ReceivedData(JSONObject d, long t)
        {
            data = d;
            time = t;
        }
    }

    /**
     * Each listener maintains its own state of when things are new or not as listeners come and go.
     */
    class StateAwareListener
    {
        DiscoveryListener listener;
        Map<UUID, Map<String, ReceivedData>> registry;

        public StateAwareListener(DiscoveryListener listener)
        {
            this.listener = listener;
            this.registry = new HashMap<UUID, Map<String, ReceivedData>>();
        }

        /**
         * Process new data
         * @param source the source IP of the data
         * @param entries the list of entries we received, which looks like  { serverid:<UUID>, service:<String>, data:<JSON> }, ... ]
         */
        public void newData(InetAddress source, JSONArray entries)
        {
            for (Object o : entries)
            {
                JSONObject serv = (JSONObject)o;
                UUID serverid   = UUID.fromString((String)serv.get("serverid"));
                String service  = (String)serv.get("service");
                JSONObject data = (JSONObject)serv.get("data");

                data.put("ip", source);
                ReceivedData info = new ReceivedData(data, System.currentTimeMillis());

                if (!registry.containsKey(serverid))
                    registry.put(serverid, new HashMap<String, ReceivedData>());
                Map<String, ReceivedData> services = registry.get(serverid);

                ReceivedData old = services.put(service, info);
                if ((old == null || !old.data.equals(info.data)))
                    listener.serviceChange(serverid, service, info.data, true);
            }
        }

        public void checkTimeouts()
        {
            long now = System.currentTimeMillis();
            for (UUID serverid : registry.keySet())
            {
                Iterator<Entry<String, ReceivedData>> iter = registry.get(serverid).entrySet().iterator();
                while (iter.hasNext())
                {
                    Map.Entry<String, ReceivedData> pair = iter.next();
                    if (pair.getValue().time + timeoutms < now) {
                        listener.serviceChange(serverid, pair.getKey(), pair.getValue().data, false);
                        iter.remove();
                    }
                }
            }
        }
    }


    class SenderThread implements Runnable
    {
        @Override
        public void run()
        {
            while (true)
            {
                try
                {
                    if (announcements.size() > 0)
                    {
                        checkSocket();
                        DatagramPacket packet = new DatagramPacket(annoucementBytes, annoucementBytes.length, InetAddress.getByName(DISCOVERY_GROUP), DISCOVERY_PORT);
                        socket.send(packet);
                    }

                    Thread.sleep(3000);
                }
                catch (Exception e)
                {
                    log.log(Level.WARNING, "error in senderthread: " + e, e);
                    try { Thread.sleep(1000); } catch (InterruptedException ie) {}
                }
            }
        }
    }


    class ReceiverThread implements Runnable
    {
        @Override
        public void run()
        {
            byte[] buf = new byte[512];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            while (true)
            {
                try
                {
                    checkSocket();
                    packet.setData(buf);
                    try {
                        socket.receive(packet);
                        processNetworkData(packet.getAddress(), buf, packet.getLength());
                    } catch (SocketTimeoutException ste) {
                    }

                    checkForTimeouts();
                }
                catch (Exception e)
                {
                    log.log(Level.WARNING, "error in receiverthread: " + e, e);
                    try { Thread.sleep(1000); } catch (InterruptedException ie) {}
                }
            }
        }
    }

    private synchronized void checkSocket() throws IOException
    {
        if ((socket == null) || (socket.isClosed()))
            openSocket();
    }

    private void openSocket() throws IOException
    {
        closeSocket();
        socket = new MulticastSocket(DISCOVERY_PORT);
        socket.setTimeToLive(10);
        socket.setSoTimeout(1);
        socket.setInterface(Network.getPrimaryAddress());
        socket.joinGroup(InetAddress.getByName(DISCOVERY_GROUP));
        log.info(String.format("Joined %s on %s", DISCOVERY_GROUP, socket.getInterface()));
    }

    private void closeSocket()
    {
        if (socket != null)
        {
            try {
                socket.leaveGroup(InetAddress.getByName(DISCOVERY_GROUP));
            } catch (IOException ioe1) {
                log.log(Level.FINER, "leavegroup: " + ioe1, ioe1);
            }
            socket.close();
            socket = null;
        }
    }
}
