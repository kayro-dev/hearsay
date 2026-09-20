package hearsay.paper;

import hearsay.Claim;
import hearsay.ClaimType;
import hearsay.MeetingSource;
import hearsay.ObservedMeeting;
import hearsay.Params;
import hearsay.PlantRumor;
import hearsay.ProximityPairing;
import hearsay.RecipeFile;
import hearsay.Run;
import hearsay.Simulation;
import hearsay.Spot;
import hearsay.Villager;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * One bound village: which real villagers stand for which simulated ones, and the
 * simulation they drive.
 *
 * <p>Minecraft owns the bodies and Hearsay owns the minds. Every tick this reads where the
 * real villagers are, works out who is standing near whom, and reports those pairs to the
 * simulation as inputs. Nothing here decides where anybody walks.
 *
 * <p>Deliberately free of anything that draws on the screen, so what the simulation is told
 * stays separate from what the player is shown.
 */
final class VillageSession {

    /** How close two villagers must be to be counted as talking. */
    static final double TALKING_RANGE = 6.0;

    /**
     * Every reported meeting is placed at the market for now, which treats the whole bound
     * village as its marketplace. The price needs to know where people are, and a pair on
     * its own does not say. Mapping real locations to spots — the bell, the workstations —
     * is the next step, and wants doing against a real village rather than guessed at here.
     */
    private static final Spot SPIKE_SPOT = Spot.MARKET;

    private final long seed;
    private final Simulation simulation;
    private final Map<Integer, UUID> bodies = new LinkedHashMap<>();
    private final Claim tracked;

    private VillageSession(long seed, Simulation simulation, Map<Integer, UUID> bodies) {
        this.seed = seed;
        this.simulation = simulation;
        this.bodies.putAll(bodies);
        this.tracked = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);
    }

    /**
     * Binds the villagers standing near the player to simulated ones.
     *
     * <p>Bodies are sorted by their own id before being numbered, so the same village binds
     * the same way twice. Villagers who wander in later are ignored: giving somebody a mind
     * halfway through would mean a run that cannot be replayed from its recipe.
     */
    static VillageSession bind(long seed, List<UUID> nearbyVillagers) {
        List<UUID> inOrder = new ArrayList<>(nearbyVillagers);
        inOrder.sort(Comparator.comparing(UUID::toString));

        Map<Integer, UUID> bodies = new LinkedHashMap<>();
        for (int id = 0; id < inOrder.size() && id < Simulation.MOST_VILLAGERS; id++) {
            bodies.put(id, inOrder.get(id));
        }

        // The village is however big it is. Simulating twenty minds for nine bodies left
        // eleven villagers at home forever and made every figure counted out of twenty
        // read low, with half the village unreachable.
        Params params = Params.defaults()
                .withMeetingSource(MeetingSource.EXTERNAL)
                .withVillagers(bodies.size());
        return new VillageSession(seed, new Simulation(seed, params, List.of()), bodies);
    }

    int boundCount() {
        return bodies.size();
    }

    UUID bodyOf(int villagerId) {
        return bodies.get(villagerId);
    }

    Map<Integer, UUID> bodies() {
        return Map.copyOf(bodies);
    }

    Claim tracked() {
        return tracked;
    }

    long tick() {
        return simulation.state().tick();
    }

    OptionalInt price() {
        return simulation.state().marketPrice();
    }

    /**
     * Reports who was standing near whom, then advances the simulation one tick.
     *
     * @param positions where each bound villager is, by simulation id
     * @return the tellings that happened, for showing on the screen
     */
    List<Telling> advance(Map<Integer, Location> positions) {
        long nextTick = simulation.state().tick() + 1;

        List<ProximityPairing.Position> standing = new ArrayList<>();
        positions.forEach((id, where) -> standing.add(
                new ProximityPairing.Position(id, where.getX(), where.getY(), where.getZ())));

        for (ProximityPairing.Encounter encounter : ProximityPairing.pairsWithin(standing, TALKING_RANGE)) {
            simulation.schedule(new ObservedMeeting(nextTick, encounter.a(), encounter.b(), SPIKE_SPOT));
        }

        int eventsBefore = simulation.log().size();
        simulation.step();
        return Telling.from(simulation.log().subList(eventsBefore, simulation.log().size()));
    }

    /** Plants a rumor in one villager, on the tick that has not happened yet. */
    void plantRumorIn(int villagerId, ClaimType type) {
        simulation.schedule(new PlantRumor(simulation.state().tick() + 1,
                new Claim(Simulation.DIAMOND, type), 1, villagerId));
    }

    /** What each bound villager makes of the claim, for the text above their head. */
    Map<Integer, Double> confidences() {
        Map<Integer, Double> held = new LinkedHashMap<>();
        for (int id : bodies.keySet()) {
            Villager villager = simulation.state().villager(id);
            var belief = villager.belief(tracked);
            if (belief != null) {
                held.put(id, belief.confidence());
            }
        }
        return held;
    }

    String nameOf(int villagerId) {
        return simulation.state().villager(villagerId).name();
    }

    /** Saves the recipe, which the headless tools can rerun and ask questions of. */
    Path save(Path folder) {
        Path file = folder.resolve("session-" + seed + "-" + System.currentTimeMillis() + ".hearsay");
        RecipeFile.write(simulation.toRun(), file);
        return file;
    }

    Run asRun() {
        return simulation.toRun();
    }

    static long seedFor(Player player, String[] arguments) {
        if (arguments.length > 1) {
            try {
                return Long.parseLong(arguments[1]);
            } catch (NumberFormatException ignored) {
                // fall through to a seed of the player's own
            }
        }
        return player.getUniqueId().getMostSignificantBits();
    }
}
