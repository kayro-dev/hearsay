package hearsay.cli;

import hearsay.Chronicle;
import hearsay.RecipeFile;
import hearsay.Run;
import hearsay.SessionReport;

import java.nio.file.Path;
import java.util.Map;

/**
 * A played session's report and chronicle, printed. Thin: the reading lives in core, where
 * it is tested.
 *
 * <pre>
 * ./gradlew :cli:run --args="report --file PATH"
 * ./gradlew :cli:run --args="chronicle --file PATH"
 * </pre>
 */
final class Report {

    private Report() {
    }

    static void report(String[] args) {
        Path file = fileFrom(args);
        try {
            SessionReport.of(RecipeFile.read(file), RecipeFile.checksumIn(file))
                    .forEach(System.out::println);
        } catch (SessionReport.NotThisSession refused) {
            System.out.println(refused.getMessage());
        }
    }

    static void chronicle(String[] args) {
        Path file = fileFrom(args);
        Run played = RecipeFile.read(file);
        System.out.println("The chronicle of " + file.getFileName() + ": what happened, in order. "
                + "What caused it is the report's to say.");
        System.out.println();
        Chronicle.of(played).forEach(System.out::println);
    }

    private static Path fileFrom(String[] args) {
        Map<String, String> options = Options.parse(args);
        if (!options.containsKey("file")) {
            throw new IllegalArgumentException("Which session? --file PATH");
        }
        return Path.of(options.get("file"));
    }
}
