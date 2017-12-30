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
import org.wwscc.storage.MetaCar;
import org.wwscc.util.Resources;


class CarRenderer implements ListCellRenderer<Object>
{
    private static ImageIcon[][][] reg = new ImageIcon[2][2][3];
    static {
        reg[0][0][1] = new ImageIcon(Resources.loadImage("reg001.png"));
        reg[1][0][0] = new ImageIcon(Resources.loadImage("reg100.png"));
        reg[1][0][1] = new ImageIcon(Resources.loadImage("reg101.png"));
        reg[1][0][2] = new ImageIcon(Resources.loadImage("reg102.png"));
        reg[1][1][0] = new ImageIcon(Resources.loadImage("reg110.png"));
        reg[1][1][1] = new ImageIcon(Resources.loadImage("reg111.png"));
        reg[1][1][2] = new ImageIcon(Resources.loadImage("reg112.png"));
    }

    private MyPanel p = new MyPanel();

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
    {
        MetaCar c = (MetaCar)value;

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

        p.carinfo.setText(String.format("%s %s #%d", c.getClassCode(), Database.d.getEffectiveIndexStr(c), c.getNumber()));
        p.cardesc.setText(String.format("%s %s %s %s", c.getYear(), c.getMake(), c.getModel(), c.getColor()));

        int c0 = 0;
        int c1 = 0;
        int c2 = 0;

        if (c.isRegistered()) c0 = 1;
        if (c.hasPaid())      c1 = 1;
        if (c.isInRunOrder()) {
            c2 = 2;
        } else if (c.hasOtherActivity()) {
            c2 = 1;
        }
        p.status.setIcon(reg[c0][c1][c2]);
        p.setOpaque(true);
        return p;
    }
}

class MyPanel extends JPanel
{
    JLabel status;
    JLabel carinfo;
    JLabel cardesc;

    public MyPanel()
    {
        setLayout(new MigLayout("ins 5, gap 0", "[80!][100:500:10000]", "[15!][15!]"));
        setBorder(new UnderlineBorder(new Color(180, 180, 180)));

        status = new JLabel();
        status.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        status.setOpaque(false);
        add(status, "ay center, spany 2");

        carinfo = new JLabel();
        carinfo.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        add(carinfo, "wrap");

        cardesc = new JLabel();
        cardesc.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        add(cardesc, "");
    }

    @Override
    public void setForeground(Color f)
    {
        super.setForeground(f);
        if (status != null) status.setForeground(f);
        if (carinfo != null) carinfo.setForeground(f);
        if (cardesc != null) cardesc.setForeground(f);
    }

    @Override
    public void setBackground(Color f)
    {
        super.setBackground(f);
        if (status != null) status.setBackground(f);
        if (carinfo != null) carinfo.setBackground(f);
        if (cardesc != null) cardesc.setBackground(f);
    }
}
