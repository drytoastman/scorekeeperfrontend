/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.tray;

import java.awt.event.ActionEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import org.wwscc.actions.QuitAction;
import org.wwscc.storage.Database;
import org.wwscc.storage.PostgresqlDatabase;
import org.wwscc.tray.HostSeriesSelectionDialog.HSResult;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.Prefs;

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
		JMenu sync = new JMenu("Sync");
		add(sync);		
        sync.add(new MergeWithAction());
		sync.add(new DownloadNewSeriesAction());

	    JMenu adv = new JMenu("Advanced");
	    add(adv);
	    adv.add(new JCheckBoxMenuItem(new LocalDiscoveryAction()));
	    //adv.add(new AddServerAction());
	    adv.add(new ResetHashAction());
	    adv.add(new DeleteLocalSeriesAction());
		
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
        }
    }	
	
	static class MergeWithAction extends AbstractAction
	{
	    public MergeWithAction() {
	        super("Sync With Host Now");
	    }
	    public void actionPerformed(ActionEvent e) {
	        HostSeriesSelectionDialog d = new HostSeriesSelectionDialog(false);
	        if (!d.doDialog("Select Host", null))
	            return;
	        Database.d.mergeServerSet(d.getResult().host, true, true, true);
	    }
	}
	
	static class LocalDiscoveryAction extends AbstractAction
	{
	    public LocalDiscoveryAction() {
	        setNewState(Prefs.getAllowDiscovery());
	    }
        public void actionPerformed(ActionEvent e) {
            setNewState(((AbstractButton)e.getSource()).getModel().isSelected());
        }
        private void setNewState(boolean on) {
            Prefs.setAllowDiscovery(on);
            putValue(Action.SELECTED_KEY, on);
            putValue(Action.NAME, "Local Discovery " + (on ? "On":"Off"));
        }
	}

	static class DeleteLocalSeriesAction extends AbstractAction
    {
        public DeleteLocalSeriesAction() {
            super("Delete Local Series Copy");
        }
        public void actionPerformed(ActionEvent e) {
            SeriesDialog sd = new SeriesDialog(PostgresqlDatabase.getSeriesList(null).toArray(new String[0]));
            if (!sd.doDialog("Select Series", null))
                return;
            List<String> selected = sd.getResult();
            if (selected.size() > 0)
            {
                for (String s : selected)
                    Database.d.deleteUserAndSeries(s);
                if (sd.allSelected())
                    Database.d.deleteDriversTable();
            }
        }
    }

	static class ResetHashAction extends AbstractAction
    {
        public ResetHashAction() {
            super("Reset Calculations");
        }
        public void actionPerformed(ActionEvent e) {
            Database.d.mergeServerResetAll();
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

