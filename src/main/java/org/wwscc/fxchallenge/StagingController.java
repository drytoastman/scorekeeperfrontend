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

import org.wwscc.fxchallenge.StagingCellFactory.CellType;
import org.wwscc.storage.Challenge;
import org.wwscc.storage.ChallengeRound;
import org.wwscc.storage.ChallengeRun;
import org.wwscc.storage.ChallengeStaging;
import org.wwscc.storage.Database;
import org.wwscc.storage.ChallengeRound.RoundEntrant;
import org.wwscc.timercomm.TimerClient;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

@SuppressWarnings({ "rawtypes", "unchecked" })  // Generics just makes everything messy in the columns case
public class StagingController implements MessageListener
{
    private static final Logger log = Logger.getLogger(StagingController.class.getCanonicalName());

    private TableView<ChallengePair> stageTable;
    private ObjectProperty<Challenge> currentChallenge;

    private IntegerProperty highlightRound;
    private IntegerProperty leftStartPtr, rightStartPtr;
    private IntegerProperty leftFinishPtr, rightFinishPtr;

    private Map<Integer, ChallengeRound> rounds;
    private ChallengeStaging stageOrder;
    private TimerClient timerclient;

    StringProperty timerHost, timerLeftDial, timerRightDial;

    public StagingController(TableView<ChallengePair> table, SimpleObjectProperty<Challenge> challenge)
    {
        Messenger.register(MT.TIMER_SERVICE_DELETE, this);
        Messenger.register(MT.TIMER_SERVICE_RUN, this);
        Messenger.register(MT.TIMER_SERVICE_CONNECTION_CLOSED, this);
        Messenger.register(MT.TIMER_SERVICE_CONNECTION_OPEN, this);
        Messenger.register(MT.TIMER_SERVICE_DIALIN_L, this);
        Messenger.register(MT.TIMER_SERVICE_DIALIN_R, this);
        Messenger.register(MT.TIMER_SERVICE_TREE, this);

        rounds         = new HashMap<>();
        timerHost      = new SimpleStringProperty("Not Connected");
        timerLeftDial  = new SimpleStringProperty();
        timerRightDial = new SimpleStringProperty();
        highlightRound = new SimpleIntegerProperty();
        leftStartPtr   = new SimpleIntegerProperty(0);
        leftFinishPtr  = new SimpleIntegerProperty(0);
        rightStartPtr  = new SimpleIntegerProperty(0);
        rightFinishPtr = new SimpleIntegerProperty(0);
        currentChallenge = challenge;
        stageTable = table;

        currentChallenge.addListener((ob, old, newchallenge) -> {
            rounds.clear();
            if (newchallenge != null) {
                for (ChallengeRound r : Database.d.getRoundsForChallenge(newchallenge.getChallengeId()))
                    rounds.put(r.getRound(), r);
                stageOrder = Database.d.getStagingForChallenge(newchallenge.getChallengeId());
                loadTable();
            } else {
                stageTable.getItems().clear();
            }
        });

        table.setRowFactory(new StagingRowFactory(highlightRound));
        setupColumns(stageTable.getColumns());

        KeyCombination cut = new KeyCodeCombination(KeyCode.X, KeyCombination.SHORTCUT_DOWN);
        table.setOnKeyPressed(e -> {
            if (cut.match(e)) {
                System.out.println("CUT");
            }
        });
    }

