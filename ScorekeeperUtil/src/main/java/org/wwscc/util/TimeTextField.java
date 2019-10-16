/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.util;

import javax.swing.JTextField;
import javax.swing.text.AbstractDocument;

public class TimeTextField extends JTextField
{
	public TimeTextField(String initial, int cols)
	{
		super(initial, cols);
		setHorizontalAlignment(JTextField.LEADING);
		AbstractDocument doc = (AbstractDocument)getDocument();
		doc.setDocumentFilter(new EasyNumFilter(3, 3));
	}

	public double getTime()
	{
		try { return Double.parseDouble(getText()); }
		catch (NumberFormatException nfe) {}
		return Double.NaN;	
	}

	public void setTime(double t)
	{
		setText(NF.format(t));
	}
}

