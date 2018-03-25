/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dialogs;

import net.miginfocom.swing.MigLayout;

public class WeekendMemberDialog extends BaseDialog<Integer>
{
    public WeekendMemberDialog()
    {
        super(new MigLayout("fill, gap 2"), true);
        mainPanel.add(label("Region", true), "al right");
        mainPanel.add(entry("Region", "date"), "al left, gap right 5");
        mainPanel.add(entry("Worker", "date"), "al left, gap right 5");
        mainPanel.add(entry("Worker", "date"), "al left, gap right 5");
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
    public Integer getResult()
    {
        return 0;
    }
}
