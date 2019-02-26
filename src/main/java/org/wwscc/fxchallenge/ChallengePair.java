package org.wwscc.fxchallenge;

import java.util.UUID;

import org.wwscc.storage.ChallengeRun;
import org.wwscc.storage.Database;
import org.wwscc.storage.Run;

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
        private boolean initializing;

        public Entry(int course)
        {
            this.course = course;
            this.carid  = new SimpleObjectProperty<UUID>();
        }

        private void init()
        {
            dial     = new SimpleDoubleProperty();
            reaction = new SimpleDoubleProperty();
            sixty    = new SimpleDoubleProperty();
            raw      = new SimpleDoubleProperty();
            cones    = new SimpleIntegerProperty();
            gates    = new SimpleIntegerProperty();
            name     = new SimpleStringProperty();
            status   = new SimpleStringProperty("");

            //carid.addListener(this);
            dial.addListener(this);
            reaction.addListener(this);
            sixty.addListener(this);
            raw.addListener(this);
            cones.addListener(this);
            gates.addListener(this);
            //name.addListener(this);
            status.addListener(this);
        }

        public void set(String name, double dial, UUID carid, ChallengeRun run)
        {
            initializing = true;
            if (this.dial == null)
                init();
            this.name.set(name);
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
            initializing = false;
        }

        public void timerData(Run r)
        {
            reaction.set(r.getReaction());
            sixty.set(r.getSixty());
            raw.set(r.getRaw());
            status.set(r.getStatus());
        }

        @Override
        public void changed(ObservableValue<? extends Object> observable, Object oldValue, Object newValue)
        {
            if (!initializing)
                Database.d.setChallengeRun(new ChallengeRun(challengeid.get(), carid.get(), reaction.get(), sixty.get(), raw.get(), round.get(), course, cones.get(), gates.get(), status.get()));
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

    public void setLeft(String name, double dialin, UUID carid, ChallengeRun run)
    {
        left.set(name, dialin, carid, run);
    }

    public void setRight(String name, double dialin, UUID carid, ChallengeRun run)
    {
        right.set(name, dialin, carid, run);
    }

    public void timerData(Run r)
    {
        if (r.course() == 1) {
            left.timerData(r);
        } else {
            right.timerData(r);
        }
    }
}