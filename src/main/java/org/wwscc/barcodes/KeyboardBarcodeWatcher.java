package org.wwscc.barcodes;

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;

/**
 * Special watcher version for intercepting keypresses from the user interface.
 */
public class KeyboardBarcodeWatcher extends WatcherBase implements KeyEventDispatcher
{
    public static final String TYPE = "Keyboard";

    public KeyboardBarcodeWatcher()
    {
        super(TYPE);
    }

    public void start()
    {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
    }

    public void stop()
    {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
    }

    /**
     * Implemented interface to respond to key events
     * @param ke the incoming event
     * @return always returns true, we will redispatch things ourselves if there is no barcode
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent ke)
    {
        processEvent(ke);
        return true;
    }

    /**
     * For the keyboard wedge, we need to push scanned keypresses back into the event system.
     */
    @Override
    protected void dumpQueue(int count)
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
