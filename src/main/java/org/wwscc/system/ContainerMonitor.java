/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
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

    public static final String NET_NAME        = "scnet";
    public static final String DB_IMAGE_NAME   = "drytoastman/scdb:"+Prefs.getFullVersion();
    public static final String PY_IMAGE_NAME   = "drytoastman/scpython:"+Prefs.getFullVersion();
    public static final String SOCK_VOL_NAME   = "scsocket";
    public static final String DB_VOL_NAME     = "scdatabase-"+Prefs.getVersionBase();
    public static final String LOG_VOL_NAME    = "sclogs-"+Prefs.getVersionBase();

    private DockerAPI docker;
    private List<DockerContainer> all, nondb;
    private DockerContainer db, web, sync;
    private BroadcastState<String> status;
    private BroadcastState<Boolean> ready;
    private Path importRequestFile;
    private BackupRequest backupRequest;
    private boolean machineready, lastcheck, restartsync;

    /** flag for debug environment where we are running the backend separately */
    private boolean external_backend;

    class BackupRequest
    {
        Path directory;
        boolean compress;
        boolean usedialog;
    }

    @SuppressWarnings("unchecked")
    public ContainerMonitor()
    {
        super("ContainerMonitor", 5000);
        docker       = new DockerAPI();
        all          = new ArrayList<DockerContainer>();
        nondb        = new ArrayList<DockerContainer>();
        status       = new BroadcastState<String>(MT.BACKEND_STATUS, "");
        ready        = new BroadcastState<Boolean>(MT.BACKEND_READY, false);
        machineready = false;
        lastcheck    = false;
        backupRequest = null;
        importRequestFile = null;

        db = new DockerContainer("db", DB_IMAGE_NAME, NET_NAME);
        db.addVolume(DB_VOL_NAME,   "/var/lib/postgresql/data");
        db.addVolume(LOG_VOL_NAME,  "/var/log");
        db.addVolume(SOCK_VOL_NAME, "/var/run/postgresql");
        db.addPort("127.0.0.1", 6432, 6432);
        db.addPort("0.0.0.0",  54329, 5432);
        all.add(db);

        web = new DockerContainer("web", PY_IMAGE_NAME, NET_NAME);
        web.addCmdItem("webserver.py");
        web.addVolume(LOG_VOL_NAME,  "/var/log");
        web.addVolume(SOCK_VOL_NAME, "/var/run/postgresql");
        web.addPort("0.0.0.0", 80, 80);
        all.add(web);
        nondb.add(web);

        sync = new DockerContainer("sync", PY_IMAGE_NAME, NET_NAME);
        sync.addCmdItem("syncserver");
        sync.addVolume(LOG_VOL_NAME,  "/var/log");
        sync.addVolume(SOCK_VOL_NAME, "/var/run/postgresql");
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
            docker.teardown(all, null);

            status.set( "Establishing Network");
            while (!done && !docker.networkUp(NET_NAME))
                donefornow();

            status.set( "Creating containers");
            while (!done && !docker.containersUp(all, (c, t) -> { status.set(String.format("Creation Step %s of %s", c, t)); }))
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
        if (importRequestFile != null) {
            ready.set(false);
            doImport(importRequestFile);
            importRequestFile = null;
            lastcheck = false;
        }

        if (backupRequest != null) {
            doBackup(backupRequest);
            backupRequest = null;
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
                if (!docker.containersUp(down, null)) {
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
                    donefornow(1000);
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
        BackupRequest request = new BackupRequest();
        request.directory = Prefs.getBackupDirectory();
        request.compress  = true;
        request.usedialog = false;
        doBackup(request);
        Database.d.close();

        status.set("Shutting down ...");
        if (!external_backend) {
            docker.teardown(all, (c, t) -> { status.set(String.format("Shutdown Step %d of %d", c, t)); });
        }
        ready.set(false);
        status.set("Done");
    }

    public DockerAPI getDockerAPI()
    {
        return docker;
    }


    private void doImport(Path importfile)
    {
        StatusDialog dialog = new StatusDialog();
        dialog.doDialog("Old Data Import", o -> {});
        dialog.setStatus("Preparing to import ...", -1);
        status.set("Preparing to import");

        try {
            importfile = ImportExportFunctions.extractSql(importfile);
            ImportExportFunctions.checkSchema(importfile);
        } catch (IOException ioe) {
            dialog.setStatus("File Error: " + ioe, 100);
            return;
        }

        docker.stop(nondb, null);
        Database.d.close();

        dialog.setStatus("Importing ...", -1);
        status.set("Importing ...");
        log.info("importing "  + importfile);

        String name = "db";
        boolean success = false;
        try {
            docker.uploadFile(name, importfile, "/tmp");
            if (docker.exec(name, "ash", "-c", "psql -U postgres -f /tmp/"+importfile.getFileName()+" &> /tmp/import.log") == 0) {
                success = docker.exec(name, "ash", "-c", "/dbconversion-scripts/upgrade.sh /dbconversion-scripts >> /tmp/import.log 2>&1") == 0;
            }
            docker.downloadTo(name, "/tmp/import.log", Prefs.getLogDirectory());
        } catch (Exception e) {
            log.log(Level.WARNING, "Failure in restoring from backup: " + e, e);
        }

        if (success)
            dialog.setStatus("Import and conversion was successful", 100);
        else
            dialog.setStatus("Import failed, see logs", 100);
    }


    private void doBackup(BackupRequest request)
    {
        StatusDialog dialog = null;
        if (request.usedialog) {
            dialog = new StatusDialog();
            dialog.doDialog("Backing Up Database", o -> {});
            dialog.setStatus("Backing up database ...", -1);
        }

        status.set("Backing up database");
        try {
            Path finalpath = ImportExportFunctions.backupName(request.directory);
            if (docker.exec("db", "pg_dumpall", "-U", "postgres", "-c", "-f", "/tmp/dump") != 0)
                throw new IOException("pg_dump failed");

            Path dumpfile = Files.createTempDirectory("backupwork").resolve("dump");
            docker.downloadTo("db", "/tmp/dump", dumpfile);

            ImportExportFunctions.processBackup(dumpfile, finalpath, request.compress);
            Files.delete(dumpfile);

            if (request.usedialog)
                dialog.setStatus("Backup Complete", 100);

        } catch (Exception e) {
            log.log(Level.WARNING, "Unabled to dump database: " + e, e);
            if (request.usedialog)
                dialog.setStatus("Backup failed, see logs", 100);

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
        backupRequest(Prefs.getBackupDirectory(), true, true, false);
    }

    public void backupRequest(Path backupdir, boolean compress, boolean usedialog, boolean wait)
    {
        BackupRequest request = new BackupRequest();
        request.directory = backupdir;
        request.compress  = compress;
        request.usedialog = usedialog;
        backupRequest = request;
        poke();
        if (wait) {
            while (backupRequest != null) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    public void importRequest(Path importfile)
    {
        importRequestFile = importfile;
        poke();
    }
}

