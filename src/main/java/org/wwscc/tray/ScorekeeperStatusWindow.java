/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.tray;

import java.awt.Font;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;

import org.wwscc.storage.Database;
import org.wwscc.util.AppSetup;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;

import net.miginfocom.swing.MigLayout;

public class ScorekeeperStatusWindow extends JFrame implements MessageListener
{
    MergeServerModel model;
    MergeStatusTable activetable, inactivetable;
    JLabel backendStatus, machineStatus;

    public ScorekeeperStatusWindow(Actions actions)
    {
        super("Scorekeeper Status");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        model = new MergeServerModel();
        activetable = new MergeStatusTable(model, true);
        inactivetable = new MergeStatusTable(model, false);

        backendStatus = new JLabel("backend");
        machineStatus = new JLabel("machine");
        Messenger.register(MT.MACHINE_STATUS, (t, data) -> machineStatus.setText((String)data));
        Messenger.register(MT.BACKEND_STATUS, (t, data) -> backendStatus.setText((String)data));

        JPanel content = new JPanel(new MigLayout("fill", "", "[grow 0][fill]"));

        content.add(header("Status"), "split");
        content.add(machineStatus, "split");
        content.add(backendStatus, "wrap");

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


        JMenu data = new JMenu("Data");
        data.add(actions.deleteSeries);
        data.add(actions.debugRequest);
        data.add(actions.importRequest);

        JMenu adv = new JMenu("Advanced");
        adv.add(new JCheckBoxMenuItem(actions.discovery));
        adv.add(actions.resetHash);

        JMenuBar bar = new JMenuBar();
        bar.add(data);
        bar.add(adv);
        setJMenuBar(bar);

        setBounds(Prefs.getWindowBounds("datasync"));
        Prefs.trackWindowBounds(this, "datasync");
        Messenger.register(MT.DATABASE_NOTIFICATION, this);
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

    @SuppressWarnings("unchecked")
    @Override
    public void event(MT type, Object data)
    {
        if (type == MT.DATABASE_NOTIFICATION)
        {
            Set<String> tables = (Set<String>)data;
            if (tables.contains("mergeservers")) {
                model.setData(Database.d.getMergeServers());
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
        Database.openPublic(true);
        ScorekeeperStatusWindow v = new ScorekeeperStatusWindow(new Actions());
        v.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        v.setVisible(true);
        Database.openPublic(true);
        v.model.setData(Database.d.getMergeServers());
        while (true)
        {
            Thread.sleep(2000);
        }
    }
}
