package hearsay.paper;

import hearsay.Params;

import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The diamond trade Hearsay puts on a villager's counter, and keeps priced.
 *
 * <p><strong>The villager buys, and never sells.</strong> Vanilla has villagers sell
 * enchanted diamond gear and exactly one trade anywhere buys raw diamonds — the Toolsmith
 * at Expert, one for one emerald, on about a twentieth of them. Having villagers sell
 * diamonds would make diamonds renewable and quietly rewrite the game's economy, which is
 * far too large a side effect for a rumour simulator. Buying changes nothing about what
 * exists in the world, only what somebody will pay for it.
 *
 * <p>It also gives the player the right verb. A scarcity panic means the village will pay
 * more for diamonds, so a player sells into the panic they started.
 */
final class Counter {

    /**
     * The professions that plausibly want diamonds, and the three vanilla already links to
     * them. A librarian buying diamonds would need explaining; a weaponsmith doing it needs
     * none.
     *
     * <p>A plain set rather than an EnumSet: professions stopped being an enum and became a
     * registry, so these are objects to be compared rather than constants to be switched on.
     */
    private static final Set<Villager.Profession> SMITHS = Set.of(
            Villager.Profession.ARMORER,
            Villager.Profession.TOOLSMITH,
            Villager.Profession.WEAPONSMITH);

    /**
     * How many diamonds a villager will buy before they need to restock. Vanilla's own
     * limit, left alone: a panicking village pays its inflated price a fixed number of
     * times and then stops until it works at its station again, which players already
     * understand and which stops a stack being sold into a single panic.
     */
    private static final int USES_BEFORE_RESTOCK = 12;

    /** Emeralds are capped at a stack; a village cannot pay what it cannot hold. */
    private static final int MOST_EMERALDS = 64;

    private Counter() {
    }

    static boolean canTrade(Villager villager) {
        return SMITHS.contains(villager.getProfession());
    }

    /**
     * Rebuilds this villager's diamond trade at what they personally would pay.
     *
     * <p>Their own asking price, not the market's: a villager who believes the lie pays
     * more than one who does not, which is visible, explicable, and gives the player a
     * reason to shop around.
     *
     * <p>Replaces vanilla's Expert diamond purchase where it exists rather than sitting
     * beside it. Two trades buying the same diamond at different prices would let the
     * player take whichever is better, and that is a measurement problem before it is a
     * balance one: the receipts would mix a Hearsay price with a fixed vanilla one, so
     * "what did the lie earn me" could no longer be attributed.
     */
    static void setPrice(Villager villager, int emeralds) {
        List<MerchantRecipe> kept = new ArrayList<>();
        for (MerchantRecipe existing : villager.getRecipes()) {
            if (!buysDiamonds(existing)) {
                kept.add(existing);
            }
        }
        kept.add(diamondPurchase(emeralds));
        villager.setRecipes(kept);
    }

    /** True if this is a trade Hearsay is managing, so a player using it is news. */
    static boolean isManaged(MerchantRecipe recipe) {
        return buysDiamonds(recipe);
    }

    private static boolean buysDiamonds(MerchantRecipe recipe) {
        return !recipe.getIngredients().isEmpty()
                && recipe.getIngredients().get(0).getType() == Material.DIAMOND
                && recipe.getResult().getType() == Material.EMERALD;
    }

    private static MerchantRecipe diamondPurchase(int emeralds) {
        int paid = Math.clamp(emeralds, 1, MOST_EMERALDS);
        MerchantRecipe recipe = new MerchantRecipe(
                new ItemStack(Material.EMERALD, paid), 0, USES_BEFORE_RESTOCK, true);
        recipe.addIngredient(new ItemStack(Material.DIAMOND, 1));
        // Vanilla's demand and reputation adjustments off, on this trade only. They are
        // state held on the villager entity rather than in the recipe, so a session with
        // them running could not be replayed, and replay is what this project is built on.
        recipe.setPriceMultiplier(0f);
        recipe.setDemand(0);
        recipe.setSpecialPrice(0);
        return recipe;
    }

    /**
     * What one diamond is worth in emeralds, from the 100-is-normal index.
     *
     * <p>A diamond is worth {@code DIAMOND_IN_EMERALDS} when nobody believes anything,
     * which is not vanilla's one emerald. At one emerald a 30% panic is unrepresentable,
     * and this figure is what every experiment from E1 was calibrated against.
     */
    static int emeraldsFor(int index, Params params) {
        return Math.max(1, Math.round(DIAMOND_IN_EMERALDS * index / (float) params.basePrice()));
    }

    static final int DIAMOND_IN_EMERALDS = 8;
}
