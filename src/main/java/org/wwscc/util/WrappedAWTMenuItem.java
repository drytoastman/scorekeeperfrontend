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