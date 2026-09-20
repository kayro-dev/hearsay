package hearsay;

/**
 * Who decides which villagers run into each other.
 *
 * <p>Not a tuning knob: a structural choice about where the bodies live. It sits with the
 * params because reproducing a run needs it, and the params are what a run is reproduced
 * from.
 */
public enum MeetingSource {
    /**
     * Hearsay moves the villagers and pairs them off, drawing on the movement stream. The
     * headless default, and the only mode in which a run is reproducible from its seed
     * alone.
     */
    SIMULATED,

    /**
     * Something outside decides where the bodies are; Hearsay is told who met whom. In
     * Minecraft the villagers already walk around on their own, and simulating movement
     * on top of that would let a rumor jump between two villagers standing at opposite
     * ends of the village.
     *
     * <p>Determinism survives in a different form. An in-game run cannot be reproduced
     * from its seed, because the seed says nothing about where Minecraft put anyone, but
     * it can be reproduced from seed, params and inputs, because every observed meeting is
     * recorded as an input.
     */
    EXTERNAL
}
