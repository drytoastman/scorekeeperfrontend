/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system.monitors;

import java.util.logging.Logger;

import org.wwscc.system.DebugCollector;

/**
 * Abstract class to define the basic init, loop/wait and shutdown phases of our monitors
 */
public abstract class Monitor extends Thread
{
    private static final Logger log = Logger.getLogger(Monitor.class.getName());

    protected final Long ms;
    protected boolean quickrecheck;
    protected boolean done;

    protected abstract boolean minit();
    protected abstract void mloop();
    protected abstract void mshutdown();

    public Monitor(String name, long ms) {
        super(name);
        this.ms = ms;
        this.done = false;
    }

    public synchronized void poke() {
        notify();
    }

    public synchronized void donefornow() {
        try {
            this.wait(ms);
        } catch (InterruptedException ie) {}
    }

    public synchronized void pause(int msec) {
        try {
            this.wait(msec);
        } catch (InterruptedException ie) {}
    }

    public synchronized void shutdown() {
        log.info(getClass().getName() + " shutdown called");
        done = true;
        poke();
    }

    @Override
    public void run() {
        if (!minit())
            return;
        while (!done) {
            mloop();
            if (!quickrecheck && !done)
                donefornow();
            quickrecheck = false;
        }
        log.info(getClass().getName() + " shutting down");
        mshutdown();
    }
}
