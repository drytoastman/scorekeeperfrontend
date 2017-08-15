/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2010 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.util;

import java.io.File;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

/**
 */
public class Launcher
{
    private static final Logger log = Logger.getLogger(Launcher.class.getName());
	
	/**
	 * Called from other Java code to launch an application as a new process
	 * @param args
	 */
    public static void launchExternal(String app, String[] args)
    {
        try {
            ArrayList<String> cmd = new ArrayList<String>();
            if (System.getProperty("os.name").split("\\s")[0].equals("Windows"))
                cmd.add("javaw");
            else
                cmd.add("java");
            cmd.add("-cp");
            cmd.add(System.getProperty("java.class.path"));
            cmd.add(app);
            if (args != null)
                cmd.addAll(Arrays.asList(args));
            log.info(String.format("Running %s", cmd));
            ProcessBuilder starter = new ProcessBuilder(cmd);
            starter.redirectErrorStream(true);
            starter.redirectOutput(Redirect.appendTo(new File(Prefs.getLogDirectory(), "jvmlaunches.log")));
            Process p = starter.start();
            Thread.sleep(1000);
            if (!p.isAlive()) {
                throw new Exception("Process not alive after 1 second");
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, String.format("Failed to launch %s",  app), e);
        }
    }
    
    /**
     * Called as the main entry point for the jar to launch an applications in this process
     * @param args passed in on command line, first one is application name, default is TrayMonitor
     */
	public static void main(String args[])
	{
		String app = "org.wwscc.tray.TrayMonitor";
		if (args.length > 0)
			app = args[0];

		System.setProperty("swing.defaultlaf", UIManager.getSystemLookAndFeelClassName()); // for error dialogs below
		Class<?> appclass = null;
        try {
            appclass = Class.forName(app);
        } catch (Throwable e) {
            JOptionPane.showMessageDialog(null, "Unknown application " + app, "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
			appclass.getMethod("main", String[].class).invoke(null, new Object[] { new String[] {}});
        } catch (Throwable e) {
            JOptionPane.showMessageDialog(null, "Unable to launch " + app + ": " + e, "Error", JOptionPane.ERROR_MESSAGE);
        }
	}
}
