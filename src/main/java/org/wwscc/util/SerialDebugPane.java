/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2013 Brett Wilson.
 * All rights reserved.
 */


package org.wwscc.util;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;


public class SerialDebugPane extends JFrame implements MessageListener
{
    private static final Logger log = Logger.getLogger(SerialDebugPane.class.getCanonicalName());

    JScrollPane scroll;
    JTextPane text;
    StyledDocument doc;
    SimpleAttributeSet character, hex, eol;

    private static SerialDebugPane window;
    public static void display()
    {
        if (window == null) {
            window = new SerialDebugPane();
            window.setDefaultCloseOperation(HIDE_ON_CLOSE);
            window.setSize(640, 480);
            window.setVisible(true);
        } else {
            window.setVisible(true);
            window.toFront();
            window.repaint();
        }
    }

    private SerialDebugPane()
    {
        super("Serial Debug Window");
        text = new JTextPane();
        text.setEditable(false);
        doc = text.getStyledDocument();

        scroll = new JScrollPane(text);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        add(scroll);

        character = new SimpleAttributeSet();
        StyleConstants.setForeground(character, Color.BLACK);
        StyleConstants.setBackground(character, Color.WHITE);
        StyleConstants.setBold(character, true);

        hex = new SimpleAttributeSet();
        StyleConstants.setForeground(hex, Color.GRAY);
        StyleConstants.setBackground(hex, Color.WHITE);
        StyleConstants.setBold(hex, false);

        eol = new SimpleAttributeSet();
        StyleConstants.setForeground(eol, Color.GRAY);
        StyleConstants.setBackground(eol, Color.WHITE);
        StyleConstants.setBold(eol, false);


        Messenger.register(MT.SERIAL_PORT_DEBUG_DATA, this);
    }

    private void newData(byte[] data)
    {
        try {
            for (int ii = 0; ii < data.length; ii++) {
                byte c = data[ii];
                byte next = (ii+1 < data.length) ? data[ii+1] : 0;

                if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                    doc.insertString(doc.getLength(), String.format("%c ", c), character);
                } else if (c == '\r') {
                    doc.insertString(doc.getLength(), next == '\n' ? "\\r " : "\\r\n", eol);
                } else if (c == '\n') {
                    doc.insertString(doc.getLength(), next == '\r' ? "\\n " : "\\n\n", eol);
                } else {
                    doc.insertString(doc.getLength(), String.format("0x%02x ", data[ii]), hex);
                }
            }
            scroll.scrollRectToVisible(new Rectangle(0, text.getBounds(null).height, 1, 1));
        } catch (BadLocationException be) {
            log.log(Level.WARNING, "", be);
        }
    }

    @Override
    public void event(MT type, Object o)
    {
        switch (type)
        {
            case SERIAL_PORT_DEBUG_DATA:
                if (o instanceof byte[])
                    newData((byte[])o);
                break;
        }
    }
}



