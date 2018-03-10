/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.actions;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;

import org.wwscc.util.MT;
import org.wwscc.util.Messenger;

public class EventSendAction extends AbstractAction
{
    MT event;
    Object arg;

    public EventSendAction(String title, MT tosend)
    {
        this(title, tosend, null, null);
    }

    public EventSendAction(String title, MT tosend, KeyStroke ks)
    {
        this(title, tosend, null, ks);
    }

    public EventSendAction(String title, MT tosend, Object o, KeyStroke ks)
    {
        super(title);
        event = tosend;
        arg = o;
        if (ks != null) putValue(ACCELERATOR_KEY, ks);
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        Messenger.sendEvent(event, arg);
    }
}
