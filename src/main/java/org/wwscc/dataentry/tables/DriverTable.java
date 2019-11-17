/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2019 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dataentry.tables;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;
import javax.swing.DropMode;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableModelEvent;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import org.wwscc.dataentry.DataEntry;
import org.wwscc.storage.Entrant;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;


/**
 * Table showing the driver entries.  Takes two columns and is placed into the scroll panel row header
 */
public class DriverTable extends TableBase
{
    public DriverTable(EntryModel m)
    {
        super(m, new EntrantRenderer(), new DriverTransferHandler(), 0, 2);

        setDragEnabled(true);
        setDropMode(DropMode.INSERT);

        InputMap im = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "cut"); // delete is same as Ctl+X

        addMouseListener(new DriverContextMenu(this));
        getTableHeader().addMouseListener( new RowHeaderTableResizer() );

        Messenger.register(MT.EVENT_CHANGED, (e,o) -> {
            startModelColumn = DataEntry.state.usingSessions() ? 1 : 0;
            tableChanged(new TableModelEvent(getModel(), TableModelEvent.HEADER_ROW));
        });
    }

    @Override
    public void setColumnSizes(TableColumnModelEvent e)
    {
        TableColumnModel tcm = (TableColumnModel)e.getSource();
        int cc = tcm.getColumnCount();
        if (cc <= 1) return;

        setColumnWidths(tcm.getColumn(0), 40, 60, 75);
        setColumnWidths(tcm.getColumn(1), 80, 250, 400);
        doLayout();
    }
}

/**
 * Special mouse listener that lets the user adjust the width of the row table header in a scroll
 * pane which is where this static two column driver table is placed.
 */
class RowHeaderTableResizer extends MouseAdapter
{
    TableColumn column;
    int columnWidth;
    int pressedX;

    @Override
    public void mousePressed(MouseEvent e)
    {
        JTableHeader header = (JTableHeader)e.getComponent();
        TableColumnModel tcm = header.getColumnModel();
        int columnIndex = tcm.getColumnIndexAtX( e.getX() );
        Cursor cursor = header.getCursor();

        if (columnIndex == tcm.getColumnCount() - 1
        &&  cursor == Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR))
        {
            column = tcm.getColumn( columnIndex );
            columnWidth = column.getWidth();
            pressedX = e.getX();
            header.addMouseMotionListener( this );
        }
    }

    @Override
    public void mouseReleased(MouseEvent e)
    {
        JTableHeader header = (JTableHeader)e.getComponent();
        header.removeMouseMotionListener( this );
    }

    @Override
    public void mouseDragged(MouseEvent e)
    {
        int width = columnWidth - pressedX + e.getX();
        column.setPreferredWidth( width );
        JTableHeader header = (JTableHeader)e.getComponent();
        JTable table = header.getTable();
        table.setPreferredScrollableViewportSize(table.getPreferredSize());
        JScrollPane scrollPane = (JScrollPane)table.getParent().getParent();
        scrollPane.revalidate();
    }
}


/**
 * Render for both columns in the driver table, it differs its display based
 * column being 0 or 1.
 */
class EntrantRenderer extends JComponent implements TableCellRenderer
{
    private Color background;
    private Color backgroundSelect;
    private Color backgroundError;
    private Color backgroundErrorSelect;
    private boolean groupBorder;
    private String topLine;
    private String bottomLine;
    private Font topFont;
    private Font bottomFont;
    private boolean sessions;
    private int numcol;
    private int namecol;

    public EntrantRenderer()
    {
        super();
        background = new Color(240, 240, 240);
        backgroundSelect = new Color(120, 120, 120);
        backgroundError = new Color(255, 180, 180);
        backgroundErrorSelect = new Color(240, 70, 70);
        topLine = null;
        bottomLine = null;

        topFont = new Font(Font.DIALOG, Font.BOLD, 11);
        bottomFont = new Font(Font.DIALOG, Font.PLAIN, 11);

        Messenger.register(MT.EVENT_CHANGED, (e,o) -> {
            sessions = DataEntry.state.usingSessions();
            numcol   = sessions ? -1 : 0;
            namecol  = sessions ? 0 : 1;
        });
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
    {
        setBackground((isSelected) ?  backgroundSelect : background);

        if (value instanceof Entrant)
        {
            Entrant e = (Entrant)value;
            if (column == numcol) {
                topLine    = e.getClassCode();
                bottomLine = ""+e.getNumber();
            } else if (column == namecol) {
                if (sessions) {
                    topLine    = String.format("%s %s", e.getFirstName(), e.getLastName());
                    bottomLine = String.format(" #%d - %s (%s)", e.getNumber(), e.getCarDesc(), e.getClassCode());
                } else {
                    topLine    = String.format("%s %s", e.getFirstName(), e.getLastName());
                    bottomLine = String.format("%s %s", e.getCarDesc(), e.getCar().getEffectiveIndexStr());
                }
            } else {
                topLine    = "What?";
                bottomLine = null;
            }

            groupBorder = ((EntryModel)table.getModel()).isGroupingStart(row);

            if (Prefs.usePaidFlag() && !e.isPaid())
                 setBackground((isSelected) ?  backgroundErrorSelect : backgroundError);
        }
        else if (value != null)
        {
            setBackground(backgroundError);
            topLine = value.toString();
        }
        else
        {
            setBackground(backgroundError);
            topLine = "ERROR";
            bottomLine = "No data for this cell";
        }
        return this;
    }

    @Override
    public void paint(Graphics g1)
    {
        Graphics2D g = (Graphics2D)g1;

        Dimension size = getSize();
        g.setColor(getBackground());
        g.fillRect(0, 0, size.width, size.height);
        g.setColor(new Color(40,40,40));

        //FontMetrics tm = g.getFontMetrics(topFont);
        FontMetrics bm = g.getFontMetrics(bottomFont);
        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        if (topLine != null)
        {
            g.setFont(topFont);
            g.drawString(topLine, 5, size.height/2 - 2);
        }
        if (bottomLine != null)
        {
            g.setFont(bottomFont);
            g.drawString(bottomLine, 5, size.height/2 + bm.getHeight() - 2);
        }
        if (groupBorder)
        {
            g.setColor(Color.GREEN);
            g.setStroke(new BasicStroke(3));
            g.drawLine(0, 0, size.width, 0);
        }
    }

    // The following methods override the defaults for performance reasons
    @Override
    public void validate() {}
    @Override
    public void revalidate() {}
    @Override
    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {}
    @Override
    public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}
}



