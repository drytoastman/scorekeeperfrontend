/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2019 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JRadioButton;

import org.wwscc.dialogs.BaseDialog;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import net.miginfocom.swing.MigLayout;

public class SerialPortUtil
{
    private static Logger log = Logger.getLogger(SerialPortUtil.class.getCanonicalName());

    public static String userPortSelection()
    {
        ArrayList<String> a, u;

        a = new ArrayList<String>();
        u = new ArrayList<String>();

        for (SerialPort port : SerialPort.getCommPorts())
        {
            log.log(Level.FINE, "RXTX found {0}", port.getSystemPortName());
            if (port.openPort()) {
                port.closePort();
                a.add(port.getSystemPortName());
            } else {
                u.add(port.getSystemPortName());
            }
        }

        PortDialog d = new PortDialog("", a, u);
        d.doDialog("Select COM Port", null);
        String s = d.getResult();
        if ((s == null) || (s.equals("")))
            return null;
        return s;
    }

    static class PortDialog extends BaseDialog<String>
    {
        public PortDialog(String def, Collection<String> available, Collection<String> unavailable)
        {
            super(new MigLayout(""), false);

            ArrayList<String> ports = new ArrayList<String>();
            ports.addAll(available);
            ports.addAll(unavailable);
            Collections.sort(ports);

            for (String p : ports)
            {
                JRadioButton rb = radio(p);
                rb.addActionListener(e -> ok.setEnabled(verifyData()));
                mainPanel.add(rb, "w 150!, gapleft 20, wrap");
                if (unavailable.contains(p))
                    radioEnable(p, false);
            }

            if (ports.size() == 0) {
                mainPanel.add(label("No ports found", true), "spanx 2, center");
            } else if (available.size() == 0){
                mainPanel.add(label("All ports in use", true), "spanx 2, center");
            }

            result = null;
            setSelectedRadio(def);
            ok.setEnabled(verifyData());
        }

        @Override
        public boolean verifyData()
        {
            result = getSelectedRadio();
            return (result != null) && !result.equals("");
        }

        @Override
        public String getResult()
        {
            return result;
        }
    }


    /**
     * Base class for common serial port wrapping features
     */
    static abstract class SerialBasic implements SerialPortDataListener
    {
        SerialPort port;

        protected void openPort(String name, SerialPortDataListener listener) throws Exception
        {
            openPort(name, listener, 9600, 3000, 30);
        }

        protected void openPort(String name, SerialPortDataListener listener, int baud, int ctimeoutms, int rtimeoutsec) throws Exception
        {
            log.info("Opening port " + name);
            port = SerialPort.getCommPort(name);
            port.openPort(ctimeoutms);
            port.setBaudRate(baud);
            port.setNumDataBits(8);
            port.setNumStopBits(1);
            port.setParity(SerialPort.NO_PARITY);
            port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
            port.addDataListener(listener);
            if (rtimeoutsec > 0)
                port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, rtimeoutsec, 0);
            Messenger.sendEvent(MT.SERIAL_PORT_OPEN, name);
        }

        public void close()
        {
            if (port != null) {
                Messenger.sendEvent(MT.SERIAL_PORT_CLOSED, port.getSystemPortName());
                port.removeDataListener();
                port.closePort();
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

        abstract void newData(byte data[]);

        @Override
        public void serialEvent(SerialPortEvent ev)
        {
            if (ev.getEventType() != SerialPort.LISTENING_EVENT_DATA_RECEIVED) return;
            try {
                byte data[] = ev.getReceivedData();
                Messenger.sendEvent(MT.SERIAL_PORT_DEBUG_DATA, data);
                newData(data);
            } catch (Exception ioe) {
                log.log(Level.WARNING, "serial port read error: " + ioe, ioe);
                close();
            }
        }

        @Override
        public int getListeningEvents() {
            return  SerialPort.LISTENING_EVENT_DATA_RECEIVED;
        }
    }

    /*
     * Utility class for doing character based reads, wrapped so we can send proper events
     */
    public interface SerialCharacterListener
    {
        public void processChar(char data);
    }

    public static class CharacterBasedSerialPort extends SerialBasic
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
        public void newData(byte data[])
        {
            for (byte b : data) {
                charlistener.processChar((char)b);
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

    public static class LineBasedSerialPort extends SerialBasic implements SerialPortDataListener
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
        public void newData(byte data[])
        {
            byte line[];
            buffer.appendData(data);
            while ((line = buffer.getNextLine()) != null)
                linelistener.processLine(line);
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

        public void appendData(byte inbuf[])
        {
            int size = inbuf.length;
            if (size + count > buf.length) // increase buffer size at runtime if needed
            {
                byte[] newbuffer = new byte[buf.length*2];
                System.arraycopy(buf, 0, newbuffer, 0, count);
                buf = newbuffer;
                log.log(Level.INFO, "Increased byte buffer size to: {0}", buf.length);
            }
            for (int ii = 0; ii < inbuf.length; ii++, count++) {
                buf[count] = inbuf[ii];
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
}
