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
 * Interface for calling docker related functions to start, stop and check status.
 */
public class DockerInterface
{
	private static final Logger log = Logger.getLogger(DockerInterface.class.getName());
    private static final Logger sublog = Logger.getLogger("docker.subprocess");
	private static String[] compose = { "docker-compose", "-p", "scorekeeper", "-f", "docker-compose.yaml" };
	private static String[] machine = { "docker-machine" };
	private static File basedir = new File(Prefs.getDocRoot());
	private static Map<String,String> dockerenv = new HashMap<String, String>();

	/**
	 * Sets and returns the environment variables used by docker-compose if docker-machine is present
	 * @return a map of the environment variables that were set
	 */
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
	
	/**
	 * @return true if docker-machine is installed
	 */
	public static boolean machinepresent()
	{
		return testit(build(basedir, machine, "-h"));
	}
	
	/**
	 * @return true if docker-machine has created a virtualbox node
	 */
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
	
	/**
	 * Create a new virtualbox node with name 'default'.  Normally should be done
	 * on installation.
	 * @return true if creation suceeded
	 */
	public static boolean createmachine()
	{
		return execit(build(basedir, machine, "create", "-d", "virtualbox", "default"), null) == 0;
	}
	
	/**
	 * @return true if virtualbox node is running
	 */
	public static boolean machinerunning()
	{
		return execit(build(basedir, machine, "ip"), null) == 0;		
	}
	
	/**
	 * Try to start virtual box node
	 * @return true if command returns success
	 */
	public static boolean startmachine()
	{
		return execit(build(basedir, machine, "start"), null) == 0;
	}

	/**
	 * Call docker-compose to bring up the backend
	 * @return true if compose succeeded
	 */
	public static boolean up()
	{
		return execit(build(basedir, compose, "up", "-d", "--no-color"), null) == 0;		
	}

	/**
	 * Call docker-compose to bring the backend down
	 * @return true if compose succeeded
	 */
	public static boolean down()
	{
		return execit(build(basedir, compose, "down"), null) == 0;		
	}
	
	/**
	 * Returns the status of the backend componets as an array of boolean
	 * @return [ true if web running, true if db running ]
	 */
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
				if (name.contains("scorekeeper_web")) {
					ret[0] = up;
				} else if (name.contains("scorekeeper_db")) {
					ret[1] = up;
				}
			}
		}
		catch (NoSuchElementException nse)
		{
		}
		return ret;		
	}

	/**
	 * Create a processbuilder object from components and environment
	 * @param root the base directory to run in
	 * @param cmd the initial array of arguments
	 * @param additional a varargs list of additional arguments
	 * @return a new ProcessBuilder object
	 */
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
	
	/**
	 * Exec a process builder and collect the output.
	 */
	private static int execit(ProcessBuilder in, byte[] buffer)
	{
		try {
            Process p = in.start();
            int ret = p.waitFor();
            log.log(Level.FINER, "{0} returns {1}", new Object [] { in.command().toString(), ret });
            
            if ((buffer == null) && ret != 0) // create buffer for errors if not present
            {
            	buffer = new byte[8192];
            }
            
            if (buffer != null) // read stream if we have a buffer
            {
	            InputStream is = p.getInputStream();
	            int len = is.read(buffer);
	            is.close();
	            if (ret != 0) {
	                log.log(Level.INFO, "Docker Error:\n " + new String(buffer, 0, len));
	            } else {
	                sublog.log(Level.FINEST, "Execution Output:\n " + new String(buffer, 0, len));
	            }
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
