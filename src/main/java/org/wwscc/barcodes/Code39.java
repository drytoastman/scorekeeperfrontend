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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JComponent;

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

    protected int codeWidth()
    {
        return SYMBOLWIDTH * codestr.length();
    }

    protected int necessaryWidth(Graphics g)
    {
        return (int)Math.max(codeWidth() + SYMBOLWIDTH*2,  // code with plus space for quiet zones
                             g.getFontMetrics(g.getFont()).getStringBounds(label, g).getWidth());
    }

    protected void draw(Graphics g)
    {
        if (codestr.isEmpty())
            return;
        drawBarcode(g);
        drawLabel((Graphics2D)g);
        drawQuietZoneHolders(g);
    }

    protected void drawBarcode(Graphics g)
    {
        int codeheight = (int)(getHeight() * 0.75);
        int sidespace  = getWidth() - codeWidth();
        int xpos       = sidespace > 0 ? sidespace/2 : 0;

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
    }

    protected void drawLabel(Graphics2D g)
    {
        int labelwidth = (int)g.getFontMetrics(g.getFont()).getStringBounds(label, g).getWidth();
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.drawString(label, (int)Math.max(0, (getWidth() - labelwidth)/2), getHeight() - 1);
    }

    protected void drawQuietZoneHolders(Graphics g)
    {
        g.setColor(Color.LIGHT_GRAY);
        g.drawString(".", 0, getHeight() - 1);
        g.drawString(".", getWidth()-2, getHeight() - 1);
    }

    @Override
    public void paintComponent(Graphics g)
    {
        draw(g);
    }

    @Override
    public int print(Graphics g, PageFormat pf, int i) throws PrinterException
    {
        if (i > 0) return NO_SUCH_PAGE;

        Graphics2D g2 = (Graphics2D)g;

        Code39 dup = new Code39();
        dup.codestr = codestr;
        dup.label = label;
        int width = necessaryWidth(g2);
        dup.setBounds(new Rectangle(0, 0, width, (int)pf.getImageableHeight()));

        // translate to printable area and shift out any extra whitespace
        g2.translate(pf.getImageableX(), pf.getImageableY());
        g2.translate(pf.getImageableWidth() - width, 0);

        dup.draw(g);
        return PAGE_EXISTS;
    }
}