/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2019 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.challenge;

import java.awt.Component;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import net.miginfocom.swing.MigLayout;
import org.wwscc.dialogs.BaseDialog;
import org.wwscc.storage.Database;
import org.wwscc.storage.Dialins;
import org.wwscc.storage.Entrant;
import org.wwscc.util.NF;


public class BracketingList extends BaseDialog<List<BracketEntry>> implements ChangeListener, ItemListener
{
    BracketingListModel model;
    JSpinner spinner;
    JCheckBox ladiesCheck;
    JCheckBox openCheck;
    JCheckBox bonusCheck;

    JTable table;
    int required;

    public BracketingList(String cname, int size)
    {
        super(new MigLayout("fill"), false);

        model = new BracketingListModel();
        required = size;

        spinner = new JSpinner(new SpinnerNumberModel(size, size/2+1, size, 1));
        spinner.addChangeListener(this);

        ladiesCheck = new JCheckBox("Ladies Classes", true);
        ladiesCheck.addItemListener(this);

        openCheck = new JCheckBox("Open Classes", true);
        openCheck.addItemListener(this);

        bonusCheck = new JCheckBox("Bonus Style Dialins", true);
        bonusCheck.addItemListener(this);

        table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.setDefaultRenderer(Double.class, new D3Renderer());
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setMaxWidth(30);
        table.getColumnModel().getColumn(1).setMaxWidth(200);
        table.getColumnModel().getColumn(2).setMaxWidth(200);
        table.getColumnModel().getColumn(3).setMaxWidth(60);
        table.getColumnModel().getColumn(4).setMaxWidth(40);
        table.getColumnModel().getColumn(5).setMaxWidth(75);
        table.getColumnModel().getColumn(6).setMaxWidth(40);
        table.getColumnModel().getColumn(7).setMaxWidth(75);
        table.getColumnModel().getColumn(8).setMaxWidth(75);

        mainPanel.add(new JLabel("Number of Drivers"), "split");
        mainPanel.add(spinner, "gapbottom 4, wrap");

        mainPanel.add(ladiesCheck, "wrap");
        mainPanel.add(openCheck, "wrap");
        mainPanel.add(bonusCheck, "gapbottom 4, wrap");

        mainPanel.add(new JLabel("Click on column header to sort"), "center, wrap");
        mainPanel.add(new JScrollPane(table), "width 500, height 450, grow");
    }

    @Override
    public boolean verifyData()
    {
        long size = model.getSelectedCount();
        if (required != size)
        {
            errorMessage = "Must select " + required + " drivers, you've selected " + size;
            return false;
        }
        return true;
    }

    @Override
    public List<BracketEntry> getResult()
    {
        if (!valid)
            return null;

        List<BracketEntry> ret = model.getSelectedValues();
        // sort by their net times
        Collections.sort(ret, new Comparator<BracketEntry>() {
            public int compare(BracketEntry o1, BracketEntry o2) {
                return Double.compare(model.dialins.getNet(o1.entrant.getCarId()), model.dialins.getNet(o2.entrant.getCarId()));
            }
        });

        return ret;
    }

    @Override
    public void stateChanged(ChangeEvent e)
    {
        required = ((Number)spinner.getModel().getValue()).intValue();
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        model.reload(openCheck.isSelected(), ladiesCheck.isSelected(), bonusCheck.isSelected());
    }
}

class D3Renderer extends DefaultTableCellRenderer
{
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                          boolean isSelected, boolean hasFocus, int row, int column)
    {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        if (value instanceof Double)
            setText(NF.format((Double)value));
        else
            setText(""+value);
        return this;
    }
}


class BracketingListModel extends AbstractTableModel
{
    List<Store> data;
    Dialins dialins;

    final static class Store
    {
        boolean selected;
        Entrant entrant;
        int netposition;
        int diffposition;
        double nettime;
        double dialin;
        double classdiff;
    }

    public BracketingListModel()
    {
        data = new ArrayList<Store>();
        reload(true, true, true);
    }

    public void reload(boolean useOpen, boolean useLadies, boolean bonusStyle)
    {
        Map<UUID, Boolean> currentSelections = new HashMap<UUID, Boolean>();
        for (Store s : data)
            currentSelections.put(s.entrant.getCarId(), s.selected);

        Map<UUID, Entrant> entrants = new HashMap<UUID, Entrant>();

        for (Entrant e : Database.d.getEntrantsByEvent(ChallengeGUI.state.getCurrentEventId()))
        {
            if ((useLadies && (e.getClassCode().startsWith("L"))) ||
                (useOpen && (!e.getClassCode().startsWith("L"))))
                entrants.put(e.getCarId(), e);
        }

        data = new ArrayList<Store>();
        dialins = Database.d.loadDialins(ChallengeGUI.state.getCurrentEventId());
        List<UUID> nets = dialins.getNetOrder();
        List<UUID> diffs = dialins.getDiffOrder();
        int pos = 1;
        for (UUID id : nets)
        {
            Store s = new Store();
            if (!entrants.containsKey(id))
                continue;
            s.selected = currentSelections.containsKey(id) ? currentSelections.get(id) : false;
            s.entrant = entrants.get(id);
            s.netposition = pos;
            s.diffposition = diffs.indexOf(id) + 1;
            s.nettime = dialins.getNet(id);
            s.dialin = dialins.getDial(id, bonusStyle);
            s.classdiff = dialins.getDiff(id);
            data.add(s);
            pos++;
        }

        fireTableDataChanged();
    }

    public long getSelectedCount()
    {
        return data.stream().filter(s -> s.selected).count();
    }

    public List<BracketEntry> getSelectedValues()
    {
        return data.stream().filter(s -> s.selected).map(s -> new BracketEntry(null, s.entrant, s.dialin)).collect(Collectors.toList());
    }


    @Override
    public int getRowCount()
    {
        return data.size();
    }

    @Override
    public int getColumnCount()
    {
        return 9;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex)
    {
        return (columnIndex == 0);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex)
    {
        switch (columnIndex)
        {
            case 0: return Boolean.class;
            case 1: return String.class;
            case 2: return String.class;
            case 3: return String.class;
            case 4: return Integer.class;
            case 5: return Double.class;
            case 6: return Integer.class;
            case 7: return Double.class;
            case 8: return Double.class;
        }
        return Object.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex)
    {
        Store s = data.get(rowIndex);
        switch (columnIndex)
        {
            case 0: return s.selected;
            case 1: return s.entrant.getFirstName();
            case 2: return s.entrant.getLastName();
            case 3: return s.entrant.getClassCode();
            case 4: return s.netposition;
            case 5: return s.nettime;
            case 6: return s.diffposition;
            case 7: return s.classdiff;
            case 8: return s.dialin;
        }
        return null;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex)
    {
        if (columnIndex != 0) return;
        data.get(rowIndex).selected = (boolean)aValue;
        fireTableCellUpdated(rowIndex, columnIndex);
    }

    @Override
    public String getColumnName(int col)
    {
        switch (col)
        {
            case 0: return "";
            case 1: return "First";
            case 2: return "Last";
            case 3: return "Class";
            case 4: return "N#";
            case 5: return "Net";
            case 6: return "D#";
            case 7: return "Diff";
            case 8: return "Dialin";
            default: return "ERROR";
        }
    }
}
