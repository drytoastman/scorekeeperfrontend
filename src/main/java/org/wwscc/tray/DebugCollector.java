/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.tray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.ProgressMonitor;
import org.apache.commons.io.FileUtils;
import org.wwscc.storage.Database;
import org.wwscc.util.AppSetup;
import org.wwscc.util.Prefs;

public class DebugCollector extends Thread
{
    private static final Logger log = Logger.getLogger(DebugCollector.class.getName());

    List<Path> files;
    DataRetrievalInterface retrieval;

    public DebugCollector(DataRetrievalInterface rt)
    {
        files = new ArrayList<Path>();
        retrieval = rt;
    }

    public void run()
    {
        final JFileChooser fc = new JFileChooser() {
            @Override
            public void approveSelection(){
                File f = getSelectedFile();
                if(f.exists()) {
                    int result = JOptionPane.showConfirmDialog(this,"The file exists, overwrite?", "Existing file", JOptionPane.YES_NO_CANCEL_OPTION);
                    switch(result){
                        case JOptionPane.YES_OPTION:
                            super.approveSelection();
                            return;
                        case JOptionPane.CANCEL_OPTION:
                            cancelSelection();
                            return;
                        case JOptionPane.NO_OPTION:
                        case JOptionPane.CLOSED_OPTION:
                            return;
                    }
                }
                super.approveSelection();
            }
        };

        fc.setDialogTitle("Specify a zip file to save files in");
        fc.setSelectedFile(new File("debug-" + new SimpleDateFormat("yyyy-MM-dd_HH-mm").format(new Date()) + ".zip"));
        int returnVal = fc.showSaveDialog(null);
        if (returnVal != JFileChooser.APPROVE_OPTION)
            return;

        File zipfile = fc.getSelectedFile();
        if (zipfile.exists() && !zipfile.canWrite())
        {
            log.warning("\b" + zipfile + " is not writable");
            return;
        }

        ProgressMonitor monitor = new ProgressMonitor(null, "Extracting and compressing files", "initializing", 0, 100);
        monitor.setMillisToDecideToPopup(0);
        monitor.setMillisToPopup(10);
        monitor.setProgress(5);
        try
        {
            Path temp = Files.createTempDirectory("sc_debug_");
            monitor.setProgress(10);
            monitor.setNote("creating info.txt");

            Path info = Files.createFile(temp.resolve("info.txt"));
            Map<String, String> machineenv = DockerMachine.machineenv();
            OutputStreamWriter out = new OutputStreamWriter(Files.newOutputStream(info, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
            out.write("Java version = " + System.getProperty("java.version") + "\n");
            out.write("Docker version = " + DockerContainer.version(machineenv) + "\n");
            out.write("Machine Env = " + Arrays.toString(machineenv.entrySet().toArray()) + "\n");
            out.close();

            monitor.setProgress(20);
            monitor.setNote("copying backend files");
            retrieval.copyLogs(temp);
            monitor.setProgress(30);
            monitor.setNote("dump database data");

            String ver = Database.d.getVersion();
            retrieval.dumpDatabase(temp.resolve(String.format("database#schema_%s.pgdump", ver)), false);

            // second backup the database
            monitor.setProgress(60);
            monitor.setNote("adding backend logs to zipfile");
            addLogs(temp);
            monitor.setProgress(70);
            monitor.setNote("adding java logs to zipfile");
            addLogs(Prefs.getLogDirectory());
            monitor.setProgress(80);
            monitor.setNote("saving zipfile to disk");
            zipfiles(zipfile);

            // Just me being worried, make sure someone doesn't hand us "/", "/tmp/asdf" is count == 2
            monitor.setProgress(90);
            if (temp.getNameCount() >= 2)
            {
                monitor.setNote("deleting temporary directory");
                FileUtils.deleteDirectory(temp.toFile());
            }
        }
        catch (Exception ioe)
        {
            JOptionPane.showMessageDialog(null, "Error create debug file: " + ioe, "Error", JOptionPane.ERROR_MESSAGE);
        }

        monitor.close();
    }

    /**
     *  Zip all the files in our
     * @param dest
     * @throws IOException
     */
    private void zipfiles(File dest) throws IOException
    {
        FileOutputStream fos = new FileOutputStream(dest);
        ZipOutputStream zos = new ZipOutputStream(fos);
        for (Path p : files) {
            try {
                Path file = p.getFileName();
                if (file != null) {
                    zos.putNextEntry(new ZipEntry(file.toString()));
                    Files.copy(p, zos);
                }
            } catch (IOException ioe) {
                log.warning("\bUnable to archive " + p);
            }
            zos.closeEntry();
        }
        zos.close();
        fos.close();
    }

    /**
     * Collects all file names in the directory and puts in our files list.
     */
    private void addLogs(Path directory) throws IOException
    {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.toString().endsWith(".lck")) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Test entry point.
     * @param args the command line args, ignored
     * @throws IOException if collection fails
     */
    public static void main(String args[]) throws IOException
    {
        AppSetup.appSetup("debugcollector");
        DockerContainer.Db db = new DockerContainer.Db();
        db.setMachineEnv(DockerMachine.machineenv());
        new DebugCollector(db).start();
    }
}
