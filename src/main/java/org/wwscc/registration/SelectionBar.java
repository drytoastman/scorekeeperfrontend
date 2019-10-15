/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2010 Brett Wilson.
 * All rights reserved.
 */


package org.wwscc.registration;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Set;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.BevelBorder;

import net.miginfocom.swing.MigLayout;

import org.wwscc.components.CurrentSeriesLabel;
import org.wwscc.storage.Database;
import org.wwscc.storage.Entrant;
import org.wwscc.storage.Event;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;


class SelectionBar extends JPanel implements ActionListener, MessageListener
{
    JComboBox<Event> eventSelect;
    JCheckBox lock;
    JLabel p1count;
    JLabel p2count;

    public SelectionBar()
    {
        super();

        Messenger.register(MT.SERIES_CHANGED, this);
        Messenger.register(MT.EVENT_CHANGED, this);
        Messenger.register(MT.DATABASE_NOTIFICATION, this);

        setLayout(new MigLayout("ins 2, center, gap 4"));
        setBorder(new BevelBorder(0));

        Font f = new Font(Font.DIALOG, Font.BOLD, 14);
        eventSelect = new JComboBox<Event>();
        eventSelect.setActionCommand("eventChange");
        eventSelect.addActionListener(this);

        lock = new JCheckBox("Lock");
        lock.setActionCommand("lockEvent");
        lock.addActionListener(this);

        JLabel dl = new JLabel("Series:");
        dl.setFont(f);
        JLabel el = new JLabel("Event:");
        el.setFont(f);
        JLabel p1l = new JLabel("Reg:");
        p1l.setFont(f);
        JLabel p2l = new JLabel("Paid:");
        p2l.setFont(f);

        p1count = new JLabel("");
        p2count = new JLabel("");

        add(dl, "");
        add(new CurrentSeriesLabel(), "");
        add(el, "gap left 20");
        add(eventSelect, "");
        add(lock, "gap right 25");
        add(p1l, "");
        add(p1count, "gap right 15");
        add(p2l, "");
        add(p2count, "");
    }

    protected void updateCounts()
    {
        int p1 = 0, p2 = 0;
        for (Entrant e : Database.d.getRegisteredEntrants(Registration.state.getCurrentEventId())) {
            p1++;
            if (e.isPaid()) p2++;
        }
        p1count.setText(""+p1);
        p2count.setText(""+p2);
    }

    @Override
    public void event(MT type, Object o)
    {
        switch (type)
        {
            case SERIES_CHANGED:
                Registration.state.setCurrentSeries((String)o);
                eventSelect.setModel(new DefaultComboBoxModel<Event>(Database.d.getEvents().toArray(new Event[0])));
                int select = Prefs.getEventIndex(0);
                if (select < eventSelect.getItemCount())
                    eventSelect.setSelectedIndex(select);
                else if (eventSelect.getItemCount() > 0)
                    eventSelect.setSelectedIndex(0);
                updateCounts();
                break;

            case EVENT_CHANGED:
                updateCounts();
                break;

            case DATABASE_NOTIFICATION:
                 @SuppressWarnings("unchecked")
                 Set<String> tables = (Set<String>)o;
                 if (tables.contains("registered") || tables.contains("payments")) {
                     updateCounts();
                 }
                 break;
        }
    }

    /**
     *
     */
    @Override
    public void actionPerformed(ActionEvent e)
    {
        if (e.getActionCommand().equals("eventChange"))
        {
            JComboBox<?> cb = (JComboBox<?>)e.getSource();
            Registration.state.setCurrentEvent(((Event)cb.getSelectedItem()).toEventInfo());
            Registration.state.setCurrentCourse(1);
            Messenger.sendEvent(MT.EVENT_CHANGED, null);
            Prefs.setEventIndex(eventSelect.getSelectedIndex());
        }
        else if (e.getActionCommand().equals("lockEvent"))
        {
            JCheckBox cb = (JCheckBox)e.getSource();
            eventSelect.setEnabled(!cb.isSelected());
        }
    }
}

