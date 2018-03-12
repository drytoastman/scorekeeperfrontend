/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2012 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.timercomm;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.wwscc.storage.LeftRightDialin;
import org.wwscc.storage.Run;
import org.wwscc.util.Discovery;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A server that create TimerClients for each connection as well as running a
 * discovery service to advertise our location.
 */
public class TimerServer implements RunServiceInterface
{
    private static final Logger log = Logger.getLogger(TimerServer.class.getName());
    public static final int TIMER_DEFAULT_PORT = 54328;

    String servicetype;
    ServerSocket serversock;
    Vector<RunServiceInterface> clients;
    Vector<RunServiceInterface> marked;
    boolean done;

    public TimerServer(String type) throws IOException
    {
        try {
            serversock = new ServerSocket(TIMER_DEFAULT_PORT);
        } catch (IOException ioe) {
            serversock = new ServerSocket(0);
        }
        servicetype = type;
        log.log(Level.INFO, "Service {0} started on port {1}", new Object[]{type, serversock.getLocalPort()});

        clients = new Vector<RunServiceInterface>();
        marked = new Vector<RunServiceInterface>();
        done = true;
    }

    public void start()
    {
        if (!done) return;
        done = false;
        new Thread(new ServiceThread()).start();
    }

    public void stop()
    {
        done = true;
    }

    @Override
    public boolean sendDial(LeftRightDialin d)
    {
        boolean ret = true;
        for (RunServiceInterface c : clients) {
            if (!c.sendDial(d))
            {
                marked.add(c);
                ret = false;
            }
        }
        clients.removeAll(marked);
        marked.clear();
        return ret;
    }

    @Override
    public boolean sendRun(Run r)
    {
        boolean ret = true;
        for (RunServiceInterface c : clients) {
            if (!c.sendRun(r))
            {
                marked.add(c);
                ret = false;
            }
        }
        clients.removeAll(marked);
        marked.clear();
        return ret;
    }

    @Override
    public boolean deleteRun(Run r)
    {
        boolean ret = true;
        for (RunServiceInterface c : clients) {
            if (!c.deleteRun(r))
            {
                marked.add(c);
                ret = false;
            }
        }
        clients.removeAll(marked);
        marked.clear();
        return ret;
    }


    class ServiceThread implements Runnable
    {
        @Override
        public void run()
        {
            try
            {
                ObjectNode data = new ObjectNode(JsonNodeFactory.instance);
                data.put("serviceport", serversock.getLocalPort());
                Discovery.get().registerService(Prefs.getServerId(), servicetype, data);
                Messenger.sendEvent(MT.TIMER_SERVICE_LISTENING, new Object[] { this, serversock.getLocalPort() } );

                while (!done)
                {
                    try
                    {
                        Socket s = serversock.accept();
                        TimerClient c = new TimerClient(s);
                        c.start();
                        clients.add(c);
                    }
                    catch (IOException ioe)
                    {
                        log.log(Level.INFO, "Server error: {0}", ioe);
                    }
                }

                for (RunServiceInterface tc : clients)
                {
                    ((TimerClient)tc).stop();
                }

                Discovery.get().unregisterService(Prefs.getServerId(), servicetype);
                try { serversock.close(); } catch (IOException ioe) {}

                Messenger.sendEvent(MT.TIMER_SERVICE_NOTLISTENING, this);
            }
            catch (JsonProcessingException je)
            {
                log.log(Level.WARNING, "Failure in timer server setup/teardown: " + je, je);
            }
        }
    }
}
