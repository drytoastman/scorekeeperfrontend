/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.wwscc.dialogs.StatusDialog;
import org.wwscc.storage.Database;
import org.wwscc.system.docker.DockerAPI;
import org.wwscc.system.docker.DockerContainer;
import org.wwscc.util.BroadcastState;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;

/**
 * Thread to keep checking our services for status.  It pauses for 5 seconds but can
 * be woken by anyone calling poke.  Also provides the interface to any of the docker
 * container calls.
 */
public class ContainerMonitor extends MonitorBase
{
    private static final Logger log = Logger.getLogger(ContainerMonitor.class.getName());

    public static final String NET_NAME     = "scnet";
    public static final String DBV_PREFIX   = "scdatabase-";
    public static final String LOGV_PREFIX  = "sclogs-";
    public static final String SOCKV_PREFIX = "scsocket";

    private DockerAPI docker;
    private List<DockerContainer> all, nondb;
    private DockerContainer db, web, sync;
    private BroadcastState<String> status;
    private BroadcastState<Boolean> ready;
    private Path toimport;
    private boolean dobackup, machineready, lastcheck, restartsync;

    /** flag for debug environment where we are running the backend separately */
    private boolean external_backend;

    @SuppressWarnings("unchecked")
    public ContainerMonitor()
    {
        super("ContainerMonitor", 5000);
        docker       = new DockerAPI();
        all          = new ArrayList<DockerContainer>();
        nondb        = new ArrayList<DockerContainer>();
        toimport     = null;
        dobackup     = false;
        status       = new BroadcastState<String>(MT.BACKEND_STATUS, "");
        ready        = new BroadcastState<Boolean>(MT.BACKEND_READY, false);
        machineready = false;
        lastcheck    = false;

        db = new DockerContainer("db", "drytoastman/scdb:"+Prefs.getVersion(), NET_NAME);
        db.addVolume(DBV_PREFIX+Prefs.getVersion(), "/var/lib/postgresql/data");
        db.addVolume(LOGV_PREFIX+Prefs.getVersion(), "/var/log");
        db.addVolume(SOCKV_PREFIX, "/var/run/postgresql");
        db.addPort("127.0.0.1", 6432, 6432);
        db.addPort("0.0.0.0", 54329, 5432);
        all.add(db);

        web = new DockerContainer("web", "drytoastman/scweb:"+Prefs.getVersion(), NET_NAME);
        web.addVolume(LOGV_PREFIX+Prefs.getVersion(), "/var/log");
        web.addVolume(SOCKV_PREFIX, "/var/run/postgresql");
        web.addPort("0.0.0.0", 80, 80);
        all.add(web);
        nondb.add(web);

        sync = new DockerContainer("sync", "drytoastman/scsync:"+Prefs.getVersion(), NET_NAME);
        sync.addVolume(LOGV_PREFIX+Prefs.getVersion(), "/var/log");
        sync.addVolume(SOCKV_PREFIX, "/var/run/postgresql");
        all.add(sync);
        nondb.add(sync);

        Messenger.register(MT.POKE_SYNC_SERVER, (m, o) -> docker.poke(sync) );
        Messenger.register(MT.NETWORK_CHANGED,  (m, o) -> { restartsync  = true;       poke(); });
        Messenger.register(MT.MACHINE_READY,    (m, o) -> { machineready = (boolean)o; poke(); });
        Messenger.register(MT.DOCKER_ENV,       (m, o) -> docker.setup((Map<String,String>)o) );

        external_backend = System.getenv("EXTERNAL_BACKEND") != null;
    }

    public boolean minit()
    {
        status.set("Waiting for VM");
        while (!done && !machineready)
            donefornow();

        status.set("Waiting for Docker API");
        while (!done && !docker.isReady())
            donefornow();

        if (!external_backend) {
            status.set( "Clearing old containers");
            docker.teardown(all);

            status.set( "Establishing Network");
            while (!done && !docker.networkUp(NET_NAME))
                donefornow();

            status.set( "Creating containers");
            while (!done && !docker.containersUp(all))
                donefornow();
        }

        return true;
    }

    public void mloop() throws IOException
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

        if (dobackup) {
            doBackup();
        }

        if (restartsync && ready.get()) {
            docker.restart(sync);
            restartsync = false;
        }

