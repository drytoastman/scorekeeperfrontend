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
import java.util.Collection;
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
    public static final Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    public static DataInterface d = new FakeDatabase(); // to prevent null pointers

    /**
     * Used at startup to open the series that was previously opened
     */
    public static void openDefault(Collection<String> watch)
    {
        openSeries(Prefs.getSeries(""), 0, watch);
    }

   /**
     * Used when the user wants to examine the database without a series in mind
     * @param superuser true if we want super user privileges with the connection, needed to create a series
     * @param timeoutms a statement timeout after the database is connected in ms, <=0 means no timeout
     * @return true if the series was opened, false otherwise
     */
    public static boolean openPublic(boolean superuser, int timeoutms, Collection<String> watch)
    {
        if (d != null)
            d.close();

        while (true) {
            try {
                d = new PostgresqlDatabase(superuser?"postgres":"localuser", watch);
                Messenger.sendEvent(MT.SERIES_CHANGED, "publiconly");
                return true;
            } catch (SQLException sqle) {
                log.log(Level.SEVERE, "\bUnable to open database (public-only) due to error "+sqle+","+sqle.getSQLState(), sqle);
                return false;
            }
        }
    }

    /**
     * Used when the user wants to select a new specific series
     * @param series the series to connect to
     * @param timeoutms a statement timeout after the database is connected in ms, <=0 means no timeout
     * @return true if the series was opened, false otherwise
     */
    public static boolean openSeries(String series, int timeoutms, Collection<String> watch)
    {
        if (d != null)
            d.close();

        try {
            d = new PostgresqlDatabase("localuser", timeoutms, watch);
            if (series.equals("") || !d.getSeriesList().contains(series))
            {
                Messenger.sendEvent(MT.SERIES_CHANGED, "<none>");
            }
            else
            {
                d.useSeries(series);
                Messenger.sendEvent(MT.SERIES_CHANGED, series);
            }
            return true;
        } catch (SQLException sqle) {
            log.severe(String.format("\bUnable to open series %s due to error %s", series, sqle));
            return false;
        }
    }

    /**
     * Used when the user wants to select a new specific series, don't allow blank series
     * @param series the series to connect to
     * @param timeoutms a statement timeout after the database is connected in ms, <=0 means no timeout
     * @return name of the series is actually opened, blank for no series but active database
     * @throws SQLException
     */
    public static String openSeriesNM(String series, int timeoutms, Collection<String> watch) throws SQLException
    {
        if (d != null)
            d.close();

        d = new PostgresqlDatabase("localuser", timeoutms, watch);
        if (series.equals("") || !d.getSeriesList().contains(series))
            return "";
        d.useSeries(series);
        return series;
    }


    /**
     * Static function to wait for the database to start and initialize
     */
    public static boolean testUp()
    {
        try {
            Logger.getLogger("org.postgresql.Driver").setLevel(Level.OFF); // Apparently, I have to set this again
            PostgresqlDatabase db = new PostgresqlDatabase("localuser", null);
            db.close();
            return true;
        } catch (SQLException sqle) {
            String ss = sqle.getSQLState();
            log.log(Level.INFO, "Database unavailable: "+sqle+","+ss);
            return false;
        }
    }
}

