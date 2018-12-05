/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2012 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dataentry.tables;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

import org.wwscc.dataentry.DataEntry;
import org.wwscc.storage.Car;
import org.wwscc.storage.ClassData;
import org.wwscc.storage.Database;
import org.wwscc.storage.DecoratedCar;
import org.wwscc.storage.Driver;
import org.wwscc.storage.Entrant;
import org.wwscc.util.IdGenerator;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;

/**
 * Wrapper that holds a separate driver and runs table to provide separate horizontal
 * scrolling as well as processing of events that can apply to both.
 */
public class DoubleTableContainer extends JScrollPane implements MessageListener
{
    private static final Logger log = Logger.getLogger(DoubleTableContainer.class.getCanonicalName());

    EntryModel dataModel;
    DriverTable driverTable;
    RunsTable runsTable;
    TableRowSorter<EntryModel> sorter;

    public DoubleTableContainer()
    {
        dataModel = new EntryModel();
        driverTable = new DriverTable(dataModel);
        runsTable = new RunsTable(dataModel);

        sorter = new TableRowSorter<EntryModel>(dataModel) { @Override public boolean isSortable(int column) { return false; } };
        driverTable.setRowSorter(sorter);
        runsTable.setRowSorter(sorter);

        setViewportView(runsTable);
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_ALWAYS);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_ALWAYS);

        driverTable.setPreferredScrollableViewportSize(new Dimension(240, Integer.MAX_VALUE));
        setRowHeaderView( driverTable );
        setCorner(UPPER_LEFT_CORNER, driverTable.getTableHeader());
        getRowHeader().addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                JViewport _viewport = (JViewport) e.getSource();
                getVerticalScrollBar().setValue(_viewport.getViewPosition().y);
            }
        });


        HeaderRenderer hr = new HeaderRenderer();
        driverTable.getTableHeader().setDefaultRenderer(hr);
        runsTable.getTableHeader().setDefaultRenderer(hr);
        Messenger.register(MT.COURSE_CHANGED, new MessageListener() {
            @Override public void event(MT type, Object data) {
                hr.course = DataEntry.state.getCurrentCourse();
                driverTable.getTableHeader().repaint();
                runsTable.getTableHeader().repaint();
            }
        });

        // When we click in the runs table, clear the drivers selection else it
        // leaves a selection box which can add to confusion, we leave the selection
        // in the runs table in case the time entry is triggered
        runsTable.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                driverTable.clearSelection();
            }
        });


        Messenger.register(MT.CAR_ADD, this);
        Messenger.register(MT.CAR_CHANGE, this);
        Messenger.register(MT.FILTER_ENTRANT, this);
        Messenger.register(MT.BARCODE_SCANNED, this);
        Messenger.register(MT.OBJECT_SCANNED, this);
        Messenger.register(MT.ENTRANTS_CHANGED, this);
    }

    public RunsTable getRunsTable() { return runsTable; }
    public DriverTable getDriverTable() { return driverTable; }

    class HeaderRenderer extends DefaultTableCellRenderer
    {
        Border border = BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 1, UIManager.getColor("Table.gridColor")),
                    BorderFactory.createEmptyBorder(4, 5, 4, 5));
        Color colors[] = new Color[] { new Color(170, 180, 255), new Color(225, 225, 230) };
        int course;

        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            setBorder(border);
            setBackground(colors[course%colors.length]);
            return this;
        }
    }

    /**
     * The logic for processing a scanned object
     *   1. Search driver barcode fields
     *   2. If found, pass first result to driver function
     *   3. If not found, created a placeholder driver and pass to driver function
     * @param barcode the scanned barcode string
     */
    public void processBarcode(String barcode)
    {
        List<Driver> found = Database.d.findDriverByBarcode(barcode);
        Driver d = null;

        if (found.size() > 0)
        {
            if (found.size() > 1)
                log.log(Level.WARNING, "{0} drivers exist with the barcode value {1}, using the first", new Object[] {found.size(), barcode});
            d = found.get(0);
        }
        else if (found.size() == 0)
        {
            log.log(Level.WARNING, "Unable to locate a driver using barcode {0}, creating a default", barcode);
            d = Driver.getPlaceHolder(barcode);
            try {
                Database.d.newDriver(d);
            } catch (Exception e) {
                log.log(Level.WARNING, "\bUnable to create driver placeholder entry: " + e, e);
            }
        }
        else
        {
            log.severe("\bNegative number of elements in list of drivers?!");
            return;
        }

        driverScanned(d);
    }

    /**
     * The logic for when a driver is scanned rather than a car entry:
     *  1. Find all registered cars that are not in use in another rungroup
     *  2. If the list contains a car that is already in the current rungroup, select that one
     *  3. If the list length ==1, select it by default
     *  4. If the list length > 1, select the non second runs car first (TOPM, ITO2)
     *  5. If nothing is found, create a placeholder entry
     * @param d the matched driver
     */
    public void driverScanned(Driver d)
    {
        List<Car> available = Database.d.getRegisteredCars(d.getDriverId(), DataEntry.state.getCurrentEventId());
        Iterator<Car> iter = available.iterator();
        Messenger.sendEvent(MT.DRIVER_SCAN_ACCEPTED, d);

        while (iter.hasNext()) {
            Car c = iter.next();
            if (Database.d.isInCurrentOrder(DataEntry.state.getCurrentEventId(), c.getCarId(), DataEntry.state.getCurrentCourse(), DataEntry.state.getCurrentRunGroup())) {
                event(MT.CAR_ADD, c.getCarId()); // if there is something in this run order, just go with it and return
                return;
            }
            if (Database.d.isInOrder(DataEntry.state.getCurrentEventId(), c.getCarId(), DataEntry.state.getCurrentCourse()))
                iter.remove(); // otherwise, remove those active in another run order (same course/event)
        }

        if (available.size() == 1) { // pick only one available
            event(MT.CAR_ADD, available.get(0).getCarId());
            return;
        }

        ClassData cd = Database.d.getClassData();
        UUID eid = IdGenerator.generateId();
        Optional<DecoratedCar> car = available.stream()
                          .filter(c -> !cd.getClass(c.getClassCode()).isSecondRuns()) // not second runs
                          .map(c -> Database.d.decorateCar(c, eid, 1))  // get decorated cars
                          .sorted(new DecoratedCar.PaidOrder())
                          .findFirst();

        if (car.isPresent()) {
            event(MT.CAR_ADD, car.get().getCarId());
            return;
        }

        log.warning("Unable to locate a registed car for " + d.getFullName() + " that isn't already used in this event on this course.  Creating a default");

        Car c = new Car();
        c.setDriverId(d.getDriverId());
        c.setModel(DataEntry.state.getCurrentEvent().toString());
        c.setColor("group=" + DataEntry.state.getCurrentRunGroup());
        c.setClassCode(ClassData.PLACEHOLDER_CLASS);
        c.setNumber(999);

        try {
            Database.d.newCar(c);
            Database.d.registerCar(DataEntry.state.getCurrentEventId(), c.getCarId());
        } catch (Exception sqle) {
            log.log(Level.WARNING, "\bUnable to create a placeholder car entry: " + sqle, sqle);
        }
        event(MT.CAR_ADD, c.getCarId());
    }


    @Override
    public void event(MT type, Object o)
    {
        switch (type)
        {
            case CAR_ADD:
                Entrant torestore = null;
                int runcol = -1;
                if ((runsTable.getSelectedRow() >= 0) && (runsTable.getSelectedColumn() >= 0)) {
                    torestore = (Entrant)dataModel.getValueAt(runsTable.convertRowIndexToModel(runsTable.getSelectedRow()), 0);
                    runcol = runsTable.getSelectedColumn();
                }
                dataModel.addCar((UUID)o);
                if (torestore != null)
                {   // update selection after moving rows around to maintain same entrant
                    int newrow = runsTable.convertRowIndexToView(dataModel.getRowForEntrant(torestore));
                    runsTable.setRowSelectionInterval(newrow, newrow);
                    runsTable.setColumnSelectionInterval(runcol, runcol);
                }
                else
                {   // only scroll to bottom if there is nothing selected
                    driverTable.scrollTable(dataModel.getRowCount(), 0);
                }
                driverTable.repaint();
                runsTable.repaint();
                break;

            case OBJECT_SCANNED:
                if (o instanceof Car)
                    event(MT.CAR_ADD, ((Car)o).getCarId());
                if (o instanceof Driver)
                    driverScanned((Driver)o);
                break;

            case BARCODE_SCANNED:
                processBarcode((String)o);
                break;

            case CAR_CHANGE:
                if (driverTable.getSelectedRowCount() == 0) {
                    JOptionPane.showMessageDialog(this,
                            "You need to select a driver in the table before you can swap entrant.", "Notice", JOptionPane.INFORMATION_MESSAGE);
                } else if (driverTable.getSelectedRowCount() > 1) {
                    JOptionPane.showMessageDialog(this,
                            "Can't swap entrant when more than one entrant is selected in the table.", "Error", JOptionPane.WARNING_MESSAGE);
                } else {
                    int row = driverTable.convertRowIndexToModel(driverTable.getSelectedRow());
                    if ((row >= 0) && (row < dataModel.getRowCount()))
                        dataModel.replaceCar((UUID)o, row);
                }
                break;

            case FILTER_ENTRANT:
                sorter.setRowFilter(new EntrantFilter((String)o));
                break;

            case ENTRANTS_CHANGED:
                // Check if runorder changed behind our backs, reload table in that case, otherwise ignore so we don't mess up cell selection
                List<Entrant> dborder = Database.d.getEntrantsByRunOrder(DataEntry.state.getCurrentEventId(), DataEntry.state.getCurrentCourse(), DataEntry.state.getCurrentRunGroup());
                if (dborder.size() == dataModel.tableData.size()) {
                    for (int ii = 0; ii < dborder.size(); ii++) {
                        if (!dborder.get(ii).getCarId().equals(dataModel.tableData.get(ii).getCarId())) {
                            Messenger.sendEventNow(MT.RUNGROUP_CHANGED, DataEntry.state.getCurrentRunGroup());
                            break;
                        }
                    }
                } else {
                    Messenger.sendEventNow(MT.RUNGROUP_CHANGED, DataEntry.state.getCurrentRunGroup());
                }
                break;
        }
    }
}
