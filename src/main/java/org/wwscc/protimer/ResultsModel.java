/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */


package org.wwscc.protimer;

import java.util.UUID;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.table.AbstractTableModel;
import org.wwscc.storage.Run;
import org.wwscc.timercomm.RunServiceInterface;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;


public class ResultsModel extends AbstractTableModel implements MessageListener
{
    private static final Logger log = Logger.getLogger(ResultsModel.class.getCanonicalName());

    Vector<DualResult> runs;
    Vector<RunServiceInterface> listeners;
    int nextLeftFinish;
    int nextRightFinish;
    int resetLine;

    double holdLeftDial;
    double holdRightDial;

    public ResultsModel()
    {
        super();
        runs = new Vector<DualResult>();
        listeners = new Vector<RunServiceInterface>();

        Messenger.register(MT.TREE, this);

        Messenger.register(MT.DIALIN_LEFT, this);
        Messenger.register(MT.DIALIN_RIGHT, this);

        Messenger.register(MT.REACTION_LEFT, this);
        Messenger.register(MT.REACTION_RIGHT, this);
        Messenger.register(MT.SIXTY_LEFT, this);
        Messenger.register(MT.SIXTY_RIGHT, this);
        Messenger.register(MT.FINISH_LEFT, this);
        Messenger.register(MT.FINISH_RIGHT, this);

        Messenger.register(MT.WIN_LEFT, this);
        Messenger.register(MT.WIN_RIGHT, this);
        Messenger.register(MT.LEAD_LEFT, this);
        Messenger.register(MT.LEAD_RIGHT, this);
        Messenger.register(MT.CHALDIAL_LEFT, this);
        Messenger.register(MT.CHALDIAL_RIGHT, this);
        Messenger.register(MT.CHALWIN_LEFT, this);
        Messenger.register(MT.CHALWIN_RIGHT, this);

        Messenger.register(MT.DELETE_START_LEFT, this);
        Messenger.register(MT.DELETE_START_RIGHT, this);
        Messenger.register(MT.DELETE_FINISH_LEFT, this);
        Messenger.register(MT.DELETE_FINISH_RIGHT, this);

        Messenger.register(MT.INPUT_RESET_SOFT, this);
        Messenger.register(MT.INPUT_RESET_HARD, this);
        Messenger.register(MT.PRO_RESET, this);
        Messenger.register(MT.RUNS_IN_PROGRESS, this);

        nextLeftFinish    = 0;
        nextRightFinish   = 0;
        resetLine         = 0;
        holdLeftDial	= Double.NaN;
        holdRightDial	= Double.NaN;
        
        new Timer("StateCheckTimer").schedule(new TimerTask() { public void run() { Messenger.sendEvent(MT.INPUT_RUNS_IN_PROGRESS, null); } }, 1000, 60000);
    }

    public void addRunServerListener(RunServiceInterface l)
    {
        listeners.add(l);
    }

    @Override
    public int getRowCount()
    {
        return runs.size();
    }

    @Override
    public int getColumnCount()
    {
        return 7;
    }

    @Override
    public String getColumnName(int col)
    {
        switch (col)
        {
            case 0: return "Reaction";
            case 1: return "Sixty";
            case 2: return "Final";
            case 3: return "Message";
            case 4: return "Reaction";
            case 5: return "Sixty";
            case 6: return "Final";
        }

        return "---";
    }


    @Override
    public Class<?> getColumnClass(int col)
    {
        if (col == 3) return DualResult.class;
        return ColorTime.class;
    }


    @Override
    public Object getValueAt(int row, int col)
    {
        if (row < runs.size())
        {
            ColorTime c = new ColorTime();
            DualResult dr = runs.get(row);
            switch (col)
            {
                case 0:	c = dr.getLeftReaction(); break;
                case 1:	c = dr.getLeftSixty(); break;
                case 2: c = dr.getLeftFinish(); break;

                case 3: return dr;

                case 4:	c = dr.getRightReaction(); break;
                case 5:	c = dr.getRightSixty(); break;
                case 6: c = dr.getRightFinish(); break;
            }

            /* This will cause a dial to show up under reaction, sixty and final */
            switch (col)
            {
                case 0:
                    c.dial = dr.getLeftOrigDial();
                    break;
                case 4:
                    c.dial = dr.getRightOrigDial();
                    break;
            }

            /* Generally return just ColorTime, except for middle which returns entire DualResult */
            return c;
        }

        return new ColorTime(Double.NaN, 0);
    }


    protected DualResult lastEntry()
    {
        if (runs.isEmpty())
            return null;
        else
            return runs.lastElement();
    }


