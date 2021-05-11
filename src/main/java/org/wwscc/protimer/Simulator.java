/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.wwscc.protimer;

import org.wwscc.util.MT;
import org.wwscc.util.Messenger;

/**
 * Simulate events from hardware without need for hardware.
 */
public class Simulator
{
    public Simulator()
    {
    }

    public void reaction(boolean left, double time, String status)
    {
        Messenger.sendEvent(MT.SERIAL_GENERIC_DATA, String.format("rt %s %s %s", left ? "L":"R", time, status));
    }

    public void sixty(boolean left, double time)
    {
        Messenger.sendEvent(MT.SERIAL_GENERIC_DATA, String.format("sixty %s %s", left ? "L":"R", time));
    }

    public void finish(boolean left, double time, double dial)
    {
        Messenger.sendEvent(MT.SERIAL_GENERIC_DATA, String.format("fin %s %s", left ? "L":"R", time));
    }

    public void tree()
    {
        Messenger.sendEvent(MT.SERIAL_GENERIC_DATA, "TREE");
    }

    public void runMode()
    {
        Messenger.sendEvent(MT.SERIAL_GENERIC_DATA, "RUN");
    }

    public void alignMode()
    {
        Messenger.sendEvent(MT.SERIAL_GENERIC_DATA, "ALIGN");
    }

    public void error()
    {
        Messenger.sendEvent(MT.SERIAL_GENERIC_DATA, "Error: No run slots left, resettingTREE");
    }

    /*
    public void dialleft(double d)
    {
        Messenger.sendEvent(MT.DIALIN_LEFT, d);
    }

    public void dialright(double d)
    {
        Messenger.sendEvent(MT.DIALIN_RIGHT, d);
    }

    public void win(boolean left)
    {
        Messenger.sendEvent(left?MT.WIN_LEFT:MT.WIN_RIGHT, null);
    }

    public void lead(boolean left, double time)
    {
        Messenger.sendEvent(left?MT.LEAD_LEFT:MT.LEAD_RIGHT, time);
    }

    public void challengewin(boolean left, double time)
    {
        Messenger.sendEvent(left?MT.CHALWIN_LEFT:MT.CHALWIN_RIGHT, time);
    }

    public void overdial(boolean left, double time, double dial)
    {
        Messenger.sendEvent(left?MT.CHALDIAL_LEFT:MT.CHALDIAL_RIGHT, new Double[] { time, dial } );
    }

    public void breakout(boolean left, double time, double dial)
    {
        Messenger.sendEvent(left?MT.CHALDIAL_LEFT:MT.CHALDIAL_RIGHT, new Double[] {-time, dial } );
    }

    */
}
