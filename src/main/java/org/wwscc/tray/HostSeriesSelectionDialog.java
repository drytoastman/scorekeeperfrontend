/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.tray;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.DefaultComboBoxModel;
import javax.swing.SwingWorker;
import org.wwscc.dialogs.BaseDialog;
import org.wwscc.storage.Database;
import org.wwscc.storage.MergeServer;
import org.wwscc.storage.PostgresqlDatabase;
import net.miginfocom.swing.MigLayout;

public class HostSeriesSelectionDialog extends BaseDialog<HostSeriesSelectionDialog.HSResult>
{
    private static final Logger log = Logger.getLogger(HostSeriesSelectionDialog.class.getCanonicalName());

    GetRemoteSeries seriesgetter;
    CheckRemotePassword passchecker;
    
    static public class HSResult
    {
        public String host;
        public String series;
        public String password;
    }
    
    public HostSeriesSelectionDialog(boolean doseries)
    {
        super(new MigLayout(""), false);
        
        Set<String> hosts = new HashSet<String>();
        Set<String> ips = new HashSet<String>();

        hosts.add("scorekeeper.wwscc.org");
        for (MergeServer s : Database.d.getMergeServers())
        {
            if (s.isLocalHost())
                continue;
            if (!s.getHostname().contains("."))
                ips.add(s.getAddress().toLowerCase());
            else
                hosts.add(s.getHostname().toLowerCase());
        }
        
        List<String> shosts = new ArrayList<String>(hosts);
        List<String> sips = new ArrayList<String>(ips);
        Collections.sort(shosts);
        Collections.sort(sips);
        shosts.addAll(sips);
        
        mainPanel.add(label("Host", true), "");
        mainPanel.add(select("host", null, shosts, this), "grow, wrap");
        if (doseries) {
            mainPanel.add(label("Series", true), "");
            mainPanel.add(select("series", null, new Object[] {}, null), "grow, wrap");
            mainPanel.add(label("Password", true), "");
            mainPanel.add(entry("password", ""), "grow, wrap");
            ok.setText("Verify Password");
        }
        selects.get("host").setEditable(true);
    }
    
    @Override
    public void actionPerformed(ActionEvent e)
    {
        if (e.getSource() == selects.get("host"))
        {
            if (selects.containsKey("series"))
            {
                selects.get("series").setModel(new DefaultComboBoxModel<Object>(new String[] { "loading ..." }));
                if (currentDialog != null)
                    currentDialog.pack();
                if (seriesgetter != null)
                    seriesgetter.cancel(true);
                seriesgetter = new GetRemoteSeries(getResult().host);
                seriesgetter.execute();
            }
        }
        else if ((e.getSource() == ok) && (ok.getText().equals("Verify Password")))
        {
            if (passchecker != null)
                passchecker.cancel(true);
            String h = (String)getSelect("host");
            String s = (String)getSelect("series");
            String p = getEntryText("password");
            if ((h != null) && (s != null) && (p != null))
            {
                passchecker = new CheckRemotePassword(h, s, p);
                passchecker.execute();
            }
        }
        else
            super.actionPerformed(e);
    }
    
    @Override
    public boolean verifyData() 
    {
        HSResult s = getResult();
        return ((s.host != null) && !s.host.equals(""));
    }
    
    @Override
    public HSResult getResult() 
    { 
        HSResult ret = new HSResult();
        ret.host   = (String)getSelect("host");
        ret.series = (String)getSelect("series");
        ret.password = getEntryText("password");
        return ret;
    }
    
    class GetRemoteSeries extends SwingWorker<List<String>, String>
    {
        String host;
        public GetRemoteSeries(String fromhost)
        {
            host = fromhost;
        }
        
        @Override
        protected List<String> doInBackground() throws Exception 
        {
            List<String> series = PostgresqlDatabase.getSeriesList(host);
            series.removeAll(PostgresqlDatabase.getSeriesList(null));
            return series;
        }
        
        @Override
        protected void done() 
        {
            try {
            selects.get("series").setModel(new DefaultComboBoxModel<Object>(get().toArray()));
            if (currentDialog != null)
                currentDialog.pack();
            } catch (Exception e) {
                log.log(Level.INFO, "Get series execution error " + e, e);
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
                if (get())
                    ok.setText("Download");
            } catch (Exception e) {
                log.log(Level.INFO, "Get series execution error " + e, e);
            }
        }
    }
}

