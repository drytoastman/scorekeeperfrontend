/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dataentry.tables;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.FocusManager;
import javax.swing.JOptionPane;
import javax.swing.table.AbstractTableModel;

import org.wwscc.dataentry.DataEntry;
import org.wwscc.storage.ClassData;
import org.wwscc.storage.Database;
import org.wwscc.storage.Driver;
import org.wwscc.storage.Entrant;
import org.wwscc.storage.Run;
import org.wwscc.system.docker.SyncPoker;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;

/*
 * The EntryModel class represents the data storage and data for the DataEntry application
 */
public class EntryModel extends AbstractTableModel implements MessageListener
{
    private static Logger log = Logger.getLogger(EntryModel.class.getCanonicalName());

    List<Entrant> tableData;
    int runoffset;
    int colCount;
    SyncPoker poker;

    public EntryModel()
    {
        super();
        tableData = null;
        runoffset = 1; /* based on number of non run columns before runs - 1 */
        colCount = 0;
        poker = new SyncPoker();

        Messenger.register(MT.EVENT_CHANGED, this);
        Messenger.register(MT.RUNGROUP_CHANGED, this);
    }

    public void addCar(UUID carid)
    {
        if (tableData == null) return;

        Entrant e = Database.d.loadEntrant(DataEntry.state.getCurrentEventId(), carid, DataEntry.state.getCurrentCourse(), true);
        if (e == null)
        {
            log.warning("\bFailed to fetch entrant data from database");
            return;
        }

        if (tableData.contains(e))
        {
            if (!Prefs.useReorderingTable())
            {
                log.log(Level.WARNING, "\bCarid {0} already in table, perhaps you want to enable constant staging mode under Event Options", carid);
                return;
            }
            tableData.remove(e); // remove it from position and following will readd at the end
        }
        else if (Database.d.isInOrder(DataEntry.state.getCurrentEventId(), carid, DataEntry.state.getCurrentCourse()))
        {
            log.log(Level.SEVERE, "\bCarid {0} already in use in another rungroup in this event", carid);
            return;
        }

        tableData.add(e);

        try {
            Database.d.registerCar(DataEntry.state.getCurrentEventId(), e.getCarId());
        } catch (Exception ioe) {
            log.log(Level.WARNING, "\bRegistration during car add failed: {0}" + ioe.getMessage(), ioe);
        }

        /* Two reasons for the using fireTableDataChanged vs inserted/updated
         *  1: having a single sorter on top of two tables with the same model means that inserted events
         *     get fired twice and cause indexing errors in the sorter/filterer
         *  2: for simplicity, do the same for updates
         */
        fireTableDataChanged();
        fireEntrantsChanged();
    }

    public void replaceCar(UUID carid, int row)
    {
        /* We are being asked to swap an entrant */
        Entrant old = tableData.get(row);
        Entrant newe = Database.d.loadEntrant(DataEntry.state.getCurrentEventId(), carid, DataEntry.state.getCurrentCourse(), true);
        if (newe == null)
        {
            log.warning("\bFailed to fetch data for the replacement car from the database");
            return;
        }

        try
        {
            Collection<Run> currentruns = old.getRuns();
            Database.d.swapRuns(currentruns, newe.getCarId());
            newe.setRuns(currentruns);
        }
        catch (Exception e)
        {
            log.log(Level.SEVERE, "\bFailed to swap runs: " + e, e);
            return;
        }

        tableData.set(row, newe);
        try { // once successful, make sure this new car is registered for later scans
            Database.d.registerCar(DataEntry.state.getCurrentEventId(), carid);
        } catch (Exception e) {
            log.log(Level.WARNING, "Unable to register new car when swapping: " + e, e);
        }
        fireRunsChanged(newe);
        fireEntrantsChanged();
        fireTableRowsUpdated(row, row);

        checkDeletePlaceholder(old);
    }

