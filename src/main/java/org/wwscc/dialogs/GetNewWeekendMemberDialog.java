/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dialogs;

import org.wwscc.storage.WeekendMember;
import org.wwscc.util.Prefs;

import net.miginfocom.swing.MigLayout;

public class GetNewWeekendMemberDialog extends BaseDialog<WeekendMember>
{
    private WeekendMember weekend;

    public GetNewWeekendMemberDialog(WeekendMember base)
    {
        super(new MigLayout("fill", "[][200]"), false);

        mainPanel.add(label("Worker Name", true), "right");
        mainPanel.add(entry("Worker", Prefs.getIssuer()), "left, growx, wrap");

        mainPanel.add(label("Worker Membership", true), "right");
        mainPanel.add(entry("WorkerMem", Prefs.getIssuerMem()), "left, growx, wrap");

        weekend = base;
    }

    /**
     * Called after OK to verify data before closing.
     */
    @Override
    public boolean verifyData()
    {
        return !(getEntryText("Worker").equals("") || getEntryText("WorkerMem").equals(""));
    }

    /**
     * OK was pressed, data was verified, now return it.
     */
    @Override
    public WeekendMember getResult()
    {
        Prefs.setIssuer(getEntryText("Worker"));
        Prefs.setIssuerMem(getEntryText("WorkerMem"));

        weekend.setIssuer(getEntryText("Worker"));
        weekend.setIssuerMem(getEntryText("WorkerMem"));
        return weekend;
    }
}
