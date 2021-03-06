/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import org.wwscc.storage.MergeServer;
import org.wwscc.system.MergeStatusTable.DecoratedMergeServer;

public class MergeServerModel extends AbstractTableModel
{
    List<DecoratedMergeServer> servers;
    List<String> series;

    public MergeServerModel()
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
                if (s.getServerId().equals(MergeServer.LOCALHOST)) {
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
        if (col < series.size() + MergeStatusTable.BASE_COL_COUNT)
            return series.get(col-MergeStatusTable.BASE_COL_COUNT);
        return "";
    }

    @Override
    public int getRowCount()                    { return servers.size(); }
    @Override
    public int getColumnCount()                 { return MergeStatusTable.BASE_COL_COUNT + series.size(); }
    @Override
    public Class<?> getColumnClass(int col)     { return DecoratedMergeServer.class; }
    @Override
    public Object getValueAt(int row, int col)  { return servers.get(row); }
}