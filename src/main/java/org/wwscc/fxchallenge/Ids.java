/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2018 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.fxchallenge;

import java.util.UUID;

import org.wwscc.storage.Entrant;

/**
 *
 */
public class Ids
{
    /**
     * Represents a pointer to a particular round in the tree
     */
    public static class Round
    {
        public UUID challengeid;
        public int round;

        public Round(Round r)
        {
            challengeid = r.challengeid;
            round = r.round;
        }

        public Round(UUID c, int r)
        {
            challengeid = c;
            round = r;
        }

        public int getRound()
        {
            return round;
        }

        public Location advancesTo()
        {
            if (round == 99) /* third place winner */
                return new Location(challengeid, 0, Location.Level.LOWER);

            Location.Level level = (round%2 != 0) ? Location.Level.UPPER : Location.Level.LOWER;
            return new Location(challengeid, round/2, level);
        }

        public Location advanceThird()
        {
            Location.Level level;
            if (round == 2)
                level = Location.Level.LOWER;
            else if (round == 3)
                level = Location.Level.UPPER;
            else
                return null;

            return new Location(challengeid, 99, level);
        }

        /**
         * @return the depth within the tree, 1 is the final round, 2 is semis, 3 is quarters, etc.
         */
        public int getDepth()
        {
            if (round < 2) return 1;
            if (round < 4) return 2;
            if (round < 8) return 3;
            if (round < 16) return 4;
            if (round < 32) return 5;
            return 6;
        }

        public Location makeLower() { return new Location(this, Location.Level.LOWER); }
        public Location makeUpper() { return new Location(this, Location.Level.UPPER); }

        public Run makeUpperLeft() { return new Run(this, Location.Level.UPPER, Run.RunType.LEFT); }
        public Run makeUpperRight() { return new Run(this, Location.Level.UPPER, Run.RunType.RIGHT); }
        public Run makeLowerLeft() { return new Run(this, Location.Level.LOWER, Run.RunType.LEFT); }
        public Run makeLowerRight() { return new Run(this, Location.Level.LOWER, Run.RunType.RIGHT); }

        @Override
        public boolean equals(Object o)
        {
            if (o == null) return false;
            if (!(o instanceof Round)) return false;
            Round other = (Round)o;
            return ((other.challengeid.equals(challengeid)) && (other.round ==  round));
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = challengeid.clockSequence();
            result = prime * result + round;
            return result;
        }

        public String toString()
        {
            return String.format("Round %s", round);
        }
    }


    /**
     * Represents a pointer to an entrant side in a specific round
     */
    public static class Location extends Round
    {
        public enum Level { UPPER, LOWER };
        Level level;

        public Location(UUID c, int r, Level l)
        {
            super(c, r);
            level = l;
        }

        public Location(Round from, Level l)
        {
            super(from);
            level = l;
        }

        public Level getLevel() { return level; }
        public boolean isUpper() { return (level == Level.UPPER); }
        public boolean isLower() { return (level == Level.LOWER); }

        public Run makeLeft() { return new Run(this, Run.RunType.LEFT); }
        public Run makeRight() { return new Run(this, Run.RunType.RIGHT); }

        public boolean equals(Object o)
        {
            if ((o == null) || (!(o instanceof Location))) return false;
            Location e = (Location)o;
            return ((e.level == level) && super.equals(o));
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + ((level == null) ? 0 : level.hashCode());
            return result;
        }

        public String toString()
        {
            return String.format("Entry %s/%s", round, level);
        }
    }


    public static class BracketEntry extends Location
    {
        Entrant entrant;
        double dialin;

        public BracketEntry(Round r, Location.Level l, Entrant e, double d)
        {
            super(r, l);
            entrant = e;
            dialin = d;
        }
    }


    /**
     * Represents a pointer to a single run for an entrant in a specific round
     */
    public static class Run extends Location
    {
        public enum RunType { LEFT, RIGHT };
        RunType runType;

        public Run(Round r, Location.Level l, RunType t)
        {
            super(r, l);
            runType = t;
        }

        public Run(Location from, RunType t)
        {
            super(from.challengeid, from.round, from.level);
            runType = t;
        }

        public RunType getRunType() { return runType; }
        public boolean isLeft() { return (runType == RunType.LEFT); }
        public boolean isRight() { return (runType == RunType.RIGHT); }

        @Override
        public boolean equals(Object o)
        {
            if ((o == null) || (!(o instanceof Run))) return false;
            Run r = (Run)o;
            return ((r.runType == runType) && super.equals(o));
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + ((runType == null) ? 0 : runType.hashCode());
            return result;
        }

        public String toString()
        {
            return String.format("Run %s/%s/%s", round, level, runType);
        }
    }
}
