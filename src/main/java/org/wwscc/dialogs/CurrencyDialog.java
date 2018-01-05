/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2009 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dialogs;

import org.wwscc.util.NumberField;

import net.miginfocom.swing.MigLayout;


/**
 */
public class CurrencyDialog extends BaseDialog<Double>
{
    NumberField amount = new NumberField(3, 2);

    public CurrencyDialog(String txt)
    {
        super(new MigLayout(""), false);
        mainPanel.add(label(txt, false), "wrap");
        mainPanel.add(amount, "wmin 150, growx, gapleft 10, wrap");
        result = null;
    }

    /**
     * Called after OK to verify data before closing.
     */
    @Override
    public boolean verifyData()
    {
        try {
            result = Double.parseDouble(amount.getText());
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    /**
     * Data is good, return it.
     */
    @Override
    public Double getResult()
    {
        return result;
    }
}


