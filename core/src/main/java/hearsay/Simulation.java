package hearsay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.OptionalInt;
import java.util.Random;
import java.util.TreeMap;

/**
 * Decides what happens, then writes it down. {@link #record(Event)} is the single door
 * every change to the world comes through.
 *
 * <p>A run is fully described by three things: the seed, the {@link Params}, and the list
 * of {@link Input}s. Given those, the whole event log follows.
 */
public final class Simulation {

    /**
     * Fixed names in a fixed order, so villager 3 is the same person for every run of a
     * seed. Village size is however many names there are.
     */
    private static final List<String> NAMES = List.of(
            "Mira", "Bo", "Ada", "Finn", "Nell", "Otto", "Ivy", "Rook", "Sela", "Tam",
            "Vero", "Wynn", "Gil", "Hana", "Jory", "Kit", "Lark", "Nix", "Pim", "Quill",
            "Rue", "Sten", "Tibb", "Ulla", "Vance", "Wren", "Yara", "Zeb", "Alder", "Bree",
            "Cass", "Dov", "Esk", "Fen", "Gwyn", "Hal", "Ines", "Joss", "Kell", "Lior");

    /** The most villagers a village can have, being how many names there are. */
    public static final int MOST_VILLAGERS = NAMES.size();

    /**
     * The village size everything was calibrated at. Ids 0 to 19 keep the names they have
     * always had, so every seed and every recorded experiment still means what it did.
     */
    public static final int VILLAGER_COUNT = 20;

    /** The one item that is traded, for now. */
    public static final String DIAMOND = "diamond";

    private final long seed;
    private final Params params;

    // One generator per subsystem. See RandomStream for why they must not be shared.
    private final Random movement;
    private final Random gossip;
    private final Random mutation;
    private final Random market;
    private final Random neighbourhood;

    /**
     * The inputs, kept so this run can describe itself. Grows during live play, where the
     * world outside supplies them as it goes instead of all at once.
     */
    private final List<Input> inputs = new ArrayList<>();
    /** The same inputs, bucketed by the tick they are due. */
    private final NavigableMap<Long, List<Input>> scheduled = new TreeMap<>();

    private final WorldState state;
    private final List<Event> log = new ArrayList<>();

    public Simulation(long seed) {
        this(seed, Params.defaults(), List.of());
    }

    public Simulation(long seed, Params params, List<Input> inputs) {
        this(seed, params, inputs, new WorldState());
    }

    /**
     * Carries on from a world that already exists, with fresh randomness. This is how a
     * counterfactual forks: take the state just before the lie and run it forward again
     * under a different roll of the dice.
     *
     * <p>Takes ownership of the state it is given. To fork many worlds from one moment,
     * replay the same prefix once per world so each gets its own.
     */
    public static Simulation resume(WorldState state, long branchSeed, Params params,
                                    List<Input> inputs) {
        return new Simulation(branchSeed, params, inputs, state);
    }

    /**
     * A new world carrying on from this one's exact moment under fresh randomness, taking
     * with it everything this simulation knows, including anything not written into
     * {@link WorldState}.
     *
     * <p>Takes over this simulation's state rather than copying it, so the parent should
     * not be run again afterwards. Forking many worlds from one moment is done by
     * replaying the prefix once per world instead, which gives each its own state through
     * the same apply() every other change goes through.
     *
     * <p>A plain copy: this class keeps no mutable state of its own beyond the world and
     * the log it is writing, so there is nothing to carry across by hand. That property is
     * held in place by SimulationStateTest rather than by anyone remembering it.
     */
    public Simulation fork(long branchSeed, List<Input> inputs) {
        return new Simulation(branchSeed, params, inputs, state);
    }

