/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.util;

import java.util.logging.Level;

public class AppLogLevel
{
    public enum ALevel { Minimal, Normal, Maximum };
    AppLogLevel.ALevel level;

    public AppLogLevel(String name)
    {
        try {
            level = ALevel.valueOf(name);
        } catch (Exception e) {
            level = ALevel.Normal;
        }
    }

    public String getName()
    {
        return level.name();
    }

    public ALevel getLevel()
    {
        return level;
    }

    public Level getJavaLevel()
    {
        switch (level) {
            case Minimal: return Level.WARNING;
            case Maximum: return Level.FINEST;
            case Normal:
            default:      return Level.INFO;
        }
    }

    public String getPythonLevel()
    {
        switch (level) {
            case Minimal: return "WARNING";
            case Maximum: return "DEBUG";
            case Normal:
            default:      return "INFO";
        }
    }
}
