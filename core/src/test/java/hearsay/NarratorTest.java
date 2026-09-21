package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class NarratorTest {

    private static final Claim DIAMONDS_SCARCE = new Claim("diamond", ClaimType.SCARCE);
    private static final Traits PLAIN = new Traits(0.5, 0.5, 0.5);

    /** Two villagers and a rumor already in Mira's head, as a starting point. */
    private static List<Event> twoVillagersAndARumor() {
        return List.of(
                new VillagerCreated(1, 0, "Mira", PLAIN, 0),
                new VillagerCreated(1, 1, "Bo", PLAIN, 0),
                new RumorPlanted(1, 0, DIAMONDS_SCARCE, 1, 0, 1.0));
    }

    private static Narrator primed() {
        Narrator narrator = Narrator.withMeetings();
        narrator.narrate(twoVillagersAndARumor());
        return narrator;
    }

    @Test
    void namesComeFromTheLogNotTheWorldState() {
        assertEquals(List.of("Day 2, morning: Mira meets Bo at the well."),
                primed().narrate(new VillagersMet(5, 0, 1, Spot.WELL)));
    }

    @Test
    void dayAndPartComeFromTheTick() {
        assertTrue(primed().narrate(new VillagersMet(4, 0, 1, Spot.WELL)).get(0)
                .startsWith("Day 1, night: "));
        assertTrue(primed().narrate(new VillagersMet(6, 0, 1, Spot.MARKET)).get(0)
                .startsWith("Day 2, midday: "));
    }

    @Test
    void aTellingShowsTheConfidenceItChanged() {
        assertEquals(
                List.of("Day 1, midday: Mira tells Bo that diamonds are scarce (Bo: 0% → 42%)."),
                primed().narrate(new RumorTold(2, 0, 1, 0, 0, 0.42, new java.util.TreeSet<>(java.util.List.of(0, 1)))));
    }

    @Test
    void aSecondTellingStartsFromWhatTheListenerAlreadyBelieved() {
        Narrator narrator = primed();
        narrator.narrate(new RumorTold(2, 0, 1, 0, 0, 0.42, new java.util.TreeSet<>(java.util.List.of(0, 1))));

        assertEquals(
                List.of("Day 1, midday: Mira tells Bo that diamonds are scarce (Bo: 42% → 70%)."),
                narrator.narrate(new RumorTold(2, 0, 1, 0, 0, 0.70, new java.util.TreeSet<>(java.util.List.of(0, 1)))));
    }

    @Test
    void aRumorThatGrewIsNarratedAfterTheTellingItGrewDuring() {
        Narrator narrator = primed();

        // The mutation is recorded first, but reads better second.
        assertEquals(List.of(), narrator.narrate(new RumorMutated(2, 1, 0, 2)));
        assertEquals(
                List.of("Day 1, midday: Mira tells Bo that diamonds are very scarce (Bo: 0% → 42%).",
                        "Day 1, midday: …and it grew in the telling: diamonds are now very scarce."),
                narrator.narrate(new RumorTold(2, 0, 1, 1, 1, 0.42, new java.util.TreeSet<>(java.util.List.of(0, 1)))));
    }

    @Test
    void movesAndDayEndsAreNotNarrated() {
        Narrator narrator = primed();
        assertEquals(List.of(), narrator.narrate(new VillagerMoved(1, 0, Spot.WELL)));
        assertEquals(List.of(), narrator.narrate(new DayEnded(4, 0.9, 0.05)));
    }

    @Test
    void meetingsAreOnlyNarratedWhenAskedFor() {
        Narrator quiet = Narrator.of();
        quiet.narrate(twoVillagersAndARumor());
        assertEquals(List.of(), quiet.narrate(new VillagersMet(5, 0, 1, Spot.WELL)));
    }

    @Test
    void aWholeRunNarratesWithEveryVillagerNamed() {
        Params params = Params.defaults();
        Simulation sim = new Simulation(42, params,
                List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, 0)));
        sim.run(40);

        List<String> lines = Narrator.of().narrate(sim.log());

        assertFalse(lines.isEmpty());
        for (String line : lines) {
            assertFalse(line.contains("villager "), "every name should be known: " + line);
        }
    }
}
