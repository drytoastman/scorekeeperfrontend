/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dataentry;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;

import org.wwscc.actions.OpenSeriesAction;
import org.wwscc.actions.EventSendAction;
import org.wwscc.actions.QuitAction;
import org.wwscc.dataentry.actions.RunOrderFromGridAction;
import org.wwscc.dataentry.actions.ScratchWindowAction;
import org.wwscc.dialogs.BaseDialog.DialogFinisher;
import org.wwscc.dialogs.GroupDialog;
import org.wwscc.storage.Database;
import org.wwscc.storage.Event;
import org.wwscc.util.BrowserControl;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;


public class Menus extends JMenuBar implements ActionListener, MessageListener
{
    private static final Logger log = Logger.getLogger("org.wwscc.dataentry.Menus");

    Map <String,JMenuItem> items;
    JMenu shortcuts, event, prosolo, preferences, reports;
    JCheckBoxMenuItem paidInfoMode;
    JCheckBoxMenuItem reorderMode;
    JCheckBoxMenuItem printMode;
    ButtonGroup runGrouping;

    public Menus(JMenu barcode, JMenu timer)
    {
        items = new HashMap <String,JMenuItem>();
        Messenger.register(MT.SERIES_CHANGED, this);
        Messenger.register(MT.EVENT_CHANGED, this);

        /* File Menu */
        JMenu file = new JMenu("File");
        file.add(new OpenSeriesAction());
        file.add(new QuitAction());

        /* Edit Menu */
        shortcuts = new JMenu("Shortcuts");
        shortcuts.add(new EventSendAction<>("Manual Barcode Entry", MT.OPEN_BARCODE_ENTRY, null, KeyStroke.getKeyStroke(KeyEvent.VK_B, ActionEvent.CTRL_MASK)));
        shortcuts.add(new EventSendAction<>("Quick Entry", MT.QUICKID_SEARCH, null, KeyStroke.getKeyStroke(KeyEvent.VK_Q, ActionEvent.CTRL_MASK)));
        shortcuts.add(new EventSendAction<>("Add By Name", MT.ADD_BY_NAME, null, KeyStroke.getKeyStroke(KeyEvent.VK_N, ActionEvent.CTRL_MASK)));
        shortcuts.add(new EventSendAction<>("Filter Table", MT.OPEN_FILTER, null, KeyStroke.getKeyStroke(KeyEvent.VK_F, ActionEvent.CTRL_MASK)));
        shortcuts.add(new EventSendAction<>("Time Focus", MT.TIME_FOCUS, null, KeyStroke.getKeyStroke(KeyEvent.VK_T, ActionEvent.CTRL_MASK)));
        shortcuts.add(new ScratchWindowAction());

        /* Options Menu */
        preferences = new JMenu("Preferences");

        printMode = new JCheckBoxMenuItem("Print Directly", Prefs.getPrintDirectly());
        printMode.addActionListener(e -> Prefs.setPrintDirectly(printMode.isSelected()));
        preferences.add(printMode);

        paidInfoMode = new JCheckBoxMenuItem("Highlight Unpaid Entries", Prefs.usePaidFlag());
        paidInfoMode.addActionListener(e -> { Prefs.setUsePaidFlag(paidInfoMode.isSelected()); Messenger.sendEvent(MT.EVENT_CHANGED, null); });
        preferences.add(paidInfoMode);

        reorderMode = new JCheckBoxMenuItem("Constant Staging Mode", Prefs.useReorderingTable());
        reorderMode.addActionListener(e -> Prefs.setReorderingTable(reorderMode.isSelected()));
        preferences.add(reorderMode);

        /* Event Menu */
        event = new JMenu("Event");
        runGrouping = new ButtonGroup();
        JMenu runs = new JMenu("Set Runs");
        event.add(runs);
        for (int ii = 2; ii <= 20; ii++)
        {
            JRadioButtonMenuItem m = new JRadioButtonMenuItem(ii + " Runs");
            m.addActionListener(this);
            runGrouping.add(m);
            runs.add(m);
        }

        prosolo = new JMenu("ProSolo");
        prosolo.add(new RunOrderFromGridAction());
        //prosolo.add(new ReorderByNetAction());

        /* Results Menu */
        reports = new JMenu("Reports");
        JMenu audit = new JMenu("Current Group Audit");
        audit.add(createItem("In Run Order", null));
        audit.add(createItem("Order By First Name", null));
        audit.add(createItem("Order By Last Name", null));
        reports.add(createItem("Multiple Group Results", null));
        reports.add(audit);
        reports.add(createItem("Results Page", null));
        reports.add(createItem("Admin Page", null));


        JMenu spacer = new JMenu("  |  ");
        spacer.setEnabled(false);

        add(file);
        add(event);
        add(prosolo);
        add(reports);
        add(spacer);
        add(shortcuts);
        add(preferences);
        add(barcode);
        add(timer);
    }

