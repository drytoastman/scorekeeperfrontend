package org.wwscc.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Exec 
{
    private static final Logger log = Logger.getLogger(Exec.class.getName());
    private static final Logger sublog = Logger.getLogger(Exec.class.getName()+".subprocess");
    
    public static ProcessBuilder build(Map <String, String> addenv, String ... cmd)
    {
        List<String> cmdlist = new ArrayList<String>();
        for (String s : cmd)
            cmdlist.add(s);
        return build(addenv, cmdlist);
    }

    public static ProcessBuilder build(Map <String, String> addenv, List<String> cmdlist)
    {
        ProcessBuilder p = new ProcessBuilder(cmdlist);
        p.redirectErrorStream(true);
        if (addenv != null) 
        {
            Map <String,String> env = p.environment();
            env.putAll(addenv);
        }
        return p;
    }
    
    public static int execit(ProcessBuilder in, byte[] output)
    {
        try {
            Process p = in.start();
            int ret = p.waitFor();
            log.log(Level.FINER, "{0} returns {1}", new Object [] { in.command().toString(), ret });
            
            if ((output == null) && ret != 0) // create buffer if there are errors but one not present so we can log
            {
                output = new byte[8192];
            }
            
            if (output != null) // read stream if we have a buffer
            {
                InputStream is = p.getInputStream();
                int len = is.read(output);
                is.close();
                if (len > 0) {
                    if (ret != 0) {
                        log.log(Level.INFO, "Docker Error:\n " + new String(output, 0, len));
                    } else {
                        sublog.log(Level.FINEST, "Execution Output:\n " + new String(output, 0, len));
                    }
                } else {
                    log.log(Level.INFO, "No output from docker command");
                }
            }
            
            p.destroy();            
            return ret;
        } catch (InterruptedException | IOException ie) {
            log.log(Level.WARNING, "Exec failed " + ie, ie);
        }
        return -1;
    }
    
    /**
     * Just testing to see if it executes cleanly or not.  Don't log errors or look at output
     * @param in the process to test
     * @return true if execution was successful, false if not 
     */
    public static boolean testit(ProcessBuilder in)
    {
        try {
            log.log(Level.FINE, "testing {0}", in.command().toString());
            Process p = in.start();
            return p.waitFor() == 0;
        } catch (InterruptedException | IOException ie) {
        }
        return false;
    }
}
