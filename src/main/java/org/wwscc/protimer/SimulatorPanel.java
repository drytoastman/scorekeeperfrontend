/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.wwscc.protimer;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import net.miginfocom.swing.MigLayout;

import org.wwscc.storage.LeftRightDialin;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.TimeTextField;

/**
 * Panel to make sure of the simulator
 */
public class SimulatorPanel extends JFrame
{
    Simulator sim;
    Returno ret;
    InternalListener lis;
    TimeTextField time;
    TimeTextField dial;

    public SimulatorPanel()
    {
        super("Simulator");
        JPanel main = new JPanel(new MigLayout("", "[fill][fill]"));
        main.setOpaque(true);
        main.setBackground(Color.WHITE);
        setContentPane(main);

        sim = new Simulator();
        ret = new Returno();
        lis = new InternalListener();
        time = new TimeTextField("0.000", 6);
        dial = new TimeTextField("0.000", 6);

        JLabel timel = new JLabel("Time");
        timel.setFont(timel.getFont().deriveFont(14).deriveFont(Font.BOLD));
        JLabel diall = new JLabel("Dial");
        diall.setFont(diall.getFont().deriveFont(14).deriveFont(Font.BOLD));

        main.add(timel, "split 2");
        main.add(time, "");
        main.add(diall, "split 2");
        main.add(dial, "wrap");

        main.add(new JSeparator(), "spanx 2, wrap");

        main.add(button("tree"), "spanx 2, wrap");
        main.add(button("reaction left"), "");
        main.add(button("reaction right"), "wrap");
        main.add(button("sixty left"), "");
        main.add(button("sixty right"), "wrap");
        main.add(button("finish left"), "");
        main.add(button("finish right"), "wrap");

        pack();
        setVisible(true);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            public void windowClosed(WindowEvent e) {
                ret.shutdown();
            }
        });
    }

    private JButton button(String s)
    {
        JButton but = new JButton(s);
        but.addActionListener(lis);
        return but;
    }

    class InternalListener implements ActionListener
    {
        @Override
        public void actionPerformed(ActionEvent ae) {
            String s = ae.getActionCommand();
            if (s.equals("tree"))
                sim.tree();
            else if (s.equals("reaction left"))
                sim.reaction(true, time.getTime(), time.getTime() < 0.500 ? ColorTime.REDLIGHT : ColorTime.NORMAL );
            else if (s.equals("reaction right"))
                sim.reaction(false, time.getTime(), time.getTime() < 0.500 ? ColorTime.REDLIGHT : ColorTime.NORMAL);
            else if (s.equals("sixty left"))
                sim.sixty(true, time.getTime(), ColorTime.NORMAL);
            else if (s.equals("sixty right"))
                sim.sixty(false, time.getTime(), ColorTime.NORMAL);
            else if (s.equals("finish left"))
                sim.finish(true, time.getTime(), dial.getTime(), ColorTime.NORMAL);
            else if (s.equals("finish right"))
                sim.finish(false, time.getTime(), dial.getTime(), ColorTime.NORMAL);
        }
    }

    class Returno implements MessageListener
    {
        public Returno()
        {
            Messenger.register(MT.TIMER_SERVICE_DIALIN_L, this);
            Messenger.register(MT.TIMER_SERVICE_DIALIN_R, this);
            Messenger.register(MT.TIMER_SERVICE_DIALIN, this);
        }

        public void shutdown()
        {
            Messenger.unregister(MT.TIMER_SERVICE_DIALIN_L, this);
            Messenger.unregister(MT.TIMER_SERVICE_DIALIN_R, this);
            Messenger.unregister(MT.TIMER_SERVICE_DIALIN, this);
        }

        @Override
        public void event(MT type, Object data)
        {
            switch (type)
            {
                case TIMER_SERVICE_DIALIN_L: Messenger.sendEvent(MT.DIALIN_LEFT,  data); break;
                case TIMER_SERVICE_DIALIN_R: Messenger.sendEvent(MT.DIALIN_RIGHT, data); break;
                case TIMER_SERVICE_DIALIN:
                    LeftRightDialin d = (LeftRightDialin)data;
                    Messenger.sendEvent(MT.DIALIN_LEFT, d.left);
                    Messenger.sendEvent(MT.DIALIN_RIGHT, d.right);
                    break;
            }
        }
    }
}
