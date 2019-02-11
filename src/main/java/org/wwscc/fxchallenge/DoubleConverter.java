package org.wwscc.fxchallenge;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.wwscc.util.NF;

import javafx.util.StringConverter;

public class DoubleConverter extends StringConverter<Double>
{
    @Override
    public String toString(Double d) {
        if (d == null) return "";
        return NF.format(d);
    }
    @Override
    public Double fromString(String s)
    {
        BigDecimal bd = new BigDecimal(s);
        bd = bd.setScale(3, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
