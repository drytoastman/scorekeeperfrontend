/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.components;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListModel;
import javax.swing.UIManager;
import org.wwscc.storage.Car;
import org.wwscc.storage.DecoratedCar;
import org.wwscc.storage.Driver;

import net.miginfocom.swing.MigLayout;

public class ListRenderers extends DefaultListCellRenderer
{
    Color offSelect = new Color(200, 200, 200);

    protected void setupPanel(JList<?> list, JPanel p, boolean isSelected, boolean cellHasFocus)
    {
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
    }

    public static class DriverRenderer extends ListRenderers
    {
        private DriverPanel p = new DriverPanel();

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
        {
            Driver d = (Driver)value;
            setupPanel(list, p, isSelected, cellHasFocus);
            p.name.setText(d.getFullName());
            p.barcode.setText(d.getBarcode());
            return p;
        }

        class DriverPanel extends JPanel
        {
            JLabel name;
            JLabel barcode;

            public DriverPanel()
            {
                setLayout(new MigLayout("fill, ins 1", "", ""));

                name = new JLabel();
                name.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
                name.setOpaque(false);
                add(name, "");

                barcode = new JLabel();
                barcode.setOpaque(true);
                add(barcode, "ax right");
            }

            @Override
            public void setForeground(Color f)
            {
                super.setForeground(f);
                if (name != null)    name.setForeground(f);
                if (barcode != null) barcode.setForeground(f);
            }

            @Override
            public void setBackground(Color f)
            {
                super.setBackground(f);
                if (name != null)    name.setBackground(f);
                if (barcode != null) barcode.setBackground(f);
            }
        }
    }


    public static class DecoratedCarRenderer extends ListRenderers
    {
        private static Font codeFont = new Font(Font.SANS_SERIF, Font.BOLD, 13);
        private static Font descFont = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        private static Color inUseColor = new Color(120, 100, 120);
        private CarPanel p = new CarPanel();
        private FontMetrics codeMetrics = p.getFontMetrics(codeFont);

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
        {
            ListModel<?> model = list.getModel();
            int leftwidth = 0;
            for (int ii = 0; ii < model.getSize(); ii++) {
                leftwidth = Math.max(leftwidth, codeMetrics.stringWidth(getCodesStr((Car)model.getElementAt(ii))));
            }
            p.setCodesLayoutWidth(leftwidth);

            DecoratedCar c = (DecoratedCar)value;
            setupPanel(list, p, isSelected, cellHasFocus);
            if (c.isInRunOrder()) {
                p.setForeground(inUseColor);
                p.setItalic(true);
            } else {
                p.setItalic(false);
            }
            p.codes.setText(getCodesStr(c));
            p.desc.setText(String.join(" ", c.getYear(), c.getModel(), c.getColor()));
            return p;
        }

        private String getCodesStr(Car c)
        {
            return String.join(" ", c.getClassCode(), c.getEffectiveIndexStr(), "#"+c.getNumber());
        }

        class CarPanel extends JPanel
        {
            JLabel codes;
            JLabel desc;

            public CarPanel()
            {
                setLayout(new MigLayout("fill, ins 1", "[120:120:120][0]", ""));

                codes = new JLabel();
                codes.setFont(codeFont);
                codes.setOpaque(false);
                add(codes, "");

                desc = new JLabel();
                desc.setFont(descFont);
                desc.setOpaque(true);
                add(desc, "ax right");
            }

            public void setCodesLayoutWidth(int width)
            {
                ((MigLayout)getLayout()).setColumnConstraints(String.format("[%d:%d:%d][0]", width, width, width));
            }

            public void setItalic(boolean b)
            {
                if (codes != null) codes.setFont(b ? codeFont.deriveFont(Font.ITALIC) : codeFont);
                if (desc  != null) desc.setFont( b ? descFont.deriveFont(Font.ITALIC) : descFont);
            }

            @Override
            public void setForeground(Color f)
            {
                super.setForeground(f);
                if (codes != null) codes.setForeground(f);
                if (desc != null) desc.setForeground(f);
            }

            @Override
            public void setBackground(Color f)
            {
                super.setBackground(f);
                if (codes != null) codes.setBackground(f);
                if (desc != null) desc.setBackground(f);
            }
        }
    }
}
