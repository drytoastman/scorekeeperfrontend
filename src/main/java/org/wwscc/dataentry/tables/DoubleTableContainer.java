/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2012 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dataentry.tables;

import java.awt.Color;
import java.awt.Dimension;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;

import org.wwscc.dataentry.DataEntry;
import org.wwscc.storage.Car;
import org.wwscc.storage.ClassData;
import org.wwscc.storage.Database;
import org.wwscc.storage.Driver;
import org.wwscc.storage.Entrant;
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

		sorter = new TableRowSorter<EntryModel>(dataModel);
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

		Messenger.register(MT.CAR_ADD, this);
		Messenger.register(MT.CAR_CHANGE, this);
		Messenger.register(MT.FILTER_ENTRANT, this);
		Messenger.register(MT.COURSE_CHANGED, this);
		Messenger.register(MT.BARCODE_SCANNED, this);
	}

	public RunsTable getRunsTable() { return runsTable; }
	public DriverTable getDriverTable() { return driverTable; }

	public void processBarcode(String barcode) throws SQLException, IOException
	{
        List<Driver> found = Database.d.findDriverByMembership(barcode);
        Driver d = null;

        if (found.size() > 0)
        {
            if (found.size() > 1)
                log.log(Level.WARNING, "{0} drivers exist with the membership value {1}, using the first", new Object[] {found.size(), barcode});
            d = found.get(0);
        }
        else if (found.size() == 0)
		{
            log.log(Level.WARNING, "Unable to locate a driver using membership {0}, creating a default", barcode);
			d = new Driver("Placeholder", barcode);
			d.setMembership(barcode);
			Database.d.newDriver(d);
		}

		List<Car> available = Database.d.getRegisteredCars(d.getDriverId(), DataEntry.state.getCurrentEventId());
		Iterator<Car> iter = available.iterator();

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

		for (Car c : available) {  // multiple available, skip second runs classes, pick other items first
			if (Database.d.getClassData().getClass(c.getClassCode()).isSecondRuns()) continue;
			event(MT.CAR_ADD, c.getCarId());
			return;
		}

        log.warning("Unable to locate a registed car for " + d.getFullName() + " that isn't already used in this event on this course.  Creating a default");

        Car c = new Car();
        c.setDriverId(d.getDriverId());
        c.setModel(DataEntry.state.getCurrentEvent().toString());
        c.setColor("group=" + DataEntry.state.getCurrentRunGroup());
        c.setClassCode(ClassData.PLACEHOLDER_CLASS);
        c.setNumber(999);
                
        Database.d.newCar(c);
        Database.d.registerCar(DataEntry.state.getCurrentEventId(), c, false, false);
        event(MT.CAR_ADD, c.getCarId());
	}


	@Override
	public void event(MT type, Object o)
	{
		switch (type)
		{
			case CAR_ADD:
				int savecol = runsTable.getSelectedColumn();
				Entrant selected = (Entrant)dataModel.getValueAt(runsTable.getSelectedRow(), 0);
				dataModel.addCar((UUID)o);
				if ((savecol >= 0) && (selected != null))
				{   // update selection after moving rows around to maintain same entrant
					int newrow = dataModel.getRowForEntrant(selected);
					runsTable.setRowSelectionInterval(newrow, newrow);
				}
				else
				{   // don't scroll to bottom if there is something selected
	                driverTable.scrollTable(dataModel.getRowCount(), 0);
				}
				driverTable.repaint();
				runsTable.repaint();
				break;

			case BARCODE_SCANNED:
				try {
					processBarcode((String)o);
				} catch (IOException | SQLException be) {
					log.log(Level.SEVERE, be.getMessage());
				}

				break;

			case CAR_CHANGE:
				int row = driverTable.getSelectedRow();
				if ((row >= 0) && (row < driverTable.getRowCount()))
					dataModel.replaceCar((UUID)o, row);
				break;

			case FILTER_ENTRANT:
                sorter.setRowFilter(new EntrantFilter((String)o));
				break;

			case COURSE_CHANGED:
				JTableHeader dh = driverTable.getTableHeader();
				JTableHeader rh = runsTable.getTableHeader();
				if (DataEntry.state.getCurrentCourse() > 1)
				{
					dh.setForeground(Color.BLUE);
					dh.setBorder(new LineBorder(Color.BLUE));
					rh.setBorder(new LineBorder(Color.BLUE));
				}
				else
				{
					dh.setForeground(Color.BLACK);
					dh.setBorder(new LineBorder(Color.GRAY, 1));
					rh.setBorder(new LineBorder(Color.GRAY, 1));
				}
				break;
		}
	}
}
