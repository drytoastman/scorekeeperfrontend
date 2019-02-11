/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2019 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.fxchallenge;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.wwscc.storage.Challenge;
import org.wwscc.storage.ChallengeRound;
import org.wwscc.storage.ChallengeRun;
import org.wwscc.storage.ChallengeStaging;
import org.wwscc.storage.Database;
import org.wwscc.storage.Run;
import org.wwscc.storage.ChallengeRound.RoundEntrant;
import org.wwscc.timercomm.TimerClient;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.ChoiceBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.paint.Color;
import javafx.util.Callback;

@SuppressWarnings({ "rawtypes", "unchecked" })  // Generics just makes everything messy in the columns case
public class StagingController implements MessageListener
{
    private static final Logger log = Logger.getLogger(StagingController.class.getCanonicalName());

    private static Callback CONESGATES = ChoiceBoxTableCell.forTableColumn(FXCollections.observableArrayList(0, 1, 2, 3, 4, 5));
    private static Callback STATUS = ChoiceBoxTableCell.forTableColumn(FXCollections.observableArrayList("OK", "RL", "NS", "DNF", "DNS"));
    private static Callback TIME = TextFieldTableCell.forTableColumn(new DoubleConverter());

    private TableView<ChallengePair> stageTable;
    private Label timerLabel;
    //private TableColumn colTimerLeft, colTimerRight;
    private SimpleObjectProperty<Challenge> currentChallenge;
    private Map<Integer, ChallengeRound> rounds;
    private ChallengeStaging stageOrder;
    private TimerClient timerclient;

    public StagingController(TableView<ChallengePair> table, SimpleObjectProperty<Challenge> challenge, Label label)
    {
        Messenger.register(MT.TIMER_SERVICE_DELETE, this);
        Messenger.register(MT.TIMER_SERVICE_RUN, this);
        Messenger.register(MT.TIMER_SERVICE_CONNECTION_CLOSED, this);
        Messenger.register(MT.TIMER_SERVICE_CONNECTION_OPEN, this);

        rounds = new HashMap<>();
        currentChallenge = challenge;
        stageTable = table;
        timerLabel = label;

        currentChallenge.addListener((ob, old, newchallenge) -> {
            rounds.clear();
            for (ChallengeRound r : Database.d.getRoundsForChallenge(currentChallenge.get().getChallengeId()))
                rounds.put(r.getRound(), r);
            stageOrder = Database.d.getStagingForChallenge(currentChallenge.get().getChallengeId());
            loadTable();
        });

        setupColumns(stageTable.getColumns());

        //colTimerLeft.setText("44.123");
        //colTimerRight.setText("42.555");
    }

