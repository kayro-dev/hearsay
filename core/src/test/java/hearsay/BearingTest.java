package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Telling a player which way to walk. */
class BearingTest {

    private static final double FAR = Bearing.CLOSE_ENOUGH + 1;
    private static final double NEAR = Bearing.CLOSE_ENOUGH - 1;

    @Test
    void namesTheOneAxisThatMatters() {
        assertEquals("north", Bearing.of(0, -FAR));
        assertEquals("south", Bearing.of(0, FAR));
        assertEquals("east", Bearing.of(FAR, 0));
        assertEquals("west", Bearing.of(-FAR, 0));
    }

    @Test
    void joinsBothAxesWhenBothAreWorthWalking() {
        assertEquals("north-east", Bearing.of(FAR, -FAR));
        assertEquals("south-west", Bearing.of(-FAR, FAR));
    }

    @Test
    void dropsAnAxisThatIsAlreadyNearEnough() {
        // Otherwise a villager two blocks to the side is reported as north-east, and the
        // player walks past them looking for something further off.
        assertEquals("north", Bearing.of(NEAR, -FAR));
        assertEquals("east", Bearing.of(FAR, NEAR));
    }

    @Test
    void saysSoWhenThereIsNowhereToWalk() {
        assertEquals("right here", Bearing.of(NEAR, NEAR));
        assertEquals("right here", Bearing.of(0, 0));
    }
}
