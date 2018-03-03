/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.components;

import java.net.InetAddress;

import javax.swing.JLabel;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.Network;

public class MyServerLabel extends JLabel implements MessageListener
{
    public MyServerLabel()
    {
        super("Server Uninitialized");
        setHorizontalAlignment(CENTER);
        Messenger.register(MT.TIMER_SERVICE_LISTENING, this);
        Messenger.register(MT.TIMER_SERVICE_NOTLISTENING, this);
    }

    @Override
    public void event(MT type, Object o)
    {
        switch (type)
        {
            case TIMER_SERVICE_LISTENING:
                Object a[] = (Object[])o;
                InetAddress ip = Network.getPrimaryAddress();
                if (a != null)
                    setText("Server On: " + ip.getHostAddress() + ":" + a[1]);
                else
                    setText("Server On: No Network");
                break;
            case TIMER_SERVICE_NOTLISTENING:
                setText("Server Off");
                break;
        }
    }
}
