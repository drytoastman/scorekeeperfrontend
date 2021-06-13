/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2012 Brett Wilson.
 * All rights reserved.
 */


package org.wwscc.protimer;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import net.miginfocom.swing.MigLayout;
import org.wwscc.components.MyServerLabel;
import org.wwscc.timercomm.TimerServer;
import org.wwscc.util.Discovery;
import org.wwscc.util.AppSetup;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.SerialPortUtil;


public class ProSoloInterface extends JFrame implements ActionListener, MessageListener
{
    private static Logger log = Logger.getLogger(ProSoloInterface.class.getCanonicalName());

    protected DebugPane debug;
    protected ResultsModel model;
    protected ResultsPane results;
    protected AuditLog audit;
    protected TimingInterface timing;
    protected TimerServer server;

    protected DialinPane dialins;
    protected JLabel openPort;

    public ProSoloInterface() throws Exception
    {
        super("NWR ProSolo Interface");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new MigLayout("fill, ins 0", "fill", "[grow 0][grow 0][grow 0][fill][grow 0][grow 0]"));

        debug = new DebugPane();
        model = new ResultsModel();

        results = new ResultsPane(model);
        audit = new AuditLog();
        dialins = new DialinPane();

        Messenger.register(MT.SERIAL_PORT_OPEN, this);
        Messenger.register(MT.SERIAL_PORT_CLOSED, this);

        createMenus();
        add(dialins, "wrap, hidemode 2");
        add(new NipErrorPanel(), "wrap, hidemode 2");
        add(new StopErrorPanel(), "wrap, hidemode 2");

        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Results", null, results, "results interface");
        tp.addTab("Debug", null, debug, "shows serial conversation");
        add(tp, "grow, wrap");

        add(createButtonPanel(), "wrap");

        openPort = new JLabel("Serial Port Not Connected");
        openPort.setHorizontalAlignment(JLabel.CENTER);
        openPort.setBorder(BorderFactory.createLoweredBevelBorder());

        MyServerLabel slbl = new MyServerLabel();
        slbl.setBorder(BorderFactory.createLoweredBevelBorder());

        JPanel bottom = new JPanel(new MigLayout("fill, ins 0", "[50%]0[50%]"));
        bottom.add(openPort, "grow");
        bottom.add(slbl, "grow");

        add(bottom, "wrap");

        setSize(1024, 768);
        setVisible(true);

        dialins.doFocus(this);
        timing = new TimingInterface();

        try {
            server = new TimerServer(Discovery.PROTIMER_TYPE);
            model.addRunServerListener(server);
            server.start();
        } catch (IOException ioe) {
            log.log(Level.SEVERE, "\bTimer Server Failed to start: {0}", ioe.getMessage());
        }
    }


    public void createMenus()
    {
        JMenu file = new JMenu("File");
        file.add(createMenu("Quit", KeyEvent.VK_Q, ActionEvent.CTRL_MASK));

        JMenu mode = new JMenu("Timer");
        mode.add(createMenu("Open Comm Port", KeyEvent.VK_O, ActionEvent.CTRL_MASK));
        mode.add(createMenu("Show/Hide Dialin", KeyEvent.VK_D, ActionEvent.CTRL_MASK));
        mode.add(createMenu("Delete Left Finish", KeyEvent.VK_Z, ActionEvent.CTRL_MASK));
        mode.add(createMenu("Delete Right Finish", KeyEvent.VK_SLASH, ActionEvent.CTRL_MASK));
        mode.add(createMenu("Set Run Mode", 0, 0));
        mode.add(createMenu("Set Align Mode", 0, 0));
        mode.add(createMenu("Soft Reset", 0, 0));
        mode.add(createMenu("Hard Reset", 0, 0));

        JMenu other = new JMenu("Other");
        other.add(createMenu("Simulator",  KeyEvent.VK_S, ActionEvent.CTRL_MASK));

        JMenuBar bar = new JMenuBar();
        bar.add(file);
        bar.add(mode);
        bar.add(other);
        setJMenuBar(bar);
    }


    public JPanel createButtonPanel()
    {
        JPanel panel = new JPanel(new MigLayout("ins 5, gap 0, fill", "[grow 33][grow 100][grow 33][grow 0]"));
        panel.add(new TimingButtons(true), "growx");
        panel.add(new OpenStatus(), "grow");
        panel.add(new TimingButtons(false), "growx");
        panel.add(new JLabel(""), "w 10!");
        return panel;
    }


    protected JMenuItem createMenu(String title, int key, int modifier)
    {
        JMenuItem item = new JMenuItem(title);
        if (key != 0) item.setAccelerator(KeyStroke.getKeyStroke(key, modifier));
        item.addActionListener(this);
        return item;
    }


    @Override
    public void actionPerformed(ActionEvent e)
    {
        String com = e.getActionCommand();

        if (com.equals("Set Run Mode")) { Messenger.sendEvent(MT.INPUT_SET_RUNMODE, null); }
        else if (com.equals("Set Align Mode")) { Messenger.sendEvent(MT.INPUT_SET_ALIGNMODE, null); }
        else if (com.equals("Show In Progress")) { Messenger.sendEvent(MT.INPUT_SHOW_INPROGRESS, null); }
        else if (com.equals("Show State")) { Messenger.sendEvent(MT.INPUT_SHOW_STATE, null); }

        else if (com.equals("Delete Left Finish")) { Messenger.sendEvent(MT.INPUT_DELETE_FINISH_LEFT, null); }
        else if (com.equals("Delete Right Finish")) { Messenger.sendEvent(MT.INPUT_DELETE_FINISH_RIGHT, null); }

        else if (com.equals("Soft Reset")) { Messenger.sendEvent(MT.INPUT_RESET_SOFT, null); }
        else if (com.equals("Hard Reset")) { Messenger.sendEvent(MT.INPUT_RESET_HARD, null); }
        else if (com.equals("Simulator")) { new SimulatorPanel(); }

        else if (com.startsWith("Show/Hide"))
        {
            dialins.setVisible(!dialins.isVisible());
        }
        else if (com.startsWith("Open Comm"))
        {
            String port;
            if ((port = SerialPortUtil.userPortSelection()) != null)
                timing.openPort(port);
        }
        else if (com.equals("Quit"))
        {
            System.exit(0);
        }

    }


    @Override
    public void event(MT type, Object o)
    {
        switch (type)
        {
            case SERIAL_PORT_OPEN:
                openPort.setText("Connected to Port " + o);
                openPort.setForeground(Color.BLACK);
                break;
            case SERIAL_PORT_CLOSED:
                openPort.setText("Serial Port Not Connected");
                openPort.setForeground(Color.RED);
                break;
            default:
                break;
        }

        validate();
        repaint();
    }


    public static void main(String args[])
    {
        try
        {
            AppSetup.appSetup("prointerface");
            final ProSoloInterface frame = new ProSoloInterface();
            frame.addWindowListener(new WindowAdapter() {
                public void windowOpened(WindowEvent e) {
                    frame.actionPerformed(new ActionEvent(frame, 1, "Open Comm"));
                }
            });
        }
        catch (Throwable e)
        {
            log.log(Level.SEVERE, "\bProTimer thread died: " + e, e);
        }
    }
}

