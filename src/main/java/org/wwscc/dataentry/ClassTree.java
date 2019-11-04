/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dataentry;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.UUID;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.wwscc.components.CarTree;
import org.wwscc.storage.Database;
import org.wwscc.storage.Entrant;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;


public class ClassTree extends CarTree implements MessageListener, ActionListener
{
    public ClassTree()
    {
        super();
        addMouseListener(new DClickWatch());
        Messenger.register(MT.ENTRANTS_CHANGED, this);
        Messenger.register(MT.COURSE_CHANGED, this);
        Messenger.register(MT.RUNGROUP_CHANGED, this);

        registerKeyboardAction(
            this,
            "enter",
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JComponent.WHEN_FOCUSED
        );
    }

    private void processSelection()
    {
        TreePath tp = getSelectionPath();
        if (tp == null)
            return;

        DefaultMutableTreeNode lf = (DefaultMutableTreeNode)tp.getLastPathComponent();
        Object o = lf.getUserObject();
        if (o instanceof Entrant)
        {
            Entrant entrant = (Entrant)lf.getUserObject();
            Messenger.sendEvent(MT.CAR_ADD, entrant.getCarId());
        }
    }

    class DClickWatch extends MouseAdapter
    {
        @Override
        public void mouseClicked(MouseEvent e)
        {
            if (e.getClickCount() == 2)
            {
                processSelection();
            }
        }
    }

    @Override
    public void event(MT type, Object o)
    {
        switch (type)
        {
            case COURSE_CHANGED:
            case RUNGROUP_CHANGED:
            case ENTRANTS_CHANGED:
                List<Entrant> reg = Database.d.getRegisteredEntrants(DataEntry.state.getCurrentEventId());
                List<UUID> exclude;
                if (DataEntry.state.usingSessions())
                    exclude = Database.d.getCarIdsForRunGroup(DataEntry.state.getCurrentEventId(), DataEntry.state.getCurrentCourse(), DataEntry.state.getCurrentRunGroup());
                else
                    exclude = Database.d.getCarIdsForCourse(DataEntry.state.getCurrentEventId(), DataEntry.state.getCurrentCourse());
                makeTree(reg, exclude);
                break;
        }
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        if(e.getActionCommand().equals("enter"))
        {
            processSelection();
        }
    }
}
