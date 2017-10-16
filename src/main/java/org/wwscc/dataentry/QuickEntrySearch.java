/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dataentry;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.RowFilter;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

import org.wwscc.storage.Database;
import org.wwscc.storage.Entrant;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;

import net.miginfocom.swing.MigLayout;

public class QuickEntrySearch extends JPanel implements MessageListener, ActionListener, DocumentListener
{
    private static Logger log = Logger.getLogger(QuickEntrySearch.class.getCanonicalName());
    
    JTextField entry;
    JTable cars;
    
    public QuickEntrySearch()
    {
        super(new MigLayout("", "fill, grow", "[grow 0][grow 0][fill, grow 100]"));
        
        entry = new JTextField();
        entry.getDocument().addDocumentListener(this);
        
        cars = new JTable();
        cars.setDefaultRenderer(Object.class, new EntryRenderer());
        cars.setRowHeight(25);
        cars.setIntercellSpacing(new Dimension(10, 10));
        cars.setFont(cars.getFont().deriveFont(12.0f));
        cars.setBorder(LineBorder.createGrayLineBorder());

        entry.registerKeyboardAction(this, "enter", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
        cars.registerKeyboardAction(this, "enter", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);

        
        add(new JLabel("Type digits to filter, Hit Enter to add highlighted"), "growx, wrap");
        add(entry, "growx, wrap");
        add(cars, "grow");

        Messenger.register(MT.QUICKID_SEARCH, this);
    }
    
    private void setColumnWidths(TableColumn col, int min, int pref, int max)
    {
        if (col == null) return;
        col.setMinWidth(min);
        col.setPreferredWidth(pref);
        col.setMaxWidth(max);
    }
    
    class EntryModel extends AbstractTableModel
    {
        List<Entrant> entries;
        
        public EntryModel() { entries = Database.d.getRegisteredEntrants(DataEntry.state.getCurrentEventId()); }
        @Override
        public int getRowCount() { return entries.size(); }
        @Override
        public int getColumnCount() { return 3; }
        @Override
        public Object getValueAt(int row, int c) { return entries.get(row); }
    }
    
    class EntryRenderer extends DefaultTableCellRenderer
    {
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) 
        {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            Entrant e = (Entrant)value;
            if (col == 0)
                setText(e.getName());
            else if (col == 1)
                setText(e.getClassCode());                
            else if (col == 2)
                setText(""+e.getNumber());
            return this;
        }
    }
    
    public class QuickEntryFilter extends RowFilter<EntryModel, Integer> 
    {
        String match;
        public QuickEntryFilter(String s)
        {
            super();
            if (s != null)
                match = s.trim();
            else
                match = null;
        }
        
        @Override
        public boolean include(Entry<? extends EntryModel, ? extends Integer> entry) 
        {
            if ((match == null) || (match.equals("")))
                return true;
            Entrant e = (Entrant)entry.getValue(0);
            return e.getQuickEntryId().trim().startsWith(match);
        }
    }

    
    /**
     * This takes care of the processing required to validate the quickTextField
     * input and send out a CAR_ADD event.
     */
    private void processQuickTextField()
    {
        String carText = entry.getText().trim();
        try
        {
            if (carText.length() > 0)
                Integer.parseInt(carText);
            TableRowSorter<EntryModel> sorter = new TableRowSorter<EntryModel>((EntryModel)cars.getModel());
            sorter.setRowFilter(new QuickEntryFilter(carText));
            cars.setRowSorter(sorter);
            int visible = sorter.getViewRowCount();
            if (visible > 0 && visible < 10)
                cars.setRowSelectionInterval(0, 0);
            else
                cars.clearSelection();
        }
        catch(NumberFormatException fe)
        {
            log.warning("\bThe provided registration card # is not a number ("+carText+").");
            return;
        }
    }


    @Override
    public void actionPerformed(ActionEvent e) 
    {
        if (e.getActionCommand().equals("enter")) {
            int idx = cars.getSelectedRow();
            if (idx >= 0) {
                Entrant ent = (Entrant)cars.getValueAt(idx, 0);
                Messenger.sendEvent(MT.CAR_ADD, ent.getCarId());   
            }
        }
    }
    
    
    @Override
    public void event(MT type, Object data) 
    {
        switch (type) 
        {
            case QUICKID_SEARCH:
                if (getParent() instanceof JTabbedPane)
                    ((JTabbedPane)getParent()).setSelectedComponent(this);
                entry.requestFocus();
                cars.setModel(new EntryModel());
                TableColumnModel tcm = cars.getColumnModel();
                setColumnWidths(tcm.getColumn(0), 80, 160, 320);
                setColumnWidths(tcm.getColumn(1), 40, 80, 160);
                setColumnWidths(tcm.getColumn(2), 40, 80, 160);
                break;
        }
    }

    @Override
    public void insertUpdate(DocumentEvent e) { processQuickTextField(); }
    @Override
    public void removeUpdate(DocumentEvent e) { processQuickTextField(); }
    @Override
    public void changedUpdate(DocumentEvent e) { processQuickTextField(); }
}
