package org.wwscc.fxchallenge;

import java.util.UUID;
import java.util.logging.Logger;

import org.wwscc.storage.ChallengeRun;
import org.wwscc.storage.Database;
import org.wwscc.storage.Driver;
import org.wwscc.storage.Run;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

public final class ChallengePair
{
    @SuppressWarnings("unused")
    private static final Logger log = Logger.getLogger(ChallengePair.class.getCanonicalName());

    protected ReadOnlyObjectProperty<UUID> challengeid;
    protected IntegerProperty round;
    protected StringProperty announcer;
    protected Entry left, right;

    public class Entry implements ChangeListener<Object>
    {
        protected ObjectProperty<UUID> carid;
        protected DoubleProperty  dial, reaction, sixty, raw;
        protected IntegerProperty cones, gates;
        protected StringProperty  name, status;
        private int course;

        private boolean changelock;  // we only want single events when updating several items at once
        private Driver driver;

        public Entry(int course)
        {
            this.course = course;
            this.carid  = new SimpleObjectProperty<UUID>();
        }

        public String getFirstName()
        {
            return (driver != null) ? driver.getFirstName() : "";
        }

        public boolean hasStatus()
        {
            return (status != null) && !status.get().equals("");
        }

        public boolean isOK()
        {
            return (status != null) && status.get().equals("OK");
        }

        private void init()
        {
            dial     = new SimpleDoubleProperty();
            reaction = new SimpleDoubleProperty();
            sixty    = new SimpleDoubleProperty();
            raw      = new SimpleDoubleProperty();
            cones    = new SimpleIntegerProperty();
            gates    = new SimpleIntegerProperty();
            status   = new SimpleStringProperty("");
            name     = new SimpleStringProperty();

            raw.addListener(this);
            cones.addListener(this);
            gates.addListener(this);
            status.addListener(this);
        }

        private void commitRun()
        {
            if ((challengeid.get() == null) || (carid.get() == null)) return;
            Database.d.setChallengeRun(new ChallengeRun(challengeid.get(), carid.get(), reaction.get(), sixty.get(), raw.get(), round.get(), course, cones.get(), gates.get(), status.get()));
            Messenger.sendEvent(MT.CHALLENGE_RUN_UPDATED, round.get());
        }

        public void set(Driver driver, double dial, UUID carid, ChallengeRun run)
        {
            changelock = true;
            if (this.dial == null)
                init();
            this.driver = driver;
            this.name.set(driver.getFullName());
            this.dial.set(dial);
            this.carid.set(carid);
            if (run != null) {
                reaction.set(run.getReaction());
                sixty.set(run.getSixty());
                raw.set(run.getRaw());
                cones.set(run.getCones());
                gates.set(run.getGates());
                status.set(run.getStatus());
            }
            changelock = false;
            //Messenger.sendEvent(MT.CHALLENGE_RUN_UPDATED, round.get());
        }

        public void clear()
        {
            if (this.dial == null)
                return;
            changelock = true;
            reaction.set(0);
            sixty.set(0);
            raw.set(0);
            cones.set(0);
            gates.set(0);
            status.set("");
            changelock = false;
            commitRun();
        }

        public void timerData(Run r)
        {
            if (reaction == null) { return; } // not a staged side
            changelock = true;
            reaction.set(r.getReaction() < 0 ? 0 : r.getReaction()); // difference between Run object and ChallengeRun object
            sixty.set(r.getSixty() < 0 ? 0 : r.getSixty());
            raw.set(r.getRaw());
            status.set(r.getStatus());
            changelock = false;
            commitRun();
        }

        public void deleteFinish()
        {
            raw.set(0.0);
        }

        @Override
        public void changed(ObservableValue<? extends Object> observable, Object oldValue, Object newValue)
        {
            if (!changelock) {
                commitRun();
            }
        }
    }

    public ChallengePair(UUID cid, int rnd)
    {
        challengeid = new SimpleObjectProperty<UUID>(cid);
        round     = new SimpleIntegerProperty(rnd);
        announcer = new SimpleStringProperty();
        left  = new Entry(1);
        right = new Entry(2);
    }

    public void setLeft(Driver driver, double dialin, UUID carid, ChallengeRun run)
    {
        left.set(driver, dialin, carid, run);
    }

    public void setRight(Driver driver, double dialin, UUID carid, ChallengeRun run)
    {
        right.set(driver, dialin, carid, run);
    }

    public void overrideDial(UUID carid, double dialin)
    {
        if (carid.equals(left.carid.get()))
            left.dial.set(dialin);
        if (carid.equals(right.carid.get()))
            right.dial.set(dialin);
    }

    public void clearData()
    {
        left.clear();
        right.clear();
    }

    public void runData(Run.WithRowId r)
    {
        timerRowId = r.getRowId(); // always set the id here, matched from outside

        if (Double.isNaN(r.getRaw())) {
            if (r.isOK()) r.setStatus("");
            r.setRaw(0);
            if (r.course() == 1) {
                leftReactionFlag = true;
            } else {
                rightReactionFlag = true;
            }
            timestamp = System.currentTimeMillis();
        }

        if (r.course() == 1)
            left.timerData(r);
        else
            right.timerData(r);
    }

    public void delete(Run.WithRowId r)
    {
        if (r.course() == 1) {
            left.deleteFinish();
        } else {
            right.deleteFinish();
        }
    }


    // Tag on state info

    private boolean activeStart;
    private boolean leftReactionFlag, rightReactionFlag;
    private long timestamp;
    protected UUID timerRowId;

    public void makeActiveStart()
    {
        activeStart = true;
        leftReactionFlag = rightReactionFlag = false;
        timerRowId = null;  // We now accept a new row from the timer
    }

    public boolean isActiveStart()
    {
        return activeStart;
    }

    public boolean startComplete()
    {
        if (!activeStart) return false;
        long activity = System.currentTimeMillis() - timestamp;
        return (leftReactionFlag && rightReactionFlag && (activity > 5000)) ||
              ((leftReactionFlag || rightReactionFlag) && (activity > 10000));
    }

    public void deactivateStart()
    {
        activeStart = false;
    }
}