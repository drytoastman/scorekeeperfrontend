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

public class PostgresqlDatabase extends SQLDataInterface 
{
	private static final Logger log = Logger.getLogger(PostgresqlDatabase.class.getCanonicalName());
	private static final List<String> ignore = Arrays.asList(new String[] {"information_schema", "pg_catalog", "public"});

	/**
	 * Static function to get the list of series from a database.  Gets the schema list
	 * from the Postgresql connection metadata using the base scorekeeper user.
	 * @param host a remote host to connect to or null for local database
	 * @return a list of series string names
	 */
	static public List<String> getSeriesList(String host)
	{
	    List<String> ret = new ArrayList<String>();
		try
		{
			Connection sconn = getRemoteConnection(host, "nulluser", "nulluser");
			DatabaseMetaData meta = sconn.getMetaData();
		    ResultSet rs = meta.getSchemas();
		    while (rs.next()) {
		    	String s = rs.getString("TABLE_SCHEM");
		    	if (!ignore.contains(s))
		    		ret.add(s);
		    }
		    rs.close();
		    sconn.close();
		}
		catch (SQLException sqle)
		{
			logError("getSeriesList", sqle);
		}
		
		return ret;
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
            Connection sconn = getRemoteConnection(host, user, password);
            sconn.close();
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
     * Static function to get an SSL JDBC Connection to a remote host with a given user
     * @param host the host to connect to (null is replaced with 127.0.0.1)
	 * @param user the username to use
	 * @param password the password to use
     * @return a java.sql.Connection object
     * @throws SQLException if connection fails
     */
    private static Connection getRemoteConnection(String host, String user, String password) throws SQLException
    {
        Properties props = new Properties();
        props.setProperty("ApplicationName", System.getProperty("program.name", "Java"));
        props.setProperty("user", user);
        props.setProperty("password", password);
        props.setProperty("ssl", "true");
        props.setProperty("sslfactory", "org.postgresql.ssl.NonValidatingFactory");
        props.setProperty("loginTimeout", "10");
        if (host == null)
            host = "127.0.0.1";

        return DriverManager.getConnection("jdbc:postgresql://"+host+":54329/scorekeeper", props);
    }
    
    /**
     * Wrap the properties and url pieces to get a local PG connection
     * @param series the series we are interested in
     * @param superuser true if we should connect as the postgres superuser
     * @return a java.sql.Connection object
     * @throws SQLException
     */
    private static Connection getConnection(String series, boolean superuser) throws SQLException
    {
        Properties props = new Properties();
        props.setProperty("ApplicationName", System.getProperty("program.name", "Java"));
        props.setProperty("loginTimeout", "1");
        if (superuser)
            props.setProperty("user", "postgres");
        else
            props.setProperty("user", "localuser");
        if (series != null)
            props.setProperty("currentSchema", series+",public");

        return DriverManager.getConnection("jdbc:postgresql://127.0.0.1:6432/scorekeeper", props);
    }
    
    
    private Connection conn;
    private String series;
    private boolean superuser;
    private Map<ResultSet, PreparedStatement> leftovers;
    
    /**
     * Open a connection to the local scorekeeper database  
     * @param series we are interested in
     * @param superuser true if we should connect as the postgres superuser
     * @throws SQLException if connection fails
     */
    public PostgresqlDatabase(String series, boolean superuser) throws SQLException
    {
        this.conn = null;
        this.series = series;
        this.superuser = superuser;
        this.leftovers = new HashMap<ResultSet, PreparedStatement>();
        init();
    }
    
    public void reconnect() throws SQLException
    {
        init();
    }
    
    private void init() throws SQLException
    {
        if (conn != null)
            conn.close();
        conn = getConnection(series, superuser);
        Statement s = conn.createStatement();
        s.execute("set time zone 'UTC'");
        s.execute("LISTEN datachange");
        s.close();        
    }
    
	@Override
	public void close() 
	{
		try 
		{
			if ((conn != null) && (!conn.isClosed()))
			{
				conn.close();
				conn = null;
			}
		} 
		catch (SQLException sqle) 
		{
			log.warning("Postgresql error closing series: " + sqle);
		}
	}

	@Override
	public void start() throws SQLException 
	{
		conn.setAutoCommit(false);
	}

	@Override
	public void commit() throws SQLException 
	{
		conn.setAutoCommit(true);
	}

	public void checkNotifications()
	{
	    try {
            PGNotification notifications[] = ((PGConnection)conn).getNotifications();
            if (notifications != null) {
                Set<String> changes = new HashSet<String>();
                for (PGNotification n : notifications) {
                    changes.add(n.getParameter());
                }
                Messenger.sendEvent(MT.DATABASE_NOTIFICATION, changes);
            }
        } catch (SQLException e) {
            log.log(Level.INFO, "Failed to process pg notifications: " + e, e);
        }
	}
	
	@Override
	public void rollback() {
		try {
			conn.rollback();
		} catch (SQLException sqle) {
			log.warning("Database rollback failed.  You should probably restart the application.");
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
			} else {
				throw new SQLException("unexpected param type: " + v.getClass());
			}
		}
	}	
	
	@Override
	public void executeUpdate(String sql, List<Object> args) throws SQLException 
	{
		PreparedStatement p = conn.prepareStatement(sql);
		bindParam(p, args);
		p.executeUpdate();
		p.close();
		checkNotifications();		
	}

	@Override
	public void executeGroupUpdate(String sql, List<List<Object>> args) throws SQLException 
	{
		PreparedStatement p = conn.prepareStatement(sql);
		for (List<Object> l : args) {
			bindParam(p, l);
			p.executeUpdate();
		}
		p.close();
		checkNotifications();
	}


	@Override 
	public ResultSet executeSelect(String sql, List<Object> args) throws SQLException
	{
		PreparedStatement p = conn.prepareStatement(sql);
		if (args != null)
			bindParam(p, args);
		ResultSet s = p.executeQuery();
		synchronized(leftovers) {
			leftovers.put(s,  p);
		}
		checkNotifications();
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
			PreparedStatement p = conn.prepareStatement(sql);
			if (args != null)
				bindParam(p, args);
			ResultSet s = p.executeQuery();
			while (s.next()) {
				result.add(objc.newInstance(s));
			}
			s.close();
			p.close();
			checkNotifications();
			return result;
		} 
		catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
		{
			throw new SQLException(e);
		}
	}
}
