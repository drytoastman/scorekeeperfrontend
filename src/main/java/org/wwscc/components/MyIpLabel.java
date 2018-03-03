/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.components;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.InetAddress;

import javax.swing.JLabel;
import javax.swing.Timer;

import org.wwscc.util.Network;

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
        InetAddress a = Network.getPrimaryAddress();
        setText("My IP: " + ((a != null) ? a.getHostAddress() : "network down"));
    }
}
