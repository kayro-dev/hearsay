package hearsay.paper;

import hearsay.MarketRegion;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;

/**
 * How much of a thing the village can actually see.
 *
 * <p>Counted out of the containers inside the marked market, because that is what makes it
 * a <em>standing fact</em> rather than an event. E34 measured a sale moving a belief by
 * four thousandths and then fading; a chest by the market is still full tomorrow, and a
 * villager who looks at it twice sees the same thing twice.
 *
 * <p>It gives the player a verb they did not have. Stocking a chest by the market calms a
 * village; emptying one frightens it. Both are things you do to the world rather than
 * things you say, which is the whole distinction stage 4 exists to add.
 */
final class Stock {

    /** Far enough above the market floor and below it to catch a cellar or a loft. */
    private static final int HEIGHT = 8;

    private Stock() {
    }

    /**
     * How many of this item sit in containers inside the market.
     *
     * <p>Read-only. Counting what is in a chest cannot change what is in it, and nothing
     * here touches the simulation: it produces a number that becomes an input.
     */
    static int visible(World world, MarketRegion market, Material item) {
        int radius = (int) Math.ceil(market.radius());
        int found = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -HEIGHT; y <= HEIGHT; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double bx = market.x() + x;
                    double by = market.y() + y;
                    double bz = market.z() + z;
                    if (!market.contains(bx, by, bz)) {
                        continue;
                    }
                    Block block = world.getBlockAt((int) bx, (int) by, (int) bz);
                    if (block.getState(false) instanceof Container container) {
                        for (ItemStack held : container.getInventory().getContents()) {
                            if (held != null && held.getType() == item) {
                                found += held.getAmount();
                            }
                        }
                    }
                }
            }
        }
        return found;
    }
}
