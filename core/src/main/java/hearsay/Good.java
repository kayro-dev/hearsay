package hearsay;

/**
 * A thing a village can be wrong about the supply of.
 *
 * <p>Every good is an independent market: its own price, its own noise, its own random
 * streams for gossip, exaggeration and the market's wobble, and its own range of rumour
 * ids. Nothing about one good can disturb another — a lie about diamonds that moved the
 * price of bread would be a bug, and the tests are written so that it would fail loudly
 * rather than show up as a slightly odd number in a sweep.
 *
 * <p><strong>The order is part of the seed contract.</strong> Each good branches its random
 * streams and its rumour ids from its position here, so reordering these gives every seed
 * a different village. Diamond is first and branches nothing: it keeps exactly the streams
 * and ids it had before any other good existed, which is what lets every experiment from E1
 * to E38 stand. New goods are appended, never inserted.
 */
public enum Good {

    DIAMOND("diamond", "diamonds");

    /**
     * How many rumour ids each good may use before it would run into the next good's range.
     * A village that invents a million rumours about one good has a problem this number is
     * not the cause of.
     */
    static final int RUMOR_IDS_PER_GOOD = 1_000_000;

    private final String id;
    private final String plural;

    Good(String id, String plural) {
        this.id = id;
        this.plural = plural;
    }

    /** The name claims and recipes use, which is the name every saved session already has. */
    public String id() {
        return id;
    }

    /**
     * What a villager calls several of them. Not simply the id with an "s", which is how
     * "breads" would have got into a sentence.
     */
    public String plural() {
        return plural;
    }

    /**
     * Where this good's rumour ids begin. Diamond at 0, so every id recorded before stage 2
     * is unchanged; each good after it a million further on.
     */
    public int firstRumorId() {
        return ordinal() * RUMOR_IDS_PER_GOOD;
    }

    /** The good a claim is about. */
    public static Good of(String id) {
        for (Good good : values()) {
            if (good.id.equals(id)) {
                return good;
            }
        }
        throw new IllegalArgumentException("No good called " + id);
    }

    public static Good of(Claim claim) {
        return of(claim.item());
    }
}
