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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.ProgressMonitor;
import javax.swing.UIManager;

import org.apache.commons.io.FileUtils;
import org.wwscc.util.Logging;
import org.wwscc.util.Prefs;

public class DebugCollector extends Thread
{
    List<Path> files;
    
    public DebugCollector()
    {
    }
    
    public void run()
    {
        files = new ArrayList<Path>();
        
        final JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Specify a location to save the zip file");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int returnVal = fc.showOpenDialog(null);
        if (returnVal != JFileChooser.APPROVE_OPTION)
            return;
        
        ProgressMonitor monitor = new ProgressMonitor(null, "Extracting and compressing files", "initializing", 0, 100);
        monitor.setMillisToDecideToPopup(100);
        monitor.setMillisToPopup(100);
        monitor.setProgress(5);
        try 
        {
            Path temp = Files.createTempDirectory("sc_debug_");
            monitor.setProgress(10);
            monitor.setNote("getting docker information");
            String dbid = DockerInterface.dbcontainerid();
            monitor.setProgress(20);
            monitor.setNote("copying backend files");
            DockerInterface.copyLogs(dbid, temp);
            monitor.setProgress(30);
            monitor.setNote("dump database data");
            DockerInterface.dumpdatabase(dbid, temp.resolve("database.sql"));
          
            monitor.setProgress(60);
            monitor.setNote("adding backend logs to zipfile");
            addLogs(temp);
            monitor.setProgress(70);
            monitor.setNote("adding java logs to zipfile");
            addLogs(Paths.get(Prefs.getLogDirectory()));
            monitor.setProgress(80);
            monitor.setNote("saving zipfile to disk");
            zipfiles(new File(fc.getSelectedFile(), "debug.zip"));
            
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
    
    private void zipfiles(File dest) throws IOException
    {
        FileOutputStream fos = new FileOutputStream(dest);
        ZipOutputStream zos = new ZipOutputStream(fos);
        for (Path p : files) {
            zos.putNextEntry(new ZipEntry(p.getFileName().toString()));
            Files.copy(p, zos);
            zos.closeEntry();
        }
        zos.close();
        fos.close();        
    }
    
    private void addLogs(Path directory) throws IOException 
    {        
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".log") || file.toString().endsWith(".sql"))
                    files.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    /**
     * Test entry point.
     */
    public static void main(String args[]) throws IOException
    {
        System.setProperty("swing.defaultlaf", UIManager.getSystemLookAndFeelClassName());
        System.setProperty("program.name", "DebugCollector");
        Logging.logSetup("debugcollector");
        DockerInterface.machineenv(); // for testing on windows
        new DebugCollector().start();
    }

}
