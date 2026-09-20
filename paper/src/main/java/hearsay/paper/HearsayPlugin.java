package hearsay.paper;

import hearsay.Bubble;
import hearsay.ClaimType;
import hearsay.Params;
import hearsay.RecipeFile;
import hearsay.Simulation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Files;
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

    /** How far away a whisper still puts a line on your action bar. */
    private static final double WHISPER_VISIBLE_RANGE = 48.0;

    /** A whisper is drawn this many times, this many game ticks apart: about a second. */
    private static final int WHISPER_FRAMES = 8;
    private static final long WHISPER_FRAME_GAP = 3L;

    private VillageSession session;
    private Displays displays;
    private BukkitTask ticking;
    private UUID watcher;
    private World world;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadEverythingSavingNeeds();
        getLogger().info("Hearsay is listening. /hearsay start to bind a village.");
    }

    /**
     * Saves a throwaway session at startup, so every class the real save needs is loaded
     * while the jar is certainly the one this plugin came from.
     *
     * <p>Classes load when they are first used. Saving is the last thing a session does, so
     * its classes are the last to load, and anything that disturbs the jar in between —
     * deploying a new build over a running server, most likely — takes the session with it.
     * Loading them up front costs a few milliseconds and removes the whole failure.
     */
    private void loadEverythingSavingNeeds() {
        Path warmUp = getDataFolder().toPath().resolve("sessions").resolve(".warmup");
        try {
            RecipeFile.write(new Simulation(0, Params.defaults(), List.of()).toRun(), warmUp);
            Files.deleteIfExists(warmUp);
        } catch (RuntimeException | IOException e) {
            getLogger().warning("Could not check that saving works: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        // A session that is only saved by /hearsay stop is a session lost to every server
        // restart, and the recipe is the whole point of playing one.
        if (session != null) {
            try {
                Path saved = session.save(getDataFolder().toPath().resolve("sessions"));
                getLogger().info("Saved the running session to " + saved.getFileName());
            } catch (RuntimeException | LinkageError e) {
                getLogger().severe("Could not save the running session: " + e);
            }
        }
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
            case "stop" -> stopFor(player, args);
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
        Map<Integer, Villager> bodies = whoIsAround();
        Map<Integer, Location> positions = new LinkedHashMap<>();
        bodies.forEach((id, body) -> positions.put(id, body.getLocation()));

        List<Telling> tellings = session.advance(positions);

        displays.showBeliefs(world, bodies, session.confidences());
        session.price().ifPresent(price -> displays.showPrice(price, Params.defaults().basePrice()));

        for (Telling telling : tellings) {
            Villager teller = bodies.get(telling.tellerId());
            Villager listener = bodies.get(telling.listenerId());
            if (teller == null || listener == null) {
                continue;
            }
            announceWhisper(telling, teller, listener);
        }
    }

    /**
     * Draws a whisper over about a second rather than for a single instant, and follows the
     * two villagers while it does, so it can actually be seen ten seconds apart.
     */
    private void announceWhisper(Telling telling, Villager teller, Villager listener) {
        String said = session.nameOf(telling.tellerId()) + " whispers to "
                + session.nameOf(telling.listenerId());
        getLogger().info(said + " (" + Math.round(telling.newConfidence() * 100) + "%)");

        Player player = watcher == null ? null : getServer().getPlayer(watcher);
        if (player != null && player.getWorld().equals(world)
                && player.getLocation().distance(teller.getLocation()) < WHISPER_VISIBLE_RANGE) {
            displays.tell(player, Component.text(said, NamedTextColor.GRAY));
            displays.playWhisper(player, teller.getLocation());
        }

        new BukkitRunnable() {
            private int frame = 0;

            @Override
            public void run() {
                if (!teller.isValid() || !listener.isValid() || world == null) {
                    cancel();
                    return;
                }
                displays.showWhisperFrame(world, teller.getLocation(), listener.getLocation());
                if (++frame >= WHISPER_FRAMES) {
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, WHISPER_FRAME_GAP);
    }

    /** The bound villagers that are still around, by simulation id. */
    private Map<Integer, Villager> whoIsAround() {
        Map<Integer, Villager> bodies = new LinkedHashMap<>();
        session.bodies().forEach((id, body) -> {
            if (getServer().getEntity(body) instanceof Villager villager && villager.isValid()) {
                bodies.put(id, villager);
            }
        });
        return bodies;
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
        for (Map.Entry<Integer, Villager> standing : whoIsAround().entrySet()) {
            double distance = standing.getValue().getLocation().distance(player.getLocation());
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

    private void stopFor(Player player, String[] args) {
        if (session == null) {
            player.sendMessage(Component.text("Nothing bound.", NamedTextColor.RED));
            return;
        }
        boolean evenIfItCannotBeSaved = args.length > 1 && args[1].equalsIgnoreCase("force");

        try {
            Path saved = session.save(getDataFolder().toPath().resolve("sessions"));
            player.sendMessage(Component.text("Saved " + saved.getFileName()
                    + " after " + session.tick() + " ticks.", NamedTextColor.GREEN));
            player.sendMessage(Component.text("Ask what would have happened without your lie: "
                    + "counterfactual --file <that file>", NamedTextColor.GRAY));
        } catch (RuntimeException | LinkageError e) {
            // Keep the session bound: it is still in memory, and throwing it away would
            // turn a failed save into a lost one.
            getLogger().severe("Could not save the session: " + e);
            player.sendMessage(Component.text("Could not save the session: " + e,
                    NamedTextColor.RED));
            player.sendMessage(Component.text("If the jar was replaced while this server was "
                    + "running, this session cannot be saved.", NamedTextColor.RED));
            if (!evenIfItCannotBeSaved) {
                player.sendMessage(Component.text("It is still running. /hearsay stop force "
                        + "to end it and lose it.", NamedTextColor.GRAY));
                return;
            }
        }
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
