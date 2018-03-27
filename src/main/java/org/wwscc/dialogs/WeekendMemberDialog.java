/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dialogs;

import java.sql.Date;
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
    JLabel number;
    Driver driver;

    public WeekendMemberDialog(Driver driver)
    {
        super(new MigLayout("fill", "[50%][50%]"), true);
        this.driver = driver;

        getnew = new JButton("Get New Membership");
        getnew.addActionListener(e -> {
            GetNewWeekendMemberDialog d = new GetNewWeekendMemberDialog();
            d.doDialog("Get New Weekend Number", i -> {
                long now = System.currentTimeMillis();
                WeekendMember m = new WeekendMember(driver.getDriverId(), new Date(now), new Date(now), "", "", "", "");
                try {
                    Database.d.newWeekendNumber(m);
                } catch (Exception ex) {
                    log.log(Level.WARNING, "Error getting a new weekend number: " + ex, ex);
                    JOptionPane.showMessageDialog(this, "Error generating new member number: \n" + ex);
                }
            });
        });

        number = new JLabel("123456");
        number.setFont(number.getFont().deriveFont(20.0f));

        mainPanel.add(number, "spanx 2, center, wrap");

        mainPanel.add(label("Start", true), "right");
        mainPanel.add(label("3/22/18", false), "wrap");
        mainPanel.add(label("End", true), "right");
        mainPanel.add(label("3/27/18", false), "wrap");

        mainPanel.add(getnew, "center, spanx 2");
        buttonPanel.remove(cancel);
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
