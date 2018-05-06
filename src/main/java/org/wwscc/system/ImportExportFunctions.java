package org.wwscc.system;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.wwscc.storage.Database;

public class ImportExportFunctions
{
    /*
     *  If file is a zip, extract any enclosed .sql file.  Either way,
     *  verify that the result is a .sql file.
     */
    public static Path extractSql(Path importfile) throws IOException
    {
        if (importfile.toString().toLowerCase().endsWith(".zip")) {
            File temp;
    fileok: try (ZipFile zip = new ZipFile(importfile.toFile())) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    temp = File.createTempFile("open-zip", ".sql");
                    FileUtils.copyInputStreamToFile(zip.getInputStream(entry), temp);
                    break fileok;
                }
                throw new IOException("No sql file found in zip file");
            }

            importfile = temp.toPath();
        }

        if (!importfile.toString().toLowerCase().endsWith(".sql")) {
            throw new IOException("Data is not a sql file");
        }

        return importfile;
    }

    /**
     * Check the schema version:
     * looking for lines like the following for the version table:
     * COPY version (id, version, modified) FROM stdin;
     * 1 20180106 2017-11-17 05:32:37.805896
     * @throws IOException
     */
    public static void checkSchema(Path importfile) throws IOException
    {
fileok: try (Scanner scan = new Scanner(importfile)) {
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
        }
    }


    public static Path backupName(Path directory) throws Exception
    {
        String ver = Database.d.getVersion();
        String date = new SimpleDateFormat("yyyy-MM-dd-HH'h'mm'm'").format(new Date());
        return directory.resolve(String.format("%s_%s.sql", date, ver));
    }


    public static void processBackup(File dumpfile, Path destfile, boolean compress) throws IOException
    {
        OutputStream out;
        if (compress) {
            File zipname = destfile.resolveSibling(destfile.getFileName() + ".zip").toFile();
            out = new ZipOutputStream(new FileOutputStream(zipname));
            ((ZipOutputStream)out).putNextEntry(new ZipEntry(destfile.getFileName().toString()));
        } else {
            out = new FileOutputStream(destfile.toFile());
        }

        out.write("UPDATE pg_database SET datallowconn = 'false' WHERE datname = 'scorekeeper';\n".getBytes());
        out.write("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'scorekeeper';\n".getBytes());
        FileUtils.copyFile(dumpfile, out);
        out.write("UPDATE pg_database SET datallowconn = 'true' WHERE datname = 'scorekeeper';\n".getBytes());

        if (compress)
            ((ZipOutputStream)out).closeEntry();
        out.close();

        dumpfile.delete();
    }
}
