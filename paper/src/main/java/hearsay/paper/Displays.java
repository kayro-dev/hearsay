package hearsay.paper;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the player sees. Kept apart from the simulation so that what the village
 * believes and what is drawn above its heads cannot drift into each other.
 */
final class Displays {

    /** Confidence at or above which a villager is shown as believing rather than informed. */
    private static final double BELIEVES = 0.5;

    private final Map<Integer, UUID> labels = new LinkedHashMap<>();
    private final BossBar priceBar = BossBar.bossBar(
            Component.text("Diamond price: -"), 0.5f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);

    void showPriceBarTo(Player player) {
        player.showBossBar(priceBar);
    }

    void hidePriceBarFrom(Player player) {
        player.hideBossBar(priceBar);
    }

    /**
     * Moves the price bar. The bar's fill runs from half the base price to twice it, so a
     * bubble visibly climbs rather than pinning at either end.
     */
    void showPrice(int price, int basePrice) {
        float lowest = basePrice * 0.5f;
        float highest = basePrice * 2.0f;
        float fraction = Math.clamp((price - lowest) / (highest - lowest), 0f, 1f);
        priceBar.progress(fraction);
        priceBar.name(Component.text("Diamond price: " + price)
                .color(price > basePrice ? NamedTextColor.GOLD : NamedTextColor.WHITE));
    }

    /**
     * Writes what each villager believes above their head. A villager who has not heard
     * anything is left alone: an empty label above every head would say nothing and hide
     * the village.
     */
    void showBeliefs(World world, Map<Integer, Location> positions, Map<Integer, Double> confidences) {
        positions.forEach((id, where) -> {
            Double confidence = confidences.get(id);
            if (confidence == null) {
                removeLabel(world, id);
                return;
            }
            TextDisplay label = labelFor(world, id, where);
            label.text(Component.text("Diamonds scarce? " + Math.round(confidence * 100) + "%")
                    .color(confidence >= BELIEVES ? NamedTextColor.GOLD : NamedTextColor.GRAY));
            label.teleport(where.clone().add(0, 2.2, 0));
        });
    }

    /** A quiet sound and a thread of particles between two villagers who just spoke. */
    void showTelling(World world, Location teller, Location listener) {
        world.playSound(teller, Sound.ENTITY_VILLAGER_TRADE, 0.4f, 1.6f);

        int steps = 8;
        for (int step = 0; step <= steps; step++) {
            double along = step / (double) steps;
            Location point = teller.clone().add(
                    (listener.getX() - teller.getX()) * along,
                    (listener.getY() - teller.getY()) * along + 1.2,
                    (listener.getZ() - teller.getZ()) * along);
            world.spawnParticle(Particle.HAPPY_VILLAGER, point, 1, 0, 0, 0, 0);
        }
    }

    void tell(Player player, Component message) {
        player.sendActionBar(message);
    }

    void removeEverything(World world) {
        for (int id : new ArrayList<>(labels.keySet())) {
            removeLabel(world, id);
        }
    }

    private TextDisplay labelFor(World world, int villagerId, Location where) {
        UUID existing = labels.get(villagerId);
        if (existing != null) {
            Entity entity = world.getEntity(existing);
            if (entity instanceof TextDisplay display && display.isValid()) {
                return display;
            }
        }
        TextDisplay created = world.spawn(where.clone().add(0, 2.2, 0), TextDisplay.class, display -> {
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(true);
        });
        labels.put(villagerId, created.getUniqueId());
        return created;
    }

    private void removeLabel(World world, int villagerId) {
        UUID id = labels.remove(villagerId);
        if (id == null) {
            return;
        }
        Entity entity = world.getEntity(id);
        if (entity != null) {
            entity.remove();
        }
    }
}
