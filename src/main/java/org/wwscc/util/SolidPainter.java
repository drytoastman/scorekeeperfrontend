package org.wwscc.util;

import java.awt.Color;
import java.awt.Graphics2D;

import javax.swing.Painter;

/*
 * Just need a way to tell nimbus to paint a flat color
 */
public class SolidPainter implements Painter<Object>
{
    Color color;
    public SolidPainter(Color c) {
        color = c;
    }
    @Override
    public void paint(Graphics2D g, Object object, int width, int height) {
        g.setColor(color);
        g.fillRect(0, 0, width, height);
    }

}