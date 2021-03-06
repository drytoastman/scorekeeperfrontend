/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.fxchallenge;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.wwscc.storage.Challenge;
import org.wwscc.storage.Database;
import org.wwscc.storage.Event;
import org.wwscc.util.BrowserControl;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Worker.State;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Pair;
import netscape.javascript.JSObject;

public class MainController
{
    private static final Logger log = Logger.getLogger(MainController.class.getCanonicalName());

    // these are the placements into the bracket as per SCCA rulebook
    public static final int[] RANK4 =  new int[] { 3, 2, 4, 1 };
    public static final int[] RANK8 =  new int[] { 6, 3, 7, 2, 5, 4, 8, 1 };
    public static final int[] RANK16 = new int[] { 11, 6, 14, 3, 10, 7, 15, 2, 12, 5, 13, 4, 9, 8, 16, 1 };
    public static final int[] RANK32 = new int[] { 22, 11, 27, 6, 19, 14, 30, 3, 23, 10, 26, 7, 18, 15, 31, 2, 21, 12, 28, 5, 20, 13, 29, 4, 24, 9, 25, 8, 17, 16, 32, 1 };

    @FXML private Label seriesLabel, timerHost, timerDialLeft, timerDialRight;
    @FXML private ComboBox<Event> eventSelect;
    @FXML private ComboBox<Challenge> challengeSelect;
    @FXML private WebView bracketView;
    @FXML private TableView<ChallengePair> stageTable;
    @FXML private MenuItem newChallengeMenu, editChallengeMenu, deleteChallengeMenu, loadEntrantsMenu, viewInBrowserMenu;
    @FXML private Menu challengeMenu;

    private StringProperty currentSeries;
    private SimpleObjectProperty<Event> currentEvent;
    private SimpleObjectProperty<Challenge> currentChallenge;

    private StagingController staging;
    private ContextMenu contextMenu;
    private int contextRound;
    private MenuItem stageNormal, stageSameCar, highlight, override;
    private Menu autoAdvance;

    private double webx, weby;

    public MainController()
    {
        currentSeries    = new SimpleStringProperty();
        currentEvent     = new SimpleObjectProperty<>();
        currentChallenge = new SimpleObjectProperty<>();

        contextMenu      = new ContextMenu();
        stageNormal      = new MenuItem("Stage normal");
        stageNormal.setOnAction(e -> staging.stage(contextRound, false));

        stageSameCar     = new MenuItem("Stage sharing car");
        stageSameCar.setOnAction(e -> staging.stage(contextRound, true));

        highlight        = new MenuItem("Highlight in table");
        highlight.setOnAction(e -> staging.highlight(contextRound));

        override         = new MenuItem("Override Dialins");
        override.setOnAction(e -> staging.overrideDialins(contextRound));

        autoAdvance = new Menu("Auto Advance");

        contextMenu.getItems().addAll(stageNormal, stageSameCar, new SeparatorMenuItem(), highlight, override, autoAdvance);

        Messenger.register(MT.RELOAD_BRACKET, (m,o) -> loadBracket());
    }

