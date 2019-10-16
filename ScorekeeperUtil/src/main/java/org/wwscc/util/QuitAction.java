/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.util;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

public class QuitAction extends AbstractAction
{
    public QuitAction()
    {
        super("Quit");
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        System.exit(0);
    }
}