    protected DualResult getLastFinishLine()
    {
        int index;
        if (nextLeftFinish > nextRightFinish) index = nextLeftFinish; else index = nextRightFinish;
        return runs.get(index-1);
    }

    public int getLastLeftFinish() { return nextLeftFinish - 1; }
    public int getLastRightFinish() { return nextRightFinish - 1; }


    @Override
    public void event(MT type, Object o)
    {
        try
        {
            Object[] result;
            Double[] doubles;

            switch (type)
            {
                case TREE:
                    createNewEntry();
                    break;

                case DIALIN_LEFT:
                    holdLeftDial = (Double)o;
                    for (RunServiceInterface l : listeners)
                        l.sendLDial(holdLeftDial);
                    break;

                case DIALIN_RIGHT:
                    holdRightDial = (Double)o;
                    for (RunServiceInterface l : listeners)
                        l.sendRDial(holdRightDial);
                    break;

                case REACTION_LEFT:
                    addReaction(true, (ColorTime)o);
                    break;
                case REACTION_RIGHT:
                    addReaction(false, (ColorTime)o);
                    break;

                case SIXTY_LEFT:
                    addSixty(true, (ColorTime)o);
                    break;
                case SIXTY_RIGHT:
                    addSixty(false, (ColorTime)o);
                    break;

                case FINISH_LEFT:
                    result = (Object[])o;
                    addFinish(true, (ColorTime)result[0], (Double)result[1]);
                    break;
                case FINISH_RIGHT:
                    result = (Object[])o;
                    addFinish(false, (ColorTime)result[0], (Double)result[1]);
                    break;

                case WIN_LEFT:			getLastFinishLine().setLeftWin(); break;
                case WIN_RIGHT:			getLastFinishLine().setRightWin(); break;
                case LEAD_LEFT:			getLastFinishLine().setLeftLead(); break;
                case LEAD_RIGHT:		getLastFinishLine().setRightLead(); break;
                case CHALWIN_LEFT:		getLastFinishLine().setLeftChallengeWin(); break;
                case CHALWIN_RIGHT:		getLastFinishLine().setRightChallengeWin(); break;

                case CHALDIAL_LEFT:
                    doubles = (Double[])o;
                    getLastFinishLine().setLeftChallengeDial(doubles[0], doubles[1]);
                    break;
                case CHALDIAL_RIGHT:
                    doubles = (Double[])o;
                    getLastFinishLine().setRightChallengeDial(doubles[0], doubles[1]);
                    break;


                case DELETE_START_LEFT:     deleteStart(true); break;
                case DELETE_START_RIGHT:    deleteStart(false); break;

                case DELETE_FINISH_LEFT:	deleteFinish(true); break;
                case DELETE_FINISH_RIGHT:	deleteFinish(false); break;

                case INPUT_RESET_SOFT:
                case INPUT_RESET_HARD:
                case PRO_RESET:
                    createNewEntry();
                    resetLine = nextLeftFinish = nextRightFinish = runs.size() - 1;
                    Messenger.sendEvent(MT.NIP_ERROR, null);
                    break;
                
                case RUNS_IN_PROGRESS:
                    int hardware[] = (int[])o;
                    int left = 0, right = 0;
                    boolean ld = false, rd = false;
                    
                    for (int ii = runs.size() - 1; ii >= resetLine; ii--) {
                        DualResult dr = runs.get(ii);
                        if (dr.left.completeRun()) {
                            ld = true;
                        } else if (!ld && dr.left.inProgress()) {
                            left++;
                        }
                        if (dr.right.completeRun()) {
                            rd = true;
                        } else if (!rd && dr.right.inProgress()) {
                            right++;
                        }
                        if (ld && rd) break;
                    }

                    String nip = "";
                    if ( left != hardware[0]) nip += String.format( "left (%d != %d) ", left, hardware[0]);
                    if (right != hardware[1]) nip += String.format("right (%d != %d)", right, hardware[1]);

                    if (nip.length() > 0) {
                        log.log(Level.SEVERE, "Software is not in the same state as the hardware! {0}", nip);
                        Messenger.sendEvent(MT.NIP_ERROR, nip);
                    } else {
                        Messenger.sendEvent(MT.NIP_ERROR, null);
                    }
                    break;
            }

            fireTableDataChanged();
        }
        catch (NullPointerException npe)
        {
            log.info("null returned in processing");
        }
        catch (ArrayIndexOutOfBoundsException aobe)
        {
            log.log(Level.INFO, "error in processing: " + aobe.getMessage(), aobe);
        }
    }


