package org.wwscc.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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

    // Sigh.. if only we had kwargs
    public static int execit(ProcessBuilder in, int waitms) { return execit(in, null, waitms); }
    public static int execit(ProcessBuilder in, byte[] output, int waitms)
    {
        return execit(in, output, waitms, false);
    }

    public static int execit(ProcessBuilder in, byte[] output)        { return execit(in, output, false); }
    public static int execit(ProcessBuilder in, boolean ignoreerrors) { return execit(in,   null, ignoreerrors); }
    public static int execit(ProcessBuilder in, byte[] output, boolean ignoreerrors)
    {
        return execit(in, output, -1, ignoreerrors);
    }

    public static int execit(ProcessBuilder in, byte[] output, int waitms, boolean ignoreerrors)
    {
        try {
            log.log(Level.FINER, "Running {0}", in.command().toString());

            Process p = in.start();
            int ret;

            if (waitms > 0) {
                if (!p.waitFor(waitms, TimeUnit.MILLISECONDS)) {
                    p.destroy();
                    throw new IOException("Process did not complete quick enough");
                }
                ret = p.exitValue();
            } else {
                ret = p.waitFor();
            }

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
                        if (!ignoreerrors)
                            log.log(Level.INFO, "Exec Error: ({0}): {1}", new Object[] { in.command().toString(), new String(output, 0, len).trim() });
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
            if (!ignoreerrors)
                log.log(Level.WARNING, "Exec Exception: ({0}): {1}", new Object[] { in.command().toString(), ie });
        }
        return -1;
    }
}
