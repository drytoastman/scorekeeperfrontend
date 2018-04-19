/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.barcodes;

import java.awt.Toolkit;
import java.util.regex.Pattern;

import javax.swing.text.*;

public class Code39Filter extends DocumentFilter
{
    Pattern valid;

    public Code39Filter()
    {
        valid = Pattern.compile("[0-9,A-Z]*");
    }

    private String processString(String input)
    {
        input = input.toUpperCase();
        if (valid.matcher(input).matches())
            return input;
        return null;
    }

    public void insertString(FilterBypass fb, int offs, String str, AttributeSet a) throws BadLocationException
    {
        str = processString(str);
        if (str != null)
            super.insertString(fb, offs, str, a);
        else
            Toolkit.getDefaultToolkit().beep();
    }

    public void replace(FilterBypass fb, int offs, int length, String str, AttributeSet a) throws BadLocationException
    {
        str = processString(str);
        if (str != null)
            super.replace(fb, offs, length, str, a);
        else
            Toolkit.getDefaultToolkit().beep();
    }
}

