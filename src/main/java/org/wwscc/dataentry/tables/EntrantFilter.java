package org.wwscc.dataentry.tables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.RowFilter;

import org.wwscc.storage.Database;
import org.wwscc.storage.Entrant;
import org.wwscc.storage.Run;
import org.wwscc.util.NF;

public class EntrantFilter extends RowFilter<EntryModel, Integer> {

    Matcher matcher;
    
    public EntrantFilter(String s)
    {
        super();
        if (s.trim().equals(""))
            matcher = null;
        else
            matcher = Pattern.compile(s.trim(), Pattern.CASE_INSENSITIVE).matcher("");
    }
    
    @Override
    public boolean include(Entry<? extends EntryModel, ? extends Integer> entry) 
    {
        if (matcher == null)
            return true;
        
        Entrant e = (Entrant)entry.getValue(0);
        if (e == null) 
            return true;
        
        // Run through the text conversions done by the renderers looking for matches
        
        matcher.reset(e.getClassCode());
        if (matcher.find()) return true;
        
        matcher.reset(""+e.getNumber());
        if (matcher.find()) return true;

        matcher.reset(e.getFirstName() + " " + e.getLastName());
        if (matcher.find()) return true;

        matcher.reset(e.getCarDesc() + " " +  Database.d.getEffectiveIndexStr(e.getCar()));
        if (matcher.find()) return true;
        
        for (Run r : e.getRuns()) {
            matcher.reset(NF.format(r.getRaw()));
            if (matcher.find()) return true;
        }
        
        return false;
    }
}
