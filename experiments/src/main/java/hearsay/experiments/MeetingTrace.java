package hearsay.experiments;

import hearsay.Input;
import hearsay.MeetingSource;
import hearsay.ObservedMeeting;
import hearsay.Params;
import hearsay.RecipeFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The meetings out of a session somebody actually played, reused as the schedule for runs
 * that were never played.
 *
 * <p>The simulated movement model has villagers mixing far more than real ones do, because
 * real villagers cluster at workstations and beds. Tuning on the model and then playing in
 * a village is tuning against the wrong village. This makes a recorded trace usable as the
 * thing a sweep runs against, so the knobs can be chosen for how a village actually behaves.
 *
 * <p>The seed still varies traits and every gossip roll, so seeds remain worth sweeping: the
 * same bodies in the same places, with different people inside them.
 */
final class MeetingTrace {

    private final List<ObservedMeeting> meetings;
    private final int villagersSeen;
    private final int ticks;
    private final String name;

    private MeetingTrace(List<ObservedMeeting> meetings, int villagersSeen, int ticks, String name) {
        this.meetings = meetings;
        this.villagersSeen = villagersSeen;
        this.ticks = ticks;
        this.name = name;
    }

    static MeetingTrace from(Path recipe) {
        List<ObservedMeeting> meetings = new ArrayList<>();
        TreeSet<Integer> seen = new TreeSet<>();
        var run = RecipeFile.read(recipe);
        for (Input input : run.inputs()) {
            if (input instanceof ObservedMeeting meeting) {
                meetings.add(meeting);
                seen.add(meeting.a());
                seen.add(meeting.b());
            }
        }
        if (meetings.isEmpty()) {
            throw new IllegalArgumentException(recipe + " records no meetings, so there is "
                    + "nothing to replay. Was it played with movement simulated?");
        }
        return new MeetingTrace(meetings, seen.isEmpty() ? 0 : seen.last() + 1, run.ticks(),
                recipe.getFileName().toString());
    }

    /**
     * The meetings this village can hold, given its size and how long the run is.
     *
     * <p>A trace cannot be stretched: it names the villagers it names. Asking for a bigger
     * village than the trace saw leaves the extra villagers with nobody to meet, which is
     * worth seeing rather than hiding. Asking for a smaller one drops the meetings naming
     * villagers who do not exist, which thins the village rather than breaking it.
     */
    List<Input> scheduleFor(int villagers, int ticks) {
        List<Input> schedule = new ArrayList<>();
        for (ObservedMeeting meeting : meetings) {
            if (meeting.a() < villagers && meeting.b() < villagers && meeting.tick() <= ticks) {
                schedule.add(meeting);
            }
        }
        return schedule;
    }

    /** How many villagers actually meet anyone at this size: the honest denominator. */
    int activeVillagers(int villagers) {
        return Math.min(villagers, villagersSeen);
    }

    int villagersSeen() { return villagersSeen; }
    int ticks() { return ticks; }
    String name() { return name; }

    static Params paramsFor(Params base, int villagers) {
        return base.withMeetingSource(MeetingSource.EXTERNAL).withVillagers(villagers);
    }
}
