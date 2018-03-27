/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dialogs;

import net.miginfocom.swing.MigLayout;

public class GetNewWeekendMemberDialog extends BaseDialog<Integer>
{
    public GetNewWeekendMemberDialog()
    {
        super(new MigLayout("fill", "[][200]"), true);
        mainPanel.add(label("Region", true), "right");
        mainPanel.add(entry("Region", ""), "left, growx, wrap");

        mainPanel.add(label("Worker", true), "right");
        mainPanel.add(entry("Worker", ""), "left, growx, wrap");

        mainPanel.add(label("Worker #", true), "right");
        mainPanel.add(entry("Worker#", ""), "left, growx, wrap");

        mainPanel.add(label("Region", true), "right");
        mainPanel.add(entry("Region", ""), "left, growx, wrap");
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
