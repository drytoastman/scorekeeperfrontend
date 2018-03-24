/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.util;

import java.util.logging.Logger;

/**
 * Wrap a variable that sends messages whenever its state changes.
 */
public class BroadcastState<T>
{
    private static final Logger log = Logger.getLogger(BroadcastState.class.getName());

    private T state;
    private MT event;

    public BroadcastState(MT event, T initial)
    {
        this.state = initial;
        this.event = event;
    }

    public void set(T newstate)
    {
        boolean change = false;
        if (newstate != null)
            change = (state == null || !newstate.equals(state));
        else
            change = (state != null);

        if (change)
        {
            log.info(String.format("%s changed: %s to %s ", event, state, newstate));
            state = newstate;
            Messenger.sendEvent(event, state);
        }
    }

    public T get()
    {
        return state;
    }
}
