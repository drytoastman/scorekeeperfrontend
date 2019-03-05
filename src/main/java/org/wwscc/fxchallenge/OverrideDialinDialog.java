/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2019 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.fxchallenge;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.GridPane;
import javafx.util.Pair;

public class OverrideDialinDialog extends Dialog<Pair<Double, Double>>
{
    public OverrideDialinDialog(double topdialin, double botdialin)
    {
        TextFormatter<Double> topdata = new StagingCells.TimeFormatter();
        TextField top = new TextField();
        top.setTextFormatter(topdata);
        topdata.setValue(topdialin);

        TextFormatter<Double> botdata = new StagingCells.TimeFormatter();
        TextField bot = new TextField();
        bot.setTextFormatter(botdata);
        botdata.setValue(botdialin);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Top"), 0, 0);
        grid.add(top, 1, 0);
        grid.add(new Label("Bottom"), 0, 1);
        grid.add(bot, 1, 1);

        setTitle("Override Dialins");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setContent(grid);
        setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return new Pair<>(topdata.getValue(), botdata.getValue());
            }
            return null;
        });
    }
}