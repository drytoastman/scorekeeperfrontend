/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */


package org.wwscc.dataentry;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.BevelBorder;
import net.miginfocom.swing.MigLayout;

import org.wwscc.components.CurrentSeriesLabel;
import org.wwscc.storage.Database;
import org.wwscc.storage.Driver;
import org.wwscc.storage.Event;
import org.wwscc.storage.Run;
import org.wwscc.util.BrowserControl;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;
import org.wwscc.util.TimedLabel;


class SelectionBar extends JPanel implements ActionListener, MessageListener
{
    //private static Logger log = Logger.getLogger(SelectionBar.class.getCanonicalName());

    JButton refreshButton, resultsButton, placeOrphanedButton;
    CurrentSeriesLabel seriesLabel;
    JLabel entrantCountLabel;
    TimedLabel scannedLabel;

    JComboBox<Event> eventSelect;
    JComboBox<Integer> courseSelect;
    JComboBox<Integer> groupSelect;

    public SelectionBar()
    {
        super(new MigLayout("fill, ins 3, hidemode 3", "fill"));

        Messenger.register(MT.SERIES_CHANGED, this);
        Messenger.register(MT.RUNGROUP_CHANGED, this);
        Messenger.register(MT.ENTRANTS_CHANGED, this);
        Messenger.register(MT.DRIVER_SCAN_ACCEPTED, this);
        setBorder(new BevelBorder(0));

        Font f = new Font(Font.DIALOG, Font.BOLD, 14);
        Font fn = f.deriveFont(Font.PLAIN);

        resultsButton = new JButton("Print Current Group Results");
        resultsButton.addActionListener(this);
        resultsButton.setActionCommand("resultsPrint");

        refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> Messenger.sendEvent(MT.RUNGROUP_CHANGED, null));

        placeOrphanedButton = new JButton("");
        placeOrphanedButton.setVisible(false);

        seriesLabel = new CurrentSeriesLabel();
        seriesLabel.setFont(fn);
        entrantCountLabel = new JLabel("0");
        entrantCountLabel.setFont(fn);

        scannedLabel = new TimedLabel(5000);
        scannedLabel.setFont(fn.deriveFont(Font.BOLD));
        scannedLabel.setForeground(new Color(200, 130, 0));

        courseSelect = createCombo("courseChange");
        groupSelect  = createCombo("groupChange");
        eventSelect = createCombo("eventChange");

        groupSelect.setModel(new DefaultComboBoxModel<Integer>(new Integer[] { 1, 2, 3, 4, 5, 6 }));

        add(createLabel("Series:", f), "gapleft 10");
        add(seriesLabel, "gapright 10");

        add(createLabel("Event:", f), "");
        add(eventSelect, "gapright 5");

        add(createLabel("Course:", f));
        add(courseSelect, "gapright 5");

        add(createLabel("RunGroup:", f));
        add(groupSelect, "gapright 5");

        add(createLabel("Count:", f));
        add(entrantCountLabel, "gapright 10");

        add(placeOrphanedButton, "gapright 5");
        add(scannedLabel, "gapright 5");

        add(new JLabel(""), "growx 100, pushx 100");

        add(refreshButton, "");
        add(resultsButton, "gapright 5");
    }


    private JLabel createLabel(String txt, Font f)
    {
        JLabel l = new JLabel(txt);
        l.setFont(f);
        return l;
    }

    private <E> JComboBox<E> createCombo(String name)
    {
        JComboBox<E> combo = new JComboBox<E>();
        combo.setActionCommand(name);
        combo.addActionListener(this);
        return combo;
    }


    public void setCourseList(int count)
    {
        Integer list[] = new Integer[count];
        for (int ii = 0; ii < count; ii++)
            list[ii] = (ii+1);

        courseSelect.setModel(new DefaultComboBoxModel<Integer>(list));
    }


    @Override
    public void event(MT type, Object o)
    {
        switch (type)
        {
            case SERIES_CHANGED:
                DataEntry.state.setCurrentSeries((String)o);
                eventSelect.setModel(new DefaultComboBoxModel<Event>(Database.d.getEvents().toArray(new Event[0])));
                int select = Prefs.getEventId(0);
                if (select < eventSelect.getItemCount())
                    eventSelect.setSelectedIndex(select);
                else if (eventSelect.getItemCount() > 0)
                    eventSelect.setSelectedIndex(0);
                break;

            case RUNGROUP_CHANGED:
            case ENTRANTS_CHANGED:
                List<UUID> runorder = Database.d.getCarIdsForRunGroup(DataEntry.state.getCurrentEventId(), DataEntry.state.getCurrentCourse(), DataEntry.state.getCurrentRunGroup());
                entrantCountLabel.setText(""+runorder.size());

                List<Run> orphaned = Database.d.getOrphanedRuns(DataEntry.state.getCurrentEventId());
                Map<UUID, Long> x = orphaned.stream()
                            .filter(r -> r.course() == DataEntry.state.getCurrentCourse())
                            .collect(Collectors.groupingBy(r -> { return r.getCarId(); }, Collectors.counting()));
                placeOrphanedButton.setText(x.size() + " orphaned cars for this course");
                placeOrphanedButton.setVisible(x.size() > 0);
                break;

            case DRIVER_SCAN_ACCEPTED:
                Driver d = (Driver)o;
                scannedLabel.setText("Scan Driver " + d.getBarcode());
                break;
        }
    }

    /**
     *
     */
    @Override
    public void actionPerformed(ActionEvent e)
    {
        String cmd = e.getActionCommand();

        if (cmd.endsWith("Change"))
        {
            JComboBox<?> cb = (JComboBox<?>)e.getSource();
            Object o = cb.getSelectedItem();

            if (cmd.startsWith("event"))
            {
                DataEntry.state.setCurrentEvent((Event)o);
                Messenger.sendEvent(MT.EVENT_CHANGED, null);
                Prefs.setEventId(eventSelect.getSelectedIndex());
                setCourseList(DataEntry.state.getCurrentEvent().getCourses());
                courseSelect.setSelectedIndex(0);
            }
            else if (cmd.startsWith("course"))
            {
                DataEntry.state.setCurrentCourse((Integer)o);
                Messenger.sendEvent(MT.COURSE_CHANGED, null);
                groupSelect.setSelectedIndex(groupSelect.getSelectedIndex());
            }
            else if (cmd.startsWith("group"))
            {
                DataEntry.state.setCurrentRunGroup((Integer)o);
                Messenger.sendEvent(MT.RUNGROUP_CHANGED, null);
            }
        }
        else if (cmd.endsWith("Print"))
        {
            if (cmd.startsWith("results"))
                BrowserControl.printGroupResults(DataEntry.state);
        }
    }
}

