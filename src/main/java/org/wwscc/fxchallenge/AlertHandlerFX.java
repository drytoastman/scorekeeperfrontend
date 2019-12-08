/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2019 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.fxchallenge;

import java.util.logging.Level;

import org.wwscc.util.AlertHandler;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

public class AlertHandlerFX extends AlertHandler
{
    @Override
    public void displayAlert(int level, String msg)
    {
        String title;
        AlertType fxtype;

        if (level >= Level.SEVERE.intValue())
        {
            title = "Error";
            fxtype = AlertType.ERROR;
        }
        else if (level >= Level.WARNING.intValue())
        {
            title = "Warning";
            fxtype = AlertType.WARNING;
        }
        else
        {
            title = "Note";
            fxtype = AlertType.INFORMATION;
        }

        Platform.runLater(() -> {
            Alert alert = new Alert(fxtype);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.showAndWait();
        });
    }
}