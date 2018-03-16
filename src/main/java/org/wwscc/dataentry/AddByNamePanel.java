/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */


package org.wwscc.dataentry;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTabbedPane;
import javax.swing.event.ListSelectionEvent;
import org.wwscc.components.DriverCarPanel;
import org.wwscc.components.UnderlineBorder;
import org.wwscc.storage.Database;
import org.wwscc.storage.Driver;
import org.wwscc.storage.Entrant;
import org.wwscc.storage.DecoratedCar;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;

import net.miginfocom.swing.MigLayout;


public class AddByNamePanel extends DriverCarPanel implements MessageListener
{
    //private static final Logger log = Logger.getLogger(DriverEntry.class.getCanonicalName());

    JButton addit, changeit;
    boolean carAlreadyInOrder = true;
    boolean entrantIsSelected = false;

    public AddByNamePanel()
    {
        super(DataEntry.state);
        setLayout(new MigLayout("ins 5, gap 3, fill", "fill", "fill"));
        carAddOption = true;

        Messenger.register(MT.OBJECT_CLICKED, this);
        Messenger.register(MT.OBJECT_DCLICKED, this);
        Messenger.register(MT.ENTRANTS_CHANGED, this);
        Messenger.register(MT.SHOW_ADD_PANE, this);
        Messenger.register(MT.COURSE_CHANGED, this);

        MyListRenderer listRenderer = new MyListRenderer();
        drivers.setCellRenderer(listRenderer);
        cars.setCellRenderer(listRenderer);

        /* Buttons */
        addit = new JButton("Add Entrant");
        addit.addActionListener(this);
        addit.setEnabled(false);

        changeit = new JButton("Swap Entrant");
        changeit.addActionListener(this);
        changeit.setEnabled(false);

        add(createTitle("1. Search"), "wrap");
        add(new JLabel("First Name"), "split, grow 0");
        add(firstSearch, "wrap");
        add(new JLabel("Last Name"), "split, grow 0");
        add(lastSearch, "wrap");
        add(smallButton(CLEAR), "wrap");

        add(createTitle("2. Driver"), "wrap");
        add(dscroll, "pushy 100, grow, wrap");
        add(smallButton(NEWDRIVER), "split");
        add(smallButton(EDITDRIVER), "wrap");

        add(createTitle("3. Car"), "wrap");
        add(cscroll, "pushy 100, grow, wrap");
        add(smallButton(NEWCAR), "split");
        add(smallButton(NEWFROM), "wrap");

        add(createTitle("4. Do it"), "wrap");
        add(addit, "split");
        add(changeit, "wrap");

        add(new JLabel(""), "h 15!");
    }


    private JComponent createTitle(String text)
    {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("serif", Font.BOLD, 16));
        lbl.setBorder(new UnderlineBorder(0, 0, 0, 0));

        return lbl;
    }

    private JButton smallButton(String text)
    {
        JButton b = new JButton(text);
        b.setFont(new Font(null, Font.PLAIN, 10));
        b.addActionListener(this);
        return b;
    }

    /**
     * Process events from the various buttons
     */
    @Override
    public void actionPerformed(ActionEvent e)
    {
        String cmd = e.getActionCommand();

        if (cmd.equals("Add Entrant"))
        {
            if (selectedCar != null)
                Messenger.sendEvent(MT.CAR_ADD, selectedCar.getCarId());
        }

        else if (cmd.equals("Swap Entrant"))
        {
            if (selectedCar != null)
                Messenger.sendEvent(MT.CAR_CHANGE, selectedCar.getCarId());
        }

        else
            super.actionPerformed(e);
    }


    /**
     * One of the list value selections has changed.
     * This can be either a user selection or the list model was updated
     */
    @Override
    public void valueChanged(ListSelectionEvent e)
    {
        super.valueChanged(e);
        carAlreadyInOrder = ((selectedCar == null) || (selectedCar.isInRunOrder()));
        addit.setEnabled(!carAlreadyInOrder);
        changeit.setEnabled(!carAlreadyInOrder && entrantIsSelected);
    }


    @Override
    public void event(MT type, Object o)
    {
        switch (type)
        {
            case OBJECT_CLICKED:
                entrantIsSelected = (o instanceof Entrant);
                changeit.setEnabled(!carAlreadyInOrder && entrantIsSelected);
                break;

            case OBJECT_DCLICKED:
                if (o instanceof Entrant)
                {
                    Entrant e = (Entrant)o;
                    focusOnDriver(e.getFirstName(), e.getLastName());
                    focusOnCar(e.getCarId());
                }
                break;

            case ENTRANTS_CHANGED: // resync loaded cars to check status
            case COURSE_CHANGED:
                reloadCars(selectedCar);
                break;

            case SHOW_ADD_PANE:
                if (o instanceof Driver)
                {
                    Driver d = (Driver)o;
                    focusOnDriver(d.getFirstName(), d.getLastName());
                    if (getParent() instanceof JTabbedPane) {
                        ((JTabbedPane)getParent()).setSelectedComponent(this);
                    }
                }
                break;
        }
    }


    final static class MyListRenderer extends DefaultListCellRenderer
    {
        private Color mygray = new Color(120,120,120);

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean iss, boolean chf)
        {
            super.getListCellRendererComponent(list, value, index, iss, chf);

            setForeground(Color.BLACK);

            if (value instanceof DecoratedCar)
            {
                DecoratedCar c = (DecoratedCar)value;
                String myclass = c.getClassCode() + " " + Database.d.getEffectiveIndexStr(c);
                setText(myclass + " #" + c.getNumber() + ": " + c.getYear() + " " + c.getModel() + " " + c.getColor());
                if (c.isInRunOrder())
                    setForeground(mygray);
            }
            else if (value instanceof Driver)
            {
                Driver d = (Driver)value;
                if (d.getBarcode().trim().equals(""))
                    setText(d.getFullName());
                else
                    setText(d.getFullName() + " - " + d.getBarcode());
            }

            return this;
        }
    }
}
