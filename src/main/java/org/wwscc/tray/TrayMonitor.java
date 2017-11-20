/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.tray;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;

import javax.swing.Action;
import javax.swing.JOptionPane;
import org.wwscc.util.AppSetup;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;
import org.wwscc.util.Resources;
import org.wwscc.util.WrappedAWTMenuItem;

public class TrayMonitor
{
    private static final Logger log = Logger.getLogger(TrayMonitor.class.getName());
    private static final Image coneok, conewarn;
    static
    {
        coneok   = Resources.loadImage("conesmall.png");
        conewarn = Resources.loadImage("conewarn.png");
    }

    StateControl state;
    ScorekeeperStatusWindow statuswindow;
    TrayIcon trayIcon;
    FileLock filelock;
    Actions actions;

    public TrayMonitor(String args[])
    {
        if (!SystemTray.isSupported())
        {
            log.severe("\bTrayIcon is not supported, unable to run Scorekeeper monitor application.");
            System.exit(-1);
        }

        if (!ensureSingleton())
        {
            log.warning("Another TrayMonitor is running, quitting now.");
            System.exit(-1);
        }

        actions = new Actions();
        state = new StateControl();
        statuswindow = new ScorekeeperStatusWindow(actions);

        PopupMenu trayPopup = new PopupMenu();
        trayPopup.add(new WrappedAWTMenuItem(actions.openStatus));
        trayPopup.addSeparator();
        for (Action a : actions.apps)
            trayPopup.add(new WrappedAWTMenuItem(a));
        trayPopup.addSeparator();
        trayPopup.add(new WrappedAWTMenuItem(actions.quit));

        trayIcon = new TrayIcon(conewarn, "Scorekeeper Monitor", trayPopup);
        trayIcon.setImageAutoSize(true);

        Messenger.register(MT.BACKEND_READY, (type, data) -> trayIcon.setImage(((boolean)data) ? coneok : conewarn));

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            log.severe("\bFailed to create TrayIcon: " + e);
            System.exit(-2);
        }

        statuswindow.setVisible(true);
    }


    public void startAndWaitForThreads()
    {
        state.startAndWaitForThreads();
    }

    /**
     * Use a local file lock to make sure that we are the only tray monitor running.
     * @return true if we are the only running traymonitor and can continue, false if we should stop
     */
    private boolean ensureSingleton()
    {
        try {
            filelock = FileChannel.open(Prefs.getLockFilePath("traymonitor"), StandardOpenOption.CREATE, StandardOpenOption.WRITE).tryLock();
            if (filelock == null) throw new IOException("File already locked");
        } catch (Exception e) {
            if (JOptionPane.showConfirmDialog(null, "<html>"+ e + "<br/><br/>" +
                        "Unable to lock TrayMonitor access. " +
                        "This usually indicates that another copy of TrayMonitor is<br/>already running and only one should be running at a time. " +
                        "It is also possible that TrayMonitor<br/>did not exit cleanly last time and the lock is just left over.<br/><br/>" +
                        "Click No to quit now or click Yes to start anyways.<br/>&nbsp;<br/>",
                        "Continue With Launch", JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION)
                return false;
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override public void run() {
                try {
                    if (filelock != null)
                        filelock.release();
                } catch (IOException e) {}
        }});

        return true;
    }

    /**
     * Main entry point.
     * @param args passed to any launched application, ignored otherwise
     */
    public static void main(String args[])
    {
        AppSetup.appSetup("traymonitor");
        TrayMonitor tm = new TrayMonitor(args);
        tm.startAndWaitForThreads();
        System.exit(0);
    }
}
