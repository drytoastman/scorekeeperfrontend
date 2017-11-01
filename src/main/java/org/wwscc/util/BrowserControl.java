/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.util;

import java.awt.Desktop;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker.State;
import javafx.print.PrinterJob;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

public class BrowserControl
{
    private static Logger log = Logger.getLogger("org.wwscc.util.Web");

    public static void openAuditReport(ApplicationState state, String order)
    {
        openResults(state, String.format("audit?order=%s", order));
    }

    public static void openResults(ApplicationState state, String selection)
    {
        openURL(String.format("http://127.0.0.1/results/%s/event/%s/%s", state.getCurrentSeries(), state.getCurrentEventId(), selection));
    }

    public static void openAdmin(ApplicationState state, String selection)
    {
        openURL(String.format("http://127.0.0.1/admin/%s/event/%s/%s", state.getCurrentSeries(), state.getCurrentEventId(), selection));
    }

    public static void openURL(String url)
    {
        // If we get here from the FX Thread, we need to push it back to the event thread
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                try{
                    log.info("openURL request: " + url);
                    Desktop.getDesktop().browse(new URI(url));
                } catch (Exception ex) {
                    log.severe("\bCouldn't open default web browser:" + ex);
                }
            }
        });
    }

    public static void printGroupResults(ApplicationState state, int[] groups)
    {
        if (groups.length == 0)
            return;

        String g = new String();
        int ii;
        for (ii = 0; ii < groups.length-1; ii++)
            g += groups[ii]+",";
        g += groups[ii];

        printURL(String.format("http://127.0.0.1/results/%s/event/%s/bygroup?course=%s&list=%s", state.getCurrentSeries(), state.getCurrentEventId(), state.getCurrentCourse(), g));
    }


    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void printURL(String url)
    {
        Platform.runLater(new Runnable() {
            @Override public void run() {
                try {
                    WebView view = new WebView();
                    WebEngine engine = view.getEngine();
                    engine.getLoadWorker().stateProperty().addListener((ChangeListener) (obsValue, oldState, newState) -> {
                       if (newState == State.SUCCEEDED) {
                           Document doc = engine.getDocument();
                           // don't know why, but this was necessary to get the font-size smaller for results, prints fine under Chrome but not WebView
                           Element extrastyle = doc.createElement("style");
                           extrastyle.appendChild(doc.createTextNode(".container-fluid { font-size: 0.65rem !important; } table td span { white-space: nowrap; }"));
                           doc.getDocumentElement().getElementsByTagName("head").item(0).appendChild(extrastyle);

                           PrinterJob job = PrinterJob.createPrinterJob();
                           if (job == null) {
                               log.warning("Unable to create a print job.  Opening in a browser instead.");
                               openURL(url);
                               return;
                           }

                           if (job.showPrintDialog(null)) {
                               engine.print(job);
                               job.endJob();
                           }
                       }
                    });
                    engine.load(url);
                } catch (Exception e) {
                    log.log(Level.SEVERE, "\bCould not print:" + e, e);
                }
            }
        });
    }
}

