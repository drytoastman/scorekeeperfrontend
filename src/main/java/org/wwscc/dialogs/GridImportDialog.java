/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dialogs;

import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;

import org.wwscc.storage.BackendDataLoader.GridEntry;
import org.wwscc.util.NF;

import net.miginfocom.swing.MigLayout;

public class GridImportDialog extends BaseDialog<Map<Integer, List<UUID>>>
{
    private static int saveGrouping = 1;
    private static int saveSort     = 0;
    private static int saveLoadTo   = 0;

    private static String[] sortTypes = new String[] { "Number", "Position" };
    private static String[] loadTypes = new String[] { "This Course", "Both Courses" };

    Map<String, Map<Integer, EntrantRowModel>> models;
    JTable first, second;
    Map<Integer,Set<UUID>> order;
    int viewedCourse;
    int loadToCourse;

    /**
     * Create the dialog.
     * @param d the driver data to source initially
     */
    public GridImportDialog(Map<String, List<GridEntry>> entries, Map<Integer,Set<UUID>> currentorder, int currentcourse)
    {
        super(new MigLayout("fill, w 500, h 600, gap 2, ins 0", "[grow 100][grow 0][grow 0]10[grow 0][grow 0][grow 100]", "[grow 0][grow 0]10[grow 100]"), true);

        models = new HashMap<String, Map<Integer, EntrantRowModel>>();
        first  = table();
        second = table();
        order  = currentorder;
        viewedCourse = currentcourse;
        loadToCourse = (saveLoadTo == 0) ? viewedCourse : 0;

        for (String key : entries.keySet()) {
            Map<Integer, EntrantRowModel> outer = new HashMap<>();
            models.put(key, outer);
            for (int ii = 0; ii < 3; ii++) {
                outer.put(ii, new EntrantRowModel());
                outer.put(100+ii, new EntrantRowModel());
            }
            for (GridEntry ge : entries.get(key)) {
                outer.get(ge.group).setEntry(ge);
            }
        }

        mainPanel.add(label("Grouping", true), "skip 1, al right");
        mainPanel.add(select("grouping",
                             saveGrouping,
                             models.get("Number").keySet().stream().filter(i -> i < 100).sorted().collect(Collectors.toList()),
                             e -> { reload(); saveGrouping = (int)getSelect("grouping"); }),
                             "growx");

        mainPanel.add(label("Clear Order First", true), "al right");
        mainPanel.add(checkbox("overwrite", false), "wrap");
        checks.get("overwrite").addActionListener(e -> { first.repaint(); second.repaint(); });

        mainPanel.add(label("Sort",    true), "skip 1, al right");
        mainPanel.add(select("sort",   sortTypes[saveSort],   sortTypes, e -> { reload(); saveSort = selects.get("sort").getSelectedIndex(); }), "growx");

        mainPanel.add(label("Load To", true), "al right");
        mainPanel.add(select("loadto", loadTypes[saveLoadTo], loadTypes, e -> {
                saveLoadTo = selects.get("loadto").getSelectedIndex();
                loadToCourse = (saveLoadTo == 0) ? viewedCourse : 0;
                first.repaint(); second.repaint();
             }),
            "growx, wrap");


        JPanel toscroll = new JPanel(new MigLayout("fill, gap 2, ins 0", "", "5[grow 0][grow 0]10[grow 0][grow 0][grow 100]"));
        toscroll.add(label("First Drivers", true), "wrap");
        toscroll.add(first, "growx, wrap");
        toscroll.add(label("Second Drivers", true), "wrap");
        toscroll.add(second, "growx, wrap");
        JScrollPane scroll = new JScrollPane(toscroll);
        Border save = scroll.getBorder();
        scroll.getViewport().addChangeListener(e -> {
            scroll.setBorder(scroll.getVerticalScrollBar().isVisible() ?  save : null);
        });

        mainPanel.add(scroll, "spanx 6, grow 100");
        reload();
    }

