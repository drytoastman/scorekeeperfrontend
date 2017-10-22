/*
 * This software is licensed under the GPLv3 license, included
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2012 Brett Wilson.
 * All rights reserved.
 */
package org.wwscc.barcodes;

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Timer;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;

/**
 * EventDispatcher that watches incoming key characters to look for the
 * of characters and timing that indicates input from a barcode scanner.
 * it into an internal event and keep the keystrokes from rest of the application.
 */
public class BarcodeScannerWatcher implements KeyEventDispatcher
{
    private static final Logger log = Logger.getLogger(BarcodeScannerWatcher.class.getCanonicalName());
    private final LinkedList<KeyEvent> queue = new LinkedList<KeyEvent>();
    private final Timer queuePush;
    ScannerConfig config;

    public BarcodeScannerWatcher()
    {
        queuePush = new Timer(1, null);
        queuePush.setCoalesce(true);
        queuePush.setRepeats(false);
        queuePush.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent ae) { 
                timeout(); 
            }
        });

        applyConfig();
        Messenger.register(MT.SCANNER_OPTIONS_CHANGED, new MessageListener() {
            @Override public void event(MT type, Object data) {
                applyConfig();
            }
        });
    }

    private void applyConfig()
    {
        config = new ScannerConfig(Prefs.getScannerConfig());
        queuePush.setInitialDelay(config.delay);
        queuePush.setDelay(config.delay);
    }


    /**
     * Implemented interface to respond to key events
     * @param ke the incoming event
     * @return always returns true, we will redispatch things ourselves if they are a barcode
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent ke)
    {
        processEvent(ke);
        return true;
    }
    
    
    /**
     * The synchronized method to dump the queue into the event system.
     */
    private synchronized void timeout()
    {
        _dumpQueue(queue.size());
    }
    

    /**
     * The synchronized methods to process key events
     * @param ke the incoming event
     */
    private synchronized void processEvent(KeyEvent ke)
    {
        queue.addLast(ke);
        int count = _scanQueue();
        if (count == queue.size()) {
            queuePush.stop();
        } else {
            queuePush.restart();
        }
        if (count > 0) // save a synchronized call
            _dumpQueue(count);
    }

    /**
     * Run through the queue to see if we got a possible match of:
     * [0] stx, [1 - (n-1)] Integers within time frame, [n]
     * @return the count of key events we can dump out to the user
     */
    private int _scanQueue()
    {
        StringBuilder barcode = new StringBuilder();
        ListIterator<KeyEvent> iter = queue.listIterator();

        KeyEvent first = iter.next();
        if ((first.getID() != KeyEvent.KEY_TYPED) || (first.getKeyChar() != config.stx))
            return 1;
        
        while (iter.hasNext())
        {
            KeyEvent ke = iter.next();            
            if (ke.getID() != KeyEvent.KEY_TYPED)  // only look at TYPED events
                continue;

            Character c = ke.getKeyChar();
            if (c == config.stx) // a second stx char, clear buffer before this 
                return iter.nextIndex()-1;
            
            if (c == config.etx) {
                queue.clear();
                log.log(Level.FINE, "Scanned barcode {0}", barcode);
                Messenger.sendEvent(MT.BARCODE_SCANNED, barcode.toString());
                return iter.nextIndex();  // time to dump
            }

            barcode.append(c);            
            if (barcode.length() > 20) {
                log.log(Level.FINE, "Barcode too long, ignoring");
                return iter.nextIndex(); // time to dump
            }
        }

        return 0;
    }


    /**
     * Dump some of the queued key events back into the regular event system.
     */
    private void _dumpQueue(int count)
    {
        KeyboardFocusManager mgr = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        while ((count > 0) && (queue.size() > 0))
        {
            KeyEvent ke = queue.pop();
            mgr.redispatchEvent(ke.getComponent(), ke);
            count--;
        }
    }
}
