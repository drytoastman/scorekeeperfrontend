package org.wwscc.tray;

import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;

import org.wwscc.util.Exec;
import org.wwscc.util.Prefs;

public class DockerContainer 
{
    public static final String NET_NAME = "scnet";

    public static class Db extends DockerContainer {
        public Db() { 
            super("drytoastman/scdb", "db");
            addVolume("scdatabase-"+Prefs.getVersion(), "/var/lib/postgresql/data");
            addVolume("scsocket", "/var/run/postgresql");
            addPort("127.0.0.1:6432", "6432");
            addPort("54329", "5432");
        }

    }

    public static class Web extends DockerContainer {
        public Web() { 
            super("drytoastman/scweb", "web");
            addVolume("scsocket", "/var/run/postgresql");
            addPort("80", "80");
        }
    }

    public static class Sync extends DockerContainer {
        public Sync() { 
            super("drytoastman/scsync", "sync");
            addVolume("scsocket", "/var/run/postgresql");
        }
    }
    
    /**
     * Quicker to send the stop signal all at once and let docker do the graceful
     * wait followed by a hard kill.
     */
    public static boolean stopAll(Collection<DockerContainer> containers)
    {
    	List<String> cmd = new ArrayList<String>(Arrays.asList("docker", "stop"));
    	Map<String, String> env = null;
    	for (DockerContainer c : containers) {
    		cmd.add(c.getName());
    		env = c.machineenv;
    	}
        return Exec.execit(Exec.build(env, cmd), null) == 0;
    }

    
    String image;
    String name;
    Map<String, String> volumes;
    Map<String, String> ports;
    Map<String, String> machineenv;
    
    public DockerContainer(String image, String name)
    {
        this.image      = image+":"+Prefs.getVersion();
        this.name       = name;        
        this.volumes    = new HashMap<String, String>();
        this.ports      = new HashMap<String, String>();
        this.machineenv = new HashMap<String, String>();
        // everybody gets the log directory
        volumes.put("sclogs-"+Prefs.getVersion(), "/var/log");
    }
    
    public String getName()
    {
        return name;
    }
    
    public void setMachineEnv(Map<String, String> env)
    {
        machineenv = env;
    }
    
    public void addVolume(String volume, String path)
    {
        volumes.put(volume, path);
    }
    
    public void addPort(String outside, String inside)
    {
        ports.put(outside, inside);
    }
    
    /**
     * Attempt to create any needed supporting objects, let docker filter out duplicates as
     * parsing the lists is extra work for no benefit.
     */
    public void createNetsAndVolumes()
    {
        Exec.execit(Exec.build(machineenv, "docker", "network", "create", NET_NAME), null);
        for (String vname : volumes.keySet())
            Exec.execit(Exec.build(machineenv, "docker", "volume", "create", vname), null);
    }
    
    /**
     * Start the container with the currently set parameters.  Some default unsettable parameters are:
     *  --net=scnet - the single user network that we create and use
     *  -e DEBUG=1 and -e LOG_LEVEL=DEBUG if debug mode is set
     * @return true if exec completed with zero return value
     */
    public boolean start()
    {
        List<String> cmd = new ArrayList<String>(Arrays.asList("docker", "run", "--rm", "-d", "--name="+name, "--net="+NET_NAME));
        if (Prefs.isDebug()) {
            cmd.add("-e");
            cmd.add("DEBUG=1");
            cmd.add("-e");
            cmd.add("LOG_LEVEL=DEBUG");
        }
        for (String k : volumes.keySet()) {
            cmd.add("-v");
            cmd.add(k+":"+volumes.get(k));
        }
        for (String k : ports.keySet()) {
            cmd.add("-p");
            cmd.add(k+":"+ports.get(k));
        }
        cmd.add(image);
        
        return Exec.execit(Exec.build(machineenv, cmd), null) == 0;
    }
    
    public boolean stop()
    {
        return Exec.execit(Exec.build(machineenv, "docker", "stop", name), null) == 0;
    }
    
    /**
     * Call docker to run pg_dump and pipe the output to file
     * @param file the file to write the backup data to
     * @return true if succeeded
     */
    public boolean dumpDatabase(Path file) 
    {
        ProcessBuilder p = Exec.build(machineenv, "docker", "exec", name, "pg_dump", "-U", "postgres", "-d", "scorekeeper");
        p.redirectOutput(Redirect.appendTo(file.toFile()));
        return Exec.execit(p, null) == 0;      
    }

    /**
     * Call docker to copy log files from container to host
     * @param dir the directory to copy the logs to
     * @return true if succeeded
     */
    public boolean copyLogs(Path dir)
    {
        return Exec.execit(Exec.build(machineenv, "docker", "cp", name+":/var/log", dir.toString()), null) == 0;      
    }
    
    /**
     * Send a SIGHUP to the container
     * @return true if succeeded
     */
    public boolean poke()
    {
        return Exec.execit(Exec.build(machineenv, "docker", "kill", "--signal=HUP", name), null) == 0;
    }
    
    /**
     * @param machineenv any necessary env variables for docker-machine
     * @param tosearch the container names to search for
     * @return the set of names from tosearch that are NOT running
     */
    public static Set<String> finddown(Map<String, String> machineenv, Set<String> tosearch)
    {
        Set<String> dead = new HashSet<String>(tosearch);
        byte buf[] = new byte[4096];
        if (Exec.execit(Exec.build(machineenv, "docker", "ps", "--format", "{{.Names}}"), buf) != 0)
            return dead;

        try (Scanner scan = new Scanner(new String(buf))) {
            while (scan.hasNextLine()) {
                dead.remove(scan.next());
                scan.nextLine();
            }
        } catch (NoSuchElementException nse) {}
        
        return dead;
    }
}
