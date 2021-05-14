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

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;

import net.miginfocom.swing.MigLayout;

public class NipErrorPanel extends JPanel implements MessageListener 
{
	JLabel label;
	JButton close;

    public NipErrorPanel()
	{
		super(new MigLayout("ins 0, al center"));
		Messenger.register(MT.NIP_ERROR, this);
        
        label = new JLabel("");
        label.setForeground(Color.RED);
        label.setFont(new Font("sansserif", Font.BOLD, 14));
        label.setHorizontalAlignment(JLabel.CENTER);
		add(label);

        close = new JButton("clear");
		close.setFont(new Font("sansserif", Font.PLAIN, 12));
		close.addActionListener(e -> setVisible(false));
		add(close);
		setVisible(false);
	}

	@Override
	public void event(MT type, Object o)
	{
        if (o != null) {
            label.setText("Hardware state mismatch: " + o);
            setVisible(true);
        } else {
            setVisible(false);
		}
	}
}

