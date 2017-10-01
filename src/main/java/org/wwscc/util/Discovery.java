package org.wwscc.util;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.collections4.MapIterator;
import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

@SuppressWarnings("unchecked")
public class Discovery
{
    private static final Logger log = Logger.getLogger(Discovery.class.getName());
    private static Discovery singleton;
    
    public static final String DISCOVERY_GROUP = "224.0.0.251";
    public static final int    DISCOVERY_PORT  = 5454;
    public static final long   TIMEOUT_MS      = 10000;
    
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
    private MultiKeyMap<Object, InternalInfo> registry;
    private JSONObject localServices;
    private byte[] localServicesBytes;
    private Map<String, HashSet<DiscoveryListener>> listeners;
    private JSONParser parser;
    
    private Discovery()
    {
        registry = new MultiKeyMap<Object, InternalInfo>();
        localServices = new JSONObject();
        localServicesBytes = new byte[0];
        listeners = new Hashtable<String, HashSet<DiscoveryListener>>();
        parser = new JSONParser();
        new Thread(new ReceiverThread(), "DiscoveryReceiver").start();
        new Thread(new SenderThread(), "DiscoverySender").start();
    }
    
    public void addServiceListener(String service, DiscoveryListener listener)
    {
        HashSet<DiscoveryListener> h = listeners.get(service);
        if (h == null) {
            h = new HashSet<DiscoveryListener>();
            listeners.put(service, h);
        }
        h.add(listener);
    }
    
    public void removeServiceListener(String service, DiscoveryListener listener)
    {
        HashSet<DiscoveryListener> h = listeners.get(service);
        if (h != null)
            h.remove(listener);
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
    
    class InternalInfo 
    {
        JSONObject json; long time;
        public InternalInfo(JSONObject j, long t) { json = j; time = t; }
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
                    socket.receive(packet);
                    
                    JSONObject data = (JSONObject)parser.parse(new String(buf, 0, packet.getLength()));
                    long now = System.currentTimeMillis();
                    
                    // Note any new or changed data
                    for (Object o : data.keySet())
                    {
                        String service = (String)o;
                        MultiKey<Object> key = new MultiKey<Object>(service, packet.getAddress());
                        InternalInfo info = new InternalInfo((JSONObject)data.get(service), now);                        
                        InternalInfo old = registry.put(key, info);
                        
                        if ((old == null || !old.json.equals(info.json)) && listeners.containsKey(service))
                            for (DiscoveryListener l : listeners.get(service))
                                l.serviceChange(service, packet.getAddress(), info.json, true);
                    }
                    
                    // Check for timeouts
                    MapIterator<MultiKey<? extends Object>, InternalInfo> iter = registry.mapIterator();
                    while (iter.hasNext())
                    {
                        MultiKey<? extends Object> key = iter.next();
                        InternalInfo info = iter.getValue();

                        if (info.time + TIMEOUT_MS < now) {
                            String service   = (String)key.getKey(0);
                            InetAddress addr = (InetAddress)key.getKey(1);
                            
                            if (listeners.containsKey(service))
                                for (DiscoveryListener l : listeners.get(service))
                                    l.serviceChange(service, addr, info.json, false);

                            iter.remove();
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
