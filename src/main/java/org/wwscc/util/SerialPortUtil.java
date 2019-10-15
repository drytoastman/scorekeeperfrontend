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
import java.util.Arrays;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.wwscc.dialogs.PortDialog;
/*
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener; */

public class SerialPortUtil
{
    private static Logger log = Logger.getLogger(SerialPortUtil.class.getCanonicalName());

    public static String userPortSelection()
    {
        ArrayList<String> a, u;

        a = new ArrayList<String>();
        u = new ArrayList<String>();


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
        public void close() { }
        public void write(String s) {}
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
        }
    }


    /*
     * Utility class for doing line based reads of serial data
     */

    public interface SerialLineListener
    {
        public void processLine(byte[] data);
    }

    public static class LineBasedSerialPort extends SerialBasic
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
                Messenger.sendEvent(MT.SERIAL_DEBUG_DATA, Arrays.copyOfRange(buf, count, count+ret));
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
