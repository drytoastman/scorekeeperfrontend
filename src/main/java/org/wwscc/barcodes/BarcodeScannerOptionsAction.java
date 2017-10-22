package org.wwscc.barcodes;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;

public class BarcodeScannerOptionsAction extends AbstractAction
{
    public BarcodeScannerOptionsAction()
    {
        super("Barcode Scanner Options");
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        ScannerOptionsDialog dialog = new ScannerOptionsDialog(new ScannerConfig(Prefs.getScannerConfig()));
        if (dialog.doDialog("Scanner Config", null)) {
            Prefs.setScannerConfig(dialog.getResult().encode());
        }
        Messenger.sendEvent(MT.SCANNER_OPTIONS_CHANGED, null);
    }
}
