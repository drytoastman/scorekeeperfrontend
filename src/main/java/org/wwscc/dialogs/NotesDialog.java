/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dialogs;

import javax.swing.JTextPane;

import net.miginfocom.swing.MigLayout;

public class NotesDialog extends BaseDialog<String>
{
    JTextPane pane;

    public NotesDialog(String notes)
    {
        super(new MigLayout("fill, gap 2"), true);
        pane = new JTextPane();
        pane.setText(notes);
        mainPanel.add(pane, "h 300, w 300, grow");
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
    public String getResult()
    {
        return pane.getText();
    }
}
