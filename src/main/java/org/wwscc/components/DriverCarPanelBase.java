/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.components;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.wwscc.dialogs.BaseDialog.DialogFinisher;
import org.wwscc.dialogs.CarDialog;
import org.wwscc.dialogs.DriverDialog;
import org.wwscc.storage.Car;
import org.wwscc.storage.Database;
import org.wwscc.storage.DecoratedCar;
import org.wwscc.storage.Driver;
import org.wwscc.util.ApplicationState;
import org.wwscc.util.IdGenerator;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.wwscc.util.SolidPainter;
import org.wwscc.util.TextChangeTrigger;
import org.wwscc.util.TextFieldFocuser;

import net.miginfocom.swing.MigLayout;


public abstract class DriverCarPanelBase extends JPanel implements ListSelectionListener
{
    private static final Logger log = Logger.getLogger(DriverCarPanelBase.class.getCanonicalName());

    protected JTextField firstSearch;
    protected JTextField lastSearch;

    protected JScrollPane dscroll;
    protected JList<Object> drivers;
    protected JScrollPane driverInfoWrapper;
    protected JTextPane driverInfo;

    protected JScrollPane cscroll;
    protected JList<DecoratedCar> cars;
    protected Vector<DecoratedCar> carVector;

    protected boolean carAddOption = false;
    protected Driver selectedDriver;
    protected DecoratedCar selectedCar;

    protected SearchDrivers searchDrivers = new SearchDrivers();
    protected ApplicationState state;

    public DriverCarPanelBase(ApplicationState s)
    {
        super();
        state = s;
        setLayout(new MigLayout("", "fill"));

        selectedDriver = null;
        selectedCar = null;

        /* Search Section */
        firstSearch = new JTextField("", 8);
        firstSearch.getDocument().addDocumentListener(searchDrivers);
        firstSearch.addFocusListener(new TextFieldFocuser());
        lastSearch = new JTextField("", 8);
        lastSearch.getDocument().addDocumentListener(searchDrivers);
        lastSearch.addFocusListener(new TextFieldFocuser());

        /* Driver Section */
        drivers = new JList<Object>();
        drivers.addListSelectionListener(this);
        drivers.setVisibleRowCount(1);
        drivers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        drivers.setCellRenderer(new ListRenderers.DriverRenderer());

        dscroll = new JScrollPane(drivers);
        dscroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        dscroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        dscroll.getVerticalScrollBar().setPreferredSize(new Dimension(15,200));

        /* create driver info box, have it NOT wrap text, use Nimbus override to set background color */
        driverInfo = new JTextPane() {
            public boolean getScrollableTracksViewportWidth() {
                return false;
            }
        };
        UIDefaults system = UIManager.getDefaults();
        UIDefaults defaults = new UIDefaults();
        defaults.put("TextPane[Enabled].backgroundPainter", new SolidPainter(system.getColor("Panel.background")));
        driverInfo.putClientProperty("Nimbus.Overrides", defaults);
        driverInfo.putClientProperty("Nimbus.Overrides.InheritDefaults", true);
        driverInfo.setEditable(false);
        driverInfo.setEnabled(true);

        /* wrap it in a scrollpane without bars to enable the hiding of lines too long */
        driverInfoWrapper = new JScrollPane(driverInfo);
        driverInfoWrapper.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        driverInfoWrapper.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        driverInfoWrapper.setBorder(BorderFactory.createTitledBorder("Info"));

        /* Car Section */
        carVector = new Vector<DecoratedCar>();
        cars = new JList<DecoratedCar>() {
            public boolean getScrollableTracksViewportWidth() {
                return true;
            }
        };
        cars.addListSelectionListener(this);
        cars.setVisibleRowCount(2);
        cars.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        cars.setCellRenderer(new ListRenderers.DecoratedCarRenderer());
        cars.getScrollableTracksViewportWidth();

        cscroll = new JScrollPane(cars);
        cscroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        cscroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        cscroll.getVerticalScrollBar().setPreferredSize(new Dimension(15,200));
    }


