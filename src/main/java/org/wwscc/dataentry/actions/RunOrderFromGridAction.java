package org.wwscc.dataentry.actions;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.KeyStroke;

import org.wwscc.dataentry.DataEntry;
import org.wwscc.dialogs.GridImportDialog;
import org.wwscc.storage.Database;
import org.wwscc.storage.BackendDataLoader;

public class RunOrderFromGridAction extends AbstractAction
{
    private static Logger log = Logger.getLogger(RunOrderFromGridAction.class.getCanonicalName());

    public RunOrderFromGridAction()
    {
        super("RunOrder From Grid");
        putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_R, ActionEvent.CTRL_MASK));
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        try {
            Set<UUID> order = Database.d.activeRunOrderForEvent(DataEntry.state.getCurrentEventId());
            GridImportDialog gi = new GridImportDialog(BackendDataLoader.fetchGrid(), order);
            if (gi.doDialog("Grid Import", null)) {
                Map<Integer, List<UUID>> toadd = gi.getResult();
                for (int course : toadd.keySet()) {
                    Database.d.setRunOrder(DataEntry.state.getCurrentEventId(), course, DataEntry.state.getCurrentRunGroup(), toadd.get(course), !gi.doOverwrite());
                }
            }
        } catch (Exception ex) {
            log.log(Level.WARNING, "\bUpdated runorder failed: " + ex, ex);
        }
    }
}