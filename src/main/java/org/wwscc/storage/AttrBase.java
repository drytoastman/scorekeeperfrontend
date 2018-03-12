/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.storage;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AttrBase
{
    private static Logger log = Logger.getLogger(AttrBase.class.getCanonicalName());
    private static ObjectMapper objectMapper = new ObjectMapper();

    @JsonProperty
    protected ObjectNode attr;

    public AttrBase()
    {
        attr = new ObjectNode(JsonNodeFactory.instance);
    }

    public AttrBase(ObjectNode o)
    {
        attr = o.deepCopy();
    }

    public AttrBase(ResultSet rs) throws SQLException
    {
        try {
            attr = (ObjectNode) objectMapper.readTree(rs.getString("attr"));
        } catch (IOException e) {
            log.warning("Failed to parse attr JSON: " + e);
            attr = new ObjectNode(JsonNodeFactory.instance);
        }
    }

    /**
     * Try and purge unnecessary keys that have no information from the database
     */
    public void attrCleanup()
    {
        attr.fields().forEachRemaining(field -> {
            JsonNode val = field.getValue();
            if (    (val == null)
                || ((val.isTextual()) && (val.asText().equals("")))
                || ((val.isInt())     && (val.asInt() == 0))
                || ((val.isDouble())  && (val.asDouble() <= 0.0))
                || ((val.isBoolean()) && (!val.asBoolean()))
               ) {
                attr.remove(field.getKey());
            }
        });
    }

    public boolean hasAttr(String name)
    {
        return attr.has(name) && !attr.get(name).asText().equals("");
    }

    public String getAttrS(String name)
    {
        String ret = null;
        try {
            if (attr.has(name)) {
                ret = attr.get(name).textValue();
                if (ret != null)
                    return ret;
            }
        } catch (Exception e) {
            log.info(String.format("Failed to load string named %s from %s: %s", name, ret, e));
        }
        return "";
    }

    public double getAttrD(String name)
    {
        Double ret = null;
        try {
             if (attr.has(name)) {
                ret = attr.get(name).doubleValue();
                if (ret != null)
                    return ret;
            }
        } catch (Exception e) {
            log.info(String.format("Failed to load double named %s from %s: %s", name, ret, e));
        }
        return -1.0;
    }

    public Set<String> getAttrKeys()
    {
        Set<String> ret = new HashSet<String>();
        attr.fieldNames().forEachRemaining(ret::add);
        return ret;
    }

    public void setAttrS(String name, String val)
    {
        if ((val == null) || (val.trim().length() == 0))
            attr.remove(name);
        else
            attr.put(name, val);
    }

    public void setAttrD(String name, Double val)
    {
        if ((val == null) || (val <= 0.0) || Double.isNaN(val))
            attr.remove(name);
        else
            attr.put(name, val);
    }
}

