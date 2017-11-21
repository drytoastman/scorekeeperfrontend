/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dialogs;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;

/**
 * Core functions for all dialogs.
 */
public class SeriesDialog extends BaseDialog<List<String>>
{
    /**
     * Create the dialog.
     * @param toplabel the label to show about the series list
     * @param seriesoptions the possible series to select
     */
    public SeriesDialog(String toplabel, String[] seriesoptions)
    {
        super(new MigLayout("", ""), true);
        mainPanel.add(label(toplabel, false), "wrap");

        JPanel sub = new JPanel(new MigLayout());
        mainPanel.add(sub, "center");
        for (String s : seriesoptions)
        {
            sub.add(checkbox(s, false), "split");
            sub.add(label(s, true), "wrap");
        }
    }

    /**
     * Called after OK to verify data before closing.
     */
    @Override
    public boolean verifyData()
    {
        return true;
    }

    /**
     * OK was pressed, data was verified, now return it.
     */
    @Override
    public List<String> getResult()
    {
        result = new ArrayList<String>();
        for (String s : checks.keySet())
        {
            if (isChecked(s))
                result.add(s);
        }
        return result;
    }

    public boolean allSelected()
    {
        for (String s : checks.keySet())
            if (!isChecked(s))
                return false;
        return true;
    }
}
