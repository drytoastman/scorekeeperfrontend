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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.wwscc.storage.Challenge;
import org.wwscc.storage.ChallengeRound;
import org.wwscc.storage.ChallengeStaging;
import org.wwscc.storage.Database;
import org.wwscc.storage.Event;
import org.wwscc.storage.LeftRightDialin;
import org.wwscc.storage.Run;
import org.wwscc.storage.ChallengeRound.RoundEntrant;
import org.wwscc.timercomm.TimerClient;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.NF;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableView;
import javafx.util.Duration;

public class StagingController implements MessageListener
{
    private static final Logger log = Logger.getLogger(StagingController.class.getCanonicalName());

    private TableView<ChallengePair> stageTable;
    private UUID shownId, activeId;
    private TimerClient timerclient;
    private Map<UUID, Store> data;
    private RoundWinnerLogic winnerLogic;

    StringProperty timerHost, timerLeftDial, timerRightDial;

    private StringProperty activeLeftDial, activeRightDial;
    private IntegerProperty highlightRound;

    static class Store
    {
        UUID challengeid;
        ObservableList<ChallengePair> pairs = FXCollections.observableArrayList();
        Map<Integer, ChallengeRound> rounds = new HashMap<>();

        public Store(UUID challengeid) { this.challengeid = challengeid; }

        void addEntry(ChallengeStaging.Entry stg)
        {
            if (!stg.valid())
                return;

            ChallengePair pair = new ChallengePair(challengeid, stg.round());
            ChallengeRound r = rounds.get(stg.round());
            RoundEntrant en;

            if (stg.left().isPresent()) {
                en = stg.left().get().equals("U") ? r.getTopCar() : r.getBottomCar();
                pair.setLeft(Database.d.getDriverForCarId(en.getCarId()), en.getDial(), en.getCarId(),
                             Database.d.getRunForChallengeEntry(challengeid, stg.round(), en.getCarId(), 1));
            }
            if (stg.right().isPresent()) {
                en = stg.right().get().equals("U") ? r.getTopCar() : r.getBottomCar();
                pair.setRight(Database.d.getDriverForCarId(en.getCarId()), en.getDial(), en.getCarId(),
                              Database.d.getRunForChallengeEntry(challengeid, stg.round(), en.getCarId(), 2));
            }

            pairs.add(pair);
        }
    }

    public StagingController(TableView<ChallengePair> table, SimpleObjectProperty<Event> event, SimpleObjectProperty<Challenge> challenge)
    {
        Messenger.register(MT.TIMER_SERVICE_DELETE, this);
        Messenger.register(MT.TIMER_SERVICE_RUN, this);
        Messenger.register(MT.TIMER_SERVICE_CONNECTION_CLOSED, this);
        Messenger.register(MT.TIMER_SERVICE_CONNECTION_OPEN, this);
        Messenger.register(MT.TIMER_SERVICE_DIALIN_L, this);
        Messenger.register(MT.TIMER_SERVICE_DIALIN_R, this);
        //Messenger.register(MT.TIMER_SERVICE_TREE, this);
        Messenger.register(MT.REMOVE_ROUND, this);
        Messenger.register(MT.CLEAR_ROW_DATA, this);
        Messenger.register(MT.MAKE_ROW_ACTIVE, this);
        Messenger.register(MT.DEACTIVATE, this);

        timerHost       = new SimpleStringProperty("Not Connected");
        timerLeftDial   = new SimpleStringProperty();
        timerRightDial  = new SimpleStringProperty();
        activeLeftDial  = new SimpleStringProperty();
        activeRightDial = new SimpleStringProperty();
        highlightRound  = new SimpleIntegerProperty(-1);
        data            = new HashMap<>();
        winnerLogic     = new RoundWinnerLogic(event, challenge, data);

        challenge.addListener((ob, old, newchallenge) -> changeVisibleChallenge(newchallenge));

        table.setRowFactory(new StagingRows(highlightRound));
        StagingColumns.setupColumns(table.getColumns());
        stageTable = table;

        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(3000), ae -> checkAdvanceActive()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    public BooleanBinding leftDialOK()  { return activeLeftDial.isEqualTo(timerLeftDial); }
    public BooleanBinding rightDialOK() { return activeRightDial.isEqualTo(timerRightDial); }

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
        return data.get(shownId).pairs.stream().anyMatch(pair -> pair.round.get() == round);
    }

