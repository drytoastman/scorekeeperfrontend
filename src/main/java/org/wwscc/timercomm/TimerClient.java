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

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.wwscc.storage.LeftRightDialin;
import org.wwscc.storage.Run;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;

@SuppressWarnings("unchecked")
public final class TimerClient implements RunServiceInterface
{
	private static final Logger log = Logger.getLogger(TimerClient.class.getName());

	Socket sock;
	BufferedReader in;
	OutputStream out;
	boolean done;

	public TimerClient(String host, int port) throws IOException
	{
		this(new InetSocketAddress(host, port));
	}

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
	
	public boolean send(JSONObject o)
	{
		try {
			log.log(Level.FINE, "Sending ''{0}'' to the timer", o);
			out.write((o.toJSONString()+"\n").getBytes());
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
	    JSONObject data = new JSONObject();
	    data.put("type", "DIAL");
	    d.encode(data);
		return send(data);
	}

	@Override
	public boolean sendRun(Run r)
	{
        JSONObject data = new JSONObject();
        data.put("type", "RUN");
        r.encode(data);	    
		return send(data);
	}

	@Override
	public boolean deleteRun(Run r)
	{
        JSONObject data = new JSONObject();
        data.put("type", "RDELETE");
        r.encode(data);     
        return send(data);
	}
	
	class ReceiverThread implements Runnable
	{
		@Override
		public void run()
		{
			try
			{
				Messenger.sendEvent(MT.TIMER_SERVICE_CONNECTION, new Object[] { TimerClient.this, true });
				JSONParser parser = new JSONParser();

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
						
						JSONObject cmd = (JSONObject)parser.parse(line);
						String type = (String)cmd.get("type");
						
						switch (type)
						{
						    case "DIAL":
	                            LeftRightDialin d = new LeftRightDialin();
	                            d.decode(cmd);
	                            Messenger.sendEvent(MT.TIMER_SERVICE_DIALIN, d);
	                            break;
						    case "RUN":
                                Run r = new Run(0.0);
                                r.decode(cmd);
                                Messenger.sendEvent(MT.TIMER_SERVICE_RUN, r);
                                break;
						    case "RDELETE":
		                        Run dr = new Run(0.0);
		                        dr.decode(cmd);
		                        Messenger.sendEvent(MT.TIMER_SERVICE_DELETE, dr);
		                        break;
						    default:
						        log.warning("Unknown message type: " + type);
						        break;
						}
					}
					catch (ParseException pe)
					{
						log.warning(String.format("TimerClient got bad data: %s (%s)", line, pe));
					}
				}
			}
			catch (IOException ex)
			{
				log.log(Level.INFO, "read failure: " + ex, ex);
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