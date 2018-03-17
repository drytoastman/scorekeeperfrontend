/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system;

import java.awt.Color;
import java.awt.Container;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.InetAddress;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.border.LineBorder;

import org.wwscc.util.AppLogLevel;
import org.wwscc.util.AppSetup;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.Network;
import org.wwscc.util.Prefs;

import net.miginfocom.swing.MigLayout;

public class ScorekeeperStatusWindow extends JFrame
{
    private static final Logger log = Logger.getLogger(ScorekeeperStatusWindow.class.getName());

    MergeStatusTable activetable, inactivetable;
    Map<String, JLabel> labels;
    Map<String, JButton> buttons;
    MiniMaxiAction minimaxi;

    public ScorekeeperStatusWindow(Actions actions, MergeServerModel serverModel)
    {
        super("Scorekeeper Status");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                Messenger.sendEvent(MT.SHUTDOWN_REQUEST, null);
            }
        });

        activetable   = new MergeStatusTable(serverModel, true);
        inactivetable = new MergeStatusTable(serverModel, false);
        labels = new HashMap<String, JLabel>();
        buttons = new HashMap<String, JButton>();
        minimaxi = new MiniMaxiAction();

        labels.put("machinestatus", new StatusLabel(MT.MACHINE_STATUS));
        labels.put("backendstatus", new StatusLabel(MT.BACKEND_STATUS));
        labels.put("networkstatus", new NetworkStatusLabel(MT.NETWORK_CHANGED));

        buttons.put("minimaxi",  button(minimaxi));
        buttons.put("mergeall",  button(actions.mergeAll));
        buttons.put("mergewith", button(actions.mergeWith));
        buttons.put("download",  button(actions.downloadSeries));
        buttons.put("clearold",  button(actions.clearOld));

        JMenu file = new JMenu("File");
        file.add(actions.debugRequest);
        file.add(actions.backupRequest);
        file.add(actions.importRequest);
        file.add(actions.deleteSeries);
        file.add(new JSeparator());
        file.add(actions.quit);

        JMenu servers = new JMenu("Servers");
        servers.add(actions.makeActive);
        servers.add(actions.makeInactive);
        servers.add(actions.addServer);
        servers.add(actions.deleteServer);
        servers.add(new JSeparator());
        servers.add(new JCheckBoxMenuItem(actions.discovery));

        JMenu debug = new JMenu("Debug");
        debug.add(actions.resetHash);
        debug.add(actions.initServers);
        JMenu levels = new JMenu("Logging Level");
        debug.add(levels);

        ButtonGroup options = new ButtonGroup();
        AppLogLevel current = Prefs.getLogLevel();
        LogLevelChanges levelchange = new LogLevelChanges();

        for (AppLogLevel.ALevel a : AppLogLevel.ALevel.values()) {
            JRadioButtonMenuItem m = new JRadioButtonMenuItem(a.name());
            m.addActionListener(levelchange);
            options.add(m);
            levels.add(m);
            if (a.equals(current.getLevel())) {
                m.setSelected(true);
            }
        }

        JMenu launch = new JMenu("Launch");
        for (Action a : actions.apps)
            launch.add(a);

        JMenuBar bar = new JMenuBar();
        bar.add(file);
        bar.add(launch);
        bar.add(servers);
        bar.add(debug);
        setJMenuBar(bar);

        statusLayout(false);
        // Don't use Prefs.track as we want to track the mini and max differently
        addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) { setBounds(e.getComponent().getBounds()); }
            public void componentMoved(ComponentEvent e)   { setBounds(e.getComponent().getBounds()); }
            private void setBounds(Rectangle current)
            {
                if (minimaxi.isMini) {  // just translate, save previous height/width
                    Rectangle prev = Prefs.getWindowBounds("statuswindow");
                    current.width = prev.width;
                    current.height = prev.height;
                }

                Prefs.setWindowBounds("statuswindow", current);
            }
        });

        Messenger.register(MT.OPEN_STATUS_REQUEST, (t,o) -> { setVisible(true); toFront(); });
    }


    class LogLevelChanges implements ActionListener
    {
        @Override
        public void actionPerformed(ActionEvent e) {
            AppLogLevel newl = new AppLogLevel(e.getActionCommand());
            AppLogLevel curl = Prefs.getLogLevel();
            Prefs.setLogLevel(newl);
            Logger.getLogger("org.wwscc").setLevel(newl.getJavaLevel());
            log.warning("Log level changed from " + curl.getName() + " to " + newl.getName());
        }
    }


    private void statusLayout(boolean mini)
    {
        Container content = getContentPane();
        content.removeAll();

        if (mini) {
            content.setLayout(new MigLayout("fill, ins 5, gap 5", "[al right][grow,fill]"));

            content.add(header("VM", 14), "");
            content.add(labels.get("machinestatus"), "growy, wmin 150, wrap");
            content.add(header("Backend", 14), "");
            content.add(labels.get("backendstatus"), "growy, wrap");
            content.add(header("Network", 14), "");
            content.add(labels.get("networkstatus"), "growy, wrap");
            content.add(new JLabel(""), "");
            content.add(buttons.get("minimaxi"), "al right, wrap");

            pack();
            setResizable(false);
        } else {
            content.setLayout(new MigLayout("fill, ins 5, gap 5", "", "[grow 0][fill]"));

            content.add(header("VM", 14), "split");
            content.add(labels.get("machinestatus"), "growy, w 150!");
            content.add(header("Backend", 14), "split");
            content.add(labels.get("backendstatus"), "growy, w 150!");
            content.add(header("Network", 14), "split");
            content.add(labels.get("networkstatus"), "growy, w 150!");
            content.add(new JLabel(""), "pushx 100, growx 100");
            content.add(buttons.get("minimaxi"), "wmin 80, wrap");

            content.add(new JSeparator(), "growx, wrap");
            content.add(header("Active Hosts", 16), "split");
            content.add(buttons.get("mergeall"),  "gapleft 10");
            content.add(buttons.get("mergewith"), "gapleft 10");
            content.add(buttons.get("download"),  "gapleft 10, wrap");
            content.add(new JScrollPane(activetable), "grow, wrap");

            content.add(new JSeparator(), "growx, wrap");
            content.add(header("Inactive Hosts", 16), "split");
            content.add(buttons.get("clearold"), "gapleft 10, wrap");
            content.add(new JScrollPane(inactivetable), "grow");

            setBounds(Prefs.getWindowBounds("statuswindow"));
            validate();
            repaint();
            setResizable(true);
        }
    }

    private JLabel header(String s, float size)
    {
        JLabel header = new JLabel(s);
        header.setFont(header.getFont().deriveFont(size).deriveFont(Font.BOLD));
        return header;
    }

    private JButton button(Action a)
    {
        JButton button = new JButton(a);
        button.setFont(button.getFont().deriveFont(11.0f));
        return button;
    }

    class MiniMaxiAction extends AbstractAction
    {
        boolean isMini;
        public MiniMaxiAction() {
            super("Mini");
            isMini = false;
        }
        public void actionPerformed(ActionEvent e) {
            isMini = !isMini;
            putValue(Action.NAME, isMini ? "Full" : "Mini"); // name represents next state
            statusLayout(isMini);
        }
    }

    class StatusLabel extends JLabel implements MessageListener
    {
        Color okbg = new Color(200, 255, 200);
        Color okfg = new Color(  0,  80,   0);
        Color notokbg = new Color(255, 200, 200);
        Color notokfg = new Color( 80,   0,   0);
        Color nnbg = new Color(200, 200, 200);
        Color nnfg = new Color( 70,  70,  70);

        public StatusLabel(MT e)
        {
            super();
            Messenger.register(e, this);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(getFont().deriveFont(12.0f));
            setOpaque(true);
            setBackground(notokbg);
            setForeground(notokfg);
            setBorder(new LineBorder(Color.GRAY, 1));
        }

        @Override
        public void event(MT type, Object data)
        {
            String txt = (String)data;
            setText(txt);
            if (txt.equals("Running")) {
                setBackground(okbg);
                setForeground(okfg);
            } else if (txt.equalsIgnoreCase("Not Needed")) {
                setBackground(nnbg);
                setForeground(nnfg);
            } else {
                setBackground(notokbg);
                setForeground(notokfg);
            }
        }
    }

    class NetworkStatusLabel extends StatusLabel
    {
        public NetworkStatusLabel(MT e)
        {
            super(e);
            event(e, Network.getPrimaryAddress());
        }

        @Override
        public void event(MT type, Object data)
        {
            if (data instanceof InetAddress) {
                setBackground(okbg);
                setForeground(okfg);
                setText(((InetAddress)data).getHostAddress());
            } else if (data == null) {
                setBackground(notokbg);
                setForeground(notokfg);
                setText("network down");
            }
        }
    }


    /**
     * A main interface for testing datasync interface by itself
     * @param args ignored
     * @throws InterruptedException ignored
     * @throws NoSuchAlgorithmException ignored
     */
    public static void main(String[] args) throws InterruptedException, NoSuchAlgorithmException
    {
        AppSetup.appSetup("statuswindow");
        ScorekeeperStatusWindow window = new ScorekeeperStatusWindow(new Actions(), new MergeServerModel());
        window.setVisible(true);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }
}
