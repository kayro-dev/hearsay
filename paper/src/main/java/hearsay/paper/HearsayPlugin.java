package hearsay.paper;

import hearsay.Bubble;
import hearsay.ClaimType;
import hearsay.Params;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The thin layer between Minecraft and Hearsay.
 *
 * <p>Everything here is reading positions, drawing on the screen, and handling commands.
 * Who meets whom is worked out by {@code ProximityPairing}, and what anyone believes by the
 * simulation; both are plain Java with no game types in them, so both can be tested without
 * a server.
 *
 * <p>A spike. One village, bound once, with villagers who wander in afterwards ignored.
 */
public final class HearsayPlugin extends JavaPlugin {

    /** How far from the player to look for a village. */
    private static final double BINDING_RANGE = 64.0;

    /** How near a villager must be to have a rumor planted in them. */
    private static final double WHISPERING_RANGE = 5.0;

    /** Real seconds per simulation tick. A Minecraft day part is far too slow to watch. */
    private static final long SECONDS_PER_TICK = 10;

    private static final long GAME_TICKS_PER_SECOND = 20;

    private VillageSession session;
    private Displays displays;
    private BukkitTask ticking;
    private UUID watcher;
    private World world;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("Hearsay is listening. /hearsay start to bind a village.");
    }

    @Override
    public void onDisable() {
        stop();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Hearsay needs somebody standing in a village.");
            return true;
        }
        String action = args.length > 0 ? args[0].toLowerCase() : "help";
        switch (action) {
            case "start" -> start(player, args);
            case "rumor", "rumour" -> plant(player, args);
            case "status" -> status(player);
            case "stop" -> stopFor(player);
            default -> player.sendMessage(Component.text(
                    "/hearsay start [seed] | rumor diamonds scarce | status | stop"));
        }
        return true;
    }

    private void start(Player player, String[] args) {
        if (session != null) {
            player.sendMessage(Component.text("A village is already bound. /hearsay stop first.",
                    NamedTextColor.RED));
            return;
        }
        long seed = VillageSession.seedFor(player, args);
        world = player.getWorld();

        List<UUID> nearby = new ArrayList<>();
        for (Villager villager : world.getNearbyEntitiesByType(
                Villager.class, player.getLocation(), BINDING_RANGE)) {
            nearby.add(villager.getUniqueId());
        }
        if (nearby.isEmpty()) {
            player.sendMessage(Component.text("No villagers within " + (int) BINDING_RANGE
                    + " blocks.", NamedTextColor.RED));
            return;
        }

        session = VillageSession.bind(seed, nearby);
        displays = new Displays();
        displays.showPriceBarTo(player);
        watcher = player.getUniqueId();

        player.sendMessage(Component.text("Bound " + session.boundCount() + " villagers, seed "
                + seed + ". A tick every " + SECONDS_PER_TICK + "s.", NamedTextColor.GREEN));

        long period = SECONDS_PER_TICK * GAME_TICKS_PER_SECOND;
        ticking = getServer().getScheduler().runTaskTimer(this, this::tick, period, period);
    }

    /** One simulation tick: look, report, advance, draw. */
    private void tick() {
        if (session == null || world == null) {
            return;
        }
        Map<Integer, Location> positions = whereEveryoneIs();
        List<Telling> tellings = session.advance(positions);

        displays.showBeliefs(world, positions, session.confidences());
        session.price().ifPresent(price -> displays.showPrice(price, Params.defaults().basePrice()));

        Player player = watcher == null ? null : getServer().getPlayer(watcher);
        for (Telling telling : tellings) {
            Location teller = positions.get(telling.tellerId());
            Location listener = positions.get(telling.listenerId());
            if (teller == null || listener == null) {
                continue;
            }
            displays.showTelling(world, teller, listener);
            if (player != null && player.getLocation().distance(teller) < 24) {
                displays.tell(player, Component.text(session.nameOf(telling.tellerId())
                        + " whispers to " + session.nameOf(telling.listenerId()),
                        NamedTextColor.GRAY));
            }
        }
    }

    /** Where each bound villager is standing, skipping any that have died or unloaded. */
    private Map<Integer, Location> whereEveryoneIs() {
        Map<Integer, Location> positions = new LinkedHashMap<>();
        session.bodies().forEach((id, body) -> {
            var entity = getServer().getEntity(body);
            if (entity instanceof Villager villager && villager.isValid()) {
                positions.put(id, villager.getLocation());
            }
        });
        return positions;
    }

    private void plant(Player player, String[] args) {
        if (session == null) {
            player.sendMessage(Component.text("Nothing bound. /hearsay start first.",
                    NamedTextColor.RED));
            return;
        }
        ClaimType type = args.length > 2 && args[2].equalsIgnoreCase("abundant")
                ? ClaimType.ABUNDANT : ClaimType.SCARCE;

        Integer nearest = nearestBoundVillager(player);
        if (nearest == null) {
            player.sendMessage(Component.text("No bound villager within "
                    + (int) WHISPERING_RANGE + " blocks.", NamedTextColor.RED));
            return;
        }
        session.plantRumorIn(nearest, type);
        player.sendMessage(Component.text("You tell " + session.nameOf(nearest)
                + " that diamonds are " + type.name().toLowerCase() + ".", NamedTextColor.GOLD));
    }

    private Integer nearestBoundVillager(Player player) {
        Integer nearest = null;
        double nearestDistance = WHISPERING_RANGE;
        for (Map.Entry<Integer, Location> standing : whereEveryoneIs().entrySet()) {
            double distance = standing.getValue().distance(player.getLocation());
            if (distance <= nearestDistance) {
                nearest = standing.getKey();
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private void status(Player player) {
        if (session == null) {
            player.sendMessage(Component.text("Nothing bound.", NamedTextColor.RED));
            return;
        }
        Map<Integer, Double> confidences = session.confidences();
        long believers = confidences.values().stream().filter(c -> c >= 0.5).count();

        player.sendMessage(Component.text("Tick " + session.tick()
                + " | price " + session.price().orElse(Params.defaults().basePrice())
                + " | heard " + confidences.size() + "/" + session.boundCount()
                + " | believe " + believers + "/" + session.boundCount()
                + " | a bubble is above " + Bubble.PEAK_ABOVE + " and back under "
                + Bubble.BACK_BELOW, NamedTextColor.AQUA));
    }

    private void stopFor(Player player) {
        if (session == null) {
            player.sendMessage(Component.text("Nothing bound.", NamedTextColor.RED));
            return;
        }
        Path saved = session.save(getDataFolder().toPath().resolve("sessions"));
        player.sendMessage(Component.text("Saved " + saved.getFileName()
                + " after " + session.tick() + " ticks.", NamedTextColor.GREEN));
        player.sendMessage(Component.text(
                "Ask what would have happened without your lie with the headless counterfactual.",
                NamedTextColor.GRAY));
        displays.hidePriceBarFrom(player);
        stop();
    }

    private void stop() {
        if (ticking != null) {
            ticking.cancel();
            ticking = null;
        }
        if (displays != null && world != null) {
            displays.removeEverything(world);
        }
        session = null;
        displays = null;
        world = null;
        watcher = null;
    }
}
