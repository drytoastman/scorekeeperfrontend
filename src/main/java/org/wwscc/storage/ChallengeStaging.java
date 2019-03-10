package org.wwscc.storage;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ChallengeStaging
{
    private static final ObjectMapper mapper = new ObjectMapper();

    protected UUID challengeid;
    protected List<Entry> entries;

    public static class Entry
    {
        private int round;
        private Optional<String> left, right;
        public Entry(int round, String left, String right)
        {
            this.round = round;
            this.left  = Optional.ofNullable(left);
            this.right = Optional.ofNullable(right);
        }
        public void setLeft(String s)   { left = Optional.ofNullable(s); }
        public void setRight(String s)  { right = Optional.ofNullable(s); }
        public Optional<String> left()  { return left;  }
        public Optional<String> right() { return right; }
        public boolean valid() { return left.isPresent() || right.isPresent(); }
        public int round() { return round; }
    }

    public ChallengeStaging(UUID challengeid)
    {
        this.challengeid = challengeid;
        this.entries = new ArrayList<>();
    }

    public ChallengeStaging(ResultSet rs) throws SQLException
    {
        challengeid  = (UUID)rs.getObject("challengeid");
        entries = new ArrayList<>();
        try {
            JsonNode info = mapper.readTree(rs.getString("stageinfo"));
            Iterator<JsonNode> iter = info.elements();
            while (iter.hasNext()) {
                JsonNode e = iter.next();
                String l = e.get("left")  != null ? e.get("left").asText()  : null;
                String r = e.get("right") != null ? e.get("right").asText() : null;
                entries.add(new Entry(e.get("round").asInt(), l, r));
            }
        } catch (IOException e) {
            throw new SQLException(e);
        }
    }

    public UUID getChallengeId()
    {
        return challengeid;
    }

    public List<Entry> getEntries()
    {
        return entries;
    }

    public JsonNode getStageInfo()
    {
        ArrayNode rootNode = mapper.createArrayNode();
        for (Entry e : entries) {
            ObjectNode n = mapper.createObjectNode();
            n.put("round", e.round);
            if (e.left.isPresent())  n.put("left",  e.left.get());
            if (e.right.isPresent()) n.put("right", e.right.get());
            rootNode.add(n);
        }
        return rootNode;
    }
}
