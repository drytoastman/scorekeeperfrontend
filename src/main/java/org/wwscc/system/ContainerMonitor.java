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
import org.wwscc.system.docker.DockerAPI.DemuxedStreams;
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

    public static final String NET_NAME  = volname("net1");
    public static final String CERTS_VOL = "certs";
    public static final String DB_IMAGE  = "drytoastman/scdb:"+Prefs.getFullVersion();
    public static final String PY_IMAGE  = "drytoastman/scpython:"+Prefs.getFullVersion();

    private DockerAPI docker;
    private List<DockerContainer> all, nondb;
    private DockerContainer db, web, sync, dns;
    private BroadcastState<String> status;
    private BroadcastState<String> containers;
    private Path importRequestFile, certsFile;
    private BackupRequest backupRequest;
    private boolean machineready, restartsync;
    private String lastcheck;

    /** flag for debug environment where we are running the backend separately */
    private boolean external_backend;

    class BackupRequest
    {
        Path directory;
        boolean compress;
        boolean usedialog;
    }

    public static String volname(String name) { return String.format("%s_%s", Prefs.getVersionBase(), name); }
    public static String conname(String name) { return String.format("%s_%s_1", Prefs.getVersionBase(), name); }

    @SuppressWarnings("unchecked")
    public ContainerMonitor(Path certsfile)
    {
        super("ContainerMonitor", 5000);
        docker       = new DockerAPI();
        all          = new ArrayList<DockerContainer>();
        nondb        = new ArrayList<DockerContainer>();
        status       = new BroadcastState<String>(MT.BACKEND_STATUS, "");
        containers   = new BroadcastState<String>(MT.BACKEND_CONTAINERS, "");
        machineready = false;
        lastcheck    = "";
        backupRequest = null;
        importRequestFile = null;
        certsFile     = certsfile;

        db = new DockerContainer(conname("db"), DB_IMAGE, NET_NAME);
        db.addVolume(volname("database"),  "/var/lib/postgresql/data");
        db.addVolume(volname("logs"),      "/var/log");
        db.addVolume(volname("socket"),    "/var/run/postgresql");
        db.addVolume(CERTS_VOL, "/certs");
        db.addPort("127.0.0.1", 6432, 6432, "tcp");
        db.addPort("0.0.0.0",  54329, 5432, "tcp");
        all.add(db);

        web = new DockerContainer(conname("web"), PY_IMAGE, NET_NAME);
        web.addCmdItem("webserver.py");
        web.addVolume(volname("logs"),  "/var/log");
        web.addVolume(volname("socket"), "/var/run/postgresql");
        web.addPort("0.0.0.0", 80, 80, "tcp");
        web.addEnvItem("FLASK_SECRET='"+Prefs.getCookieSecret()+"'");
        all.add(web);
        nondb.add(web);

        sync = new DockerContainer(conname("sync"), PY_IMAGE, NET_NAME);
        sync.addCmdItem("syncserver");
        sync.addVolume(volname("logs"),  "/var/log");
        sync.addVolume(volname("socket"), "/var/run/postgresql");
        sync.addVolume(CERTS_VOL, "/certs");
        all.add(sync);
        nondb.add(sync);

        dns = new DockerContainer(conname("dns"), PY_IMAGE, NET_NAME);
        dns.addCmdItem("dnsserver");
        dns.addVolume(volname("logs"),  "/var/log");
        dns.addVolume(volname("socket"), "/var/run/postgresql");
        dns.addPort("0.0.0.0", 53, 53, "udp");
        all.add(dns);
        nondb.add(dns);

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

        if (certsFile != null) {
            try {
                docker.loadVolume(CERTS_VOL, certsFile, (c, t) -> { status.set(String.format("Certs Step %s of %s", c, t)); });
            } catch (IOException ioe) {
                log.log(Level.SEVERE, "Failed to load certs file: " + ioe, ioe);
                return false;
            }
        }

        if (!external_backend) {
            status.set( "Clearing old containers");
            docker.teardown(all, null);

            status.set( "Establishing Network");
            long starttime = System.currentTimeMillis();
            boolean warned = false;
            while (!done && !docker.networkUp(NET_NAME)) {
                if (!warned && System.currentTimeMillis() > starttime + 5000) {
                    Messenger.sendEvent(MT.DOCKER_NOT_OK, null);
                    warned = true;
                }
                donefornow();
            }
            Messenger.sendEvent(MT.DOCKER_OK, null);

            status.set( "Creating containers");
            while (!done) {
                docker.containersUp(all, (c, t) -> { status.set(String.format("Creation Step %s of %s", c, t)); });
                try { docker.loadState(all); } catch (IOException ioe) {}

                if (db.isUp())  // only need db to run applications
                    break;

                donefornow();
            }
        }

        return true;
    }

    public void mloop() throws IOException
    {
        if (!machineready) {
            status.set("Waiting for VM");
            containers.set("");
            lastcheck = "";
            return;
        }

        // interrupt our regular schedule to shutdown and import data or backup
        if (importRequestFile != null) {
            containers.set("");
            doImport(importRequestFile);
            importRequestFile = null;
            lastcheck = "";
        }

        if (backupRequest != null) {
            doBackup(backupRequest);
            backupRequest = null;
        }

        if (restartsync && sync.isUp()) {
            docker.restart(sync);
            restartsync = false;
        }

        // If something isn't running, try and start them now
        docker.loadState(all);
        List<DockerContainer> down = all.stream().filter(c -> !c.isUp()).collect(Collectors.toList());
        if (down.size() > 0) {
            if (external_backend) {
                status.set("Down");
            } else {
                if (!docker.containersUp(down, null)) {
                    log.severe("Error during call to up."); // don't send to dialog, noisy
                } else {
                    quickrecheck = true;
                }
            }
        }

        if (!lastcheck.contains("db") && db.isUp()) {
            status.set("Waiting for Database");
            while (!done && !Database.testUp()) {
                donefornow(1000);
                docker.loadState(all);
                if (!db.isUp())  // database container is down, restart the loop and try again
                    return;
            }
            if (done) return;
            Database.openPublic(true, 5000);
            Messenger.sendEvent(MT.DATABASE_NOTIFICATION, new HashSet<String>(Arrays.asList("mergeservers")));
        }

        containers.set(all.stream().filter(c -> c.isUp()).map(c -> c.shortName()).collect(Collectors.joining(",")));
        if (down.size() == 0)
            status.set("Running");
        else
            status.set(down.stream().map(e -> e.shortName()).collect(Collectors.joining(",")));

        lastcheck = containers.get();
    }

    public void mshutdown()
    {
        if (!external_backend) {
            BackupRequest request = new BackupRequest();
            request.directory = Prefs.getBackupDirectory();
            request.compress  = true;
            request.usedialog = false;
            doBackup(request);
        }

        Database.d.close();

        status.set("Shutting down ...");
        if (!external_backend) {
            docker.teardown(all, (c, t) -> { status.set(String.format("Shutdown Step %d of %d", c, t)); });
        }
        containers.set("");
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

        String name = db.getName();
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
            if (docker.exec(db.getName(), "pg_dumpall", "-U", "postgres", "-c", "-f", "/tmp/dump") != 0)
                throw new IOException("pg_dump failed");

            Path dumpdir = Files.createTempDirectory("backupwork");
            Path dumpfile = dumpdir.resolve("dump");
            docker.downloadTo(db.getName(), "/tmp/dump", dumpdir);

            ImportExportFunctions.processBackup(dumpfile, finalpath, request.compress);
            Files.delete(dumpfile);
            Files.delete(dumpdir);

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
            docker.downloadTo(db.getName(), "/var/log/", dir);
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
        if (wait) { while (backupRequest != null) { try { Thread.sleep(200); } catch (InterruptedException e) { break;}}}
    }

    public void importRequest(Path importfile)
    {
        importRequestFile = importfile;
        poke();
    }

    public String syncCommand(String ... cmd) throws IOException
    {
        // a single wait to see if it comes up, otherwise just fail
        if (!sync.isUp()) { try { Thread.sleep(1000); } catch (InterruptedException e) {}}

        DemuxedStreams ret = docker.run(sync.getName(), cmd);
        if (ret.stderr.length() > 0) {
            log.log(Level.WARNING, "syncCommand stderr is {0}", ret.stderr);
            if (ret.stdout.length() == 0) {
                if (ret.stderr.contains("Traceback")) {
                    String err[] = ret.stderr.split("\n");
                    throw new IOException(err[err.length-1]);
                }
                throw new IOException(ret.stderr);
            }
        }
        log.log(Level.FINER, "syncCommand stdout is {0}", ret.stdout);
        return ret.stdout;
    }
}