/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dialogs;

import java.time.LocalDate;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import org.wwscc.storage.Database;
import org.wwscc.storage.Driver;
import org.wwscc.storage.WeekendMember;

import net.miginfocom.swing.MigLayout;

public class WeekendMemberDialog extends BaseDialog<Void>
{
    private Logger log = Logger.getLogger(WeekendMemberDialog.class.getCanonicalName());

    JButton getnew;

    public WeekendMemberDialog(Driver driver, WeekendMember active)
    {
        super(new MigLayout("h 90, w 150, center", ""), true);

        getnew = new JButton("Assign New Membership");
        getnew.addActionListener(e -> {
            LocalDate start = LocalDate.now();
            LocalDate end   = start.plusDays(5);
            WeekendMember temp = new WeekendMember(driver.getDriverId(), start, end);
            GetNewWeekendMemberDialog d = new GetNewWeekendMemberDialog(temp);
            if (d.doDialog("Assign New Weekend Number", null)) {
                try {
                    temp = d.getResult();
                    Database.d.newWeekendNumber(temp);
                    rebuildPanel(temp);
                } catch (Exception ex) {
                    log.log(Level.WARNING, "Error getting a new weekend number: " + ex, ex);
                    JOptionPane.showMessageDialog(this, "Error generating new member number: \n" + ex);
                }
            }
        });

        buttonPanel.remove(cancel);
        rebuildPanel(active);
    }

    private void rebuildPanel(WeekendMember active)
    {
        mainPanel.removeAll();

        if (active != null) {
            JLabel number = new JLabel("");
            number.setFont(number.getFont().deriveFont(20.0f));
            number.setText(""+active.getMemberId());
            mainPanel.add(number, "spanx 2, center, wrap");
            mainPanel.add(label("Start", true), "right");
            mainPanel.add(label(active.getStartDate()+"", false), "wrap");
            mainPanel.add(label("End", true), "right");
            mainPanel.add(label(active.getEndDate()+"", false), "wrap");
        } else {
            mainPanel.add(label("No membership covering today", true), "center, gapbottom 20, wrap");
            mainPanel.add(getnew, "center");
        }

        revalidate();
        repaint();
    }

    /**
     * Called after OK to verify data before closing.
     */
    @Override
    public boolean verifyData()
    {
        return true;
    }

    /**
     * OK was pressed, data was verified, now return it.
     */
    @Override
    public Void getResult()
    {
        return null;
    }
}
