/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Add some meta information that can be loaded base on current state but it associated
 * directly with a Car entry.  This make sure that anyone using the information is getting
 * something that was required to be set during instantiation.
 */
public class DecoratedCar extends Car
{
    protected boolean registered;
    protected boolean inRunOrder;
    protected boolean otherActivity;
    protected List<String> sessions;
    protected List<Payment> payments;

    public DecoratedCar(Car c)
    {
        super(c);
        registered = false;
        inRunOrder = false;
        otherActivity = false;
        sessions = new ArrayList<String>();
        payments = new ArrayList<Payment>();
    }

    public void addPayment(Payment p)  { payments.add(p); }

    public double getPaymentTotal()    { return payments.stream().mapToDouble(x -> x.getAmount()).sum(); }
    public List<Payment> getPayments() { return payments;}
    public boolean hasPaid()           { return payments.size() > 0; }
    public boolean isRegistered()      { return registered; }
    public boolean isInRunOrder()      { return inRunOrder; }
    public boolean hasOtherActivity()  { return otherActivity; }
    public List<String> getSessions()  { return sessions; }
    public boolean allSessions(List<String> compare) {
        return sessions.containsAll(compare);
    }

    public static class PaidOrder implements Comparator<DecoratedCar>
    {
        @Override
        public int compare(DecoratedCar o1, DecoratedCar o2) {
            if (o1.hasPaid() && !o2.hasPaid()) return -1;
            if (o2.hasPaid() && !o1.hasPaid()) return  1;
            return 0;
        }
    }
}
