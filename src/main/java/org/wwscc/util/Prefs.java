/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */
package org.wwscc.util;

import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.prefs.Preferences;

import javax.swing.JFrame;

public class Prefs
{
	private static Preferences prefs;
	
	static
	{
		prefs = Preferences.userNodeForPackage(Prefs.class);
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			public void run() {
				try { prefs.sync(); } catch (Exception e) { }
			}
		}));
	}

	public static void setPrefsNode(String name)
	{
		prefs = Preferences.userRoot().node(name);
	}
	
    public static String getDocRoot() 
    {
    	if (System.getenv("DEBUG") != null)
    		return Paths.get(".").toString();
    	return Paths.get(System.getProperty("user.home"), "scorekeeper").toString();
    }

	public static String getLogDirectory()
	{
		return FileSystems.getDefault().getPath(getDocRoot(), "logs").toString();
	}

	public static UUID getServerId() 
	{
	    UUID ret = IdGenerator.nullid;
	    String s = prefs.get("serverid", "");
	    if (s.equals("")) {
	        ret = IdGenerator.generateId();
	        prefs.put("serverid", ret.toString());
	    } else {
	        ret = UUID.fromString(s);
	    }

	    return ret;
	}

	public static String getHomeServer() { return prefs.get("hostname", "scorekeeper.wwscc.org"); }
	public static String getPasswordFor(String series) { return prefs.get("password-"+series, ""); }
	public static String getSeries(String def) { return prefs.get("series", def); }
	public static int getEventId(int def) { return prefs.getInt("eventid", def); }
	public static int getChallengeId(int def) { return prefs.getInt("challengeid", def); }
	public static boolean useReorderingTable() { return prefs.getBoolean("reorderingtable", false); }
	public static int getLightCount() { return prefs.getInt("lights", 2); }
	public static String getScannerConfig() { return prefs.get("scannerconfig", ""); }
	public static String getDefaultPrinter() { return prefs.get("defaultprinter", ""); }
	public static boolean usePaidFlag() { return prefs.getBoolean("paidflag", false); }
	public static boolean getAllowDiscovery() { return prefs.getBoolean("allowdiscovery", true); }
	public static Rectangle getWindowBounds(String p)
	{
		Rectangle r = new Rectangle();
		r.x = prefs.getInt(p+".x", 0);
		r.y = prefs.getInt(p+".y", 0);
		r.width = prefs.getInt(p+".width", 1080);
		r.height = prefs.getInt(p+".height", 600);
		return r;
	}

	public static void setHomeServer(String s) { prefs.put("hostname", s); }
	public static void setPasswordFor(String series, String s) { prefs.put("password-"+series, s); }
	public static void setSeries(String s) { prefs.put("series", s); }
	public static void setEventId(int i) { prefs.putInt("eventid", i); }
	public static void setChallengeId(int i) { prefs.putInt("challengeid", i); }
	public static void setReorderingTable(boolean b) { prefs.putBoolean("reorderingtable", b); }
	public static void setLightCount(int i) { prefs.putInt("lights", i); }
	public static void setScannerConfig(String s) { prefs.put("scannerconfig", s); }
	public static void setDefaultPrinter(String s) { prefs.put("defaultprinter", s); }
	public static void setUsePaidFlag(boolean b) { prefs.putBoolean("paidflag", b); }
	public static void setAllowDiscovery(boolean b) { prefs.putBoolean("allowdiscovery", b); }
	public static void setWindowBounds(String p, Rectangle r)
	{
		prefs.putInt(p + ".x", r.x);
		prefs.putInt(p + ".y", r.y);
		prefs.putInt(p + ".width", r.width);
		prefs.putInt(p + ".height", r.height);
	}
	
	public static void trackWindowBounds(JFrame frame, String prefix)
	{
        frame.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                setWindowBounds(prefix, e.getComponent().getBounds());
            }
            public void componentMoved(ComponentEvent e) {
                setWindowBounds(prefix, e.getComponent().getBounds());
            }
        });
	}
}
