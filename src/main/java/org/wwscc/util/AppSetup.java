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
import java.util.logging.SimpleFormatter;
import javax.swing.FocusManager;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

/**
 */
public class AppSetup
{
    public static enum Mode { SWING_MODE, FX_MODE };

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
        appSetup(name, Mode.SWING_MODE);
    }

    public static void appSetup(String name, Mode mode)
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
        applog.addHandler(new AlertHandler(mode));

        // Add console handler if running in debug mode
        if (Prefs.isDebug()) {
            ConsoleHandler ch = new ConsoleHandler();
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

    /**
     * Special handler that looks for the unprintable backspace '\b' character in a log record
     * as an indicator that it should throw up a dialog with the record message.  This lets us
     * still log warning and severe messages but only use a dialog when requested and with
     * a very easy indicator that works inside the java logging framework.
     */
    public static class AlertHandler extends Handler
    {
        Mode mode;
        public AlertHandler(Mode mode)
        {
            this.mode = mode;
            setLevel(Level.ALL);
            setFormatter(new SimpleFormatter());
        }

        public void publish(LogRecord logRecord)
        {
            if (isLoggable(logRecord))
            {
                AlertType fxtype;
                int swingtype;
                String title;
                if (logRecord.getMessage().charAt(0) != '\b') {
                    return;
                }

                int val = logRecord.getLevel().intValue();
                if (val >= Level.SEVERE.intValue())
                {
                    title = "Error";
                    swingtype = JOptionPane.ERROR_MESSAGE;
                    fxtype = AlertType.ERROR;
                }
                else if (val >= Level.WARNING.intValue())
                {
                    title = "Warning";
                    swingtype = JOptionPane.WARNING_MESSAGE;
                    fxtype = AlertType.WARNING;
                }
                else
                {
                    title = "Note";
                    swingtype = JOptionPane.INFORMATION_MESSAGE;
                    fxtype = AlertType.INFORMATION;
                }

                final String record = getFormatter().formatMessage(logRecord).replaceAll("[\b]","");

                if (mode.equals(Mode.FX_MODE)) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(fxtype);
                        alert.setTitle(title);
                        alert.setHeaderText(null);
                        alert.setContentText(record);
                        alert.showAndWait();
                    });
                } else {
                    final String msg = (record.contains("\n")) ? "<HTML>" + record.replace("\n", "<br>") + "</HTML>" : record;
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane p = new JOptionPane(msg, swingtype);
                        JDialog d = p.createDialog(title);
                        d.setAlwaysOnTop(true);
                        d.setLocationRelativeTo(FocusManager.getCurrentManager().getActiveWindow());
                        d.setVisible(true);
                    });

                }
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
            String name   = record.getLoggerName();
            String source = record.getSourceMethodName();
            String level  = record.getLevel().getLocalizedName();

            if (name != null)
                sb.append(" " + name);
            if (source != null)
                sb.append(" " + source);
            if (level.length() > 0)
                sb.append(" " + level);
            sb.append(": ");
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
