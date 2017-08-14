/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.wwscc.components;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JLabel;
import javax.swing.Timer;

import org.wwscc.util.Network;

/**
 *
 * @author bwilson
 */
public class MyIpLabel extends JLabel implements ActionListener
{
	public MyIpLabel()
	{
		super("");
		setHorizontalAlignment(CENTER);
		actionPerformed(null);
		new Timer(3000, this).start();
	}

	@Override
	public void actionPerformed(ActionEvent e)
	{
		setText("My IP: " + Network.getPrimaryAddress().getHostAddress());
	}
}
