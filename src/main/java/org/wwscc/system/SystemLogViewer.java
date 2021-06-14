/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2021 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

public class SystemLogViewer extends JFrame
{
    JTextPane text;
    JScrollPane scroll;

    public SystemLogViewer()
    {
        super("Scorekeeper System Warnings/Errors");
        text = new JTextPane() {
            public boolean getScrollableTracksViewportWidth() {
                return false;
            }
        };
        scroll = new JScrollPane(text);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setContentPane(new JScrollPane(text));
        pack();
    }

    protected void read(Path p) {
        try {
            StyledDocument doc = new DefaultStyledDocument();
            Style def = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
            StyleConstants.setFontFamily(def, "SansSerif");
            Style w = doc.addStyle("warning", def);
            Style e = doc.addStyle("error", def);

            StyleConstants.setForeground(w, new Color(206, 88, 0));
            StyleConstants.setForeground(e, new Color(206, 0, 0));

            for (String s : Files.readAllLines(p)) {
                String style = null;
                if (s.contains("WARNING")) style = "warning";
                if (s.contains("ERROR")) style = "error";
                if (style != null) doc.insertString(doc.getLength(), s + "\n", doc.getStyle(style));
            }
            text.setDocument(doc);
            text.setCaretPosition(doc.getLength());
        } catch (Exception ioe) {
            text.setText(ioe.toString());
        }
    }

    public static void show(Path filename)
    {
        SystemLogViewer v = new SystemLogViewer();
        v.setSize(1000, 600);
        v.setLocationByPlatform(true);
        v.setVisible(true);
        v.read(filename);
    }
}
