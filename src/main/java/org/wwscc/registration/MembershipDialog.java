/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2020 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.registration;

import java.util.List;
import java.util.logging.Logger;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.wwscc.dialogs.BaseDialog;
import org.wwscc.storage.Database;
import org.wwscc.storage.Driver;
import org.wwscc.storage.Payment;
import org.wwscc.storage.PaymentItem;

import net.miginfocom.swing.MigLayout;


/**
 */
public class MembershipDialog extends BaseDialog<PaymentItem> {

    public MembershipDialog(Driver driver) {
        super(new MigLayout(""), false);

        mainPanel.add(paidPanel(driver), "wrap");
        mainPanel.add(newItemPanel(), "wrap");
        result = null;
        cancel.setVisible(false);
    }


    JPanel paidPanel(Driver driver) {
        JPanel ret = new JPanel(new MigLayout());
        ret.add(label("Current Payments", true), "spanx 2, wrap");
        for (Payment p : Database.d.getMembershipPayments(driver.getDriverId())) {
            JLabel name = new JLabel(p.getItemName());
            JLabel txtype = new JLabel(p.getTxType());
            JLabel amount = new JLabel(String.format("$%.2f", p.getAmount()));
            ret.add(name);
            ret.add(txtype);
            ret.add(amount);
            if (p.getTxType().equals(EntryPanel.ONSITE_PAYMENT)) {
                JButton delete = new JButton("\u274C");
                delete.setFont(delete.getFont().deriveFont(8.0f));
                delete.setBorderPainted(false);
                delete.setFocusPainted(false);
                delete.addActionListener(e -> {
                    try {
                        Database.d.deletePayment(p.getPayId());
                        name.setVisible(false);
                        txtype.setVisible(false);
                        amount.setVisible(false);
                        delete.setVisible(false);
                    } catch (Exception ex1) {
                        Logger.getLogger(MembershipDialog.class.getCanonicalName()).severe(ex1.getMessage());
                    }
                });
                ret.add(delete, "wrap");
            } else {
                ret.add(new JLabel(""), "wrap");
            }
        }
        return ret;
    }


    JPanel newItemPanel() {
        JPanel ret = new JPanel(new MigLayout());

        List<PaymentItem> items = Database.d.getPaymentItemsForMembership();
        ButtonGroup buttonGroup = new ButtonGroup();

        ret.add(label("Add Payment", true), "spanx 2, wrap");
        for (PaymentItem i: items) {
            JRadioButton rb = new JRadioButton(i.getName());
            buttonGroup.add(rb);
            rb.addActionListener(e -> { result = i; });
            ret.add(rb, "");
            ret.add(new JLabel(String.format("$%.2f", i.getPrice())), "wrap");
        }

        return ret;
    }


    /**
     * Called after OK to verify data before closing.
     */
    @Override
    public boolean verifyData() {
        return true;
    }

    /**
     * Data is good, return it.
     */
    @Override
    public PaymentItem getResult() { return result; }
}


