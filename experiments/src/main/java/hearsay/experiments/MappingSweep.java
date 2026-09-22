package hearsay.experiments;

import hearsay.Claim;
import hearsay.ClaimType;
import hearsay.Event;
import hearsay.Input;
import hearsay.MarketPriceSet;
import hearsay.MarketStats;
import hearsay.ObservedMeeting;
import hearsay.Params;
import hearsay.PlantRumor;
import hearsay.ProximityPairing;
import hearsay.RecipeFile;
import hearsay.Run;
import hearsay.Sighting;
import hearsay.Simulation;
import hearsay.Spot;
import hearsay.SpotMapper;
import hearsay.SurveyFile;
import hearsay.VillagerSeen;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Sweeps the rule that turns where a villager is standing into what spot they are at.
 *
 * <p>Every other number in this project was chosen by sweeping. This one could not be,
 * because a recipe stores the spot that was decided rather than the position it was decided
 * from, and a session cannot be replayed at a different range. A survey stores the
 * positions, so the whole input stream can be built again under a different rule: who was
 * near enough to whom to talk, and what spot each of them counted as.
 *
 * <p>What is swept: how near a villager must be to their bed or workstation to count as
 * being there, and whether the village at large counts as somewhere to trade. The second is
 * the question E12 raised, since villagers spend about 5% of their time at a workstation
 * and two thirds of it walking about.
 *
 * <pre>
 * ./gradlew :experiments:mapping --args="--survey <survey.csv> --recipe <session.hearsay> \
 *     --ranges 3,6,10,16 --csv mapping.csv"
 * </pre>
 */
public final class MappingSweep {

    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

    public static void main(String[] args) throws IOException {
        Map<String, String> options = Cli.parse(args);
        Path surveyPath = Path.of(options.get("survey"));
        Path recipePath = Path.of(options.get("recipe"));
        List<Double> ranges = Cli.doubles(options.getOrDefault("ranges", "3,6,10,16"));
        double talkingRange = Double.parseDouble(options.getOrDefault("talking-range", "6"));
        Path csv = Path.of(options.getOrDefault("csv", "mapping.csv"));

        List<Sighting> survey = SurveyFile.read(surveyPath);
        Run played = RecipeFile.read(recipePath);
        List<Input> lies = new ArrayList<>();
        for (Input input : played.inputs()) {
            if (input instanceof PlantRumor) {
                lies.add(input);
            }
        }

        Map<Long, List<Sighting>> byTick = new TreeMap<>();
        for (Sighting sighting : survey) {
            byTick.computeIfAbsent(sighting.tick(), t -> new ArrayList<>()).add(sighting);
        }
        System.out.printf(Locale.ROOT, "Survey: %d sightings over %d ticks, %d villagers, %d lies planted.%n",
                survey.size(), byTick.size(), played.params().villagers(), lies.size());
        System.out.printf(Locale.ROOT, "Rebuilding who met whom at a talking range of %.0f blocks.%n%n",
                talkingRange);
        System.out.println("  range  wandering counts   meetings/tick   at market   ticks priced"
                + "   peak$   believers   bubbles");

        List<String> rows = new ArrayList<>();
        for (double range : ranges) {
            for (boolean wanderingTrades : new boolean[] {false, true}) {
                rows.add(measure(byTick, played, lies, range, talkingRange, wanderingTrades));
            }
        }

        if (csv.getParent() != null) {
            Files.createDirectories(csv.getParent());
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(csv))) {
            out.println("range,wanderingCountsAsMarket,meetingsPerTick,meanAtMarket,"
                    + "sharePriced,peakPrice,peakBelievers,bubbles");
            rows.forEach(out::println);
        }
        System.out.println();
        System.out.println("Wrote " + csv.toAbsolutePath());
    }

    /**
     * Builds the inputs again under one rule and runs them.
     *
     * @param wanderingTrades whether a villager out in the village counts as being in the
     *                        market, rather than only one at a workstation
     */
    private static String measure(Map<Long, List<Sighting>> byTick, Run played, List<Input> lies,
                                  double range, double talkingRange, boolean wanderingTrades) {
        List<Input> inputs = new ArrayList<>(lies);
        Map<Integer, Spot> lastSpot = new LinkedHashMap<>();
        long meetings = 0;

        for (Map.Entry<Long, List<Sighting>> tick : byTick.entrySet()) {
            List<ProximityPairing.Position> standing = new ArrayList<>();
            Map<Integer, Spot> spots = new LinkedHashMap<>();
            for (Sighting sighting : tick.getValue()) {
                Spot spot = spotUnder(sighting, range, wanderingTrades);
                spots.put(sighting.villagerId(), spot);
                standing.add(new ProximityPairing.Position(sighting.villagerId(),
                        sighting.x(), sighting.y(), sighting.z()));
                if (lastSpot.put(sighting.villagerId(), spot) != spot) {
                    inputs.add(new VillagerSeen(tick.getKey(), sighting.villagerId(), spot));
                }
            }
            for (ProximityPairing.Encounter met : ProximityPairing.pairsWithin(
                    standing, talkingRange, hearsay.Seeds.branch(played.seed(), tick.getKey().intValue()))) {
                Spot a = spots.getOrDefault(met.a(), SpotMapper.ANYWHERE_ELSE);
                Spot b = spots.getOrDefault(met.b(), SpotMapper.ANYWHERE_ELSE);
                if (a == Spot.HOME && b == Spot.HOME) {
                    continue;
                }
                inputs.add(new ObservedMeeting(tick.getKey(), met.a(), met.b(),
                        a == SpotMapper.ANYWHERE_ELSE ? b : a));
                meetings++;
            }
        }

        int ticks = byTick.size();
        Run run = Run.execute(played.seed(), played.params(), inputs, ticks);
        MarketStats stats = MarketStats.of(run.log(), DIAMONDS_SCARCE);

        long priced = 0;
        double sellers = 0;
        for (Event event : run.log()) {
            if (event instanceof MarketPriceSet price && price.item().equals(Simulation.DIAMOND)) {
                priced++;
                sellers += price.askingVillagers();
            }
        }
        System.out.printf(Locale.ROOT, "  %5.0f %18s %15.2f %11.1f %14s %7d %11d %9d%n",
                range, wanderingTrades ? "yes" : "no", meetings / (double) ticks,
                priced == 0 ? 0 : sellers / priced, percent(priced / (double) ticks),
                stats.peakPrice(), stats.peakBelievers(), stats.bubbles().size());
        return String.format(Locale.ROOT, "%.0f,%s,%.3f,%.2f,%.4f,%d,%d,%d", range, wanderingTrades,
                meetings / (double) ticks, priced == 0 ? 0 : sellers / priced,
                priced / (double) ticks, stats.peakPrice(), stats.peakBelievers(),
                stats.bubbles().size());
    }

    /** One candidate rule: the mapper's, optionally counting the village at large as trade. */
    private static Spot spotUnder(Sighting sighting, double range, boolean wanderingTrades) {
        Spot spot = sighting.spotWithin(range);
        return wanderingTrades && spot == SpotMapper.ANYWHERE_ELSE ? Spot.MARKET : spot;
    }

    private static String percent(double share) {
        return Math.round(share * 100) + "%";
    }
}
