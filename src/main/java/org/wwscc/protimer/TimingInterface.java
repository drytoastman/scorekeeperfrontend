/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.protimer;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.wwscc.storage.LeftRightDialin;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.SerialPortUtil.LineBasedSerialPort;


public class TimingInterface implements MessageListener
{
    private static Logger log = Logger.getLogger(TimingInterface.class.getCanonicalName());
    LineBasedSerialPort serial;

    public TimingInterface()
    {
        Messenger.register(MT.SERIAL_GENERIC_DATA, this);
        Messenger.register(MT.CONTROL_DATA, this);

        Messenger.register(MT.TIMER_SERVICE_DIALIN, this);
        Messenger.register(MT.TIMER_SERVICE_DIALIN_L, this);
        Messenger.register(MT.TIMER_SERVICE_DIALIN_R, this);
        Messenger.register(MT.INPUT_SET_DIALIN, this);
        Messenger.register(MT.INPUT_SET_RUNMODE, this);
        Messenger.register(MT.INPUT_SET_ALIGNMODE, this);
        Messenger.register(MT.INPUT_SHOW_INPROGRESS, this);
        Messenger.register(MT.INPUT_SHOW_STATE, this);
        Messenger.register(MT.INPUT_RESET_SOFT, this);
        Messenger.register(MT.INPUT_RESET_HARD, this);

        Messenger.register(MT.INPUT_DELETE_FINISH_LEFT, this);
        Messenger.register(MT.INPUT_DELETE_FINISH_RIGHT, this);
        Messenger.register(MT.INPUT_DELETE_START_LEFT, this);
        Messenger.register(MT.INPUT_DELETE_START_RIGHT, this);

        Messenger.register(MT.INPUT_FINISH_LEFT, this);
        Messenger.register(MT.INPUT_FINISH_RIGHT, this);
        Messenger.register(MT.INPUT_START_LEFT, this);
        Messenger.register(MT.INPUT_START_RIGHT, this);

        Messenger.register(MT.INPUT_RUNS_IN_PROGRESS, this);
        Messenger.register(MT.INPUT_TEXT, this);

        // start in disconnected state
        Messenger.sendEvent(MT.SERIAL_PORT_CLOSED, null);
    }


    public void openPort(String port)
    {
        if (serial != null) {
            serial.close();
        }
        try {
            serial = new LineBasedSerialPort(port, buf -> Messenger.sendEvent(MT.SERIAL_GENERIC_DATA, new String(buf)));
        } catch (Exception e) {
            log.log(Level.WARNING, "\bUnable to open " + port + ": " + e, e);
        }
    }

    @Override
    public void event(MT type, Object o)
    {
        switch (type)
        {
            case SERIAL_GENERIC_DATA:
                try {
                    processData((String)o);
                } catch (Exception pse) {
                    log.log(Level.WARNING, "Error processing data: {0}", pse); // don't dialog
                }
                break;

            case CONTROL_DATA:
                String data[] = ((String)o).split("\\s+");
                if ((data.length == 3) && (data[0].equals("DIAL")))
                    doCommand("DIAL L " + data[1]);
                    doCommand("DIAL R " + data[2]);
                break;

            case TIMER_SERVICE_DIALIN_L:
                doCommand("DIAL L " + (double)o);
                break;

            case TIMER_SERVICE_DIALIN_R:
                doCommand("DIAL L " + (double)o);
                break;

            case TIMER_SERVICE_DIALIN:
            case INPUT_SET_DIALIN:
                LeftRightDialin d = (LeftRightDialin)o;
                doCommand("DIAL L " + d.left);
                doCommand("DIAL R " + d.right);
                break;

            case INPUT_SET_RUNMODE: doCommand("RUN"); break;
            case INPUT_SET_ALIGNMODE: doCommand("ALIGN"); break;
            case INPUT_SHOW_INPROGRESS: doCommand("SIP"); break;
            case INPUT_SHOW_STATE: doCommand("SS"); break;
            case INPUT_RESET_SOFT: doCommand("RESET"); break;
            case INPUT_RESET_HARD: doCommand("HARD"); break;

            case INPUT_DELETE_FINISH_LEFT: doCommand("DF L"); break;
            case INPUT_DELETE_FINISH_RIGHT: doCommand("DF R"); break;
            case INPUT_DELETE_START_LEFT: doCommand("DS L"); break;
            case INPUT_DELETE_START_RIGHT: doCommand("DS R"); break;


            case INPUT_START_LEFT: doCommand("START L"); break;
            case INPUT_START_RIGHT: doCommand("START R"); break;
            case INPUT_FINISH_LEFT: doCommand("FIN L"); break;
            case INPUT_FINISH_RIGHT: doCommand("FIN R"); break;

            case INPUT_RUNS_IN_PROGRESS: doCommand("NIP"); break;
            case INPUT_TEXT: doCommand((String)o); break;
        }
    }


    public void doCommand(String command)
    {
        if (serial != null)
        {
            log.log(Level.INFO, "OUT: {0}", command);
            Messenger.sendEvent(MT.SENDING_SERIAL, command);
            serial.write(command + "\r");
        }
        else
        {
            log.log(Level.FINE, "Dropping command, no serial port: {0}", command);
        }
    }


    private boolean get_side(String s[], int n)
    {
        if ((s.length > n) && (s[n].equalsIgnoreCase("L")))
            return true;
        else
            return false;
    }


