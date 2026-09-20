package hearsay;

public sealed interface Event permits PriceChanged {
    long tick();
}
