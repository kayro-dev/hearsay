package hearsay;

/**
 * A tick began. Carries nothing but the moment itself.
 *
 * <p>The clock lives in the world, and the next tick starts from where the world's clock
 * stands, so every tick has to write down at least one thing. Until now that was true only
 * because movement and the market wobble happened to be recorded unconditionally. With
 * movement able to come from outside the simulation, that is no longer something to lean
 * on, so the clock is moved by an event whose whole purpose is to move it.
 */
public record TickStarted(long tick) implements Event {}
