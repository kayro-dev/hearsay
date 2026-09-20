package hearsay;

public sealed interface Event
        permits PriceChanged, VillagerCreated, VillagerMoved, VillagersMet {
    long tick();
}
