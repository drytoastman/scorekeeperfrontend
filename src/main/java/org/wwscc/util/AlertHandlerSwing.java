/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2019 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.util;

import java.util.logging.Level;
import javax.swing.FocusManager;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class AlertHandlerSwing extends AlertHandler
{
    @Override
    public void displayAlert(int level, String msg)
    {
        String title;
        int swingtype;
        if (level >= Level.SEVERE.intValue())
        {
            title = "Error";
            swingtype = JOptionPane.ERROR_MESSAGE;
        }
        else if (level >= Level.WARNING.intValue())
        {
            title = "Warning";
            swingtype = JOptionPane.WARNING_MESSAGE;
        }
        else
        {
            title = "Note";
            swingtype = JOptionPane.INFORMATION_MESSAGE;
        }

        final String swmsg = (msg.contains("\n")) ? "<HTML>" + msg.replace("\n", "<br>") + "</HTML>" : msg;
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(FocusManager.getCurrentManager().getActiveWindow(), swmsg, title, swingtype));
    }
}