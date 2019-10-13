/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2019 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.ArrayUtils;
import org.wwscc.dialogs.PortDialog;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

public class SerialPortUtil
{
    private static Logger log = Logger.getLogger(SerialPortUtil.class.getCanonicalName());

    public static String userPortSelection()
    {
        ArrayList<String> a, u;

        a = new ArrayList<String>();
        u = new ArrayList<String>();

        Enumeration<?> list = CommPortIdentifier.getPortIdentifiers();
        while (list.hasMoreElements())
        {
            CommPortIdentifier cpi = (CommPortIdentifier)list.nextElement();
            log.log(Level.FINE, "RXTX found {0}", cpi.getName());
            if (cpi.getPortType() != CommPortIdentifier.PORT_SERIAL)
                continue;

            try
            {
                cpi.open("Testing", 500).close();
                a.add(cpi.getName());
            }
            catch (Exception e)
            {
                u.add(cpi.getName());
            }
        }

        PortDialog d = new PortDialog("", a, u);
        d.doDialog("Select COM Port", null);
        String s = d.getResult();
        if ((s == null) || (s.equals("")))
            return null;
        return s;
    }

    /**
     * Base class for common serial port wrapping features
     */
    static class SerialBasic
    {
        SerialPort port;

        protected void openPort(String name, SerialPortEventListener listener) throws Exception
        {
            openPort(name, listener, 9600, 3000, 30);
        }

        protected void openPort(String name, SerialPortEventListener listener, int baud, int ctimeoutms, int rtimeoutsec) throws Exception
        {
            log.info("Opening port " + name);
            port = CommPortIdentifier.getPortIdentifier(name).open("Scorekeeper-"+name, ctimeoutms);
            port.setSerialPortParams(baud, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
            port.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
            port.notifyOnDataAvailable(true);
            port.addEventListener(listener);
            if (rtimeoutsec > 0)
                port.enableReceiveTimeout(rtimeoutsec);
            Messenger.sendEvent(MT.SERIAL_PORT_OPEN, name);
        }

        public void close()
        {
            if (port != null) {
                Messenger.sendEvent(MT.SERIAL_PORT_CLOSED, port.getName());
                port.removeEventListener();
                port.close();
                port = null;
            }
        }

        public void write(String s)
        {
            try {
                port.getOutputStream().write(s.getBytes());
            } catch (Exception ioe) {
                log.log(Level.WARNING, "serial port write error: " + ioe, ioe);
                close();
            }
        }
    }

    /*
     * Utility class for doing character based reads, wrapped so we can send proper events
     */
    public interface SerialCharacterListener
    {
        public void processChar(char data);
    }

    public static class CharacterBasedSerialPort extends SerialBasic implements SerialPortEventListener
    {
        SerialCharacterListener charlistener;

        public CharacterBasedSerialPort(String name, SerialCharacterListener lis) throws Exception
        {
            this(name, lis, 9600, 3000, 30);
        }

        public CharacterBasedSerialPort(String name, SerialCharacterListener lis, int baud, int ctimeoutms, int rtimeoutsec) throws Exception
        {
            charlistener = lis;
            openPort(name, this, baud, ctimeoutms, rtimeoutsec);
        }

        @Override
        public void serialEvent(SerialPortEvent ev)
        {
            if (ev.getEventType() != SerialPortEvent.DATA_AVAILABLE) return;
            try {
                int data = port.getInputStream().read();
                if (data < 0) return;
                Messenger.sendEvent(MT.SERIAL_DEBUG_DATA,  new byte[] { (byte)data });
                charlistener.processChar((char)data);
            } catch (Exception ioe) {
                log.log(Level.WARNING, "serial port read error: " + ioe, ioe);
                close();
            }
        }
    }


    /*
     * Utility class for doing line based reads of serial data
     */

    public interface SerialLineListener
    {
        public void processLine(byte[] data);
    }

    public static class LineBasedSerialPort extends SerialBasic implements SerialPortEventListener
    {
        LineBasedSerialBuffer buffer;
        SerialLineListener linelistener;

        public LineBasedSerialPort(String name, SerialLineListener lis) throws Exception
        {
            this(name, lis, 9600, 3000, 30);
        }

        public LineBasedSerialPort(String name, SerialLineListener lis, int baud, int ctimeoutms, int rtimeoutsec) throws Exception
        {
            buffer = new LineBasedSerialBuffer();
            linelistener = lis;
            openPort(name, this, baud, ctimeoutms, rtimeoutsec);
        }

        @Override
        public void serialEvent(SerialPortEvent ev)
        {
            byte[] line;
            if (ev.getEventType() != SerialPortEvent.DATA_AVAILABLE) return;
            try {
                buffer.readData(port.getInputStream());
                while ((line = buffer.getNextLine()) != null)
                    linelistener.processLine(line);
            } catch (Exception ex) {
                log.log(Level.WARNING, "serial port read error: " + ex, ex);
                close();
            }
        }
    }


    public static class LineBasedSerialBuffer
    {
        byte[] buf;
        int count;
        int search;

        public LineBasedSerialBuffer()
        {
            buf = new byte[128];
            count = 0;
            search = 0;
        }

        public void readData(InputStream in) throws IOException
        {
            int size = in.available();
            if (size + count > buf.length) // increase buffer size at runtime if needed
            {
                byte[] newbuffer = new byte[buf.length*2];
                System.arraycopy(buf, 0, newbuffer, 0, count);
                buf = newbuffer;
                log.log(Level.INFO, "Increased byte buffer size to: {0}", buf.length);
            }
            int ret = in.read(buf, count, size);
            if (ret > 0) {
                Messenger.sendEvent(MT.SERIAL_DEBUG_DATA, ArrayUtils.subarray(buf, count, count+ret));
                count += ret;
            }
        }

        public byte[] getNextLine()
        {
            for ( ; search < count; search++)
            {
                if ((buf[search] == '\r') || (buf[search] == '\n'))
                {
                    // copy out line from start of buffer
                    byte[] ret = new byte[search];
                    System.arraycopy(buf, 0, ret, 0, search);

                    // skip any \r or \n
                    while( ((buf[search] == '\r') || (buf[search] == '\n')) && (search < count))
                        search += 1;

                    // move from search back to start of buffer
                    System.arraycopy(buf, search, buf, 0, count-search);
                    count -= search;
                    search = 0;

                    if (ret.length > 0)
                        return ret;
                    return null;
                }
            }

            return null;
        }
    }

    public static void main(String args[])
    {
        System.out.println(userPortSelection());
    }
}
