/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.storage;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.postgresql.util.PGobject;
import org.postgresql.util.PSQLException;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PostgresqlDatabase extends SQLDataInterface implements AutoCloseable
{
    private static final Logger log = Logger.getLogger(PostgresqlDatabase.class.getCanonicalName());
    private static final List<String> ignore = Arrays.asList(new String[] {"information_schema", "pg_catalog", "public", "template"});
    private static ObjectMapper objectMapper = new ObjectMapper();

    private volatile Connection conn;
    private volatile ConnectParam connectParam;
    private volatile boolean inTransaction;
    private Map<ResultSet, PreparedStatement> leftovers;
    private PostgresConnectionWatcher watcher;

    class ConnectParam {
        String user, series;
        int statementtimeout;
        Set<String> watchtables;
    }

    /**
     * Connect to the localhost with the given user
     * @param user localuser or postgres
     * @throws SQLException
     */
    public PostgresqlDatabase(String user, Collection<String> watch) throws SQLException
    {
        this(user, 0, watch);
    }

    /**
     * Connect to the localhost with the given user and statement timeout
     * @param user localuser or postgres
     * @param statementtimeout if > 0, a statement timeout in ms
     * @throws SQLException
     */
    public PostgresqlDatabase(String user, int statementtimeout, Collection<String> watch) throws SQLException
    {
        leftovers = new HashMap<ResultSet, PreparedStatement>();
        connectParam = new ConnectParam();
        connectParam.user = user;
        connectParam.statementtimeout = statementtimeout;
        connectParam.series = null;
        connectParam.watchtables = new HashSet<String>();
        if (watch != null)
            connectParam.watchtables.addAll(watch);
        conn = internalConnect();

        watcher = new PostgresConnectionWatcher();
        watcher.setName("PostgresConnectionWatcher-"+watcher.getId());
        watcher.setDaemon(true);
        watcher.start();
    }

    /**
     * Wrap the connect in a state based call so we can reconnect internally as well
     * @throws SQLException
     */
    private Connection internalConnect() throws SQLException
    {
        String host = null;
        int port = -1;
        Properties props = new Properties();
        props.setProperty("ApplicationName", System.getProperty("program.name", "Java"));
        props.setProperty("user", connectParam.user);
        props.setProperty("loginTimeout", "1");
        host = "127.0.0.1";
        port = 6432;

        String url = String.format("jdbc:postgresql://%s:%d/scorekeeper", host, port);
        log.log(Level.INFO, "Connecting to postgres @ {0} with param {1}", new Object[] { url, props });
        Connection c = DriverManager.getConnection(url, props);

        Statement s = c.createStatement();
        s.execute("set time zone 'UTC'");
        for (String table : connectParam.watchtables)
            s.execute("LISTEN " + table);
        if (connectParam.statementtimeout > 0)
            s.execute("SET statement_timeout='"+connectParam.statementtimeout+"'");
        if (connectParam.series != null)
            s.execute("SET search_path='"+connectParam.series+"','public'");

        s.close();
        return c;
    }


    @Override
    public void close()
    {
        try
        {
            if ((conn != null) && (!conn.isClosed()))
            {
                conn.close();
                if (watcher != null)
                    watcher.done = true;
                conn = null;
            }
        }
        catch (SQLException sqle)
        {
            log.warning("\bPostgresql error closing series: " + sqle);
        }
    }


    /**
     * Interface for use of connection to check if its up and available, synchronized as callers can
     * come from multiple threads and we don't want to call internalConnect more than once
     * @return the connection for this object
     * @throws SQLException
     */
    private synchronized Connection getConnection() throws SQLException
    {
        if (conn == null)
            return null;
        if (!conn.isValid(0))
        {
            if (inTransaction)
                log.severe("\bThere was a database connection problem in a transaction, you should restart the application");
            conn = internalConnect();
        }
        return conn;
    }


    /**
     * Thread to do connection and notification checks in the background
     */
    private class PostgresConnectionWatcher extends Thread
    {
        boolean done = false;
        public void run()
        {
            trysleep(2000);
            while (!done) {
                try {
                    PGConnection pg = (PGConnection)getConnection();
                    if (pg == null) {
                        log.info("watcher: connection was null");
                        trysleep(5000);
                        continue;
                    }
                    PGNotification notifications[] = pg.getNotifications();
                    if (notifications != null) {
                        for (PGNotification n : notifications) {
                            Messenger.sendEvent(MT.DATABASE_NOTIFICATION, n.getName());
                        }
                    }
                } catch (Throwable e) {
                    log.log(Level.WARNING, "ConnectionWatcher exception: " + e, e);
                }

                trysleep(500);
            }
        }

        private void trysleep(long ms)
        {
            try {
                sleep(ms);
            } catch (InterruptedException ie) {
            }
        }
    }


    @Override
    public List<String> getSeriesList()
    {
        List<String> ret = new ArrayList<String>();
        try
        {
            DatabaseMetaData meta = getConnection().getMetaData();
            ResultSet rs = meta.getSchemas();
            while (rs.next()) {
                String s = rs.getString("TABLE_SCHEM");
                if (!ignore.contains(s))
                    ret.add(s);
            }
            rs.close();
        }
        catch (SQLException sqle)
        {
            logError("getSeriesList", sqle);
        }

        return ret;
    }


    @Override
    public void useSeries(String series)
    {
        try {
            Statement s = getConnection().createStatement();
            s.execute("SET search_path='"+series+"','public'");
            s.close();
            connectParam.series = series;
        } catch (SQLException sqle) {
            log.log(Level.SEVERE, "\bUnable to set series: " + sqle, sqle);
        }
    }


    @Override
    public void start() throws SQLException
    {
        getConnection().setAutoCommit(false);
        inTransaction = true;
    }

    @Override
    public void commit() throws SQLException
    {
        inTransaction = false;
        getConnection().setAutoCommit(true);
    }


    @Override
    public void rollback() {
        try {
            Connection c = getConnection();
            inTransaction = false;
            c.rollback();
            c.setAutoCommit(true);
        } catch (SQLException sqle) {
            log.log(Level.WARNING, "\bDatabase rollback failed.  You should probably restart the application.", sqle);
        }
    }

    void bindParam(PreparedStatement p, List<Object> args) throws SQLException
    {
        if (args == null)
            return;

        for (int ii = 0; ii < args.size(); ii++)
        {
            Object v = args.get(ii);
            if (v == null) {
                p.setNull(ii+1, java.sql.Types.NULL);
            } else if (v instanceof Integer) {
                p.setInt(ii+1, (Integer)v);
            } else if (v instanceof Long) {
                p.setLong(ii+1, (Long)v);
            } else if (v instanceof Double) {
                p.setDouble(ii+1, (Double)v);
            } else if (v instanceof String) {
                p.setString(ii+1, (String)v);
            } else if (v instanceof Boolean) {
                p.setBoolean(ii+1, (Boolean)v);
            } else if (v instanceof UUID) {
                p.setObject(ii+1, v);
            } else if (v instanceof JsonNode) {
                PGobject pgo = new PGobject();
                pgo.setType("json");
                try {
                    pgo.setValue(objectMapper.writeValueAsString((JsonNode)v));
                } catch (JsonProcessingException e) {
                    throw new SQLException(e);
                }
                p.setObject(ii+1, pgo);
            } else if (v instanceof Timestamp) {
                p.setTimestamp(ii+1, (Timestamp)v, Database.utc);
            } else if (v instanceof LocalDate) {
                p.setObject(ii+1, v);
            } else if (v.getClass().isArray()) {
                String t = "text";
                switch (v.getClass().getComponentType().getName()) {
                    case "java.util.UUID": t = "uuid"; break;
                }
                p.setArray(ii+1, getConnection().createArrayOf(t, (Object[])v));
            } else {
                throw new SQLException("unexpected param type: " + v.getClass());
            }
        }
    }

    @Override
    public void executeUpdate(String sql, List<Object> args) throws SQLException
    {
        PreparedStatement p = getConnection().prepareStatement(sql);
        bindParam(p, args);
        p.executeUpdate();
        p.close();
    }

    @Override
    public void executeGroupUpdate(String sql, List<List<Object>> args) throws SQLException
    {
        PreparedStatement p = getConnection().prepareStatement(sql);
        for (List<Object> l : args) {
            bindParam(p, l);
            p.executeUpdate();
        }
        p.close();
    }


    @Override
    public ResultSet executeSelect(String sql, List<Object> args) throws SQLException
    {
        PreparedStatement p = getConnection().prepareStatement(sql);
        if (args != null)
            bindParam(p, args);
        try {
            ResultSet s = p.executeQuery();
            synchronized(leftovers) {
                leftovers.put(s,  p);
            }
            return s;
        } catch (SQLException sqle) {
            if (sqle instanceof PSQLException) {
                if (((PSQLException)sqle).getSQLState().equals("42P01")) {
                    throw new NoSeriesException("\n\nMost likely using an old default series that is no longer present. Use the File menu to open a new series.\n\n" + sqle.getMessage());
                }
            }
            throw sqle;
        }
    }


    /**
     * executeSelect(String,List<Object>) cannot close its result sets as they are returned to the caller.  The caller
     * can call this method to close those 'leftover' cursors.
     */
    @Override
    public void closeLeftOvers()
    {
        synchronized (leftovers)
        {
            for (ResultSet s : leftovers.keySet())
            {
                try {
                    s.close();
                    leftovers.get(s).close();
                } catch (SQLException sqle) {
                    log.info("Error closing leftover statements: " + sqle);
                }
            }

            leftovers.clear();
        }
    }


    /**
     * Run a SELECT statement and create objects with the results using the given constructor that takes a
     * ResultSet as an argument.
     */
    @Override
    public <T> List<T> executeSelect(String sql, List<Object> args, Constructor<T> objc) throws SQLException
    {
        try
        {
            List<T> result = new ArrayList<T>();
            PreparedStatement p = getConnection().prepareStatement(sql);
            if (args != null)
                bindParam(p, args);
            ResultSet s = p.executeQuery();
            while (s.next()) {
                result.add(objc.newInstance(s));
            }
            s.close();
            p.close();
            return result;
        }
        catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
        {
            throw new SQLException(e);
        }
    }
}