    private Simulation(long seed, Params params, List<Input> inputs, WorldState state) {
        this.seed = seed;
        this.params = params;
        this.inputs.addAll(inputs);
        this.state = state;
        this.movement = RandomStream.MOVEMENT.from(seed);
        this.gossip = RandomStream.GOSSIP.from(seed);
        this.mutation = RandomStream.MUTATION.from(seed);
        this.market = RandomStream.MARKET.from(seed);
        this.neighbourhood = RandomStream.NEIGHBOURHOOD.from(seed);
        for (Input input : this.inputs) {
            rejectMismatchedMeeting(input);
            scheduled.computeIfAbsent(input.tick(), t -> new ArrayList<>()).add(input);
        }
    }

    private void rejectMismatchedMeeting(Input input) {
        boolean fromOutside = input instanceof ObservedMeeting || input instanceof VillagerSeen;
        if (fromOutside && params.meetingSource() != MeetingSource.EXTERNAL) {
            throw new IllegalArgumentException("A sighting was given to a simulation that "
                    + "moves its own villagers. Mixing the two would have people in two "
                    + "places at once.");
        }
    }

    public void step() {
        // The tick is read from the world rather than counted here, so this class keeps no
        // running total of its own for a forked world to lose. That works because every
        // tick records at least one event, which the check at the end of this method holds
        // to rather than trusting.
        long tick = state.tick() + 1;
        record(new TickStarted(tick));
        if (tick == 1) {
            createVillagers(tick);
        }
        applyInputs(tick);
        if (params.meetingSource() == MeetingSource.SIMULATED) {
            moveEveryone(tick, DayPart.of(tick));
            holdMeetings(tick);
        }
        // Price first, then the people standing there read it: within a tick, belief
        // moves the price and the price moves belief, in that order.
        advanceMarketNoise(tick);
        observeTheMarket(tick, settleMarketPrice(tick));
        if (DayPart.of(tick) == DayPart.NIGHT) {
            record(new DayEnded(tick, params.dailyDecay(), params.forgetThreshold()));
        }

        // Nothing here depends on anyone meeting anyone: a tick where nobody meets is
        // ordinary, and most nights are one. What the clock does depend on is that
        // something was written down, because the world's tick is where the next one
        // starts from. If a tick ever records nothing, the clock stops and the next tick
        // silently repeats this one, so it is caught here rather than left to be noticed
        // in a counterfactual months later.
        if (state.tick() != tick) {
            throw new IllegalStateException("Tick " + tick + " recorded no events, so the "
                    + "world's clock did not move. Every tick must write down at least one "
                    + "thing, or the next tick will repeat this one.");
        }
    }

    public void run(int ticks) {
        for (int i = 0; i < ticks; i++) {
            step();
        }
    }

    /**
     * The village is built by events like everything else. Creating villagers in the
     * constructor instead would put part of the world outside the log, and replay would
     * silently stop describing the whole world.
     */
    private void createVillagers(long tick) {
        for (int id = 0; id < params.villagers(); id++) {
            // The order of these three rolls is part of the seed contract: reordering
            // them gives every seed a different village.
            Traits traits = new Traits(movement.nextDouble(), movement.nextDouble(), movement.nextDouble());
            // Drawn from its own stream, so a village with mixing at 1.0 walks and talks
            // exactly as it did before neighbourhoods existed.
            record(new VillagerCreated(tick, id, NAMES.get(id), traits,
                    neighbourhood.nextInt(params.neighbourhoods())));
        }
    }

    /** Turns any inputs scheduled for this tick into input events. */
    private void applyInputs(long tick) {
        for (Input input : scheduled.getOrDefault(tick, List.of())) {
            switch (input) {
                case PlantRumor p -> record(new RumorPlanted(tick, state.nextRumorId(),
                        p.claim(), p.severity(), p.villagerId(), params.plantedConfidence()));
                // Somebody outside saw these two together. Where they are is as much news
                // as who they are with, since the market is made of whoever stands in it.
                // Only worth writing down when it changes something: a villager who has
                // not moved has told the world nothing new.
                case VillagerSeen seen -> {
                    if (state.villager(seen.villagerId()).spot() != seen.spot()) {
                        record(new VillagerMoved(tick, seen.villagerId(), seen.spot()));
                    }
                }
                case ObservedMeeting m -> {
                    record(new VillagerMoved(tick, m.a(), m.spot()));
                    record(new VillagerMoved(tick, m.b(), m.spot()));
                    record(new VillagersMet(tick, m.a(), m.b(), m.spot()));
                    exchangeNews(tick, m.a(), m.b());
                }
            }
        }
    }

