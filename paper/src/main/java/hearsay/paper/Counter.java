package hearsay.paper;

import hearsay.Good;
import hearsay.Params;

import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The trades Hearsay puts on a villager's counter, one per good, and keeps priced.
 *
 * <p><strong>Every managed trade is a fixed bundle for a moving number of emeralds.</strong>
 * The bundle is sized so its normal price is vanilla's value — three gold to the emerald,
 * so twenty-four for eight — because gold is farmable and pricing it above vanilla would
 * turn a gold farm into an emerald printer. Diamond alone is repriced, because at vanilla's
 * one emerald a 30% panic cannot be shown and diamonds cannot be farmed. The number of
 * emeralds is the price, and that is the one thing a player has to learn.
 *
 * <p>The villager <strong>buys</strong> every one of today's goods. Villagers only ever buy a
 * non-renewable good, so that no trade here creates one from nothing.
 */
final class Counter {

    /** What each good is called in the game, which only the plugin needs to know. */
    private static final Map<Good, Material> ITEM = new EnumMap<>(Map.of(
            Good.DIAMOND, Material.DIAMOND,
            Good.GOLD, Material.GOLD_INGOT,
            Good.IRON, Material.IRON_INGOT));

    /**
     * Who keeps a counter for each good. Checked against the wiki rather than guessed: the
     * three smiths are the professions vanilla links to diamonds, and the Cleric is the one
     * vanilla has buying gold. A plain set rather than an EnumSet, since professions became
     * a registry rather than an enum.
     */
    private static final Map<Good, Set<Villager.Profession>> WHO = new EnumMap<>(Map.of(
            Good.DIAMOND, Set.of(Villager.Profession.ARMORER, Villager.Profession.TOOLSMITH,
                    Villager.Profession.WEAPONSMITH),
            Good.GOLD, Set.of(Villager.Profession.CLERIC),
            // The Armorer is also a smith, so keeps two counters: diamonds and iron. The
            // first villager to, and the reason every sale is grouped by good as well as
            // by counter.
            Good.IRON, Set.of(Villager.Profession.ARMORER)));

    /**
     * How many bundles a villager takes before they need to restock. Vanilla's own limit,
     * left alone: it stops a stack being sold into a single panic.
     */
    private static final int USES_BEFORE_RESTOCK = 12;

    /** A villager cannot pay what they cannot hold. */
    private static final int MOST_EMERALDS = 64;

    private Counter() {
    }

    /** The goods this villager keeps a counter for, in declared order. */
    static List<Good> goodsFor(Villager villager) {
        List<Good> goods = new ArrayList<>();
        for (Good good : Good.values()) {
            if (WHO.getOrDefault(good, Set.of()).contains(villager.getProfession())) {
                goods.add(good);
            }
        }
        return goods;
    }

    static boolean canTrade(Villager villager) {
        return !goodsFor(villager).isEmpty();
    }

    /**
     * Rebuilds this villager's trade for one good at what they personally would pay.
     *
     * <p>Their own asking price, not the market's: a villager who believes the lie pays
     * more than one who does not, which is visible and gives the player a reason to shop
     * around. Replaces vanilla's own trade for the same good rather than sitting beside it,
     * because two prices for one good would make "what did the lie earn me" unattributable.
     */
    static void setPrice(Villager villager, Good good, int emeralds) {
        List<MerchantRecipe> kept = new ArrayList<>();
        for (MerchantRecipe existing : villager.getRecipes()) {
            if (goodOf(existing).orElse(null) != good) {
                kept.add(existing);
            }
        }
        kept.add(purchaseOf(good, emeralds));
        villager.setRecipes(kept);
    }

    /**
     * The good a trade buys for emeralds, if it is one Hearsay manages or would replace.
     * A player using one of these is news the village hears about.
     */
    static Optional<Good> goodOf(MerchantRecipe recipe) {
        if (recipe.getIngredients().isEmpty() || recipe.getResult().getType() != Material.EMERALD) {
            return Optional.empty();
        }
        Material given = recipe.getIngredients().get(0).getType();
        for (Map.Entry<Good, Material> entry : ITEM.entrySet()) {
            if (entry.getValue() == given) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    private static MerchantRecipe purchaseOf(Good good, int emeralds) {
        MerchantRecipe recipe = new MerchantRecipe(
                new ItemStack(Material.EMERALD, Math.clamp(emeralds, 1, MOST_EMERALDS)),
                0, USES_BEFORE_RESTOCK, true);
        recipe.addIngredient(new ItemStack(ITEM.get(good), good.bundle()));
        // Vanilla's demand and reputation adjustments off, on managed trades only. They are
        // state held on the villager rather than in the saved session, so a session with
        // them running could not be replayed.
        recipe.setPriceMultiplier(0f);
        recipe.setDemand(0);
        recipe.setSpecialPrice(0);
        return recipe;
    }

    /** What a bundle of this good costs at this price index, in whole emeralds. */
    static int emeraldsFor(Good good, int index, Params params) {
        return Math.max(1, Math.round(good.normalEmeralds() * index / (float) params.basePrice()));
    }
}
