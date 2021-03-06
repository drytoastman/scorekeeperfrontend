/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.barcodes;

import java.util.Arrays;
import java.util.List;
import net.miginfocom.swing.MigLayout;
import org.wwscc.dialogs.BaseDialog;


/**
 * Core functions for all dialogs.
 */
public class ScannerOptionsDialog extends BaseDialog<ScannerConfig>
{
    //private static final Logger log = Logger.getLogger(ScannerOptionsDialog.class.getCanonicalName());
    private static List<String>    soptions = Arrays.asList(new String[]    { "<None>", "STX",  "ETX"  });
    private static List<Character> smatches = Arrays.asList(new Character[] { '\uFFFF', '\002', '\003' });
    private static List<String>    eoptions = Arrays.asList(new String[]    { "STX",  "ETX",  "CR", "NL" });
    private static List<Character> ematches = Arrays.asList(new Character[] { '\002', '\003', '\r', '\n' });

    public ScannerOptionsDialog(ScannerConfig config)
    {
        super(new MigLayout(""), true);

        result = config;
        mainPanel.add(label("Start Character", true), "");
        mainPanel.add(select("stx", soptions.get(smatches.indexOf(config.stx)), soptions, null), "growx, wrap");
        mainPanel.add(label("End Character", true), "");
        mainPanel.add(select("etx", eoptions.get(ematches.indexOf(config.etx)), eoptions, null), "growx, wrap");
        mainPanel.add(label("Max Delay (ms)", true), "");
        mainPanel.add(ientry("delay", config.delay), "growx, wrap");
    }

    @Override
    public boolean verifyData()
    {
        return true;
    }

    /**
     * OK was pressed, data was verified, now return it.
     */
    @Override
    public ScannerConfig getResult()
    {
        result.stx = smatches.get(soptions.indexOf(getSelect("stx")));
        result.etx = ematches.get(eoptions.indexOf(getSelect("etx")));
        result.delay = getEntryInt("delay");
        return result;
    }
}
