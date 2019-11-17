/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2017 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.util;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ApplicationState {

       private String currentSeries;
       private EventInfo currentEvent;
       private int currentCourse;
       private int currentRunGroup;
       private UUID currentChallengeId;

       public void setCurrentSeries(String s) { currentSeries = s; }
       public void setCurrentEvent(EventInfo e) { currentEvent = e; }
       public void setCurrentCourse(int course) { currentCourse = course; }
       public void setCurrentRunGroup(int rungroup) { currentRunGroup = rungroup; }
       public void setCurrentChallengeId(UUID challengeid) { currentChallengeId = challengeid; }

       public String getCurrentSeries() { return currentSeries; }
       public EventInfo getCurrentEvent() { return currentEvent; }
       public int getCurrentCourse() { return currentCourse; }
       public int getCurrentRunGroup() { return currentRunGroup; }
       public UUID getCurrentChallengeId() { return currentChallengeId; }

       public boolean isPro()         { return (currentEvent != null) ? currentEvent.isPro() : false; }
       public boolean usingSessions() { return (currentEvent != null) ? currentEvent.getSessions().size() > 0 : false; }
       public List<String> getSessions() { return (currentEvent != null) ? currentEvent.getSessions() : new ArrayList<String>(); }
       public UUID getCurrentEventId() { return (currentEvent != null) ? currentEvent.getEventId() : null; }
}
