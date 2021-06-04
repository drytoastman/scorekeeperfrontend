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

    public void text(String text)
    {
        Messenger.sendEvent(MT.SERIAL_GENERIC_DATA, text);
    }
}
