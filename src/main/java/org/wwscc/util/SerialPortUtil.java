package org.wwscc.util;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.wwscc.dialogs.PortDialog;
import gnu.io.CommPortIdentifier;

public class SerialPortUtil
{
    private static Logger log = Logger.getLogger(SerialPortUtil.class.getCanonicalName());

    public static String userPortSelection()
    {
        ArrayList<String> a, u;

        a = new ArrayList<String>();
        u = new ArrayList<String>();

        for (CommPortIdentifier cpi : scan())
        {
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
            log.info("There are no available serial ports to open");
        }

        PortDialog d = new PortDialog("", a, u);
        d.doDialog("Select COM Port", null);
        String s = d.getResult();
        if ((s == null) || (s.equals("")))
            return null;
        return s;
    }

    public static List<CommPortIdentifier> scan()
    {
        List<CommPortIdentifier> found = new ArrayList<CommPortIdentifier>();
        Enumeration<?> list = CommPortIdentifier.getPortIdentifiers();
        while (list.hasMoreElements())
        {
            CommPortIdentifier c = (CommPortIdentifier)list.nextElement();
            log.log(Level.FINE, "RXTX found {0}", c.getName());
            if (c.getPortType() == CommPortIdentifier.PORT_SERIAL)
                found.add(c);
        }

        return found;
    }

    public static void main(String args[])
    {
        System.out.println(userPortSelection());
    }
}
