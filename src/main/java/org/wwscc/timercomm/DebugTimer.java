/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.timercomm;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;

import org.wwscc.storage.Run;
import org.wwscc.util.Discovery;
import org.wwscc.util.AppSetup;
import org.wwscc.util.TimeTextField;

/**
 * Simple source to send debug times to listeners.
 */
public class DebugTimer extends JPanel
{
    private static Logger log = Logger.getLogger(DebugTimer.class.getCanonicalName());

    JButton defaultButton;
    TimeTextField tf;
    TimerServer server;

    public DebugTimer() throws IOException
    {
        super(new MigLayout());

        tf = new TimeTextField("123.456", 6);

        defaultButton = new JButton("Send");
        defaultButton.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                server.sendRun(new Run(DebugTimer.this.tf.getTime()));
            }
        });

        add(tf, "w 200, wrap");
        add(defaultButton, "w 200, wrap");

        server = new TimerServer(Discovery.BWTIMER_TYPE);
        server.start();
    }


    public static void main(String args[])
    {
        try
        {
            AppSetup.appSetup("debugtimer");
            DebugTimer t = new DebugTimer();
            JFrame f = new JFrame("DebugTimer");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.getContentPane().add(t);
            f.pack();
            f.setVisible(true);
            t.getRootPane().setDefaultButton(t.defaultButton);
        }
        catch (Throwable e)
        {
            log.log(Level.SEVERE, "\bTimer stopped: " + e, e);
            e.printStackTrace();
        }
    }
}