    public int getRowForEntrant(Entrant find)
    {
        for (int ii = 0; ii < tableData.size(); ii++)
        {
            if (tableData.get(ii).equals(find))
                return ii;
        }
        return -1;
    }

    @Override
    public int getRowCount()
    {
        if (tableData == null) return 0;
        return tableData.size();
    }

    @Override
    public int getColumnCount()
    {
        return colCount;
    }

    @Override
    public String getColumnName(int col)
    {
        if (col == 0) return "Class/#";
        if (col == 1) return "Entrant";
        return "Run " + (col - runoffset);
    }


    /**
     * Interface call, return the col class, either an Entrant or a Run.
     */
    @Override
    public Class<?> getColumnClass(int col)
    {
        if (col <= runoffset) return Entrant.class;
        return Run.class;
    }


    /**
     * Interface call returns false as we don't allows edits in the table itself.
     */
    @Override
    public boolean isCellEditable(int row, int col)
    {
        return false;
    }


    public boolean rowIsFull(int row)
    {
        Entrant e = tableData.get(row);
        if (e == null) return true;  // ????
        return (e.runCount() >= DataEntry.state.getCurrentEvent().getRuns());
    }

    public boolean isBest(int row, Run x)
    {
        Entrant e = tableData.get(row);
        if (e == null) return false;

        // check if anyone is better
        Run.NetOrder no = new Run.NetOrder(DataEntry.state.getCurrentEvent());
        for (Run r : e.getRuns())
        {
            if (r == x) continue;
            if (no.compare(x, r) > 0) return false;
        }
        return true;
    }

    /**
     * Get a value from the mode, either Entrant or Run.
     * @param row the row of the cell
     * @param col the col of the cell
     * @return an Entrant, a Run or null if invalid cell
     */
    public Object getValueAt(int row, int col)
    {
        if (tableData == null) return null;
        if (row >= tableData.size()) return null;
        if (row < 0) return null;

        Entrant e = tableData.get(row);
        if (e == null)
        {
            log.info("get("+row+","+col+") e is null");
            return null;
        }

        if (col <= runoffset) return e;
        return e.getRun((col-runoffset));
    }


    /**
     * Set a value in the table, either an Entrant or a Run
     * @param aValue the value to set
     * @param row the row of the cell
     * @param col the col of the cell (0,1 are Entrant, the rest are Run)
     */
    @Override
    public void	setValueAt(Object aValue, int row, int col)
    {
        if (tableData == null) return;
        if (row >= tableData.size()) return;

        Entrant e = tableData.get(row);

        // Setting the car/driver value
        if (col <= runoffset)
        {
            if (aValue instanceof Entrant)
            {
                log.warning("\bHow did you get here?");
                Thread.dumpStack();
            }
            else if (aValue == null)
            {
                /* We are being asked to delete this entry, check if they have runs first. */
                if (e.hasRuns())
                {
                    log.warning("\bCan't remove an entrant that has runs");
                    return;
                }

                tableData.remove(row); // remove the row which removes from runorder upon commit
                fireEntrantsChanged();
                fireTableStructureChanged(); // must be structure change or filtered table update throws IOB
                checkDeletePlaceholder(e);
            }
            else  // driver change
            {
                fireEntrantsChanged();
                fireTableRowsUpdated(row, row);
            }
        }

        // Setting a run
        else
        {
            try {
                String quicksync = DataEntry.state.getCurrentEvent().isPro() ? DataEntry.state.getCurrentSeries() : null;
                if (aValue instanceof Run) {
                    Run r = (Run)aValue;
                    r.updateTo(DataEntry.state.getCurrentEvent().getEventId(), e.getCarId(), DataEntry.state.getCurrentCourse(), col-runoffset);
                    Database.d.setRun(r, quicksync);
                    e.setRun(r);
                } else if (aValue == null){
                    Database.d.deleteRun(DataEntry.state.getCurrentEvent().getEventId(), e.getCarId(), DataEntry.state.getCurrentCourse(), col-runoffset, quicksync);
                    e.deleteRun(col-runoffset);
                }
                poker.poke();
            } catch (Exception sqle) {
                log.log(Level.SEVERE, "\bFailed to update run data: " + sqle, sqle);
            }

            fireRunsChanged(e);
            fireTableCellUpdated(row, col);
        }
    }


