/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2012 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dataentry;

import java.awt.Dimension;
import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import net.miginfocom.swing.MigLayout;

import org.wwscc.barcodes.BarcodeController;
import org.wwscc.components.MyIpLabel;
import org.wwscc.dataentry.tables.DoubleTableContainer;
import org.wwscc.storage.Database;
import org.wwscc.storage.Entrant;
import org.wwscc.storage.Run;
import org.wwscc.util.ApplicationState;
import org.wwscc.util.Discovery;
import org.wwscc.util.AppSetup;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;


public class DataEntry extends JFrame implements MessageListener
{
    private static final Logger log = Logger.getLogger(DataEntry.class.getName());
    public static final ApplicationState state = new ApplicationState();

    Menus menus;
    SelectionBar setupBar;
    AddByNamePanel addByName;
    ClassTree  numberTree;
    AnnouncerPanel announcer;
    TimeEntry timeEntry;
    JTabbedPane tabs;

    final static class HelpPanel extends JLabel implements MessageListener
    {
        private static final long serialVersionUID = -6376824946457087404L;

        public HelpPanel()
        {
            super("Use Tabbed Panels to add Entrants");
            setHorizontalAlignment(CENTER);
            setFont(getFont().deriveFont(12f));
            Messenger.register(MT.OBJECT_CLICKED, this);
            Messenger.register(MT.OBJECT_DCLICKED, this);
        }

        @Override
        public void event(MT type, Object data) {
            switch (type)
            {
                case OBJECT_CLICKED:
                    if (data instanceof Entrant)
                        setText("Entrant: Ctrl-X or Delete to remove them, Drag to move them, Swap Entrant to change them");
                    else if (data instanceof Run)
                        setText("Runs: Ctrl-X or Delete to cut, Ctrl-C to copy, Ctrl-V to paste");
                    else
                        setText("Ctrl-V to paste a Run");
                    break;
                case OBJECT_DCLICKED:
                    if (data instanceof Entrant)
                        setText("Click Swap Entrant to change the entrant for the given runs");
                    break;
            }
        }
    }

    public DataEntry() throws IOException
    {
        super("DataEntry");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setupBar = new SelectionBar();
        numberTree = new ClassTree();
        addByName = new AddByNamePanel();
        announcer = new AnnouncerPanel();

        tabs = new JTabbedPane();
        tabs.setMinimumSize(new Dimension(270, 400));
        tabs.setPreferredSize(new Dimension(270, 768));
        tabs.addTab("Add By Name", addByName);
        tabs.addTab("Quick Entry", new QuickEntrySearch());
        tabs.addTab("Preregistered", new JScrollPane(numberTree));
        tabs.addTab("Announcer Data", announcer);

        DoubleTableContainer tableScroll = new DoubleTableContainer();
        timeEntry = new TimeEntry();

        menus = new Menus(new BarcodeController(), timeEntry.getTimerMenu());
        setJMenuBar(menus);

        HelpPanel help = new HelpPanel();
        MyIpLabel myip = new MyIpLabel();
        help.setBorder(BorderFactory.createLoweredBevelBorder());
        myip.setBorder(BorderFactory.createLoweredBevelBorder());

        JPanel infoBoxes = new JPanel(new MigLayout("fill, ins 0", "[75%]0[25%]"));
        infoBoxes.add(help, "grow, hmin 20");
        infoBoxes.add(myip, "grow");

        JPanel miniPanels = new JPanel(new MigLayout("fill, ins 0, gap 0", "", ""));
        miniPanels.add(new MiniInput.ManualBarcodeInput(), "growx, growy 0, hidemode 2, wrap");
        miniPanels.add(new MiniInput.FilterEntries(), "growx, growy 0, hidemode 2, wrap");

        JPanel content = new JPanel(new MigLayout("fill, ins 1, gap 2", "[grow 0][fill][grow 0]", "[grow 0][grow 0][grow 100][grow 0]"));
        content.add(setupBar, "spanx 3, growx, wrap");
        content.add(tabs, "spany 2, growx 0, growy");
        content.add(miniPanels, "growx, growy 0, hidemode 2");
        content.add(timeEntry, "spany 2, growx 0, growy, gap 0 4, w 150!, wrap");
        content.add(tableScroll, "grow, wrap");
        content.add(infoBoxes, "spanx 3, growx, wrap");

        setContentPane(content);
        setBounds(Prefs.getWindowBounds("dataentry"));
        Prefs.trackWindowBounds(this, "dataentry");
        setVisible(true);

        log.log(Level.INFO, "Starting Application: {0}", new java.util.Date());

        Messenger.register(MT.OBJECT_DCLICKED, this);
        Messenger.register(MT.DATABASE_NOTIFICATION, this);
        Database.openDefault();

        Discovery.get().registerService(Prefs.getServerId(), Discovery.DATAENTRY_TYPE, new ObjectNode(JsonNodeFactory.instance));
    }


    @Override
    public void event(MT type, Object o)
    {
        switch (type)
        {
            case OBJECT_DCLICKED:
                if (o instanceof Entrant)
                    tabs.setSelectedComponent(addByName);
                break;
            case DATABASE_NOTIFICATION:
                @SuppressWarnings("unchecked")
                Set<String> tables = (Set<String>)o;
                if (tables.contains("registered") || tables.contains("runorder") || tables.contains("cars") || tables.contains("drivers")) {
                    // We do not refresh on runs as 1) we are most likely changing it and 2) it messes up the current table selection
                    // The user has the refresh button if they need it
                    log.fine("directing db notification into entrants changed");
                    Messenger.sendEventNow(MT.ENTRANTS_CHANGED, null);
                }
                break;
        }
    }

    /**
     * Main
     * @param args the command line args, added to title
     */
    public static void main(String args[])
    {
        try
        {
            AppSetup.appSetup("dataentry");
            new DataEntry();
        }
        catch (Throwable e)
        {
            log.log(Level.SEVERE, "\bApp failure: " + e, e);
        }
    }
}

