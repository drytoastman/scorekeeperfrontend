/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */


package org.wwscc.util;

import javax.swing.*;
import javax.swing.text.*;

public class NumberField extends JTextField
{
    public NumberField(int intsize, int decimalsize)
    {
        setHorizontalAlignment(JTextField.LEADING);
        AbstractDocument doc = (AbstractDocument)getDocument();

        if (decimalsize > 0)
            doc.setDocumentFilter(new EasyNumFilter(intsize, decimalsize));
        else
            doc.setDocumentFilter(new EasyNumFilter(intsize));
    }
}

