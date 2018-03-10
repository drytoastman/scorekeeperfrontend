/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */


package org.wwscc.util;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

public class Messenger
{
    private static final Logger log = Logger.getLogger(Messenger.class.getCanonicalName());
    private static EnumMap<MT, Set<MessageListener>> listPtrs = new EnumMap<MT, Set<MessageListener>>(MT.class);
    private static boolean testMode = false;

    static public void setTestMode()
    {
        testMode = true;
    }

    static public synchronized void unregisterAll(MessageListener listener)
    {
        for (Set<MessageListener> s : listPtrs.values())
            s.remove(listener);
    }

    public static synchronized void unregister(MT type, MessageListener listener)
    {
        Set<MessageListener> h = listPtrs.get(type);
        if (h != null)
            h.remove(listener);
    }

    public static synchronized void register(MT type, MessageListener listener)
    {
        Set<MessageListener> h = listPtrs.get(type);
        if (h == null)
        {
            h = new HashSet<MessageListener>();
            listPtrs.put(type, h);
        }
        h.add(listener);
    }

    public static void sendEvent(final MT type, final Object data)
    {
        if (testMode) { // in test mode we don't need to let it step through the event thread
            sendEventNow(type, data);
            return;
        }

        SwingUtilities.invokeLater(new Runnable() { public void run() {
            try {
                sendEventNow(type, data);
            } catch (Exception e) {
                log.log(Level.WARNING, "Error sending " + type.toString() + ": " + e.getMessage(), e);
            }
        }});
    }

    public static synchronized void sendEventNow(MT type, Object data)
    {
        Set<MessageListener> h = listPtrs.get(type);
        if (h == null) return;
        for (MessageListener ml : h) {
            ml.event(type, data);
        }
    }
}


