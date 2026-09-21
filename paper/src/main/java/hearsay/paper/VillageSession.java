package hearsay.paper;

import hearsay.BeliefReport;
import hearsay.Claim;
import hearsay.ClaimType;
import hearsay.MeetingSource;
import hearsay.ObservedMeeting;
import hearsay.Params;
import hearsay.Personality;
import hearsay.PlantRumor;
import hearsay.PlayerTraded;
import hearsay.ProximityPairing;
import hearsay.RealityChecked;
import hearsay.RecipeFile;
import hearsay.Sighting;
import hearsay.SurveyFile;
import hearsay.Run;
import hearsay.Seeds;
import hearsay.Simulation;
import hearsay.Spot;
import hearsay.VillagerSeen;
import hearsay.SpotMapper;
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

    private final long seed;
    private final Simulation simulation;
    private final Map<Integer, UUID> bodies = new LinkedHashMap<>();
    private final Claim tracked;

    /**
     * Where everybody stood, tick by tick. Held in memory and written beside the recipe at
     * the end, since it is evidence about the village rather than part of the run.
     */
    private final List<Sighting> survey = new ArrayList<>();

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

    /**
     * Whether the village exists yet. Villagers are created by the first tick like every
     * other change, so for the first few seconds after binding there are bodies but no
     * minds, and anything that asks about a villager will not find one.
     */
    boolean awake() {
        return !simulation.state().villagers().isEmpty();
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
    List<Telling> advance(Map<Integer, Location> positions, Map<Integer, Spot> spots) {
        long nextTick = simulation.state().tick() + 1;

        // Everything sold at each counter since the last tick, as one sale apiece.
        pendingSales.forEach((villagerId, running) -> simulation.schedule(new PlayerTraded(
                nextTick, villagerId, running[0], running[1],
                pendingWatchers.getOrDefault(villagerId, new java.util.TreeSet<>()))));
        pendingSales.clear();
        pendingWatchers.clear();

        // Where everybody is, every tick, whether or not they are talking to anyone. The
        // market is then something measured rather than guessed at from who happened to be
        // gossiping in it. Only the changes reach the log.
        spots.forEach((id, spot) -> simulation.schedule(new VillagerSeen(nextTick, id, spot)));

        List<ProximityPairing.Position> standing = new ArrayList<>();
        positions.forEach((id, where) -> standing.add(
                new ProximityPairing.Position(id, where.getX(), where.getY(), where.getZ())));

        // A different shuffle every tick, derived from the session seed, so who talks to
        // whom rotates and the run still reproduces from its own recipe.
        long shuffle = Seeds.branch(seed, (int) nextTick);
        for (ProximityPairing.Encounter encounter
                : ProximityPairing.pairsWithin(standing, TALKING_RANGE, shuffle)) {
            Spot a = spots.getOrDefault(encounter.a(), SpotMapper.ANYWHERE_ELSE);
            Spot b = spots.getOrDefault(encounter.b(), SpotMapper.ANYWHERE_ELSE);
            // Two villagers in bed are in two beds, not one room. The headless model skips
            // meetings at home for the same reason, and a night where nobody gossips is a
            // night, not a fault.
            if (a == Spot.HOME && b == Spot.HOME) {
                continue;
            }
            // Where they met is where the one who is somewhere definite is standing; a tie
            // goes to the lower id, which the pairing already put first.
            simulation.schedule(new ObservedMeeting(nextTick, encounter.a(), encounter.b(),
                    a == SpotMapper.ANYWHERE_ELSE ? b : a));
        }

        int eventsBefore = simulation.log().size();
        simulation.step();
        return Telling.from(simulation.log().subList(eventsBefore, simulation.log().size()),
                simulation.state());
    }

    /** Notes where everybody was standing, for sweeping the mapping afterwards. */
    void survey(long tick, Map<Integer, org.bukkit.entity.Villager> bodies) {
        bodies.forEach((id, body) -> survey.add(Whereabouts.sightingOf(tick, id, body)));
    }

    /**
     * Sales waiting to be reported, by the villager who took them.
     *
     * <p>Shift-clicking a trade fires one event per item, so selling a dozen diamonds
     * arrives as a dozen separate sales of one. E34 found that this is not merely untidy:
     * the sight of goods saturates, so a stack sold one at a time applies the saturating
     * weight sixty-four times over and convinces about three times as much as the same
     * stack sold at once. That is backwards — one large sale is the stronger sight.
     *
     * <p>So they are gathered here and reported as what they were: one sale, of everything
     * that crossed that counter before the tick came round, seen by everyone who saw any
     * part of it.
     */
    private final Map<Integer, int[]> pendingSales = new LinkedHashMap<>();
    private final Map<Integer, java.util.NavigableSet<Integer>> pendingWatchers =
            new LinkedHashMap<>();

    /**
     * Records that somebody sold a villager diamonds, in front of whoever was near enough.
     *
     * <p>Held until the tick comes round rather than scheduled at once, so a burst of
     * clicks at one counter becomes one sale. Inputs still land on a tick boundary, exactly
     * as planting a rumour does.
     *
     * @param witnesses simulation ids of everyone who saw it, the trader included
     */
    void recordTrade(int villagerId, int count, int emeralds,
                     java.util.NavigableSet<Integer> witnesses) {
        int[] running = pendingSales.computeIfAbsent(villagerId, id -> new int[2]);
        running[0] += count;
        running[1] += emeralds;
        pendingWatchers.computeIfAbsent(villagerId, id -> new java.util.TreeSet<>())
                .addAll(witnesses);
    }

    /** How much has been sold at this counter since the last tick, for telling the player. */
    int soldSoFar(int villagerId) {
        int[] running = pendingSales.get(villagerId);
        return running == null ? 0 : running[0];
    }

    /** Who in this village answers to what. Reads the world; changes nothing. */
    Map<Integer, String> labels() {
        return Personality.of(simulation.state());
    }

    /** What this villager would tell you if you asked. Reads the world; changes nothing. */
    List<String> whatTheyHeard(int villagerId) {
        return BeliefReport.of(simulation.state(), villagerId);
    }

    /**
     * Tells the villagers standing in the market how much the village has to hand.
     *
     * <p>Only those in the market: a villager asleep at the other end of the village has
     * not looked at anything. Scheduled for the tick that has not happened yet, like every
     * other input.
     */
    void reportStock(int howMany) {
        long nextTick = simulation.state().tick() + 1;
        simulation.state().villagers().forEach((id, villager) -> {
            if (villager.spot() == Spot.MARKET) {
                simulation.schedule(new RealityChecked(nextTick, id, Simulation.DIAMOND, howMany));
            }
        });
    }

    /** Which bound villager this body is, or null if it is not one of ours. */
    Integer idOf(UUID body) {
        for (Map.Entry<Integer, UUID> bound : bodies.entrySet()) {
            if (bound.getValue().equals(body)) {
                return bound.getKey();
            }
        }
        return null;
    }

    /** Plants a rumor in one villager, on the tick that has not happened yet. */
    void plantRumorIn(int villagerId, ClaimType type) {
        simulation.schedule(new PlantRumor(simulation.state().tick() + 1,
                new Claim(Simulation.DIAMOND, type), 1, villagerId));
    }

    /** What each bound villager would charge, as a price index, for the text above them. */
    Map<Integer, Integer> asks() {
        Map<Integer, Integer> asks = new LinkedHashMap<>();
        simulation.state().villagers().forEach((id, villager) ->
                asks.put(id, (int) Math.round(simulation.askingPrice(villager))));
        return asks;
    }

    /**
     * What each bound villager makes of the claim, for the text above their head.
     *
     * <p>Read from the simulation's villagers rather than from the bound bodies, which is
     * the difference between working and bringing the session down. A body is bound the
     * moment the village is, but the mind inside it is created by the first tick like every
     * other change, so asking the bound ids what they believe before that tick has run
     * asks about villagers who do not exist yet.
     *
     * <p>This is a read for the screen and must never throw. When it did, the exception
     * came out of the scheduled tick before the villagers were created, so they were never
     * created, so it threw again on the next tick and every tick after: the village stayed
     * "still waking up" for ever and no command worked. The same mistake cost a session
     * once before through {@code /hearsay who}.
     */
    Map<Integer, Double> confidences() {
        Map<Integer, Double> held = new LinkedHashMap<>();
        simulation.state().villagers().forEach((id, villager) -> {
            if (!bodies.containsKey(id)) {
                return;
            }
            var belief = villager.belief(tracked);
            if (belief != null) {
                held.put(id, belief.confidence());
            }
        });
        return held;
    }

    /** How much this villager talks, which decides whether a rumor told to them travels. */
    double gossipOf(int villagerId) {
        return simulation.state().villager(villagerId).traits().gossip();
    }

    String nameOf(int villagerId) {
        return simulation.state().villager(villagerId).name();
    }

    /** Saves the recipe, which the headless tools can rerun and ask questions of. */
    Path save(Path folder) {
        String stamp = seed + "-" + System.currentTimeMillis();
        Path file = folder.resolve("session-" + stamp + ".hearsay");
        RecipeFile.write(simulation.toRun(), file);
        if (!survey.isEmpty()) {
            SurveyFile.write(survey, folder.resolve("survey-" + stamp + ".csv"));
        }
        return file;
    }

    int sightingsRecorded() {
        return survey.size();
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
