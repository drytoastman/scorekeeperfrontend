package org.wwscc.util;

import javax.swing.JLabel;
import javax.swing.Timer;

public class TimedLabel extends JLabel
{
    Timer timer;

    public TimedLabel(int ms)
    {
        super("");
        timer = new Timer(ms, e -> { setText(""); });
    }

    @Override
    public void setText(String s)
    {
        super.setText(s);
        if (timer != null)
            timer.restart();
    }
}
