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
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

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

    public static final String NET_NAME        = "scnet";
    public static final String DB_IMAGE_NAME   = "drytoastman/scdb:"+Prefs.getFullVersion();
    public static final String WEB_IMAGE_NAME  = "drytoastman/scweb:"+Prefs.getFullVersion();
    public static final String SYNC_IMAGE_NAME = "drytoastman/scsync:"+Prefs.getFullVersion();
    public static final String SOCK_VOL_NAME   = "scsocket";
    public static final String DB_VOL_NAME     = "scdatabase-"+Prefs.getVersionBase();
    public static final String LOG_VOL_NAME    = "sclogs-"+Prefs.getVersionBase();

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

        db = new DockerContainer("db", DB_IMAGE_NAME, NET_NAME);
        db.addVolume(DB_VOL_NAME,   "/var/lib/postgresql/data");
        db.addVolume(LOG_VOL_NAME,  "/var/log");
        db.addVolume(SOCK_VOL_NAME, "/var/run/postgresql");
        db.addPort("127.0.0.1", 6432, 6432);
        db.addPort("0.0.0.0",  54329, 5432);
        all.add(db);

        web = new DockerContainer("web", WEB_IMAGE_NAME, NET_NAME);
        web.addVolume(LOG_VOL_NAME,  "/var/log");
        web.addVolume(SOCK_VOL_NAME, "/var/run/postgresql");
        web.addPort("0.0.0.0", 80, 80);
        all.add(web);
        nondb.add(web);

        sync = new DockerContainer("sync", SYNC_IMAGE_NAME, NET_NAME);
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

        /*
         *  Decompress the file if its a zip
         */
        if (toimport.toString().toLowerCase().endsWith(".zip")) {
            File temp;
    fileok: try (ZipFile zip = new ZipFile(toimport.toFile())) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    temp = File.createTempFile("open-zip", ".sql");
                    FileUtils.copyInputStreamToFile(zip.getInputStream(entry), temp);
                    break fileok;
                }
                throw new IOException("No sql file found in zip file");
            } catch (IOException ioe) {
                dialog.setStatus("Zip Error: " + ioe, 100);
                toimport = null;
                return;
            }

            toimport = temp.toPath();
        }

        if (!toimport.toString().toLowerCase().endsWith(".sql")) {
            dialog.setStatus("Data is not a sql file", 100);
            toimport = null;
            return;
        }

        /* Check the schema version:
         * looking for lines like the following for the version table:
         * COPY version (id, version, modified) FROM stdin;
         * 1 20180106 2017-11-17 05:32:37.805896
         */
fileok: try (Scanner scan = new Scanner(toimport)) {
            boolean mark = false;
            while (scan.hasNextLine()) {
                if (mark) {
                    scan.next(); // id
                    int schema = scan.nextInt();
                    if (schema < 20180000)
                        throw new IOException("Unable to import backups with schema earlier than 2018, selected file is " + schema);
                    break fileok;
                }
                String line = scan.nextLine();
                if (line.startsWith("COPY version"))
                    mark = true;
            }
            throw new IOException("No schema version found in file.");
        } catch (Exception e1) {
            dialog.setStatus("Error: " + e1, 100);
            toimport = null;
            return;
        }

        docker.stop(nondb);
        Database.d.close();

        dialog.setStatus("Importing ...", -1);
        status.set("Importing ...");
        log.info("importing "  + toimport);

        String name = "db";
        boolean success = false;
        try {
            docker.uploadFile(name, toimport.toFile(), "/tmp");
            if (docker.exec(name, "ash", "-c", "psql -U postgres -f /tmp/"+toimport.getFileName()+" &> /tmp/import.log") == 0) {
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
        String date = new SimpleDateFormat("yyyy-MM-dd-HH'h'mm'm'").format(new Date());
        Path path   = dir.resolve(String.format("%s(%s).pgdump", date, ver));

        try {
            if (docker.exec(name, "pg_dumpall", "-U", "postgres", "-c", "-f", "/tmp/dump") != 0)
                throw new IOException("pg_dump failed");

            File tempdir = FileUtils.getTempDirectory();
            File tempfile = new File(tempdir, "dump");
            docker.downloadTo(name, "/tmp/dump", tempdir.toPath());

            OutputStream out;
            if (compress) {
                File zipname = path.resolveSibling(path.getFileName() + ".zip").toFile();
                out = new ZipOutputStream(new FileOutputStream(zipname));
                ((ZipOutputStream)out).putNextEntry(new ZipEntry(path.getFileName().toString()));
            } else {
                out = new FileOutputStream(path.toFile());
            }

            out.write("UPDATE pg_database SET datallowconn = 'false' WHERE datname = 'scorekeeper';\n".getBytes());
            out.write("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'scorekeeper';\n".getBytes());
            FileUtils.copyFile(tempfile, out);
            out.write("UPDATE pg_database SET datallowconn = 'true' WHERE datname = 'scorekeeper';\n".getBytes());

            if (compress)
                ((ZipOutputStream)out).closeEntry();
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

