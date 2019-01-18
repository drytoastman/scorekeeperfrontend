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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Class to get the current grid from the backend.  Use the backend web interface so that the table
 * calculations are all done in one place rather than replicated in Python and Java.
 */
public class BackendDataLoader
{
    public static final String resultsURL = "http://127.0.0.1/api/%s/event/%s";
    public static final String gridURL = "http://127.0.0.1/results/%s/event/%s/grid?order=%s&json";
    private static final ObjectMapper mapper = new ObjectMapper();

    public static class GridEntry
    {
        public int group;
        public int grid;
        public UUID carid;
        public String classcode;
        public String name;
        public double net;
        public int number;
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
        Map<String, List<GridEntry>> ret = new HashMap<String, List<GridEntry>>();
        ret.put("Number", new ArrayList<GridEntry>());
        ret.put("Position", new ArrayList<GridEntry>());

        createList(mapper.readTree(new URL(String.format(gridURL, DataEntry.state.getCurrentSeries(), DataEntry.state.getCurrentEventId(), "number"))), ret.get("Number"));
        createList(mapper.readTree(new URL(String.format(gridURL, DataEntry.state.getCurrentSeries(), DataEntry.state.getCurrentEventId(), "position"))), ret.get("Position"));
        return ret;
    }

    private static void createList(JsonNode node, List<GridEntry> dest)
    {
        node.fields().forEachRemaining(e -> {
            int group = Integer.parseInt(e.getKey());
            e.getValue().forEach(l -> {
                try {
                    GridEntry ge = mapper.treeToValue(l, GridEntry.class);
                    ge.group = group;
                    dest.add(ge);
                } catch (JsonProcessingException e1) {
                    e1.printStackTrace();
                }
            });
        });
    }
}