    private void reload()
    {
        int grouping = (int) getSelect("grouping");
        String sort = (String) getSelect("sort");
        first.setModel(models.get(sort).get(grouping));
        second.setModel(models.get(sort).get(grouping+100));
        for (JTable t : new JTable[] { first, second }) {
            TableColumnModel tcm = t.getColumnModel();
            for (int col : new int[] { 0, EntrantRowModel.SECOND } ) {
                tcm.getColumn(col).setMaxWidth(40);
                tcm.getColumn(col+1).setMaxWidth(35);
                tcm.getColumn(col+2).setMinWidth(sort.equals("Position") ? 50 : 0);
                tcm.getColumn(col+2).setMaxWidth(sort.equals("Position") ? 50 : 0);
            }
        }
        this.getLayout().layoutContainer(this);
    }

    private JTable table()
    {
        JTable ret = new JTable();
        ret.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        ret.setColumnSelectionAllowed(false);
        ret.setDefaultRenderer(Object.class, new RenderTweaks());
        ret.setBorder(LineBorder.createGrayLineBorder());
        return ret;
    }

    @Override
    public boolean verifyData()
    {
        return (first.getSelectedRowCount() > 0) || (second.getSelectedRowCount() > 0);
    }


    @Override
    public Map<Integer, List<UUID>> getResult()
    {
        if (!valid)
            return null;

        Map<Integer, List<UUID>> ret = new HashMap<>();
        int courses[];

        if (loadToCourse == 0) {
            courses = new int[] { 1, 2 };
        } else {
            courses = new int[] { loadToCourse };
        }

        for (int c : courses) {
            ArrayList<UUID> ids = new ArrayList<>();
            ret.put(c, ids);
            int i1 = (c == 1) ? 0 : EntrantRowModel.SECOND;
            int i2 = (c == 1) ? EntrantRowModel.SECOND : 0;
            extractIds(first,  i1, ids);
            extractIds(second, i1, ids);
            extractIds(first,  i2, ids);
            extractIds(second, i2, ids);
        }

        return ret;
    }

    public boolean doOverwrite()
    {
        return isChecked("overwrite");
    }

    private boolean canAdd(GridEntry e)
    {
        return (e != null) && (e.carid != null) && (isChecked("overwrite") || !order.get(loadToCourse).contains(e.carid));
    }

    private void extractIds(JTable tbl, int col, ArrayList<UUID> ids)
    {
        for (int ii :  tbl.getSelectedRows()) {
            GridEntry ge = ((EntrantRowModel)tbl.getModel()).getEntryAt(ii, col);
            if (canAdd(ge)) {
                ids.add(ge.carid);
            }
        }
    }


    class RenderTweaks extends DefaultTableCellRenderer
    {
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col)
        {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);

            GridEntry e = ((EntrantRowModel)table.getModel()).getEntryAt(row, col);
            if (!canAdd(e)) {
                super.setForeground(Color.LIGHT_GRAY);
            } else if (isSelected) {
                super.setForeground(table.getSelectionForeground());
            } else {
                super.setForeground(table.getForeground());
            }
            if (value instanceof Double) {
                setValue(NF.format((Double)value));
            }
            if ((value instanceof Number) && (((Number)value).intValue() == 0)) {
                setValue("");
            }
            return this;
        }
    }

    class EntrantPair
    {
        GridEntry left;
        GridEntry right;
    }

    class EntrantRowModel extends AbstractTableModel
    {
        List<EntrantPair> data;
        public static final int SECOND = 4;

        public EntrantRowModel()
        {
            data = new ArrayList<>();
        }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return 8; }
        @Override
        public Object getValueAt(int row, int col)
        {
            GridEntry e = getEntryAt(row, col);
            if (e == null) return null;
            switch (col)
            {
                case 0: case 4: return e.classcode;
                case 1: case 5: return e.number;
                case 2: case 6: return e.net;
                case 3: case 7: return e.name;
            }
            return null;
        }

        public GridEntry getEntryAt(int row, int col)
        {
            EntrantPair p = data.get(row);
            GridEntry e;
            if (col >= SECOND) {
                e = p.right;
            } else {
                e = p.left;
            }
            return e;
        }

        public void setEntry(GridEntry ge)
        {
            int row = (ge.grid-1)/2;
            for (int ii = data.size(); ii <= row; ii++) {
                data.add(new EntrantPair());
            }
            if (((ge.grid % 2)==1)) {
                data.get(row).left = ge;
            } else {
                data.get(row).right = ge;
            }
        }
    }
}