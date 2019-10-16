/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract class to define the basic init, loop/wait and shutdown phases of our monitors
 */
public abstract class MonitorBase extends Thread
{
    private static final Logger log = Logger.getLogger(MonitorBase.class.getName());

    protected final Long ms;
    protected boolean quickrecheck;
    protected boolean done;

    protected abstract boolean minit() throws Exception;
    protected abstract void mloop() throws Exception;
    protected abstract void mshutdown() throws Exception;

    public MonitorBase(String name, long ms) {
        super(name);
        this.ms = ms;
        this.done = false;
    }

    public synchronized void poke() {
        notify();
    }

    public void donefornow() {
        donefornow(ms);
    }

    public synchronized void donefornow(long waitms) {
        try {
            this.wait(waitms);
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
        try {
            if (!minit())
                return;
        } catch (Exception e) {
            log.log(Level.SEVERE, getClass().getName() + " mshutdown exception: " + e, e);
            return;
        }

        while (!done) {
            try {
                mloop();
            } catch (Exception e) {
                log.log(Level.SEVERE, getClass().getName() + " mloop exception: " + e, e);
            }
            if (!quickrecheck && !done)
                donefornow();
            quickrecheck = false;
        }

        log.info(getClass().getName() + " shutting down");
        try {
            mshutdown();
        } catch (Exception e) {
            log.log(Level.SEVERE, getClass().getName() + " mshutdown exception: " + e, e);
        }
    }
}
