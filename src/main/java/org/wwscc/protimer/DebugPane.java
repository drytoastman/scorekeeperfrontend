/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2013 Brett Wilson.
 * All rights reserved.
 */


package org.wwscc.protimer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileNotFoundException;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;

import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;


public class DebugPane extends JPanel implements ActionListener, MessageListener
{
    private static final Logger log = Logger.getLogger(DebugPane.class.getCanonicalName());

    JTextPane text;
    JTextField input;
    JButton enter;

    public DebugPane() throws FileNotFoundException
    {
        super(new BorderLayout());

        text = new JTextPane();
        input = new JTextField(40);
        enter = new JButton("Send");
        enter.addActionListener(this);

        JScrollPane sp = new JScrollPane(text);
        sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        JPanel p = new JPanel();
        p.add(input);
        p.add(enter);

        add(p, BorderLayout.NORTH);
        add(sp, BorderLayout.CENTER);

        Messenger.register(MT.SERIAL_GENERIC_DATA, this);
        Messenger.register(MT.SENDING_SERIAL, this);
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        String s = input.getText();
        if (!s.equals(""))
            Messenger.sendEvent(MT.INPUT_TEXT, s);
        input.setText("");
    }

    private Color inColor  = new Color(20, 150, 20);
    private Color outColor = new Color(0, 0, 200);
    StyleContext sc = StyleContext.getDefaultStyleContext();
    AttributeSet aset = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.FontFamily, "Lucida Console");
    private void newText(String s, boolean in)
    {
        aset = sc.addAttribute(aset, StyleConstants.FontSize, 16);
        aset = sc.addAttribute(aset, StyleConstants.Foreground, in ? inColor : outColor);

        log.finer("dbp: " + s + ", " + in);
        text.setCaretPosition(text.getDocument().getLength());
        text.setCharacterAttributes(aset, false);
        text.replaceSelection(s + "\n");
        text.setCaretPosition(text.getDocument().getLength());
    }

    @Override
    public void event(MT type, Object o)
    {
        switch (type)
        {
            case SERIAL_GENERIC_DATA:
                newText(new String((byte[])o), true);
                break;

            case SENDING_SERIAL:
                newText((String)o, false);
                break;
        }
    }
}



