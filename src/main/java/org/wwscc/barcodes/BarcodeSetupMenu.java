package org.wwscc.barcodes;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JMenu;

import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;

public class BarcodeSetupMenu extends JMenu
{
    public BarcodeSetupMenu()
    {
        super("Barcode Scanner");
        add(new BarcodeScannerOptionsAction());
    }

    static class BarcodeScannerOptionsAction extends AbstractAction
    {
        public BarcodeScannerOptionsAction()
        {
            super("Barcode Scanner Options");
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            ScannerOptionsDialog dialog = new ScannerOptionsDialog(new ScannerConfig(Prefs.getScannerConfig("serial")));
            if (dialog.doDialog("Scanner Config", null)) {
                Prefs.setScannerConfig("serial", dialog.getResult().encode());
            }
            Messenger.sendEvent(MT.SCANNER_OPTIONS_CHANGED, null);
        }
    }
}
