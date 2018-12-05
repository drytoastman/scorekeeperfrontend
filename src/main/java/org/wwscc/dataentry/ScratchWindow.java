package org.wwscc.dataentry;

import javax.swing.JFrame;
import javax.swing.JScrollPane;

import org.wwscc.dataentry.tables.RunsTable;

public class ScratchWindow extends JFrame
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
