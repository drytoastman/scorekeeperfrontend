package org.wwscc.storage;

import java.math.BigDecimal;

/**
 * Add some meta information that can be loaded base on current state but it associated 
 * directly with a Car entry.  This make sure that anyone using the information is getting
 * something that was required to be set during instantiation.
 */
public class MetaCar extends Car 
{
    public static final BigDecimal ZERO = new BigDecimal(0);
	protected boolean isRegistered;
	protected boolean isInRunOrder;
	protected boolean hasActivity;
	protected boolean paid;
	
	public MetaCar(Car c)
	{
		super(c);
	}
	
	public boolean hasPaid()      { return paid; }
	//public BigDecimal getPaid()   { return paid; }
	public boolean isRegistered() { return isRegistered; }
	public boolean isInRunOrder() { return isInRunOrder; }
	public boolean hasActivity()  { return hasActivity; }
}
