/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2009 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dialogs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import javax.swing.JRadioButton;
import net.miginfocom.swing.MigLayout;


/**
 */
public class PortDialog extends BaseDialog<String>
{
    //private static Logger log = Logger.getLogger(PortDialog.class.getCanonicalName());

    /**
     * Create the dialog
     * @param def the default radio to select
     * @param available the radios to show as available
     * @param unavailable  the radios to show as not available
     */
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

    /**
     * Called after OK to verify data before closing.
     */
    @Override
    public boolean verifyData()
    {
        result = getSelectedRadio();
        return (result != null) && !result.equals("");
    }


    /**
     * Data is good, return it.
     */
    @Override
    public String getResult()
    {
        return result;
    }
}


