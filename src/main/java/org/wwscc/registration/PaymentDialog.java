/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2020 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.registration;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JRadioButton;

import org.wwscc.dialogs.BaseDialog;
import org.wwscc.storage.Database;
import org.wwscc.storage.PaymentItem;
import org.wwscc.util.NumberField;

import net.miginfocom.swing.MigLayout;


/**
 */
public class PaymentDialog extends BaseDialog<PaymentItem> {
    NumberField amount;
    List<PaymentItem> items;
    ButtonGroup buttonGroup;

    public PaymentDialog(UUID eventid) {
        super(new MigLayout(""), false);

        items = Database.d.getPaymentItemsForEvent(eventid);
        items.removeIf(pi -> pi.getItemType() != PaymentItem.ENTRY);
        items.sort(new Comparator<PaymentItem>() {
            public int compare(PaymentItem i1, PaymentItem i2) {
                return i1.getName().compareTo(i2.getName());
            }
        });

        amount = new NumberField(3, 2);
        buttonGroup = new ButtonGroup();

        for (PaymentItem i: items) {
            JRadioButton rb = new JRadioButton(i.getName());
            buttonGroup.add(rb);
            rb.addActionListener(e -> { result = i; });

            mainPanel.add(rb, "");
            mainPanel.add(new JLabel(String.format("$%.2f", i.getPrice())), "wrap");
        }

        JRadioButton rb = new JRadioButton("Other");
        buttonGroup.add(rb);
        rb.addActionListener(e -> { result = null; });

        mainPanel.add(rb, "");
        mainPanel.add(amount, "wmin 100, growx, wrap");
        result = null;
    }

    public double getOtherAmount() {
        return Double.parseDouble(amount.getText());
    }

    /**
     * Called after OK to verify data before closing.
     */
    @Override
    public boolean verifyData() {
        try {
            return (result != null) || (getOtherAmount() > 0.0);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Data is good, return it.
     */
    @Override
    public PaymentItem getResult() { return result; }
}


