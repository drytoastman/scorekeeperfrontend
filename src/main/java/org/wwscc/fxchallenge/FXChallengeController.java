/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.fxchallenge;

import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;
import org.wwscc.storage.Challenge;
import org.wwscc.storage.Database;
import org.wwscc.storage.Event;
import org.wwscc.util.ApplicationState;
import org.wwscc.util.Prefs;

import javafx.collections.FXCollections;
import javafx.concurrent.Worker.State;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseEvent;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

public class FXChallengeController implements Initializable
{
    private static final Logger log = Logger.getLogger(FXChallengeController.class.getCanonicalName());

    @FXML private Label seriesLabel;
    @FXML private Label timerLabel;
    @FXML private ComboBox<Event> eventSelect;
    @FXML private ComboBox<Challenge> challengeSelect;
    @FXML private WebView bracketView;

    private ApplicationState appState = new ApplicationState();
    private Challenge currentChallenge;
    private ContextMenu contextMenu;
    private double webx, weby;


    public FXChallengeController()
    {
        contextMenu = new ContextMenu();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources)
    {
    }

    public void quit(ActionEvent event)
    {
        System.exit(0);
    }

    public void openSeries(ActionEvent event)
    {
        ChoiceDialog<String> dialog = FXDialogs.choice("Choose Series", null, null, Database.d.getSeriesList());
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> attemptSeriesChange(name));
    }

    public void newChallenge(ActionEvent event)
    {
        List<String> names = Database.d.getChallengesForEvent(appState.getCurrentEventId()).stream().map(c -> c.getName()).collect(Collectors.toList());
        new NewChallengeDialog(names).showAndWait().ifPresent(pair -> {
            try {
                currentChallenge = Database.d.newChallenge(appState.getCurrentEventId(), pair.getKey(), pair.getValue());
                eventChanged();
                challengeSelect.getSelectionModel().selectLast();
            } catch (SQLException sqle) {
                log.log(Level.WARNING, "\bFailed to create challenge: " + sqle, sqle);
            }
        });
    }

    public void editChallenge(ActionEvent event)
    {
        FXDialogs.input("New Challenge", null, "Enter a new challenge name:").showAndWait().ifPresent(name -> {
            currentChallenge.setName(name);
            Database.d.updateChallenge(currentChallenge);
            eventChanged();
        });
    }

    public void deleteChallenge(ActionEvent event)
    {
        Alert dialog = FXDialogs.confirm("Delete Challenge", null, "Are you sure you want to delete " + currentChallenge.getName() + ".  This will delete all data assoicated with this challenge");
        if (dialog.showAndWait().get().equals(ButtonType.OK)) {
            Database.d.deleteChallenge(currentChallenge.getChallengeId());
            eventChanged();
        }
    }

    public void loadEntrants(ActionEvent event)
    {
        //int baseRounds = depthToBaseRounds(currentChallenge.getDepth());
        Optional<List<ChallengeEntry>> ret = new LoadEntrantsDialog(currentChallenge).showAndWait();
        if (!ret.isPresent())
            return;
        List<ChallengeEntry> toload = ret.get();

        int[] pos = null;
        switch (currentChallenge.getBaseRounds())
        {
            case 16: pos = Positions.POS32; break;
            case 8: pos = Positions.POS16; break;
            case 4: pos = Positions.POS8; break;
            case 2: pos = Positions.POS4; break;
        }

        // build a list of things to update
        int bys = currentChallenge.getMaxEntrantCount() - toload.size();
        for (int ii = 0; ii < toload.size(); ii++)
        {
            int placement = pos[ii];
            int rndidx = currentChallenge.getFirstRoundNumber() - placement/2;
            Ids.Location.Level level = (placement%2 != 0) ? Ids.Location.Level.LOWER : Ids.Location.Level.UPPER;
            Ids.Location loc = new Ids.Location(currentChallenge.getChallengeId(), rndidx, level);
            if (bys > 0)
            {
                loc = loc.advancesTo();
                bys--;
            }

            toload.get(ii).setLocation(loc);
        }

        System.out.println(toload);
        // make the actual call to update the model
        //model.setEntrants(updates);

        // call set challenge to update all of our labels
        //setChallenge(challenge);
    }

    public void eventSelected(ActionEvent event)
    {
        Event e = eventSelect.getValue();
        if (e != null) {
            appState.setCurrentEvent(e);
            Prefs.setEventIndex(eventSelect.getSelectionModel().getSelectedIndex());
        } else {
            appState.setCurrentEvent(null);
        }

        eventChanged();
    }


    public void challengeSelected(ActionEvent event)
    {
        Challenge c = challengeSelect.getValue();
        if (c != null) {
            currentChallenge = c;
            Prefs.setChallengeIndex(challengeSelect.getSelectionModel().getSelectedIndex());
            setBracketURL(c.getChallengeId());
        } else {
            currentChallenge = null;
            setBracketURL(null);
        }
    }


    public void bracketViewMousePressed(MouseEvent event)
    {
        webx = event.getScreenX();
        weby = event.getScreenY();
    }


    public void attemptSeriesChange(String newseries)
    {
        if (!Database.openSeriesStrict(newseries, 0)) {
            return;
        }
        Prefs.setSeries(newseries);
        appState.setCurrentSeries(newseries);
        seriesLabel.setText(newseries);
        seriesChanged();
    }

    public void seriesChanged()
    {
        List<Event> events = Database.d.getEvents();
        eventSelect.setItems(FXCollections.observableArrayList(events));
        eventSelect.getSelectionModel().select(Math.min(Prefs.getEventIndex(0), eventSelect.getItems().size()-1));
    }

    public void eventChanged()
    {
        List<Challenge> challenges = Database.d.getChallengesForEvent(appState.getCurrentEventId());
        challengeSelect.setItems(FXCollections.observableArrayList(challenges));
        challengeSelect.getSelectionModel().select(Math.min(Prefs.getChallengeIndex(0), challengeSelect.getItems().size()-1));
    }

    public void doPopup(int rnd)
    {
        if (contextMenu.isShowing()) {
            contextMenu.hide();
        }

        contextMenu.getItems().clear();

        // create menuitems
        MenuItem menuItem1 = new MenuItem(""+webx);
        MenuItem menuItem2 = new MenuItem(""+weby);
        MenuItem menuItem3 = new MenuItem(""+rnd);

        // add menu items to menu
        contextMenu.getItems().add(menuItem1);
        contextMenu.getItems().add(menuItem2);
        contextMenu.getItems().add(menuItem3);

        contextMenu.show(bracketView, webx, weby);
    }


    private void setBracketURL(UUID challengeid)
    {
        WebEngine engine = bracketView.getEngine();

        if (challengeid == null) {
            engine.loadContent("");
            return;
        }

        String url = String.format("http://127.0.0.1/results/%s/challenge/%s/bracket", appState.getCurrentSeries(), challengeid);
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == State.SUCCEEDED) {
                Document doc = engine.getDocument() ;
                Element styleNode = doc.createElement("style");
                Text styleContent = doc.createTextNode(
                          "nav, h3 { display: none !important; } "
                        + ".container { max-width: 4000px !important; } "
                        );
                styleNode.appendChild(styleContent);
                doc.getDocumentElement().getElementsByTagName("head").item(0).appendChild(styleNode);

                JSObject win = (JSObject)engine.executeScript("window");
                win.setMember("fxcontroller", this);
                engine.executeScript("openround = function(ev, rnd) { fxcontroller.doPopup(rnd); }");
            }
        });
        engine.load(url);
    }


}
