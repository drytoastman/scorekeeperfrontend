/*
 * This software is licensed under the GPLv3 license, included
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */
package org.wwscc.barcodes;

import java.awt.event.KeyEvent;
import java.math.BigInteger;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Timer;

import org.wwscc.storage.Car;
import org.wwscc.storage.Database;
import org.wwscc.storage.Driver;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;

/**
 * Queue that watches incoming key characters to look for the
 * characters and timing that indicates input from a barcode scanner.
 */
public abstract class WatcherBase
{
    private static final Logger log = Logger.getLogger(WatcherBase.class.getCanonicalName());
    protected final LinkedList<KeyEvent> queue = new LinkedList<KeyEvent>();
    protected final Timer queuePush;
    protected final String inputtype;
    protected ScannerConfig config;

    public WatcherBase(String inputtype)
    {
        queuePush = new Timer(1, null);
        queuePush.setCoalesce(true);
        queuePush.setRepeats(false);
        queuePush.addActionListener(e -> timeout());

        this.inputtype = inputtype;
        applyConfig();
        Messenger.register(MT.SCANNER_OPTIONS_CHANGED, (t, d) -> applyConfig());
    }

    public abstract void start();
    public abstract void stop();
    protected abstract void dumpQueue(int count);

    /**
     * The synchronized interface to process key events
     * @param ke the incoming event
     */
    public synchronized void processEvent(KeyEvent ke)
    {
        queue.addLast(ke);
        int count = scanQueue();
        if (count == queue.size()) {
            queuePush.stop();
        } else {
            queuePush.restart();
        }
        if (count > 0) // save a synchronized call
            dumpQueue(count);
    }

    /**
     * The synchronized interface for queue timeouts
     */
    private synchronized void timeout()
    {
        dumpQueue(queue.size());
    }

    /**
     * Applying current config to local variables
     */
    private void applyConfig()
    {
        config = ScannerConfig.defaultFor(inputtype);
        config.decode(Prefs.getScannerConfig(inputtype));
        queuePush.setInitialDelay(config.delay);
        queuePush.setDelay(config.delay);
    }

    /**
     * A 40 digit input was scanned, this is our CODE128C encoding of a UUID
     * @param digits the 40 digits
     */
    private void processUUID(String digits)
    {
        String s = String.format("%032x", new BigInteger(digits.toString()));
        UUID uuid = UUID.fromString(String.format("%s-%s-%s-%s-%s", s.substring(0,8), s.substring(8,12), s.substring(12,16), s.substring(16,20), s.substring(20,32)));
        log.log(Level.INFO, "UUID scanned - {0}", uuid);

        if (Prefs.isTestMode()) {
            Messenger.sendEvent(MT.OBJECT_SCANNED, uuid);
            return;
        }

        Car c = Database.d.getCar(uuid);
        if (c != null) {
            Messenger.sendEvent(MT.OBJECT_SCANNED, c);
            return;
        }

        Driver d = Database.d.getDriver(uuid);
        if (d != null) {
            Messenger.sendEvent(MT.OBJECT_SCANNED, d);
            return;
        }

        log.log(Level.WARNING, "\bNothing found for scanned UUID {0}", uuid);
    }

    /**
     * Primary logic.
     * Run through the queue to see if we got a possible match of:
     * [0] stx, [1 - (n-1)] Integers within time frame, [n]
     * @return the count of key events we can get rid of
     */
    private int scanQueue()
    {
        StringBuilder barcode = new StringBuilder();
        ListIterator<KeyEvent> iter = queue.listIterator();

        if (config.stx != '\uFFFF')
        {
            KeyEvent first = iter.next();
            if (first.getID() != KeyEvent.KEY_TYPED)
                return 1;

            if (first.getKeyChar() != config.stx)
                return 1;
        }

        while (iter.hasNext())
        {
            KeyEvent ke = iter.next();
            if (ke.getID() != KeyEvent.KEY_TYPED)  // only look at TYPED events
                continue;

            Character c = ke.getKeyChar();
            if ((config.stx != '\uFFFF') && (c == config.stx)) // a second stx char, clear buffer before this
                return iter.nextIndex()-1;

            if (c == config.etx) {
                queue.clear();
                if (barcode.length() == 40) { // encoded UUID, decode
                    processUUID(barcode.toString());
                } else {
                    log.log(Level.INFO, "Barcode scanned {0}", barcode);
                    Messenger.sendEvent(MT.BARCODE_SCANNED, barcode.toString());
                }
                return iter.nextIndex();  // time to dump
            }

            barcode.append(c);
            if (barcode.length() > 40) {
                log.log(Level.FINE, "Barcode too long, ignoring");
                return iter.nextIndex(); // time to dump
            }
        }

        return 0;
    }
}
