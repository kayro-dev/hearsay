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

    private static final String HEADER = "hearsay-recipe 1";

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
        if (lines.isEmpty() || !lines.get(0).trim().equals(HEADER)) {
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
                + " marketQuorum=" + p.marketQuorum()
                + " meetingSource=" + p.meetingSource();
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
                (int) number(values, "marketQuorum"),
                MeetingSource.valueOf(required(values, "meetingSource")));
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
        };
    }

    private static Input readInput(String text) {
        String[] parts = text.trim().split("\\s+");
        return switch (parts[0]) {
            case "plant" -> new PlantRumor(Long.parseLong(parts[1]),
                    new Claim(parts[2], ClaimType.valueOf(parts[3])),
                    Integer.parseInt(parts[4]), Integer.parseInt(parts[5]));
            case "meet" -> new ObservedMeeting(Long.parseLong(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                    Spot.valueOf(parts[4]));
            default -> throw new IllegalArgumentException("Unknown input in recipe: " + text);
        };
    }
}
