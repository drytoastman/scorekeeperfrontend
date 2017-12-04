/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dialogs;

import java.util.List;
import java.util.Vector;
import java.util.logging.Logger;

import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

import net.miginfocom.swing.MigLayout;

/**
 * Simple dialog with a JList, could use JOptionPane but this allows for multiple selection.
 */
public class ListDialog extends BaseDialog<List<String>>
{
    private static final Logger log = Logger.getLogger(ListDialog.class.getCanonicalName());

    /**
     * Create the dialog.
     * @param toplabel the label to show about the options
     * @param options the possible options to select
     */

    JList<String> theList;
    String deleteAllWarning;

    public ListDialog(String toplabel, List<String> options)
    {
        super(new MigLayout("", "", "[grow 0][grow 100]"), true);
        mainPanel.add(label(toplabel, false), "wrap");

        theList = new JList<String>(new Vector<String>(options));
        JScrollPane scroll = new JScrollPane(theList);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        mainPanel.add(scroll, "growx");
    }

    public ListDialog(String toplabel, List<String> options, String deleteallwarning)
    {
        this(toplabel, options);
        deleteAllWarning = deleteallwarning;
    }


    /**
     * Called after OK to verify data before closing.
     */
    @Override
    public boolean verifyData()
    {
        if (deleteAllWarning != null && allSelected()) {
            log.warning(deleteAllWarning);
            return false;
        }
        return true;
    }

    /**
     * OK was pressed, data was verified, now return it.
     */
    @Override
    public List<String> getResult()
    {
        return theList.getSelectedValuesList();
    }

    public boolean allSelected()
    {
        return theList.getSelectedIndices().length == theList.getModel().getSize();
    }
}
