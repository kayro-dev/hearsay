package hearsay;

public record PriceChanged(long tick, int delta) implements Event {}
