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
import java.util.UUID;

import javax.swing.Action;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTabbedPane;
import org.wwscc.actions.EventSendAction;
import org.wwscc.components.DriverCarPanelBase;
import org.wwscc.components.UnderlineBorder;
import org.wwscc.storage.Database;
import org.wwscc.storage.Driver;
import org.wwscc.storage.Entrant;
import org.wwscc.storage.DecoratedCar;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;

import net.miginfocom.swing.MigLayout;


public class AddByNamePanel extends DriverCarPanelBase implements MessageListener
{
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
        addit = new JButton(new EventSendAction<UUID>("Add Entrant", MT.CAR_ADD, () -> (selectedCar != null) ? selectedCar.getCarId() : null));
        addit.setEnabled(false);

        changeit = new JButton(new EventSendAction<UUID>("Swap Entrant", MT.CAR_CHANGE, () -> (selectedCar != null) ? selectedCar.getCarId() : null));
        changeit.setEnabled(false);

        add(createTitle("1. Search"), "wrap");
        add(new JLabel("First Name"), "split, grow 0");
        add(firstSearch, "wrap");
        add(new JLabel("Last Name"), "split, grow 0");
        add(lastSearch, "wrap");
        add(smallButton(new ClearSearchAction()), "wrap");

        add(createTitle("2. Driver"), "wrap");
        add(dscroll, "pushy 100, grow, wrap");
        add(smallButton(new NewDriverAction()), "split");
        add(smallButton(new EditDriverAction()), "wrap");

        add(createTitle("3. Car"), "wrap");
        add(cscroll, "pushy 100, grow, wrap");
        add(smallButton(new NewCarAction(false)), "split");
        add(smallButton(new NewCarAction(true)), "wrap");

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

    private JButton smallButton(Action action)
    {
        JButton b = new JButton(action);
        b.setFont(new Font(null, Font.PLAIN, 10));
        return b;
    }

    @Override
    protected void carSelectionChanged()
    {
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
                reloadDrivers();
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
