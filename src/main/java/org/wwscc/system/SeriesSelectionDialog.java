/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import org.wwscc.dialogs.BaseDialog;
import org.wwscc.util.TextChangeTrigger;

import net.miginfocom.swing.MigLayout;

public class SeriesSelectionDialog extends BaseDialog<SeriesSelectionDialog.HSResult>
{
    private static final Logger log = Logger.getLogger(SeriesSelectionDialog.class.getCanonicalName());

    GetRemoteSeries seriesgetter;
    CheckRemotePassword passchecker;
    JLabel errornote;

    ContainerMonitor cmonitor;
    String host;

    static public class HSResult
    {
        public String series;
        public String password;
    }

    public SeriesSelectionDialog(ContainerMonitor cmonitor, String host)
    {
        super(new MigLayout("", "[][fill, 300]", "[fill]"), false);
        this.host = host;
        this.cmonitor = cmonitor;

        mainPanel.add(label("Series", true), "");
        mainPanel.add(select("series", null, new Object[] {}, this), "grow, wrap");
        mainPanel.add(label("Password", true), "");
        mainPanel.add(entry("password", ""), "grow, wrap");
        ok.setText("Verify Password");
        errornote = new JLabel(" ", SwingConstants.CENTER);
        errornote.setForeground(Color.RED);
        mainPanel.add(errornote, "spanx 2, center, grow, wrap");

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
                seriesgetter = new GetRemoteSeries();
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
            if ((host != null) && (s != null) && (p != null))
            {
                passchecker = new CheckRemotePassword(s, p);
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
        @Override
        protected List<String> doInBackground() throws Exception
        {
            return Arrays.asList(cmonitor.syncCommand("remotelist", host).split(","));
        }

        @Override
        protected void done()
        {
            try {
                selects.get("series").setModel(new DefaultComboBoxModel<Object>(get().toArray()));
                if (currentDialog != null)
                    currentDialog.pack();
            } catch (Exception e) {
                log.log(Level.WARNING, "\bError getting series list: " + e.getMessage(), e);
                close();
            }
        }
    }

    class CheckRemotePassword extends SwingWorker<Boolean, Boolean>
    {
        String series;
        String password;

        public CheckRemotePassword(String series, String password)
        {
            this.series   = series;
            this.password = password;
        }

        @Override
        protected Boolean doInBackground() throws Exception
        {
            return cmonitor.syncCommand("remotepassword", host, series, password).equals("accepted");
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
                log.log(Level.INFO, "Check password error: " + e, e);
                errornote.setText(e.getMessage());
            }
        }
    }
}