        // If something isn't running, try and start them now
        docker.loadState(all);
        List<DockerContainer> down = all.stream().filter(c -> !c.isUp()).collect(Collectors.toList());
        if (down.size() > 0) {
            ok = false;
            if (external_backend) {
                status.set("Down");
            } else {
                status.set("Restarting " + down.stream().map(e -> e.getName()).collect(Collectors.joining(",")));
                if (!docker.containersUp(down)) {
                    log.severe("Error during call to up."); // don't send to dialog, noisy
                } else {
                    quickrecheck = true;
                }
            }
        }

        if (ok)
        {
            if (ok != lastcheck)
            {
                status.set("Waiting for Database");
                while (!done && !Database.testUp())
                    donefornow();
                if (done) return;
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
            docker.teardown(all);
        }
        ready.set(false);
        status.set("Done");
    }

    public DockerAPI getDockerAPI()
    {
        return docker;
    }

    private void doImport()
    {
        StatusDialog dialog = new StatusDialog();
        dialog.doDialog("Old Data Import", o -> {});
        dialog.setStatus("Preparing to import ...", -1);
        status.set("Preparing to import");

        docker.stop(nondb);
        Database.d.close();

        dialog.setStatus("Importing ...", -1);
        status.set("Importing ...");
        log.info("importing "  + toimport);

        String name = "db";
        boolean success = false;
        try {
            docker.uploadFile(name, toimport.toFile(), "/tmp");
            if (docker.exec(name, "ash", "-c", "unzip -p /tmp/"+toimport.getFileName()+" | psql -U postgres &> /tmp/import.log") == 0) {
                success = docker.exec(name, "ash", "-c", "/dbconversion-scripts/upgrade.sh /dbconversion-scripts >> /tmp/import.log 2>&1") == 0;
            }
            docker.downloadTo(name, "/tmp/import.log", Prefs.getLogDirectory());
        } catch (Exception e) {
            log.log(Level.WARNING, "Failure in restoring backup: " + e, e);
        }

        if (success)
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

        if (backup(Prefs.getBackupDirectory(), true)) {
            dialog.setStatus("Backup Complete", 100);
        } else {
            dialog.setStatus("Backup failed, see logs", 100);
        }

        dobackup = false;
    }

    public boolean backup(Path dir, boolean compress)
    {
        status.set("Backing up database");
        String ver = Database.d.getVersion();
        if (ver.equals("unknown"))
            return false;

        String name = "db";
        String date = new SimpleDateFormat("yyyy-MM-dd-HH-mm").format(new Date());
        Path path   = dir.resolve(String.format("date_%s#schema_%s.pgdump", date, ver));

        try {
            if (docker.exec(name, "pg_dumpall", "-U", "postgres", "-c", "-f", "/tmp/dump") != 0)
                throw new IOException("pg_dump failed");

            File tempdir = FileUtils.getTempDirectory();
            File tempfile = new File(tempdir, "dump");
            docker.downloadTo(name, "/tmp/dump", tempdir.toPath());

            OutputStream out;
            if (compress) {
                File zipname = path.resolveSibling(path.getFileName() + ".zip").toFile();
                out = new ZipArchiveOutputStream(zipname);
                ((ZipArchiveOutputStream)out).putArchiveEntry(new ZipArchiveEntry(path.getFileName().toString()));
            } else {
                out = new FileOutputStream(path.toFile());
            }

            out.write("UPDATE pg_database SET datallowconn = 'false' WHERE datname = 'scorekeeper';\n".getBytes());
            out.write("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'scorekeeper';\n".getBytes());
            FileUtils.copyFile(tempfile, out);
            out.write("UPDATE pg_database SET datallowconn = 'true' WHERE datname = 'scorekeeper';\n".getBytes());

            if (compress)
                ((ZipArchiveOutputStream)out).closeArchiveEntry();
            out.close();

            tempfile.delete();
            return true;

        } catch (Exception e) {
            log.log(Level.WARNING, "Unabled to dump database: " + e, e);
            return false;
        } finally {
            poke();
        }
    }

    public boolean copyLogs(Path dir)
    {
        try {
            docker.downloadTo("db", "/var/log/", dir);
            return true;
        } catch (IOException ioe) {
            log.warning("Failed to copy log files from container: " + ioe);
            return false;
        }
    }

    public void backupRequest()
    {
        dobackup = true;
        poke();
    }

    public void importRequest(Path backupfile)
    {
        toimport = backupfile;
        poke();
    }
}

