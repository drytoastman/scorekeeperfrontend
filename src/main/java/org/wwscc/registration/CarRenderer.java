package org.wwscc.registration;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;

import net.miginfocom.swing.MigLayout;

import org.wwscc.components.UnderlineBorder;
import org.wwscc.storage.Database;
import org.wwscc.storage.MetaCar;


class CarRenderer implements ListCellRenderer<Object>
{
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
		
		p.status.setForeground(Color.BLACK);
		if (c.isInRunOrder())
		{
			p.status.setText("In Event");
		}
		else if (c.isRegistered())
		{
			if (c.hasPaid())
				p.status.setText("Reg/Paid");
			else
				p.status.setText("Registered");
		}
		else if (c.hasActivity())
		{
		    p.status.setForeground(Color.LIGHT_GRAY);
			p.status.setText("Other");
		}
		else
			p.status.setText("");
		
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
		setLayout(new MigLayout("ins 5, gap 0", "[85!][100:500:10000]", "[15!][15!]"));
		setBorder(new UnderlineBorder(new Color(180, 180, 180)));
		
		status = new JLabel();
		status.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
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
