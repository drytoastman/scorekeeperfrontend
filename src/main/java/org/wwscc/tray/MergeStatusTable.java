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
import java.awt.Font;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

import org.json.simple.JSONObject;
import org.wwscc.storage.MergeServer;
import org.wwscc.util.IdGenerator;
import org.wwscc.util.Resources;

public class MergeStatusTable extends JTable {

    public static final int BASE_COL_COUNT = 4;

    public MergeStatusTable(MergeServerModel model, boolean active)
    {
        super(model);
        getTableHeader().setReorderingAllowed(false);
        setFillsViewportHeight(true);
        setRowHeight(25);
        setDefaultRenderer(DecoratedMergeServer.class, new MergeServerColumnsRenderer());
        TableRowSorter<MergeServerModel> sorter = new TableRowSorter<MergeServerModel>(model);
        setRowSorter(sorter);
        sorter.setRowFilter(new RowFilter<MergeServerModel, Integer>() {
            @Override
            public boolean include(Entry<? extends MergeServerModel, ? extends Integer> entry) {
                MergeServer s = (MergeServer)entry.getValue(0);
                return !(active ^ (s.isActive() || s.isLocalHost()));
            }

        });

        ToolTipManager.sharedInstance().setInitialDelay(100);
        ToolTipManager.sharedInstance().setDismissDelay(30000);
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
                case 1:  setColumnWidthMin(tcm.getColumn(ii), 200); break;
                case 2:  setColumnWidthMin(tcm.getColumn(ii), 150); break;
                case 3:  setColumnWidthMin(tcm.getColumn(ii), 150); break;
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
            if (hoststate != o.hoststate)
                return hoststate.compareTo(o.hoststate);

            return hostname.compareTo(o.hostname);
        }
    }

    static class MergeServerColumnsRenderer extends DefaultTableCellRenderer
    {
        ImageIcon home = new ImageIcon(Resources.loadImage("home.png"));
        ImageIcon group = new ImageIcon(Resources.loadImage("group.png"));
        ImageIcon servericon = new ImageIcon(Resources.loadImage("server.png"));
        ImageIcon syncing = new ImageIcon(Resources.loadImage("syncing.png"));
        Font normal = getFont();
        Font bold = normal.deriveFont(14f);

        Color[] good  = new Color[] { Color.BLACK, Color.WHITE };
        Color[] inact = new Color[] { Color.BLACK, Color.LIGHT_GRAY };
        Color[] abad  = new Color[] { Color.WHITE, new Color(210,  60,  60) };
        Color[] ibad  = new Color[] { new Color(230, 230, 230), new Color(160, 100, 100) };
        Color mycolor = new Color(240, 240, 255);

        SimpleDateFormat dformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date epoch = new Date(1);


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

        private String textLimit(String s, int limit)
        {
            if (s == null)
                return "";
            else if (s.length() > limit)
                return s.substring(0, limit);
            else
                return s;
        }

        private boolean isMismatchedWithLocal(JTable table, int col, String testhash)
        {
            DecoratedMergeServer local = ((MergeServerModel)table.getModel()).servers.get(0);
            JSONObject lstatus = local.columns[col-BASE_COL_COUNT];
            String lhash = (String)lstatus.get("totalhash");
            return !testhash.equals(lhash);
        }

        private String getToolTip(String error, String hash)
        {
            String head = "<html>" + hash + "<br/>" + error + "<br/><br/>";
            if (error.contains("timeout expired"))
                return head + "The remote database is visible via UDP:5454 but we can't connect to the database at TCP:54329<br/>" +
                              "Are the firewall rules for 54329 in effect?";
            else if (error.contains("could not connect to server"))
                return head + "The remote database actively refused the connection.  This means we can reach the server but<br/>"+
                              "nothing is listening on port 54329";
            else if (error.contains("Unable to obtain locks"))
                return head + "With multiple active computers, we sometimes can't obtain a lock in a reasonable time<br/>"+
                              "and will then wait for 60 seconds before trying again.  You can click 'Sync All Active Now'<br/>" + 
                              "to try again now";
            else if (error.contains("Different schema"))
                return head + "The remote machine has a software that is newer (or older) than this computer and<br/>" + 
                              "cannot be merged with due to database schema differences.  The older machine should<br/>" + 
                              "be updated with the latest software.";
            else if (error.contains("No password"))
                return head + "There is no open password in the local database with which to connect to the remote<br/>" +
                              "machine.  This is a rare occurence that needs manual intervention and should be reported";
            else
                return head;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col)
        {
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(normal);
            setIcon(null);
            setText("");
            setToolTipText(null);
            if ((value == null) || !(value instanceof DecoratedMergeServer))
                return this;

            DecoratedMergeServer server = (DecoratedMergeServer)value;
            setColors(server.isActive() || server.isLocalHost(), false);
            if (server.isLocalHost())
                setBackground(mycolor);

            switch (col)
            {
                case 0:
                    if (server.isLocalHost())
                        setIcon(home);
                    else if (server.isRemote())
                        setIcon(servericon);
                    else
                        setIcon(group);
                    break;
                case 1:
                    if (server.getAddress().equals(""))
                        setText(server.getHostname());
                    else
                        setText(server.getHostname()+"/"+server.getAddress());
                    break;
                case 2:
                    // set date but also mark colors if the last check has been too long (2x the server waittime)
                    Timestamp last = server.getLastCheck();
                    setToDate(last);
                    if (!server.isLocalHost() && !last.before(epoch) && (System.currentTimeMillis() - last.getTime() > server.getWaitTime()*2000))
                        setColors(server.isActive(), true);
                    break;
                case 3:
                    if (server.isActive())
                        setToDate(server.getNextCheck());
                    break;

                default: // a series hash column
                    JSONObject seriesstatus = server.columns[col-BASE_COL_COUNT];
                    if (seriesstatus == null) {
                        return this;
                    }

                    String error = (String)seriesstatus.get("error");
                    String progress = (String)seriesstatus.get("progress");
                    String hash = (String)seriesstatus.get("totalhash");

                    if (error != null) {
                        setToolTipText(getToolTip(error, textLimit(hash, 12)));
                        setText(error);
                        setColors(server.isActive(), true);
                    } else if (progress != null) {
                        setText(progress);
                    } else {
                        if (isMismatchedWithLocal(table, col, hash))
                            setColors(server.isActive(), true);
                        setText(textLimit(hash, 12));
                        setFont(bold);
                    }
                    
                    if (seriesstatus.containsKey("syncing"))
                        setIcon(syncing);

                    break;
            }
            return this;
        }
    }
}
