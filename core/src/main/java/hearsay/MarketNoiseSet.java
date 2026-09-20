package hearsay;

/**
 * The market's wobble moved. Recorded every tick, whether or not the market opens, because
 * the wobble carries over and a world rebuilt from its log has to know where it stands.
 *
 * <p>Without this the log would describe the whole world except for one number, and a
 * forked world would start from a calm market it never had. The resume test exists to
 * catch exactly that.
 */
public record MarketNoiseSet(long tick, double level) implements Event {}
