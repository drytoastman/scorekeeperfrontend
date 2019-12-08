/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2019 Brett Wilson.
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
    private static enum Mode { SWING_THREAD, CALLBACK, SENDER_THREAD };
    private static final Logger log = Logger.getLogger(Messenger.class.getCanonicalName());
    private static EnumMap<MT, Set<MessageListener>> listPtrs = new EnumMap<MT, Set<MessageListener>>(MT.class);
    private static Mode mode = Mode.SWING_THREAD;
    private static SendMessageCallback callback = null;

    public static interface SendMessageCallback
    {
        public void sendEvent(final MT type, final Object data);
    }

    static public void setTestMode()
    {
        mode = Mode.SENDER_THREAD;
    }

    static public void setCallbackMode(SendMessageCallback intf)
    {
        mode = Mode.CALLBACK;
        callback = intf;
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
        if (!listPtrs.containsKey(type))
            return;
        switch (mode)
        {
            case SWING_THREAD:
                SwingUtilities.invokeLater(() -> sendEventNowWrapper(type, data));
                break;

            case CALLBACK:
                callback.sendEvent(type,  data);
                break;

            case SENDER_THREAD:
            default:
                sendEventNowWrapper(type, data);
                break;
        }
    }

    public static void sendEventNowWrapper(MT type, Object data)
    {
        try {
            sendEventNow(type, data);
        } catch (Exception e) {
            log.log(Level.WARNING, "Error sending " + type.toString() + ": " + e.getMessage(), e);
        }
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


