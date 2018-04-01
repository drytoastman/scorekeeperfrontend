/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2012 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.registration;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.print.DocFlavor;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.OrientationRequested;
import javax.swing.AbstractAction;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionEvent;
import net.miginfocom.swing.MigLayout;

import org.wwscc.components.DriverCarPanel;
import org.wwscc.components.UnderlineBorder;
import org.wwscc.dialogs.CarDialog;
import org.wwscc.dialogs.CurrencyDialog;
import org.wwscc.dialogs.NotesDialog;
import org.wwscc.dialogs.WeekendMemberDialog;
import org.wwscc.dialogs.BaseDialog.DialogFinisher;
import org.wwscc.storage.Car;
import org.wwscc.storage.Driver;
import org.wwscc.storage.Payment;
import org.wwscc.storage.Database;
import org.wwscc.storage.DecoratedCar;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;
import org.wwscc.util.Resources;


public class EntryPanel extends DriverCarPanel implements MessageListener
{
    private static final ImageIcon noteicon = new ImageIcon(Resources.loadImage("notes.png"));
    private static final ImageIcon cardicon = new ImageIcon(Resources.loadImage("card.png"));
    private static final Logger log = Logger.getLogger(EntryPanel.class.getCanonicalName());

    public static final String ONSITE_PAYMENT = "onsite";

    JButton registerandpay, registerit, unregisterit, movepayment, deletepayment;
    JButton clearSearch, newdriver, editdriver, editnotes, weekmember;
    JButton newcar, newcarfrom, editcar, deletecar, print;
    JLabel paidwarning, mergeWarning;
    JPanel singleCarPanel, multiCarPanel;
    JComboBox<PrintService> printers;

    LayeredBarcode barcode;