    private double get_value(String s[], int n)
    {
        if (s.length > n)
        {
            try { return Double.parseDouble(s[n]); }
            catch (NumberFormatException nfe) {}
        }
        return 0.0;
    }


    private int get_color(String s[], int n)
    {
        if (s.length > n)
        {
            if (s[n].equalsIgnoreCase("redlight"))
                return ColorTime.REDLIGHT;
            else if (s[n].equalsIgnoreCase("not_staged"))
                return ColorTime.NOTSTAGED;
        }
        return ColorTime.NORMAL;
    }


    public void processData(String input)
    {
        log.log(Level.FINEST, "\"{0}\"", input);
        String args[] = input.split("[ \r\n]+");

        boolean left;
        double  time;
        double  dial;

        /* Process comand */
        if (args[0].equalsIgnoreCase("rt"))
        {
            left = get_side(args, 1);
            ColorTime c = new ColorTime(get_value(args, 2), get_color(args, 3));
            Messenger.sendEvent(left?MT.REACTION_LEFT:MT.REACTION_RIGHT, c);
        }

        else if (args[0].equalsIgnoreCase("sixty"))
        {
            left = get_side(args, 1);
            ColorTime c = new ColorTime(get_value(args, 2), get_color(args, 3));
            Messenger.sendEvent(left?MT.SIXTY_LEFT:MT.SIXTY_RIGHT, c);
        }

        else if (args[0].equalsIgnoreCase("fin"))
        {
            left = get_side(args, 1);
            ColorTime c = new ColorTime(get_value(args, 2), get_color(args, 4));
            Double d = get_value(args, 3);
            Object [] result = { c, d };

            Messenger.sendEvent(left?MT.FINISH_LEFT:MT.FINISH_RIGHT, result);
        }

        else if (args[0].equalsIgnoreCase("win"))
        {
            left = get_side(args, 1);
            Messenger.sendEvent(left?MT.WIN_LEFT:MT.WIN_RIGHT, null);
        }

        else if (args[0].equalsIgnoreCase("lead"))
        {
            left = get_side(args, 1);
            time = get_value(args, 2);
            Messenger.sendEvent(left?MT.LEAD_LEFT:MT.LEAD_RIGHT, time);
        }

        else if (args[0].equalsIgnoreCase("challenge"))
        {
            left  = get_side(args, 2);
            time   = get_value(args, 3);
            dial   = get_value(args, 4);

            if (args[1].equalsIgnoreCase("overdial"))
            {
                Messenger.sendEvent(left?MT.CHALDIAL_LEFT:MT.CHALDIAL_RIGHT, new Double[] { time, dial } );
            }
            else if (args[1].equalsIgnoreCase("breakout"))
            {
                Messenger.sendEvent(left?MT.CHALDIAL_LEFT:MT.CHALDIAL_RIGHT, new Double[] {-time, dial } );
            }
            else if (args[1].equalsIgnoreCase("win"))
            {
                Messenger.sendEvent(left?MT.CHALWIN_LEFT:MT.CHALWIN_RIGHT, time);
            }
        }
        else if (args[0].equalsIgnoreCase("dial"))
        {
            left = get_side(args, 1);
            time = get_value(args, 2);
            Messenger.sendEvent((left)?MT.DIALIN_LEFT:MT.DIALIN_RIGHT, time);

            if (args.length >= 5)
            {
                left = get_side(args, 3);
                time = get_value(args, 4);
                Messenger.sendEvent((left)?MT.DIALIN_LEFT:MT.DIALIN_RIGHT, time);
            }
        }
        else if (args[0].equalsIgnoreCase("open"))
        {
            left = get_side(args, 1);
            Messenger.sendEvent(MT.OPEN_SENSOR, new Object[] { left, args[2] });
        }
        else if (args[0].equalsIgnoreCase("tree"))
        {
            Messenger.sendEvent(MT.TREE, null);
        }
        else if (args[0].equalsIgnoreCase("ds"))
        {
            left = get_side(args, 1);
            Messenger.sendEvent((left)?MT.DELETE_START_LEFT:MT.DELETE_START_RIGHT, null);
        }
        else if (args[0].equalsIgnoreCase("df"))
        {
            left = get_side(args, 1);
            Messenger.sendEvent((left)?MT.DELETE_FINISH_LEFT:MT.DELETE_FINISH_RIGHT, null);
        }
        else if (args[0].equalsIgnoreCase("run"))
        {
            Messenger.sendEvent(MT.RUN_MODE, null);
        }
        else if (args[0].equalsIgnoreCase("align"))
        {
            Messenger.sendEvent(MT.ALIGN_MODE, null);
        }
        else if (args[0].equalsIgnoreCase("nip") && args.length > 1)
        {
            try {
                Messenger.sendEvent(MT.RUNS_IN_PROGRESS, new int[] { Integer.parseInt(args[1]), Integer.parseInt(args[2]) });
            } catch (NumberFormatException|ArrayIndexOutOfBoundsException e) {
                log.log(Level.WARNING, "invalid nip data: \"{0}\"", input);
            }
        }
        else if (input.toLowerCase().contains("reset"))
        {
            log.warning("Reset notice from Pro Hardware");
            Messenger.sendEvent(MT.PRO_RESET, null);
        }
    }
}

