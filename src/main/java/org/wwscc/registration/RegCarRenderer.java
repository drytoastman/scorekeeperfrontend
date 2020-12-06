/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.registration;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import net.miginfocom.swing.MigLayout;

import org.wwscc.storage.DecoratedCar;
import org.wwscc.storage.Payment;
import org.wwscc.util.Resources;

public class RegCarRenderer implements ListCellRenderer<Object>
{
    private static Color greenish   = new Color(10, 150, 10);
    private static ImageIcon star   = new ImageIcon(Resources.loadImage("star.png"));
    private static ImageIcon nostar = new ImageIcon(Resources.loadImage("nostar.png"));
    private static Color washout    = new Color(240, 240, 240, 200);
    private static Font infont      = new Font(Font.SANS_SERIF, Font.BOLD, 20);

    private CarPanel p;
    private boolean sessions;

    public RegCarRenderer(boolean usingSessions)
    {
        super();
        sessions = usingSessions;
        p = new CarPanel(sessions);
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
    {
        DecoratedCar c = (DecoratedCar)value;

        p.setBorder(UIManager.getBorder("List.cellNoFocusBorder"));
        p.setBackground(Color.WHITE);
        p.setForeground(list.getForeground());
        if (isSelected)
        {
            p.setBackground(UIManager.getColor("nimbusSelection"));
            p.setForeground(list.getSelectionForeground());
            if (cellHasFocus) {
                p.setBorder(UIManager.getBorder("List.focusCellHighlightBorder"));
            }
        }

        if (c == null) { return p; }

        p.setPayments(c.getPayments());
        p.carinfo.setText(String.format("%s %s #%d", c.getClassCode(), c.getEffectiveIndexStr(), c.getNumber()));
        p.cardesc.setText(String.format("%s %s %s %s", c.getYear(), c.getMake(), c.getModel(), c.getColor()));

        p.quicklbl.setVisible(true);
        String q = c.getQuickEntryId();
        p.quickid.setText(String.format("%s %s %s", q.substring(0, 3), q.substring(3, 6), q.substring(6,10)));

        p.inevent = c.isInAnyRunOrder();
        p.runs.setIcon(c.hasOtherActivity() ? star : nostar);

        p.reg.removeAll();
        for (String session : c.getSessions()) {
            if (session.length() > 0)
                p.reg.add(session, 14);
            else
                p.reg.add("\u2714", 18);
        }
        p.setOpaque(true);
        return p;
    }


    class RegPanel extends JPanel
    {
        public RegPanel()
        {
            setLayout(new MigLayout("flowy, ins 0, gapx 0, gapy 0", "[30!]"));
        }

        public void add(String label, int size)
        {
            JLabel lbl = new JLabel(label);
            lbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
            add(lbl);
        }
    }

    class CarPanel extends JPanel
    {
        RegPanel reg;
        JLabel runs;
        JLabel carinfo;
        JLabel cardesc;
        JLabel quicklbl;
        JLabel quickid;
        List<JLabel> paymentlabels;
        boolean inevent;
        boolean sessions;

        public CarPanel(boolean usingSessions)
        {
            setLayout(new MigLayout("ins 5, gapx 0, gapy 1", "[]10[]5[100:500:10000]", ""));
            sessions = usingSessions;

            reg = new RegPanel();
            reg.setOpaque(false);
            add(reg, "ay center, spany 3");

            runs = new JLabel("");
            runs.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            add(runs, "ay top, ax right, gap 0, spany 3");

            carinfo = new JLabel();
            carinfo.setFont(new Font(Font.SANS_SERIF, sessions ? Font.PLAIN : Font.BOLD, 12));
            add(carinfo, "gap 0, wrap");

            cardesc = new JLabel();
            cardesc.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            add(cardesc, "gap 0, wrap");

            quicklbl = new JLabel("Quick  ");
            quicklbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            add(quicklbl, "gap 0, split");

            quickid = new JLabel("");
            quickid.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            add(quickid, "gap 0, gapbottom 5, wrap");

            paymentlabels = new ArrayList<JLabel>();
        }

        public void paint(Graphics g)
        {
            ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            super.paint(g);
            if (inevent) {
                g.setColor(washout);
                g.fillRect(0, 0, getWidth(), getHeight());

                g.setColor(Color.BLACK);
                g.setFont(infont);
                FontMetrics metrics = g.getFontMetrics(infont);
                g.drawString("In Event", 10, ((g.getClipBounds().height - metrics.getHeight()) / 2) + metrics.getAscent());
            }
        }

        @Override
        public void setForeground(Color f)
        {
            super.setForeground(f);
            if (reg != null)    reg.setForeground(f);
            if (carinfo != null) carinfo.setForeground(f);
            if (cardesc != null) cardesc.setForeground(f);
            if (quickid != null) quickid.setForeground(f);
        }

        @Override
        public void setBackground(Color f)
        {
            super.setBackground(f);
            if (reg != null)    reg.setBackground(f);
            if (carinfo != null) carinfo.setBackground(f);
            if (cardesc != null) cardesc.setBackground(f);
            if (quickid != null) quickid.setBackground(f);
        }

        public void setPayments(List<Payment> payments)
        {
            int ii;
            for (ii = 0; ii < payments.size(); ii++) {
                if (paymentlabels.size() <= ii) {
                    JLabel l = new JLabel("");
                    l.setForeground(greenish);
                    add(l, "skip 2, wrap");
                    paymentlabels.add(l);
                }
                Payment p = payments.get(ii);
                paymentlabels.get(ii).setText(String.format("$%.2f %s", p.getAmount(), p.getItemName()));
                paymentlabels.get(ii).setVisible(true);
            }

            for (; ii < paymentlabels.size(); ii++) {
                paymentlabels.get(ii).setText("");
                paymentlabels.get(ii).setVisible(false);
            }
        }
    }
}