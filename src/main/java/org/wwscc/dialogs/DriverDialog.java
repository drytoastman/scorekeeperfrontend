/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dialogs;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.text.AbstractDocument;

import org.wwscc.barcodes.Code39Filter;
import org.wwscc.storage.Database;
import org.wwscc.storage.Driver;
import org.wwscc.util.HintTextField;

import net.miginfocom.swing.MigLayout;

/**
 * Core functions for all dialogs.
 */
public class DriverDialog extends BaseDialog<Driver>
{
    public final static String[] ATTRLIST = { "scca", "address", "city", "state", "zip", "phone", "brag", "sponsor", "econtact", "ephone" };
    public final static String[] LABELS = { "SCCA #", "Address", "City", "State", "Zip", "Phone", "Brag Fact", "Sponsor", "Emerg Contact", "Emerg Phone" };

    /**
     * Create the dialog.
     * @param d the driver data to source initially
     */
    public DriverDialog(Driver d)
    {
        super(new MigLayout("fill, w 500, gap 5, ins 5", "[10%, right][40%, fill][10%, right][40%, fill]", ""), true);

        if (d == null) d = new Driver();

        HintTextField pwf = new HintTextField("sets a new password");
        fields.put("plaintext", pwf);

        mainPanel.add(label("First Name", true), "");
        mainPanel.add(entry("firstname", d.getFirstName()), "");
        mainPanel.add(label("Last Name", true), "right");
        mainPanel.add(entry("lastname", d.getLastName()), "wrap");

        mainPanel.add(label("Username", true), "");
        mainPanel.add(entry("username", d.getUserName()), "");
        mainPanel.add(label("Password", false), "");
        mainPanel.add(pwf, "wrap");

        mainPanel.add(label("Email", false), "");
        JPanel esub = new JPanel(new MigLayout("gap 0, ins 0", "[fill,grow 100][grow 0][grow 0]"));
        esub.add(entry("email", d.getEmail()), "gapright 5");
        esub.add(label("OptOut", false), "");
        esub.add(checkbox("optoutmail", d.getOptOutMail()), "");
        mainPanel.add(esub, "spanx 3, wrap");

        mainPanel.add(label("Barcode", false), "");
        mainPanel.add(entry("barcode", d.getBarcode()), "");
        ((AbstractDocument)fields.get("barcode").getDocument()).setDocumentFilter(new Code39Filter());

        mainPanel.add(label("SCCA #", false), "");
        mainPanel.add(entry("scca", d.getAttrS("scca")), "wrap");

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

        for (int ii = 5; ii < ATTRLIST.length; ii++) {
            mainPanel.add(label(LABELS[ii], false), "");
            mainPanel.add(entry(ATTRLIST[ii], d.getAttrS(ATTRLIST[ii])), "spanx 3, wrap");
        }

        result = d;
    }


    /**
     * Called after OK to verify data before closing.
     */
    @Override
    public boolean verifyData()
    {
        String first = getEntryText("firstname").trim();
        String last  = getEntryText("lastname").trim();
        String username = getEntryText("username").trim();
        String plaintext = getEntryText("plaintext").trim();
        String email = getEntryText("email").trim();

        if (first.length() < 2) {
            errorMessage = "Firstname must be at least 2 characters";
            return false;
        }

        if (last.length() < 2) {
            errorMessage = "Lastname must be at least 2 characters";
            return false;
        }

        if (username.length() < 6) {
            errorMessage = "Username must be at least 6 characters";
            return false;
        }

        Driver d1 = Database.d.getDriverByUsername(username);
        if ((d1 != null) && (!d1.getDriverId().equals(result.getDriverId()))) {
            errorMessage = "Username '" + username + "' is already taken";
            return false;
        }

        if ((plaintext.length() > 0) && (plaintext.length() < 6)) {
            errorMessage = "Password must be at least 6 characters";
            return false;
        }

        // if the first, last or email are not what we started with, check for duplicate
        if (!result.equalNameEmail(first, last, email)) {
            for (Driver d2 : Database.d.getDriversLike(first, last)) {
                if (d2.getEmail().toLowerCase().equals(email.toLowerCase())) {
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

        result.setFirstName(getEntryText("firstname").trim());
        result.setLastName(getEntryText("lastname").trim());
        result.setUsername(getEntryText("username").trim());
        String plaintext = getEntryText("plaintext").trim();
        if (!plaintext.isEmpty())
            result.setPasswordPlaintext(plaintext);

        result.setEmail(getEntryText("email").trim());
        result.setOptOutMail(isChecked("optoutmail"));
        result.setBarcode(getEntryText("barcode").trim());
        for (String attr : ATTRLIST) {
            result.setAttrS(attr, getEntryText(attr).trim());
        }
        return result;
    }
}

