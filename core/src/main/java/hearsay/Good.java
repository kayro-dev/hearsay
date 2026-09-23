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

    /**
     * Repriced from vanilla's one emerald, because at one emerald a 30% panic cannot be
     * shown at all, and diamonds are not renewable so a higher price cannot be farmed.
     */
    DIAMOND("diamond", "diamond", "diamonds", 1, 8, true),

    /**
     * At vanilla's value, three to the emerald, bought by a cleric. Gold is farmable, and
     * pricing it above vanilla would turn a gold farm into an emerald printer.
     */
    GOLD("gold", "gold ingot", "gold ingots", 24, 8, true),

    /**
     * At vanilla's value, four to the emerald, bought by an armorer. Iron farms are the
     * most common farm there is, which is exactly why this must not pay more than vanilla.
     */
    IRON("iron", "iron ingot", "iron ingots", 32, 8, true),

    /**
     * The famine rumour's diamond: bought by a farmer, so a player can sell into a panic
     * about the harvest and the neighbours who watch see there was wheat after all. At
     * vanilla's value, twenty to the emerald.
     *
     * <p>The one good that does not fit the eight-emerald bundle. Eight emeralds' worth is
     * 160 wheat, and a trade takes at most two ingredients of at most a stack each — 128 —
     * so the bundle is 120 in two stacks of 60, for six emeralds. It moves in sixths where
     * every other good moves in eighths, which is the cost of keeping vanilla's value.
     */
    WHEAT("wheat", "wheat", "wheat", 120, 6, true);

    /**
     * How many rumour ids each good may use before it would run into the next good's range.
     * A village that invents a million rumours about one good has a problem this number is
     * not the cause of.
     */
    static final int RUMOR_IDS_PER_GOOD = 1_000_000;

    private final String id;
    private final String singular;
    private final String plural;
    private final int bundle;
    private final int normalEmeralds;
    private final boolean villagerBuys;

    /**
     * @param bundle         how many change hands in one trade
     * @param normalEmeralds what that bundle is worth when nobody believes anything
     * @param villagerBuys   whether villagers buy it from the player, as every
     *                       non-renewable good must be, or sell it, as vanilla already does
     *                       with some renewable ones
     */
    Good(String id, String singular, String plural, int bundle, int normalEmeralds,
         boolean villagerBuys) {
        this.id = id;
        this.singular = singular;
        this.plural = plural;
        this.bundle = bundle;
        this.normalEmeralds = normalEmeralds;
        this.villagerBuys = villagerBuys;
    }

    /**
     * A number of them, as a person would say it: "1 diamond", "24 gold ingots". Diamond's
     * bundle is one, so every diamond sale is exactly the case a plural-only name gets wrong.
     */
    public String amount(int count) {
        return count + " " + (count == 1 ? singular : plural);
    }

    /**
     * This good's bundle split into the stacks one trade can hold.
     *
     * <p>Paper's MerchantRecipe takes one or two ingredients and keeps each within the item's
     * stack size (checked against its javadoc, not assumed). So a bundle over a stack goes
     * into two halves — 120 wheat as two sixties — and a bundle over two stacks cannot be
     * offered at all. Here rather than in the plugin because it is arithmetic with a rule in
     * it, and a good whose bundle could not be traded should fail a test, not a player.
     *
     * @param mostInAStack the item's stack size, 64 for everything so far
     */
    public int[] stacks(int mostInAStack) {
        if (bundle <= mostInAStack) {
            return new int[] {bundle};
        }
        if (bundle > 2 * mostInAStack) {
            throw new IllegalStateException(plural + " come in bundles of " + bundle
                    + ", and a trade holds at most two stacks of " + mostInAStack);
        }
        int first = (bundle + 1) / 2;
        return new int[] {first, bundle - first};
    }

    /** How many change hands in one trade: a fixed bundle, so the emerald count is the price. */
    public int bundle() {
        return bundle;
    }

    /** What one bundle is worth when nobody believes anything. */
    public int normalEmeralds() {
        return normalEmeralds;
    }

    public boolean villagerBuys() {
        return villagerBuys;
    }

    /**
     * What one of these is worth at normal, in emeralds. This is what lets a sale be
     * weighed by value rather than by count: sixteen diamonds is a glut and sixteen loaves
     * is breakfast.
     */
    public double emeraldsEach() {
        return normalEmeralds / (double) bundle;
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
     * "Wheat is", "diamonds are". A good whose plural is its singular is counted as a mass,
     * and a mass takes "is": "wheat are scarce" is nobody talking.
     */
    public String isOrAre() {
        return plural.equals(singular) ? "is" : "are";
    }

    /**
     * Where this good's rumour ids begin. Diamond at 0, so every id recorded before stage 2
     * is unchanged; each good after it a million further on.
     */
    public int firstRumorId() {
        return ordinal() * RUMOR_IDS_PER_GOOD;
    }

    /** The good a rumour is about, read from the range its id falls in. */
    public static Good ofRumor(int rumorId) {
        return values()[rumorId / RUMOR_IDS_PER_GOOD];
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
