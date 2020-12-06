/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2020 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.storage;

import java.sql.ResultSet;
import java.sql.SQLException;

public class PaymentItem
{
    public static final int ENTRY = 0;
    public static final int NONENTRY = 1;
    public static final int MEMBERSHIP = 2;

    protected String itemid;
    protected String name;
    protected int itemtype;
    protected int priceInCents;
    protected String currency;

    public PaymentItem()
    {
    }

    public PaymentItem(ResultSet rs) throws SQLException
    {
        itemid       = rs.getString("itemid");
        name         = rs.getString("name");
        itemtype     = rs.getInt("itemtype");
        priceInCents = rs.getInt("price");
        currency     = rs.getString("currency");
    }

    public static PaymentItem otherItem() {
        PaymentItem ret = new PaymentItem();
        ret.itemid = "other";
        ret.name   = "other";
        ret.itemtype = ENTRY;
        ret.priceInCents = 0;
        ret.currency = "USD";
        return ret;
    }

    public String getName() { return name; }
    public int getItemType() { return itemtype; }
    public double getPrice() { return priceInCents/100.0; }
}
