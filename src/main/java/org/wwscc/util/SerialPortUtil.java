/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.wwscc.dialogs.PortDialog;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

public class SerialPortUtil
{
    private static Logger log = Logger.getLogger(SerialPortUtil.class.getCanonicalName());

    /*
     * Generic functions for opening a port.
     */

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

        if (a.isEmpty())
        {
            log.warning("\bThere are no available serial ports to open");
            return null;
        }

        PortDialog d = new PortDialog("", a, u);
        d.doDialog("Select COM Port", null);
        String s = d.getResult();
        if ((s == null) || (s.equals("")))
            return null;
        return s;
    }

    public static SerialPort openPort(String name, SerialPortEventListener listener) throws Exception
    {
        return openPort(name, listener, 9600, 3000, 30);
    }

    public static SerialPort openPort(String name, SerialPortEventListener listener, int baud, int ctimeoutms, int rtimeoutsec) throws Exception
    {
        log.info("Opening port " + name);
        SerialPort port = CommPortIdentifier.getPortIdentifier(name).open("Scorekeeper-"+name, ctimeoutms);
        port.setSerialPortParams(baud, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
        port.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
        port.notifyOnDataAvailable(true);
        port.addEventListener(listener);
        if (rtimeoutsec > 0)
            port.enableReceiveTimeout(rtimeoutsec);
        return port;
    }


    /*
     * Utility class for doing line based reads of serial data
     */

    public interface SerialLineListener
    {
        public void processLine(byte[] data);
    }

    public static class LineBasedSerialPort implements SerialPortEventListener
    {
        SerialPort port;
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
            port = openPort(name, this, baud, ctimeoutms, rtimeoutsec);
        }

        public void close()
        {
            port.close();
        }

        public void write(String s) throws IOException
        {
            port.getOutputStream().write(s.getBytes());
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
            } catch (IOException ex) {
                log.log(Level.WARNING, "serial port read error: " + ex, ex);
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
            in.read(buf, count, size);
            count += size;
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