    protected void carCreated() {}
    protected void driverSelectionChanged() {}
    protected void carSelectionChanged() {}


    /**
     * One of the list value selections has changed.
     * This can be either a user selection or the list model was updated
     */
    @Override
    public void valueChanged(ListSelectionEvent e)
    {
        if (e.getValueIsAdjusting())
           return;

        if (e.getSource() == drivers) {
            Object o = drivers.getSelectedValue();
            if (o instanceof Driver) {
                selectedDriver = (Driver)o;
                driverInfo.setText(driverDisplay(selectedDriver));
                reloadCars(null);
            } else {
                selectedDriver = null;
                driverInfo.setText("\n\n\n\n");
                cars.setListData(new DecoratedCar[0]);
                cars.clearSelection();
            }
            driverSelectionChanged();
        } else if (e.getSource() == cars) {
            Object o = cars.getSelectedValue();
            selectedCar = (o instanceof DecoratedCar) ? (DecoratedCar)o : null;
            carSelectionChanged();
        }
    }

    public void reloadDrivers()
    {
        Driver save = selectedDriver;
        searchDrivers.changedTo("");
        drivers.setSelectedValue(save, true);
    }

    /**
     * Set the name search fields and select the name.
     * @param firstname the value to put in the firstname field
     * @param lastname  the value to put in the lastname field
     * @param driverid  if given, the specific driver to highlight (multiple name matches)
     */
    public void focusOnDriver(Driver d)
    {
        searchDrivers.enable(false);
        firstSearch.setText(d.getFirstName());
        lastSearch.setText(d.getLastName());
        searchDrivers.enable(true);
        searchDrivers.changedTo("");
        drivers.setSelectedValue(d, true);
    }

    /**
     * Set the car list to select a particular carid if its in the list.
     * @param carid  the id of the car to select
     */
    public void focusOnCar(UUID carid)
    {
        ListModel<DecoratedCar> lm = cars.getModel();
        for (int ii = 0; ii < lm.getSize(); ii++)
        {
            Car c = (Car)lm.getElementAt(ii);
            if (c.getCarId().equals(carid))
            {
                cars.setSelectedIndex(ii);
                cars.ensureIndexIsVisible(ii);
                break;
            }
        }
    }


    /**
     * Reload the carlist based on the selected driver, and optionally select one.
     * @param select the car to set as initial selection after loading
     */
    public void reloadCars(Car select)
    {
        log.log(Level.FINE, "Reload cars ({0})", select);
        Driver d = (Driver)drivers.getSelectedValue();

        if (d == null) // nothing to do
            return;

        carVector.clear();
        for (Car c : Database.d.getCarsForDriver(d.getDriverId())) {
            carVector.add(Database.d.decorateCar(c, state.getCurrentEventId(), state.getCurrentCourse()));
        }

        cars.setListData(carVector);
        if (select != null)
            focusOnCar(select.getCarId());
        else
            cars.setSelectedIndex(0);
    }


    protected class NewDriverAction extends AbstractAction
    {
        public NewDriverAction() { super("New Driver"); }
        public void actionPerformed(ActionEvent e) {
            DriverDialog dd = new DriverDialog(new Driver(firstSearch.getText(), lastSearch.getText()));
            dd.doDialog("New Driver", new DialogFinisher<Driver>() {
                @Override
                public void dialogFinished(Driver d) {
                    if (d == null) return;
                    try {
                        Database.d.newDriver(d);
                        focusOnDriver(d);
                    } catch (Exception ioe) {
                        log.log(Level.SEVERE, "\bFailed to create driver: " + ioe, ioe);
                    }
                }
            });
        }
    }

