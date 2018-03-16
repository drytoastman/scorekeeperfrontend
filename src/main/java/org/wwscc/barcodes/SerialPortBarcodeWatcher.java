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

import org.wwscc.util.SerialPortUtil.CharacterBasedSerialPort;

/**
 * Special watcher version for intercepting keypresses from the user interface.
 */
public class SerialPortBarcodeWatcher extends WatcherBase
{
    public static final String TYPE = "SerialPort";
    private static final Logger log = Logger.getLogger(SerialPortBarcodeWatcher.class.getCanonicalName());
    private static JLabel dummy = new JLabel();
    protected String portName;
    protected CharacterBasedSerialPort port;

    public SerialPortBarcodeWatcher(String portName)
    {
        super(TYPE);
        this.portName = portName;
    }

    @Override
    public void start()
    {
        try {
            port = new CharacterBasedSerialPort(portName, ch ->
                processEvent(new KeyEvent(dummy, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, ch)));
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

    /** For the serial port, we can just drop it all */
    @Override
    protected void dumpQueue(int count)
    {
        queue.clear();
    }
}
