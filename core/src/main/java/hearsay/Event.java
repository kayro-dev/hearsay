package hearsay;

/**
 * Something that happened. Events are either <em>input</em> events, which come from
 * outside the simulation ({@link RumorPlanted}, and every player action later), or
 * <em>derived</em> events, which the simulation decides for itself.
 */
public sealed interface Event permits
        PriceChanged, VillagerCreated, VillagerMoved, VillagersMet,
        RumorPlanted, RumorMutated, RumorTold, DayEnded {

    long tick();

    /** True if this event came from outside the simulation rather than being decided by it. */
    default boolean isInput() {
        return this instanceof RumorPlanted;
    }
}
