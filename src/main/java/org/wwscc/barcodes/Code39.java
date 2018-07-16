/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.barcodes;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JComponent;

import org.wwscc.util.Prefs;

/**
 */
public class Code39 extends JComponent implements Printable
{
    static protected final Map<Character, String> barChar = new HashMap<Character, String>();
    static
    {
        barChar.put('0', "nnnwwnwnn");
        barChar.put('1', "wnnwnnnnw");
        barChar.put('2', "nnwwnnnnw");
        barChar.put('3', "wnwwnnnnn");
        barChar.put('4', "nnnwwnnnw");
        barChar.put('5', "wnnwwnnnn");
        barChar.put('6', "nnwwwnnnn");
        barChar.put('7', "nnnwnnwnw");
        barChar.put('8', "wnnwnnwnn");
        barChar.put('9', "nnwwnnwnn");
        barChar.put('A', "wnnnnwnnw");
        barChar.put('B', "nnwnnwnnw");
        barChar.put('C', "wnwnnwnnn");
        barChar.put('D', "nnnnwwnnw");
        barChar.put('E', "wnnnwwnnn");
        barChar.put('F', "nnwnwwnnn");
        barChar.put('G', "nnnnnwwnw");
        barChar.put('H', "wnnnnwwnn");
        barChar.put('I', "nnwnnwwnn");
        barChar.put('J', "nnnnwwwnn");
        barChar.put('K', "wnnnnnnww");
        barChar.put('L', "nnwnnnnww");
        barChar.put('M', "wnwnnnnwn");
        barChar.put('N', "nnnnwnnww");
        barChar.put('O', "wnnnwnnwn");
        barChar.put('P', "nnwnwnnwn");
        barChar.put('Q', "nnnnnnwww");
        barChar.put('R', "wnnnnnwwn");
        barChar.put('S', "nnwnnnwwn");
        barChar.put('T', "nnnnwnwwn");
        barChar.put('U', "wwnnnnnnw");
        barChar.put('V', "nwwnnnnnw");
        barChar.put('W', "wwwnnnnnn");
        barChar.put('X', "nwnnwnnnw");
        barChar.put('Y', "wwnnwnnnn");
        barChar.put('Z', "nwwnwnnnn");
        barChar.put('*', "nwnnwnwnn");
        barChar.put('-', "nwnnnnwnw");
    }

    public static final int NARROW = 1;
    public static final int WIDE   = 3;
    public static final int SYMBOLWIDTH = (16 * NARROW);

    protected String label = "";
    protected String codestr = "";

    public Code39()
    {
        setMinimumSize(new Dimension(SYMBOLWIDTH*8, 50));
        clear();
    }

    public void clear()
    {
        label = "";
        codestr = "";
    }

    public void setValue(String code, String label) throws InvalidBarcodeException
    {
        for (char c : code.toCharArray()) {
            if (!barChar.containsKey(c)) {
                throw new InvalidBarcodeException();
            }
        }
        this.label = label;
        if (code.equals("")) {
            codestr = "";
        } else {
            codestr = String.format("*%s*", code.toUpperCase());
        }
    }

    @Override
    public void paintComponent(Graphics g)
    {
        if (codestr.isEmpty())
            return;

        int codeheight = (int)(getHeight() * 0.75);
        int codewidth  = codestr.length() * SYMBOLWIDTH;
        int xpos       = (getWidth() - codewidth)/2;

        g.setColor(Color.BLACK);
        for (Character c : codestr.toCharArray())
        {
            String seq = barChar.get(c);
            boolean draw = true;
            for (Character bar : seq.toCharArray())
            {
                int width = (int)((bar == 'n') ? NARROW : WIDE);
                if (draw) g.fillRect(xpos, 0, width, codeheight);
                xpos += width;
                draw = !draw;
            }
            xpos += NARROW; // inter character space
        }

        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        FontMetrics metrics = g.getFontMetrics(g.getFont());
        Rectangle2D bounds = new Rectangle();
        String printlabel = new String(label);
        int diff = 1;
        while (diff > 0) {
            bounds = metrics.getStringBounds(printlabel, g);
            diff = codewidth - (int)bounds.getWidth();
            if (diff > 0) {
                printlabel = " " + printlabel + " ";
            }
        }

        printlabel = "*  "+printlabel+"*";
        bounds = metrics.getStringBounds(printlabel, g);
        int x = (int)Math.max(0, (getWidth() - bounds.getWidth())/2);
        int y = getHeight() - 1;
        g.drawString(printlabel, x, y);
    }


    @Override
    public int print(Graphics g, PageFormat pf, int i) throws PrinterException
    {
        if (i > 0) return NO_SUCH_PAGE;
        Graphics2D g2 = (Graphics2D)g;

        // translate to printable area
        g2.translate(pf.getImageableX(), pf.getImageableY());

        // now attempt to scale if needed
        double scale = Math.min(pf.getImageableWidth() / getWidth(), pf.getImageableHeight() / getHeight());
        if ((scale > 1.05) || (scale < 0.95)) {
            g2.scale(scale, scale);
        }

        g2.translate(Prefs.getPrintLabelOffset(), 0); // PT2730, prints backwards and leaves a gap so we remove that gap here, user adjustable

        paint(g);
        return PAGE_EXISTS;
    }
}