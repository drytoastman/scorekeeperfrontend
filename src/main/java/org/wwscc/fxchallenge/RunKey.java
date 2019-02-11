package org.wwscc.fxchallenge;

import java.util.UUID;

import org.wwscc.storage.ChallengeRun;

public class RunKey
{
    UUID carid;
    int round, course;

    public RunKey(UUID carid, int round, int course)
    {
        this.carid = carid;
        this.round = round;
        this.course = course;
    }

    public RunKey(ChallengeRun r)
    {
        this.carid = r.getCarId();
        this.round = r.getRound();
        this.course = r.getCourse();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((carid == null) ? 0 : carid.hashCode());
        result = prime * result + course;
        result = prime * result + round;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RunKey other = (RunKey) obj;
        if (carid == null) {
            if (other.carid != null)
                return false;
        } else if (!carid.equals(other.carid))
            return false;
        if (course != other.course)
            return false;
        if (round != other.round)
            return false;
        return true;
    }
}