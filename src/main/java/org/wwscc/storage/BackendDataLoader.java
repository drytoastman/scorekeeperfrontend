package org.wwscc.storage;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.wwscc.dataentry.DataEntry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Class to get the current grid from the backend.  Use the backend web interface so that the table
 * calculations are all done in one place rather than replicated in Python and Java.
 */
public class BackendDataLoader
{
    public static final String resultsURL = "http://127.0.0.1/api/%s/event/%s";
    public static final String gridURL = "http://127.0.0.1/results/%s/event/%s/grid?order=%s&idsonly";
    private static final ObjectMapper mapper = new ObjectMapper();

    public static class GridEntry
    {
        public int group;
        public int grid;
        public Entrant entrant;
        public GridEntry(int group, int grid, Entrant entrant)
        {
            this.group = group;
            this.grid = grid;
            this.entrant = entrant;
        }
    }

    public static Map<String, List<UUID>> fetchResults() throws MalformedURLException, IOException
    {
        Map<String, List<UUID>> ret = new HashMap<>();
        JsonNode root = mapper.readTree(new URL(String.format(resultsURL, DataEntry.state.getCurrentSeries(), DataEntry.state.getCurrentEventId())));
        for (JsonNode classlist : root.get("classes")) {
            List<UUID> results = new ArrayList<UUID>();
            for (JsonNode entry : classlist.get("entries")) {
                results.add(UUID.fromString(entry.get("carid").asText()));
            }
            ret.put(classlist.get("classcode").asText(), results);
        }
        return ret;
    }

    public static Map<String, List<GridEntry>> fetchGrid() throws MalformedURLException, IOException
    {
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

        createList(mapper.readTree(new URL(String.format(gridURL, DataEntry.state.getCurrentSeries(), DataEntry.state.getCurrentEventId(), "number"))),
                   Database.d.getRegisteredEntrants(DataEntry.state.getCurrentEventId()),
                   ret.get("Number"));
        createList(mapper.readTree(new URL(String.format(gridURL, DataEntry.state.getCurrentSeries(), DataEntry.state.getCurrentEventId(), "position"))),
                   Database.d.getEntrantsByEvent(DataEntry.state.getCurrentEventId()),
                   ret.get("Position"));
        return ret;
    }

    private static void createList(JsonNode node, List<Entrant> entrantlist, List<GridEntry> dest)
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
