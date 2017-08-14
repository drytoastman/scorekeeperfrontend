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
import java.util.UUID;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import org.wwscc.storage.LeftRightDialin;
import org.wwscc.storage.Run;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.wwscc.util.Network;

/**
 * A server that create TimerClients for each connection as well as running a
 * TimerService to advertise our location.
 */
public class TimerService implements RunServiceInterface
{
	private static final Logger log = Logger.getLogger(TimerService.class.getName());

    JmDNS jmdns; 
	Thread autocloser;
	String servicetype;
	String servicename;
	ServerSocket serversock;
	Vector<RunServiceInterface> clients;
	Vector<RunServiceInterface> marked;
	boolean done;

	
	public TimerService(String type) throws IOException
	{
		serversock = new ServerSocket(0);
		servicetype = type;
		servicename = UUID.randomUUID().toString();
		log.log(Level.INFO, "Service {0} started on port {1}", new Object[]{servicename, serversock.getLocalPort()});
		
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

	class AutoCloseHook extends Thread
	{
		@Override
		public void run()
		{
			try { 
				jmdns.unregisterAllServices(); 
				jmdns.close(); 
			} catch (IOException ioe) {
				log.warning("Error shutting down TimerService announcer");
			}
		}
	}
	
	class ServiceThread implements Runnable
	{
		@Override
		public void run()
		{
			try {
	            jmdns = JmDNS.create(Network.getPrimaryAddress());                
				jmdns.registerService(ServiceInfo.create(servicetype, servicename, serversock.getLocalPort(), ""));
                Messenger.sendEvent(MT.TIMER_SERVICE_LISTENING, new Object[] { this, jmdns.getInetAddress().getHostAddress(), serversock.getLocalPort() } );
				autocloser = new AutoCloseHook();
				Runtime.getRuntime().addShutdownHook(autocloser);
			} catch (IOException ioe) {
				log.warning("Unable to register timer service for discovery: " + ioe);
			}
			
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

			try { serversock.close(); } catch (IOException ioe) {}
			autocloser.start();
			Runtime.getRuntime().removeShutdownHook(autocloser);
			
			Messenger.sendEvent(MT.TIMER_SERVICE_NOTLISTENING, this);
		}
	}
}
