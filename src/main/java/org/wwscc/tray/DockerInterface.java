package org.wwscc.tray;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.wwscc.util.Prefs;

/**
 * Thread to keep pinging our services to check their stlatus.  It pauses for 3 seconds but can
 * be woken by anyone calling notify on the class object.
 */
public class DockerInterface
{
	private static final Logger log = Logger.getLogger(DockerInterface.class.getName());
	private static String[] compose = { "docker-compose", "-p", "nwrsc", "-f", "docker-compose.yaml" };
	private static String[] machine = { "docker-machine" };
	private static File basedir = new File(Prefs.getDocRoot());
	private static Map<String,String> dockerenv = new HashMap<String, String>();
			
	public static Map<String,String> machineenv()
	{
		byte buf[] = new byte[4096];
		if (execit(build(basedir, machine, "env", "--shell", "cmd"), buf) != 0) {
			return dockerenv;
		}
		try (Scanner scan = new Scanner(new String(buf))) 
		{
			while (scan.hasNext())
			{
				String set = scan.next();
				String var = scan.next();
				if (set.equals("SET")) {
					String p[] = var.split("=");
					dockerenv.put(p[0], p[1]);
				} else {
					scan.nextLine();
				}
			}
		}		
		catch (NoSuchElementException nse)
		{
		}
		
		return dockerenv;
	}
	
	public static boolean machinepresent()
	{
		return testit(build(basedir, machine, "-h"));
	}
	
	public static boolean machinecreated()
	{
		byte buf[] = new byte[1024];
		if (execit(build(basedir, machine, "ls"), buf) != 0) {
			return false;
		}
		try (Scanner scan = new Scanner(new String(buf))) {
			scan.nextLine();
			return (scan.hasNextLine());
		}
	}
	
	public static boolean createmachine()
	{
		return execit(build(basedir, machine, "create", "-d", "virtualbox", "default"), null) == 0;
	}
	
	public static boolean machinerunning()
	{
		return execit(build(basedir, machine, "ip"), null) == 0;		
	}
	
	public static boolean startmachine()
	{
		return execit(build(basedir, machine, "start"), null) == 0;
	}

	public static boolean up()
	{
		return execit(build(basedir, compose, "up", "-d"), null) == 0;		
	}

	public static boolean down()
	{
		return execit(build(basedir, compose, "down"), null) == 0;		
	}
	
	public static boolean[] ps()
	{
		boolean ret[] = new boolean[] { false, false };
		byte buf[] = new byte[4096];
		if (execit(build(basedir, compose, "ps"), buf) != 0)
		{
			return ret;
		}
		
		try (Scanner scan = new Scanner(new String(buf))) 
		{
			scan.nextLine();
			scan.nextLine();
			while (scan.hasNextLine())
			{
				String name = scan.next();
				boolean up  = scan.nextLine().contains("Up");
				if (name.contains("nwrsc_web")) {
					ret[0] = up;
				} else if (name.contains("nwrsc_db")) {
					ret[1] = up;
				}
			}
		}
		catch (NoSuchElementException nse)
		{
		}
		return ret;		
	}

	private static ProcessBuilder build(File root, String[] cmd, String ... additional)
	{
		List<String> cmdlist = new ArrayList<String>();
		for (String s : cmd)
			cmdlist.add(s);
		for (String s : additional)
			cmdlist.add(s);
		ProcessBuilder p = new ProcessBuilder(cmdlist);
        p.directory(root);
        p.redirectErrorStream(true);
        Map <String,String> env = p.environment();
        env.putAll(dockerenv);
        return p;
	}
	
	/*
	 * Exec a process builder and collect the output.
	 */
	private static int execit(ProcessBuilder in, byte[] buffer)
	{
		try {
            Process p = in.start();
            int ret = p.waitFor();
            log.log(Level.FINE, "{0} returns {1}", new Object [] { in.command().toString(), ret });
            
            if ((buffer == null) && ret != 0) // create buffer for errors if not present
            {
            	buffer = new byte[8192];
            }
            
            if (buffer != null) // read stream if we have a buffer
            {
	            InputStream is = p.getInputStream();
	            int len = is.read(buffer);
	            is.close();
            	log.log((ret != 0) ? Level.INFO : Level.FINEST, "Execution Output:\n " + new String(buffer, 0, len));
            }
            
            p.destroy();            
            return ret;
		} catch (InterruptedException | IOException ie) {
			log.log(Level.WARNING, "Exec failed " + ie, ie);
		}
		return -1;
	}
	
	/**
	 * Just testing to see if it executes cleanly or not.  Don't log errors or look at output
	 * @param in the process to test
	 * @return true if execution was successful, false if not 
	 */
	private static boolean testit(ProcessBuilder in)
	{
		try {
            log.log(Level.FINE, "testing {0}", in.command().toString());
            Process p = in.start();
            return p.waitFor() == 0;
		} catch (InterruptedException | IOException ie) {
		}
		return false;
	}
}
