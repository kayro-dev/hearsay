package hearsay;

import java.util.List;
import java.util.Locale;

/**
 * What a player means by {@code /hearsay rumor ...}, read strictly.
 *
 * <p>It used to be read by position and forgivingly: a missing good meant diamonds, and any
 * third word that was not "abundant" meant scarce. So a bare {@code /hearsay rumour}, typed
 * to see the usage, planted a diamond rumour, and {@code wheat is abundant} planted the
 * opposite of what it said. A planted rumour is an input, recorded for good in the session
 * and replayed by every counterfactual asked of it, so a wrong guess here is a wrong
 * experiment. Now a claim needs exactly one good and exactly one of scarce or abundant, in
 * any order; anything else is refused with the reason, and nothing is planted.
 *
 * <p>Other words are ignored, so {@code wheat is scarce} and {@code gold ingots are abundant}
 * read as they sound. A misspelt good or claim is therefore missing, and refused, rather
 * than guessed.
 */
public final class RumorWords {

    /** A good and what is claimed about it. */
    public record Said(Good good, ClaimType type) {
    }

    private RumorWords() {
    }

    /**
     * The claim these words make.
     *
     * @throws IllegalArgumentException saying what is missing or doubled, for the player
     */
    public static Said read(List<String> words) {
        return read(words, null);
    }

    /**
     * The claim a player asks to watch. The same words, except that the claim may be left
     * out and means scarce: watching changes nothing, so a default there cannot spoil an
     * experiment the way one in a planted rumour did.
     */
    public static Said readWatched(List<String> words) {
        return read(words, ClaimType.SCARCE);
    }

    private static Said read(List<String> words, ClaimType unsaid) {
        Good good = null;
        ClaimType type = null;
        for (String raw : words) {
            String word = raw.toLowerCase(Locale.ROOT);
            Good named = goodCalled(word);
            ClaimType claimed = typeCalled(word);
            if (named != null) {
                if (good != null && good != named) {
                    throw new IllegalArgumentException("One good at a time: you named "
                            + good.id() + " and " + named.id() + ".");
                }
                good = named;
            }
            if (claimed != null) {
                if (type != null && type != claimed) {
                    throw new IllegalArgumentException("Scarce or abundant, not both.");
                }
                type = claimed;
            }
        }
        if (good == null) {
            throw new IllegalArgumentException("Which good? One of " + goodIds() + ".");
        }
        if (type == null && unsaid == null) {
            throw new IllegalArgumentException("Scarce or abundant?");
        }
        if (type == null) {
            type = unsaid;
        }
        return new Said(good, type);
    }

    private static Good goodCalled(String word) {
        for (Good good : Good.values()) {
            if (word.equals(good.id()) || word.equals(good.id() + "s")) {
                return good;
            }
        }
        return null;
    }

    private static ClaimType typeCalled(String word) {
        for (ClaimType type : ClaimType.values()) {
            if (word.equals(type.name().toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        return null;
    }

    private static String goodIds() {
        StringBuilder ids = new StringBuilder();
        for (Good good : Good.values()) {
            ids.append(ids.isEmpty() ? "" : ", ").append(good.id());
        }
        return ids.toString();
    }
}
