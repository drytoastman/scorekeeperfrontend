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
        openResults(state, String.format("audit?order=%s&course=%s&group=%s", order, state.getCurrentCourse(), state.getCurrentRunGroup()));
    }

    public static void openGroupResults(ApplicationState state, String[] groups)
    {
        if (groups.length == 0) return;
        openResults(state, String.format("bygroup?course=%s&list=%s", state.getCurrentCourse(), String.join(",", groups)));
    }

    public static void printGroupResults(ApplicationState state)
    {
        printURL(String.format("http://127.0.0.1/results/%s/event/%s/bygroup?course=%s&list=%s",
                state.getCurrentSeries(), state.getCurrentEventId(), state.getCurrentCourse(), state.getCurrentRunGroup()));
    }

    public static void openResults(ApplicationState state, String selection)
    {
        openURL(String.format("http://127.0.0.1/results/%s/event/%s/%s", state.getCurrentSeries(), state.getCurrentEventId(), selection));
    }

    public static void openAdmin(ApplicationState state, String selection)
    {
        openURL(String.format("http://127.0.0.1/admin/%s/event/%s/%s", state.getCurrentSeries(), state.getCurrentEventId(), selection));
    }

    public static void openReport(ApplicationState state, String selection)
    {
        openURL(String.format("http://127.0.0.1/admin/%s/%s", state.getCurrentSeries(), selection));
    }

    public static void openURL(String url)
    {
        // If we get here from the FX Thread, we need to push it back to the event thread
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                try{
                    log.info("openURL request: " + url);
                    try {
                        Desktop.getDesktop().browse(new URI(url));
                    } catch (UnsupportedOperationException uoe ) {
                        Runtime.getRuntime().exec("xdg-open " + url);  // for other linux desktops
                    }
                } catch (Exception ex) {
                    log.severe("\bCouldn't open default web browser:" + ex);
                }
            }
        });
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void printURL(String url)
    {
        if (!Prefs.getPrintDirectly()) {
            openURL(url);
            return;
        }

        Platform.runLater(new Runnable() {
            @Override public void run() {
                try {
                    WebView view = new WebView();
                    WebEngine engine = view.getEngine();
                    engine.getLoadWorker().stateProperty().addListener((ChangeListener) (obsValue, oldState, newState) -> {
                       if (newState == State.SUCCEEDED) {
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

