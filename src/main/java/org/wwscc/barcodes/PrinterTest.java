package org.wwscc.barcodes;

import javax.print.DocFlavor;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.OrientationRequested;

public class PrinterTest
{
    public static void print(PrintService ps, Code39 barcode) throws PrintException
    {
        PrintRequestAttributeSet attr = new HashPrintRequestAttributeSet();
        attr.add(new Copies(1));
        attr.add((Media)ps.getDefaultAttributeValue(Media.class)); // set to default paper from printer
        attr.add(OrientationRequested.LANDSCAPE);

        SimpleDoc doc = new SimpleDoc(barcode, DocFlavor.SERVICE_FORMATTED.PRINTABLE, null);
        ps.createPrintJob().print(doc, attr);
    }

    public static void main(String args[]) throws InvalidBarcodeException, PrintException
    {
        Code39 barcode = new Code39();
        barcode.setValue("123456", "123456 - Somename");

        HashPrintRequestAttributeSet aset = new HashPrintRequestAttributeSet();
        aset.add(new Copies(2)); // silly request but cuts out fax, xps, etc.
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(DocFlavor.SERVICE_FORMATTED.PRINTABLE, aset);
        for (PrintService ps : printServices) {
            if (ps.getName().contains("PT-2730")) {
                print(ps, barcode);
            }
        }

    }
}
