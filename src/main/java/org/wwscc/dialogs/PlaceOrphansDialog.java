/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dialogs;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.wwscc.storage.Entrant;
import org.wwscc.util.HintTextField;

import net.miginfocom.swing.MigLayout;

public class PlaceOrphansDialog extends BaseDialog<List<UUID>>
{
    public PlaceOrphansDialog(List<Entrant> orphans)
    {
        super(new MigLayout("fill, w 500, gap 5, ins 5", "", ""), true);

        HintTextField pwf = new HintTextField("sets a new password");
        fields.put("plaintext", pwf);

        mainPanel.add(label("Which cars do you wish to place into the current rungroup?", false), "spanx 5, wrap");
        for (Entrant e : orphans) {
            mainPanel.add(checkbox(e.getCarId().toString(), false), "split 3");
            mainPanel.add(label(String.format("%s/%s", e.getClassCode(), e.getNumber()), true), "");
            mainPanel.add(label(String.format("- %s - %s (%d runs)", e.getName(), e.getCarDesc(), e.getRuns().size()), false), "wrap");
        }
    }

    @Override
    public boolean verifyData()
    {
        result = new ArrayList<UUID>();
        for (String s : checks.keySet()) {
            if (checks.get(s).isSelected())
                result.add(UUID.fromString(s));
        }
        return true;
    }
}