    /** Every villager picks a spot for this part of the day, in id order. */
    private void moveEveryone(long tick, DayPart part) {
        for (Villager villager : state.villagers().values()) {
            record(new VillagerMoved(tick, villager.id(), pickSpot(part)));
        }
    }

    /** A weighted roll over the spots, in declaration order so the draw is reproducible. */
    private Spot pickSpot(DayPart part) {
        int total = 0;
        for (Spot spot : Spot.values()) {
            total += part.weight(spot);
        }
        int roll = movement.nextInt(total);
        for (Spot spot : Spot.values()) {
            roll -= part.weight(spot);
            if (roll < 0) {
                return spot;
            }
        }
        throw new IllegalStateException("Weights did not sum to the total for " + part);
    }

    /**
     * At every public spot, whoever is there is shuffled and paired off. An odd villager
     * out meets nobody this tick. Villagers only meet those standing next to them, which
     * is the brake that will make news spread in waves rather than all at once.
     */
    private void holdMeetings(long tick) {
        for (Spot spot : Spot.values()) {
            // HOME is not one place: it stands for twenty separate houses, so two
            // villagers being home at the same time are not in the same room. Pairing
            // here would turn every night into a village-wide mixing round, because the
            // night weights put nearly everyone home at once, and that would undo the
            // locality brake the rest of this method exists to create.
            if (spot == Spot.HOME) {
                continue;
            }

            // Whoever is here, split into the pools that can actually see each other.
            // The village-wide pool comes first and keeps id order, so a village with
            // mixing at 1.0 shuffles exactly the list it always shuffled.
            List<Integer> everyone = new ArrayList<>();
            // A TreeMap on principle rather than on evidence: neighbourhood ids are small
            // integers, so a HashMap happens to iterate them in the same order today and a
            // test could not tell the two apart. It would stop being true the moment the
            // ids stopped being small, and pairs decided by hash order are exactly the bug
            // this codebase refuses to risk.
            Map<Integer, List<Integer>> byNeighbourhood = new TreeMap<>();
            for (Villager villager : state.villagers().values()) { // id order
                if (villager.spot() != spot) {
                    continue;
                }
                if (neighbourhood.nextDouble() < params.mixing()) {
                    everyone.add(villager.id());
                } else {
                    byNeighbourhood.computeIfAbsent(villager.neighbourhood(), k -> new ArrayList<>())
                            .add(villager.id());
                }
            }

            List<List<Integer>> pools = new ArrayList<>();
            pools.add(everyone);
            pools.addAll(byNeighbourhood.values()); // neighbourhood order, never hash order
            for (List<Integer> pool : pools) {
                Collections.shuffle(pool, movement);
                for (int i = 0; i + 1 < pool.size(); i += 2) {
                    int a = pool.get(i);
                    int b = pool.get(i + 1);
                    record(new VillagersMet(tick, a, b, spot));
                    exchangeNews(tick, a, b);
                }
            }
        }
    }

    /** Both villagers get a turn to speak, the lower id first so the order never varies. */
    private void exchangeNews(long tick, int a, int b) {
        int first = Math.min(a, b);
        int second = Math.max(a, b);
        maybeTell(tick, first, second);
        maybeTell(tick, second, first);
    }

