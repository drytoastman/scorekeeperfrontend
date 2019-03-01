package org.wwscc.fxchallenge;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.wwscc.util.NF;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableCell;
import javafx.scene.control.cell.ChoiceBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.Callback;
import javafx.util.StringConverter;

public class StagingCells
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
    static Callback Factory(CellType type, String startClass, String finishClass)
    {
        return tcolumn -> {
            final TableCell ret;
            switch (type) {
                case CONESGATES:
                    ret = conesGatesCell(startClass, finishClass);
                    break;
                case TIME:
                    ret = timeCell(startClass, finishClass);
                    break;
                case STATUS:
                    ret = statusCell(startClass, finishClass);
                    break;
                default:
                    ret = textCell(startClass, finishClass);
                    break;
            }

            /*  We end up having to call refresh anyhow, so don't bother with listeners */
            return ret;
        };
    }

    static void updateCss(TableCell<ChallengePair,? extends Object> cell, String startClass, String finishClass)
    {
        ChallengePair pair = (ChallengePair)cell.getTableRow().getItem();
        if ((pair != null) && pair.isActiveStart())
            cell.getStyleClass().add(startClass);
        else
            cell.getStyleClass().remove(startClass);

        if ((pair != null) && pair.isActiveFinish())
            cell.getStyleClass().add(finishClass);
        else
            cell.getStyleClass().remove(finishClass);
    }


    static TableCell<ChallengePair,String> textCell(String startClass, String finishClass)
    {
        return new TextFieldTableCell<ChallengePair, String>() {
            public void updateItem(String t, boolean empty) {
                super.updateItem(t, empty);
                updateCss(this, startClass, finishClass);
                setEditable(t != null);
            }
        };
    }

    static TableCell<ChallengePair,Double> timeCell(String startClass, String finishClass)
    {
        return new TextFieldTableCell<ChallengePair, Double>(new DoubleConverter()) {
            public void updateItem(Double t, boolean empty) {
                super.updateItem(t, empty);
                updateCss(this, startClass, finishClass);
                setEditable(t != null);
            }
        };
    }

    static TableCell<ChallengePair,Integer> conesGatesCell(String startClass, String finishClass)
    {
        return new ChoiceBoxTableCell<ChallengePair, Integer>(ConesGatesOptions) {
            public void updateItem(Integer t, boolean empty) {
                super.updateItem(t, empty);
                updateCss(this, startClass, finishClass);
                setEditable(t != null);
            }
        };
    }

    static TableCell<ChallengePair,String> statusCell(String startClass, String finishClass)
    {
        return new ChoiceBoxTableCell<ChallengePair, String>(StatusOptions) {
            public void updateItem(String t, boolean empty) {
                super.updateItem(t, empty);
                updateCss(this, startClass, finishClass);
                setEditable(t != null);
            }
        };
    }
}
