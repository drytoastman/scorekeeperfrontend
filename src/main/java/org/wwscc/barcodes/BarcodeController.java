/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.barcodes;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;

import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;
import org.wwscc.util.SerialPortUtil;

public class BarcodeController extends JMenu implements ActionListener
{
    private static final String OFF = "Off";
    public static final Color RED_MENU = new Color(200, 0, 0);

    Map<String, JRadioButtonMenuItem> radios;
    JMenuItem keyboardoptions, serialoptions;
    WatcherBase watcher;
    JSeparator separator;
    String active;

    public BarcodeController()
    {
        super("Scanner");
        radios          = new HashMap<String, JRadioButtonMenuItem>();
        separator       = new JSeparator();
        keyboardoptions = new JMenuItem(new BarcodeScannerOptionsAction(KeyboardBarcodeWatcher.TYPE));
        serialoptions   = new JMenuItem(new BarcodeScannerOptionsAction(SerialPortBarcodeWatcher.TYPE));

        add(makeRadio(OFF));
        add(makeRadio(KeyboardBarcodeWatcher.TYPE));
        add(makeRadio(SerialPortBarcodeWatcher.TYPE));
        add(separator);
        add(keyboardoptions);
        add(serialoptions);

        String prev = Prefs.getScannerInput();
        if (!prev.equals(KeyboardBarcodeWatcher.TYPE)) // don't auto connect serial and noval is off
            prev = OFF;
        switchBarcodeMode(prev);
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        switchBarcodeMode(e.getActionCommand());
    }

    private JRadioButtonMenuItem makeRadio(String type)
    {
        JRadioButtonMenuItem bm = new JRadioButtonMenuItem(type);
        bm.addActionListener(this);
        radios.put(type, bm);
        return bm;
    }

    private void setRadio(String type)
    {
        for (JRadioButtonMenuItem b : radios.values())
            b.setSelected(false);
        radios.get(type).setSelected(true);
        active = type;
        Prefs.setScannerInput(type);
    }

    private void switchBarcodeMode(String type)
    {
        String newCommPort = "";
        if (type == SerialPortBarcodeWatcher.TYPE) {
            if ((newCommPort = SerialPortUtil.userPortSelection()) == null) {
                setRadio(active); // return selection to previous
                return;
            }
        }

        if (watcher != null)
            watcher.stop();

        switch (type)
        {
            case KeyboardBarcodeWatcher.TYPE:
                watcher = new KeyboardBarcodeWatcher();
                watcher.start();
                break;
            case SerialPortBarcodeWatcher.TYPE:
                watcher = new SerialPortBarcodeWatcher(newCommPort);
                watcher.start();
                break;
        }

        keyboardoptions.setVisible(type.equals(KeyboardBarcodeWatcher.TYPE));
        serialoptions.setVisible(type.equals(SerialPortBarcodeWatcher.TYPE));
        separator.setVisible(keyboardoptions.isVisible() || serialoptions.isVisible());
        setRadio(type);

        setForeground((type.equals(OFF) ? RED_MENU : Color.BLACK));
        repaint();
    }

    class BarcodeScannerOptionsAction extends AbstractAction
    {
        String type;
        public BarcodeScannerOptionsAction(String type)
        {
            super(type+" Options");
            this.type = type;
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            // set to defaults and then merge in preferences if available
            ScannerConfig current = ScannerConfig.defaultFor(type);
            current.decode(Prefs.getScannerConfig(type));

            // present dialog and save/notify on change
            ScannerOptionsDialog dialog = new ScannerOptionsDialog(current);
            if (dialog.doDialog("Scanner Config", null)) {
                Prefs.setScannerConfig(type, dialog.getResult().encode());
                Messenger.sendEvent(MT.SCANNER_OPTIONS_CHANGED, null);
            }
        }
    }
}
