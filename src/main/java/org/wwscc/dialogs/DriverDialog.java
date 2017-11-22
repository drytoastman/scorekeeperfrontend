/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dialogs;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;

import org.wwscc.storage.Database;
import org.wwscc.storage.Driver;

import net.miginfocom.swing.MigLayout;

/**
 * Core functions for all dialogs.
 */
public class DriverDialog extends BaseDialog<Driver>
{
    public final static String[] ATTRLIST = { "address", "city", "state", "zip", "phone", "brag", "sponsor", "econtact", "ephone" };
    public final static String[] LABELS = { "Address", "City", "State", "Zip", "Phone", "Brag Fact", "Sponsor", "Emerg Contact", "Emerg Phone" };

    /**
     * Create the dialog.
     * @param d the driver data to source initially
     */
    public DriverDialog(Driver d)
    {
        super(new MigLayout("fill, w 500, gap 5, ins 5", "[grow 0, right][fill,grow 100][grow 0][fill, grow 100]", ""), true);

        if (d == null) d = new Driver();

        mainPanel.add(label("First Name", true), "");
        mainPanel.add(entry("firstname", d.getFirstName()), "");
        mainPanel.add(label("Last Name", true), "");
        mainPanel.add(entry("lastname", d.getLastName()), "wrap");

        mainPanel.add(label("Email", false), "");
        JPanel esub = new JPanel(new MigLayout("gap 0, ins 0", "[fill,grow 100][grow 0][grow 0]"));
        esub.add(entry("email", d.getEmail()), "gapright 5");
        esub.add(label("OptOut", false), "");
        esub.add(checkbox("optoutmail", d.getOptOutMail()), "");
        mainPanel.add(esub, "spanx 3, wrap");

        mainPanel.add(label("Membership", false), "");
        mainPanel.add(entry("membership", d.getMembership()), "spanx 3, wrap");

        mainPanel.add(new JSeparator(), "spanx 4, growx, h 2!, wrap");

        mainPanel.add(label("Address", false), "");
        mainPanel.add(entry("address", d.getAttrS("address")), "spanx 3, wrap");

        mainPanel.add(label("City", false), "");
        mainPanel.add(entry("city", d.getAttrS("city")), "");
        JPanel szp = new JPanel(new MigLayout("gap 0, ins 0, fill", "[grow 0, right][fill,grow 100][grow 0][fill, grow 100]"));
        szp.add(label("State", false), "gapright 5");
        szp.add(entry("state", d.getAttrS("state")), "gapright 5");
        szp.add(label("Zip", false), "gapright 5");
        szp.add(entry("zip", d.getAttrS("zip")), "");
        mainPanel.add(szp, "growx, spanx 2, wrap");

        for (int ii = 4; ii < ATTRLIST.length; ii++) {
            mainPanel.add(label(LABELS[ii], false), "");
            mainPanel.add(entry(ATTRLIST[ii], d.getAttrS(ATTRLIST[ii])), "spanx 3, wrap");
        }

        mainPanel.add(new JLabel(" "), "h 0!, pushy 100");
        result = d;
    }


    /**
     * Called after OK to verify data before closing.
     */
    @Override
    public boolean verifyData()
    {
        String first = getEntryText("firstname");
        String last  = getEntryText("lastname");
        String email = getEntryText("email");

        if (first.equals("")) return false;
        if (last.equals("")) return false;

        // if the first, last or email are not what we started with, check for duplicate
        if (!first.equals(result.getFirstName()) || !last.equals(result.getLastName()) || !email.equals(result.getEmail())) {
            for (Driver d : Database.d.getDriversLike(first, last)) {
                if (d.getEmail().equals(email)) {
                    return JOptionPane.showConfirmDialog(this, "A driver with the same firstname, lastname and email already exists. " +
                                                               "Are you sure you want to create a duplicate?",
                                                               "title", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
                }
            }
        }

        return true;
    }


    /**
     * Called after OK is pressed and data is verified and before the dialog is closed.
     */
    @Override
    public Driver getResult()
    {
        if (!valid)
            return null;

        result.setFirstName(getEntryText("firstname"));
        result.setLastName(getEntryText("lastname"));
        result.setEmail(getEntryText("email"));
        result.setOptOutMail(isChecked("optoutmail"));
        result.setMembership(getEntryText("membership"));
        for (String attr : ATTRLIST) {
            result.setAttrS(attr, getEntryText(attr));
        }
        return result;
    }

}

