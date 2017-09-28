/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2009 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.storage;

import java.sql.SQLException;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;

/**
 */
public class Database
{
	private static final Logger log = Logger.getLogger(Database.class.getCanonicalName());
	public static Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
	public static DataInterface d;

	static
	{
		d = new FakeDatabase();
	}

	/**
	 * Used at startup to open the series that was previously opened
	 */
	public static void openDefault()
	{
		try {
			openSeries(Prefs.getSeries(""));
		} catch (Exception ioe) {
			log.severe("Failed to open default: " + ioe);
		}
	}
	
   /**
     * Used when the user wants to examine the database without a series in mind
     * @param superuser true if we want super user privileges with the connection, needed to create a series
     * @return true if the series was opened, false otherwise
     */
    public static boolean openPublic(boolean superuser)
    {
        if (d != null)
            d.close();
        
        while (true) {
	        try {
	            d = new PostgresqlDatabase(null, superuser);
	            Messenger.sendEvent(MT.SERIES_CHANGED, "publiconly");
	            return true;
	        } catch (SQLException sqle) {
	            String ss = sqle.getSQLState();
	            if (ss.equals("57P03") || ss.equals("08001")) // database still starting up
	                continue;
	            log.log(Level.SEVERE, "\bUnable to open database (public-only) due to error "+sqle+","+ss, sqle);
	            return false;
	        }
        }
    }
    
	/**
	 * Used when the user wants to select a new specific series
	 * @param series the series to connect to
	 * @return true if the series was opened, false otherwise
	 */
	public static boolean openSeries(String series)
	{
		if (d != null)
			d.close();
		
		try {
			if (series.equals("") || !PostgresqlDatabase.getSeriesList(null).contains(series))
			{
				d = new FakeDatabase();
				Messenger.sendEvent(MT.SERIES_CHANGED, "<none>");
			}
			else
			{
				d = new PostgresqlDatabase(series, false);
				Messenger.sendEvent(MT.SERIES_CHANGED, series);
			}
			return true;
		} catch (SQLException sqle) {
			log.severe(String.format("Unable to open series %s due to error %s", series, sqle));
			return false;
		}
	}
}