    @FXML
    public void initialize()
    {
        staging = new StagingController(stageTable, currentEvent, currentChallenge);

        timerHost.textProperty().bind(staging.timerHost);
        timerHost.textFillProperty().bind(Bindings.when(staging.timerHost.isNotEqualTo("Not Connected")).then(Color.BLACK).otherwise(Color.RED));
        timerDialLeft.textProperty().bind(staging.timerLeftDial);
        timerDialLeft.textFillProperty().bind(Bindings.when(staging.leftDialOK()).then(Color.BLACK).otherwise(Color.RED));
        timerDialRight.textProperty().bind(staging.timerRightDial);
        timerDialRight.textFillProperty().bind(Bindings.when(staging.rightDialOK()).then(Color.BLACK).otherwise(Color.RED));

        currentSeries.addListener((ob, old, name) -> {
            List<Event> events = Database.d.getEvents();
            eventSelect.setItems(FXCollections.observableArrayList(events));
            eventSelect.getSelectionModel().select(Math.min(Prefs.getEventIndex(0), eventSelect.getItems().size()-1));
            Prefs.setSeries(name);
        });
        seriesLabel.textProperty().bind(currentSeries);

        currentEvent.bind(eventSelect.valueProperty());
        currentEvent.addListener((ob, old, newevent) -> {
            reloadChallengeSelect();
            int index = eventSelect.getSelectionModel().getSelectedIndex();
            Prefs.setEventIndex(index);
            challengeMenu.setDisable(index < 0);
            newChallengeMenu.setDisable(index < 0);
        });

        currentChallenge.bind(challengeSelect.valueProperty());
        currentChallenge.addListener((ob, old, newchallenge) -> {
            int index = challengeSelect.getSelectionModel().getSelectedIndex();
            if (index >= 0)
                Prefs.setChallengeIndex(index);
            editChallengeMenu.setDisable(index < 0);
            deleteChallengeMenu.setDisable(index < 0);
            loadEntrantsMenu.setDisable(index < 0);
            viewInBrowserMenu.setDisable(index < 0);
            loadBracket();
        });

        bracketView.setContextMenuEnabled(false);
    }

    public void quit(ActionEvent event)
    {
        System.exit(0);
    }

