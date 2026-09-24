package hearsay;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The story of a played session: what happened, in order, and nothing about why.
 *
 * <p>It reads the log as played and nothing else — no reruns, no counterfactual — so it never
 * says a lie caused anything. Where {@link SessionReport} discusses the same moment against
 * the timeline without the lies, the line carries a pointer, {@link #SEE_THE_REPORT}, and
 * the claim stays in the report where it is backed.
 *
 * <p>Curated rather than complete. The narrator tells every telling; this keeps the moments a
 * reader would retell: the lies, the first to pass one on and the first to believe it, a
 * quarter and then half of the village having heard, the most that believed at once, each
 * time it grew in the telling, the market starting a rumour of its own, the price turning
 * alarmed or glutted and coming back, its peak, a bubble or crash completing, and each day's
 * selling at each counter.
 *
 * <p><strong>Read-only</strong>, like {@link BeliefReport}: a log in, lines out, and a test holds
 * the replayed world unchanged.
 */
public final class Chronicle {

    /** Marks a moment the report weighs against a village nobody lied to. Not a claim. */
    public static final String SEE_THE_REPORT = "(see the report for what caused this)";

    private Chronicle() {
    }

    public static List<String> of(Run played) {
        return new Teller(played).tell();
    }

    private static final class Teller {
        private final Run played;
        private final int base;
        private final int villagers;
        private final SessionReport.Names names;
        private final Set<Good> goods;
        private final Set<Good> discussed;
        /** Every rumour family's claim: the lies, and the ones the market started. */
        private final Map<Integer, Claim> claimOf = new TreeMap<>();
        /** Lines by tick, in the order they were found. */
        private final NavigableMap<Long, List<String>> lines = new TreeMap<>();

        Teller(Run played) {
            this.played = played;
            this.base = played.params().basePrice();
            this.villagers = played.params().villagers();
            this.names = new SessionReport.Names(played.finalState());
            this.goods = SessionReport.goodsOfInterest(played);
            this.discussed = SessionReport.liedAbout(played);
        }

        private void at(long tick, String line) {
            lines.computeIfAbsent(tick, t -> new ArrayList<>()).add(line);
        }

        List<String> tell() {
            // Markets first, so within a tick the market opening comes before the rumour it
            // started.
            markets();
            rumours();
            sales();
            List<String> out = new ArrayList<>();
            if (SessionReport.beforeTheGate(played)) {
                out.add(SessionReport.BEFORE_THE_GATE);
                out.add("");
            }
            long day = -1;
            for (Map.Entry<Long, List<String>> entry : lines.entrySet()) {
                long tick = entry.getKey();
                if (DayPart.dayOf(tick) != day) {
                    day = DayPart.dayOf(tick);
                    out.add("Day " + day);
                }
                for (String line : entry.getValue()) {
                    out.add(String.format(java.util.Locale.ROOT, "  %-8s %s",
                            DayPart.of(tick).description(), line));
                }
            }
            if (out.stream().noneMatch(line -> line.startsWith("Day "))) {
                out.add("Nothing happened worth retelling: no lies, no sales, and no price that "
                        + "went further than the market's everyday wobble.");
            }
            return out;
        }

        /** Lies, and rumours the market started, from telling to forgetting. */
        private void rumours() {
            WorldState world = new WorldState();
            Map<Integer, Integer> root = new TreeMap<>();
            Map<Integer, Integer> teller = new TreeMap<>();       // family -> who it was planted in
            Map<Integer, Integer> worst = new TreeMap<>();        // family -> worst severity so far
            Map<Integer, Boolean> spread = new TreeMap<>();
            Map<Integer, Set<Integer>> believersSeen = new TreeMap<>();
            Map<Integer, Integer> heardMilestone = new TreeMap<>(); // 0, 1 quarter, 2 half
            Map<Integer, int[]> mostBelieved = new TreeMap<>();   // family -> {count, tick}
            Map<Integer, Integer> lastHeld = new TreeMap<>();
            Set<Integer> forgotten = new TreeSet<>();
            long tick = 0;

            for (Event event : played.log()) {
                if (event instanceof TickStarted started && started.tick() != tick) {
                    if (tick > 0) {
                        endOfTick(world, tick, root, claimOf, teller, believersSeen, heardMilestone,
                                mostBelieved, lastHeld, forgotten);
                    }
                    tick = started.tick();
                }
                switch (event) {
                    case RumorPlanted planted -> {
                        root.put(planted.rumorId(), planted.rumorId());
                        claimOf.put(planted.rumorId(), planted.claim());
                        teller.put(planted.rumorId(), planted.villagerId());
                        worst.put(planted.rumorId(), planted.severity());
                        at(planted.tick(), "You told " + names.of(planted.villagerId()) + " that "
                                + BeliefReport.inWords(planted.claim(), planted.severity()) + ".");
                    }
                    case RumorMutated mutated -> {
                        int family = root.getOrDefault(mutated.parentId(), mutated.parentId());
                        root.put(mutated.rumorId(), family);
                        if (claimOf.containsKey(family) && mutated.severity() > worst.getOrDefault(family, 1)) {
                            worst.put(family, mutated.severity());
                            at(mutated.tick(), capitalised(which(family, teller))
                                    + " grew in the telling: now "
                                    + BeliefReport.inWords(claimOf.get(family), mutated.severity()) + ".");
                        }
                    }
                    case RumorTold told -> {
                        int family = root.getOrDefault(told.keptRumorId(),
                                root.getOrDefault(told.toldRumorId(), told.toldRumorId()));
                        if (teller.containsKey(family) && !spread.getOrDefault(family, false)
                                && told.listenerId() != teller.get(family)) {
                            spread.put(family, true);
                            at(told.tick(), names.of(told.tellerId()) + " passed it on to "
                                    + names.of(told.listenerId()) + ".");
                        }
                    }
                    case PriceObserved read -> {
                        if (!world.rumors().containsKey(read.rumorId()) && !root.containsKey(read.rumorId())) {
                            root.put(read.rumorId(), read.rumorId());
                            claimOf.put(read.rumorId(), read.claim());
                            worst.put(read.rumorId(), 1);
                            at(read.tick(), "The market started a rumour of its own: that "
                                    + BeliefReport.inWords(read.claim(), 1)
                                    + ", read off the price.");
                        }
                    }
                    default -> { }
                }
                world.apply(event);
            }
            if (tick > 0) {
                endOfTick(world, tick, root, claimOf, teller, believersSeen, heardMilestone,
                        mostBelieved, lastHeld, forgotten);
            }
            mostBelieved.forEach((family, most) -> {
                // Two is the one they told and the first to believe it, already said.
                if (most[0] >= 3) {
                    at(most[1], most[0] + " villagers believed "
                            + which(family, teller) + " — the most at once.");
                }
            });
        }

        private void endOfTick(WorldState world, long tick, Map<Integer, Integer> root,
                               Map<Integer, Claim> claimOf, Map<Integer, Integer> teller,
                               Map<Integer, Set<Integer>> believersSeen,
                               Map<Integer, Integer> heardMilestone,
                               Map<Integer, int[]> mostBelieved, Map<Integer, Integer> lastHeld,
                               Set<Integer> forgotten) {
            Map<Integer, Integer> held = new TreeMap<>();
            Map<Integer, List<Integer>> believing = new TreeMap<>();
            for (Villager villager : world.villagers().values()) {
                for (Belief belief : villager.beliefs().values()) {
                    Integer family = root.get(belief.rumorId());
                    if (family == null || !claimOf.containsKey(family)) {
                        continue;
                    }
                    held.merge(family, 1, Integer::sum);
                    if (belief.confidence() >= RumorStats.DEFAULT_BELIEVE_THRESHOLD) {
                        believing.computeIfAbsent(family, f -> new ArrayList<>()).add(villager.id());
                    }
                }
            }
            for (int family : claimOf.keySet()) {
                int heard = held.getOrDefault(family, 0);
                List<Integer> now = believing.getOrDefault(family, List.of());
                Set<Integer> seen = believersSeen.computeIfAbsent(family, f -> new TreeSet<>());
                Integer planter = teller.get(family);
                for (int id : now) {
                    if (seen.isEmpty() && planter != null && id != planter) {
                        at(tick, names.of(id) + " was the first to believe it after "
                                + names.plain(planter) + ".");
                    }
                    if (planter == null || id != planter) {
                        seen.add(id);
                    }
                }
                int milestone = heardMilestone.getOrDefault(family, 0);
                if (milestone < 1 && heard * 4 >= villagers && heard >= 2) {
                    heardMilestone.put(family, 1);
                    at(tick, "A quarter of the village had heard " + which(family, teller) + ".");
                }
                if (milestone < 2 && heard * 2 >= villagers && heard >= 2) {
                    heardMilestone.put(family, 2);
                    at(tick, "Half the village had heard " + which(family, teller) + ".");
                }
                int[] most = mostBelieved.computeIfAbsent(family, f -> new int[] {0, 0});
                if (now.size() > most[0]) {
                    most[0] = now.size();
                    most[1] = (int) tick;
                }
                int before = lastHeld.getOrDefault(family, 0);
                if (before > 0 && heard == 0 && forgotten.add(family)) {
                    at(tick, "Nobody held " + which(family, teller) + " any more.");
                }
                lastHeld.put(family, heard);
            }
        }

        private static String capitalised(String text) {
            return Character.toUpperCase(text.charAt(0)) + text.substring(1);
        }

        private String which(int family, Map<Integer, Integer> teller) {
            return teller.containsKey(family) ? "your lie"
                    : "the market's rumour that " + BeliefReport.inWords(claimOf.get(family), 1);
        }

        /** Each day's selling, one line per good and counter. */
        private void sales() {
            Map<String, int[]> byDay = new TreeMap<>();
            Map<String, Set<Integer>> watching = new TreeMap<>();
            Map<String, Long> firstTick = new TreeMap<>();
            for (Input input : played.inputs()) {
                if (input instanceof PlayerTraded sale) {
                    Good good = Good.of(sale.item());
                    String key = String.format(java.util.Locale.ROOT, "%08d|%02d|%04d",
                            DayPart.dayOf(sale.tick()), good.ordinal(), sale.villagerId());
                    int[] sum = byDay.computeIfAbsent(key, k -> new int[2]);
                    sum[0] += sale.count();
                    sum[1] += sale.emeralds();
                    watching.computeIfAbsent(key, k -> new TreeSet<>()).addAll(sale.witnesses());
                    firstTick.merge(key, sale.tick(), Math::min);
                }
            }
            byDay.forEach((key, sum) -> {
                String[] parts = key.split("\\|");
                Good good = Good.values()[Integer.parseInt(parts[1])];
                int counter = Integer.parseInt(parts[2]);
                int watched = watching.get(key).size();
                at(firstTick.get(key), "You sold " + good.amount(sum[0]) + " to "
                        + names.of(counter) + " for " + sum[1] + " emerald" + (sum[1] == 1 ? "" : "s")
                        + ", watched by " + watched + ".");
            });
        }

        /** Each good worth a line: turning alarmed or glutted, its peak, and bubbles completing. */
        private void markets() {
            boolean opened = false;
            Map<Good, PriceMood> mood = new TreeMap<>();
            // A change of mood is told only once it has held for SETTLED readings, told at the
            // first of them: a price sitting on a boundary would otherwise flip it every tick.
            Map<Good, PriceMood> pending = new TreeMap<>();
            Map<Good, int[]> pendingSince = new TreeMap<>(); // readings, tick, price
            Map<Good, int[]> peak = new TreeMap<>();       // price, tick
            for (Event event : played.log()) {
                if (!(event instanceof MarketPriceSet set)) {
                    continue;
                }
                if (!opened) {
                    opened = true;
                    at(set.tick(), "The market opened: enough villagers stood in it at once to "
                            + "set a price.");
                }
                Good good = Good.of(set.item());
                if (!goods.contains(good)) {
                    continue;
                }
                PriceMood now = band(PriceMood.of(set.price(), base));
                PriceMood was = mood.getOrDefault(good, PriceMood.NORMAL);
                if (now == was) {
                    pending.remove(good);
                } else {
                    if (pending.get(good) != now) {
                        pending.put(good, now);
                        pendingSince.put(good, new int[] {0, (int) set.tick(), set.price()});
                    }
                    int[] since = pendingSince.get(good);
                    if (++since[0] == SETTLED) {
                        mood.put(good, now);
                        pending.remove(good);
                        at(since[1], moodLine(good, was, now, since[2]));
                    }
                }
                int[] best = peak.computeIfAbsent(good, g -> new int[] {Integer.MIN_VALUE, 0});
                if (set.price() > best[0]) {
                    best[0] = set.price();
                    best[1] = (int) set.tick();
                }
            }
            peak.forEach((good, best) -> {
                if (PriceMood.of(best[0], base).compareTo(PriceMood.RISING) >= 0) {
                    at(best[1], SessionReport.title(good) + " peaked at "
                            + PriceMood.describe(best[0], base) + "." + pointer(good));
                }
            });
            for (Good good : goods) {
                MarketStats stats = MarketStats.of(played.log(), new Claim(good.id(), ClaimType.SCARCE));
                for (Bubble bubble : stats.bubbles()) {
                    at(bubble.recoveryTick(), "The panic over " + good.plural() + " broke: back to "
                            + PriceMood.describe(bubble.recoveryPrice(), base) + ", "
                            + Math.round(bubble.days()) + " days after its peak." + pointer(good));
                }
                for (Bubble bust : stats.busts()) {
                    at(bust.recoveryTick(), "The glut in " + good.plural() + " passed: back to "
                            + PriceMood.describe(bust.recoveryPrice(), base) + ".");
                }
            }
        }

        private static final int SETTLED = 3;

        private String moodLine(Good good, PriceMood was, PriceMood now, int price) {
            String level = PriceMood.describe(price, base);
            if (now != PriceMood.NORMAL) {
                return SessionReport.title(good) + " " + level + " — "
                        + now.label().toLowerCase(java.util.Locale.ROOT) + ".";
            }
            // Back inside the everyday band, which is not the same as back to normal.
            return SessionReport.title(good) + (was == PriceMood.GLUT ? " recovered to " : " came down to ")
                    + level + ".";
        }

        /**
         * The moods worth a line. Rising and easing are the market's everyday wobble; a line
         * each time the price crossed five percent would bury the story.
         */
        private static PriceMood band(PriceMood mood) {
            return switch (mood) {
                case RISING, EASING, NORMAL -> PriceMood.NORMAL;
                case ALARMED, PANIC -> mood;
                case GLUT -> PriceMood.GLUT;
            };
        }

        private String pointer(Good good) {
            return discussed.contains(good) ? " " + SEE_THE_REPORT : "";
        }
    }
}