    private void setupColumns(List<TableColumn<ChallengePair,?>> columns)
    {
        for (TableColumn<ChallengePair, ?> col : columns) {
            setupColumns(col.getColumns()); // recurse for subcolumns
            String id = col.getId();
            if (id == null) continue;

            switch(id) {
                case "colRound":
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().round);
                    break;
                case "colAnnouncer":
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().announcer);
                    break;

                case "colDialLeft":
                    col.setCellFactory(StagingCellFactory.Factory(CellType.TIME, leftStartPtr, "nextDialCell"));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.dial);
                    break;
                case "colNameLeft":
                    col.setCellFactory(StagingCellFactory.Factory(CellType.STRING, leftStartPtr, "nextDialCell"));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.name);
                    break;
                case "colReactionLeft":
                    col.setCellFactory(StagingCellFactory.Factory(CellType.TIME, leftStartPtr, "nextDialCell"));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.reaction);
                    break;
                case "colSixtyLeft":
                    col.setCellFactory(StagingCellFactory.Factory(CellType.TIME, leftStartPtr, "nextDialCell"));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.sixty);
                    break;
                case "colTimeLeft":
                    col.setCellFactory(StagingCellFactory.Factory(CellType.TIME, leftFinishPtr, "nextResultCell"));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.raw);
                    break;
                case "colConesLeft":
                    col.setCellFactory(StagingCellFactory.Factory(CellType.CONESGATES, leftFinishPtr, "nextResultCell"));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.cones);
                    break;
                case "colGatesLeft":
                    col.setCellFactory(StagingCellFactory.Factory(CellType.CONESGATES, leftFinishPtr, "nextResultCell"));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.gates);
                    break;
                case "colStatusLeft":
                    col.setCellFactory(StagingCellFactory.Factory(CellType.STATUS, leftFinishPtr, "nextResultCell"));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().left.status);
                    break;

                case "colDialRight":
                    col.setCellFactory(StagingCellFactory.Factory(CellType.TIME, rightStartPtr, "nextDialCell"));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().right.dial);
                    break;
                case "colNameRight":
                    col.setCellFactory(StagingCellFactory.Factory(CellType.STRING, rightStartPtr, "nextDialCell"));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().right.name);
                    break;
                case "colReactionRight":
                    col.setCellFactory(StagingCellFactory.Factory(CellType.TIME, rightStartPtr, "nextDialCell"));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().right.reaction);
                    break;
                case "colSixtyRight":
                    col.setCellFactory(StagingCellFactory.Factory(CellType.TIME, rightStartPtr, "nextDialCell"));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().right.sixty);
                    break;
                case "colTimeRight":
                    col.setCellFactory(StagingCellFactory.Factory(CellType.TIME, rightFinishPtr, "nextResultCell"));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().right.raw);
                    break;
                case "colConesRight":
                    col.setCellFactory(StagingCellFactory.Factory(CellType.CONESGATES, rightFinishPtr, "nextResultCell"));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().right.cones);
                    break;
                case "colGatesRight":
                    col.setCellFactory(StagingCellFactory.Factory(CellType.CONESGATES, rightFinishPtr, "nextResultCell"));
                    col.setCellValueFactory(p -> (ObservableValue)p.getValue().right.gates);
                    break;
                case "colStatusRight":
                    col.setCellFactory(StagingCellFactory.Factory(CellType.STATUS, rightFinishPtr, "nextResultCell"));
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
        ChallengeRound r = rounds.get(round);
        UUID topid = r.getTopCar().getCarId();
        UUID botid = r.getBottomCar().getCarId();
        for (ChallengePair p : stageTable.getItems()) {
            if (p.round.get() != round) continue;
            UUID lid = p.left.carid.get();
            UUID rid = p.right.carid.get();
            if ((topid!=null) && (topid.equals(lid) || topid.equals(rid)))
                return true;
            if ((botid!=null) && (botid.equals(lid) || botid.equals(rid)))
                return true;
        }
        return false;
    }

    public boolean hasBothEntries(int round)
    {
        ChallengeRound r = rounds.get(round);
        return r.getTopCar().getCarId() != null && r.getBottomCar().getCarId() != null;
    }

    public void stage(int round, boolean samecar)
    {
        try {
            List<ChallengeStaging.Entry> entries = stageOrder.getEntries();
            if (samecar) {
                entries.add(new ChallengeStaging.Entry(round, "U", null));
                entries.add(new ChallengeStaging.Entry(round, null, "U"));
                entries.add(new ChallengeStaging.Entry(round, null, "L"));
                entries.add(new ChallengeStaging.Entry(round, "L", null));
            } else {
                entries.add(new ChallengeStaging.Entry(round, "U", "L"));
                entries.add(new ChallengeStaging.Entry(round, "L", "U"));
            }
            Database.d.setChallengeStaging(stageOrder);
            loadTable();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void highlight(int round)
    {
        highlightRound.set(round);
        stageTable.refresh();
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
                timerHost.set(((TimerClient)data).getRemote().getHostString());
                break;

            case TIMER_SERVICE_CONNECTION_CLOSED:
                timerHost.set("Not Connected");
                break;

            case TIMER_SERVICE_TREE:
            case TIMER_SERVICE_DELETE:
                log.info("Don't implement " + type.toString() + " yet");
                break;

            case TIMER_SERVICE_RUN:
                //stageTable.getItems().get(0).timerData((Run)data);
                rightStartPtr.set(rightStartPtr.get()+1);
                //stageTable.refresh();
                break;

            case TIMER_SERVICE_DIALIN_L:
                timerLeftDial.set(data.toString());
                break;

            case TIMER_SERVICE_DIALIN_R:
                timerRightDial.set(data.toString());
                break;
        }
    }
}
