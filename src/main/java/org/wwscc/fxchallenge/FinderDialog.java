/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2019 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.fxchallenge;

import java.awt.Color;
import java.awt.Dimension;
import java.net.InetSocketAddress;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.wwscc.dialogs.SimpleFinderDialog;
import org.wwscc.util.Discovery;

import javafx.embed.swing.SwingNode;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;

/**
 * Reuse swing dialog as a template for the FX version for now.
 */
public class FinderDialog extends Dialog<InetSocketAddress>
{
    public FinderDialog()
    {
        SimpleFinderDialog template = new SimpleFinderDialog(Discovery.PROTIMER_TYPE);
        JComponent content = template.getMainPanel();
        content.setBackground(new Color(245, 245, 245));

        SwingNode swingNode = new SwingNode();
        SwingUtilities.invokeLater(() -> swingNode.setContent(content));
        Dimension d = content.getPreferredSize();

        setTitle("Find Timer");
        setResizable(true);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setContent(swingNode);
        getDialogPane().setPrefSize(d.getWidth(), d.getHeight());

        setResultConverter(dialogButton -> {
            if ((dialogButton == ButtonType.OK) && template.verifyData())
                return template.getResult();
            return null;
        });
    }
}
