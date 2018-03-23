/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system.docker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.wwscc.system.docker.models.CreateContainerConfig;
import org.wwscc.system.docker.models.HostConfig;
import org.wwscc.system.docker.models.ImageSummary;
import org.wwscc.system.docker.models.PortMap;
import org.wwscc.util.Prefs;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Wraps the docker configuration details as well as more complex interactions that are really
 * outside of the Docker API itself, things specific to scorekeeper
 */
public class DockerContainer
{
    private static final Logger log = Logger.getLogger(DockerContainer.class.getCanonicalName());

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
            addPort("127.0.0.1", 6432, 6432);
            addPort("0.0.0.0", 54329, 5432);
        }
    }

    public static class Web extends DockerContainer {
        public Web() {
            super("drytoastman/scweb", "web");
            addVolume(LOGV_PREFIX+Prefs.getVersion(), "/var/log");
            addVolume(SOCKV_PREFIX, "/var/run/postgresql");
            addPort("0.0.0.0", 80, 80);
        }
    }

    public static class Sync extends DockerContainer {
        public Sync() {
            super("drytoastman/scsync", "sync");
            addVolume(LOGV_PREFIX+Prefs.getVersion(), "/var/log");
            addVolume(SOCKV_PREFIX, "/var/run/postgresql");
        }
    }

    CreateContainerConfig config;
    String name;

    public DockerContainer(String image, String name)
    {
        this.name = name;

        config = new CreateContainerConfig();
        config.addEnvItem("UI_TIME_ZONE="+TimeZone.getDefault().getID());
        config.addEnvItem("SECRET='"+Prefs.getCookieSecret()+"'");
        config.addEnvItem("LOG_LEVEL="+Prefs.getLogLevel().getPythonLevel());
        config.setHostConfig(new HostConfig().autoRemove(false).portBindings(new PortMap()).binds(new ArrayList<>()));
        config.setExposedPorts(new HashMap<String, Object>());

        config.setNetwork(NET_NAME);
        config.setImage(image.contains(":") ? image : image+":"+Prefs.getVersion());
        config.setAttachStderr(false);
        config.setAttachStdout(false);
        config.setAttachStdin(false);
    }

    public void addPort(String hostip, int hostport, int containerport)
    {
        String to = containerport+"/tcp";
        HashMap<String, String> h = new HashMap<String, String>();
        h.put("HostIP", hostip);
        h.put("HostPort", ""+hostport);
        config.getHostConfig().getPortBindings().put(to, Arrays.asList(new Object[] { h }));
        config.getExposedPorts().put(to, new HashMap<Object,Object>());
    }

    public void addVolume(String vol, String path)
    {
        config.getHostConfig().addBindsItem(vol+":"+path);
    }

    public void up(DockerAPI docker)
    {
        if (Prefs.isDebug())
            config.addEnvItem("DEBUG=1");
        else
            config.getEnv().remove("DEBUG=1");

        boolean needpull = true;
        for (ImageSummary s : docker.images()) {
            if (s.getRepoTags().contains(config.getImage())) {
                needpull = false;
                break;
            }
        }

        if (needpull)
            docker.pull(config.getImage());
        for (String mount : config.getHostConfig().getBinds())
            docker.volumeCreate(mount.split(":")[0]);
        docker.create(name, config);
        docker.start(name);
    }

    public boolean restoreBackup(DockerAPI docker, Path toimport)
    {
        boolean success = false;
        try {
            if (docker.uploadFile(name, toimport.toFile(), "/tmp")) {
                if (docker.exec(name, "ash", "-c", "unzip -p /tmp/"+toimport.getFileName()+" | psql -U postgres &> /tmp/import.log") == 0) {
                    success = docker.exec(name, "ash", "-c", "/dbconversion-scripts/upgrade.sh /dbconversion-scripts >> /tmp/import.log 2>&1") == 0;
                }
                docker.downloadTo(name, "/tmp/import.log", Prefs.getLogDirectory());
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Failure in restoring backup: " + e, e);
        }
        return success;
    }


    public boolean dumpDatabase(DockerAPI docker, Path dest, boolean compress)
    {
        try {
            if (docker.exec(name, "pg_dumpall", "-U", "postgres", "-c", "-f", "/tmp/dump") != 0)
                throw new IOException("pg_dump failed");

            File tempdir = FileUtils.getTempDirectory();
            File tempfile = new File(tempdir, "dump");
            docker.downloadTo(name, "/tmp/dump", tempdir.toPath());

            OutputStream out;
            if (compress) {
                File zipname = dest.resolveSibling(dest.getFileName() + ".zip").toFile();
                out = new ZipArchiveOutputStream(zipname);
                ((ZipArchiveOutputStream)out).putArchiveEntry(new ZipArchiveEntry(dest.getFileName().toString()));
            } else {
                out = new FileOutputStream(dest.toFile());
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
            log.warning("Unabled to dump database: " + e);
            return false;
        }
    }

    public String getName()
    {
        return name;
    }

    public static void main(String args[]) throws JsonProcessingException
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(Include.NON_NULL);
        System.out.println(mapper.writeValueAsString(new Db().config));
    }
}
