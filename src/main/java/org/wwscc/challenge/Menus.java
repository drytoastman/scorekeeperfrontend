/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.challenge;


import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

import org.wwscc.dialogs.OpenSeriesAction;
import org.wwscc.storage.Challenge;
import org.wwscc.storage.Database;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;


public class Menus extends JMenuBar implements ActionListener
{
    private static final Logger log = Logger.getLogger(Menus.class.getCanonicalName());

    Map <String,JMenuItem> items;
    JFileChooser chooser;

    public Menus()
    {
        items = new HashMap <String,JMenuItem>();
        chooser = new JFileChooser();

        /* File Menu */
        JMenu file = new JMenu("File");
        add(file);
        file.add(new OpenSeriesAction(ChallengeGUI.watch));
        file.add(createItem("Print Bracket", KeyEvent.VK_P));
        file.addSeparator();
        file.add(createItem("Quit", KeyEvent.VK_Q));

        JMenu chl = new JMenu("Challenge");
        add(chl);
        chl.add(createItem("New Challenge", KeyEvent.VK_N));
        chl.add(createItem("Edit Challenge", KeyEvent.VK_E));
        chl.add(createItem("Delete Challenge", KeyEvent.VK_D));
        chl.add(createItem("Auto Load Current", KeyEvent.VK_L));

        JMenu timer = new JMenu("Timer");
        add(timer);
        timer.add(createItem("Connect", KeyEvent.VK_C));
    }

    protected final JMenuItem createItem(String title, Integer key)
    {
        JMenuItem item = new JMenuItem(title);
        if (key != null)
            item.setAccelerator(KeyStroke.getKeyStroke(key, ActionEvent.CTRL_MASK));
        item.addActionListener(this);

        items.put(title, item);
        return item;
    }

    protected void createChallenge()
    {
        NewChallengeDialog d = new NewChallengeDialog();
        d.doDialog("New Challenge", null);
        if (!d.isValid())
            return;

        try {
            Challenge newc = Database.d.newChallenge(ChallengeGUI.state.getCurrentEventId(), d.getChallengeName(), d.getChallengeSize());
            ChallengeGUI.state.setCurrentChallengeId(newc.getChallengeId());
            Messenger.sendEvent(MT.NEW_CHALLENGE, newc.getChallengeId());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    protected void editChallenge()
    {
        UUID curid = ChallengeGUI.state.getCurrentChallengeId();
        for (Challenge c : Database.d.getChallengesForEvent(ChallengeGUI.state.getCurrentEventId()))
        {
            if (c.getChallengeId().equals(curid))
            {
                String response = (String)JOptionPane.showInputDialog(this, "Edit Challenge", c.getName());
                if (response != null)
                {
                    c.setName(response);
                    Database.d.updateChallenge(c);
                    Messenger.sendEvent(MT.NEW_CHALLENGE, c);
                }
            }
        }
    }

    protected void deleteChallenge()
    {
        List<Challenge> current = Database.d.getChallengesForEvent(ChallengeGUI.state.getCurrentEventId());
        Challenge response = (Challenge)JOptionPane.showInputDialog(this, "Delete which challenge?", "Delete Challenge", JOptionPane.QUESTION_MESSAGE, null, current.toArray(), null);
        if (response == null)
            return;

        int answer = JOptionPane.showConfirmDialog(this, "Are you sure you with to delete " + response + ".  All current activity will be 'lost'", "Confirm Delete", JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.OK_OPTION)
        {
            Database.d.deleteChallenge(response.getChallengeId());
            Messenger.sendEvent(MT.CHALLENGE_DELETED, null);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        String cmd = e.getActionCommand();

        if (cmd.equals("Quit"))
        {
            System.exit(0);
        }
        else if (cmd.equals("Print Bracket"))
        {
            Messenger.sendEvent(MT.PRINT_BRACKET, chooser.getSelectedFile());
        }
        else if (cmd.equals("Auto Load Current"))
        {
            Messenger.sendEvent(MT.PRELOAD_MENU, null);
        }
        else if (cmd.equals("New Challenge"))
        {
            createChallenge();
        }
        else if (cmd.equals("Edit Challenge"))
        {
            editChallenge();
        }
        else if (cmd.equals("Delete Challenge"))
        {
            deleteChallenge();
        }
        else if (cmd.equals("Connect"))
        {
            Messenger.sendEvent(MT.CONNECT_REQUEST, null);
        }
        else
        {
            log.log(Level.INFO, "Unknown command from menubar: {0}", cmd);
        }
    }
}