    protected class EditDriverAction extends AbstractAction
    {
        public EditDriverAction() { super("Edit Driver"); }
        public void actionPerformed(ActionEvent e) {
            DriverDialog dd = new DriverDialog(selectedDriver);
            dd.doDialog("Edit Driver", new DialogFinisher<Driver>() {
                @Override
                public void dialogFinished(Driver d) {
                    if (d == null) return;
                    try {
                        Database.d.updateDriver(d);
                        driverInfo.setText(driverDisplay(d));
                        // round about way to fire selected index
                        int ii = drivers.getSelectedIndex();
                        drivers.clearSelection();
                        drivers.setSelectedIndex(ii);
                    } catch (Exception ioe) {
                        log.log(Level.SEVERE, "\bFailed to update driver: " + ioe, ioe);
                    }
                }
            });
        }
    }


    protected class NewCarAction extends AbstractAction
    {
        boolean newfrom;
        public NewCarAction(boolean fromselected) {
            super(fromselected ? "New From" : "New Car");
            newfrom = fromselected;
        }

        public void actionPerformed(ActionEvent e) {
            final CarDialog cd;
            if (newfrom && (selectedCar != null)) {
                Car initial = new Car(selectedCar);
                initial.setCarId(IdGenerator.generateId());  // Need a new id
                cd = new CarDialog(selectedDriver.getDriverId(), initial, Database.d.getClassData(), carAddOption);
            } else {
                cd = new CarDialog(selectedDriver.getDriverId(), null, Database.d.getClassData(), carAddOption);
            }

            cd.doDialog("New Car", new DialogFinisher<Car>() {
                @Override
                public void dialogFinished(Car c) {
                    if (c == null)
                        return;
                    try {
                        if (selectedDriver != null) {
                            Database.d.newCar(c);
                            reloadCars(c);
                            carCreated();
                            if (cd.getAddToRunOrder())
                                Messenger.sendEvent(MT.CAR_ADD, c.getCarId());
                        }
                    } catch (Exception ioe) {
                        log.log(Level.SEVERE, "\bFailed to create a car: " + ioe, ioe);
                    }
                }
            });
        }
    }


    protected class ClearSearchAction extends AbstractAction
    {
        public ClearSearchAction() { super("Clear Search"); }
        public void actionPerformed(ActionEvent e) {
            firstSearch.setText("");
            lastSearch.setText("");
            firstSearch.requestFocus();
        }
    }

    public String driverDisplay(Driver d)
    {
        StringBuilder ret = new StringBuilder("");
        ret.append(d.getFullName() + " (" + d.getUserName() + ")\n");
        ret.append(d.getEmail() + "\n");
        if (!d.getAttrS("scca").isEmpty())
            ret.append("SCCA: " + d.getAttrS("scca") + "\n");
        ret.append(d.getAttrS("address") + "\n");
        ret.append(String.format("%s%s%s %s\n", d.getAttrS("city"), d.hasAttr("city")&&d.hasAttr("state")?", ":"", d.getAttrS("state"), d.getAttrS("zip")));
        if (!d.getAttrS("phone").isEmpty())
            ret.append("\n" + d.getAttrS("phone"));
        return ret.toString();
    }


    public static String carDisplay(Car c)
    {
        StringBuilder ret = new StringBuilder();
        ret.append(c.getCarId()).append("\n");
        ret.append(c.getClassCode()).append(" ").append(c.getEffectiveIndexStr()).append(" #").append(c.getNumber()).append("\n");
        ret.append(c.getYear() + " " + c.getMake() + " " + c.getModel() + " " + c.getColor());
        return ret.toString();
    }


    class SearchDrivers extends TextChangeTrigger
    {
        @Override
        public void changedTo(String txt)
        {
            String first = null, last = null;
            if (lastSearch.getDocument().getLength() > 0)
                last = lastSearch.getText();
            if (firstSearch.getDocument().getLength() > 0)
                first = firstSearch.getText();

             List<Driver> display = Database.d.getDriversLike(first, last);
             Collections.sort(display, new NameDriverComparator());
             drivers.setListData(display.toArray());
             drivers.setSelectedIndex(0);
        }
    }

    final static class NameDriverComparator implements Comparator<Driver>
    {
        public int compare(Driver d1, Driver d2)
        {
            return d1.getFullName().toLowerCase().compareTo(d2.getFullName().toLowerCase());
        }
    }
}
