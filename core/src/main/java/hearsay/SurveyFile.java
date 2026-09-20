package hearsay;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reading and writing a survey: where every villager stood, every tick, alongside the
 * recipe of the run it came from.
 *
 * <p>Kept apart from the recipe on purpose. A recipe is what reproduces a run and must stay
 * exactly what it is; a survey is evidence about the world the run happened in, and is only
 * ever read by experiments. Positions do not affect a run — the spots derived from them do —
 * so nothing is lost by keeping them out.
 */
public final class SurveyFile {

    private static final String HEADER =
            "tick,villagerId,x,y,z,toBed,toJobSite,jobSiteKind";

    private SurveyFile() {
    }

    public static void write(List<Sighting> sightings, Path path) {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (Sighting sighting : sightings) {
            lines.add(String.format("%d,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%s",
                    sighting.tick(), sighting.villagerId(), sighting.x(), sighting.y(),
                    sighting.z(), sighting.toBed(), sighting.toJobSite(),
                    sighting.jobSiteKind()));
        }
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.write(path, lines);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the survey to " + path, e);
        }
    }

    public static List<Sighting> read(Path path) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read a survey from " + path, e);
        }
        if (lines.isEmpty() || !lines.get(0).trim().equals(HEADER)) {
            throw new IllegalArgumentException(path + " is not a Hearsay survey");
        }

        List<Sighting> sightings = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split(",");
            sightings.add(new Sighting(Long.parseLong(parts[0]), Integer.parseInt(parts[1]),
                    Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                    Double.parseDouble(parts[4]), Double.parseDouble(parts[5]),
                    Double.parseDouble(parts[6]), Spot.valueOf(parts[7])));
        }
        return sightings;
    }
}