    protected final JMenuItem createItem(String title, KeyStroke ks)
    {
        JMenuItem item = new JMenuItem(title);

        item.addActionListener(this);
        if (ks != null)
            item.setAccelerator(ks);
        items.put(title, item);
        return item;
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        String cmd = e.getActionCommand();
        if (cmd.equals("Multiple Group Results"))
        {
            new GroupDialog().doDialog("Select groups to view:", new DialogFinisher<String[]>() {
                @Override
                public void dialogFinished(String[] result) {
                    if (result != null)
                        BrowserControl.openGroupResults(DataEntry.state, result);
                }
            });
        }
        else if (cmd.startsWith("Order By First Name")) BrowserControl.openAuditReport(DataEntry.state, "firstname");
        else if (cmd.startsWith("Order By Last Name")) BrowserControl.openAuditReport(DataEntry.state, "lastname");
        else if (cmd.startsWith("In Run Order")) BrowserControl.openAuditReport(DataEntry.state, "runorder");
        else if (cmd.startsWith("Results Page")) BrowserControl.openResults(DataEntry.state, "");
        else if (cmd.startsWith("Admin Page")) BrowserControl.openAdmin(DataEntry.state, "");
        else if (cmd.endsWith("Runs"))
        {
            if (DataEntry.state.getCurrentEvent() == null)
            {
                log.warning("\bCan't set runs when there is no series/event open");
                return;
            }

            int runs = Integer.parseInt(cmd.split(" ")[0]);
            if ((runs > 1) && (runs < 100))
            {
                Event event = DataEntry.state.getCurrentEvent();
                int save = event.getRuns();
                event.setRuns(runs);
                if (Database.d.updateEventRuns(event.getEventId(), runs))
                    Messenger.sendEvent(MT.EVENT_CHANGED, null);
                else
                    event.setRuns(save); // We bombed
            }
        }
        else
        {
            log.log(Level.INFO, "Unknown command from menubar: {0}", cmd);
        }
    }


    @Override
    public void event(MT type, Object data)
    {
        switch (type)
        {
            case SERIES_CHANGED:
                boolean blank = ((data == null) || (data.equals("<none>")));
                shortcuts.setEnabled(!blank);
                event.setEnabled(!blank);
                reports.setEnabled(!blank);
                break;

            case EVENT_CHANGED:
                /* when we first start or the a new event is selected, will also double when selecting via menu */
                Enumeration<AbstractButton> e = runGrouping.getElements();
                while (e.hasMoreElements())
                {
                    AbstractButton b = e.nextElement();
                    int run = Integer.parseInt(b.getActionCommand().split(" ")[0]);
                    if (run == DataEntry.state.getCurrentEvent().getRuns())
                        b.setSelected(true);
                }

                prosolo.setEnabled(DataEntry.state.getCurrentEvent().isPro());
                break;
        }
    }
}