    @SuppressWarnings("deprecation")
    public EntryPanel()
    {
        super(Registration.state);
        setLayout(new MigLayout("fill, gap 0, ins 0", "[45%, fill][55%, fill]", "fill"));
        Messenger.register(MT.SERIES_CHANGED, this);
        Messenger.register(MT.EVENT_CHANGED, this);
        Messenger.register(MT.BARCODE_SCANNED, this);
        Messenger.register(MT.OBJECT_SCANNED, this);

        printers = new JComboBox<PrintService>();
        printers.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> jlist, Object e, int i, boolean bln, boolean bln1) {
                super.getListCellRendererComponent(jlist, e, i, bln, bln1);
                if ((e != null) && (e instanceof PrintService))
                    setText(((PrintService)e).getName());
                return this;
            }
        });
        printers.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent ie) {
                Prefs.setDefaultPrinter(((PrintService)printers.getSelectedItem()).getName());
                print.setEnabled(true);
            }
        });

        drivers.setCellRenderer(new DriverRenderer());
        cars.setCellRenderer(new CarRenderer());

        /* Buttons */
        registerandpay = new JButton(new RegisterAndPayAction());
        registerandpay.setEnabled(false);

        registerit = new JButton(new RegisterAction());
        registerit.setEnabled(false);

        unregisterit = new JButton(new UnregisterAction());
        unregisterit.setEnabled(false);

        movepayment = new JButton(new MovePaymentMenuAction());
        movepayment.setEnabled(false);

        deletepayment = new JButton(new DeletePaymentMenuAction());
        deletepayment.setEnabled(false);

        clearSearch = smallButton(CLEAR, true);
        newdriver   = smallButton(NEWDRIVER, true);
        editdriver  = smallButton(EDITDRIVER, false);
        editnotes   = new JButton(new EditNotesAction());
        weekmember  = new JButton(new WeekendMemberAction());
        newcar      = smallButton(NEWCAR, false);
        newcarfrom  = smallButton(NEWFROM, false);

        editcar = new JButton(new EditCarAction());
        editcar.setEnabled(false);

        deletecar = new JButton(new DeleteCarAction());
        deletecar.setEnabled(false);

        paidwarning = new JLabel("");
        paidwarning.setForeground(Color.WHITE);
        paidwarning.setBackground(Color.RED);
        paidwarning.setHorizontalAlignment(JLabel.CENTER);

        barcode = new LayeredBarcode();
        print   = new JButton(new PrintLabelAction());
        print.setEnabled(false);

        mergeWarning = new JLabel("<html>Can only merge cars with<br/>the same class and index</html>", SwingConstants.CENTER);
        mergeWarning.setOpaque(true);
        mergeWarning.setForeground(Color.WHITE);
        mergeWarning.setBackground(Color.RED);
        cars.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JPanel searchp = new JPanel(new MigLayout("fill, gap 2", "[fill,15%][fill,50%][fill,35%]", ""));
        JPanel driverp = new JPanel(new MigLayout("fill, gap 2, ins 0 6 6 6, hidemode 3", "[fill,50%][50%!]", "fill"));
        JPanel leftp   = new JPanel(new MigLayout("fill, gap 0, ins 0", "fill", "[grow 0][grow 100]"));
        JPanel rightp  = new JPanel(new MigLayout("fill, gap 2", "[fill,55%][45%!]", "[grow 0][grow 100][grow 0]"));

        leftp.add(searchp, "growx, wrap");
        leftp.add(driverp, "grow");
        add(leftp, "grow");
        add(rightp,  "grow");

        // deprecated but nothing easy enough to replace
        firstSearch.setNextFocusableComponent(lastSearch);

        searchp.add(createTitle("1. Search"), "spanx 5, growx, wrap");
        searchp.add(new JLabel("First Name"), "");
        searchp.add(firstSearch,              "");
        searchp.add(clearSearch,              "wrap");
        searchp.add(new JLabel("Last Name"),  "");
        searchp.add(lastSearch,               "");
        searchp.add(newdriver,                "");

        driverp.add(createTitle("2. Driver"), "spanx 2, growx, wrap");
        driverp.add(dscroll,           "spany 11, grow");
        driverp.add(editdriver,        "growx, wrap");
        driverp.add(editnotes,         "growx, wrap");
        driverp.add(weekmember,        "growx, wrap");
        driverp.add(new JLabel(""),    "pushy 10, wrap");
        driverp.add(driverInfo,        "growx, wrap");
        driverp.add(new JLabel(""),    "pushy 10, wrap");
        driverp.add(barcode,           "growx, h 50, center, wrap");
        driverp.add(printers,          "growx, wrap");
        driverp.add(print,             "growx, wrap");
        driverp.add(new JLabel(""),    "pushy 15");

        singleCarPanel = new JPanel(new MigLayout("fill, ins 0, gap 2"));
        multiCarPanel  = new JPanel(new MigLayout("fill, ins 0, gap 2"));
        multiCarPanel.setVisible(false);

        rightp.add(createTitle("3. Car"), "spanx 2, growx, wrap");
        rightp.add(cscroll,            "grow");
        rightp.add(singleCarPanel,     "grow, wrap, hidemode 3");
        rightp.add(multiCarPanel,      "grow, wrap, hidemode 3");

        singleCarPanel.add(new JLabel(""),    "pushy 10, wrap");
        singleCarPanel.add(newcar,            "growx, split");
        singleCarPanel.add(newcarfrom,        "growx, wrap");
        singleCarPanel.add(editcar,           "growx, split");
        singleCarPanel.add(deletecar,         "growx, wrap");
        singleCarPanel.add(new JSeparator(),  "growx, gapy 10 10, wrap");
        singleCarPanel.add(registerandpay,    "growx, wrap");
        singleCarPanel.add(movepayment,       "growx, wrap");
        singleCarPanel.add(deletepayment,     "growx, wrap");
        singleCarPanel.add(registerit,        "growx, wrap");
        singleCarPanel.add(unregisterit,      "growx, wrap");
        singleCarPanel.add(new JLabel(""),    "pushy 10, wrap");
        singleCarPanel.add(paidwarning,       "growx, wrap");
        singleCarPanel.add(new JLabel(""),    "pushy 100, wrap");

        carSelectionChanged();
        new Thread(new FindPrinters()).start();
    }


    class EditNotesAction extends AbstractAction
    {
        public EditNotesAction() { super("Edit Notes"); }
        @Override
        public void actionPerformed(ActionEvent e) {
            if (selectedDriver == null)
                return;
            NotesDialog nd = new NotesDialog(selectedDriver.getAttrS("notes"));
            if (nd.doDialog("Edit Notes", null)) {
                selectedDriver.setAttrS("notes", nd.getResult());
                try {
                    Database.d.updateDriver(selectedDriver);
                    valueChanged(new ListSelectionEvent(drivers, -1, -1, false));
                } catch (Exception ex) {
                    log.warning("\bUpdate driver failed: " + ex);
                }
            }
        }
    }


    class WeekendMemberAction extends AbstractAction
    {
        public WeekendMemberAction() { super("Weekend Membership"); }
        @Override
        public void actionPerformed(ActionEvent e) {
            if (selectedDriver == null)
                return;
            WeekendMemberDialog wd = new WeekendMemberDialog(selectedDriver, Database.d.getActiveWeekendMembership(selectedDriver.getDriverId()));
            wd.doDialog("Weekend Membership", null);
            valueChanged(new ListSelectionEvent(drivers, -1, -1, false));
        }
    }


    class PrintLabelAction extends AbstractAction
    {
        public PrintLabelAction() { super("Print Label"); }
        @Override
        public void actionPerformed(ActionEvent e)
        {
            try {
                if (selectedDriver == null)
                    return;

                PrintService ps = (PrintService)printers.getSelectedItem();
                PrintRequestAttributeSet attr = new HashPrintRequestAttributeSet();

                attr.add(new Copies(1));
                attr.add((Media)ps.getDefaultAttributeValue(Media.class)); // set to default paper from printer
                attr.add(OrientationRequested.LANDSCAPE);

                SimpleDoc doc = new SimpleDoc(barcode.getBarcode(), DocFlavor.SERVICE_FORMATTED.PRINTABLE, null);
                ps.createPrintJob().print(doc, attr);
            }  catch (PrintException ex) {
                log.log(Level.SEVERE, "\bBarcode print failed: " + ex.getMessage(), ex);
            }
        }
    }


    class EditCarAction extends AbstractAction
    {
        public EditCarAction() { super(EDITCAR); }
        @Override
        public void actionPerformed(ActionEvent e)
        {
            if (selectedCar == null) return;
            final CarDialog cd = new CarDialog(selectedDriver.getDriverId(), selectedCar, Database.d.getClassData(), false);
            cd.setOkButtonText("Edit");
            cd.doDialog(EDITCAR, new DialogFinisher<Car>() {
                @Override
                public void dialogFinished(Car c) {
                    if ((c == null) || (selectedDriver == null))
                        return;
                    try {
                        c.setDriverId(selectedDriver.getDriverId());
                        Database.d.updateCar(c);
                        reloadCars(c);
                    } catch (Exception ioe) {
                        log.log(Level.SEVERE, "\bFailed to update car: " + ioe.getMessage(), ioe);
                    }
                }
            });
        }
    }


    class DeleteCarAction extends AbstractAction
    {
        public DeleteCarAction() { super(DELETECAR); }
        @Override
        public void actionPerformed(ActionEvent e)
        {
            if (selectedCar == null) return;
            if (JOptionPane.showConfirmDialog(EntryPanel.this, "Are you sure you want to delete the selected car?", DELETECAR, JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
            {
                try {
                    Database.d.deleteCar(selectedCar);
                    reloadCars(null);
                } catch (Exception ioe) {
                    log.log(Level.SEVERE, "\bFailed to delete car: " + ioe, ioe);
                }
            }
        }
    }


    class MergeCarsAction extends AbstractAction
    {
        Car target;
        public MergeCarsAction(int index)
        {
            target = cars.getSelectedValuesList().get(index);
            putValue(NAME, "<html><center><b>Merge Selected Into</b><br/>" + (index+1) + ". " +
                  target.getMake() + " " + target.getModel() + " " + target.getColor() + " #" + target.getNumber());
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            try {
                for (Car c : cars.getSelectedValuesList()) {
                    if (!c.getCarId().equals(target.getCarId()))
                        Database.d.mergeCar(c, target);
                }
                reloadCars(target);
            } catch (Exception sqle) {
                log.log(Level.WARNING, "\bUnable to merge cars: " + sqle, sqle);
            }
        }
    }


    class RegisterAction extends AbstractAction
    {
        public RegisterAction()
        {
            super("Register Only");
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            try {
                Database.d.registerCar(Registration.state.getCurrentEventId(), selectedCar.getCarId());
                reloadCars(selectedCar);
            } catch (Exception sqle) {
                log.log(Level.WARNING, "\bFailed to register car: " + sqle, sqle);
            }
        }
    }


    class UnregisterAction extends AbstractAction
    {
        public UnregisterAction()
        {
            super("Unregister");
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            try {
                Database.d.unregisterCar(Registration.state.getCurrentEventId(), selectedCar.getCarId());
                reloadCars(selectedCar);
            } catch (Exception sqle) {
                log.log(Level.WARNING, "\bFailed to unregister car: " + sqle, sqle);
            }
        }
    }


    class RegisterAndPayAction extends AbstractAction
    {
        public RegisterAndPayAction()
        {
            super("Register/Make Payment");
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            try {
                CurrencyDialog d = new CurrencyDialog("Enter an (additional) amount paid onsite:");
                if (d.doDialog("Payment", null)) {
                    Database.d.registerPayment(Registration.state.getCurrentEventId(), selectedCar.getCarId(), ONSITE_PAYMENT, d.getResult());
                    reloadCars(selectedCar);
                }
            } catch (Exception sqle) {
                log.log(Level.WARNING, "\bFailed to register car and payment: " + sqle, sqle);
            }
        }
    }


    class MovePaymentAction extends AbstractAction
    {
        DecoratedCar dest;
        public MovePaymentAction(DecoratedCar d)
        {
            dest = d;
            String display = "<html><div style='margin-top: 2px;'><b>" + (dest.getIndexCode().equals("") ?
                    dest.getClassCode() + " #" +dest.getNumber() :
                    dest.getClassCode() + " (" + dest.getIndexCode() + ") #" +dest.getNumber());
            display += String.format("</b></div><div style='margin-bottom: 2px;'>%s %s %s %s</div>", dest.getYear(), dest.getMake(), dest.getModel(), dest.getColor());
            this.putValue(NAME, display);
        }

        public void actionPerformed(ActionEvent ev)
        {
            try {
                Database.d.movePayments(Registration.state.getCurrentEventId(), selectedCar.getCarId(), dest.getCarId());
                Database.d.registerCar(Registration.state.getCurrentEventId(), dest.getCarId());
                Database.d.unregisterCar(Registration.state.getCurrentEventId(), selectedCar.getCarId());
            } catch (Exception e) {
                log.log(Level.WARNING, "\bFailed to move payments: " + e, e);
            }
        }
    }


    /**
     * Action that creates a popup menu population with Actions created from the list
     * of cars we can move the payments to.
     */
    class MovePaymentMenuAction extends AbstractAction
    {
        public MovePaymentMenuAction()
        {
            super("Move Registration/Payments To \u2BC6"); // down arrow
        }

        public void actionPerformed(ActionEvent e)
        {
            JPopupMenu menu = new JPopupMenu();
            for (DecoratedCar car : carVector)
            {
                if (car.getCarId().equals(selectedCar.getCarId()) || car.isInRunOrder())
                    continue;
                menu.add(new MovePaymentAction(car));
            }
            Component c = (Component)e.getSource();
            menu.show(c, 5, c.getHeight()-5);
        }
    }


    class DeletePaymentAction extends AbstractAction
    {
        Payment p;
        public DeletePaymentAction(Payment p)
        {
            this.p = p;
            this.putValue(NAME, String.format("<html><font size=+0><b>%s</b> - $%.2f", p.getTxType(), p.getAmount()));
            this.setEnabled(p.getTxType().equals(ONSITE_PAYMENT));
        }

        public void actionPerformed(ActionEvent ev)
        {
            try {
                Database.d.deletePayment(p.getPayId());
            } catch (Exception e) {
                log.log(Level.WARNING, "\bFailed to delete payment: " + e, e);
            }
        }
    }

    /**
     * Action that creates a popup menu population with Actions created from the list
     * of payments we can delete.
     */
    class DeletePaymentMenuAction extends AbstractAction
    {
        public DeletePaymentMenuAction()
        {
            super("Delete A Payment \u2BC6"); // down arrow
        }

        public void actionPerformed(ActionEvent e)
        {
            JPopupMenu menu = new JPopupMenu();
            for (Payment p : selectedCar.getPayments())
                menu.add(new DeletePaymentAction(p));
            Component c = (Component)e.getSource();
            menu.show(c, 5, c.getHeight()-5);
        }
    }


    class FindPrinters implements Runnable
    {
        public void run()
        {
            HashPrintRequestAttributeSet aset = new HashPrintRequestAttributeSet();
            aset.add(new Copies(2)); // silly request but cuts out fax, xps, etc.
            PrintService[] printServices = PrintServiceLookup.lookupPrintServices(DocFlavor.SERVICE_FORMATTED.PRINTABLE, aset);
            for (PrintService ps : printServices) {
                log.log(Level.INFO, "Found printer: {0}", ps);
                printers.addItem(ps);
                if (ps.getName().equals(Prefs.getDefaultPrinter()))
                    printers.setSelectedItem(ps);
            }
        }
    }

    private JComponent createTitle(String text)
    {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("serif", Font.BOLD, 18));
        lbl.setBorder(new UnderlineBorder(0, 0, 0, 0));

        return lbl;
    }

    private JButton smallButton(String text, boolean enabled)
    {
        JButton b = new JButton(text);
        b.setEnabled(enabled);
        b.addActionListener(this);
        return b;
    }

    public void reloadCars(Car select)
    {
        super.reloadCars(select);
        setPaidDriverInfo();
    }

    protected void setPaidDriverInfo()
    {
        paidwarning.setOpaque(false);
        paidwarning.setText("");

        if (selectedDriver == null)
            return;

        ListModel<DecoratedCar> m = cars.getModel();
        if (m.getSize() > 0) {
            for (int ii = 0; ii < m.getSize(); ii++) {
                DecoratedCar c = (DecoratedCar)m.getElementAt(ii);
                if (!c.isInRunOrder() && c.hasPaid()) return;
            }
        }

        paidwarning.setText("No cars paid and not in runorder");
        paidwarning.setOpaque(true);
    }


    /**
     * One of the list value selections has changed.
     * This can be either a user selection or the list model was updated
     */
    @Override
    public void valueChanged(ListSelectionEvent e)
    {
        if (e.getValueIsAdjusting())
           return;
        super.valueChanged(e);

        if (e.getSource() == drivers) {
            driverSelectionChanged();
        } else if (e.getSource() == cars) {
            carSelectionChanged();
        }
    }

    protected void driverSelectionChanged()
    {
        editnotes.setIcon(null);

        if (selectedDriver != null)
        {
            newcar.setEnabled(true);
            editdriver.setEnabled(true);
            editnotes.setEnabled(true);
            weekmember.setEnabled(true);
            barcode.setValue(selectedDriver.getBarcode(), String.format("%s - %s", selectedDriver.getBarcode(), selectedDriver.getFullName()));
            barcode.setWarning("");
            barcode.repaint();


            boolean emptybarcode = selectedDriver.getBarcode().trim().equals("");
            if (!emptybarcode)
            {
                List<Driver> dups = Database.d.findDriverByBarcode(selectedDriver.getBarcode());
                dups.remove(selectedDriver);
                if (dups.size() > 0)
                {
                    StringBuffer buf = new StringBuffer(dups.get(0).getFullName());
                    for (int ii = 1; ii < dups.size(); ii++)
                        buf.append(", ").append(dups.get(ii).getFullName());
                    barcode.setWarning("Duplicate Barcode - " + buf);
                }
            }
            else
            {
                try {
                    if (Database.d.getSetting("requestbarcodes", Boolean.class)) {
                        barcode.setWarning("No Barcode");
                    }
                } catch (NumberFormatException nfe) {}
            }

            weekmember.setIcon((Database.d.getActiveWeekendMembership(selectedDriver.getDriverId()) != null) ? cardicon : null);

            String notes = selectedDriver.getAttrS("notes");
            if (!notes.trim().equals(""))
                editnotes.setIcon(noteicon);
        }
        else
        {
            newcar.setEnabled(false);
            editdriver.setEnabled(false);
            editnotes.setEnabled(false);
            weekmember.setEnabled(false);
            barcode.setValue("", "");
            barcode.setWarning("");
            carVector.clear();
            setPaidDriverInfo(); // make sure to clear old data
        }
    }

    protected void carSelectionChanged()
    {
        List<DecoratedCar> selectedCars = cars.getSelectedValuesList();
        if (selectedCars.size() > 1)
        {
            singleCarPanel.setVisible(false);
            multiCarPanel.removeAll();

            Car first = selectedCars.get(0);
            for (int ii = 1; ii < selectedCars.size(); ii++) {
                if (!first.canMerge(selectedCars.get(ii))) {
                    multiCarPanel.add(mergeWarning, "grow, wrap");
                    break;
                }
            }

            if (multiCarPanel.getComponentCount() == 0) {
                for (int ii = 0; ii < selectedCars.size(); ii++) {
                    multiCarPanel.add(new JButton(new MergeCarsAction(ii)), "grow, pushy 0, wrap");
                }
            }

            multiCarPanel.add(new JLabel(""), "pushy 100, wrap");
            multiCarPanel.setVisible(true);
            multiCarPanel.repaint();
            return;
        }

        multiCarPanel.setVisible(false);
        singleCarPanel.setVisible(true);

        if (selectedCar != null)
        {
            newcarfrom.setEnabled(true);
            editcar.setEnabled(       !selectedCar.isInRunOrder() && !selectedCar.hasOtherActivity());
            deletecar.setEnabled(     !selectedCar.isInRunOrder() && !selectedCar.hasOtherActivity() && !selectedCar.isRegistered());
            registerandpay.setEnabled(!selectedCar.isInRunOrder());
            movepayment.setEnabled(   !selectedCar.isInRunOrder() && selectedCar.hasPaid());
            deletepayment.setEnabled( !selectedCar.isInRunOrder() && selectedCar.hasPaid());
            registerit.setEnabled(    !selectedCar.isInRunOrder() && !selectedCar.isRegistered());
            unregisterit.setEnabled(  !selectedCar.isInRunOrder() && selectedCar.isRegistered() && !selectedCar.hasPaid());
        }
        else
        {
            newcarfrom.setEnabled(false);
            editcar.setEnabled(false);
            deletecar.setEnabled(false);
            registerandpay.setEnabled(false);
            movepayment.setEnabled(false);
            deletepayment.setEnabled(false);
            registerit.setEnabled(false);
            unregisterit.setEnabled(false);
        }
    }

    protected void carCreated()
    {
        if (JOptionPane.showConfirmDialog(this,
                "Do you wish to mark this newly created car as registered and paid?",
                "Register Car",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
        {
            registerandpay.doClick();
        }
    }

    @Override
    public void event(MT type, Object o)
    {
        switch (type)
        {
            case SERIES_CHANGED:
                weekmember.setVisible(Database.d.getSetting("doweekendmembers", Boolean.class));
                break;

            case EVENT_CHANGED:
                driverSelectionChanged();
                reloadCars(selectedCar);
                break;

            case OBJECT_SCANNED:
                if (o instanceof Car) {
                    Car c = (Car)o;
                    Driver d = Database.d.getDriver(c.getDriverId());
                    focusOnDriver(d.getFirstName(), d.getLastName());
                    focusOnCar(c.getCarId());
                }
                if (o instanceof Driver)
                    focusOnDriver(((Driver)o).getFirstName(), ((Driver)o).getLastName());
                break;

            case BARCODE_SCANNED:
                String barcode = (String)o;
                List<Driver> found = Database.d.findDriverByBarcode(barcode);
                if (found.size() == 0)
                {
                    log.log(Level.WARNING, "\bUnable to locate a driver using barcode {0}", barcode);
                    break;
                }

                if (found.size() > 1)
                    log.log(Level.WARNING, "\b{0} drivers exist with the barcode value {1}, using the first", new Object[] {found.size(), barcode});

                Driver d = found.get(0);
                log.info("Focus on driver");
                focusOnDriver(d.getFirstName(), d.getLastName());
                break;
        }
    }
}
