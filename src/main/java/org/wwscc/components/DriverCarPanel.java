/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */


package org.wwscc.components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.Painter;
import javax.swing.UIDefaults;
import javax.swing.border.BevelBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import net.miginfocom.swing.MigLayout;
import org.wwscc.dialogs.BaseDialog.DialogFinisher;
import org.wwscc.dialogs.CarDialog;
import org.wwscc.dialogs.DriverDialog;
import org.wwscc.storage.Car;
import org.wwscc.storage.Database;
import org.wwscc.storage.Driver;
import org.wwscc.storage.DecoratedCar;
import org.wwscc.util.ApplicationState;
import org.wwscc.util.IdGenerator;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.wwscc.util.TextChangeTrigger;


public abstract class DriverCarPanel extends JPanel implements ActionListener, ListSelectionListener, FocusListener
{
    private static final Logger log = Logger.getLogger(DriverCarPanel.class.getCanonicalName());

    public static final String CLEAR      = "Clear Search";
    public static final String NEWDRIVER  = "New Driver";
    public static final String EDITDRIVER = "Edit Driver";
    public static final String EDITNOTES  = "Edit Notes";

    public static final String NEWCAR     = "New Car";
    public static final String NEWFROM    = "New From";
    public static final String EDITCAR    = "Edit Car";
    public static final String DELETECAR  = "Delete Car";

    protected JTextField firstSearch;
    protected JTextField lastSearch;

    protected JScrollPane dscroll;
    protected JList<Object> drivers;
    protected JTextPane driverInfo;

    protected JScrollPane cscroll;
    protected JList<DecoratedCar> cars;
    protected Vector<DecoratedCar> carVector;

    protected boolean carAddOption = false;
    protected Driver selectedDriver;
    protected DecoratedCar selectedCar;

    protected SearchDrivers searchDrivers = new SearchDrivers();
    protected ApplicationState state;

    public DriverCarPanel(ApplicationState s)
    {
        super();
        state = s;
        setLayout(new MigLayout("", "fill"));

        selectedDriver = null;
        selectedCar = null;

        /* Search Section */
        firstSearch = new JTextField("", 8);
        firstSearch.getDocument().addDocumentListener(searchDrivers);
        firstSearch.addFocusListener(this);
        lastSearch = new JTextField("", 8);
        lastSearch.getDocument().addDocumentListener(searchDrivers);
        lastSearch.addFocusListener(this);

        /* Driver Section */
        drivers = new JList<Object>();
        drivers.addListSelectionListener(this);
        drivers.setVisibleRowCount(1);
        //drivers.setPrototypeCellValue("12345678901234567890");
        drivers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        dscroll = new JScrollPane(drivers);
        dscroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        dscroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        dscroll.getVerticalScrollBar().setPreferredSize(new Dimension(15,200));

        // create default height
        driverInfo = displayArea(7);

        /* Car Section */
        carVector = new Vector<DecoratedCar>();
        cars = new JList<DecoratedCar>();
        cars.addListSelectionListener(this);
        cars.setVisibleRowCount(2);
        cars.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        cscroll = new JScrollPane(cars);
        cscroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        cscroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        cscroll.getVerticalScrollBar().setPreferredSize(new Dimension(15,200));
    }


    static class SolidPainter implements Painter<Object>
    {
        Color color;
        public SolidPainter(Color c) {
            color = c;
        }
        @Override
        public void paint(Graphics2D g, Object object, int width, int height) {
            g.setColor(color);
            g.fillRect(0, 0, width, height);
        }

    }

