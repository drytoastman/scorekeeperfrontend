/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.tray;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JSeparator;

import org.wwscc.actions.QuitAction;
import org.wwscc.storage.Database;
import org.wwscc.tray.HostSeriesSelectionDialog.HSResult;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;

public class Controls extends JMenuBar implements MessageListener
{
	//private static final Logger log = Logger.getLogger(Controls.class.getCanonicalName());

	public Controls()
	{
		/* File Menu */
		JMenu file = new JMenu("File");
        add(file);
        file.add(new QuitAction());

		/*  */
		JMenu merge = new JMenu("Merging");
		add(merge);		
		merge.add(new DownloadNewSeriesAction());
	    merge.add(new MergeWithAction());

	    JMenu adv = new JMenu("Advanced");
	    add(adv);
	    adv.add(new JCheckBoxMenuItem(new LocalDiscoveryAction()));
	    adv.add(new AddServerAction());
	    adv.add(new ResetHashAction());
		
	    Messenger.register(MT.DATABASE_NOTIFICATION, this);
	}

	
    static class DownloadNewSeriesAction extends AbstractAction
    {
        public DownloadNewSeriesAction() {
            super("Download New Series From");
        }
        public void actionPerformed(ActionEvent e) {
            HostSeriesSelectionDialog hd = new HostSeriesSelectionDialog(true);
            if (!hd.doDialog("Select Host and Series", null))
                return;

            HSResult ret = hd.getResult();
            Database.d.verifyUserAndSeries(ret.series, ret.password);
            Database.d.mergeServerSet(ret.host, true, true, true);
            DockerInterface.pokecontainers();
        }
    }	
	
	static class MergeWithAction extends AbstractAction
	{
	    public MergeWithAction() {
	        super("Merge With Now");
	    }
	    public void actionPerformed(ActionEvent e) {
	        HostSeriesSelectionDialog d = new HostSeriesSelectionDialog(false);
	        if (!d.doDialog("Select Host", null))
	            return;
	        Database.d.mergeServerSet(d.getResult().host, true, true, true);
	        DockerInterface.pokecontainers();
	    }
	}
	
	static class LocalDiscoveryAction extends AbstractAction
	{
	    public LocalDiscoveryAction() {
	        super("Local Discovery");
	    }
        public void actionPerformed(ActionEvent e) {
            AbstractButton aButton = (AbstractButton)e.getSource();
            if (aButton.getModel().isSelected())
                putValue(Action.NAME, "Local Discovery On");
            else
                putValue(Action.NAME, "Local Discovery Off");
        }
	}

	static class AddServerAction extends AbstractAction
    {
        public AddServerAction() {
            super("Add Server");
        }
        public void actionPerformed(ActionEvent e) {
        }
    }

	static class ResetHashAction extends AbstractAction
    {
        public ResetHashAction() {
            super("Reset Calculations");
        }
        public void actionPerformed(ActionEvent e) {
            Database.d.mergeServerResetAll();
            DockerInterface.pokecontainers();
        }
    }
	
	@Override
	public void event(MT type, Object data)
	{
		switch (type)
		{
	        case DATABASE_NOTIFICATION:
			    break;
		}
	}
}

