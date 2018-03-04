package org.wwscc.util;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;

import javax.swing.JOptionPane;

public class SingletonProcessTest
{
    /**
     * Use a local file lock to make sure that we are the only application with a particular string key running.
     * @param appname the name key we are checking with
     * @return true if we are the only app with the key running and can continue, false if we should stop
     */
    public static boolean ensureSingleton(String appname)
    {
        FileLock filelock = null;
        try {
            filelock = FileChannel.open(Prefs.getLockFilePath(appname), StandardOpenOption.CREATE, StandardOpenOption.WRITE).tryLock();
            if (filelock == null) throw new IOException("File already locked");
        } catch (Exception e) {
            if (JOptionPane.showConfirmDialog(null, "<html>"+ e + "<br/><br/>" +
                        "Unable to lock "+appname+" access. " +
                        "This usually indicates that another copy of "+appname+" is<br/>already running and only one should be running at a time. " +
                        "It is also possible that "+appname+" <br/>did not exit cleanly last time and the lock is just left over.<br/><br/>" +
                        "Click No to quit now or click Yes to start anyways.<br/>&nbsp;<br/>",
                        "Continue With Launch", JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION)
                return false;
        }

        Runtime.getRuntime().addShutdownHook(new CleanupThread(filelock));
        return true;
    }

    /**
     * Class wrapper for passing file lock into the thread for later cleanup.
     */
    static class CleanupThread extends Thread
    {
        FileLock filelock;
        public CleanupThread(FileLock l) {
            filelock = l;
        }
        @Override
        public void run() {
            try {
                if (filelock != null)
                    filelock.release();
            } catch (IOException e) {}
        }
    }
}
