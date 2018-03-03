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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.simple.JSONObject;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.postgresql.util.PGobject;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;

public class PostgresqlDatabase extends SQLDataInterface implements AutoCloseable
{
    private static final Logger log = Logger.getLogger(PostgresqlDatabase.class.getCanonicalName());
    private static final List<String> ignore = Arrays.asList(new String[] {"information_schema", "pg_catalog", "public", "template"});

    private volatile Connection conn;
    private volatile ConnectParam connectParam;
    private volatile boolean inTransaction;
    private Map<ResultSet, PreparedStatement> leftovers;
    private PostgresConnectionWatcher watcher;

    class ConnectParam {
        String host, user, password, series;
        int statementtimeout;
    }

    /**
     * Attempt a connection to a remote host to see if the password is valid
     * @param host the host to connect to
     * @param user the username
     * @param password the password
     * @return true if the connection was successful with the username/password
     */
    static public boolean checkPassword(String host, String user, String password)
    {
        try {
            PostgresqlDatabase db = new PostgresqlDatabase(host, user, password, 0);
            db.close();
            return true;
        } catch (SQLException sqle) {
            if (sqle.getSQLState().equals("28P01")) {
                log.warning("Incorrect Password");
            } else {
                log.warning(sqle.getMessage());
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "General exception checking password: " + e, e);
        }
        return false;
    }

    /**
     * Connect to the localhost with the given user
     * @param user localuser or postgres
     * @throws SQLException
     */
    public PostgresqlDatabase(String user) throws SQLException
    {
        this(null, user, null, 0);
    }


    /**
     * Connect to the localhost with the given user and statement timeout
     * @param user localuser or postgres
     * @param statementtimeout if > 0, a statement timeout in ms
     * @throws SQLException
     */
    public PostgresqlDatabase(String user, int statementtimeout) throws SQLException
    {
        this(null, user, null, statementtimeout);
    }


    /**
     * Connect to a local or remote host
     * @param host if null use localhost, otherwise the remote host to connect to
     * @param user the username to connect with
     * @param password if not null, the password to use
     * @param statementtimeout if > 0, a statement timeout in ms
     * @throws SQLException
     */
    public PostgresqlDatabase(String host, String user, String password, int statementtimeout) throws SQLException
    {
        leftovers = new HashMap<ResultSet, PreparedStatement>();
        connectParam = new ConnectParam();
        connectParam.host = host;
        connectParam.user = user;
        connectParam.password = password;
        connectParam.statementtimeout = statementtimeout;
        connectParam.series = null;
        conn = internalConnect();

        if (password == null) {
            // only start watcher for local connections, remotes are only used to check series and password
            watcher = new PostgresConnectionWatcher();
            watcher.setName("PostgresConnectionWatcher-"+watcher.getId());
            watcher.setDaemon(true);
            watcher.start();
        }
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

        if ((connectParam.host == null) || connectParam.host.equals("127.0.0.1") || connectParam.host.equals("localhost"))
        {
            props.setProperty("loginTimeout", "1");
            host = "127.0.0.1";
            port = 6432;
        }
        else
        {
            if (connectParam.password != null)
                props.setProperty("password", connectParam.password);
            props.setProperty("ssl", "true");
            props.setProperty("sslfactory", "org.postgresql.ssl.NonValidatingFactory");
            props.setProperty("loginTimeout", "20");
            host = connectParam.host;
            port = 54329;
        }

        String url = String.format("jdbc:postgresql://%s:%d/scorekeeper", host, port);
        log.log(Level.INFO, "Connecting to postgres @ {0} with param {1}", new Object[] { url, props });
        Connection c = DriverManager.getConnection(url, props);

        Statement s = c.createStatement();
        s.execute("set time zone 'UTC'");
        s.execute("LISTEN datachange");
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
                        Set<String> changes = new HashSet<String>();
                        for (PGNotification n : notifications) {
                            changes.add(n.getParameter());
                        }
                        Messenger.sendEvent(MT.DATABASE_NOTIFICATION, changes);
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
            log.warning("\bDatabase rollback failed.  You should probably restart the application.");
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
            } else if (v instanceof JSONObject) {
                PGobject pgo = new PGobject();
                pgo.setType("json");
                pgo.setValue(((JSONObject)v).toJSONString());
                p.setObject(ii+1, pgo);
            } else if (v instanceof Timestamp) {
                p.setTimestamp(ii+1, (Timestamp)v);
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
        ResultSet s = p.executeQuery();
        synchronized(leftovers) {
            leftovers.put(s,  p);
        }
        return s;
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
