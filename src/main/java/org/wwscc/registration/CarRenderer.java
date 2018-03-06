package org.wwscc.registration;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import net.miginfocom.swing.MigLayout;

import org.wwscc.components.UnderlineBorder;
import org.wwscc.storage.Database;
import org.wwscc.storage.DecoratedCar;
import org.wwscc.util.Resources;


class CarRenderer implements ListCellRenderer<Object>
{
    private static ImageIcon[][] reg = new ImageIcon[2][3];
    static {
        reg[0][0] = new ImageIcon(Resources.loadImage("reg00.png"));
        reg[0][1] = new ImageIcon(Resources.loadImage("reg01.png"));
        reg[0][2] = new ImageIcon(Resources.loadImage("reg02.png"));
        reg[1][0] = new ImageIcon(Resources.loadImage("reg10.png"));
        reg[1][1] = new ImageIcon(Resources.loadImage("reg11.png"));
        reg[1][2] = new ImageIcon(Resources.loadImage("reg12.png"));
    }

    private MyPanel p = new MyPanel();

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

        p.payment.setText(String.format("$%.2f", c.getPaymentTotal()));
        p.carinfo.setText(String.format("%s %s #%d", c.getClassCode(), Database.d.getEffectiveIndexStr(c), c.getNumber()));
        p.cardesc.setText(String.format("%s %s %s %s", c.getYear(), c.getMake(), c.getModel(), c.getColor()));
        p.quickid.setText(c.getQuickEntryId());

        int c0 = 0;
        int c1 = 0;

        if (c.isRegistered()) c0 = 1;
        if (c.isInRunOrder()) {
            c1 = 2;
        } else if (c.hasOtherActivity()) {
            c1 = 1;
        }
        p.status.setIcon(reg[c0][c1]);
        p.setOpaque(true);
        return p;
    }
}

class MyPanel extends JPanel
{
    JLabel status;
    JLabel payment;
    JLabel carinfo;
    JLabel cardesc;
    JLabel quicklbl;
    JLabel quickid;

    public MyPanel()
    {
        setLayout(new MigLayout("ins 5, gapx 12, gapy 1", "[][40!][100:500:10000]", ""));
        setBorder(new UnderlineBorder(new Color(180, 180, 180)));

        status = new JLabel();
        status.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        status.setOpaque(false);
        add(status, "ay center, spany 3");

        payment = new JLabel();
        payment.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        add(payment, "ax right, spany 3");

        carinfo = new JLabel();
        carinfo.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        add(carinfo, "gap 0, wrap");

        cardesc = new JLabel();
        cardesc.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        add(cardesc, "gap 0, wrap");

        quicklbl = new JLabel("Quick  ");
        quicklbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        add(quicklbl, "gap 0, split");

        quickid = new JLabel("");
        quickid.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        add(quickid, "gap 0, wrap");
    }

    @Override
    public void setForeground(Color f)
    {
        super.setForeground(f);
        if (status != null) status.setForeground(f);
        if (carinfo != null) carinfo.setForeground(f);
        if (cardesc != null) cardesc.setForeground(f);
        if (quickid != null) quickid.setForeground(f);
    }

    @Override
    public void setBackground(Color f)
    {
        super.setBackground(f);
        if (status != null) status.setBackground(f);
        if (carinfo != null) carinfo.setBackground(f);
        if (cardesc != null) cardesc.setBackground(f);
        if (quickid != null) quickid.setBackground(f);
    }
}
