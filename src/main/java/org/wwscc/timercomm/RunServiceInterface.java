/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2012 Brett Wilson.
 * All rights reserved.
 */
package org.wwscc.timercomm;

import java.util.EventListener;
import org.wwscc.storage.LeftRightDialin;
import org.wwscc.storage.Run;

/**
 * Abstraction of timer service so it can be a listener for others
 */
public interface RunServiceInterface extends EventListener
{
    public boolean sendTree();
    public boolean sendDial(LeftRightDialin d);
    public boolean sendLDial(double l);
    public boolean sendRDial(double r);
    public boolean deleteRun(Run.WithRowId r);
    public boolean sendRun(Run.WithRowId r);
}