    protected JTextPane displayArea(int linecount)
    {
        JTextPane tp = new JTextPane();
        tp.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createBevelBorder(BevelBorder.LOWERED),
                BorderFactory.createEmptyBorder(3,3,3,3)));

        UIDefaults defaults = new UIDefaults();
        defaults.put("TextPane[Enabled].backgroundPainter", new SolidPainter(new Color(240, 240, 240)));
        tp.putClientProperty("Nimbus.Overrides", defaults);
        tp.putClientProperty("Nimbus.Overrides.InheritDefaults", true);
        tp.setEditable(false);
        tp.setEnabled(true);

        // ugly hack to set a preferred height based on lines of text
        tp.setSize(100,Short.MAX_VALUE);
        StringBuilder b = new StringBuilder();
        for (int ii = 0; ii < linecount-1; ii++) {
            b.append(ii+"\n"+ii);
        }
        tp.setText(b.toString());
        int h = (int)(tp.getPreferredSize().height*0.9);
        tp.setPreferredSize(new Dimension(Short.MAX_VALUE, h));
        tp.setText("");
        return tp;
    }


    /**
     * Set the name search fields and select the name.
     * @param firstname the value to put in the firstname field
     * @param lastname  the value to put in the lastname field
     */
    public void focusOnDriver(String firstname, String lastname)
    {
        searchDrivers.enable(false);
        firstSearch.setText(firstname);
        lastSearch.setText(lastname);
        searchDrivers.enable(true);
        searchDrivers.changedTo("");
        drivers.setSelectedIndex(0);
        drivers.ensureIndexIsVisible(0);
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


    /**
     * Process events from the various buttons
     * @param e the button event
     */
    @Override
    public void actionPerformed(ActionEvent e)
    {
        String cmd = e.getActionCommand();

        if (cmd.equals(NEWDRIVER))
        {
            DriverDialog dd = new DriverDialog(new Driver(firstSearch.getText(), lastSearch.getText()));
            dd.doDialog(NEWDRIVER, new DialogFinisher<Driver>() {
                @Override
                public void dialogFinished(Driver d) {
                    if (d == null) return;
                    try {
                        Database.d.newDriver(d);
                        focusOnDriver(d.getFirstName(), d.getLastName());
                    } catch (Exception ioe) {
                        log.log(Level.SEVERE, "\bFailed to create driver: " + ioe, ioe);
                    }
                }
            });
        }

        else if (cmd.equals(EDITDRIVER))
        {
            DriverDialog dd = new DriverDialog(selectedDriver);
            dd.doDialog(EDITDRIVER, new DialogFinisher<Driver>() {
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

        else if (cmd.equals(NEWCAR) || cmd.equals(NEWFROM))
        {
            final CarDialog cd;
            if (cmd.equals(NEWFROM) && (selectedCar != null))
            {
                Car initial = new Car(selectedCar);
                initial.setCarId(IdGenerator.generateId());  // Need a new id
                cd = new CarDialog(selectedDriver.getDriverId(), initial, Database.d.getClassData(), carAddOption);
            }
            else
            {
                cd = new CarDialog(selectedDriver.getDriverId(), null, Database.d.getClassData(), carAddOption);
            }

            cd.doDialog(NEWCAR, new DialogFinisher<Car>() {
                @Override
                public void dialogFinished(Car c) {
                    if (c == null)
                        return;
                    try
                    {
                        if (selectedDriver != null)
                        {
                            Database.d.newCar(c);
                            reloadCars(c);
                            carCreated();
                            if (cd.getAddToRunOrder())
                                Messenger.sendEvent(MT.CAR_ADD, c.getCarId());
                        }
                    }
                    catch (Exception ioe)
                    {
                        log.log(Level.SEVERE, "\bFailed to create a car: " + ioe, ioe);
                    }
                }
            });
        }

        else if (cmd.equals(CLEAR))
        {
            firstSearch.setText("");
            lastSearch.setText("");
            firstSearch.requestFocus();
        }

        else
        {
            log.log(Level.INFO, "Unknown command in DriverEntry: {0}", cmd);
        }
    }

    /**
     * Called when a new car is created.  The new car should be the selected value in the list
     */
    protected void carCreated()
    {
    }

    /**
     * One of the list value selections has changed.
     * This can be either a user selection or the list model was updated
     */
    @Override
    public void valueChanged(ListSelectionEvent e)
    {
        if (e.getValueIsAdjusting() == false)
        {
            Object source = e.getSource();
            if (source == drivers)
            {
                Object o = drivers.getSelectedValue();
                if (o instanceof Driver)
                {
                    selectedDriver = (Driver)o;
                    driverInfo.setText(driverDisplay(selectedDriver));
                    reloadCars(null);
                }
                else
                {
                    selectedDriver = null;
                    driverInfo.setText("\n\n\n\n");
                    cars.setListData(new DecoratedCar[0]);
                    cars.clearSelection();
                }
            }

            else if (source == cars)
            {
                Object o = cars.getSelectedValue();
                selectedCar = (o instanceof DecoratedCar) ? (DecoratedCar)o : null;
            }
        }
    }

    public String driverDisplay(Driver d)
    {
        StringBuilder ret = new StringBuilder("");
        ret.append(d.getDriverId()).append("\n");
        ret.append(d.getFullName()).append("\n");
        ret.append(d.getAttrS("address")).append("\n");
        ret.append(String.format("%s%s%s %s\n", d.getAttrS("city"), d.hasAttr("city")&&d.hasAttr("state")?", ":"", d.getAttrS("state"), d.getAttrS("zip")));
        ret.append(d.getEmail()).append("\n");
        ret.append(d.getAttrS("phone")).append("\n");
        if (!d.getBarcode().equals("")) {
            ret.append("Barcode = ").append(d.getBarcode());
        }
        return ret.toString();
    }


    public static String carDisplay(Car c)
    {
        StringBuilder ret = new StringBuilder();
        ret.append(c.getCarId()).append("\n");
        ret.append(c.getClassCode()).append(" ").append(Database.d.getEffectiveIndexStr(c)).append(" #").append(c.getNumber()).append("\n");
        ret.append(c.getYear() + " " + c.getMake() + " " + c.getModel() + " " + c.getColor());
        return ret.toString();
    }


    @Override
    public void focusGained(FocusEvent e)
    {
        JTextField tf = (JTextField)e.getComponent();
        tf.selectAll();
    }

    @Override
    public void focusLost(FocusEvent e)
    {
        JTextField tf = (JTextField)e.getComponent();
        tf.select(0,0);
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
            return d1.getFullName().compareTo(d2.getFullName());
        }
    }
}
