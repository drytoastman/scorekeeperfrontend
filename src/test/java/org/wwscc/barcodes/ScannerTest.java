package org.wwscc.barcodes;

import java.awt.event.KeyEvent;
import java.util.UUID;

import javax.swing.JLabel;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wwscc.util.AppSetup;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;

public class ScannerTest {

    static char start = 2;
    static char end   = 3;
    static JLabel ignore = new JLabel();
    static String scanned = "";
    static Object object = null;
    static MessageListener listener = new MessageListener() {
        @Override
        public void event(MT type, Object data) {
            if (type == MT.BARCODE_SCANNED) {
                scanned = (String)data;
            } else if (type == MT.OBJECT_SCANNED) {
                object = data;
            }
        }
    };

    @BeforeClass
    public static void init() {
        AppSetup.unitLogging();
        Prefs.setTestMode();
        Prefs.setScannerConfig(KeyboardBarcodeWatcher.TYPE, ScannerConfig.defaultFor(KeyboardBarcodeWatcher.TYPE).encode());
        Prefs.setScannerConfig(SerialPortBarcodeWatcher.TYPE, ScannerConfig.defaultFor(SerialPortBarcodeWatcher.TYPE).encode());
        Messenger.setTestMode();
        Messenger.register(MT.BARCODE_SCANNED, listener);
        Messenger.register(MT.OBJECT_SCANNED, listener);
    }

    @AfterClass
    public static void fini() throws Exception {
        Messenger.unregister(MT.BARCODE_SCANNED, listener);
        Messenger.unregister(MT.OBJECT_SCANNED, listener);
    }

    WatcherBase watcher;

    @Before
    public void start() {
        watcher = new KeyboardBarcodeWatcher();
        scanned = "";
    }

    @Test
    public void testGood() throws InterruptedException {
        for (int delay : new int[] { 0, 100, 190 }) {
            scanned = "";
            for (char c : new char[] { start, '1', '2', '3', end }) {
                watcher.processEvent(new KeyEvent(ignore, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,  KeyEvent.VK_UNDEFINED, c));
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
                watcher.processEvent(new KeyEvent(ignore, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,  KeyEvent.VK_UNDEFINED, c));
                Thread.sleep(delay);
            }
            Assert.assertEquals("inter-char delay="+delay, "", scanned);
        }
    }

    public void sendString(String s)
    {  // no delay sending of string inside start/end characters
        watcher.processEvent(new KeyEvent(ignore, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,  KeyEvent.VK_UNDEFINED, start));
        for (char c : s.toCharArray())
            watcher.processEvent(new KeyEvent(ignore, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,  KeyEvent.VK_UNDEFINED, c));
        watcher.processEvent(new KeyEvent(ignore, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,  KeyEvent.VK_UNDEFINED, end));
    }

    @Test
    public void longBarcodes() {
        String lessthanuuid = "123456789012345678901234567890123456789";
        String toolong = "12345678901234567890123456789012345678901";

        scanned = "";
        sendString(lessthanuuid);
        Assert.assertEquals("long but okay", lessthanuuid, scanned);

        scanned = "";
        sendString(toolong);
        Assert.assertEquals("too long", "", scanned);
    }

    @Test
    public void encodedUUID() {
        object = null;
        scanned = "";
        sendString("0039147042491687017526368347237630738434");
        Assert.assertEquals("encoded uuid", UUID.fromString("1d737236-0709-11e8-b6d2-0242ac120002"), object);
        Assert.assertEquals("encoded uuid", "", scanned);

        object = null;
        sendString("0000000000000000000000000000000000000000");
        Assert.assertEquals("null uuid", UUID.fromString("00000000-0000-0000-0000-000000000000"), object);
        Assert.assertEquals("null uuid", "", scanned);

        object = null;
        sendString("0340282366920938463463374607431768211455");
        Assert.assertEquals("max uuid", UUID.fromString("FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF"), object);
        Assert.assertEquals("max uuid", "", scanned);
    }

    @Test
    public void doubleStart() {
        for (char c : new char[] { start, '1', start, '1', '2', '3', end })
            watcher.processEvent(new KeyEvent(ignore, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,  KeyEvent.VK_UNDEFINED, c));
        Assert.assertEquals("123", scanned);
    }

    @Test
    public void doubleEnd() {
        for (char c : new char[] { start, '1', '2', '3', end, end })
            watcher.processEvent(new KeyEvent(ignore, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,  KeyEvent.VK_UNDEFINED, c));
        Assert.assertEquals("123", scanned);
    }

    @Test
    public void nonTypedExtras() {
        for (char c : new char[] { start, '1', '2', '3', end, end }) {
            watcher.processEvent(new KeyEvent(ignore, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,  KeyEvent.VK_UNDEFINED, c));
            watcher.processEvent(new KeyEvent(ignore, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,  KeyEvent.VK_UNDEFINED, c));
            watcher.processEvent(new KeyEvent(ignore, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0,  KeyEvent.VK_UNDEFINED, c));
        }
        Assert.assertEquals("123", scanned);
    }
}
