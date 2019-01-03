package org.wwscc.dataentry.actions;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.wwscc.dialogs.GridImportDialog.GridEntry;
import org.wwscc.storage.Database;
import org.wwscc.storage.Entrant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
            GridImportDialog gi = new GridImportDialog(fetchGrid(), order);
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

    private Map<String, List<GridEntry>> fetchGrid() throws MalformedURLException, IOException
    {
        ObjectMapper mapper = new ObjectMapper();
        String url = "http://127.0.0.1/results/%s/event/%s/grid?order=%s&idsonly";

        // make sure we get anyone registered or with runs
        Map<UUID,Entrant> entrants = new HashMap<UUID, Entrant>();
        for (Entrant e : Database.d.getRegisteredEntrants(DataEntry.state.getCurrentEventId())) {
            entrants.put(e.getCarId(), e);
        }
        for (Entrant e : Database.d.getEntrantsByEvent(DataEntry.state.getCurrentEventId())) {
            entrants.put(e.getCarId(), e);
        }

        Map<String, List<GridEntry>> ret = new HashMap<String, List<GridEntry>>();
        ret.put("Number", new ArrayList<GridEntry>());
        ret.put("Position", new ArrayList<GridEntry>());

        createList(mapper.readTree(new URL(String.format(url, DataEntry.state.getCurrentSeries(), DataEntry.state.getCurrentEventId(), "number"))),
                   Database.d.getRegisteredEntrants(DataEntry.state.getCurrentEventId()),
                   ret.get("Number"));
        createList(mapper.readTree(new URL(String.format(url, DataEntry.state.getCurrentSeries(), DataEntry.state.getCurrentEventId(), "position"))),
                   Database.d.getEntrantsByEvent(DataEntry.state.getCurrentEventId()),
                   ret.get("Position"));
        return ret;
    }

    private void createList(JsonNode node, List<Entrant> entrantlist, List<GridEntry> dest)
    {
        Map<UUID,Entrant> entrants = new HashMap<UUID, Entrant>();
        for (Entrant e : entrantlist) {
            entrants.put(e.getCarId(), e);
        }

        node.fields().forEachRemaining(e -> {
            int group = Integer.parseInt(e.getKey());
            e.getValue().forEach(l -> {
                String carid = l.get("carid").asText();
                Entrant entrant = null;
                if (!carid.trim().equals("")) {
                    entrant = entrants.get(UUID.fromString(carid));
                }
                dest.add(new GridEntry(group, l.get("grid").asInt(), entrant));
            });
        });
    }
}