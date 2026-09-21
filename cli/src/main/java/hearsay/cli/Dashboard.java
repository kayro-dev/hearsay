package hearsay.cli;

import hearsay.Claim;
import hearsay.ClaimType;
import hearsay.DashboardPage;
import hearsay.Input;
import hearsay.MarketStats;
import hearsay.Params;
import hearsay.PlantRumor;
import hearsay.RecipeFile;
import hearsay.Run;
import hearsay.Simulation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes one village's story to a page you can open.
 *
 * <p>Takes either a seed or a session played in Minecraft. A played session is the more
 * useful of the two: its meetings were observed rather than decided, and both timelines get
 * the same ones, so the page shows what that village would have done had nobody lied to it.
 */
final class Dashboard {

    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

    private Dashboard() {
    }

    static void write(String[] args) {
        Map<String, String> options = Options.parse(args);
        Path out = Path.of(options.getOrDefault("out", "build/dashboard.html"));

        Run withLie;
        if (options.containsKey("file")) {
            withLie = RecipeFile.read(Path.of(options.get("file")));
        } else {
            long seed = Options.longOption(options, "seed", 42);
            int ticks = Options.intOption(options, "ticks", 200);
            int planter = Run.execute(seed, Params.defaults(), List.of(), 1)
                    .finalState().gossipiestVillager().id();
            withLie = Run.execute(seed, Params.defaults(),
                    List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, planter)), ticks);
        }

        // Every lie removed, not merely the first. A village told three lies has three
        // things to account for, and leaving two in place answers a question nobody asked.
        List<Input> truthful = new ArrayList<>();
        for (Input input : withLie.inputs()) {
            if (!(input instanceof PlantRumor)) {
                truthful.add(input);
            }
        }
        if (truthful.size() == withLie.inputs().size()) {
            System.out.println("Nothing was ever planted in this run, so there is no "
                    + "counterfactual to draw. Writing it anyway.");
        }
        Run withoutLie = Run.execute(
                withLie.seed(), withLie.params(), truthful, withLie.ticks());

        String html = DashboardPage.render(withLie.seed(), withLie.ticks(), withLie.params(),
                DIAMONDS_SCARCE,
                MarketStats.of(withLie.log(), DIAMONDS_SCARCE),
                MarketStats.of(withoutLie.log(), DIAMONDS_SCARCE));

        try {
            if (out.getParent() != null) {
                Files.createDirectories(out.getParent());
            }
            Files.writeString(out, html);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + out.toAbsolutePath(), e);
        }
        System.out.println("Wrote " + out.toAbsolutePath());
        System.out.println("Open it in a browser. It needs nothing else: no server, no "
                + "network, no files beside it.");
    }
}
