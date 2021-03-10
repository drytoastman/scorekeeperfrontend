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
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Hashtable;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.JFrame;

public class Prefs
{
    public interface PrefsInterface {
        public String get(String key, String def);
        public int getInt(String key, int def);
        public boolean getBoolean(String key, boolean def);
        public void put(String key, String val);
        public void putInt(String key, int val);
        public void putBoolean(String key, boolean val);
        public void sync();
    }

    static class JavaPrefsWrapper implements PrefsInterface {
        Preferences p;
        public JavaPrefsWrapper(Preferences inp) { p = inp; }
        @Override public String get(String key, String def) { return p.get(key, def); }
        @Override public int getInt(String key, int def) { return p.getInt(key, def); }
        @Override public boolean getBoolean(String key, boolean def) { return p.getBoolean(key, def); }
        @Override public void put(String key, String val) { p.put(key, val); }
        @Override public void putInt(String key, int val) { p.putInt(key, val); }
        @Override public void putBoolean(String key, boolean val) { p.putBoolean(key, val); }
        @Override public void sync() { try { p.sync(); } catch (BackingStoreException e) {} }
    }

    static class TestMemoryPrefs implements PrefsInterface {
        Map<String, Object> map;
        public TestMemoryPrefs() {  map = new Hashtable<String, Object>(); }
        @Override public String get(String key, String def) { return (String)map.getOrDefault(key, def); }
        @Override public int getInt(String key, int def) { return (int)map.getOrDefault(key, def); }
        @Override public boolean getBoolean(String key, boolean def) { return (boolean)map.getOrDefault(key, def); }
        @Override public void put(String key, String val) { map.put(key, val); }
        @Override public void putInt(String key, int val) { map.put(key, val); }
        @Override public void putBoolean(String key, boolean val) { map.put(key, val); }
        @Override public void sync() {}
    }

