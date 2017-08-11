/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.wwscc.components;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.logging.Logger;
import javax.swing.JLabel;
import javax.swing.Timer;

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
		try {
			DatagramSocket s = new DatagramSocket();
			s.connect(InetAddress.getByAddress(new byte[]{1,1,1,1}), 0);
			setText("My IP: " + s.getLocalAddress().getHostAddress());
			s.close();
			return;
		} catch (SocketException | UnknownHostException se) {
			Logger.getLogger(MyIpLabel.class.getName()).info("My IP attempt 1 failed: " + se);
		} 
		
		try {
			setText("My IP: " + InetAddress.getLocalHost().getHostAddress());
			return;
		} catch (UnknownHostException ex) {
			Logger.getLogger(MyIpLabel.class.getName()).info("My IP attempt 2 failed: " + ex);
		}
		
		setText("My IP: Unknown");
	}
}
