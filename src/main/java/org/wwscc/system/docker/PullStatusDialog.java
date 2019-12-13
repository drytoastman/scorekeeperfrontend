/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2019 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system.docker;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;

import org.wwscc.dialogs.BaseDialog;
import net.miginfocom.swing.MigLayout;

public class PullStatusDialog extends BaseDialog<Void>
{
    JLabel header;
    Map<String, JProgressBar> bars;

    public PullStatusDialog(String name)
    {
        super(new MigLayout("fill, gap 0", "[][fill,200:200:200]"), false);
        bars = new HashMap<>();
        header = new JLabel("Pulling " + name + " layers");
        mainPanel.add(header, "gapbottom 10, spanx 2, wrap");
        buttonPanel.remove(ok);
    }

    private JProgressBar newBar(String id)
    {
        JProgressBar bar = new JProgressBar(SwingConstants.HORIZONTAL);
        bar.setStringPainted(true);
        bar.setForeground(Color.GRAY);

        JLabel idlabel = new JLabel(id);
        idlabel.setForeground(Color.DARK_GRAY);

        mainPanel.add(idlabel, "gapright 10");
        mainPanel.add(bar, "growx, wrap");
        repack();
        return bar;
    }

    public void setStatus(String id, String status)
    {
        JProgressBar bar = bars.computeIfAbsent(id, x -> newBar(x));
        bar.setValue(0);
        bar.setString(status);
    }

    public void setStatus(String id, int current, int total, String status)
    {
        JProgressBar bar = bars.computeIfAbsent(id, x -> newBar(x));
        bar.setMaximum(total);
        bar.setValue(current);
        bar.setString(status);
    }

    @Override
    public boolean verifyData()
    {
        return true;
    }
}
