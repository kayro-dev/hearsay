package hearsay.paper;

import hearsay.ClaimType;
import hearsay.Event;
import hearsay.RumorTold;
import hearsay.WorldState;

import java.util.ArrayList;
import java.util.List;

/**
 * One villager telling another something, pulled out of the events a tick produced so the
 * screen has something to show. Plain data: no game types, nothing drawn.
 */
record Telling(int tellerId, int listenerId, double newConfidence, ClaimType type) {

    /**
     * @param world the world after the tick, used only to look up which claim a rumor id
     *              stands for. Scarcity and abundance are drawn in opposite colours, and
     *              the event carries an id rather than a claim
     */
    static List<Telling> from(List<Event> events, WorldState world) {
        List<Telling> tellings = new ArrayList<>();
        for (Event event : events) {
            if (event instanceof RumorTold told) {
                tellings.add(new Telling(told.tellerId(), told.listenerId(),
                        told.newConfidence(), world.rumor(told.keptRumorId()).claim().type()));
            }
        }
        return tellings;
    }
}
