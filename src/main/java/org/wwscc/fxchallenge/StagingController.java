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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
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

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableView;
import javafx.util.Duration;

public class StagingController implements MessageListener
{
    private static final Logger log = Logger.getLogger(StagingController.class.getCanonicalName());

    private TableView<ChallengePair> stageTable;
    private ObjectProperty<Challenge> currentChallenge;

    private Map<Integer, ChallengeRound> rounds;
    private TimerClient timerclient;

    StringProperty timerHost, timerLeftDial, timerRightDial;
    IntegerProperty highlightRound;


    public StagingController(TableView<ChallengePair> table, SimpleObjectProperty<Challenge> challenge)
    {
        Messenger.register(MT.TIMER_SERVICE_DELETE, this);
        Messenger.register(MT.TIMER_SERVICE_RUN, this);
        Messenger.register(MT.TIMER_SERVICE_CONNECTION_CLOSED, this);
        Messenger.register(MT.TIMER_SERVICE_CONNECTION_OPEN, this);
        Messenger.register(MT.TIMER_SERVICE_DIALIN_L, this);
        Messenger.register(MT.TIMER_SERVICE_DIALIN_R, this);
        Messenger.register(MT.TIMER_SERVICE_TREE, this);
        Messenger.register(MT.REMOVE_ROUND_PAIR, this);
        Messenger.register(MT.CLEAR_ROW_DATA, this);
        Messenger.register(MT.MAKE_ROW_ACTIVE, this);


        rounds         = new HashMap<>();
        timerHost      = new SimpleStringProperty("Not Connected");
        timerLeftDial  = new SimpleStringProperty();
        timerRightDial = new SimpleStringProperty();
        highlightRound = new SimpleIntegerProperty(-1);
        currentChallenge = challenge;
        stageTable = table;

        currentChallenge.addListener((ob, old, newchallenge) -> {
            rounds.clear();
            if (newchallenge != null) {
                for (ChallengeRound r : Database.d.getRoundsForChallenge(newchallenge.getChallengeId()))
                    rounds.put(r.getRound(), r);
                loadTable(Database.d.getStagingForChallenge(newchallenge.getChallengeId()));
            } else {
                stageTable.getItems().clear();
            }
        });

        table.setRowFactory(new StagingRows(highlightRound));
        StagingColumns.setupColumns(stageTable.getColumns());

        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(3000), ae -> checkStarts()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
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
            ChallengeStaging stageOrder = Database.d.getStagingForChallenge(currentChallenge.get().getChallengeId());
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
            loadTable(stageOrder);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void removePair(int round)
    {
        try {
            stageTable.getItems().removeIf(pair -> pair.round.get() == round);
            ChallengeStaging stageOrder = Database.d.getStagingForChallenge(currentChallenge.get().getChallengeId());
            if (stageOrder.getEntries().removeIf(pair -> pair.round() == round)) {
                Database.d.setChallengeStaging(stageOrder);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void highlight(int round)
    {
        highlightRound.set(round);
    }

    public void clearRow(int rowindex)
    {
        if (FXDialogs.confirm("Clear Data", null, "You are about to reset all run data for this row.  Continue?").showAndWait().get().equals(ButtonType.OK)) {
            stageTable.getItems().get(rowindex).clearData();
        }
    }

    public void activateRow(int rowindex)
    {
        if (timerclient == null) {
            FXDialogs.warning("Not Connected", null, "You are trying to active a pair but you are not connected to the timer.").showAndWait();
            return;
        }

        List<ChallengePair> active = stageTable.getItems().filtered(p -> p.isActiveStart() || p.isActiveFinish());
        if (active.size() > 0) {
            if (!FXDialogs.confirm("Set Pair Active", null, "You are about to manually change the next active pair.  Continue?").showAndWait().get().equals(ButtonType.OK)) {
                return;
            }
            for (ChallengePair p : active) {
                p.deactivate();
            }
        }

        try {
            ChallengePair p = stageTable.getItems().get(rowindex);
            timerclient.sendLDial(p.left.dial.get());
            timerclient.sendRDial(p.right.dial.get());
            p.makeActiveStart();
            p.makeActiveFinish();
            stageTable.refresh();
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to active row: " + e, e);
        }
    }

    public void newRunData(Run r)
    {
        Iterator<ChallengePair> iter = stageTable.getItems().iterator();

        if (Double.isNaN(r.getRaw())) { // reaction/sixty
            while (iter.hasNext())
            {
                ChallengePair p = iter.next();
                if (p.isActiveStart()) {
                    p.reactionData(r);
                    if (p.startComplete()) {
                        p.deactivateStart();
                        if (iter.hasNext()) {
                            ChallengePair next = iter.next();
                            next.makeActiveStart();
                            timerclient.sendLDial(next.left.dial.get());
                            timerclient.sendRDial(next.right.dial.get());
                        }
                        stageTable.refresh();
                    }
                    break;
                }
            }

        } else {
            while (iter.hasNext())
            {
                ChallengePair p = iter.next();
                if (p.isActiveFinish()) {
                    p.runData(r);
                    if (p.finishComplete()) {
                        p.deactivateFinish();
                        if (iter.hasNext()) {
                            iter.next().makeActiveFinish();
                        }
                        stageTable.refresh();
                    }
                    break;
                }
            }
        }
    }

    private void checkStarts()
    {
        Iterator<ChallengePair> iter = stageTable.getItems().iterator();
        while (iter.hasNext()) {
            ChallengePair p = iter.next();
            if (p.isActiveStart()) {
                if (p.startTimeoutComplete()) {
                    p.deactivateStart();
                    if (iter.hasNext()) {
                        ChallengePair next = iter.next();
                        next.makeActiveStart();
                        timerclient.sendLDial(next.left.dial.get());
                        timerclient.sendRDial(next.right.dial.get());
                    }
                    stageTable.refresh();
                }
            }
        }
    }


    private void loadTable(ChallengeStaging staging)
    {
        Map<RunKey, ChallengeRun> runs = new HashMap<>();
        for (ChallengeRun r : Database.d.getRunsForChallenge(staging.getChallengeId()))
            runs.put(new RunKey(r), r);

        ObservableList<ChallengePair> items = FXCollections.observableArrayList();
        for (ChallengeStaging.Entry e : staging.getEntries())
        {
            if (!e.valid())
                return;

            ChallengePair p = new ChallengePair(staging.getChallengeId(), e.round());
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
                newRunData((Run)data);
                break;

            case TIMER_SERVICE_DIALIN_L:
                timerLeftDial.set(data.toString());
                break;

            case TIMER_SERVICE_DIALIN_R:
                timerRightDial.set(data.toString());
                break;

            case REMOVE_ROUND_PAIR:
                removePair((int)data);
                break;

            case CLEAR_ROW_DATA:
                clearRow((int)data);
                break;

            case MAKE_ROW_ACTIVE:
                activateRow((int)data);
                break;
        }
    }
}
