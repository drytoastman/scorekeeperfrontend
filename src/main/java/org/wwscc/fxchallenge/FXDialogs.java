/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.fxchallenge;

import java.util.List;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TextInputDialog;

public class FXDialogs
{
    public static <T> ChoiceDialog<T> choice(String title, String header, String content, List<T> choices)
    {
        ChoiceDialog<T> dialog = new ChoiceDialog<T>(null, choices);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(content);
        dialog.setGraphic(null);
        return dialog;
    }

    public static TextInputDialog input(String title, String header, String content)
    {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(content);
        dialog.setGraphic(null);
        return dialog;
    }

    public static Alert confirm(String title, String header, String content)
    {
        Alert dialog = new Alert(AlertType.CONFIRMATION);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(content);
        dialog.setGraphic(null);
        return dialog;
    }
}
