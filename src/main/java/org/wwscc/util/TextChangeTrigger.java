/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.util;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;

public abstract class TextChangeTrigger implements DocumentListener
{
    private static final Logger log = Logger.getLogger(TextChangeTrigger.class.getCanonicalName());
    private boolean enabled = true;

    @Override
    public void changedUpdate(DocumentEvent e) { if (enabled) search(e); }
    @Override
    public void insertUpdate(DocumentEvent e) { if (enabled) search(e); }
    @Override
    public void removeUpdate(DocumentEvent e) { if (enabled) search(e); }

    public void enable(boolean e)
    {
        enabled = e;
    }

    public void search(DocumentEvent e)
    {
        Document d = e.getDocument();
        try { changedTo(d.getText(0, d.getLength())); }
        catch (Exception ex) { log.log(Level.INFO, "Search error: " + ex, ex); }
    }

    public abstract void changedTo(String txt);
}