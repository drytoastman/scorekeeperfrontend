/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.protimer;

import java.awt.Color;
import java.awt.Font;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;

import net.miginfocom.swing.MigLayout;

public class StopErrorPanel extends JPanel implements MessageListener 
{
	JLabel label;
    public StopErrorPanel()
	{
		super(new MigLayout("ins 0, al center"));
        Messenger.register(MT.RUN_MODE, this);
        Messenger.register(MT.ALIGN_MODE, this);
        Messenger.register(MT.PRO_RESET, this);
        		
        label = new JLabel("");
        label.setForeground(Color.RED);
        label.setFont(new Font("sansserif", Font.BOLD, 20));
        label.setHorizontalAlignment(JLabel.CENTER);
		add(label);
	}

	@Override
	public void event(MT type, Object o)
	{
        switch (type)
        {
            case ALIGN_MODE:
				label.setText("Align Mode");
                break;
            case PRO_RESET:
				label.setText("Received RESET notice from Pro hardware");
                break;
            case RUN_MODE:
				label.setText("");
                break;
		}
	}
}