    private void maybeTell(long tick, int tellerId, int listenerId) {
        Villager teller = state.villager(tellerId);
        Belief toTell = teller.strongestBeliefWorthTelling(params.tellThreshold());
        if (toTell == null) {
            return;
        }
        // Eager gossips with strong beliefs talk most.
        if (gossip.nextDouble() >= teller.traits().gossip() * toTell.confidence()) {
            return;
        }

        int toldRumorId = growInTheTelling(tick, toTell.rumorId());
        Claim claim = state.rumor(toldRumorId).claim();
        Villager listener = state.villager(listenerId);
        Belief held = listener.belief(claim);
        double heard = confidenceAfterHearing(listener, claim, toTell, tellerId);

        record(new RumorTold(tick, tellerId, listenerId, toldRumorId,
                worseOf(held, toldRumorId), heard));
    }

    /**
     * Which version of the claim the listener ends up holding. Once you have heard that
     * the diamonds are gone, being told they are merely scarce does not walk it back.
     * A tie goes to what was just said, so the freshest telling wins.
     */
    private int worseOf(Belief held, int toldRumorId) {
        if (held == null) {
            return toldRumorId;
        }
        return state.rumor(held.rumorId()).severity() > state.rumor(toldRumorId).severity()
                ? held.rumorId()
                : toldRumorId;
    }

    /**
     * A rumor sometimes gains a notch of severity as it passes on. The child keeps a link
     * to its parent, so the family tree stays intact.
     *
     * @return the rumor id the listener actually receives
     */
    private int growInTheTelling(long tick, int rumorId) {
        Rumor told = state.rumor(rumorId);
        // "Gone" is as bad as it gets. Rolling here anyway would spawn a child identical
        // to its parent, cluttering the family tree with exaggerations that exaggerate
        // nothing.
        if (told.severity() >= Rumor.MAX_SEVERITY) {
            return rumorId;
        }
        if (mutation.nextDouble() >= params.mutationChance()) {
            return rumorId;
        }
        int grown = told.severity() + 1;
        int childId = state.nextRumorId();
        record(new RumorMutated(tick, childId, rumorId, grown));
        return childId;
    }

    /**
     * How sure the listener ends up, as one rule for every telling: a piece of evidence of
     * weight w combines with what is already held by
     * {@code new = 1 - (1 - old) * (1 - w)}. Two independent halves leave a quarter of the
     * doubt rather than none of it, so confidence climbs with corroboration and never
     * passes 1. A first hearing is the same rule with nothing held, which lands exactly at
     * credulity times the teller's confidence.
     */
    private double confidenceAfterHearing(Villager listener, Claim claim, Belief toTell,
                                          int tellerId) {
        Belief held = listener.belief(claim);
        double before = held == null ? 0 : held.confidence();

        double weight = listener.traits().credulity() * toTell.confidence()
                * sourceWeight(held, toTell, tellerId, listener.id());
        if (listener.belief(claim.opposite()) != null) {
            weight *= params.contradictionFactor();
        }
        return Math.min(1.0, 1 - (1 - before) * (1 - weight));
    }

    /**
     * How much the listener should count this teller as evidence.
     *
     * <p>A source the belief has not already come through is independent corroboration and
     * counts in full. A source already in the listener's chain is the same news arriving by
     * a route they have already counted, so it is worth only {@code repeatWeight}: that
     * covers the direct teller repeating themselves and anyone further back that the claim
     * reached them through. An echo, where what the teller is passing on came through the
     * listener in the first place, is worth nothing at all.
     *
     * <p>A villager who has forgotten the claim entirely has lost the history with it, so
     * hearing it again is genuinely new.
     */
    private double sourceWeight(Belief held, Belief toTell, int tellerId, int listenerId) {
        if (held == null) {
            return 1.0;
        }
        if (toTell.cameThrough(listenerId)) {
            return 0.0;
        }
        return held.cameThrough(tellerId) ? params.repeatWeight() : 1.0;
    }

