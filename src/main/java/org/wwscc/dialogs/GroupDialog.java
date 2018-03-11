/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dialogs;

import net.miginfocom.swing.MigLayout;

public class GroupDialog extends BaseDialog<String[]>
{
    private static final int groupCount = 6;

    public GroupDialog()
    {
        super(new MigLayout("fill, gap 2"), true);
        for (int ii = 0; ii < groupCount; ii++) {
            String lbl = ""+(ii+1);
            mainPanel.add(label(lbl, true), "al right");
            mainPanel.add(checkbox(lbl, false), "al left, gap right 5");
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
    public String[] getResult()
    {
        int size = 0;
        for (int ii = 0; ii < groupCount; ii++)
            if (isChecked(""+(ii+1)))
                size++;

        result = new String[size];
        size = 0;
        for (int ii = 0; ii < groupCount; ii++)
            if (isChecked(""+(ii+1)))
                result[size++] = Integer.toString(ii+1);

        return result;
    }
}