    public boolean hasBothEntries(int round)
    {
        ChallengeRound r = data.get(shownId).rounds.get(round);
        return r.getTopCar().getCarId() != null && r.getBottomCar().getCarId() != null;
    }

    public void stage(int round, boolean samecar)
    {
        try {
            Store store = data.get(shownId);
            ChallengeStaging stageOrder = Database.d.getStagingForChallenge(shownId);
            List<ChallengeStaging.Entry> entries = stageOrder.getEntries();
            if (samecar) {
                entries.add(new ChallengeStaging.Entry(round, "U", null));
                entries.add(new ChallengeStaging.Entry(round, null, "U"));
                entries.add(new ChallengeStaging.Entry(round, null, "L"));
                entries.add(new ChallengeStaging.Entry(round, "L", null));
                for (int ii = entries.size()-4; ii < entries.size(); ii++) {
                    store.addEntry(entries.get(ii));
                }
            } else {
                entries.add(new ChallengeStaging.Entry(round, "U", "L"));
                entries.add(new ChallengeStaging.Entry(round, "L", "U"));
                for (int ii = entries.size()-2; ii < entries.size(); ii++) {
                    store.addEntry(entries.get(ii));
                }
            }
            winnerLogic.checkForWinner(round, true); // just in case it was removed and added again, update the comment
            Database.d.setChallengeStaging(stageOrder);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void highlight(int round)
    {
        highlightRound.set(round);
    }

    public void overrideDialins(int round)
    {
        Store store = data.get(shownId);
        ChallengeRound r = store.rounds.get(round);

        new OverrideDialinDialog(r.getTopCar().getDial(), r.getBottomCar().getDial()).showAndWait().ifPresent(p -> {
            r.getTopCar().setDial(p.getKey());
            r.getBottomCar().setDial(p.getValue());
            Database.d.updateChallengeRound(r);
            for (ChallengePair pair : store.pairs) { // in place update of table dialins
                if (pair.round.get() != round) continue;
                pair.overrideDial(r.getTopCar().getCarId(), p.getKey());
                pair.overrideDial(r.getBottomCar().getCarId(), p.getValue());
            }
            Messenger.sendEvent(MT.RELOAD_BRACKET, null);
        });
    }


    private void changeVisibleChallenge(Challenge newchallenge)
    {
        if (newchallenge != null) {
            shownId = newchallenge.getChallengeId();
            if (data.containsKey(shownId)) {
                stageTable.setItems(data.get(shownId).pairs);
            } else {
                reloadRoundsFromDatabase(shownId);
            }
        } else {
            stageTable.setItems(FXCollections.observableArrayList());
        }
    }

    public void reloadRoundsFromDatabase(UUID newid)
    {
        data.remove(shownId);
        Store store = new Store(shownId);
        data.put(shownId, store);
        for (ChallengeRound r : Database.d.getRoundsForChallenge(shownId))
            store.rounds.put(r.getRound(), r);
        for (ChallengeStaging.Entry e : Database.d.getStagingForChallenge(shownId).getEntries())
            store.addEntry(e);
        for (int round : store.rounds.keySet()) {
            winnerLogic.checkForWinner(round, true);
        }
        stageTable.setItems(store.pairs);
    }

    private void removeRound(int round)
    {
        try {
            for (ChallengePair pair : data.get(shownId).pairs.filtered(pair -> pair.round.get() == round)) {
                if (pair.isActiveStart()) { // || pair.isActiveFinish()) {
                    if (!FXDialogs.confirm("Removing Active Row", null, "You are about to remove staged entrants that are currently active. Continue?").showAndWait().get().equals(ButtonType.OK))
                        return;
                    break;
                }
            }

            data.get(shownId).pairs.removeIf(pair -> pair.round.get() == round);
            ChallengeStaging stageOrder = Database.d.getStagingForChallenge(shownId);
            if (stageOrder.getEntries().removeIf(pair -> pair.round() == round)) {
                Database.d.setChallengeStaging(stageOrder);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void clearRow(int rowindex)
    {
        if (FXDialogs.confirm("Clear Data", null, "You are about to reset all run data for this row.  Continue?").showAndWait().get().equals(ButtonType.OK)) {
            data.get(shownId).pairs.get(rowindex).clearData();
        }
    }

    private void activateRow(int rowindex)
    {
        if (timerclient == null) {
            FXDialogs.warning("Not Connected", null, "You are trying to active a pair but you are not connected to the timer.").showAndWait();
            return;
        }

        List<ChallengePair> active = data.get(shownId).pairs.filtered(p -> p.isActiveStart()); // || p.isActiveFinish());
        if (active.size() > 0) {
            if (!FXDialogs.confirm("Set Pair Active", null, "You are about to manually change the next active pair.  Continue?").showAndWait().get().equals(ButtonType.OK)) {
                return;
            }
            for (ChallengePair p : active) {
                p.deactivateStart();
            }
        }

        try {
            ChallengePair p = data.get(shownId).pairs.get(rowindex);
            activeId = shownId;
            sendDialsToTimer(p);
            p.makeActiveStart();
            stageTable.refresh();
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to active row: " + e, e);
        }
    }

    private void deleteFinish(Run.WithRowId r)
    {
        for (Store s : data.values())
        {
            FilteredList<ChallengePair> f = s.pairs.filtered(p -> r.getRowId().equals(p.timerRowId));
            if (f.size() > 0) {
                f.forEach(p -> p.delete(r));
                break;
            }
        }
    }

    private void newRunData(Run.WithRowId r)
    {
        for (Store s : data.values())
        {
            FilteredList<ChallengePair> f = s.pairs.filtered(p -> r.getRowId().equals(p.timerRowId));
            if (f.size() > 0) {
                f.forEach(p -> p.runData(r));
                checkAdvanceActive();
                return;
            }
        }

        /* If we didn't find a match but there is an active start row, we can apply a new row if just reaction/sixty */
        if ((activeId == null) || !Double.isNaN(r.getRaw())) return;
        for (ChallengePair p : data.get(activeId).pairs) {
            if (p.isActiveStart()) {
                p.runData(r);
            }
        }
        checkAdvanceActive();
    }

    private void deactivate()
    {
        if (activeId == null) return;
        for (ChallengePair p : data.get(activeId).pairs) {
            p.deactivateStart();
        }
        stageTable.refresh();
    }

    private void checkAdvanceActive()
    {
        if (activeId == null) return;
        List<ChallengePair> l = data.get(activeId).pairs;

        for (int ii = 0; ii < l.size(); ii++)
        {
            ChallengePair p = l.get(ii);
            ChallengePair n = (ii+1 < l.size()) ? l.get(ii+1) : null;

            if (p.isActiveStart()) {
                if (p.startComplete()) {
                    p.deactivateStart();
                    if (n != null) {
                        n.makeActiveStart();
                        sendDialsToTimer(n);
                    }
                    stageTable.refresh();
                }
                return;
            }
        }
    }

    private void sendDialsToTimer(ChallengePair pair)
    {
        double left, right;
        if (pair.left.dial == null) {
            left = right = pair.right.dial.get();
        } else if (pair.right.dial == null) {
            left = right = pair.left.dial.get();
        } else {
            left = pair.left.dial.get();
            right = pair.right.dial.get();
        }
        activeLeftDial.set(NF.format(left));
        activeRightDial.set(NF.format(right));
        timerclient.sendDial(new LeftRightDialin(left, right));
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

            case TIMER_SERVICE_DELETE:
                deleteFinish((Run.WithRowId)data);
                break;

            case TIMER_SERVICE_RUN:
                newRunData((Run.WithRowId)data);
                break;

            case TIMER_SERVICE_DIALIN_L:
                timerLeftDial.set(NF.format((double)data));
                break;

            case TIMER_SERVICE_DIALIN_R:
                timerRightDial.set(NF.format((double)data));
                break;

            case REMOVE_ROUND:
                removeRound((int)data);
                break;

            case CLEAR_ROW_DATA:
                clearRow((int)data);
                break;

            case MAKE_ROW_ACTIVE:
                activateRow((int)data);
                break;

            case DEACTIVATE:
                deactivate();
                break;
        }
    }
}