    private void setupColumns(List<TableColumn<ChallengePair,?>> columns)
    {
        for (TableColumn<ChallengePair, ?> col : columns) {
            setupColumns(col.getColumns()); // recurse for subcolumns
            String id = col.getId();
            if (id == null) continue;

            switch(id) {
                //case "colTimerLeft":  colTimerLeft  = col; break;
                //case "colTimerRight": colTimerRight = col; break;

                case "colRound":
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().round);
                    break;
                case "colAnnouncer":
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().announcer);
                    break;

                case "colDialLeft":
                    col.setCellFactory(TIME);
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.dial);
                    break;
                case "colNameLeft":
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.name);
                    break;
                case "colReactionLeft":
                    col.setCellFactory(TIME);
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.reaction);
                    break;
                case "colSixtyLeft":
                    col.setCellFactory(TIME);
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.sixty);
                    break;
                case "colTimeLeft":
                    col.setCellFactory(TIME);
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.raw);
                    break;
                case "colConesLeft":
                    col.setCellFactory(CONESGATES);
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.cones);
                    break;
                case "colGatesLeft":
                    col.setCellFactory(CONESGATES);
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.gates);
                    break;
                case "colStatusLeft":
                    col.setCellFactory(STATUS);
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.status);
                    break;

                case "colDialRight":
                    col.setCellFactory(TIME);
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().right.dial);
                    break;
                case "colNameRight":
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().right.name);
                    break;
                case "colReactionRight":
                    col.setCellFactory(TIME);
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().right.reaction);
                    break;
                case "colSixtyRight":
                    col.setCellFactory(TIME);
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().right.sixty);
                    break;
                case "colTimeRight":
                    col.setCellFactory(TIME);
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().right.raw);
                    break;
                case "colConesRight":
                    col.setCellFactory(CONESGATES);
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().right.cones);
                    break;
                case "colGatesRight":
                    col.setCellFactory(CONESGATES);
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().right.gates);
                    break;
                case "colStatusRight":
                    col.setCellFactory(STATUS);
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().right.status);
                    break;

                default:
                    break;
            }
        }
    }


    public void timerConnect()
    {
        new FinderDialog().showAndWait().ifPresent(newaddr -> {
            try {
                if (timerclient != null)
                    timerclient.stop();
                timerclient = null;
                timerclient = new TimerClient(newaddr);
                timerclient.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public boolean isStaged(int round)
    {
        if (!hasBothEntries(round)) return false;

        ChallengeRound r = rounds.get(round);
        UUID topid = r.getTopCar().getCarId();
        UUID botid = r.getBottomCar().getCarId();
        for (ChallengePair p : stageTable.getItems()) {
            if (p.round.get() != round) continue;
            UUID lid = p.left.carid.get();
            UUID rid = p.right.carid.get();
            if (topid.equals(lid) || topid.equals(rid) || botid.equals(lid) || botid.equals(rid))
                return true;
        }
        return false;
    }

    public boolean hasBothEntries(int round)
    {
        ChallengeRound r = rounds.get(round);
        return r.getTopCar().getCarId() != null && r.getBottomCar().getCarId() != null;
    }

    public void stage(int round, boolean swapped)
    {
        try {
            if (swapped) {
                stageOrder.getEntries().add(new ChallengeStaging.Entry(round, "L", "U"));
                stageOrder.getEntries().add(new ChallengeStaging.Entry(round, "U", "L"));
            } else {
                stageOrder.getEntries().add(new ChallengeStaging.Entry(round, "U", "L"));
                stageOrder.getEntries().add(new ChallengeStaging.Entry(round, "L", "U"));
            }
            Database.d.setChallengeStaging(stageOrder);
            loadTable();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadTable()
    {
        UUID challengeid = currentChallenge.get().getChallengeId();
        Map<RunKey, ChallengeRun> runs = new HashMap<>();
        for (ChallengeRun r : Database.d.getRunsForChallenge(challengeid))
            runs.put(new RunKey(r), r);

        ObservableList<ChallengePair> items = FXCollections.observableArrayList();
        for (ChallengeStaging.Entry e : stageOrder.getEntries())
        {
            if (!e.valid())
                return;

            ChallengePair p = new ChallengePair(challengeid, e.round());
            ChallengeRound r = rounds.get(e.round());
            RoundEntrant en;

            if (e.left().isPresent()) {
                en = e.left().get().equals("U") ? r.getTopCar() : r.getBottomCar();
                p.setLeft(Database.d.getDriverForCarId(en.getCarId()).getFullName(), en.getDial(), en.getCarId(), runs.get(new RunKey(en.getCarId(), e.round(), 1)));
            }
            if (e.right().isPresent()) {
                en = e.right().get().equals("U") ? r.getTopCar() : r.getBottomCar();
                p.setRight(Database.d.getDriverForCarId(en.getCarId()).getFullName(), en.getDial(), en.getCarId(), runs.get(new RunKey(en.getCarId(), e.round(), 2)));
            }
            items.add(p);
        }

        stageTable.setItems(items);
    }

    @Override
    public void event(MT type, Object data)
    {
        switch (type)
        {
            case TIMER_SERVICE_CONNECTION_OPEN:
                TimerClient t = (TimerClient)data;
                timerLabel.setText(t.getRemote().getHostString());
                //timerLabel.setText("Dial L 0.000 Dial R 0.000");
                timerLabel.setTextFill(Color.BLACK);
                break;

            case TIMER_SERVICE_CONNECTION_CLOSED:
                timerLabel.setText("Not Connected");
                timerLabel.setTextFill(Color.RED);
                break;

            case TIMER_SERVICE_DELETE:
                log.info("Don't implement DELETE yet");
                break;

            case TIMER_SERVICE_RUN:
                stageTable.getItems().get(0).timerData((Run)data);
                break;
        }
    }
}
