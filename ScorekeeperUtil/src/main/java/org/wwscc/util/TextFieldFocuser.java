package org.wwscc.util;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.JTextField;

/**
 * select all when focusing, select none when losing focus
 */
public class TextFieldFocuser implements FocusListener
{
    @Override
    public void focusGained(FocusEvent e)
    {
        if (e.getComponent() instanceof JTextField)
            ((JTextField)e.getComponent()).selectAll();
    }

    @Override
    public void focusLost(FocusEvent e)
    {
        if (e.getComponent() instanceof JTextField)
            ((JTextField)e.getComponent()).select(0,0);
    }
}
