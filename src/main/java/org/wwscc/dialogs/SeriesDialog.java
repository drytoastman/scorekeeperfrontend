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
import javax.swing.JLabel;
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
        super(new MigLayout("", "[50%][50%]"), true);
        JLabel title = new JLabel(toplabel);
        title.setFont(title.getFont().deriveFont(13.0f));
        mainPanel.add(title, "spanx 2, gapbottom 5, wrap");
		for (String s : seriesoptions)
		{
		    JLabel l = new JLabel(s);
		    l.setFont(l.getFont().deriveFont(12.0f));
		    mainPanel.add(l, "right");
		    mainPanel.add(checkbox(s, false), "wrap");
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
