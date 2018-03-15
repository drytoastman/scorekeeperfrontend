/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system.docker;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.wwscc.storage.Database;
import org.wwscc.util.Exec;
import org.wwscc.util.Prefs;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DockerContainer
{
    private static final Logger log = Logger.getLogger(DockerContainer.class.getCanonicalName());
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static final String DBV_PREFIX   = "scdatabase-";
    public static final String LOGV_PREFIX  = "sclogs-";
    public static final String SOCKV_PREFIX = "scsocket";
    public static final String NET_NAME     = "scnet";

    public static class Db extends DockerContainer {
        public Db() {
            super("drytoastman/scdb", "db");
            addVolume(DBV_PREFIX+Prefs.getVersion(), "/var/lib/postgresql/data");
            addVolume(LOGV_PREFIX+Prefs.getVersion(), "/var/log");
            addVolume(SOCKV_PREFIX, "/var/run/postgresql");
            addPort("127.0.0.1:6432", "6432");
            addPort("54329", "5432");
        }
    }

    public static class Web extends DockerContainer {
        public Web() {
            super("drytoastman/scweb", "web");
            addVolume(LOGV_PREFIX+Prefs.getVersion(), "/var/log");
            addVolume(SOCKV_PREFIX, "/var/run/postgresql");
            addPort("80", "80");
        }
    }

    public static class Sync extends DockerContainer {
        public Sync() {
            super("drytoastman/scsync", "sync");
            addVolume(LOGV_PREFIX+Prefs.getVersion(), "/var/log");
            addVolume(SOCKV_PREFIX, "/var/run/postgresql");
        }
    }

    String image;
    String name;
    Map<String, String> volumes;
    Map<String, String> ports;
    Map<String, String> machineenv;

    public DockerContainer(String image, String name)
    {
        if (image.contains(":"))
            this.image  = image;
        else
            this.image  = image+":"+Prefs.getVersion();
        this.name       = name;
        this.volumes    = new HashMap<String, String>();
        this.ports      = new HashMap<String, String>();
        this.machineenv = new HashMap<String, String>();
    }

    public String getName()
    {
        return name;
    }

    public void setMachineEnv(Map<String,String> env)
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
    public void createVolumes()
    {
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
        return start(-1);
    }

    public synchronized boolean start(int waitms)
    {
        synchronized (DockerContainer.class) {
            List<String> cmd = new ArrayList<String>(Arrays.asList("docker", "run", "--rm", "-d", "--name="+name, "--net="+NET_NAME));
            cmd.add("-e");
            cmd.add("UI_TIME_ZONE="+TimeZone.getDefault().getID());
            cmd.add("-e");
            cmd.add("SECRET='"+Prefs.getCookieSecret()+"'");
            cmd.add("-e");
            cmd.add("LOG_LEVEL="+Prefs.getLogLevel().getPythonLevel());
            if (Prefs.isDebug()) {
                cmd.add("-e");
                cmd.add("DEBUG=1");
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

            return Exec.execit(Exec.build(machineenv, cmd), waitms) == 0;
        }
    }

    public synchronized boolean restart()
    {
        synchronized (DockerContainer.class) {
            return Exec.execit(Exec.build(machineenv, "docker", "restart", name), null) == 0;
        }
    }

    public synchronized boolean stop()
    {
        synchronized (DockerContainer.class) {
            return Exec.execit(Exec.build(machineenv, "docker", "stop", name), null) == 0;
        }
    }

    public boolean kill()
    {
        synchronized (DockerContainer.class) {
            return Exec.execit(Exec.build(machineenv, "docker", "kill", name), null) == 0;
        }
    }

    public boolean dumpDatabase(Path dir, boolean compress)
    {
        String ver = Database.d.getVersion();
        if (ver.equals("unknown"))
            return false;

        String date = new SimpleDateFormat("yyyy-MM-dd-HH-mm").format(new Date());
        Path path = dir.resolve(String.format("date_%s#schema_%s.pgdump", date, ver));

        ProcessBuilder p = Exec.build(machineenv, "docker", "exec", name, "pg_dumpall", "-U", "postgres", "-c");
        p.redirectOutput(Redirect.appendTo(path.toFile()));
        int ret = Exec.execit(p, null);
        if ((ret == 0) && compress) {
            try {
                ZipOutputStream out = new ZipOutputStream(new FileOutputStream(path.toFile()+".zip"));
                out.putNextEntry(new ZipEntry(path.getFileName().toString()));
                FileInputStream in = new FileInputStream(path.toFile());

                out.write("UPDATE pg_database SET datallowconn = 'false' WHERE datname = 'scorekeeper';\n".getBytes());
                out.write("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'scorekeeper';\n".getBytes());
                IOUtils.copy(in, out);
                out.write("UPDATE pg_database SET datallowconn = 'true' WHERE datname = 'scorekeeper';\n".getBytes());

                IOUtils.closeQuietly(out);
                IOUtils.closeQuietly(in);
                Files.deleteIfExists(path);
            } catch (Exception ioe) {
                log.log(Level.INFO, "Unable to compress database backup: " + ioe, ioe);
            }
        }
        return ret == 0;
    }

    public boolean importDatabase(Path path)
    {
        String tmp = "/tmp/"+path.getFileName().toString();
        if (Exec.execit(Exec.build(machineenv, "docker", "cp", path.toString(), name+":"+tmp), null) != 0)
            return false;
        ProcessBuilder p = Exec.build(machineenv, "docker", "exec", name, "ash", "-c", "unzip -p "+tmp+" | psql -U postgres");
        p.redirectOutput(Redirect.appendTo(Prefs.getLogDirectory().resolve("import.log").toFile()));
        if (Exec.execit(p, null) != 0)
            return false;
        return Exec.execit(Exec.build(machineenv, "docker", "exec", name, "/dbconversion-scripts/upgrade.sh", "/dbconversion-scripts"), null) == 0;
    }

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
        synchronized (DockerContainer.class) {
            Set<String> dead = new HashSet<String>(tosearch);
            byte buf[] = new byte[4096];
            if (Exec.execit(Exec.build(machineenv, "docker", "ps", "--format", "{{.Names}}"), buf, 3000) != 0)
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

    /**
     * Quicker to send the stop signal all at once and let docker do the graceful
     * wait followed by a hard kill.  Also call rm to remove if they weren't
     * started with --rm.
     * @param containers the list of containers to stop
     */
    public static void stopAll(Collection<DockerContainer> containers)
    {
        List<String> cmd = new ArrayList<String>(Arrays.asList("docker", "stop"));
        Map<String, String> env = null;
        for (DockerContainer c : containers) {
            cmd.add(c.getName());
            env = c.machineenv;
        }

        Exec.execit(Exec.build(env, cmd), null);
        cmd.set(1, "rm");
        Exec.execit(Exec.build(env, cmd), null, true); // most likely not there, ignore errors
    }

    /**
     * Static method to forcefully clear out old network and then create a new one.  The hope is that
     * this will catch the issue of phantom container network connections from getting in our way.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void establishNetwork(Map<String, String> machineenv)
    {
        byte result[] = new byte[8192];

        try {
            if (Exec.execit(Exec.build(machineenv, "docker", "network", "inspect", NET_NAME), result) == 0) {
                for (String cid : ((Map<String,Object>)((Map)objectMapper.readValue(result, List.class).get(0)).get("Containers")).keySet()) {
                    Exec.execit(Exec.build(machineenv, "docker", "network", "disconnect", "-f", NET_NAME, cid), null);
                }

                Exec.execit(Exec.build(machineenv, "docker", "network", "rm", NET_NAME), null);
            }
        } catch (IOException ioe) {
            log.log(Level.WARNING, "Error tearing down old network: " + ioe, ioe);
        }

        Exec.execit(Exec.build(machineenv, "docker", "network", "create", NET_NAME), null);
    }
}