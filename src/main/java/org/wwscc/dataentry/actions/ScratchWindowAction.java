/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dataentry.actions;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;

import org.wwscc.dataentry.tables.RunsTable;

public class ScratchWindowAction extends AbstractAction
{
    public ScratchWindowAction()
    {
        super("Scratch Window");
        putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_W, ActionEvent.CTRL_MASK));
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        new ScratchWindow(40, 10);
    }

    static class ScratchWindow extends JFrame
    {
        public ScratchWindow(int rowcount, int colcount)
        {
            super("Copy/Paste Work Area");
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            getContentPane().add(new JScrollPane(new RunsTable(rowcount, colcount)));
            pack();
            setVisible(true);
        }
    }
}
