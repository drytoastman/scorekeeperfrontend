/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.wwscc.protimer;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;

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
    JTextField text;
    TimeTextField reacl, sixtyl, timel;
    TimeTextField reacr, sixtyr, timer;
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
        text   = new JTextField();
        reacl  = new TimeTextField("0.500",  6);
        sixtyl = new TimeTextField("2.100",  6);
        timel  = new TimeTextField("30.000", 6);
        reacr  = new TimeTextField("0.600",  6);
        sixtyr = new TimeTextField("2.200",  6);
        timer  = new TimeTextField("31.000", 6);
        dial   = new TimeTextField("0.000",  6);

        main.add(text, "spanx 3");
        main.add(button("text"), "wrap");        
        main.add(button("tree"), "spanx 4, wrap");

        main.add(reacl, "");
        main.add(button("reaction left"), "");
        main.add(reacr, "");
        main.add(button("reaction right"), "wrap");
        main.add(sixtyl, "");
        main.add(button("sixty left"), "");
        main.add(sixtyr, "");
        main.add(button("sixty right"), "wrap");
        main.add(timel, "");
        main.add(button("finish left"), "");
        main.add(timer, "");
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
            switch (s) {
                case "text":
                    sim.text(text.getText());
                    break;
                case "tree": 
                    sim.tree(); 
                    break;
                case "reaction left":
                    sim.reaction(true, reacl.getTime(), reacl.getTime() < 0.500 ? "redlight" : "" );
                    break;
                case "reaction right":
                    sim.reaction(false, reacr.getTime(), reacr.getTime() < 0.500 ? "redlight" : "" );
                    break;
                case "sixty left":
                    sim.sixty(true, sixtyl.getTime());
                    break;
                case "sixty right":
                    sim.sixty(false, sixtyr.getTime());
                    break;
                case "finish left":
                    sim.finish(true, timel.getTime(), dial.getTime());
                    break;
                case "finish right":
                    sim.finish(false, timer.getTime(), dial.getTime());
                    break;
            }
        }
    }

    class Returno implements MessageListener
    {
        public Returno()
        {
            Messenger.register(MT.TIMER_SERVICE_DIALIN_L, this);
            Messenger.register(MT.TIMER_SERVICE_DIALIN_R, this);
            Messenger.register(MT.TIMER_SERVICE_DIALIN, this);
            Messenger.register(MT.INPUT_DELETE_FINISH_LEFT, this);
            Messenger.register(MT.INPUT_DELETE_FINISH_RIGHT, this);
        }

        public void shutdown()
        {
            Messenger.unregister(MT.TIMER_SERVICE_DIALIN_L, this);
            Messenger.unregister(MT.TIMER_SERVICE_DIALIN_R, this);
            Messenger.unregister(MT.TIMER_SERVICE_DIALIN, this);
            Messenger.unregister(MT.INPUT_DELETE_FINISH_LEFT, this);
            Messenger.unregister(MT.INPUT_DELETE_FINISH_RIGHT, this);
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
                case INPUT_DELETE_FINISH_LEFT: Messenger.sendEvent(MT.DELETE_FINISH_LEFT, data); break;
                case INPUT_DELETE_FINISH_RIGHT: Messenger.sendEvent(MT.DELETE_FINISH_RIGHT, data); break;
            }
        }
    }
}
