/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.tray;

import java.awt.Color;
import java.awt.Component;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import org.json.simple.JSONObject;
import org.wwscc.storage.MergeServer;
import org.wwscc.util.IdGenerator;
import org.wwscc.util.Resources;

public class MergeStatusTable extends JTable {

    public static final int BASE_COL_COUNT = 4;
    
    public MergeStatusTable()
    {
        super(new ServerModel());
        
        getTableHeader().setReorderingAllowed(false);
        setFillsViewportHeight(true);
        setRowHeight(20);
        setDefaultRenderer(DecoratedMergeServer.class, new MergeServerColumnsRenderer());     
    }
    
    public void setData(List<MergeServer> data)
    {
        ServerModel model = (ServerModel)getModel();
        model.setData(data);
    }
    
    @Override
    public boolean getScrollableTracksViewportWidth()
    {
        return getPreferredSize().width < getParent().getWidth();
    }
    
    @Override
    public void tableChanged(TableModelEvent e)
    {
        super.tableChanged(e);
        if (e == null || e.getFirstRow() == TableModelEvent.HEADER_ROW) {
            setColumnWidths();
        }
    }
    
    public void setColumnWidths()
    {
        TableColumnModel tcm = getColumnModel();
        for (int ii = 0; ii < tcm.getColumnCount(); ii++) 
        {
            switch (ii)
            {
                case 0:  setColumnWidthMin(tcm.getColumn(ii),  25); break;
                case 1:  setColumnWidthMin(tcm.getColumn(ii), 130); break;
                case 2:  setColumnWidthMin(tcm.getColumn(ii), 130); break;
                case 3:  setColumnWidthMin(tcm.getColumn(ii), 130); break;
                default: setColumnWidthMax(tcm.getColumn(ii), 200); break;
            }
        }
    }
    
    private void setColumnWidthMin(TableColumn col, int pref)
    {
        col.setMinWidth(pref-(int)(pref*0.05));
        col.setPreferredWidth(pref);
        col.setMaxWidth(pref+(int)(pref*0.15));
    }

    private void setColumnWidthMax(TableColumn col, int pref)
    {
        col.setMinWidth(pref-(int)(pref*0.2));
        col.setPreferredWidth(pref);
        col.setMaxWidth(pref+(int)(pref*1.25));
    }
    
    static class DecoratedMergeServer extends MergeServer implements Comparable<DecoratedMergeServer>
    {
        JSONObject[] columns;
        public DecoratedMergeServer(MergeServer m, List<String> order)
        {
            super(m);
            columns = new JSONObject[order.size()];
            for (int ii = 0; ii < order.size(); ii++)
            {
                columns[ii] = getSeriesState(order.get(ii));
            }
        }
        
        @Override
        public int compareTo(DecoratedMergeServer o) 
        {
            if (serverid.equals(IdGenerator.nullid))
                return -1;
            if (o.serverid.equals(IdGenerator.nullid))
                return 1;
            if (active != o.active)
            {
                if (active)
                    return -1;
                else
                    return 1;
            }
            
            return hostname.compareTo(o.hostname);
        }
    }
    
    static class ServerModel extends AbstractTableModel
    {
        List<DecoratedMergeServer> servers;
        List<String> series;
        
        public ServerModel()
        {
            servers = new ArrayList<DecoratedMergeServer>();
            series = new ArrayList<String>();
        }
        
        public void setData(List<MergeServer> data)
        {
            servers.clear();            
            series.clear();
            
            // Figure out columns
            if (data != null)
            {
                for (MergeServer s : data) {
                    if (s.getServerId().equals(IdGenerator.nullid)) {
                        series.addAll(s.getSeriesSet());
                        break;
                    }
                }
                Collections.sort(series);
                
                // Then create our decorated MergeServers
                for (MergeServer s : data)
                    servers.add(new DecoratedMergeServer(s, series));
                Collections.sort(servers);
            }
            
            fireTableStructureChanged();
        }
        
