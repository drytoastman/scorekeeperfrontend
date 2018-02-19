/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.barcodes;

import java.awt.event.KeyEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JLabel;

import org.wwscc.util.SerialPortUtil;

import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

/**
 * Special watcher version for intercepting keypresses from the user interface.
 */
public class SerialPortBarcodeWatcher extends WatcherBase implements SerialPortEventListener
{
    public static final String TYPE = "SerialPort";
    private static final Logger log = Logger.getLogger(SerialPortBarcodeWatcher.class.getCanonicalName());
    private static JLabel dummy = new JLabel();
    protected String portName;
    protected SerialPort port;

    public SerialPortBarcodeWatcher(String portName)
    {
        super(TYPE);
        this.portName = portName;
    }

    @Override
    public void start()
    {
        try {
            port = SerialPortUtil.openPort(portName, this);
        } catch (Exception e) {
            log.log(Level.WARNING, "\bFailed to open serial port: " + e, e);
        }
    }

    @Override
    public void stop()
    {
        if (port != null)
            port.close();
        port = null;
    }

    /**
     * For the serial port, we can just drop it all
     */
    @Override
    protected void dumpQueue(int count)
    {
        queue.clear();
    }

    /**
     * Process each available serial character in turn
     */
    @Override
    public void serialEvent(SerialPortEvent e)
    {
        if (e.getEventType() != SerialPortEvent.DATA_AVAILABLE) return;
        try {
            long ms = System.currentTimeMillis();
            while (true) {
                int data = port.getInputStream().read();
                if (data < 0) return;
                processEvent(new KeyEvent(dummy, KeyEvent.KEY_TYPED, ms, 0, KeyEvent.VK_UNDEFINED, (char)data));
            }
        } catch (Exception ex) {
            log.log(Level.WARNING, "serial port read error: " + ex, ex);
        }
    }
}
