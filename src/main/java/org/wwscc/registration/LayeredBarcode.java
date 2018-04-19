package org.wwscc.registration;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;
import javax.swing.JLabel;

import org.wwscc.barcodes.Code39;
import org.wwscc.barcodes.InvalidBarcodeException;

public class LayeredBarcode extends JComponent
{
    private Code39 barcode;
    private JLabel warning;

    public LayeredBarcode()
    {
        barcode = new Code39();
        barcode.setAlignmentX(CENTER_ALIGNMENT);

        warning = new JLabel("");
        warning.setOpaque(true);
        warning.setForeground(Color.WHITE);
        warning.setBackground(Color.RED);
        warning.setHorizontalAlignment(JLabel.CENTER);
        warning.setVisible(false);
    }

    public void clear()
    {
        barcode.clear();
    }

    public void setValue(String code, String label) throws InvalidBarcodeException
    {
        barcode.setValue(code, label);
    }

    public void setWarning(String txt)
    {
        warning.setText(txt);
        warning.setVisible(!txt.equals(""));
    }

    public Code39 getBarcode()
    {
        return barcode;
    }

    @Override
    public void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D)g;

        if (barcode.isVisible()) {
            barcode.setBounds(getBounds());
            barcode.paint(g2);
        }

        if (warning.isVisible()) {
            FontMetrics metrics = g.getFontMetrics(warning.getFont());
            warning.setBounds(0, 0, getWidth(), metrics.getHeight());
            g.translate(0, (getHeight()-metrics.getHeight())/2);
            warning.paint(g);
        }
    }
}
