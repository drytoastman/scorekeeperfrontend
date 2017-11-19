package org.wwscc.util;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

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
    public static final long   TIMEOUT_MS      = 10000;

    public static final String BWTIMER_TYPE    = "BWTIMER";
    public static final String PROTIMER_TYPE   = "PROTIMER";
    public static final String DATABASE_TYPE   = "DATABASE";

    public interface DiscoveryListener
    {
        public void serviceChange(String service, InetAddress ip, JSONObject data, boolean up);
    }

    public static Discovery get()
    {
        if (singleton == null)
            singleton = new Discovery();
        return singleton;
    }

    private MulticastSocket socket;
    private JSONObject localServices;
    private byte[] localServicesBytes;
    private Map<String, Map<DiscoveryListener, StateAwareListener>> listeners;
    private JSONParser parser;

    private Discovery()
    {
        localServices = new JSONObject();
        localServicesBytes = new byte[0];
        listeners = new Hashtable<String, Map<DiscoveryListener, StateAwareListener>>();
        parser = new JSONParser();
        new Thread(new ReceiverThread(), "DiscoveryReceiver").start();
        new Thread(new SenderThread(), "DiscoverySender").start();
    }

    public void addServiceListener(String service, DiscoveryListener listener)
    {
        Map<DiscoveryListener, StateAwareListener> map = listeners.get(service);
        if (map == null) {
            map = new Hashtable<DiscoveryListener, StateAwareListener>();
            listeners.put(service, map);
        }
        if (map.containsKey(listener)) {
            log.warning("addServiceListener called while already listening: " + listener);
        } else {
            map.put(listener, new StateAwareListener(service, listener));
        }
    }

    public void removeServiceListener(String service, DiscoveryListener listener)
    {
        Map<DiscoveryListener, StateAwareListener> map = listeners.get(service);
        if (map != null)
            map.remove(listener);
    }

    public void registerService(String service, JSONObject data)
    {
        localServices.put(service, data);
        localServicesBytes = localServices.toJSONString().getBytes();
    }

    public void unregisterService(String service)
    {
        localServices.remove(service);
        localServicesBytes = localServices.toJSONString().getBytes();
    }

    public InetAddress getLocalAddress() throws SocketException
    {
        if (socket != null)
            return socket.getInterface();
        return null;
    }


    class TimedJSON
    {
        JSONObject json;
        long time;
        public TimedJSON(JSONObject j, long t)
        {
            json = j;
            time = t;
        }
    }

    /**
     * Each listener maintains its own state of when things are new or not as listeners
     * come and go.
     */
    class StateAwareListener
    {
        String service;
        DiscoveryListener listener;
        Map<InetAddress, TimedJSON> registry;

        public StateAwareListener(String service, DiscoveryListener listener)
        {
            this.service = service;
            this.listener = listener;
            this.registry = new HashMap<InetAddress, TimedJSON>();
        }

        public void newData(InetAddress source, JSONObject serviceObject)
        {
            TimedJSON info = new TimedJSON(serviceObject, System.currentTimeMillis());
            TimedJSON old = registry.put(source, info);
            if ((old == null || !old.json.equals(info.json)))
                listener.serviceChange(service, source, info.json, true);
        }

        public void checkTimeouts()
        {
            long now = System.currentTimeMillis();
            Iterator<Entry<InetAddress, TimedJSON>> iter = registry.entrySet().iterator();
            while (iter.hasNext())
            {
                Map.Entry<InetAddress, TimedJSON> pair = iter.next();
                if (pair.getValue().time + TIMEOUT_MS < now) {
                    if (listeners.containsKey(service))
                        listener.serviceChange(service, pair.getKey(), pair.getValue().json, false);
                    iter.remove();
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
                    if (localServices.size() > 0)
                    {
                        checkSocket();
                        DatagramPacket packet = new DatagramPacket(localServicesBytes, localServicesBytes.length, InetAddress.getByName(DISCOVERY_GROUP), DISCOVERY_PORT);
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

                        // Note any new or changed data
                        JSONObject data = (JSONObject)parser.parse(new String(buf, 0, packet.getLength()));
                        for (Object o : data.keySet())
                        {
                            String service = (String)o;
                            Map<DiscoveryListener, StateAwareListener> map = listeners.get(service);
                            if (map != null)
                                for (StateAwareListener l : map.values())
                                    l.newData(packet.getAddress(), (JSONObject)data.get(service));
                        }
                    } catch (SocketTimeoutException ste) {

                    }

                    // Check for timeouts
                    for (Map<DiscoveryListener, StateAwareListener> map : listeners.values()) {
                        for (StateAwareListener l : map.values()) {
                            l.checkTimeouts();
                        }
                    }
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
