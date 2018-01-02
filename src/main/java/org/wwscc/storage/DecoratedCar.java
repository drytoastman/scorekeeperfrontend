package org.wwscc.storage;

import java.util.ArrayList;
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
    protected List<Payment> payments;

    public DecoratedCar(Car c)
    {
        super(c);
        registered = false;
        inRunOrder = false;
        otherActivity = false;
        payments = new ArrayList<Payment>();
    }

    public void addPayment(Payment p)  { payments.add(p); }

    public double getPaymentTotal()    { return payments.stream().mapToDouble(x -> x.getAmount()).sum(); }
    public List<Payment> getPayments() { return payments;}
    public boolean hasPaid()           { return payments.size() > 0; }
    public boolean isRegistered()      { return registered; }
    public boolean isInRunOrder()      { return inRunOrder; }
    public boolean hasOtherActivity()  { return otherActivity; }
}
