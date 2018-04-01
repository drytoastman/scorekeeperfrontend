/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.storage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.UUID;

import org.mindrot.jbcrypt.BCrypt;
import org.wwscc.util.IdGenerator;

public class Driver extends AttrBase
{
    public static final String PLACEHOLDER = "Placeholder";

    protected UUID driverid;
    protected String firstname;
    protected String lastname;
    protected String email;
    protected String username;
    protected String password;
    protected String barcode;
    protected boolean optoutmail;

    public Driver()
    {
        super();
        driverid = IdGenerator.generateId();
        firstname = "";
        lastname = "";
        email = "";
        username = driverid.toString().substring(0, 8);
        password = "";
        barcode = "";
        optoutmail = false;
    }

    public Driver(String f, String l)
    {
        this();
        firstname = f;
        lastname = l;
    }

    public Driver(ResultSet rs) throws SQLException
    {
        super(rs);
        driverid   = (UUID)rs.getObject("driverid");
        firstname  = rs.getString("firstname");
        lastname   = rs.getString("lastname");
        email      = rs.getString("email");
        username   = rs.getString("username");
        password   = rs.getString("password");
        barcode = rs.getString("membership");
        optoutmail = rs.getBoolean("optoutmail");
    }

    public LinkedList<Object> getValues()
    {
        LinkedList<Object> ret = new LinkedList<Object>();
        ret.add(driverid);
        ret.add(firstname);
        ret.add(lastname);
        ret.add(email);
        ret.add(username);
        ret.add(password);
        ret.add(barcode);
        ret.add(optoutmail);
        attrCleanup();
        ret.add(attr);
        return ret;
    }

    public String getUserName()   { return username; }
    public String getFullName()   { return firstname + " " + lastname; }
    public UUID   getDriverId()   { return driverid; }
    public String getFirstName()  { return firstname; }
    public String getLastName()   { return lastname; }
    public String getEmail()      { return email; }
    public String getBarcode()    { return barcode; }
    public boolean getOptOutMail(){ return optoutmail; }

    public void setFirstName(String s)  { firstname = s; }
    public void setLastName(String s)   { lastname = s; }
    public void setEmail(String s)      { email = s; }
    public void setBarcode(String s)    { barcode = s; }
    public void setUsername(String s)   { username = s; }
    public void setOptOutMail(boolean b){ optoutmail = b; }

    @Override
    public boolean equals(Object o)
    {
        return ((o instanceof Driver) && ((Driver)o).driverid.equals(driverid));
    }

    @Override
    public int hashCode()
    {
        return driverid.hashCode();
    }

    public String toString()
    {
        return firstname + " " + lastname;
    }

    public static Driver getPlaceHolder(String barcode)
    {
        Driver ret = new Driver(PLACEHOLDER, barcode);
        ret.setBarcode(barcode);
        return ret;
    }

    public boolean isPlaceholder()
    {
        return firstname.equals(PLACEHOLDER);
    }

    public void setPasswordPlaintext(String plaintext)
    {
        password = BCrypt.hashpw(plaintext, BCrypt.gensalt(12));
    }
}