        @Override
        public String getColumnName(int col) {
            switch (col) {
                case 0:  return "";
                case 1:  return "Host";
                case 2:  return "Last";
                case 3:  return "Next";
            }
            if (col < series.size() + BASE_COL_COUNT)
                return series.get(col-BASE_COL_COUNT);
            return "";
        }
        
        @Override
        public int getRowCount()                    { return servers.size(); }
        @Override
        public int getColumnCount()                 { return BASE_COL_COUNT + series.size(); }
        @Override
        public Class<?> getColumnClass(int col)     { return DecoratedMergeServer.class; }
        @Override
        public Object getValueAt(int row, int col)  { return servers.get(row); }
    }
    
    static class MergeServerColumnsRenderer extends DefaultTableCellRenderer
    {
        ImageIcon home = new ImageIcon(Resources.loadImage("home.png"));
        ImageIcon syncing = new ImageIcon(Resources.loadImage("syncing.png"));
        SimpleDateFormat dformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date epoch = new Date(1);
        Color[] good  = new Color[] { Color.BLACK, Color.WHITE };
        Color[] inact = new Color[] { Color.BLACK, Color.LIGHT_GRAY };
        Color[] abad  = new Color[] { Color.BLACK, Color.RED };
        Color[] ibad  = new Color[] { Color.BLACK, new Color(200, 70, 70) };
        Color mycolor = new Color(240, 240, 255);
        
        private void setColors(boolean active, boolean warn)
        {
            Color[] use;
            if (active) {
                if (warn) use = abad;
                else      use = good;
            } else {
                if (warn) use = ibad;
                else      use = inact;
            }
            setForeground(use[0]);
            setBackground(use[1]);
        }
        
        private void setToDate(Timestamp time)
        {
            if (time.before(epoch)) setText("");
            else setText(dformat.format(time));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) 
        {
            setHorizontalAlignment(SwingConstants.CENTER);
            setIcon(null);
            setText("");
            if ((value == null) || !(value instanceof DecoratedMergeServer))
                return this;
            
            DecoratedMergeServer server = (DecoratedMergeServer)value;
            setColors(server.isActive() || server.isLocalHost(), false);

            if ((col == 0) && server.isLocalHost()){
                setIcon(home);
                return this;
            }
            
            if (server.isLocalHost())
                setBackground(mycolor);

            if (col >= BASE_COL_COUNT)
            {
                JSONObject rstatus = server.columns[col-BASE_COL_COUNT];
                if (rstatus == null) { 
                    return this;
                } else if (rstatus.containsKey("error")) {
                    setText(rstatus.get("error").toString());
                    setColors(server.isActive(), true);
                } else {
                    DecoratedMergeServer local = ((ServerModel)table.getModel()).servers.get(0);
                    JSONObject lstatus = local.columns[col-BASE_COL_COUNT];                    
                    String lh = (String)lstatus.get("totalhash");
                    String rh = (String)rstatus.get("totalhash");
                    if ((rh != null) && !rh.equals("")) {
                        setText(rh.substring(0, 12));

                        if (!lh.equals(rh))
                            setColors(server.isActive(), true);
                    }
                    if (rstatus.containsKey("syncing"))
                        setIcon(syncing);
                }
            }
            
            switch (col) 
            {
                case 0:  break;
                case 1:  
                    if (server.getAddress().equals(""))
                        setText(server.getHostname());
                    else
                        setText(server.getHostname()+"/"+server.getAddress());
                    break;
                case 2:  setToDate(server.getLastCheck()); break;
                case 3:  setToDate(server.getNextCheck()); break;
            }
            
            // Last check has been a long time away
            if ((col == 2) && !server.isLocalHost() && (System.currentTimeMillis() - server.getLastCheck().getTime() > server.getWaitTime()*2000))
                setColors(server.isActive(), true);
            
            return this;
        }
    }
}