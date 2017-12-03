package org.wwscc.tray;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.TimeZone;
import java.util.logging.Logger;

import org.apache.commons.lang3.SystemUtils;
import org.wwscc.util.Exec;

/**
 * Interface for calling docker machine functions
 */
public class DockerMachine
{
    private static final Logger log = Logger.getLogger(DockerMachine.class.getName());

    /**
     * Sets and returns the environment variables used by docker-compose if docker-machine is present
     * @return a map of the environment variables that were set
     */
    public static Map<String,String> machineenv()
    {
        Map<String,String> dockerenv = new HashMap<String, String>();
        byte buf[] = new byte[4096];
        if (Exec.execit(Exec.build(null, "docker-machine", "env", "--shell", "cmd"), buf) != 0) {
            log.severe(new String(buf).trim());
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
     * @return true if docker-machine and vboxmanage are installed (VirtualBox comes with Docker ToolBox)
     */
    public static boolean machinepresent()
    {
        if (Exec.testit(Exec.build(null, "docker-machine", "-h"))) {
            if (SystemUtils.IS_OS_WINDOWS) {
                return Exec.testit(Exec.build(null, "c:\\Program Files\\Oracle\\VirtualBox\\VBoxManage.exe", "-v"));
            } else {
                return Exec.testit(Exec.build(null, "vboxmanage", "-v"));
            }
        }
        return false;
    }

    /**
     * @return true if docker-machine has created a virtualbox node
     */
    public static boolean machinecreated()
    {
        byte buf[] = new byte[128];
        if (Exec.execit(Exec.build(null, "docker-machine", "ls", "-q", "--filter", "name=default"), buf) != 0) {
            return false;
        }
        try (Scanner scan = new Scanner(new String(buf).trim())) {
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
        return Exec.execit(Exec.build(null, "docker-machine", "create", "-d", "virtualbox", "default"), null) == 0;
    }

    /**
     * @return true if virtualbox node is running
     */
    public static boolean machinerunning()
    {
        return Exec.execit(Exec.build(null, "docker-machine", "ip"), null) == 0;
    }

    /**
     * Try to start virtual box node
     * @return true if command returns success
     */
    public static boolean startmachine()
    {
        return Exec.execit(Exec.build(null, "docker-machine", "start"), null) == 0;
    }

    /**
     * Try to set the date on docker VM to match that of local machine (important after sleep)
     * @return true if command returns success
     */
    public static boolean settime()
    {
        SimpleDateFormat fmt = new SimpleDateFormat("MMddHHmmyyyy.ss");
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return Exec.execit(Exec.build(null, "docker-machine", "ssh", "default", "sudo date -u " + fmt.format(new Date())), null) == 0;
    }

    /**
     * Try to stop virtual box node
     * @return true if command returns success
     */
    public static boolean stopmachine()
    {
        return Exec.execit(Exec.build(null, "docker-machine", "stop"), null) == 0;
    }
}