    /* This will only effect the 'runorder' table and nothing else */
    public void moveRow(int start, int end, int to)
    {
        if (tableData == null) return;

        int ii, a, b, c, x;
        if ((start <= to) && (to <= (end+1))) return; // Move doesn't make sense, doesn't do anything

        /*
            a = start of first block
            b = start of second block
            c = start of 'third' block or end of second +1
            x = location where first block will start after move
        */
        if (to < start) // move upwards
        {
            a = to;
            b = start;
            c = end + 1;
            x = a + (c-b);
        }
        else // move downwards
        {
            a = start;
            b = end + 1;
            c = to;
            x = a + (c-b);
        }

        ArrayList<Entrant> tmp = new ArrayList<Entrant>(b - a);
        for (ii = a; ii < b; ii++) // Copy everything from block 1
            tmp.add(tableData.get(ii));

        for (ii = 0; ii < (c - b); ii++)  // Move block 2 up into block 1, index-b goes to index-a
            tableData.set(ii+a, tableData.get(ii+b));

        for (ii = 0; ii < tmp.size(); ii++) // Copy block 1 back in after moved block 2
            tableData.set(ii+x, tmp.get(ii));

        fireTableRowsUpdated(a, c-1);
        fireEntrantsChanged();
    }


    @Override
    public void event(MT type, Object o)
    {
        switch (type)
        {
            case EVENT_CHANGED:
                colCount = DataEntry.state.getCurrentEvent().getRuns() + 2;
                fireTableStructureChanged();
                break;

            case RUNGROUP_CHANGED:
                tableData = Database.d.getEntrantsByRunOrder(DataEntry.state.getCurrentEventId(), DataEntry.state.getCurrentCourse(), DataEntry.state.getCurrentRunGroup());
                fireTableDataChanged();
                break;
        }
    }


    /* Notifying Listeners, committing */
    public void fireEntrantsChanged()
    {
        ArrayList<UUID> ids = new ArrayList<UUID>();
        for (Entrant e : tableData)
            ids.add(e.getCarId());
        Database.d.setRunOrder(DataEntry.state.getCurrentEventId(), DataEntry.state.getCurrentCourse(), DataEntry.state.getCurrentRunGroup(), ids);
        // Database notification will cause this event for us, don't double the reload
        // Messenger.sendEvent(MT.ENTRANTS_CHANGED, null);
    }


    /* */
    public void fireRunsChanged(Entrant e)
    {
        Messenger.sendEvent(MT.RUN_CHANGED, e);
    }

    /**
     * Called when we removed a placeholder car from the runoder, check if they want to delete it and do so here
     * @param old the entrant being removed/swapped
     */
    public void checkDeletePlaceholder(Entrant old)
    {
        if (old.getClassCode().equals(ClassData.PLACEHOLDER_CLASS))
        {
            if (JOptionPane.showConfirmDialog(FocusManager.getCurrentManager().getActiveWindow(), "Do you want to remove the placeholder entry as well?",
                      "Remove PlaceHolder", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
                return;

            try {
                Database.d.deleteCar(old.getCar());
            } catch (Exception sqle) {
                log.log(Level.WARNING, "\bUnable delete car as it is still in use\n\n" + sqle, sqle);
            }

            try {
                if (old.getFirstName().equals(Driver.PLACEHOLDER))
                    Database.d.deleteDriver(old.getDriverId()); // this may fail if there are multiple placeholder cars still available, but that is okay
            } catch (Exception sqle) {
                log.info(""+sqle);
            }
        }
    }
}


