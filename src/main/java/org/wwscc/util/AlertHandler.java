/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2019 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.util;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/**
 * Special handler that looks for the unprintable backspace '\b' character in a log record
 * as an indicator that it should throw up a dialog with the record message.  This lets us
 * still log warning and severe messages but only use a dialog when requested and with
 * a very easy indicator that works inside the java logging framework.
 */
public abstract class AlertHandler extends Handler
{
    public AlertHandler()
    {
        setLevel(Level.ALL);
        setFormatter(new SimpleFormatter());
    }

    abstract public void displayAlert(int level, String msg);

    public void publish(LogRecord logRecord)
    {
        if (isLoggable(logRecord))
        {
            if (logRecord.getMessage().charAt(0) != '\b') {
                return;
            }

            int val = logRecord.getLevel().intValue();
            String record = getFormatter().formatMessage(logRecord).replaceAll("[\b]","");
            displayAlert(val, record);
        }
    }

    public void flush() {}
    public void close() {}
}