    /**
     * What one villager wants for a diamond. Belief in scarcity pushes the ask up, belief
     * in plenty pushes it down.
     *
     * <p>The two beliefs are netted against each other, because a villager can hold both
     * at once, and the result is clamped: confidence times the severity weighting reaches
     * 2.0, and a conviction beyond total conviction should not exist. One consequence is
     * that half-sure of the worst version and certain of the mildest saturate at the same
     * ask.
     */
    double askingPrice(Villager villager) {
        double scarcityBelief = strengthOf(villager, ClaimType.SCARCE)
                - strengthOf(villager, ClaimType.ABUNDANT);
        double clamped = Math.max(-1, Math.min(1, scarcityBelief));
        return params.basePrice() * (1 + params.priceSensitivity() * clamped);
    }

    /** Confidence in one side of the claim, weighted by how bad the version they hold is. */
    private double strengthOf(Villager villager, ClaimType type) {
        Belief held = villager.belief(new Claim(DIAMOND, type));
        if (held == null) {
            return 0;
        }
        return held.confidence() * severityWeight(state.rumor(held.rumorId()).severity());
    }

    /** Severity 1, 2 and 3 count for 1x, 1.5x and 2x. */
    private static double severityWeight(int severity) {
        return 1 + (severity - 1) * 0.5;
    }

    /**
     * The wobble carries over from tick to tick instead of being drawn fresh, so a run of
     * steps in the same direction can build into something the village notices. Drawn
     * every tick whether or not the market opens, since it is a property of the day
     * rather than of who turned up.
     */
    private void advanceMarketNoise(long tick) {
        double step = params.marketNoise() * (market.nextDouble() * 2 - 1);
        record(new MarketNoiseSet(tick, params.noiseDecay() * state.marketNoiseLevel() + step));
    }

    /**
     * The market price is the average ask of whoever is standing there. Below a quorum
     * there is no market and no price is set.
     *
     * <p>This was the median until E20. A median asks which side of the middle a villager
     * falls on and never how strongly they feel, so it cannot move at all until believers
     * are more than half of the people standing in the market at one moment. In the model
     * that went unnoticed for five weeks, because a planted rumor there reaches most of the
     * village and a majority does move a median. In a real village belief reaches about
     * half, and the market is a small wandering subset of that, so the median never crossed
     * and a lie told to a third of the village moved the price by exactly nothing.
     *
     * <p>The median was chosen so that one extreme villager could not drag the market. That
     * was never a risk: {@link #askingPrice} clamps belief to [-1, 1], so every ask is
     * bounded, and one utterly convinced villager in a market of twelve moves the average
     * by a few percent. The insurance was real; the thing it insured against was not.
     */
    private OptionalInt settleMarketPrice(long tick) {
        List<Double> asks = new ArrayList<>();
        for (Villager villager : state.villagers().values()) { // id order
            if (villager.inTheMarket(tick, params.marketWindowTicks())) {
                asks.add(askingPrice(villager));
            }
        }
        if (asks.size() < params.marketQuorum()) {
            return OptionalInt.empty();
        }
        double total = 0;
        for (double ask : asks) { // id order, and addition of a sorted-by-id list either way
            total += ask;
        }
        double average = total / asks.size();

        int price = Math.max(1, (int) Math.round(average * (1 + state.marketNoiseLevel())));
        record(new MarketPriceSet(tick, price, asks.size()));
        return OptionalInt.of(price);
    }