    public void openSeries(ActionEvent event)
    {
        ChoiceDialog<String> dialog = FXDialogs.choice("Choose Series", null, null, Database.d.getSeriesList());
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> connectSeries(name));
    }

    public void newChallenge(ActionEvent event)
    {
        List<String> names = Database.d.getChallengesForEvent(currentEvent.get().getEventId()).stream().map(c -> c.getName()).collect(Collectors.toList());
        new NewChallengeDialog(names).showAndWait().ifPresent(pair -> {
            try {
                Challenge c = Database.d.newChallenge(currentEvent.get().getEventId(), pair.getKey(), pair.getValue());
                reloadChallengeSelect();
                challengeSelect.getSelectionModel().select(c);
            } catch (SQLException sqle) {
                log.log(Level.WARNING, "\bFailed to create challenge: " + sqle, sqle);
            }
        });
    }

    public void editChallenge(ActionEvent event)
    {
        FXDialogs.input("New Challenge", null, "Enter a new challenge name:").showAndWait().ifPresent(name -> {
            currentChallenge.get().setName(name);
            Database.d.updateChallenge(currentChallenge.get());
            reloadChallengeSelect();
        });
    }

    public void deleteChallenge(ActionEvent event)
    {
        Alert dialog = FXDialogs.confirm("Delete Challenge", null, "Are you sure you want to delete " + currentChallenge.get().getName() + ".  This will delete all data assoicated with this challenge");
        if (dialog.showAndWait().get().equals(ButtonType.OK)) {
            Database.d.deleteChallenge(currentChallenge.get().getChallengeId());
            Prefs.setChallengeIndex(0);
            reloadChallengeSelect();
        }
    }

    public void loadEntrants(ActionEvent event)
    {
        Challenge c = currentChallenge.get();
        if (c == null) {
            FXDialogs.warning("No Challenge", null, "Please select a challenge before trying to load entrants").showAndWait();
            return;
        }

        Optional<List<DialinEntry>> ret = new LoadEntrantsDialog(c).showAndWait();
        if (!ret.isPresent())
            return;
        List<DialinEntry> toload = ret.get();

        int[] rank = null;
        switch (c.getBaseRounds())
        {
            case 16: rank = RANK32; break;
            case 8:  rank = RANK16; break;
            case 4:  rank = RANK8;  break;
            case 2:  rank = RANK4;  break;
        }

        List<SQLException> exceptions = new ArrayList<>();
        int bys = c.getMaxEntrantCount() - toload.size();
        int finishpos;

        // load first round, possibly some bys as well
        for (int rndidx = c.getFirstRoundNumber(), ii = 0; rndidx >= c.getBaseRounds(); rndidx--) {
            try {
                finishpos = rank[ii++];
                loadOne(toload, new Ids.Location(c.getChallengeId(), rndidx, Ids.Location.Level.UPPER), finishpos, finishpos <= bys);
                finishpos = rank[ii++];
                loadOne(toload, new Ids.Location(c.getChallengeId(), rndidx, Ids.Location.Level.LOWER), finishpos, finishpos <= bys);
            } catch (SQLException sqle) {
                exceptions.add(sqle);
            }
        }

        if (exceptions.size() > 0) {
            FXDialogs.warning("Challenge Loading Error", null, Arrays.toString(exceptions.toArray()));
        }

        staging.reloadRoundsFromDatabase(currentChallenge.get().getChallengeId());
        loadBracket();
    }

    public void viewInBrowser(ActionEvent event)
    {
        BrowserControl.openURL(String.format("http://127.0.0.1/results/%s/%s/bracket", currentSeries.get(), currentChallenge.get().getChallengeId()));
    }

    public void timerConnect(ActionEvent event)
    {
        staging.timerConnect();
    }

    public void bracketViewMousePressed(MouseEvent event)
    {
        webx = event.getScreenX();
        weby = event.getScreenY();
    }

    // --------------------------------------------------------

    public void connectSeries(String name)
    {
        try {
            String seriesattached = Database.openSeriesNM(name, 0, null);
            currentSeries.set(seriesattached);
            return;
        } catch (SQLException sqle) {
            log.log(Level.WARNING, "Unable to make a database connection: " + sqle, sqle);
        }
    }

    public void reloadChallengeSelect()
    {
        if (currentEvent.get() != null)
            challengeSelect.setItems(FXCollections.observableArrayList(Database.d.getChallengesForEvent(currentEvent.get().getEventId())));
        else
            challengeSelect.setItems(FXCollections.observableArrayList());
        challengeSelect.getSelectionModel().select(Math.min(Prefs.getChallengeIndex(0), challengeSelect.getItems().size()-1));
    }

    private void loadOne(List<DialinEntry> toload, Ids.Location location, int finishpos, boolean by) throws SQLException
    {
        UUID carid = null;
        double dialin = 999.999;

        finishpos--; // convert to 0 index
        if (finishpos < toload.size()) {
            DialinEntry entry = toload.get(finishpos);
            carid = entry.entrant.getCarId();
            dialin = entry.dialin.get();
            if (by) {
                Database.d.updateChallengeRound(location.challengeid, location.round, 1, null, 999.999);
                Database.d.updateChallengeRound(location.challengeid, location.round, 2, null, 999.999);
                location = location.advancesTo();
            }
        }
        Database.d.updateChallengeRound(location.challengeid, location.round, location.level == Ids.Location.Level.UPPER ? 1 : 2, carid, dialin);
    }

    public void doPopup(int rnd)
    {
        contextRound = rnd;
        boolean hidestage = !staging.hasBothEntries(rnd) || staging.isStaged(rnd);
        stageNormal.setDisable(hidestage);
        stageSameCar.setDisable(hidestage);
        highlight.setDisable(!staging.isStaged(rnd));
        override.setDisable(!staging.hasBothEntries(rnd));

        autoAdvance.getItems().clear();
        for (Pair<Ids.Location.Level, String> d : staging.bracketDrivers(rnd)) {
            MenuItem mi = new MenuItem(d.getValue());
            mi.setOnAction(e -> staging.autoAdvance(rnd, d.getKey()));
            autoAdvance.getItems().add(mi);
        }

        contextMenu.show(bracketView, webx, weby);
    }

    private void loadBracket()
    {
        WebEngine engine = bracketView.getEngine();

        if (currentChallenge.get() == null) {
            engine.loadContent("");
            return;
        }

        String url = String.format("http://127.0.0.1/results/%s/%s/bracket?gui=1", currentSeries.get(), currentChallenge.get().getChallengeId());
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == State.SUCCEEDED) {
                JSObject win = (JSObject)engine.executeScript("window");
                win.setMember("maincontroller", this);
                engine.executeScript("openround = function(rnd) { maincontroller.doPopup(rnd); }");
            }
        });
        engine.load(url);
    }
}
