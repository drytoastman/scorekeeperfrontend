/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dialogs;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Collection;

import javax.swing.AbstractAction;
import javax.swing.FocusManager;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

import org.wwscc.storage.Database;
import org.wwscc.util.Prefs;

public class OpenSeriesAction extends AbstractAction
{
    Collection<String> watch;

    public OpenSeriesAction(Collection<String> watch)
    {
        super("Open Series");
        putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.CTRL_MASK));
        this.watch = watch;
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        String options[] = Database.d.getSeriesList().toArray(new String[0]);
        String series = (String)JOptionPane.showInputDialog(FocusManager.getCurrentManager().getActiveWindow(), "Select the series", "Series Selection", JOptionPane.QUESTION_MESSAGE, null, options, null);
        if (series == null)
            return;

        if (Database.openSeries(series, 0, watch))
        {
            Prefs.setSeries(series);
            return;
        }
    }
}
