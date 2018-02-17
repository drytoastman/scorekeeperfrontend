/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.timercomm;

import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.wwscc.storage.Run;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;


public class SerialDataInterface implements SerialPortEventListener
{
    private static Logger log = Logger.getLogger(SerialDataInterface.class.getCanonicalName());

    private String portName = null;
    private SerialPort port = null;
    private OutputStream os = null;
    private InputStream is = null;
    private BufferedReader reader = null;

    public SerialDataInterface(String name) throws Exception
    {
        log.info("Opening port " + name);
        portName = name;

        port = CommPortIdentifier.getPortIdentifier(name).open("TimerInterface-"+name, 3000);
        port.setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
        port.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);

        port.addEventListener(this);
        port.notifyOnDataAvailable(true);
        port.enableReceiveTimeout(30);

        os = port.getOutputStream();
        is = port.getInputStream();
        reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
    }

    public void write(String s) throws IOException
    {
        os.write(s.getBytes());
    }

    public void close() throws IOException
    {
        log.info("Closing port " + portName);
        os.close();
        is.close();
        port.close();
    }

    public void processData(String line)
    {
        switch ((int)line.charAt(0) & 0xFF)  // compare as unsigned
        {
            case 0x80:
                String strtime = new StringBuffer(line).reverse().toString();
                double time = Double.valueOf(strtime) / 1000;
                log.info("Finish time: " + time);
                Messenger.sendEvent(MT.SERIAL_TIMER_DATA, new Run(time));
                break;

            case 0xF0: log.info("Mode changed"); break;
            case 0x90: log.info("Car started"); break;
            case 0xA0: log.info("Timer restarted"); break;
            case 0xB0: log.info("Sensors disabled"); break;
            case 0xC0: log.info("Sensors enabled"); break;

            default:
                Messenger.sendEvent(MT.SERIAL_GENERIC_DATA, line);
        }
    }


    @Override
    public void serialEvent(SerialPortEvent e)
    {
        String line;
        if (e.getEventType() != SerialPortEvent.DATA_AVAILABLE) return;
        try {
            while ((line = reader.readLine()) != null)
                processData(line);
        } catch (IOException ex) {
            log.log(Level.WARNING, "serial port read error: " + ex, ex);
        }
    }
}