    /**
     * Everyone at the market reads the price, and what they read into it is the move
     * since they last drew a conclusion, not the level. A price that climbs is evidence
     * of scarcity; one that falls back is evidence of plenty; one that holds steady,
     * however high, is no evidence at all.
     *
     * <p>That last part is what lets a bubble deflate. While the price is climbing it
     * keeps confirming itself, but the moment it levels off the confirmations stop, decay
     * starts winning, asks come down, and the fall then reads as evidence in the other
     * direction.
     *
     * <p>A villager who has never concluded anything measures against the base price, so
     * the first move still registers as a level. Measuring the first one as a change would
     * leave nobody with anything to compare against, and the loop could never start.
     */
    private void observeTheMarket(long tick, OptionalInt settledThisTick) {
        // Only a price set this very tick is there to be read. Asking the world whether its
        // clock had moved used to answer that; it cannot now that the tick moves itself.
        if (settledThisTick.isEmpty()) {
            return;
        }
        int price = settledThisTick.getAsInt();

        for (Villager villager : state.villagers().values()) { // id order
            if (villager.spot() != Spot.MARKET) {
                continue;
            }
            int anchor = villager.lastObservedPrice().orElse(params.basePrice());
            double move = (price - anchor) / (double) anchor;
            if (Math.abs(move) <= params.observationThreshold()) {
                continue;
            }

            Claim claim = new Claim(DIAMOND, move > 0 ? ClaimType.SCARCE : ClaimType.ABUNDANT);
            // The threshold decides whether the move is noticed; fullMoveSize decides
            // how much a noticed move is worth. Scaling by the raw move instead would
            // make every observation a fraction of a fraction.
            double weight = params.observationWeight()
                    * Math.min(1, Math.abs(move) / params.fullMoveSize());
            Belief held = villager.belief(claim);
            double before = held == null ? 0 : held.confidence();
            double after = Math.min(1.0, 1 - (1 - before) * (1 - weight));
            // The rumor the belief ends up on: whatever they already held, or a fresh
            // observation-born family if this conclusion is new to the village.
            int rumorId = held != null ? held.rumorId() : observedRumorFor(claim);
            record(new PriceObserved(tick, villager.id(), price, claim, rumorId, after));
        }
    }

    /** The id of the observation-born family for this claim, existing or about to exist. */
    private int observedRumorFor(Claim claim) {
        for (Rumor rumor : state.rumors().values()) { // id order
            if (rumor.isObserved() && rumor.claim().equals(claim)) {
                return rumor.id();
            }
        }
        return state.nextRumorId();
    }

    private void record(Event event) {
        log.add(event);
        state.apply(event);
    }

    public Params params() { return params; }
    public WorldState state() { return state; }
    public List<Event> log() { return Collections.unmodifiableList(log); }

    /** Just the events that came from outside: the recipe a counterfactual varies. */
    public List<Event> inputEvents() {
        List<Event> found = new ArrayList<>();
        for (Event event : log) {
            if (event.isInput()) {
                found.add(event);
            }
        }
        return found;
    }

    /**
     * Rebuilds the world from an event list. Needs nothing but the events: every one of
     * them carries whatever its own consequences depend on.
     */
    public static WorldState replay(List<Event> events) {
        WorldState fresh = new WorldState();
        for (Event event : events) {
            fresh.apply(event);
        }
        return fresh;
    }

    public long seed() { return seed; }

    /** The inputs this simulation was given, in the order they were given. */
    public List<Input> inputs() { return Collections.unmodifiableList(inputs); }

    /**
     * Adds an input for a tick that has not happened yet.
     *
     * <p>For live play. A headless run knows every input before it starts; a game does not,
     * because the player has not done it yet and Minecraft has not put anyone anywhere yet.
     * The recipe still records everything, so the session replays exactly either way.
     */
    public void schedule(Input input) {
        if (input.tick() <= state.tick()) {
            throw new IllegalArgumentException("Tick " + input.tick() + " has already "
                    + "happened; the world is at " + state.tick() + ". Inputs cannot "
                    + "change the past.");
        }
        rejectMismatchedMeeting(input);
        inputs.add(input);
        scheduled.computeIfAbsent(input.tick(), t -> new ArrayList<>()).add(input);
    }

    /**
     * Everything needed to reproduce this run, bundled with the log it produced, so the
     * recipe and its result cannot be separated and later mismatched.
     */
    public Run toRun() {
        return new Run(seed, params, inputs, (int) state.tick(), log);
    }
}
