/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.tray;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
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
        public MergeServer host;
        public String series;
        public String password;
    }
    
    public HostSeriesSelectionDialog(boolean doseries)
    {
        super(new MigLayout("", "[][fill, 300]", "[fill,24]"), false);

        List<MergeServer> data = Database.d.getMergeServers();
        ListIterator<MergeServer> iter = data.listIterator();
        while (iter.hasNext()) {
            if (iter.next().isLocalHost())
                iter.remove();
        }
        
        mainPanel.add(label("Host", true), "");
        mainPanel.add(select("host", null, data, this), "grow, wrap");
        if (doseries) {
            mainPanel.add(label("Series", true), "");
            mainPanel.add(select("series", null, new Object[] {}, null), "grow, wrap");
            mainPanel.add(label("Password", true), "");
            mainPanel.add(entry("password", ""), "grow, wrap");
            ok.setText("Verify Password");
        }
        selects.get("host").setRenderer(new MergeServerRenderer());
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
            MergeServer h = (MergeServer)getSelect("host");
            String s = (String)getSelect("series");
            String p = getEntryText("password");
            if ((h != null) && (s != null) && (p != null))
            {
                passchecker = new CheckRemotePassword(h.getConnectEndpoint(), s, p);
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
        return ((s.host != null) && !s.host.getHostname().equals(""));
    }
    
    @Override
    public HSResult getResult() 
    { 
        HSResult ret = new HSResult();
        ret.host   = (MergeServer)getSelect("host");
        ret.series = (String)getSelect("series");
        ret.password = getEntryText("password");
        return ret;
    }
    
    class MergeServerRenderer extends DefaultListCellRenderer
    {
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) 
        {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof MergeServer) {
                MergeServer m = (MergeServer)value;
                if (m.getAddress().equals(""))
                    setText(m.getHostname());
                else
                    setText(m.getHostname() + "/" + m.getAddress());
            }
            return this;
        }
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
            List<String> series = PostgresqlDatabase.getSeriesList(server.getConnectEndpoint());
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

