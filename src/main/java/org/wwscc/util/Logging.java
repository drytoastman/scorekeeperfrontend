/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.util;

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
import java.util.logging.SimpleFormatter;
import javax.swing.FocusManager;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 */
public class Logging
{
    public static void logSetup(String name)
    {
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
        Logger.getLogger("org.postgresql.Driver").setLevel(Level.OFF);

        // Add console handler if running in debug mode
        if (Prefs.isDebug()) {
            ConsoleHandler ch = new ConsoleHandler();
            ch.setLevel(Level.ALL);
            ch.setFormatter(format);
            root.addHandler(ch);
        }

        // For our own logs, we can set super fine level or info depending on if debug mode and attach dialogs to those
        Logger applog = Logger.getLogger("org.wwscc");
        applog.setLevel(Prefs.isDebug() ? Level.FINEST : Level.INFO);
        applog.addHandler(new AlertHandler());

        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                applog.log(Level.WARNING, String.format("\bUncaughtException in %s: %s", t, e), e);
            }});

        try {
            File logdir = new File(Prefs.getLogDirectory());
            if (!logdir.exists())
                if (!logdir.mkdirs())
                    throw new IOException("Can't create log directory " + logdir);
            FileHandler fh = new FileHandler(new File(logdir, name+".%g.log").getAbsolutePath(), 1000000, 10, true);
            fh.setFormatter(format);
            fh.setLevel(Level.ALL);
            root.addHandler(fh);
        } catch (IOException ioe) {
            JOptionPane.showMessageDialog(FocusManager.getCurrentManager().getActiveWindow(),
                    "Unable to enable logging to file: " + ioe, "Log Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Special handler that looks for the unprintable backspace '\b' character in a log record
     * as an indicator that it should throw up a dialog with the record message.  This lets us
     * still log warning and severe messages but only use a dialog when requested and with
     * a very easy indicator that works inside the java logging framework.
     */
    public static class AlertHandler extends Handler
    {
        public AlertHandler()
        {
            setLevel(Level.ALL);
            setFormatter(new SimpleFormatter());
        }

        public void publish(LogRecord logRecord)
        {
            if (isLoggable(logRecord))
            {
                int type;
                String title;
                if (logRecord.getMessage().charAt(0) != '\b') {
                    return;
                }

                int val = logRecord.getLevel().intValue();
                if (val >= Level.SEVERE.intValue())
                {
                    title = "Error";
                    type = JOptionPane.ERROR_MESSAGE;
                }
                else if (val >= Level.WARNING.intValue())
                {
                    title = "Warning";
                    type = JOptionPane.WARNING_MESSAGE;
                }
                else
                {
                    title = "Note";
                    type = JOptionPane.INFORMATION_MESSAGE;
                }

                String record = getFormatter().formatMessage(logRecord).replaceAll("[\b]","");
                if (record.contains("\n"))
                    record = "<HTML>" + record.replace("\n", "<br>") + "</HTML>";

                // stay off FX or other problem threads
                final String msg = record;
                SwingUtilities.invokeLater(new Runnable() {
                    @Override public void run() {
                        JOptionPane.showMessageDialog(FocusManager.getCurrentManager().getActiveWindow(), msg, title, type);
                    }
                });
            }
        }

        public void flush() {}
        public void close() {}
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
