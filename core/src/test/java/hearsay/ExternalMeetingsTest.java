package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Minecraft owns the bodies; Hearsay owns the minds. */
class ExternalMeetingsTest {

    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);
    private static final Params EXTERNAL = Params.defaults().withMeetingSource(MeetingSource.EXTERNAL);

    /**
     * Meetings as an adapter would report them, with the rumor planted in a villager who
     * actually talks. Villager 0 on this seed has a gossip trait of 0.05 and would sit on
     * the rumor forever, which would leave the spreading test checking nothing.
     */
    private static List<Input> someReportedMeetings() {
        int talker = Run.execute(42, Params.defaults(), List.of(), 1)
                .finalState().gossipiestVillager().id();

        List<Input> inputs = new ArrayList<>();
        inputs.add(new PlantRumor(2, DIAMONDS_SCARCE, 1, talker));
        for (long tick = 2; tick <= 40; tick++) {
            int partner = (int) (tick % (Simulation.VILLAGER_COUNT - 1));
            if (partner == talker) {
                partner = Simulation.VILLAGER_COUNT - 1;
            }
            inputs.add(new ObservedMeeting(tick, talker, partner, Spot.MARKET));

            int other = (int) ((tick * 3) % Simulation.VILLAGER_COUNT);
            int alsoOther = (int) ((tick * 7 + 1) % Simulation.VILLAGER_COUNT);
            if (other != alsoOther && other != talker && alsoOther != talker
                    && other != partner && alsoOther != partner) {
                inputs.add(new ObservedMeeting(tick, other, alsoOther, Spot.WELL));
            }
        }
        return inputs;
    }

    @Test
    void withNothingReportedNobodyEverMeets() {
        Run run = Run.execute(42, EXTERNAL, List.of(), 60);

        for (Event event : run.log()) {
            assertFalse(event instanceof VillagersMet, "no meeting was reported, so none happened");
            assertFalse(event instanceof VillagerMoved, "nobody moves unless something says so");
        }
        // ...and the village still exists and the clock still runs.
        assertEquals(Simulation.VILLAGER_COUNT, run.finalState().villagers().size());
        assertEquals(60, run.finalState().tick());
    }

    @Test
    void everyMeetingComesFromSomethingThatWasReported() {
        Run run = Run.execute(42, EXTERNAL, someReportedMeetings(), 40);

        List<String> reported = new ArrayList<>();
        for (Input input : run.inputs()) {
            if (input instanceof ObservedMeeting m) {
                reported.add(m.tick() + ":" + m.a() + "-" + m.b() + "@" + m.spot());
            }
        }

        int met = 0;
        for (Event event : run.log()) {
            if (event instanceof VillagersMet e) {
                assertTrue(reported.contains(e.tick() + ":" + e.a() + "-" + e.b() + "@" + e.spot()),
                        "a meeting on tick " + e.tick() + " that nobody reported");
                met++;
            }
        }
        assertEquals(reported.size(), met, "every reported meeting should have happened");
    }

    @Test
    void theMovementStreamIsUntouchedOnceTheVillageExists() {
        // Traits are still rolled from the movement stream when the village is made, so the
        // villagers are the same people either way. What must not happen is any further
        // draw: were movement still being rolled, these two runs would part company.
        Run simulated = Run.execute(42, Params.defaults(), List.of(), 40);
        Run external = Run.execute(42, EXTERNAL, someReportedMeetings(), 40);

        for (int id = 0; id < Simulation.VILLAGER_COUNT; id++) {
            assertEquals(simulated.finalState().villager(id).traits(),
                    external.finalState().villager(id).traits(),
                    "villager " + id + " is a different person in the two modes");
        }
    }

    @Test
    void aReportedMeetingPutsBothVillagersWhereItSaysTheyWere() {
        Run run = Run.execute(42, EXTERNAL, someReportedMeetings(), 40);

        WorldState mirror = new WorldState();
        int checked = 0;
        for (Event event : run.log()) {
            mirror.apply(event);
            if (event instanceof VillagersMet e) {
                assertEquals(e.spot(), mirror.villager(e.a()).spot());
                assertEquals(e.spot(), mirror.villager(e.b()).spot());
                checked++;
            }
        }
        assertTrue(checked > 0);
    }

    @Test
    void anInGameRunIsReproducibleFromItsRecipe() {
        Run run = Run.execute(42, EXTERNAL, someReportedMeetings(), 40);

        // The seed alone says nothing about where Minecraft put anyone. The recipe does.
        assertEquals(run.log(), run.rerun().log());
        assertEquals(run.finalState(), Simulation.replay(run.log()));
    }

    @Test
    void rumorsStillSpreadThroughReportedMeetings() {
        Run run = Run.execute(42, EXTERNAL, someReportedMeetings(), 40);

        int told = 0;
        for (Event event : run.log()) {
            if (event instanceof RumorTold) {
                told++;
            }
        }
        assertTrue(told > 0, "the minds should still work when the bodies are somebody else's");
    }

    @Test
    void inputsArrivingDuringPlayEndUpInTheRecipe() {
        // How a game feeds the simulation: a tick's meetings are only known once it has
        // happened, so they are scheduled one tick ahead as play goes on.
        Simulation live = new Simulation(42, EXTERNAL, List.of());
        for (long tick = 1; tick <= 20; tick++) {
            live.schedule(new ObservedMeeting(tick + 1, (int) (tick % 4), (int) (tick % 4) + 9,
                    Spot.MARKET));
            live.step();
        }

        Run session = live.toRun();
        assertEquals(20, session.inputs().size(), "every reported meeting should be recorded");
        assertEquals(session.log(), session.rerun().log(),
                "a session fed live should replay from its recipe");
    }

    @Test
    void anInputCannotChangeThePast() {
        Simulation live = new Simulation(42, EXTERNAL, List.of());
        live.run(5);

        assertThrows(IllegalArgumentException.class,
                () -> live.schedule(new ObservedMeeting(3, 0, 1, Spot.WELL)));
    }

    @Test
    void aSightingPutsAVillagerSomewhereWithoutThemMeetingAnybody() {
        // The point of sightings: a villager standing at a stall alone used to be invisible,
        // because where anyone stood could only be inferred from who they were talking to.
        Simulation sim = new Simulation(42, EXTERNAL,
                List.of(new VillagerSeen(2, 7, Spot.MARKET)));
        sim.run(3);

        assertEquals(Spot.MARKET, sim.state().villager(7).spot());
        assertEquals(Spot.HOME, sim.state().villager(6).spot(), "nobody else was seen");
    }

    @Test
    void aSightingThatChangesNothingIsNotWrittenDown() {
        // Reported every tick for every villager, so only the changes are worth recording.
        List<Input> sameSpotTwice = List.of(
                new VillagerSeen(2, 7, Spot.MARKET), new VillagerSeen(3, 7, Spot.MARKET));
        Simulation sim = new Simulation(42, EXTERNAL, sameSpotTwice);
        sim.run(4);

        int moves = 0;
        for (Event event : sim.log()) {
            if (event instanceof VillagerMoved) {
                moves++;
            }
        }
        assertEquals(1, moves, "standing still is not news");
    }

    @Test
    void aMarketRemembersItsTradersForTheLengthOfTheWindow() {
        Params windowed = EXTERNAL.withMarketWindowTicks(4);
        Simulation sim = new Simulation(42, windowed,
                List.of(new VillagerSeen(2, 7, Spot.MARKET), new VillagerSeen(3, 7, Spot.WELL)));
        sim.run(4);

        // Left the market on tick 3, still counts as a trader on tick 4.
        assertTrue(sim.state().villager(7).inTheMarket(4, 4));
        assertFalse(sim.state().villager(7).inTheMarket(20, 4), "but not forever");
        assertFalse(sim.state().villager(7).inTheMarket(4, 0),
                "and not at all without a window");
    }

    @Test
    void aVillagerWhoHasNeverBeenToTheMarketIsNotInIt() {
        // Whatever the window, somebody who has never been there is not there. Measuring
        // the gap from "never" instead of guarding it overflows, and a negative answer
        // reads as "just now", which quietly puts the whole village in the market.
        Simulation sim = new Simulation(42, EXTERNAL.withMarketWindowTicks(8), List.of());
        sim.run(20);

        for (Villager villager : sim.state().villagers().values()) {
            assertFalse(villager.inTheMarket(20, 8),
                    villager.name() + " has never been seen anywhere, let alone the market");
            assertFalse(villager.inTheMarket(1, 1000));
        }
    }

    @Test
    void reportingASightingToASimulationThatWalksItsOwnVillagersIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Simulation(42, Params.defaults(),
                List.of(new VillagerSeen(2, 0, Spot.MARKET))));
    }

    @Test
    void reportingAMeetingToASimulationThatWalksItsOwnVillagersIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Simulation(42, Params.defaults(),
                List.of(new ObservedMeeting(2, 0, 1, Spot.WELL))));
    }
}
