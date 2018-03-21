/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system.monitors;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.wwscc.dialogs.StatusDialog;
import org.wwscc.storage.Database;
import org.wwscc.system.docker.DockerAPI;
import org.wwscc.system.docker.DockerContainer;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;

/**
 * Thread to keep checking our services for status.  It pauses for 5 seconds but can
 * be woken by anyone calling poke.  Also provides the interface to any of the docker
 * container calls.
 */
public class ContainerMonitor extends Monitor
{
    private static final Logger log = Logger.getLogger(ContainerMonitor.class.getName());

    private DockerAPI docker;
    private Map<String, DockerContainer> containers;
    private BroadcastState<String> status;
    private BroadcastState<Boolean> ready;
    private Set<String> names;
    private Path toimport;
    private Path tobackup;

    private Map<String, String> machineenv;
    private boolean machineready, lastcheck, restartsync;

    /** flag for debug environment where we are running the backend separately */
    private boolean external_backend;

    @SuppressWarnings("unchecked")
    public ContainerMonitor()
    {
        super("ContainerMonitor", 5000);
        docker     = new DockerAPI();
        containers = new HashMap<String, DockerContainer>();
        containers.put("db", new DockerContainer.Db());
        containers.put("web", new DockerContainer.Web());
        containers.put("sync", new DockerContainer.Sync());
        names = new HashSet<String>();
        for (DockerContainer c : containers.values())
            names.add(c.getName());
        toimport     = null;
        tobackup     = null;
        status       = new BroadcastState<String>(MT.BACKEND_STATUS, "");
        ready        = new BroadcastState<Boolean>(MT.BACKEND_READY, false);
        machineenv   = new HashMap<String, String>();
        machineready = false;
        lastcheck    = false;

        Messenger.register(MT.POKE_SYNC_SERVER, (m, o) -> containers.get("sync").poke());
        Messenger.register(MT.NETWORK_CHANGED,  (m, o) -> { restartsync  = true;       poke(); });
        Messenger.register(MT.MACHINE_READY,    (m, o) -> { machineready = (boolean)o; poke(); });
        Messenger.register(MT.MACHINE_ENV,      (m, o) -> { machineenv   = (Map<String,String>)o; env_change(); });

        external_backend = System.getenv("EXTERNAL_BACKEND") != null;
    }

    private void env_change()
    {
        try {
            docker.setup(machineenv);
        } catch (Exception e) {
            log.warning("Unable to setup docker connection at this time: " + e);
        }
    }

    public boolean minit()
    {
        status.set("Waiting for VM");
        while (!machineready)
            donefornow();

        for (DockerContainer c : containers.values())
            c.setMachineEnv(machineenv);
        docker.setup(machineenv);

        if (!external_backend) {
            status.set( "Clearing old containers");
            DockerContainer.stopAll(containers.values());

            status.set( "Establishing Network");
            docker.resetNetwork(DockerContainer.NET_NAME);

            status.set( "Creating new containers");
            for (DockerContainer c : containers.values()) {
                status.set("Init " + c.getName());
                c.createVolumes();
                c.start();
            }
        }

        return true;
    }

    public void mloop()
    {
        boolean ok = true;

        if (!machineready) {
            status.set("Waiting for VM");
            ready.set(false);
            lastcheck = false;
            return;
        }

        // interrupt our regular schedule to shutdown and import data
        if (toimport != null) {
            ready.set(false);
            doImport();
            lastcheck = false;
        }

        if (tobackup != null) {
            doBackup();
        }

        if (restartsync && ready.get()) {
            containers.get("sync").restart();
            restartsync = false;
        }

        // If something isn't running, try and start them now
        Set<String> dead = DockerContainer.finddown(machineenv, names);
        if (dead.size() > 0) {
            ok = false;
            if (external_backend) {
                status.set("Down");
            } else {
                status.set("Restarting " + dead);
                for (DockerContainer c : containers.values()) {
                    if (dead.contains(c.getName())) {
                        if (!c.start(5000)) {
                            log.severe("Unable to start " + c.getName()); // don't send to dialog, noisy
                        } else {
                            quickrecheck = true;
                        }
                    }
                }
            }
        }

        if (ok)
        {
            if (ok != lastcheck)
            {
                status.set("Waiting for Database");
                Database.waitUntilUp();
                Database.openPublic(true, 5000);
                Messenger.sendEvent(MT.DATABASE_NOTIFICATION, new HashSet<String>(Arrays.asList("mergeservers")));
            }
            status.set("Running");
            ready.set(true);
        }

        lastcheck = ok;
    }

    public void mshutdown()
    {
        status.set("Shutting down ...");
        if (!external_backend) {
            DockerContainer.stopAll(containers.values());
        }
        ready.set(false);
        status.set("Done");
    }

    private void doImport()
    {
        StatusDialog dialog = new StatusDialog();
        dialog.doDialog("Old Data Import", o -> {});
        dialog.setStatus("Preparing to import ...", -1);
        status.set("Preparing to import");

        List<DockerContainer> nondb = new ArrayList<DockerContainer>();
        nondb.add(containers.get("web"));
        nondb.add(containers.get("sync"));
        DockerContainer.stopAll(nondb);
        Database.d.close();

        dialog.setStatus("Importing ...", -1);
        status.set("Importing ...");
        log.info("importing "  + toimport);
        boolean success = containers.get("db").importDatabase(toimport);
        toimport = null;

        if (success)
            dialog.setStatus("Import and conversion was successful", 100);
        else
            dialog.setStatus("Import failed, see logs", 100);
    }

    private void doBackup()
    {
        StatusDialog dialog = new StatusDialog();
        dialog.doDialog("Backing Up Database", o -> {});
        dialog.setStatus("Backing up database ...", -1);

        if (backupNow(tobackup, true)) {
            dialog.setStatus("Backup Complete", 100);
        } else {
            dialog.setStatus("Backup failed, see import.log", 100);
        }

        tobackup = null;
    }

    public boolean backupNow(Path dir, boolean compress)
    {
        status.set("Backing up database");
        boolean ret = containers.get("db").dumpDatabase(dir, compress);
        poke(); // update status quicker
        return ret;
    }

    public boolean copyLogs(Path dir)
    {
        return containers.get("db").copyLogs(dir);
    }

    public void backupRequest(Path dir)
    {
        tobackup = dir;
        poke();
    }

    public void importRequest(Path p)
    {
        toimport = p;
        poke();
    }
}

