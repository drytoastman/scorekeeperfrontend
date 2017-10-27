/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.protimer;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.wwscc.util.Logging;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;


public class AuditLog implements MessageListener
{
    private static final Logger log = Logger.getLogger(AuditLog.class.getCanonicalName());

    FileHandler audit;
    
    public AuditLog()
    {
        try {
            audit = new FileHandler(Prefs.getLogDirectory().resolve("proaudit.%g.log").toAbsolutePath().toString(), 1000000, 10, true);
            audit.setFormatter(new Logging.SingleLineFormatter());
        } catch (IOException ioe) {
            log.log(Level.WARNING, "Can't open audit log: " + ioe, ioe);
            return;
        }

        Messenger.register(MT.FINISH_LEFT, this);
        Messenger.register(MT.FINISH_RIGHT, this);
        Messenger.register(MT.DELETE_FINISH_LEFT, this);
        Messenger.register(MT.DELETE_FINISH_RIGHT, this);
    }


    @Override
    public void event(MT type, Object o)
    {
        try 
        {
            LogRecord r = new LogRecord(Level.INFO, type.toString());
            switch (type)
            {
                case FINISH_LEFT:         r.setMessage(String.format("L+%03.3f", ((ColorTime)((Object[])o)[0]).time)); break;
                case FINISH_RIGHT:        r.setMessage(String.format("          R+%03.3f", ((ColorTime)((Object[])o)[0]).time)); break;
                case DELETE_FINISH_LEFT:  r.setMessage(              "L-DELETE"); break;
                case DELETE_FINISH_RIGHT: r.setMessage(              "          R-DELETE"); break;
            }
            audit.publish(r);
        }
        catch (Exception e)
        {
            log.log(Level.INFO, "audit log error: {0}", e);
        }
    }
}