    /**
     * During regular init, our default is to use the regular Java preferences for our base
     */
    private static PrefsInterface prefs;
    private static String os;
    static
    {
        prefs = new JavaPrefsWrapper(Preferences.userNodeForPackage(Prefs.class));
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() { prefs.sync(); }}));
        os = System.getProperty("os.name").toLowerCase();
    }

    /**
     * During testing, we use a memory map backing so changes are not permanent
     */
    public static void setTestMode()
    {
        prefs = new TestMemoryPrefs();
    }

    public static boolean isTestMode()
    {
        return (prefs instanceof TestMemoryPrefs);
    }


    /* ***************************************************************************** */


    public static boolean isDebug()
    {
        return (System.getenv("DEBUG") != null);
    }

    public static boolean isWindows()
    {
        return os.contains("windows");
    }

    public static boolean isLinux()
    {
        return os.contains("linux");
    }

    public static String getFullVersion()
    {
        String s = System.getenv("OVERRIDE_VERSION");
        if (s != null) return s;

        Package p = Prefs.class.getPackage();
        if (p == null) return "latest";
        String v = p.getImplementationVersion();
        if (v == null) return "latest";
        return v;
    }

    public static String getVersionBase()
    {
        String v = getFullVersion();
        int i1 = v.indexOf(".");
        if (i1 < 0)
            return v;
        int i2 = v.indexOf(".", i1+1);
        if (i2 < 0)
            return v;
        return v.substring(0, i2);
    }

    private static Path ensureDirectory(Path p)
    {
        File f = p.toFile();
        if (!f.exists())
            f.mkdirs();
        return p;
    }

    public static Path getRootDir()
    {
        return ensureDirectory(Paths.get(System.getProperty("user.home"), "scorekeeper"));
    }

    public static Path getLockFilePath(String name)
    {
        return getRootDir().resolve(name+".lock");
    }

    public static Path getLogDirectory()
    {
        return ensureDirectory(getRootDir().resolve(Paths.get(getVersionBase(), "logs")));
    }

    public static Path getBackupDirectory()
    {
        return ensureDirectory(getRootDir().resolve(Paths.get(getVersionBase(), "backup")));
    }

    public static UUID getServerId()
    {
        UUID ret;
        String s = prefs.get("serverid", "");
        if (s.equals("")) {
            ret = IdGenerator.generateId();
            prefs.put("serverid", ret.toString());
        } else {
            ret = UUID.fromString(s);
        }

        return ret;
    }

    public static String getCookieSecret()
    {
        String ret = prefs.get("cookiesecret", "");
        if (ret.equals("")) {
            byte[] bytes = new byte[32];
            new Random().nextBytes(bytes);
            ret = new String(Base64.getEncoder().encode(bytes));
            prefs.put("cookiesecret", ret);
        }
        return ret;
    }


    public static String getSeries(String def) { return prefs.get("series", def); }
    public static int getEventIndex(int def)   { return prefs.getInt("eventindex", def); }
    public static int getChallengeIndex(int def) { return prefs.getInt("challengeindex", def); }
    public static boolean useReorderingTable() { return prefs.getBoolean("reorderingtable", false); }
    public static int getLightCount()          { return prefs.getInt("lights", 2); }
    public static String getDefaultPrinter()   { return prefs.get("defaultprinter", ""); }
    public static boolean usePaidFlag()        { return prefs.getBoolean("paidflag", false); }
    public static boolean getAllowDiscovery()  { return prefs.getBoolean("allowdiscovery", true); }
    public static boolean getPrintDirectly()   { return prefs.getBoolean("printdirectly", true); }
    public static AppLogLevel getLogLevel()    { return new AppLogLevel(prefs.get("loglevel", "INFO")); }
    public static String getIssuer()           { return prefs.get("issuer", ""); }
    public static String getIssuerMem()        { return prefs.get("issuermem", ""); }
    public static boolean getSkip53()          { return prefs.getBoolean("skip53", false); }
    public static boolean getSkip80()          { return prefs.getBoolean("skip80", false); }

    public static String getScannerConfig(String type) { return prefs.get("scannerconfig-"+type, ""); }
    public static String getScannerInput() { return prefs.get("scannerinput", ""); }

    public static Rectangle getWindowBounds(String p)
    {
        Rectangle r = new Rectangle();
        r.x = prefs.getInt(p+".x", 0);
        r.y = prefs.getInt(p+".y", 0);
        r.width = prefs.getInt(p+".width", 1080);
        r.height = prefs.getInt(p+".height", 600);
        return r;
    }

    public static void setSeries(String s)           { prefs.put("series", s); }
    public static void setEventIndex(int i)          { prefs.putInt("eventindex", i); }
    public static void setChallengeIndex(int i)      { prefs.putInt("challengeindex", i); }
    public static void setReorderingTable(boolean b) { prefs.putBoolean("reorderingtable", b); }
    public static void setLightCount(int i)          { prefs.putInt("lights", i); }
    public static void setDefaultPrinter(String s)   { prefs.put("defaultprinter", s); }
    public static void setUsePaidFlag(boolean b)     { prefs.putBoolean("paidflag", b); }
    public static void setAllowDiscovery(boolean b)  { prefs.putBoolean("allowdiscovery", b); }
    public static void setPrintDirectly(boolean b)   { prefs.putBoolean("printdirectly", b); }
    public static void setLogLevel(AppLogLevel l)    { prefs.put("loglevel", l.getName()); }
    public static void setIssuer(String s)           { prefs.put("issuer", s); }
    public static void setIssuerMem(String s)        { prefs.put("issuermem", s); }
    public static void setSkip53(boolean b)          { prefs.putBoolean("skip53", b); }
    public static void setSkip80(boolean b)          { prefs.putBoolean("skip80", b); }

    public static void setScannerConfig(String type, String s) { prefs.put("scannerconfig-"+type, s); }
    public static void setScannerInput(String s) { prefs.put("scannerinput", s); }

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
