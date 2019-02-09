package org.wwscc.fxchallenge;

import java.util.UUID;

import org.wwscc.storage.ChallengeRun;
import org.wwscc.storage.Database;

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
    protected Entry left, right;

    public class Entry implements ChangeListener<Object>
    {
        protected ObjectProperty<UUID> carid;
        protected DoubleProperty  dial, reaction, sixty, raw;
        protected IntegerProperty cones, gates;
        protected StringProperty  name, status;
        private int course;

        public Entry(int course)
        {
            carid    = new SimpleObjectProperty<UUID>();
            dial     = new SimpleDoubleProperty();
            reaction = new SimpleDoubleProperty();
            sixty    = new SimpleDoubleProperty();
            raw      = new SimpleDoubleProperty();
            cones    = new SimpleIntegerProperty();
            gates    = new SimpleIntegerProperty();
            name     = new SimpleStringProperty("Piotr ScarchoasdfdAs");
            status   = new SimpleStringProperty("OK");

            this.course = course;

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

        @Override
        public void changed(ObservableValue<? extends Object> observable, Object oldValue, Object newValue)
        {
            Database.d.setChallengeRun(new ChallengeRun(challengeid.get(), carid.get(), reaction.get(), sixty.get(), raw.get(), round.get(), course, cones.get(), gates.get(), status.get()));
        }
    }

    public ChallengePair()
    {
        challengeid = new SimpleObjectProperty<UUID>();
        round = new SimpleIntegerProperty(42);
        left  = new Entry(1);
        right = new Entry(2);
    }
}