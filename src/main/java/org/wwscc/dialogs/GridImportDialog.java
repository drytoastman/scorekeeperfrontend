/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dialogs;

import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import org.wwscc.storage.Entrant;

import net.miginfocom.swing.MigLayout;

public class GridImportDialog extends BaseDialog<Map<Integer, List<UUID>>>
{
    public static class GridEntry
    {
        public int group;
        public int grid;
        public Entrant entrant;
        public GridEntry(int group, int grid, Entrant entrant)
        {
            this.group = group;
            this.grid = grid;
            this.entrant = entrant;
        }
    }

    class NameRenderer extends DefaultTableCellRenderer
    {
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
        {
            JLabel l = (JLabel)super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof Entrant) {
                Entrant e = (Entrant)value;
                l.setText(String.format("%4s - %s", e.getClassCode(), e.getName()));
            }
            return l;
        }
    }

    Map<String, Map<Integer, DefaultTableModel>> models;
    JTable first, second;
    int course;

    /**
     * Create the dialog.
     * @param d the driver data to source initially
     */
    public GridImportDialog(Map<String, List<GridEntry>> entries, int initialcourse)
    {
        super(new MigLayout("fill, w 400, h 600, gap 2, ins 0", "", "[grow 0][grow 0]10[grow 100]"), true);

        models = new HashMap<String, Map<Integer, DefaultTableModel>>();
        first  = table();
        second = table();
        course = initialcourse;

        for (String key : entries.keySet()) {
            Map<Integer, DefaultTableModel> outer = new HashMap<Integer, DefaultTableModel>();
            models.put(key, outer);

            for (GridEntry e : entries.get(key)) {
                if (!outer.containsKey(e.group)) {
                    outer.put(e.group, new DefaultTableModel(new String[] { "Left", "Right" }, 2));
                }

                int row = (e.grid-1)/2;
                int col = ((e.grid % 2)==1) ? 0 : 1;
                DefaultTableModel model = outer.get(e.group);
                for (int ii = model.getRowCount(); ii <= row; ii++) {
                    model.addRow(new Object[] { null, null });
                }
                model.setValueAt(e.entrant, row, col);
            }
        }

        mainPanel.add(label("RunGroup", true), "al right");
        mainPanel.add(select("rungroup", 1, models.get("Number").keySet().stream().filter(i -> i < 100).sorted().collect(Collectors.toList()), e -> reload()), "growx");
        mainPanel.add(label("Overwrite Current Order", true), "al right");
        mainPanel.add(checkbox("overwrite", false), "wrap");

        mainPanel.add(label("Order", true), "al right");
        mainPanel.add(select("order", "Number", new String[] { "Number", "Position" }, e -> reload()), "growx");
        mainPanel.add(label("Load Both Courses", true), "al right");
        mainPanel.add(checkbox("bothcourses", false), "wrap");

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

        mainPanel.add(scroll, "spanx 4, grow 100");
        reload();
    }

    private void reload()
    {
        int group = (int) getSelect("rungroup");
        String order = (String) getSelect("order");
        first.setModel(models.get(order).get(group));
        second.setModel(models.get(order).get(group+100));
        this.getLayout().layoutContainer(this);
    }

    private JTable table()
    {
        JTable ret = new JTable();
        ret.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        ret.setColumnSelectionAllowed(false);
        ret.setDefaultRenderer(Object.class, new NameRenderer());
        ret.setBorder(LineBorder.createGrayLineBorder());
        return ret;
    }

    /**
     * Called after OK to verify data before closing.
     */
    @Override
    public boolean verifyData()
    {
        return (first.getSelectedRowCount() > 0) || (second.getSelectedRowCount() > 0);
    }


    /**
     * Called after OK is pressed and data is verified and before the dialog is closed.
     */
    @Override
    public Map<Integer, List<UUID>> getResult()
    {
        if (!valid)
            return null;

        Map<Integer, List<UUID>> ret = new HashMap<>();

        int courses[] = new int[] { course };
        if (isChecked("bothcourses")) {
            courses = new int[] { 1, 2 };
        }

        for (int c : courses) {
            ArrayList<UUID> ids = new ArrayList<>();
            ret.put(c, ids);
            int i1 = (c == 1) ? 0 : 1;
            int i2 = (c == 1) ? 1 : 0;
            extractIds(first,  i1, ids);
            extractIds(second, i1, ids);
            extractIds(first,  i2, ids);
            extractIds(second, i2, ids);
        }

        return ret;
    }

    private void extractIds(JTable tbl, int col, ArrayList<UUID> ids)
    {
        for (int ii :  tbl.getSelectedRows()) {
            Entrant e = (Entrant)tbl.getValueAt(ii, col);
            if (e != null) {
                ids.add(e.getCarId());
            }
        }
    }

    public boolean doOverwrite()
    {
        return isChecked("overwrite");
    }
}

