/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.system;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.InetAddress;
import javax.swing.Action;
import javax.swing.FocusManager;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.border.LineBorder;

import org.wwscc.system.sync.MergeServerModel;
import org.wwscc.system.sync.MergeStatusTable;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.Network;
import org.wwscc.util.Prefs;
import net.miginfocom.swing.MigLayout;

public class ScorekeeperStatusWindow extends JFrame
{
    MergeStatusTable activetable, inactivetable;

    public ScorekeeperStatusWindow(Actions actions, MergeServerModel serverModel, boolean hastrayicon)
    {
        super("Scorekeeper Status");

        activetable = new MergeStatusTable(serverModel, true);
        inactivetable = new MergeStatusTable(serverModel, false);

        JPanel content = new JPanel(new MigLayout("fill", "", "[grow 0][fill]"));

        content.add(header("VM"), "split");
        content.add(new StatusLabel(MT.MACHINE_STATUS), "growy, w 150!");
        content.add(header("Backend"), "split");
        content.add(new StatusLabel(MT.BACKEND_STATUS), "growy, w 150!");
        content.add(header("Network"), "split");
        content.add(new NetworkStatusLabel(MT.NETWORK_CHANGED), "growy, w 150!, wrap");

        content.add(new JSeparator(), "growx, wrap");
        content.add(header("Active Hosts"), "split");
        content.add(button(actions.mergeAll), "gapleft 10");
        content.add(button(actions.mergeWith), "gapleft 10");
        content.add(button(actions.downloadSeries), "gapleft 10, wrap");
        content.add(new JScrollPane(activetable), "grow, wrap");

        content.add(new JSeparator(), "growx, wrap");
        content.add(header("Inactive Hosts"), "split");
        content.add(button(actions.clearOld), "gapleft 10, wrap");
        content.add(new JScrollPane(inactivetable), "grow");
        setContentPane(content);

        JMenu file = new JMenu("File");
        file.add(actions.debugRequest);
        file.add(actions.backupRequest);
        file.add(actions.importRequest);
        file.add(new JSeparator());
        file.add(actions.quit);

        JMenu data = new JMenu("Data");
        data.add(actions.addServer);
        data.add(actions.deleteServer);
        data.add(new JSeparator());
        data.add(actions.deleteSeries);

        JMenu remote = new JMenu("Remote");
        remote.add(actions.makeActive);
        remote.add(actions.makeInactive);

        JMenu adv = new JMenu("Advanced");
        adv.add(new JCheckBoxMenuItem(actions.discovery));
        adv.add(actions.resetHash);
        adv.add(actions.initServers);

        JMenu launch = new JMenu("Launch");
        for (Action a : actions.apps)
            launch.add(a);

        JMenuBar bar = new JMenuBar();
        bar.add(file);
        bar.add(data);
        bar.add(remote);
        bar.add(adv);
        bar.add(launch);
        setJMenuBar(bar);

        setBounds(Prefs.getWindowBounds("datasync"));
        Prefs.trackWindowBounds(this, "datasync");

        if (hastrayicon) {
            setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        } else {
            setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            this.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    JOptionPane.showMessageDialog(FocusManager.getCurrentManager().getActiveWindow(),
                            "As the tray icon is not supported on this system, this window cannot be closed."+
                            "It can minimized or you can select shutdown from the File menu",
                            "Unsupported Operation", JOptionPane.WARNING_MESSAGE);
                }
            });
        }

        Messenger.register(MT.OPEN_STATUS_REQUEST, (t,o) -> { setVisible(true); toFront(); });
    }

    private JLabel header(String s)
    {
        JLabel header = new JLabel(s);
        header.setFont(header.getFont().deriveFont(18.0f).deriveFont(Font.BOLD));
        return header;
    }

    private JButton button(Action a)
    {
        JButton button = new JButton(a);
        button.setFont(button.getFont().deriveFont(11.0f));
        return button;
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
            setFont(getFont().deriveFont(13.0f));
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
}