    public void createNewEntry()
    {
        DualResult dr = lastEntry();
        if ((dr == null) || dr.hasLeftReaction() || dr.hasRightReaction())
        {
            runs.add(new DualResult());
            for (RunServiceInterface l : listeners)
                l.sendTree();
        }
    }


    public void addReaction(boolean left, ColorTime c)
    {
        DualResult dr = lastEntry();
        if (dr == null)
            return;
        if (left)
        {
            dr.setLeftReaction((ColorTime)c);
            dr.setLeftDial(holdLeftDial);
            holdLeftDial = Double.NaN;
            for (RunServiceInterface l : listeners)
                l.sendRun(resultToRun(dr.left, 1, dr.rowid));
        }
        else
        {
            dr.setRightReaction((ColorTime)c);
            dr.setRightDial(holdRightDial);
            holdRightDial = Double.NaN;
            for (RunServiceInterface l : listeners)
                l.sendRun(resultToRun(dr.right, 2, dr.rowid));
        }
    }


    public void addSixty(boolean left, ColorTime c)
    {
        DualResult dr = lastEntry();
        if (dr == null)
            return;
        if (left)
        {
            dr.setLeftSixty((ColorTime)c);
            for (RunServiceInterface l : listeners)
                l.sendRun(resultToRun(dr.left, 1, dr.rowid));
        }
        else
        {
            dr.setRightSixty((ColorTime)c);
            for (RunServiceInterface l : listeners)
                l.sendRun(resultToRun(dr.right, 2, dr.rowid));
        }
    }

    public void deleteStart(boolean left)
    {
        DualResult dr = lastEntry();
        if (dr == null)
            return;
        if (left)
            dr.deleteLeftStart();
        else
            dr.deleteRightStart();
    }

    public void deleteFinish(boolean left)
    {
        if (left)
        {
            if ((nextLeftFinish == 0) || (nextLeftFinish-1 >= runs.size()))
                return;
            DualResult dr = runs.get(nextLeftFinish-1);
            Result r = dr.deleteLeftFinish();
            if (r != null)
            {
                for (RunServiceInterface l : listeners)
                    l.deleteRun(resultToRun(r, 1, dr.rowid));
                nextLeftFinish--;
            }
        }
        else
        {
            if ((nextRightFinish == 0) || (nextRightFinish-1 >= runs.size()))
                return;
            DualResult dr = runs.get(nextRightFinish-1);
            Result r = dr.deleteRightFinish();
            if (r != null)
            {
                for (RunServiceInterface l : listeners)
                    l.deleteRun(resultToRun(r, 2, dr.rowid));
                nextRightFinish--;
            }
        }
    }


    public void addFinish(boolean left, ColorTime c, double dial)
    {
        if (Double.isNaN(c.time)) {
            log.warning("Trying to set finish time to NaN!");
            (new Throwable()).printStackTrace();
            return;
        }

        if (left)
        {
            if (nextLeftFinish >= runs.size())
                return;
            DualResult dr = runs.get(nextLeftFinish);
            while (!dr.hasLeftReaction())
            {
                nextLeftFinish++;
                dr = runs.get(nextLeftFinish);
            }

            dr.setLeftFinish(c, dial);
            nextLeftFinish++;

            for (RunServiceInterface l : listeners)
                l.sendRun(resultToRun(dr.left, 1, dr.rowid));
        }
        else
        {
            if (nextRightFinish >= runs.size())
                return;
            DualResult dr = runs.get(nextRightFinish);
            while (!dr.hasRightReaction())
            {
                nextRightFinish++;
                dr = runs.get(nextRightFinish);
            }

            dr.setRightFinish(c, dial);
            nextRightFinish++;

            for (RunServiceInterface l : listeners)
                l.sendRun(resultToRun(dr.right, 2, dr.rowid));
        }
    }

    private Run.WithRowId resultToRun(Result r, int course, UUID rowid)
    {
        Run.WithRowId run = new Run.WithRowId();
        run.setRaw(r.finish);
        run.setCourse(course);
        run.setReaction(r.rt);
        run.setSixty(r.sixty);
        run.setRowId(rowid);
        switch (r.state)
        {
            case Result.REDLIGHT: run.setStatus("RL"); break;
            case Result.NOTSTAGED: run.setStatus("NS"); break;
        }
        return run;
    }


    public void clear()
    {
        runs.clear();
        fireTableDataChanged();
        nextLeftFinish    = 0;
        nextRightFinish   = 0;
    }
}

