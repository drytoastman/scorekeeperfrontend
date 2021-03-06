package org.wwscc.fxchallenge;

import org.wwscc.storage.Entrant;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public final class DialinEntry
{
    Entrant entrant;
    IntegerProperty position, diffposition;
    DoubleProperty net, dialin, classdiff;
    StringProperty first, last, classCode;
    BooleanProperty selected;

    public DialinEntry()
    {
    }

    public DialinEntry(Entrant e, int position, double net, double dialin, int diffposition, double classdiff)
    {
        this.entrant   = e;
        this.first     = new SimpleStringProperty(e.getFirstName());
        this.last      = new SimpleStringProperty(e.getLastName());
        this.classCode = new SimpleStringProperty(e.getClassCode());
        this.position  = new SimpleIntegerProperty(position);
        this.net       = new SimpleDoubleProperty(net);
        this.dialin    = new SimpleDoubleProperty(dialin);
        this.selected  = new SimpleBooleanProperty(false);

        this.diffposition = new SimpleIntegerProperty(diffposition);
        this.classdiff    = new SimpleDoubleProperty(classdiff);
    }
}