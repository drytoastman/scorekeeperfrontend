/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2019 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.fxchallenge;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.wwscc.fxchallenge.StagingController.Store;
import org.wwscc.storage.Challenge;
import org.wwscc.storage.ChallengeRound;
import org.wwscc.storage.ChallengeRun;
import org.wwscc.storage.Database;
import org.wwscc.storage.Event;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import org.wwscc.util.NF;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public class RoundWinnerLogic
{
    //private static final Logger log = Logger.getLogger(RoundWinnerLogic.class.getCanonicalName());
    public enum RoundState { NONE, PARTIAL1, HALFNORMAL, HALFINVERSE, PARTIAL2, DONE, INVALID };


    private ObjectProperty<Event> currentEvent;
    private ObjectProperty<Challenge> currentChallenge;
    private Map<UUID, Store> data;

    public RoundWinnerLogic(SimpleObjectProperty<Event> event, SimpleObjectProperty<Challenge> challenge, Map<UUID, Store> data)
    {
        currentEvent = event;
        currentChallenge = challenge;
        this.data = data;

        Messenger.register(MT.CHALLENGE_RUN_UPDATED, (m,o) -> checkForWinner((int)o, false));
    }

    public RoundState getState(ChallengeRun ul, ChallengeRun ur, ChallengeRun ll, ChallengeRun lr)
    {
        int val = 0;
        if (ul != null && (ul.getRaw() > 0 || ul.hasStatus())) val |= 0x08;
        if (ur != null && (ur.getRaw() > 0 || ur.hasStatus())) val |= 0x04;
        if (ll != null && (ll.getRaw() > 0 || ll.hasStatus())) val |= 0x02;
        if (lr != null && (lr.getRaw() > 0 || lr.hasStatus())) val |= 0x01;

        switch (val)
        {
            case 0:
                return RoundState.NONE;

            case 1:
            case 2:
            case 4:
            case 8:
                return RoundState.PARTIAL1;

            case 6:
                return RoundState.HALFINVERSE;

            case 9:
                return RoundState.HALFNORMAL;

            case 7:
            case 11:
            case 13:
            case 14:
                return RoundState.PARTIAL2;

            case 15:
                return RoundState.DONE;

            default:
                return RoundState.INVALID;
        }
    }

    private double getPenSum(ChallengeRun r)
    {
        if (!r.isOK())
            return 999.999;
        Event e = currentEvent.get();
        return r.getRaw() + (e.getConePenalty() * r.getCones()) + (e.getGatePenalty() * r.getGates());
    }

    private double getPenSum(ChallengePair.Entry ent)
    {
        if (!ent.isOK())
            return 999.999;
        Event e = currentEvent.get();
        return ent.raw.get() + (e.getConePenalty() * ent.cones.get()) + (e.getGatePenalty() * ent.gates.get());
    }

    private Double getNewDial(ChallengeRound.RoundEntrant re)
    {
        if ((re.getLeft() == null) || (re.getRight() == null))
            return re.getDial();
        double halfres = (getPenSum(re.getLeft()) + getPenSum(re.getRight()))/2;
        double dial = re.getDial();
        if (halfres < dial)
            return dial - ((dial - halfres)*1.5);
        else
            return dial;
    }

    public void checkForWinner(int round, boolean noupdate)
    {
        UUID cid = currentChallenge.get().getChallengeId();
        Store store = data.get(cid);
        ChallengeRound cround    = store.rounds.get(round);

        ChallengeRun topLeft     = Database.d.getRunForChallengeEntry(cid, round, cround.getTopCar().getCarId(), 1);
        ChallengeRun topRight    = Database.d.getRunForChallengeEntry(cid, round, cround.getTopCar().getCarId(), 2);
        cround.getTopCar().applyRun(topLeft);
        cround.getTopCar().applyRun(topRight);

        ChallengeRun bottomLeft  = Database.d.getRunForChallengeEntry(cid, round, cround.getBottomCar().getCarId(), 1);
        ChallengeRun bottomRight = Database.d.getRunForChallengeEntry(cid, round, cround.getBottomCar().getCarId(), 2);
        cround.getBottomCar().applyRun(bottomLeft);
        cround.getBottomCar().applyRun(bottomRight);

        ChallengeRound.RoundEntrant winner = null;
        Double margin = null;

        switch (getState(topLeft, topRight, bottomLeft, bottomRight))
        {
            case HALFNORMAL:
                int diffn = topLeft.statusLevel() - bottomRight.statusLevel();
                if (diffn == 0) // both OK, both DNF or both RL
                    break;
                else if (diffn < 0) // bottom higher
                    winner = cround.getTopCar();
                else
                    winner = cround.getBottomCar();
                break;

            case HALFINVERSE:
                int diffi = topRight.statusLevel() - bottomLeft.statusLevel();
                if (diffi == 0) // both OK, both DNF or both RL
                    break;
                else if (diffi < 0) // bottom higher
                    winner = cround.getTopCar();
                else
                    winner = cround.getBottomCar();
                break;

            case DONE:
                int topLevel = topLeft.statusLevel() + topRight.statusLevel();
                int botLevel = bottomLeft.statusLevel() + bottomRight.statusLevel();

                if ((topLevel > 1) && (botLevel > 1))
                    break; // no winner
                else if (topLevel > 0)
                    winner = cround.getBottomCar();
                else if (botLevel > 0)
                    winner = cround.getTopCar();
                else {
                    double topresult = getPenSum(topLeft) + getPenSum(topRight) - (2*cround.getTopCar().getDial());
                    double botresult = getPenSum(bottomLeft) + getPenSum(bottomRight) - (2*cround.getBottomCar().getDial());
                    if (topresult < botresult) {
                        winner = cround.getTopCar();
                        margin = botresult - topresult;
                    } else if (botresult < topresult) {
                        winner = cround.getBottomCar();
                        margin = topresult - botresult;
                    }
                    //else no winner due to tie
                }
                break;
        }

        List<ChallengePair> found = store.pairs.stream().filter(pair -> pair.round.get() == round).collect(Collectors.toList());
        for (int ii = 0; ii < found.size(); ii++) {
            ChallengePair p = found.get(ii);
            p.announcer.set(getPairResult(p.left, p.right));
        }

        if ((winner == null) || (found.size() == 0))
            return;

        String winnername = "ERROR";
        for (ChallengePair p : found) {
            if (winner.getCarId().equals(p.left.carid.get())) {
                winnername = p.left.getFirstName();
                break;
            } else if (winner.getCarId().equals(p.right.carid.get())) {
                winnername = p.right.getFirstName();
                break;
            }
        }
        found.get(found.size()-1).announcer.set(winnername + " wins by " + ((margin!= null) ? NF.format(margin) : "default"));

        if (noupdate)
            return;

        advanceWinner(round, winner);
    }


    public void advanceWinner(int round, ChallengeRound.RoundEntrant winner)
    {
        UUID cid = currentChallenge.get().getChallengeId();
        Store store = data.get(cid);
        ChallengeRound cround    = store.rounds.get(round);

        /* Advance the winner */
        Ids.Round rid = new Ids.Round(cid, round);
        Ids.Location eid = rid.advancesTo();
        ChallengeRound next = store.rounds.get(eid.getRound());
        if (eid.isUpper())
            next.getTopCar().setTo(winner.getCarId(), getNewDial(winner));
        else
            next.getBottomCar().setTo(winner.getCarId(), getNewDial(winner));

        Database.d.updateChallengeRound(next);

        /* Special advance to third place bracket for losers in semifinal */
        Ids.Location thirdid = rid.advanceThird();
        if (thirdid != null)
        {
            ChallengeRound.RoundEntrant loser;
            loser = (winner == cround.getTopCar()) ? cround.getBottomCar() : cround.getTopCar();
            ChallengeRound third = store.rounds.get(thirdid.getRound());
            if (thirdid.isUpper())
                third.getTopCar().setTo(loser.getCarId(), getNewDial(loser));
            else
                third.getBottomCar().setTo(loser.getCarId(), getNewDial(loser));

            Database.d.updateChallengeRound(third);
        }

        Messenger.sendEvent(MT.RELOAD_BRACKET, null);
    }


    private String getPairResult(ChallengePair.Entry left, ChallengePair.Entry right)
    {
        if (!left.hasStatus() && !right.hasStatus()) {
            return "";
        }

        double d1 = getPenSum(left) - left.dial.get();
        double d2 = getPenSum(right) - right.dial.get();
        String ret = "";

        if (Double.isNaN(d1) || Double.isNaN(d2))
            ret = "Uh oh. Someone had NaN";
        else if (!left.isOK() || !right.isOK()) {
            int lsl = ChallengeRun.statusLevel(left.status.get());
            int rsl = ChallengeRun.statusLevel(right.status.get());
            if ((lsl > 1) && (rsl > 1)) {
                ret = "Both entrants had light troubles";
            } else if (lsl > rsl) {
                ret = left.getFirstName() + " has status " + left.status.get();
            } else if (lsl < rsl) {
                ret = right.getFirstName() + " has status " + right.status.get();
            } else {
                ret = "Both entrants have non finishing status";
            }
        }
        else if (d1 < d2)
            ret = left.getFirstName() + " has a " + NF.format(d2 - d1) + " advantage";
        else if (d2 < d1)
            ret = right.getFirstName() + " has a " + NF.format(d1 - d2) + " advantage";
        else
            ret = "Both return with the same net time";

        return ret;
    }
}
