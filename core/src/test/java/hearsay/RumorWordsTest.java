package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class RumorWordsTest {

    private static RumorWords.Said read(String... words) {
        return RumorWords.read(List.of(words));
    }

    private static String refusal(String... words) {
        return assertThrows(IllegalArgumentException.class, () -> read(words)).getMessage();
    }

    @Test
    void theCommandsInTheManualReadAsWritten() {
        assertEquals(new RumorWords.Said(Good.DIAMOND, ClaimType.SCARCE), read("diamonds", "scarce"));
        assertEquals(new RumorWords.Said(Good.GOLD, ClaimType.ABUNDANT), read("gold", "abundant"));
        assertEquals(new RumorWords.Said(Good.IRON, ClaimType.SCARCE), read("iron", "scarce"));
        assertEquals(new RumorWords.Said(Good.WHEAT, ClaimType.ABUNDANT), read("wheat", "abundant"));
        assertEquals(new RumorWords.Said(Good.DIAMOND, ClaimType.SCARCE), read("Diamond", "SCARCE"));
    }

    @Test
    void wordsInBetweenAreIgnoredAndOrderDoesNotMatter() {
        // The one typed in a played session, 2026-09-23: "wheat is scarce".
        assertEquals(new RumorWords.Said(Good.WHEAT, ClaimType.SCARCE), read("wheat", "is", "scarce"));
        // The one that used to plant the opposite of what it said.
        assertEquals(new RumorWords.Said(Good.WHEAT, ClaimType.ABUNDANT),
                read("wheat", "is", "abundant"));
        assertEquals(new RumorWords.Said(Good.GOLD, ClaimType.ABUNDANT),
                read("abundant", "gold", "ingots"));
    }

    @Test
    void nothingIsGuessed() {
        // Bare "/hearsay rumour" planted a diamond rumour in the same session.
        assertTrue(refusal().startsWith("Which good?"));
        assertTrue(refusal("scarce").startsWith("Which good?"));
        assertEquals("Scarce or abundant?", refusal("wheat"));
        assertEquals("Scarce or abundant?", refusal("wheat", "scarse"));
        assertTrue(refusal("wheet", "scarce").startsWith("Which good?"));
        assertEquals("One good at a time: you named wheat and gold.",
                refusal("wheat", "gold", "scarce"));
        assertEquals("Scarce or abundant, not both.", refusal("wheat", "scarce", "abundant"));
    }

    @Test
    void watchingMayLeaveTheClaimOutButNotTheGood() {
        // Watching records nothing, so a default there is harmless; planting still refuses.
        assertEquals(new RumorWords.Said(Good.WHEAT, ClaimType.SCARCE),
                RumorWords.readWatched(List.of("wheat")));
        assertEquals(new RumorWords.Said(Good.DIAMOND, ClaimType.ABUNDANT),
                RumorWords.readWatched(List.of("diamonds", "abundant")));
        assertThrows(IllegalArgumentException.class, () -> RumorWords.readWatched(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> RumorWords.readWatched(List.of("wheat", "gold")));
        assertEquals("Scarce or abundant?", refusal("wheat"));
    }

    @Test
    void everyGoodCanBeNamed() {
        for (Good good : Good.values()) {
            assertEquals(good, read(good.id(), "scarce").good());
            assertTrue(refusal().contains(good.id()), "the usage should list " + good.id());
        }
    }
}
