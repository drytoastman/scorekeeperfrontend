/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dialogs;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import org.wwscc.storage.Database;
import org.wwscc.storage.MergeServer;
import org.wwscc.storage.PostgresqlDatabase;
import org.wwscc.util.TextChangeTrigger;

import net.miginfocom.swing.MigLayout;

public class SeriesSelectionDialog extends BaseDialog<SeriesSelectionDialog.HSResult>
{
    private static final Logger log = Logger.getLogger(SeriesSelectionDialog.class.getCanonicalName());

    MergeServer server;
    GetRemoteSeries seriesgetter;
    CheckRemotePassword passchecker;
    JLabel errornote;

    static public class HSResult
    {
        public String series;
        public String password;
    }

    public SeriesSelectionDialog(MergeServer s)
    {
        super(new MigLayout("", "[][fill, 300]", "[fill]"), false);
        mainPanel.add(label("Series", true), "");
        mainPanel.add(select("series", null, new Object[] {}, this), "grow, wrap");
        mainPanel.add(label("Password", true), "");
        mainPanel.add(entry("password", ""), "grow, wrap");
        ok.setText("Verify Password");
        errornote = new JLabel(" ", SwingConstants.CENTER);
        errornote.setForeground(Color.RED);
        mainPanel.add(errornote, "spanx 2, center, grow, wrap");

        server = s;
        selects.get("series").addAncestorListener(new AncestorListener() {
            @Override public void ancestorRemoved(AncestorEvent event) {}
            @Override public void ancestorMoved(AncestorEvent event) {}
            @Override
            public void ancestorAdded(AncestorEvent event) {
                selects.get("series").setModel(new DefaultComboBoxModel<Object>(new String[] { "loading ..." }));
                if (currentDialog != null)
                    currentDialog.pack();
                if (seriesgetter != null)
                    seriesgetter.cancel(true);
                seriesgetter = new GetRemoteSeries(server);
                seriesgetter.execute();
            }
        });

        fields.get("password").getDocument().addDocumentListener(new TextChangeTrigger() {
            @Override public void changedTo(String txt) {
                ok.setText("Verify Password");
        }});
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        if (e.getSource() == selects.get("series"))
        {
            ok.setText("Verify Password");
            errornote.setText(" ");
        }
        else if ((e.getSource() == ok) && (ok.getText().equals("Verify Password")))
        {
            errornote.setText("Checking ...");
            if (passchecker != null)
                passchecker.cancel(true);
            String s = (String)getSelect("series");
            String p = getEntryText("password");
            if ((server != null) && (s != null) && (p != null))
            {
                passchecker = new CheckRemotePassword(server.getConnectEndpoint(), s, p);
                passchecker.execute();
            }
        }
        else
            super.actionPerformed(e);
    }

    @Override
    public HSResult getResult()
    {
        HSResult ret = new HSResult();
        ret.series = (String)getSelect("series");
        ret.password = getEntryText("password");
        return ret;
    }

    @Override
    public boolean verifyData()
    {
        return ok.getText().equals("Download");
    }

    class GetRemoteSeries extends SwingWorker<List<String>, String>
    {
        MergeServer server;
        public GetRemoteSeries(MergeServer fromhost)
        {
            server = fromhost;
        }

        @Override
        protected List<String> doInBackground() throws Exception
        {
            try (PostgresqlDatabase db = new PostgresqlDatabase(server.getConnectEndpoint(), "nulluser", "nulluser", 0)) {
                List<String> series = db.getSeriesList();
                series.removeAll(Database.d.getSeriesList());
                if (series.isEmpty())
                    errornote.setText("No additional series on remote server");
                return series;
            }
        }

        @Override
        protected void done()
        {
            try {
                selects.get("series").setModel(new DefaultComboBoxModel<Object>(get().toArray()));
                if (currentDialog != null)
                    currentDialog.pack();
            } catch (Exception e) {
                log.log(Level.WARNING, "\bError getting series list: " + e, e);
                close();
            }
        }
    }

    class CheckRemotePassword extends SwingWorker<Boolean, Boolean>
    {
        String host;
        String series;
        String password;

        public CheckRemotePassword(String inhost, String inseries, String inpassword)
        {
            host = inhost;
            series = inseries;
            password = inpassword;
        }

        @Override
        protected Boolean doInBackground() throws Exception
        {
            return PostgresqlDatabase.checkPassword(host, series, password);
        }

        @Override
        protected void done()
        {
            try {
                if (get()) {
                    errornote.setText(" ");
                    ok.setText("Download");
                } else {
                    errornote.setText("Incorrect Password");
                }
            } catch (Exception e) {
                log.log(Level.INFO, "Get series execution error " + e, e);
                errornote.setText(e.getMessage());
            }
        }
    }
}

