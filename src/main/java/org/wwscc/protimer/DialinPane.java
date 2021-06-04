/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */


package org.wwscc.protimer;

import java.awt.Component;
import java.awt.Container;
import java.awt.FocusTraversalPolicy;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.text.AbstractDocument;

import org.wwscc.storage.LeftRightDialin;
import org.wwscc.util.EasyNumFilter;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.NF;

import net.miginfocom.swing.MigLayout;

public class DialinPane extends JPanel implements ActionListener, MessageListener
{
    private JLabel leftDial;
    private JLabel rightDial;
    private JTextField leftField;
    private JTextField rightField;
    private JButton set;

    public DialinPane()
    {
        super(new MigLayout("ins 0, al center"));

        Font f = new Font("sansserif", Font.BOLD, 18);

        Messenger.register(MT.DIALIN_LEFT, this);
        Messenger.register(MT.DIALIN_RIGHT, this);
        Messenger.register(MT.INPUT_RESET_SOFT, this);
        Messenger.register(MT.INPUT_RESET_HARD, this);
        Messenger.register(MT.SIXTY_LEFT, this);
        Messenger.register(MT.SIXTY_RIGHT, this);

        JLabel leftLbl = new JLabel("Left Dial-in");
        JLabel rightLbl = new JLabel("Right Dial-in");

        leftDial = new JLabel("      ");
        leftField = new JTextField(6);
        ((AbstractDocument)leftField.getDocument()).setDocumentFilter(new EasyNumFilter(3, 3));

        rightDial = new JLabel("      ");
        rightField = new JTextField(6);
        ((AbstractDocument)rightField.getDocument()).setDocumentFilter(new EasyNumFilter(3, 3));

        set = new JButton("Set");
        set.addActionListener(this);

        leftLbl.setFont(f);
        leftDial.setFont(f);

        rightLbl.setFont(f);
        rightDial.setFont(f);

        add(leftLbl, "");
        add(leftDial, "w 60!");
        add(leftField, "");
        add(set, "");
        add(rightField, "");
        add(rightDial, "w 60!");
        add(rightLbl, "");

    }

    public void doFocus(JFrame f)
    {
        f.setFocusTraversalPolicy(new DialinFocus());
    }

    class DialinFocus extends FocusTraversalPolicy
    {
        public Component getComponentAfter(Container focusCycleRoot, Component aComponent)
        {
            if (aComponent.equals(leftField))
                return rightField;
            else if (aComponent.equals(rightField))
                return set;

            return leftField;
        }

        public Component getComponentBefore(Container focusCycleRoot, Component aComponent)
        {
            if (aComponent.equals(leftField))
                return set;
            else if (aComponent.equals(set))
                return rightField;

            return leftField;
        }

        public Component getDefaultComponent(Container focusCycleRoot) { return leftField; }
        public Component getLastComponent(Container focusCycleRoot) { return set; }
        public Component getFirstComponent(Container focusCycleRoot) { return leftField; }
    }


    @Override
    public void event(MT type, Object o)
    {
        switch (type)
        {
            case DIALIN_LEFT:
                leftDial.setText(NF.format((Double)o));
                break;
            case DIALIN_RIGHT:
                rightDial.setText(NF.format((Double)o));
                break;

            case INPUT_RESET_SOFT:
            case INPUT_RESET_HARD:
            case SIXTY_LEFT:
            case SIXTY_RIGHT:
                leftDial.setText("      ");
                rightDial.setText("      ");
                break;
        }
    }


    @Override
    public void actionPerformed(ActionEvent e)
    {
        try
        {
            LeftRightDialin d = new LeftRightDialin(Double.parseDouble(leftField.getText()), Double.parseDouble(rightField.getText()));
            Messenger.sendEvent(MT.INPUT_SET_DIALIN, d);
            leftField.setText("");
            rightField.setText("");
            leftField.requestFocus();
        }
        catch (NumberFormatException nfe)
        {
        }
    }
}

