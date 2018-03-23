/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system.monitors;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.wwscc.dialogs.StatusDialog;
import org.wwscc.storage.Database;
import org.wwscc.system.docker.DockerAPI;
import org.wwscc.system.docker.DockerContainer;
import org.wwscc.util.Exec;
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
        toimport     = null;
        tobackup     = null;
        status       = new BroadcastState<String>(MT.BACKEND_STATUS, "");
        ready        = new BroadcastState<Boolean>(MT.BACKEND_READY, false);
        machineenv   = new HashMap<String, String>();
        machineready = false;
        lastcheck    = false;

        Messenger.register(MT.POKE_SYNC_SERVER, (m, o) -> docker.poke("sync") );
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

        docker.setup(machineenv);

        if (!external_backend) {
            status.set( "Clearing old containers");
            docker.stop(containers.keySet());
            docker.rm(containers.keySet());

            status.set( "Establishing Network");
            docker.networkDelete(DockerContainer.NET_NAME);
            docker.networkCreate(DockerContainer.NET_NAME);

            status.set( "Creating new containers");
            for (DockerContainer c : containers.values()) {
                status.set("Create " + c.getName());
                c.up(docker);
            }
        }

        //Logger.getLogger("org.apache.http").setLevel(Level.ALL);
        //Logger.getLogger("org.apache.http.wire").setLevel(Level.ALL);
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
            docker.restart("sync");
            restartsync = false;
        }

        // If something isn't running, try and start them now
        Set<String> dead = new HashSet<String>(containers.keySet());
        dead.removeAll(docker.alive());
        if (dead.size() > 0) {
            ok = false;
            if (external_backend) {
                status.set("Down");
            } else {
                status.set("Restarting " + dead);
                for (String name : dead) {
                    if (!docker.start(name)) {
                        log.severe("Unable to start " + name); // don't send to dialog, noisy
                    } else {
                        //quickrecheck = true;
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
            docker.stop(containers.keySet());
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

        docker.stop("web", "sync");
        Database.d.close();

        dialog.setStatus("Importing ...", -1);
        status.set("Importing ...");
        log.info("importing "  + toimport);

        if (containers.get("db").restoreBackup(docker, toimport))
            dialog.setStatus("Import and conversion was successful", 100);
        else
            dialog.setStatus("Import failed, see logs", 100);
        toimport = null;
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

        String ver = Database.d.getVersion();
        if (ver.equals("unknown"))
            return false;

        String date = new SimpleDateFormat("yyyy-MM-dd-HH-mm").format(new Date());
        Path path = dir.resolve(String.format("date_%s#schema_%s.pgdump", date, ver));

        int ret = Exec.execit(Exec.build(machineenv, "docker", "exec", "db", "pg_dumpall", "-U", "postgres", "-c", "-f", "/tmp/dump"), null);

        //p.redirectOutput(Redirect.appendTo(path.toFile()));
        if ((ret == 0) && compress) {
            try {
                ZipOutputStream out = new ZipOutputStream(new FileOutputStream(path.toFile()+".zip"));
                out.putNextEntry(new ZipEntry(path.getFileName().toString()));
                FileInputStream in = new FileInputStream(path.toFile());

                out.write("UPDATE pg_database SET datallowconn = 'false' WHERE datname = 'scorekeeper';\n".getBytes());
                out.write("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'scorekeeper';\n".getBytes());
                IOUtils.copy(in, out);
                out.write("UPDATE pg_database SET datallowconn = 'true' WHERE datname = 'scorekeeper';\n".getBytes());

                try { out.close(); } catch (IOException ioe) {}
                try { in.close(); } catch (IOException ioe) {}
                Files.deleteIfExists(path);
            } catch (Exception ioe) {
                log.log(Level.INFO, "Unable to compress database backup: " + ioe, ioe);
            }
        }
        poke();
        return ret == 0;
    }

    public boolean copyLogs(Path dir)
    {
        return Exec.execit(Exec.build(machineenv, "docker", "cp", "db:/var/log", dir.toString()), null) == 0;
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

