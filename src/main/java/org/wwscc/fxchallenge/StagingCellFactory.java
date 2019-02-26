package org.wwscc.fxchallenge;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.wwscc.util.NF;

import javafx.beans.property.IntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableCell;
import javafx.scene.control.cell.ChoiceBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.Callback;
import javafx.util.StringConverter;

public class StagingCellFactory
{
    public enum CellType { STRING, CONESGATES, TIME, STATUS };
    private static ObservableList<Integer> ConesGatesOptions = FXCollections.observableArrayList(0, 1, 2, 3, 4, 5);
    private static ObservableList<String> StatusOptions = FXCollections.observableArrayList("OK", "RL", "NS", "DNF", "DNS");

    static class DoubleConverter extends StringConverter<Double>
    {
        @Override
        public String toString(Double d) {
            if (d == null) return "";
            return NF.format(d);
        }
        @Override
        public Double fromString(String s)
        {
            if (s == null) return null;
            BigDecimal bd = new BigDecimal(s);
            bd = bd.setScale(3, RoundingMode.HALF_UP);
            return bd.doubleValue();
        }
    }

    @SuppressWarnings("rawtypes")
    static Callback Factory(CellType type, IntegerProperty active, String cssClass)
    {
        return tcolumn -> {
            final TableCell ret;
            switch (type) {
                case CONESGATES:
                    ret = conesGatesCell(active, cssClass);
                    break;
                case TIME:
                    ret = timeCell(active, cssClass);
                    break;
                case STATUS:
                    ret = statusCell(active, cssClass);
                    break;
                default:
                    ret = textCell(active, cssClass);
                    break;
            }

            if (active != null) {
                active.addListener((obs, oldv, newv) -> {
                    if (newv.intValue() == ret.getIndex())
                        ret.getStyleClass().add(cssClass);
                    else
                        ret.getStyleClass().remove(cssClass);
                }
            );}

            return ret;
        };
    }

    static TableCell<ChallengePair,String> textCell(IntegerProperty active, String cssClass)
    {
        return new TextFieldTableCell<ChallengePair, String>() {
            public void updateItem(String t, boolean empty) {
                super.updateItem(t, empty);
                if ((active != null) && (this.getIndex() == active.get())) getStyleClass().add(cssClass);
                else getStyleClass().remove(cssClass);
                setEditable(t != null);
            }
        };
    }

    static TableCell<ChallengePair,Double> timeCell(IntegerProperty active, String cssClass)
    {
        return new TextFieldTableCell<ChallengePair, Double>(new DoubleConverter()) {
            public void updateItem(Double t, boolean empty) {
                super.updateItem(t, empty);
                if ((active != null) && (this.getIndex() == active.get())) getStyleClass().add(cssClass);
                else getStyleClass().remove(cssClass);
                setEditable(t != null);
            }
        };
    }

    static TableCell<ChallengePair,Integer> conesGatesCell(IntegerProperty active, String cssClass)
    {
        return new ChoiceBoxTableCell<ChallengePair, Integer>(ConesGatesOptions) {
            public void updateItem(Integer t, boolean empty) {
                super.updateItem(t, empty);
                if ((active != null) && (this.getIndex() == active.get())) getStyleClass().add(cssClass);
                else getStyleClass().remove(cssClass);
                setEditable(t != null);
            }
        };
    }

    static TableCell<ChallengePair,String> statusCell(IntegerProperty active, String cssClass)
    {
        return new ChoiceBoxTableCell<ChallengePair, String>(StatusOptions) {
            public void updateItem(String t, boolean empty) {
                super.updateItem(t, empty);
                if ((active != null) && (this.getIndex() == active.get())) getStyleClass().add(cssClass);
                else getStyleClass().remove(cssClass);
                setEditable(t != null);
            }
        };
    }
}
