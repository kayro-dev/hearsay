package hearsay;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reading and writing the recipe for a run: its seed, its params, its inputs and its
 * length. Everything needed to produce the run again, and nothing else.
 *
 * <p>This is the bridge out of the game. A session played in Minecraft cannot be reproduced
 * from its seed, because the seed says nothing about where Minecraft put anybody, but every
 * meeting it observed was recorded as an input, so the recipe reproduces it exactly. Saved
 * from the plugin and handed to the headless tools, it makes "what would have happened
 * without my lie" a question you can ask of a session you actually played.
 *
 * <p>The format is plain text, one fact per line, so a saved run can be read and edited
 * without any tooling. The log is deliberately not saved: it follows from the recipe, and
 * storing both would let them disagree.
 */
public final class RecipeFile {

    /** What this writes today. Version 1 had no village size, because it was always 20. */
    private static final String HEADER = "hearsay-recipe 7";

    private static final java.util.Set<String> READABLE_HEADERS =
            java.util.Set.of("hearsay-recipe 1", "hearsay-recipe 2", "hearsay-recipe 3",
                    "hearsay-recipe 4", "hearsay-recipe 5", "hearsay-recipe 6", HEADER);

    private RecipeFile() {
    }

    public static void write(Run run, Path path) {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        lines.add("seed " + run.seed());
        lines.add("ticks " + run.ticks());
        lines.add("params " + describe(run.params()));
        for (Input input : run.inputs()) {
            lines.add("input " + describe(input));
        }
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.write(path, lines);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the recipe to " + path, e);
        }
    }

    /** Reads a recipe and runs it, giving back the run it describes. */
    public static Run read(Path path) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read a recipe from " + path, e);
        }
        String header = lines.isEmpty() ? "" : lines.get(0).trim();
        if (!READABLE_HEADERS.contains(header)) {
            throw new IllegalArgumentException(path + " is not a Hearsay recipe");
        }

        long seed = 0;
        int ticks = 0;
        Params params = Params.defaults();
        List<Input> inputs = new ArrayList<>();

        for (String line : lines.subList(1, lines.size())) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split(" ", 2);
            switch (parts[0]) {
                case "seed" -> seed = Long.parseLong(parts[1].trim());
                case "ticks" -> ticks = Integer.parseInt(parts[1].trim());
                case "params" -> params = readParams(parts[1]);
                case "input" -> inputs.add(readInput(parts[1]));
                default -> throw new IllegalArgumentException("Unknown line in recipe: " + trimmed);
            }
        }
        return Run.execute(seed, params, inputs, ticks);
    }

    private static String describe(Params p) {
        return "tellThreshold=" + p.tellThreshold()
                + " repeatWeight=" + p.repeatWeight()
                + " contradictionFactor=" + p.contradictionFactor()
                + " dailyDecay=" + p.dailyDecay()
                + " forgetThreshold=" + p.forgetThreshold()
                + " mutationChance=" + p.mutationChance()
                + " plantedConfidence=" + p.plantedConfidence()
                + " basePrice=" + p.basePrice()
                + " priceSensitivity=" + p.priceSensitivity()
                + " observationWeight=" + p.observationWeight()
                + " observationThreshold=" + p.observationThreshold()
                + " fullMoveSize=" + p.fullMoveSize()
                + " marketNoise=" + p.marketNoise()
                + " noiseDecay=" + p.noiseDecay()
                + " marketQuorumFraction=" + p.marketQuorumFraction()
                + " marketWindowTicks=" + p.marketWindowTicks()
                + " meetingSource=" + p.meetingSource()
                + " villagers=" + p.villagers()
                + " mixing=" + p.mixing()
                + " tradeWeight=" + p.tradeWeight()
                + " witnessWeight=" + p.witnessWeight()
                + " checkWeight=" + p.checkWeight()
                + " emptyEvidence=" + p.emptyEvidence()
                + " trendAnchor=" + p.trendAnchor()
                + " trendWindowTicks=" + p.trendWindowTicks()
                + " levelGate=" + p.levelGate()
                + " goods=" + String.join(",", p.goods().stream().map(Good::id).toList());
    }

    private static Params readParams(String text) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : text.trim().split("\\s+")) {
            String[] halves = pair.split("=", 2);
            if (halves.length != 2) {
                throw new IllegalArgumentException("Not a name=value pair: " + pair);
            }
            values.put(halves[0], halves[1]);
        }
        return new Params(
                number(values, "tellThreshold"), number(values, "repeatWeight"),
                number(values, "contradictionFactor"), number(values, "dailyDecay"),
                number(values, "forgetThreshold"), number(values, "mutationChance"),
                number(values, "plantedConfidence"), (int) number(values, "basePrice"),
                number(values, "priceSensitivity"), number(values, "observationWeight"),
                number(values, "observationThreshold"), number(values, "fullMoveSize"),
                number(values, "marketNoise"), number(values, "noiseDecay"),
                quorumFractionIn(values),
                // Older files predate the window; they ran without one.
                values.containsKey("marketWindowTicks")
                        ? (int) number(values, "marketWindowTicks") : 0,
                MeetingSource.valueOf(required(values, "meetingSource")),
                // Written by a version that had no village size, from when it was always
                // twenty. Sessions saved then really did simulate twenty villagers, so
                // this reproduces them exactly rather than guessing.
                values.containsKey("villagers")
                        ? (int) number(values, "villagers")
                        : Simulation.VILLAGER_COUNT,
                // Written before neighbourhoods existed, when the village was perfectly
                // mixed. 1.0 is that village exactly, so those sessions still reproduce.
                values.containsKey("mixing") ? number(values, "mixing") : 1.0,
                // Written before anybody could trade, so no trade ever happened in them and
                // the weights cannot change what they replay to.
                values.containsKey("tradeWeight")
                        ? number(values, "tradeWeight") : Params.TRADE_WEIGHT,
                values.containsKey("witnessWeight")
                        ? number(values, "witnessWeight") : Params.WITNESS_WEIGHT,
                // Written before anybody could look at a chest, so no check ever happened
                // in them and the weights cannot change what they replay to.
                values.containsKey("checkWeight")
                        ? number(values, "checkWeight") : Params.CHECK_WEIGHT,
                values.containsKey("emptyEvidence")
                        ? number(values, "emptyEvidence") : Params.EMPTY_EVIDENCE,
                // Written before the market had a trend to read, when every villager read
                // the price against their own last conclusion. 0 is that rule exactly.
                values.containsKey("trendAnchor") ? number(values, "trendAnchor") : 0.0,
                values.containsKey("trendWindowTicks")
                        ? (int) number(values, "trendWindowTicks") : Params.TREND_WINDOW_TICKS,
                // Written before prices were read against normal; 0 is the rule then.
                values.containsKey("levelGate") ? number(values, "levelGate") : 0.0,
                // Written before a village could trade in anything but diamonds, so that is
                // what those villages traded in.
                goodsIn(values));
    }

    /**
     * The quorum, however the file spells it. Older files name a count, from when it was a
     * count; dividing it by the village they were written with gives back the same market.
     */
    private static double quorumFractionIn(Map<String, String> values) {
        if (values.containsKey("marketQuorumFraction")) {
            return number(values, "marketQuorumFraction");
        }
        double villagers = values.containsKey("villagers")
                ? number(values, "villagers") : Simulation.VILLAGER_COUNT;
        return number(values, "marketQuorum") / villagers;
    }

    private static double number(Map<String, String> values, String name) {
        return Double.parseDouble(required(values, name));
    }

    private static String required(Map<String, String> values, String name) {
        String value = values.get(name);
        if (value == null) {
            throw new IllegalArgumentException("The recipe is missing " + name);
        }
        return value;
    }

    private static String describe(Input input) {
        return switch (input) {
            case PlantRumor p -> "plant " + p.tick() + " " + p.claim().item() + " "
                    + p.claim().type() + " " + p.severity() + " " + p.villagerId();
            case ObservedMeeting m -> "meet " + m.tick() + " " + m.a() + " " + m.b()
                    + " " + m.spot();
            case VillagerSeen s -> "seen " + s.tick() + " " + s.villagerId() + " " + s.spot();
            // The witnesses are part of what happened, not a detail of how it was drawn:
            // who saw a sale decides who learned anything from it, so a recipe without them
            // would replay to a different village.
            case PlayerTraded t -> "trade " + t.tick() + " " + t.villagerId() + " "
                    + t.count() + " " + t.emeralds() + " " + join(t.witnesses())
                    + " " + t.item();
            case RealityChecked c -> "saw " + c.tick() + " " + c.villagerId() + " "
                    + c.item() + " " + c.sawHowMany();
        };
    }

    private static java.util.Set<Good> goodsIn(Map<String, String> values) {
        if (!values.containsKey("goods")) {
            return java.util.EnumSet.of(Good.DIAMOND);
        }
        java.util.Set<Good> goods = java.util.EnumSet.noneOf(Good.class);
        for (String id : values.get("goods").split(",")) {
            goods.add(Good.of(id));
        }
        return goods;
    }

    /** Witness ids as one field, so a trade stays one whitespace-separated line. */
    private static String join(java.util.Collection<Integer> ids) {
        StringBuilder out = new StringBuilder();
        for (int id : ids) {
            out.append(out.isEmpty() ? "" : ",").append(id);
        }
        return out.isEmpty() ? "-" : out.toString();
    }

    private static java.util.NavigableSet<Integer> split(String field) {
        java.util.NavigableSet<Integer> ids = new java.util.TreeSet<>();
        if (field.isBlank() || field.equals("-")) {
            return ids;
        }
        for (String id : field.split(",")) {
            ids.add(Integer.parseInt(id));
        }
        return ids;
    }

    private static Input readInput(String text) {
        String[] parts = text.trim().split("\\s+");
        return switch (parts[0]) {
            case "plant" -> new PlantRumor(Long.parseLong(parts[1]),
                    new Claim(parts[2], ClaimType.valueOf(parts[3])),
                    Integer.parseInt(parts[4]), Integer.parseInt(parts[5]));
            case "seen" -> new VillagerSeen(Long.parseLong(parts[1]),
                    Integer.parseInt(parts[2]), Spot.valueOf(parts[3]));
            case "saw" -> new RealityChecked(Long.parseLong(parts[1]),
                    Integer.parseInt(parts[2]), parts[3], Integer.parseInt(parts[4]));
            // The item comes last, so a line written before there was more than one good
            // still reads: a sale then could only have been of diamonds.
            case "trade" -> new PlayerTraded(Long.parseLong(parts[1]),
                    Integer.parseInt(parts[2]),
                    parts.length > 6 ? parts[6] : Simulation.DIAMOND,
                    Integer.parseInt(parts[3]), Integer.parseInt(parts[4]),
                    split(parts.length > 5 ? parts[5] : ""));
            case "meet" -> new ObservedMeeting(Long.parseLong(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                    Spot.valueOf(parts[4]));
            default -> throw new IllegalArgumentException("Unknown input in recipe: " + text);
        };
    }
}
