package org.wwscc.dialogs;

import javax.swing.JLabel;
import net.miginfocom.swing.MigLayout;

public class HoverMessage extends BaseDialog<Void>
{
    public HoverMessage(String msg)
    {
        super(new MigLayout(""), true);
        JLabel l = new JLabel(msg);
        l.setFont(l.getFont().deriveFont(16f));
        mainPanel.add(l, "");
        buttonPanel.removeAll();
    }
}
