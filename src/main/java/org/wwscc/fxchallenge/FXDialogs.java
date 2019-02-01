/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.fxchallenge;

import java.util.List;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.util.Pair;

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

    public static Dialog<Pair<String, Integer>> newChallengeDialog(List<String> disallowed)
    {
        Dialog<Pair<String, Integer>> dialog = new Dialog<>();

        TextField name = new TextField();
        name.setPromptText("Challenge Name");
        name.textProperty().addListener((observable, oldValue, newValue) -> {
            dialog.getDialogPane().lookupButton(ButtonType.OK).setDisable(disallowed.contains(newValue) || newValue.isEmpty());
        });

        ComboBox<Integer> size = new ComboBox<Integer>(FXCollections.observableArrayList(4, 8, 16, 32, 64));
        size.getSelectionModel().selectFirst();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Name:"), 0, 0);
        grid.add(name, 1, 0);
        grid.add(new Label("Size:"), 0, 1);
        grid.add(size, 1, 1);
        GridPane.setVgrow(size, Priority.ALWAYS);

        dialog.setTitle("New Challenge");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().lookupButton(ButtonType.OK).setDisable(true);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return new Pair<>(name.getText(), size.getValue());
            }
            return null;
        });
        return dialog;
    }
}
