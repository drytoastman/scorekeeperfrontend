/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2019 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.util;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Event info (to keep separate from org.wwscc.storage)
 */
public class EventInfo
{
    protected UUID      eventId;
    protected String    name;
    protected LocalDate date;
    protected int       courses;
    protected int       runs;
    protected int       countedRuns;
    protected double    conePenalty;
    protected double    gatePenalty;
    protected boolean   isPro;
    protected boolean   isPractice;

    @Override
    public String toString()
    {
        return name;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public LocalDate getDate() {
        return date;
    }

    public int getCourses() {
        return courses;
    }

    public int getRuns() {
        return runs;
    }

    public int getCountedRuns() {
        return countedRuns;
    }

    public double getConePenalty() {
        return conePenalty;
    }

    public double getGatePenalty() {
        return gatePenalty;
    }

    public boolean isPro() {
        return isPro;
    }

    public boolean isPractice() {
        return isPractice;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public void setCourses(int courses) {
        this.courses = courses;
    }

    public void setRuns(int runs) {
        this.runs = runs;
    }

    public void setCountedRuns(int countedRuns) {
        this.countedRuns = countedRuns;
    }

    public void setConePenalty(double conePenalty) {
        this.conePenalty = conePenalty;
    }

    public void setGatePenalty(double gatePenalty) {
        this.gatePenalty = gatePenalty;
    }

    public void setPro(boolean isPro) {
        this.isPro = isPro;
    }

    public void setPractice(boolean isPractice) {
        this.isPractice = isPractice;
    }
}

