package hearsay;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What a played session did, and how much of it was the player's lie.
 *
 * <p>The report answers questions; {@link Chronicle} tells the story. The split is so that
 * anything saying a lie <em>caused</em> something is always backed by a counterfactual: the
 * same recipe rerun without the lies, which Minecraft's recorded meetings, sightings and
 * sales make possible for a played session.
 *
 * <p><strong>Read-only.</strong> It takes a run and returns lines. The counterfactual timelines
 * are new runs from the recipe; the session itself is never touched, and a test holds the
 * world replayed from its log equal before and after.
 *
 * <p>Every figure says what it is measured against, in the sentence rather than a footnote:
 * a lie is only blamed for a bubble within {@link #WITHIN_DAYS} days of it (E38), heard is
 * never reported as believed, the player's own selling is kept apart from the lie
 * ({@link Footprint}), and the earnings say plainly what they assume.
 */
public final class SessionReport {

    /** How long after a lie a bubble may still be laid at its door, as everywhere else. */
    public static final int WITHIN_DAYS = 30;

    /** Past this many lies, only their combined effect is reported. */
    static final int MOST_LIES_ONE_BY_ONE = 3;

    /** The recipe reran into a different village from the one that was played. */
    public static final class NotThisSession extends IllegalStateException {
        NotThisSession(String message) {
            super(message);
        }
    }

    private SessionReport() {
    }

    /**
     * @param recorded the checksum saved with the session, if it has one; empty for recipes
     *                 saved before sessions carried one
     * @throws NotThisSession if the rerun does not reproduce the recorded session
     */
    public static List<String> of(Run played, Optional<String> recorded) {
        String rerun = RecipeFile.checksum(played.log());
        if (recorded.isPresent() && !recorded.get().equals(rerun)) {
            throw new NotThisSession("This recipe does not replay into the session it was saved "
                    + "from, so nothing here would be about what you played. It was most likely "
                    + "saved by a different version of Hearsay.");
        }
        return new SessionReport.Writer(played, recorded.isPresent()).write();
    }

    /**
     * Said at the top of both outputs when a session was played before the level gate (E47),
     * which is every recipe saved before the gate existed. Such a session replays under the
     * reading it was played under, and a reader comparing it with a newer one should know.
     */
    public static final String BEFORE_THE_GATE = "Played under the price reading before E47: a "
            + "price moving back toward normal still counted as news, so gluts could rebound "
            + "into panics that would not happen now.";

    static boolean beforeTheGate(Run played) {
        return played.params().levelGate() == 0;
    }

    /**
     * The report for the chat window at the end of a session: everything but "What happened",
     * which the chronicle tells better and which is the longest part. What is kept is kept
     * word for word, so the attribution and the earnings' stated assumption are exactly the
     * full report's.
     */
    public static List<String> brief(Run played, Optional<String> recorded) {
        List<String> full = of(played, recorded);
        List<String> kept = new ArrayList<>();
        boolean skipping = false;
        for (String line : full) {
            if (line.equals("What happened")) {
                skipping = true;
                if (!kept.isEmpty() && kept.get(kept.size() - 1).isEmpty()) {
                    kept.remove(kept.size() - 1);
                }
                continue;
            }
            if (skipping && line.isEmpty()) {
                skipping = false;
            }
            if (!skipping) {
                kept.add(line);
            }
        }
        return kept;
    }

    /** The goods worth a line: lied about, sold, or driven far enough to have a mood. */
    static Set<Good> goodsOfInterest(Run played) {
        Set<Good> goods = new LinkedHashSet<>();
        Set<Good> lied = liedAbout(played);
        for (Good good : Good.values()) {
            if (!played.params().goods().contains(good)) {
                continue;
            }
            boolean sold = played.inputs().stream().anyMatch(
                    i -> i instanceof PlayerTraded t && Good.of(t.item()) == good);
            if (lied.contains(good) || sold || wentFar(played.log(), good, played.params())) {
                goods.add(good);
            }
        }
        return goods;
    }

    /**
     * The goods the report attributes to the player's lies: their peak and every bubble in
     * them is discussed against the timeline without the lies. The chronicle points at these.
     */
    static Set<Good> liedAbout(Run played) {
        Set<Good> goods = new TreeSet<>();
        for (Input input : played.inputs()) {
            if (input instanceof PlantRumor lie) {
                goods.add(Good.of(lie.claim()));
            }
        }
        return goods;
    }

    private static boolean wentFar(List<Event> log, Good good, Params params) {
        for (Event event : log) {
            if (event instanceof MarketPriceSet set && set.item().equals(good.id())) {
                PriceMood mood = PriceMood.of(set.price(), params.basePrice());
                if (mood == PriceMood.ALARMED || mood == PriceMood.PANIC || mood == PriceMood.GLUT) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The goods named as a sentence would name them: "Wheat". */
    static String title(Good good) {
        return Character.toUpperCase(good.plural().charAt(0)) + good.plural().substring(1);
    }

    static String price(int price, int base) {
        return PriceMood.describe(price, base) + " (" + PriceMood.of(price, base).label().toLowerCase(java.util.Locale.ROOT) + ")";
    }

    static String when(long tick) {
        return "day " + DayPart.dayOf(tick) + ", " + DayPart.of(tick).description();
    }

    static String list(List<String> items) {
        if (items.size() == 1) {
            return items.get(0);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " and "
                + items.get(items.size() - 1);
    }

    /** Named once in full, "Lark, the Town Crier", and by name alone after that. */
    static final class Names {
        private final WorldState world;
        private final Map<Integer, String> labels;
        private final Set<Integer> introduced = new TreeSet<>();

        Names(WorldState world) {
            this.world = world;
            this.labels = Personality.of(world);
        }

        String of(int id) {
            String name = world.villager(id).name();
            if (labels.containsKey(id) && introduced.add(id)) {
                return name + ", " + labels.get(id) + ",";
            }
            return name;
        }

        String plain(int id) {
            return world.villager(id).name();
        }
    }

    private static final class Writer {
        private final Run played;
        private final boolean verified;
        private final Params params;
        private final int base;
        private final List<PlantRumor> lies = new ArrayList<>();
        private final List<PlayerTraded> sales = new ArrayList<>();
        private final Names names;
        private final List<String> out = new ArrayList<>();

        Writer(Run played, boolean verified) {
            this.played = played;
            this.verified = verified;
            this.params = played.params();
            this.base = params.basePrice();
            for (Input input : played.inputs()) {
                if (input instanceof PlantRumor lie) {
                    lies.add(lie);
                } else if (input instanceof PlayerTraded sale) {
                    sales.add(sale);
                }
            }
            this.names = new Names(played.finalState());
        }

        List<String> write() {
            header();
            whatYouDid();
            whatHappened();
            if (!lies.isEmpty()) {
                whatYourLiesDid();
            }
            earnings();
            return out;
        }

        private void header() {
            out.add(String.format(java.util.Locale.ROOT,
                    "Your session: %d village days (%d ticks), %d villagers, seed %d.",
                    Math.round(played.ticks() / 4.0), played.ticks(), params.villagers(),
                    played.seed()));
            out.add(verified
                    ? "Replayed from its recipe and checked against the session it was saved from."
                    : "Saved before sessions carried a checksum: this is the session as this "
                            + "version of Hearsay replays it, and it cannot be checked against "
                            + "what was played.");
            if (beforeTheGate(played)) {
                out.add(BEFORE_THE_GATE);
            }
        }

        private void whatYouDid() {
            out.add("");
            out.add("What you did");
            if (lies.isEmpty() && sales.isEmpty()) {
                out.add("  You told no lies and sold nothing.");
                return;
            }
            for (PlantRumor lie : lies) {
                out.add("  You told " + names.of(lie.villagerId()) + " that "
                        + BeliefReport.inWords(lie.claim(), lie.severity()) + " ("
                        + when(lie.tick()) + ").");
            }
            Map<Good, List<PlayerTraded>> byGood = new TreeMap<>();
            for (PlayerTraded sale : sales) {
                byGood.computeIfAbsent(Good.of(sale.item()), g -> new ArrayList<>()).add(sale);
            }
            byGood.forEach((good, sold) -> {
                int count = sold.stream().mapToInt(PlayerTraded::count).sum();
                int emeralds = sold.stream().mapToInt(PlayerTraded::emeralds).sum();
                Set<Integer> counters = new TreeSet<>();
                Set<Integer> watching = new TreeSet<>();
                for (PlayerTraded sale : sold) {
                    counters.add(sale.villagerId());
                    watching.addAll(sale.witnesses());
                }
                List<String> at = new ArrayList<>();
                counters.forEach(id -> at.add(names.plain(id) + "'s"));
                out.add("  You sold " + good.amount(count) + " for " + emeralds + " emerald"
                        + (emeralds == 1 ? "" : "s") + ", in " + sold.size() + " sale"
                        + (sold.size() == 1 ? "" : "s") + " at " + list(at) + " counter"
                        + (counters.size() == 1 ? "" : "s") + ", watched by " + watching.size()
                        + " villager" + (watching.size() == 1 ? "" : "s") + ".");
            });
        }

        private void whatHappened() {
            out.add("");
            out.add("What happened");
            if (played.log().stream().noneMatch(e -> e instanceof MarketPriceSet)) {
                out.add("  The market never opened: too few villagers stood in it at once to "
                        + "set a price.");
                return;
            }
            Set<Good> goods = goodsOfInterest(played);
            if (goods.isEmpty()) {
                out.add("  No price went further than the market's everyday wobble.");
            }
            RumorStats rumours = RumorStats.of(played.log());
            for (Good good : goods) {
                MarketStats stats = MarketStats.of(played.log(), new Claim(good.id(), ClaimType.SCARCE));
                StringBuilder line = new StringBuilder("  " + title(good) + " peaked at "
                        + price(stats.peakPrice(), base) + " on day " + peakDay(stats) + ".");
                MarketStats.DayOfTrading lowest = null;
                for (MarketStats.DayOfTrading day : stats.daily()) {
                    if (day.lowPrice() > 0 && (lowest == null || day.lowPrice() < lowest.lowPrice())) {
                        lowest = day;
                    }
                }
                if (lowest != null && PriceMood.of(lowest.lowPrice(), base).compareTo(PriceMood.NORMAL) < 0) {
                    line.append(" It fell as low as ").append(price(lowest.lowPrice(), base))
                            .append(" on day ").append(lowest.day()).append('.');
                }
                out.add(line.toString());
                for (PlantRumor lie : lies) {
                    if (Good.of(lie.claim()) != good) {
                        continue;
                    }
                    int family = familyOf(lie);
                    out.add("  " + (lies.size() == 1 ? "Your lie" : "Your lie to "
                            + names.plain(lie.villagerId())) + " reached "
                            + rumours.everHeard(family) + " villagers; at most "
                            + rumours.peakBelieves(family) + " believed it at once.");
                }
                for (Bubble bubble : stats.bubbles()) {
                    out.add("  It bubbled: " + price(bubble.peakPrice(), base) + " on day "
                            + DayPart.dayOf(bubble.peakTick()) + ", back to "
                            + PriceMood.describe(bubble.recoveryPrice(), base) + " by day "
                            + DayPart.dayOf(bubble.recoveryTick()) + ".");
                }
                for (Bubble bust : stats.busts()) {
                    out.add("  It crashed: " + price(bust.peakPrice(), base) + " on day "
                            + DayPart.dayOf(bust.peakTick()) + ", back to "
                            + PriceMood.describe(bust.recoveryPrice(), base) + " by day "
                            + DayPart.dayOf(bust.recoveryTick()) + ".");
                }
            }
            marketBornRumours();
        }

        private void marketBornRumours() {
            WorldState world = new WorldState();
            Map<Integer, Set<Integer>> readers = new TreeMap<>();
            Map<Integer, Long> born = new TreeMap<>();
            Map<Integer, Claim> claims = new TreeMap<>();
            for (Event event : played.log()) {
                if (event instanceof PriceObserved read) {
                    if (!world.rumors().containsKey(read.rumorId())) {
                        born.put(read.rumorId(), read.tick());
                        claims.put(read.rumorId(), read.claim());
                    }
                    if (born.containsKey(read.rumorId())) {
                        readers.computeIfAbsent(read.rumorId(), r -> new TreeSet<>()).add(read.villagerId());
                    }
                }
                world.apply(event);
            }
            born.forEach((id, tick) -> out.add("  On day " + DayPart.dayOf(tick)
                    + " the market started a rumour of its own: that "
                    + BeliefReport.inWords(claims.get(id), 1) + ", read off the price by "
                    + readers.get(id).size() + " villager" + (readers.get(id).size() == 1 ? "" : "s")
                    + "."));
        }

        private int familyOf(PlantRumor lie) {
            for (Event event : played.log()) {
                if (event instanceof RumorPlanted planted && planted.tick() == lie.tick()
                        && planted.villagerId() == lie.villagerId()
                        && planted.claim().equals(lie.claim())) {
                    return planted.rumorId();
                }
            }
            throw new IllegalStateException("A lie with no rumour planted for it: " + lie);
        }

        private void whatYourLiesDid() {
            String yours = lies.size() == 1 ? "your lie" : "your lies";
            out.add("");
            out.add("What " + yours + " did");
            out.add("  Against the same village, with the same meetings and the same sales, and "
                    + (lies.size() == 1 ? "no lie." : "none of your lies."));
            Run without = rerunWithout(input -> input instanceof PlantRumor);
            Run withoutSales = sales.isEmpty() ? null : rerunWithout(input -> input instanceof PlayerTraded);
            Run neither = sales.isEmpty() ? null : rerunWithout(
                    input -> input instanceof PlantRumor || input instanceof PlayerTraded);

            for (Good good : liedAbout(played)) {
                Claim claim = new Claim(good.id(), ClaimType.SCARCE);
                MarketStats with = MarketStats.of(played.log(), claim);
                MarketStats withoutLies = MarketStats.of(without.log(), claim);
                out.add("  " + title(good) + ": peak " + PriceMood.describe(with.peakPrice(), base)
                        + " with " + yours + ", " + PriceMood.describe(withoutLies.peakPrice(), base)
                        + " without.");
                List<PlantRumor> aboutThis = lies.stream().filter(l -> Good.of(l.claim()) == good).toList();
                if (with.bubbles().isEmpty()) {
                    out.add("  It did not become a bubble" + (withoutLies.bubbles().isEmpty()
                            ? "." : ", though without " + yours + " it did, on day "
                            + DayPart.dayOf(withoutLies.bubbles().get(0).peakTick()) + "."));
                }
                for (Bubble bubble : with.bubbles()) {
                    Optional<PlantRumor> blamed = aboutThis.stream()
                            .filter(l -> bubble.peakTick() >= l.tick()
                                    && bubble.peakTick() <= l.tick() + WITHIN_DAYS * 4L)
                            .findFirst();
                    long day = DayPart.dayOf(bubble.peakTick());
                    if (blamed.isEmpty()) {
                        out.add("  The bubble on day " + day + " came more than " + WITHIN_DAYS
                                + " days after " + yours + ", so nothing here lays it at "
                                + (lies.size() == 1 ? "its" : "their") + " door.");
                        continue;
                    }
                    long from = blamed.get().tick();
                    Optional<Bubble> anyway = withoutLies.bubbles().stream()
                            .filter(b -> b.peakTick() >= from && b.peakTick() <= from + WITHIN_DAYS * 4L)
                            .findFirst();
                    out.add(anyway.isEmpty()
                            ? "  The bubble on day " + day + " would not have happened without " + yours + "."
                            : "  The bubble on day " + day + " happened without " + yours
                                    + " too (day " + DayPart.dayOf(anyway.get().peakTick())
                                    + "), so it cannot be laid at " + (lies.size() == 1 ? "its" : "their")
                                    + " door.");
                }
                boolean soldThis = sales.stream().anyMatch(s -> Good.of(s.item()) == good);
                if (soldThis) {
                    Footprint footprint = new Footprint(with, withoutLies,
                            MarketStats.of(withoutSales.log(), claim), MarketStats.of(neither.log(), claim));
                    out.add(String.format(java.util.Locale.ROOT,
                            "  Your own selling, holding %s fixed, moved that peak by %+d points; "
                                    + "that is yours, not the lie's.",
                            lies.size() == 1 ? "the lie" : "the lies", footprint.peakFromTheTrading()));
                }
            }
            if (lies.size() > 1 && lies.size() <= MOST_LIES_ONE_BY_ONE) {
                out.add("  One lie at a time, the others kept:");
                for (PlantRumor lie : lies) {
                    Run withoutThis = rerunWithout(input -> input.equals(lie));
                    Claim claim = new Claim(Good.of(lie.claim()).id(), ClaimType.SCARCE);
                    out.add("    without your lie to " + names.plain(lie.villagerId()) + " ("
                            + when(lie.tick()) + "): " + Good.of(lie.claim()).plural() + " peak "
                            + PriceMood.describe(MarketStats.of(withoutThis.log(), claim).peakPrice(), base)
                            + ", against " + PriceMood.describe(MarketStats.of(played.log(), claim).peakPrice(), base)
                            + " with it.");
                }
            } else if (lies.size() > MOST_LIES_ONE_BY_ONE) {
                out.add("  With more than " + MOST_LIES_ONE_BY_ONE + " lies they are only weighed "
                        + "together: lies told into one village interact, and taking them out one "
                        + "at a time would need a rerun each.");
            }
        }

        private void earnings() {
            if (sales.isEmpty()) {
                return;
            }
            out.add("");
            out.add("What " + (lies.size() == 1 ? "your lie" : "your lies") + " earned you");
            if (lies.isEmpty()) {
                out.add("  You told no lies, so there is no honest village to compare your sales with.");
                return;
            }
            int took = sales.stream().mapToInt(PlayerTraded::emeralds).sum();
            int[] asPlayed = priced(played.inputs());
            int[] honest = priced(played.inputs().stream().filter(i -> !(i instanceof PlantRumor)).toList());
            out.add("  You took " + took + " emeralds for what you sold.");
            if (asPlayed[0] != took) {
                out.add("  (Repriced from the village's own asks, those sales come to " + asPlayed[0]
                        + "; the comparison below uses the repriced figure on both sides.)");
            }
            out.add("  The same sales, at the same times, in a village nobody had lied to, would have "
                    + "paid " + honest[0] + ".");
            int earned = asPlayed[0] - honest[0];
            out.add("  " + (lies.size() == 1 ? "Your lie" : "Your lies") + (earned >= 0 ? " earned you " : " cost you ")
                    + (earned >= 0 ? "+" + earned : String.valueOf(-earned)) + " emerald"
                    + (Math.abs(earned) == 1 ? "" : "s") + ".");
            out.add("  This assumes you made exactly the same sales at exactly the same times in the "
                    + "honest village. It cannot say what you would have done had the prices been "
                    + "different.");
            out.add("  Anything you bought from a villager, such as bread, is not recorded in a "
                    + "session, so it is not counted here.");
        }

        /**
         * Every sale priced as its counter priced it: the trader's own ask on the tick before
         * the sale reached the village, in whole emeralds per bundle — what the plugin put on
         * the counter when the player clicked.
         */
        private int[] priced(List<Input> inputs) {
            Simulation village = new Simulation(played.seed(), params, inputs);
            Map<Long, List<PlayerTraded>> due = new TreeMap<>();
            for (PlayerTraded sale : sales) {
                due.computeIfAbsent(sale.tick() - 1, t -> new ArrayList<>()).add(sale);
            }
            int total = 0;
            for (long tick = 0; tick <= played.ticks(); tick++) {
                if (tick > 0) {
                    village.step();
                }
                for (PlayerTraded sale : due.getOrDefault(tick, List.of())) {
                    Good good = Good.of(sale.item());
                    int ask = village.state().villagers().isEmpty() ? base
                            : (int) Math.round(village.askingPrice(
                                    village.state().villager(sale.villagerId()), good));
                    total += Math.max(1, sale.count() / good.bundle()) * good.emeraldsAt(ask, base);
                }
            }
            return new int[] {total};
        }

        private Run rerunWithout(java.util.function.Predicate<Input> dropped) {
            List<Input> kept = played.inputs().stream().filter(dropped.negate()).toList();
            return Run.execute(played.seed(), params, kept, played.ticks());
        }

        private static long peakDay(MarketStats stats) {
            for (MarketStats.DayOfTrading day : stats.daily()) {
                if (day.highPrice() == stats.peakPrice()) {
                    return day.day();
                }
            }
            return 0;
        }
    }
}
