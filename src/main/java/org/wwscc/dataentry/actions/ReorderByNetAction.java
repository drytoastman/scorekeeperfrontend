package org.wwscc.dataentry.actions;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.FocusManager;
import javax.swing.JOptionPane;
import org.wwscc.dataentry.DataEntry;
import org.wwscc.storage.Database;
import org.wwscc.storage.Entrant;
import org.wwscc.storage.BackendDataLoader;

public class ReorderByNetAction extends AbstractAction
{
    private static Logger log = Logger.getLogger(ReorderByNetAction.class.getCanonicalName());

    public static class Placement
    {
        String classcode;
        int position;
        public Placement(String c, int p)
        {
            classcode = c;
            position = p;
        }
        public Placement(Placement p)
        {
            classcode = p.classcode;
            position = p.position;
        }
        public String toString()
        {
            return classcode + "/" + position;
        }
        @Override
        public boolean equals(Object o)
        {
            Placement p = (Placement)o;
            return p.classcode.equals(classcode) && (p.position == position);
        }
        @Override
        public int hashCode()
        {
            return classcode.hashCode() + position;
        }
    }

    public ReorderByNetAction()
    {
        super("Reorder By Net");
        //putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_N, ActionEvent.CTRL_MASK));
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        try {
            int rg = DataEntry.state.getCurrentRunGroup();
            List<Entrant> orderleft  = Database.d.getEntrantsByRunOrder(DataEntry.state.getCurrentEventId(), 1, rg);
            List<Entrant> orderright = Database.d.getEntrantsByRunOrder(DataEntry.state.getCurrentEventId(), 2, rg);

            boolean allhaveruns = true;
            for (Entrant l : orderleft) { allhaveruns &= l.hasRuns(); }
            for (Entrant r: orderright) { allhaveruns &= r.hasRuns(); }
            if (!allhaveruns) {
                JOptionPane.showMessageDialog(FocusManager.getCurrentManager().getActiveWindow(),
                        "Not all of the entries in the left course or right course have runs, reorder will not work for rungroup " + rg,
                        "Missing Runs", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (JOptionPane.showConfirmDialog(
                    FocusManager.getCurrentManager().getActiveWindow(),
                    "This will reorder the entrants on both courses for rungroup "+rg+" using the current order as a template, continue?",
                    "Reorder Entrants",
                    JOptionPane.YES_NO_OPTION)
                        != JOptionPane.YES_OPTION)
                  return;

            List<List<Placement>> placements = calculatePlacements(orderleft, orderright);
            Map<String, List<UUID>> results  = BackendDataLoader.fetchResults();

            List<UUID> newleft  = new ArrayList<>();
            List<UUID> newright = new ArrayList<>();
            for (Placement p : placements.get(0)) {
                newleft.add( results.get(p.classcode).get(p.position-1));
            }
            for (Placement p : placements.get(1)) {
                newright.add(results.get(p.classcode).get(p.position-1));
            }

            Database.d.setRunOrder(DataEntry.state.getCurrentEventId(), 1, rg, newleft,  false);
            Database.d.setRunOrder(DataEntry.state.getCurrentEventId(), 2, rg, newright, false);
            DataEntry.poker.poke();
        } catch (Exception ex) {
            log.log(Level.WARNING, "\bReorder failed: " + ex, ex);
        }
    }


    protected static List<List<Placement>> calculatePlacements(List<Entrant> orderleft, List<Entrant> orderright)
    {
        Set<UUID> seenleft = new HashSet<UUID>();
        Set<UUID> seenright = new HashSet<UUID>();

        ClassCounters counters = new ClassCounters();
        ReplayLists replay = new ReplayLists();

        List<Placement> placeleft  = new ArrayList<>();
        List<Placement> placeright = new ArrayList<>();

        for (int ii = 0; ii < orderleft.size(); ii++) {
            Entrant e1 = orderleft.get(ii);
            Entrant e2 = orderright.get(ii);
            Placement p;

            if (!seenright.contains(e1.getCarId())) {
                p = counters.next(e1.getClassCode());
                replay.record(1, p);
            } else {
                p = replay.next(2, e1.getClassCode());
            }
            placeleft.add(p);
            seenleft.add(e1.getCarId());

            if (!seenleft.contains(e2.getCarId())) {
                p = counters.next(e2.getClassCode());
                replay.record(2, p);
            } else {
                p = replay.next(1, e2.getClassCode());
            }
            placeright.add(p);
            seenright.add(e2.getCarId());
        }

        return Arrays.asList(placeleft, placeright);
    }

    static class ReplayLists
    {
        HashMap<String, Queue<Placement>> replayleft;
        HashMap<String, Queue<Placement>> replayright;

        public ReplayLists() {
            replayleft = new HashMap<>();
            replayright = new HashMap<>();
        }

        public void record(int course, Placement p) {
            record(course == 1 ? replayleft : replayright, p);
        }

        public Placement next(int course, String classcode) {
            return next(course == 1 ? replayleft : replayright, classcode);
        }

        private void record(HashMap<String, Queue<Placement>> m, Placement p) {
            if (!m.containsKey(p.classcode))
                m.put(p.classcode, new LinkedList<>());
            m.get(p.classcode).add(new Placement(p));
        }

        private Placement next(HashMap<String, Queue<Placement>> m, String classcode) {
            return m.get(classcode).remove();
        }
    }

    static class ClassCounters
    {
        HashMap<String, Integer> counters;
        public ClassCounters() {
            counters = new HashMap<>();
        }

        public Placement next(String classcode) {
            int next = counters.getOrDefault(classcode, 1);
            counters.put(classcode, next+1);
            return new Placement(classcode, next);
        }
    }
}