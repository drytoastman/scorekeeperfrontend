package org.wwscc.fxchallenge;

import org.wwscc.storage.Entrant;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

final class ChallengeEntry
{
    Entrant entrant;
    IntegerProperty position;
    DoubleProperty net, dialin;
    StringProperty first, last, classCode;

    //Ids.Location location;

    public ChallengeEntry(Entrant e, int position, double net, double dialin)
    {
        this.entrant   = e;
        this.first     = new SimpleStringProperty(e.getFirstName());
        this.last      = new SimpleStringProperty(e.getLastName());
        this.classCode = new SimpleStringProperty(e.getClassCode());
        this.position  = new SimpleIntegerProperty(position);
        this.net       = new SimpleDoubleProperty(net);
        this.dialin    = new SimpleDoubleProperty(dialin);

        //this.location     = null;
    }

    public void XsetLocation(Ids.Location location)
    {
        //this.location = location;
    }
}