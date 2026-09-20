package hearsay.paper;

import hearsay.Spot;
import hearsay.SpotMapper;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;

import java.util.Optional;

/**
 * Reads where a villager sleeps and works out of Minecraft, and hands both to
 * {@link SpotMapper}, which decides what that makes of them.
 *
 * <p>The only part of the mapping that needs to know one block from another: which
 * workstations are a market stall and which are a field. Everything about distance and
 * precedence lives in the core, where it can be tested.
 */
final class Whereabouts {

    private Whereabouts() {
    }

    /** Where this villager counts as standing, for the simulation's purposes. */
    static Spot spotOf(Villager villager) {
        return SpotMapper.spotFor(placeOf(villager.getLocation()),
                remembered(villager, MemoryKey.HOME),
                remembered(villager, MemoryKey.JOB_SITE),
                kindOfWorkstation(villager));
    }

    /**
     * What sort of place a villager's workstation is.
     *
     * <p>A farmer's composter is the field; everything else a villager works at is somewhere
     * they trade. A villager whose workstation block has been broken or replaced since they
     * claimed it falls back to trading, which is harmless: they will not be standing at a
     * block that is no longer there.
     */
    private static Spot kindOfWorkstation(Villager villager) {
        Optional<Location> site = rememberedLocation(villager, MemoryKey.JOB_SITE);
        if (site.isEmpty()) {
            return Spot.MARKET;
        }
        Material block = site.get().getBlock().getType();
        return block == Material.COMPOSTER || block == Material.FARMLAND
                ? Spot.FIELDS
                : Spot.MARKET;
    }

    private static Optional<SpotMapper.Place> remembered(Villager villager, MemoryKey<Location> key) {
        return rememberedLocation(villager, key).map(Whereabouts::placeOf);
    }

    private static Optional<Location> rememberedLocation(Villager villager, MemoryKey<Location> key) {
        try {
            return Optional.ofNullable(villager.getMemory(key));
        } catch (IllegalStateException unloaded) {
            // The remembered place is in a chunk that is not loaded, so it cannot be read.
            // Treating that as "no such place" sends them to the village at large, which is
            // where a villager whose bed is far away actually is.
            return Optional.empty();
        }
    }

    private static SpotMapper.Place placeOf(Location location) {
        return new SpotMapper.Place(location.getX(), location.getY(), location.getZ());
    }
}
