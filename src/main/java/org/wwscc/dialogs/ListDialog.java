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

import net.miginfocom.swing.MigLayout;

/**
 * Simple dialog with a JList, could use JOptionPane but this allows for multiple selection.
 * @param <T>
 */
public class ListDialog<T> extends BaseDialog<List<T>>
{
    private static final Logger log = Logger.getLogger(ListDialog.class.getCanonicalName());

    /**
     * Create the dialog.
     * @param toplabel the label to show about the options
     * @param options the possible options to select
     */

    JList<T> theList;
    String selectAllWarning;

    public ListDialog(String toplabel, List<T> options)
    {
        super(new MigLayout("fill", "", "[grow 0][grow 100, fill]"), true);

        theList = new JList<T>(new Vector<T>(options));
        JScrollPane scroll = new JScrollPane(theList);

        mainPanel.add(label(toplabel, false), "wmin 200, wrap");
        mainPanel.add(scroll, "grow");
    }

    public ListDialog(String toplabel, List<T> options, String selectallwarning)
    {
        this(toplabel, options);
        selectAllWarning = selectallwarning;
    }



    /**
     * Called after OK to verify data before closing.
     */
    @Override
    public boolean verifyData()
    {
        if (selectAllWarning != null && allSelected()) {
            log.warning(selectAllWarning);
            return false;
        }
        return true;
    }

    /**
     * OK was pressed, data was verified, now return it.
     */
    @Override
    public List<T> getResult()
    {
        return theList.getSelectedValuesList();
    }

    public boolean allSelected()
    {
        return theList.getSelectedIndices().length == theList.getModel().getSize();
    }
}
