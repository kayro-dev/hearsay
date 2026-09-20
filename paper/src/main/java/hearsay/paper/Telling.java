package hearsay.paper;

import hearsay.Event;
import hearsay.RumorTold;

import java.util.ArrayList;
import java.util.List;

/**
 * One villager telling another something, pulled out of the events a tick produced so the
 * screen has something to show. Plain data: no game types, nothing drawn.
 */
record Telling(int tellerId, int listenerId, double newConfidence) {

    static List<Telling> from(List<Event> events) {
        List<Telling> tellings = new ArrayList<>();
        for (Event event : events) {
            if (event instanceof RumorTold told) {
                tellings.add(new Telling(told.tellerId(), told.listenerId(), told.newConfidence()));
            }
        }
        return tellings;
    }
}
