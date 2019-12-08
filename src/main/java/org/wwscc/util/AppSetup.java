/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.util;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.swing.FocusManager;
import javax.swing.JOptionPane;
import javax.swing.UIDefaults;
import javax.swing.UIManager;

/**
 */
public class AppSetup
{
    public static void unitLogging()
    {
        // Start with a fresh root set at warning
        Logger root = LogManager.getLogManager().getLogger("");
        root.setLevel(Level.WARNING);
        for(Handler handler : root.getHandlers()) { root.removeHandler(handler); }

        Logger.getLogger("java.util.prefs").setLevel(Level.SEVERE);
        Logger.getLogger("org.postgresql.jdbc").setLevel(Level.OFF);
        Logger.getLogger("org.postgresql.Driver").setLevel(Level.OFF);
        Logger.getLogger("org.wwscc").setLevel(Level.ALL);

        ConsoleHandler ch = new ConsoleHandler();
        ch.setLevel(Level.ALL);
        ch.setFormatter(new SingleLineFormatter());
        root.addHandler(ch);
    }

    /**
     * Do some common setup for all applications at startup
     * @param name the application name used for Java logging and database logging
     */
    public static void appSetup(String name)
    {
        appSetup(name, new AlertHandlerSwing());
    }

    public static void appSetup(String name, AlertHandler ahandler)
    {
        // Set our platform wide L&F
        System.setProperty("swing.defaultlaf", "javax.swing.plaf.nimbus.NimbusLookAndFeel");
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        defaults.put("Table.gridColor", new Color(140,140,140));
        defaults.put("Table.showGrid", true);

        // Set the program name which is used by PostgresqlDatabase to identify the app in logs
        System.setProperty("program.name", name);

        // Start with a fresh root set at warning
        Logger root = LogManager.getLogManager().getLogger("");
        Formatter format = new SingleLineFormatter();

        root.setLevel(Level.WARNING);
        for(Handler handler : root.getHandlers()) {
            root.removeHandler(handler);
        }

        // Set prefs levels before windows preference load barfs useless data on the user
        Logger.getLogger("java.util.prefs").setLevel(Level.SEVERE);
        // postgres JDBC spits out a lot of data even though we catch the exception
        Logger.getLogger("org.postgresql.jdbc").setLevel(Level.OFF);
        Logger.getLogger("org.postgresql.Driver").setLevel(Level.OFF);

        // For our own logs, we can set super fine level or info depending on if debug mode and attach dialogs to those
        Logger applog = Logger.getLogger("org.wwscc");
        applog.setLevel(Prefs.getLogLevel().getJavaLevel());
        //applog.addHandler(ahandler);

        // Add console handler if running in debug mode
        if (Prefs.isDebug()) {
            ConsoleHandler ch = new ConsoleHandler();
            applog.setLevel(Level.ALL);
            ch.setLevel(Level.ALL);
            ch.setFormatter(format);
            root.addHandler(ch);
        }

        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                applog.log(Level.WARNING, String.format("\bUncaughtException in %s: %s", t, e), e);
            }});

        try {
            File logdir = Prefs.getLogDirectory().toFile();
            if (!logdir.exists())
                if (!logdir.mkdirs())
                    throw new IOException("Can't create log directory " + logdir);
            FileHandler fh = new FileHandler(new File(logdir, name+".%g.log").getAbsolutePath(), 10000000, 10, true);
            fh.setFormatter(format);
            fh.setLevel(Level.ALL);
            root.addHandler(fh);
        } catch (IOException ioe) {
            JOptionPane.showMessageDialog(FocusManager.getCurrentManager().getActiveWindow(),
                    "Unable to enable logging to file: " + ioe, "Log Error", JOptionPane.ERROR_MESSAGE);
        }

        applog.info("*** "+ name + " starting at " + new Date() + ", version=" + Prefs.getFullVersion());
        // force the initialization of IdGenerator on another thread so app can start now without an odd delay later
        new Thread() {
            public void run() {
                IdGenerator.generateId();
            }
        }.start();
    }

    public static class SingleLineFormatter extends Formatter
    {
        SimpleDateFormat dformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = new Date();

        /**
         * Format the given LogRecord.
         * @param record the log record to be formatted.
         * @return a formatted log record
         */
        @Override
        public synchronized String format(LogRecord record)
        {
            date.setTime(record.getMillis());
            StringBuffer sb = new StringBuffer(dformat.format(date));
            if (record.getLoggerName() != null)
                sb.append(" " + record.getLoggerName());
            if (record.getSourceMethodName() != null)
                sb.append(" " + record.getSourceMethodName());
            sb.append(" " + record.getLevel().getLocalizedName() + ": ");
            sb.append(formatMessage(record).replaceAll("[\b]", ""));
            sb.append("\n");

            if (record.getThrown() != null)
            {
                try
                {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    record.getThrown().printStackTrace(pw);
                    pw.close();
                    sb.append(sw.toString());
                }
                catch (Exception ex)
                {
                }
            }

            return sb.toString();
        }
    }
}
