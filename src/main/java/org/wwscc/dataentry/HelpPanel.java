/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dataentry;

import javax.swing.JLabel;

import org.wwscc.storage.Entrant;
import org.wwscc.storage.Run;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;

public class HelpPanel extends JLabel implements MessageListener
{
    private static final long serialVersionUID = -6376824946457087404L;

    public HelpPanel()
    {
        super("Use Tabbed Panels to add Entrants");
        setHorizontalAlignment(CENTER);
        setFont(getFont().deriveFont(12f));
        Messenger.register(MT.OBJECT_CLICKED, this);
        Messenger.register(MT.OBJECT_DCLICKED, this);
    }

    @Override
    public void event(MT type, Object data) {
        switch (type)
        {
            case OBJECT_CLICKED:
                if (data instanceof Entrant)
                    setText("Entrant: Ctrl-X or Delete to remove them, Drag to move them, Swap Entrant to change them");
                else if (data instanceof Run)
                    setText("Runs: Ctrl-X or Delete to cut, Ctrl-C to copy, Ctrl-V to paste");
                else
                    setText("Ctrl-V to paste a Run");
                break;
            case OBJECT_DCLICKED:
                if (data instanceof Entrant)
                    setText("Click Swap Entrant to change the entrant for the given runs");
                break;
        }
    }
}
