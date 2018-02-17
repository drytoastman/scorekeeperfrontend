/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2010 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.registration;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import org.wwscc.actions.OpenSeriesAction;
import org.wwscc.actions.QuitAction;
import org.wwscc.barcodes.BarcodeSetupMenu;
import org.wwscc.barcodes.KeyboardBarcodeWatcher;
import org.wwscc.storage.Database;
import org.wwscc.util.ApplicationState;
import org.wwscc.util.BrowserControl;
import org.wwscc.util.AppSetup;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;


public class Registration extends JFrame
{
    private static final Logger log = Logger.getLogger(Registration.class.getCanonicalName());
    public static final ApplicationState state = new ApplicationState();

    SelectionBar setupBar;
    EntryPanel driverEntry;

    public Registration() throws IOException
    {
        super("Registration");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        KeyboardBarcodeWatcher w = new KeyboardBarcodeWatcher();
        w.start();

        setupBar = new SelectionBar();
        driverEntry = new EntryPanel();

        BorderLayout layout = new BorderLayout();
        JPanel content = new JPanel(layout);
        content.add(setupBar, BorderLayout.NORTH);
        content.add(driverEntry, BorderLayout.CENTER);

        setContentPane(content);
        log.log(Level.INFO, "Starting Registration: {0}", new java.util.Date());

        JMenu file = new JMenu("File");
        file.add(new OpenSeriesAction());
        file.add(new JSeparator());
        file.add(new QuitAction());

        JMenu find = new JMenu("Find By...");
        find.add(new FindByAction("Membership", 'M'));

        JMenu reports = new JMenu("Reports");
        reports.add(new OpenReportAction("Numbers Report", "numbers"));
        reports.add(new OpenReportAction("Payments Report", "payments"));

        JMenuBar bar = new JMenuBar();
        bar.add(file);
        bar.add(find);
        bar.add(new BarcodeSetupMenu());
        bar.add(reports);
        setJMenuBar(bar);

        setBounds(Prefs.getWindowBounds("registration"));
        Prefs.trackWindowBounds(this, "registration");
        setVisible(true);

        Database.openDefault();
    }


    final static class FindByAction extends AbstractAction
    {
        String type;
        char prefix;
        public FindByAction(String t, char key)
        {
            super();
            type = t;
            prefix = key;
            putValue(NAME, type);
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyStroke.getAWTKeyStroke(prefix).getKeyChar(), ActionEvent.CTRL_MASK));
        }

        public void actionPerformed(ActionEvent e)
        {
            Object o = JOptionPane.showInputDialog("Enter " + type);
            if (o != null)
                Messenger.sendEvent(MT.BARCODE_SCANNED, (prefix != 'M') ? prefix+o.toString() : o);
        }
    }

    final static class OpenReportAction extends AbstractAction
    {
        String report;
        public OpenReportAction(String name, String r)
        {
            super(name);
            report = r;
        }

        public void actionPerformed(ActionEvent e)
        {
            BrowserControl.openReport(Registration.state, report);
        }
    }


    /**
     * Main
     * @param args command line args ignored
     */
    public static void main(String[] args)
    {
        try
        {
            AppSetup.appSetup("registration");
            new Registration();
            Messenger.register(MT.DATABASE_NOTIFICATION, (t,o) ->  {
                @SuppressWarnings("unchecked")
                Set<String> tables = (Set<String>)o;
                if (tables.contains("drivers") || tables.contains("cars") || tables.contains("registered") || tables.contains("runorder") || tables.contains("runs") || tables.contains("payments")) {
                    log.fine("directing db notification into event changed");
                    Messenger.sendEvent(MT.EVENT_CHANGED, null); // simple reload all event for registration, event didn't really change
                }
            });
        }
        catch (Throwable e)
        {
            log.log(Level.SEVERE, "\bRegistration main failure: " + e, e);
        }
    }
}