/**
 * Class to enable special DnD handling in our JTable.
 * Allow only cut drag movements (insertions) in the driver columns
 */
class DriverTransferHandler extends TransferHandler
{
    private static Logger log = Logger.getLogger(DriverTransferHandler.class.getCanonicalName());
    private static DataFlavor entrantFlavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + "; class=org.wwscc.storage.Entrant", "EntrantData");
    private int[] rowsidx = null;
    private boolean isCut = false;

    @Override
    public int getSourceActions(JComponent c)
    {
        return COPY_OR_MOVE;
    }

    @Override
    public void exportAsDrag(JComponent comp, InputEvent e, int action)
    {
        isCut = false;
        super.exportAsDrag(comp, e, action);
    }

    @Override
    public void exportToClipboard(JComponent comp, Clipboard cb, int action)
    {
        isCut = true;
        super.exportToClipboard(comp, cb, action);
    }

    /******* Export Side *******/

    /* Create data from the selected rows */
    @Override
    protected Transferable createTransferable(JComponent c)
    {
        JTable table = (JTable)c;
        rowsidx = table.getSelectedRows();

        log.info("create transfer " + Arrays.toString(rowsidx));
        Entrant store[] = new Entrant[rowsidx.length];
        for (int ii = 0; ii < rowsidx.length; ii++)
            store[ii] = (Entrant)table.getValueAt(rowsidx[ii], 0);

        boolean classcode = false, name = false;
        for (int col : table.getSelectedColumns()) {
            if (col == 0) classcode = true;
            if (col == 1) name = true;
        }
        return new EntrantTransfer(store, classcode, name);
    }

    class EntrantTransfer implements Transferable, ClipboardOwner
    {
        Entrant data[];
        boolean exportcode;
        boolean exportname;
        public EntrantTransfer(Entrant data[], boolean exportcode, boolean exportname) {
            this.data = data;
            this.exportcode = exportcode;
            this.exportname = exportname;
        }
        @SuppressWarnings("deprecation")
        @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] { entrantFlavor, DataFlavor.plainTextFlavor }; }
        @Override public boolean isDataFlavorSupported(DataFlavor testflavor) { return testflavor.equals(entrantFlavor); }
        @Override public void lostOwnership(Clipboard clipboard, Transferable contents) {}
        @Override
        public Object getTransferData(DataFlavor outflavor) throws UnsupportedFlavorException, IOException {
            if (!outflavor.equals(entrantFlavor)) {
                StringBuilder b = new StringBuilder();
                for (Entrant e : data) {
                    if (exportcode)
                        b.append(e.getClassCode() + " " + e.getNumber());
                    if (exportcode && exportname)
                        b.append(" - ");
                    if (exportname)
                        b.append(e.getName() + " " + e.getCarDesc() + " " + e.getCar().getEffectiveIndexStr());
                    b.append("\n");
                }
                return b.toString();
            }
            return data;
        }
    }


    @Override
    protected void exportDone(JComponent c, Transferable data, int action)
    {
        if ((rowsidx == null)|| (rowsidx.length == 0))
            return;
        if (action == COPY)
            return;

        /* use isCut to determine if we cut or were just dragging columns around */
        if (isCut)
        {
            DriverTable t = (DriverTable)c;
            log.fine("cut drivers " + Arrays.toString(rowsidx));
            ((EntryModel)t.getModel()).removeRows(rowsidx);
        }

        rowsidx = null;
    }

    /******* Import Side *******/

    /**
     * Called to allow drop operations, allow driver drag full range of rows
     * except for last (Add driver box).
     */
    @Override
    public boolean canImport(TransferHandler.TransferSupport support)
    {
        JTable.DropLocation dl = (JTable.DropLocation)support.getDropLocation();
        JTable target = (JTable)support.getComponent();

        if (dl.getRow() > target.getRowCount()) return false;
        return true;
    }


    /**
     * Called for drop and paste operations
     */
    @Override
    public boolean importData(TransferHandler.TransferSupport support)
    {
        try
        {
            JTable target = (JTable)support.getComponent();
            EntryModel model = (EntryModel)target.getModel();

            if (support.isDrop())
            {
                JTable.DropLocation dl = (JTable.DropLocation)support.getDropLocation();
                model.moveRow(rowsidx[0], rowsidx[rowsidx.length-1], dl.getRow());
                target.clearSelection();
            }

            return true;
        }
        catch (Exception e) { log.warning("\bGeneral error during driver drag:" + e); }

        return false;
    }
}
