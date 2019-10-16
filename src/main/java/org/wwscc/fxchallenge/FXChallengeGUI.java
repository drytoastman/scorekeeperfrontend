/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.fxchallenge;

import java.util.logging.Logger;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.wwscc.util.Prefs;

public class FXChallengeGUI extends Application
{
    static Logger log = Logger.getLogger(FXChallengeGUI.class.getCanonicalName());

    @Override
    public void start(Stage stage) throws Exception {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/wwscc/fxchallenge/challengegui.fxml"));
            Parent root = loader.load();
            root.getStylesheets().add(getClass().getResource("/org/wwscc/fxchallenge/challengestyle.css").toString());
            Scene scene = new Scene(root, 1366, 768);
            stage.setTitle("Challenge");
            stage.setScene(scene);
            stage.show();
            stage.setOnCloseRequest(e -> Platform.exit());

            MainController con = loader.getController();
            Platform.runLater(() ->  {
                con.connectSeries(Prefs.getSeries(""));

            });
        } catch (Exception e) {
            System.out.println(e);
            throw e;
        }
    }
}
