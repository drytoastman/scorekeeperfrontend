/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.mutable.MutableLong;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Simple presence and info detection.  Assumes that each IP will only be hosting one
 * of each type of service string at this point.  Maybe more later but there doesn't
 * seem to be any need to support that.
 */
public class Discovery
{
    private static final Logger log = Logger.getLogger(Discovery.class.getName());
    private static ObjectMapper objectMapper = new ObjectMapper();
    private static volatile Discovery singleton;

    public static final String DISCOVERY_GROUP = "224.0.0.251";
    public static final int    DISCOVERY_PORT  = 5454;
    public static final long   DEFAULT_TIMEOUT = 10000;

    public static final String BWTIMER_TYPE    = "BWTIMER";
    public static final String PROTIMER_TYPE   = "PROTIMER";
    public static final String DATABASE_TYPE   = "DATABASE";

    private static final int READ_TIMEOUT_MS = 1000;
    private static final int COOLOFF_MS      = 3000;
    private static final int MULTICAST_TTL   = 6;

    public interface DiscoveryListener
    {
        public void serviceChange(UUID serverid, String service, InetAddress src, ObjectNode data, boolean up);
    }

    class NoInternetException extends IOException {}

    public static Discovery get()
    {
        if (singleton == null) {
            singleton = new Discovery();
            new Thread(singleton.new DiscoveryThread(), "DiscoveryThread").start();
        }
        return singleton;
    }

    private ArrayNode announcements;
    private byte[] annonucementBytes;
    private Map<DiscoveryListener, StateAwareListener> listeners;
    private long timeoutms;
    private boolean resetFlag;

    protected Discovery()
    {
        announcements = new ArrayNode(JsonNodeFactory.instance);
        annonucementBytes = new byte[0];
        listeners = new HashMap<DiscoveryListener, StateAwareListener>();
        timeoutms = DEFAULT_TIMEOUT;
        Messenger.register(MT.NETWORK_CHANGED, (e,o) -> singleton.resetFlag = true);
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


    private void remove(UUID serverid, String service)
    {
        for (int ii = 0; ii < announcements.size(); ) {
            ObjectNode node = (ObjectNode)announcements.get(ii);
            if (node.get("serverid").asText().equals(serverid.toString()) &&
                node.get("service").asText().equals(service)) {
                announcements.remove(ii);
                continue; // continue at same index as we just removed it
            }
            ii++;
        }
    }

    public void registerService(UUID serverid, String service, ObjectNode data) throws JsonProcessingException
    {
        remove(serverid, service);
        ObjectNode serv = new ObjectNode(JsonNodeFactory.instance);
        serv.put("serverid", serverid.toString());
        serv.put("service", service);
        serv.set("data", data);
        announcements.add(serv);
        annonucementBytes = objectMapper.writeValueAsBytes(announcements);
    }

    public void unregisterService(UUID serverid, String service) throws JsonProcessingException
    {
        remove(serverid, service);
        annonucementBytes = objectMapper.writeValueAsBytes(announcements);
    }

    /**
     * Process new data from the network.  The actual JSON data looks like:
     * @throws IOException
     * @throws ParseException
     */
    protected void processNetworkData(InetAddress addr, byte buf[], int len) throws IOException
    {
        ArrayNode data = (ArrayNode)objectMapper.readTree(new String(buf, 0, len));
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
        ObjectNode data;
        InetAddress src;
        long time;
        public ReceivedData(ObjectNode d, InetAddress a, long t)
        {
            data = d;
            src  = a;
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
        public void newData(InetAddress source, ArrayNode entries)
        {
            for (Object o : entries)
            {
                ObjectNode serv = (ObjectNode)o;
                UUID serverid   = UUID.fromString(serv.get("serverid").asText());
                String service  = serv.get("service").asText();
                ObjectNode data = (ObjectNode)serv.get("data");

                ReceivedData info = new ReceivedData(data, source, System.currentTimeMillis());

                if (!registry.containsKey(serverid))
                    registry.put(serverid, new HashMap<String, ReceivedData>());
                Map<String, ReceivedData> services = registry.get(serverid);

                ReceivedData old = services.put(service, info);
                if ((old == null || !old.src.equals(info.src) || !old.data.equals(info.data)))
                    listener.serviceChange(serverid, service, info.src, info.data, true);
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
                        ReceivedData info = pair.getValue();
                        listener.serviceChange(serverid, pair.getKey(), info.src, info.data, false);
                        iter.remove();
                    }
                }
            }
        }
    }

    class DiscoveryThread implements Runnable
    {
        MutableLong  sendms = new MutableLong();
        MutableLong  checkms = new MutableLong();
        boolean reset = false;
        byte[] buf = new byte[512];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        MulticastSocket socket;

        public boolean timeout(MutableLong  m)
        {
            if (System.currentTimeMillis() >= m.longValue()+READ_TIMEOUT_MS) {
                m.setValue(System.currentTimeMillis());
                return true;
            }
            return false;
        }

        @Override
        public void run()
        {
            while (true)
            {
                try
                {
                    if (timeout(checkms))
                        checkForTimeouts();

                    if (resetFlag) {
                        if (socket != null) {
                            socket.close();
                            socket = null;
                        }
                        resetFlag = false;
                    }

                    if ((socket == null) || (socket.isClosed())) {
                        InetAddress bind = Network.getPrimaryAddress();
                        if (bind == null)
                            throw new NoInternetException();
                        socket = new MulticastSocket(DISCOVERY_PORT);
                        socket.setTimeToLive(MULTICAST_TTL);
                        socket.setSoTimeout(READ_TIMEOUT_MS);
                        socket.setInterface(bind);
                        socket.joinGroup(InetAddress.getByName(DISCOVERY_GROUP));
                        log.info(String.format("Joined %s on %s", DISCOVERY_GROUP, socket.getInterface()));
                    }

                    try {
                        packet.setData(buf);
                        socket.receive(packet);
                        processNetworkData(packet.getAddress(), buf, packet.getLength());
                    } catch (SocketTimeoutException ste) {}

                    if ((announcements.size() > 0) && timeout(sendms))
                        socket.send(new DatagramPacket(annonucementBytes, annonucementBytes.length, InetAddress.getByName(DISCOVERY_GROUP), DISCOVERY_PORT));
                }
                catch (NoInternetException nie)
                {
                    try { Thread.sleep(COOLOFF_MS); } catch (InterruptedException ie) {}
                }
                catch (IOException ioe)
                {
                    log.log(Level.WARNING, "IO Error in discoverythread: " + ioe, ioe);
                    resetFlag = true;
                    try { Thread.sleep(COOLOFF_MS); } catch (InterruptedException ie) {}
                }
                catch (Exception e)
                {
                    log.log(Level.WARNING, "General exception in discoverythread: " + e, e);
                }
            }
        }
    }
}
