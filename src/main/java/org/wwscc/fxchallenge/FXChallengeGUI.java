/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.fxchallenge;

import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.wwscc.util.Prefs;
import org.wwscc.util.AppSetup;
import org.wwscc.util.Messenger;

public class FXChallengeGUI extends Application
{
    private static Logger log = Logger.getLogger(FXChallengeGUI.class.getCanonicalName());

    @Override
    public void start(Stage stage) throws Exception {
        try {

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/wwscc/fxchallenge/challengegui.fxml"));
        Parent root = loader.load();
        //root.getStylesheets().add(getClass().getResource("/org/wwscc/fxchallenge/stylesheet.css").toString());
        Scene scene = new Scene(root, 1000, 800);
        stage.setTitle("Challenge");
        stage.setScene(scene);
        stage.show();

        FXChallengeController con = loader.getController();
        Platform.runLater(() ->  {
            String series = Prefs.getSeries("");
            if (series.trim().isEmpty())
                return;
            con.attemptSeriesChange(series);
        });

        } catch (Exception e) {
            System.out.println(e);
            throw e;
        }
    }

    /**
     * Entry point for Challenge GUI.
     * @param args unused
     */
    public static void main(String args[])
    {
        try
        {
            Messenger.setFXMode();
            AppSetup.appSetup("challengegui");
            launch(args);
        }
        catch (Throwable e)
        {
            log.log(Level.SEVERE, "\bFailed to start Challenge GUI: " + e, e);
        }
    }
}
