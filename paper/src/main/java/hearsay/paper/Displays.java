package hearsay.paper;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.util.Transformation;

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

    /** How high above the villager's own position the label rides. */
    private static final float LABEL_HEIGHT = 0.9f;

    private final Map<Integer, UUID> labels = new LinkedHashMap<>();
    /**
     * Says there is no market rather than showing a dash, which reads as a fault. Villagers
     * have no position until they are seen meeting somebody, so the market cannot open
     * until enough of them have been, however far a rumor has got in the meantime.
     */
    private final BossBar priceBar = BossBar.bossBar(
            Component.text("Diamonds: no market yet", NamedTextColor.GRAY),
            0.0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);

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
     * Writes what each villager believes above their head.
     *
     * <p>The label rides the villager as a passenger rather than being moved to where they
     * were last seen. A simulation tick is ten seconds apart, so a label that was teleported
     * each tick trailed well behind anyone walking, and stayed where a villager had been
     * standing when they moved on.
     *
     * <p>A villager who has not heard anything is left alone: an empty label above every
     * head would say nothing and hide the village.
     */
    void showBeliefs(World world, Map<Integer, Villager> bodies, Map<Integer, Double> confidences) {
        bodies.forEach((id, body) -> {
            Double confidence = confidences.get(id);
            if (confidence == null || !body.isValid()) {
                removeLabel(world, id);
                return;
            }
            labelFor(world, id, body).text(
                    Component.text("Diamonds scarce? " + Math.round(confidence * 100) + "%")
                            .color(confidence >= BELIEVES ? NamedTextColor.GOLD : NamedTextColor.GRAY));
        });
    }

    /**
     * One frame of a whisper: a thread of particles between two villagers.
     *
     * <p>Drawn repeatedly over about a second by the caller. A single frame of a handful of
     * particles, once every ten seconds, was there but almost impossible to catch.
     */
    void showWhisperFrame(World world, Location teller, Location listener) {
        int steps = 20;
        for (int step = 0; step <= steps; step++) {
            double along = step / (double) steps;
            Location point = teller.clone().add(
                    (listener.getX() - teller.getX()) * along,
                    (listener.getY() - teller.getY()) * along + 1.4,
                    (listener.getZ() - teller.getZ()) * along);
            world.spawnParticle(Particle.HAPPY_VILLAGER, point, 2, 0.06, 0.06, 0.06, 0);
        }
    }

    void tell(Player player, Component message) {
        player.sendActionBar(message);
    }

    /**
     * The sound of a whisper, played to the listening player rather than into the world.
     *
     * <p>A sound played into the world goes out under the category of whatever made it, so
     * anyone with friendly creatures turned down hears nothing, and villager chatter covers
     * what is left. Sent to the player under the master category it still comes from the
     * right direction but cannot be turned off by accident.
     */
    void playWhisper(Player player, Location where) {
        player.playSound(where, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 1.0f, 1.4f);
    }

    void removeEverything(World world) {
        for (int id : new ArrayList<>(labels.keySet())) {
            removeLabel(world, id);
        }
    }

    private TextDisplay labelFor(World world, int villagerId, Villager body) {
        UUID existing = labels.get(villagerId);
        if (existing != null) {
            Entity entity = world.getEntity(existing);
            if (entity instanceof TextDisplay display && display.isValid()) {
                return display;
            }
        }
        TextDisplay created = world.spawn(body.getLocation(), TextDisplay.class, display -> {
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(true);
            Transformation raised = display.getTransformation();
            raised.getTranslation().set(0f, LABEL_HEIGHT, 0f);
            display.setTransformation(raised);
        });
        body.addPassenger(created);
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
