/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2012 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.timercomm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.wwscc.storage.LeftRightDialin;
import org.wwscc.storage.Run;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class TimerClient implements RunServiceInterface
{
    private static final Logger log = Logger.getLogger(TimerClient.class.getName());
    private static ObjectMapper objectMapper = new ObjectMapper();

    public static final String DIAL_MESSAGE       = "DIAL";
    public static final String RUN_MESSAGE        = "RUN";
    public static final String RUN_DELETE_MESSAGE = "RDELETE";

    Socket sock;
    BufferedReader in;
    OutputStream out;
    boolean done;

    public TimerClient(InetSocketAddress addr) throws IOException
    {
        sock = new Socket();
        sock.connect(addr);
        in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
        out = sock.getOutputStream();
        done = true;
    }

    public TimerClient(Socket s) throws IOException
    {
        sock = s;
        in = new BufferedReader(new InputStreamReader(s.getInputStream()));
        out = s.getOutputStream();
        done = true;
    }

    public TimerClient(OutputStream test)
    {
        sock = null;
        in = null;
        out = test;
        done = true;
    }

    public void start()
    {
        if (!done) return;
        done = false;
        new Thread(new ReceiverThread()).start();
    }

    public void stop()
    {
        done = true;
    }

    public boolean send(ObjectNode o)
    {
        try {
            log.log(Level.FINE, "Sending ''{0}'' to the timer", o);
            out.write((objectMapper.writeValueAsString(o)+"\n").getBytes());
            return true;
        } catch (IOException ioe) {
            log.log(Level.INFO, "TimerClient send failed: " + ioe, ioe);
            done = true;
        }
        return false;
    }

    @Override
    public boolean sendDial(LeftRightDialin d)
    {
        ObjectNode data = new ObjectNode(JsonNodeFactory.instance);
        data.put("type", DIAL_MESSAGE);
        data.set("data", objectMapper.valueToTree(d));
        return send(data);
    }

    @Override
    public boolean sendRun(Run r)
    {
        ObjectNode data = new ObjectNode(JsonNodeFactory.instance);
        data.put("type", RUN_MESSAGE);
        data.set("data", objectMapper.valueToTree(r));
        return send(data);
    }

    @Override
    public boolean deleteRun(Run r)
    {
        ObjectNode data = new ObjectNode(JsonNodeFactory.instance);
        data.put("type", RUN_DELETE_MESSAGE);
        data.set("data", objectMapper.valueToTree(r));
        return send(data);
    }

    protected void processLine(String line) throws IOException
    {
        ObjectNode msg = (ObjectNode) objectMapper.readTree(line);
        String type = msg.get("type").asText();
        switch (type)
        {
            case DIAL_MESSAGE:
                Messenger.sendEvent(MT.TIMER_SERVICE_DIALIN, objectMapper.treeToValue(msg.get("data"), LeftRightDialin.class));
                break;
            case RUN_MESSAGE:
                Messenger.sendEvent(MT.TIMER_SERVICE_RUN, objectMapper.treeToValue(msg.get("data"), Run.class));
                break;
            case RUN_DELETE_MESSAGE:
                Messenger.sendEvent(MT.TIMER_SERVICE_DELETE, objectMapper.treeToValue(msg.get("data"), Run.class));
                break;
            default:
                log.warning("Unknown message type: " + type);
                break;
        }
    }

    class ReceiverThread implements Runnable
    {
        @Override
        public void run()
        {
            try
            {
                Messenger.sendEvent(MT.TIMER_SERVICE_CONNECTION, new Object[] { TimerClient.this, true });

                while (!done)
                {
                    String line = "";
                    try
                    {
                        line = in.readLine();
                        log.log(Level.INFO, "TimerClient reads: {0}", line);
                        if (line == null)
                        {
                            log.info("readLine returns null, closing connection");
                            return;
                        }

                        processLine(line);
                    }
                    catch (IOException ioe)
                    {
                        log.warning(String.format("TimerClient processing error: %s (%s)", line, ioe));
                    }
                }
            }
            catch (Exception e)
            {
                log.log(Level.WARNING, "Unexpected timer connection failure: " + e, e);
            }
            finally
            {
                try { sock.close(); } catch (IOException ioe)  {}
                Messenger.sendEvent(MT.TIMER_SERVICE_CONNECTION, new Object[] { TimerClient.this, false });
            }
        }
    }
}