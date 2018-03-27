/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system.docker;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.TimeZone;
import org.apache.commons.lang3.SystemUtils;
import org.wwscc.util.BroadcastState;
import org.wwscc.util.Exec;

/**
 * Interface for calling docker machine functions
 */
public class DockerMachine
{
    public static final String MACHINE_NAME = "default";
    public enum MachineReturn { OK, REGEN_CERTS, ERROR };

    /**
     * @return true if docker-machine and vboxmanage are installed (VirtualBox comes with Docker ToolBox)
     */
    public static boolean machinepresent()
    {
        if (Exec.execit(Exec.build(null, "docker-machine", "-h"), true) == 0) {
            return vboxversion().length() > 3;
        }
        return false;
    }

    /**
     * @return the version information for vbox if installed
     */
    public static String vboxversion()
    {
        byte[] ver = new byte[128];
        if (SystemUtils.IS_OS_WINDOWS) {
            Exec.execit(Exec.build(null, "c:\\Program Files\\Oracle\\VirtualBox\\VBoxManage.exe", "-v"), ver, true);
        } else {
            Exec.execit(Exec.build(null, "vboxmanage", "-v"), ver, true);
        }
        return new String(ver).trim();
    }

    /**
     * @return true if docker-machine has created a virtualbox node
     */
    public static boolean machinecreated()
    {
        byte buf[] = new byte[128];
        if (Exec.execit(Exec.build(null, "docker-machine", "ls", "-q", "--filter", "name="+MACHINE_NAME), buf) != 0) {
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
        return Exec.execit(Exec.build(null, "docker-machine", "create", "-d", "virtualbox", MACHINE_NAME), null) == 0;
    }


    /**
     * Returns the environment variables used by docker-compose if docker-machine is present
     * @return a map of the environment variables that were set
     */
    public static MachineReturn machineenv(BroadcastState<Map<String, String>> env)
    {
        byte buf[] = new byte[4096];
        if (Exec.execit(Exec.build(null, "docker-machine", "env", "--shell", "cmd"), buf) == 0) {
            env.set(scanenv(new String(buf)));
            return MachineReturn.OK;
        }
        env.set(null);
        if (new String(buf).contains("regenerat")) {
            return MachineReturn.REGEN_CERTS;
        }
        return MachineReturn.ERROR;
    }

    protected static Map<String, String> scanenv(String buf)
    {
        HashMap<String, String> ret = new HashMap<String, String>();

        try (Scanner scan = new Scanner(buf))
        {
            while (scan.hasNext())
            {
                String set = scan.next();
                String var = scan.nextLine();
                if (set.equals("SET")) {
                    String p[] = var.split("=");
                    ret.put(p[0].trim(), p[1].trim());
                } else {
                    scan.nextLine();
                }
            }
        }
        catch (NoSuchElementException nse)
        {
        }

        return ret;
    }

    /**
     * @return true if virtualbox node is running
     */
    public static MachineReturn machinerunning()
    {
        byte buf[] = new byte[1024];
        if (Exec.execit(Exec.build(null, "docker-machine", "ip", MACHINE_NAME), buf) == 0)
            return MachineReturn.OK;
        if (new String(buf).contains("regenerat"))
            return MachineReturn.REGEN_CERTS;
        return MachineReturn.ERROR;
    }

    /**
     * Try to start virtual box node
     * @return true if command returns success
     */
    public static boolean startmachine()
    {
        return Exec.execit(Exec.build(null, "docker-machine", "start", MACHINE_NAME), null) == 0;
    }

    /**
     * Regenerate certs in response to errors.
     * @return true the command returns success
     */
    public static boolean regenerate_certs()
    {
        return Exec.execit(Exec.build(null, "docker-machine", "regenerate-certs", "-f", MACHINE_NAME), null) == 0;
    }

    /**
     * Try to set the date on docker VM to match that of local machine (important after sleep)
     * @return true if command returns success
     */
    public static boolean settime()
    {
        SimpleDateFormat fmt = new SimpleDateFormat("MMddHHmmyyyy.ss");
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return Exec.execit(Exec.build(null, "docker-machine", "ssh", MACHINE_NAME, "sudo date -u " + fmt.format(new Date())), null) == 0;
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
