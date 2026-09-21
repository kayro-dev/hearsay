package hearsay;

/**
 * A villager looked at how much of something the village actually has.
 *
 * <p>An input, like every other thing the world outside does to the village. What they saw
 * is written into the recipe, so a session replays exactly and a third counterfactual
 * becomes available: what would they have believed if they had never looked?
 *
 * <p>Unlike a sale, this is a <em>standing fact</em>. E34 measured a sale moving the
 * biggest single belief by four thousandths, because a sale is an event: it happens, it
 * fades, and nothing is left to come back to. A full chest is still full tomorrow, and that
 * is the difference the whole of stage 4 turns on.
 *
 * @param sawHowMany how many of the item were within reach of them
 */
public record RealityChecked(long tick, int villagerId, String item, int sawHowMany)
        implements Input {

    public RealityChecked {
        if (sawHowMany < 0) {
            throw new IllegalArgumentException("Cannot see fewer than none, was " + sawHowMany);
        }
    }
}
