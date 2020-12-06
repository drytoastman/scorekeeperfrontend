/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2009 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.registration;

import java.awt.Color;
import java.awt.Component;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;

import org.wwscc.dialogs.BaseDialog;
import org.wwscc.storage.Database;
import org.wwscc.storage.PaymentItem;
import org.wwscc.util.NumberField;

import net.miginfocom.swing.MigLayout;

/**
 */
public class PaymentDialog extends BaseDialog<Void> {
    NumberField amount = new NumberField(3, 2);
    JLabel otherlabel = new JLabel("Amount");
    JComboBox<PaymentItem> cb;

    public PaymentDialog(UUID eventid) {
        super(new MigLayout(""), false);
        List<PaymentItem> items = Database.d.getPaymentItemsForEvent(eventid);
        items.removeIf(pi -> pi.getItemType() != PaymentItem.ENTRY);
        items.sort(new Comparator<PaymentItem>() {
            public int compare(PaymentItem i1, PaymentItem i2) {
                return i1.getName().compareTo(i2.getName());
            }
        });
        items.add(PaymentItem.otherItem());

        cb = new JComboBox<PaymentItem>(items.toArray(new PaymentItem[0]));
        cb.setRenderer(new ItemRenderer());
        cb.addActionListener(e -> {
            boolean show = (((PaymentItem)cb.getSelectedItem()).getPrice() <= 0.0);
            otherlabel.setVisible(show);
            amount.setVisible(show);
        });
        cb.setSelectedIndex(0);

        mainPanel.add(cb, "growx, spanx 2, wrap");
        mainPanel.add(otherlabel, "");
        mainPanel.add(amount, "wmin 150, growx, gapleft 10, wrap");
        result = null;
    }

    public PaymentItem getSelectedItem() {
        return (PaymentItem)cb.getSelectedItem();
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
            if (((PaymentItem)cb.getSelectedItem()).getPrice() > 0.0) return true;
            getOtherAmount(); // valid double
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Data is good, return it.
     */
    @Override
    public Void getResult() { return result; }

    class ItemRenderer extends DefaultListCellRenderer {
        public ItemRenderer() {
            super();
            setBackground(Color.WHITE);
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {

            PaymentItem item = (PaymentItem)value;
            if (item.getPrice() > 0.0) {
                setText(String.format("$%.2f - %s", item.getPrice(), item.getName()));
            } else {
                setText(String.format("%s", item.getName()));
            }
            return this;
        }
    }
}


