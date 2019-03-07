/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2019 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.timercomm;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;
import java.util.function.Function;
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
    Vector<TimerClient> clients;
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

        clients = new Vector<TimerClient>();
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
    public boolean sendTree()
    {
        return forEachClient(tc -> tc.sendTree());
    }

    @Override
    public boolean sendDial(LeftRightDialin d)
    {
        return forEachClient(tc -> tc.sendDial(d));
    }

    @Override
    public boolean sendLDial(double left)
    {
        return forEachClient(tc -> tc.sendLDial(left));
    }

    @Override
    public boolean sendRDial(double right)
    {
        return forEachClient(tc -> tc.sendRDial(right));
    }

    @Override
    public boolean sendRun(Run.WithRowId r)
    {
        return forEachClient(tc -> tc.sendRun(r));
    }

    @Override
    public boolean deleteRun(Run.WithRowId r)
    {
        return forEachClient(tc -> tc.deleteRun(r));
    }

    private boolean forEachClient(Function<TimerClient, Boolean> operation)
    {
        boolean ok = clients.stream().map(operation).reduce(true, (a,b) -> a&&b);
        clients.removeIf(tc -> tc.done);
        return ok;
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

                for (TimerClient tc : clients)
                {
                    tc.stop();
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
