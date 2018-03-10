/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.util;

import java.awt.MenuItem;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.Action;

public class WrappedAWTMenuItem extends MenuItem implements PropertyChangeListener
{
    public WrappedAWTMenuItem(Action a)
    {
        super((String)a.getValue(Action.NAME));
        addActionListener(a);
        setEnabled(a.isEnabled());
        a.addPropertyChangeListener(this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
        switch (evt.getPropertyName()) {
            case Action.NAME:  setLabel((String)evt.getNewValue()); break;
            case "enabled": this.setEnabled((boolean)evt.getNewValue()); break;
        }
    }
}