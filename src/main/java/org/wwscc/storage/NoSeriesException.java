package org.wwscc.storage;

import java.sql.SQLException;

public class NoSeriesException extends SQLException
{
    public NoSeriesException(String origmessage)
    {
        super(origmessage);
    }
}