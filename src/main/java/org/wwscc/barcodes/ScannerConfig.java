/*
 * This software is licensed under the GPLv3 license, included
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */
package org.wwscc.barcodes;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ScannerConfig
{
    private static final Logger log = Logger.getLogger(ScannerConfig.class.getCanonicalName());

    char stx;
    char etx;
    int delay;

    private ScannerConfig(char s, char e, int d)
    {
        stx = s;
        etx = e;
        delay = d;
    }

    public static ScannerConfig defaultFor(String type)
    {
        if (type == SerialPortBarcodeWatcher.TYPE)
            return new ScannerConfig('\uFFFF', '\r', 100);
        return new ScannerConfig('\002', '\003', 100);
    }

    public String encode()
    {
        return String.format("%d;%d;%d", (int)stx, (int)etx, delay);
    }

    public void decode(String s)
    {
        try
        {
            String p[] = s.split(";");
            stx = (char)Integer.parseInt(p[0]);
            etx = (char)Integer.parseInt(p[1]);
            delay = Integer.parseInt(p[2]);
        }
        catch (Exception e)
        {
            log.log(Level.INFO, "Failed to decode scanner config: {0}", e.getMessage());
        }
    }
}
