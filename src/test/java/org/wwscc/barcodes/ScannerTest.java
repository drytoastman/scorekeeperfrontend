package org.wwscc.barcodes;

import java.awt.event.KeyEvent;
import javax.swing.JLabel;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wwscc.util.Logging;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;

public class ScannerTest {

    static char start = 2;
    static char end   = 3;
    static JLabel ignore = new JLabel();
    static String scanned = "";
    static MessageListener listener = new MessageListener() {
        @Override
        public void event(MT type, Object data) {
            if (type == MT.BARCODE_SCANNED) {
                scanned = (String)data;
            }
        }
    };

    @BeforeClass
    public static void init() {
        Logging.unitLogging();
        Prefs.setTestMode();
        Prefs.setScannerConfig(new ScannerConfig(start, end, 200).encode());
        Messenger.setTestMode();
        Messenger.register(MT.BARCODE_SCANNED, listener);
    }
    
    @AfterClass
    public static void fini() throws Exception {
        Messenger.unregister(MT.BARCODE_SCANNED, listener);
    }
    
    BarcodeScannerWatcher watcher;
    
    @Before
    public void start() {
        watcher = new BarcodeScannerWatcher();
        scanned = "";
    }
    
    @Test
    public void testGood() throws InterruptedException {
        for (int delay : new int[] { 0, 100, 190 }) {
            scanned = "";
            for (char c : new char[] { start, '1', '2', '3', end }) {
                watcher.dispatchKeyEvent(new KeyEvent(ignore, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,  KeyEvent.VK_UNDEFINED, c));
                Thread.sleep(delay);
            }
            Assert.assertEquals("inter-char delay="+delay, "123", scanned);
        }
    }
    
    @Test
    public void testSlow() throws InterruptedException {
        for (int delay : new int[] { 210, 250 }) {
            scanned = "";
            for (char c : new char[] { start, '1', '2', '3', end }) {
                watcher.dispatchKeyEvent(new KeyEvent(ignore, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,  KeyEvent.VK_UNDEFINED, c));
                Thread.sleep(delay);
            }
            Assert.assertEquals("inter-char delay="+delay, "", scanned);
        }
    }
    
    public void sendString(String s)
    {  // no delay sending of string inside start/end characters
        watcher.dispatchKeyEvent(new KeyEvent(ignore, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,  KeyEvent.VK_UNDEFINED, start));
        for (char c : s.toCharArray())
            watcher.dispatchKeyEvent(new KeyEvent(ignore, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,  KeyEvent.VK_UNDEFINED, c));
        watcher.dispatchKeyEvent(new KeyEvent(ignore, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,  KeyEvent.VK_UNDEFINED, end));        
    }
    
    @Test
    public void longBarcodes() throws InterruptedException {
        String    okay = "12345678901234567890";
        String toolong = "123456789012345678901";
        
        scanned = "";
        sendString(okay);
        Assert.assertEquals("long but okay", okay, scanned);

        scanned = "";
        sendString(toolong);
        Assert.assertEquals("too long", "", scanned);
    }
    
    @Test
    public void doubleStart() throws InterruptedException {
        for (char c : new char[] { start, '1', start, '1', '2', '3', end })
            watcher.dispatchKeyEvent(new KeyEvent(ignore, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,  KeyEvent.VK_UNDEFINED, c));
        Assert.assertEquals("123", scanned);
    }
    
    @Test
    public void doubleEnd() throws InterruptedException {
        for (char c : new char[] { start, '1', '2', '3', end, end })
            watcher.dispatchKeyEvent(new KeyEvent(ignore, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,  KeyEvent.VK_UNDEFINED, c));
        Assert.assertEquals("123", scanned);
    }
    
    @Test
    public void nonTypedExtras() throws InterruptedException {
        for (char c : new char[] { start, '1', '2', '3', end, end }) {
            watcher.dispatchKeyEvent(new KeyEvent(ignore, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,  KeyEvent.VK_UNDEFINED, c));
            watcher.dispatchKeyEvent(new KeyEvent(ignore, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,  KeyEvent.VK_UNDEFINED, c));
            watcher.dispatchKeyEvent(new KeyEvent(ignore, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0,  KeyEvent.VK_UNDEFINED, c));
        }
        Assert.assertEquals("123", scanned);
    }
}
