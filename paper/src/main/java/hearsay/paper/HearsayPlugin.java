package hearsay.paper;

import hearsay.Bearing;
import hearsay.Bubble;
import hearsay.ClaimType;
import hearsay.MarketRegion;
import hearsay.Params;
import hearsay.RecipeFile;
import hearsay.Simulation;
import hearsay.Spot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
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
public final class HearsayPlugin extends JavaPlugin implements Listener {

    /** How far from the player to look for a village. */
    private static final double BINDING_RANGE = 64.0;

    /** How near a villager must be to have a rumor planted in them. */
    private static final double WHISPERING_RANGE = 5.0;

    /** Real seconds per simulation tick. A Minecraft day part is far too slow to watch. */
    private static final long SECONDS_PER_TICK = 10;

    private static final long GAME_TICKS_PER_SECOND = 20;

    /** How far away a whisper still puts a line on your action bar. */
    private static final double WHISPER_VISIBLE_RANGE = 48.0;

    /** The confidence at which a villager counts as believing it, per VISUAL_LANGUAGE.md. */
    private static final double BELIEVES = 0.5;

    /** A market a player would pace out without thinking about it. */
    private static final double DEFAULT_MARKET_RADIUS = 8.0;

    /** The gossip level worth walking across the village for. */
    private static final double TALKATIVE = 0.6;

    /** How many talkers to outline at once, and for how long. */
    private static final int MOST_TO_LIGHT = 3;
    private static final long GLOW_SECONDS = 30;

    /** A whisper is drawn this many times, this many game ticks apart: about a second. */
    private static final int WHISPER_FRAMES = 8;
    private static final long WHISPER_FRAME_GAP = 3L;

    private VillageSession session;
    private Displays displays;
    private BukkitTask ticking;
    private UUID watcher;
    private World world;
    private boolean showingSpots;

    /**
     * The marked market, or null. Not part of the recipe on purpose: it decides which
     * sightings get reported, and the sightings are what a session records, so a run
     * reproduces without the region needing to exist.
     */
    private MarketRegion market;

    /** Villagers /hearsay who is outlining in green, which the belief glow must not fight. */
    private final java.util.Set<UUID> lit = new java.util.LinkedHashSet<>();

    /** How many bound villagers were still alive last tick, to notice when one is not. */
    private int lastSeenAlive;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadEverythingSavingNeeds();
        getServer().getPluginManager().registerEvents(this, this);
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
            case "who" -> who(player);
            case "market" -> market(player, args);
            case "debug" -> toggleSpots(player);
            case "stop" -> stopFor(player, args);
            default -> player.sendMessage(Component.text(
                    "/hearsay start [seed] | rumor diamonds scarce | who | market <radius>"
                    + " | market clear | status | stop"));
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
        if (nearby.size() < 2) {
            player.sendMessage(Component.text("Found " + nearby.size() + " villagers within "
                    + (int) BINDING_RANGE + " blocks. A village needs at least two to gossip.",
                    NamedTextColor.RED));
            return;
        }

        session = VillageSession.bind(seed, nearby);
        displays = new Displays();
        displays.showPriceBarTo(player);
        watcher = player.getUniqueId();

        player.sendMessage(Component.text("Bound " + session.boundCount() + " villagers, seed "
                + seed + ". A tick every " + SECONDS_PER_TICK + "s of game time.",
                NamedTextColor.GREEN));

        // Every mind here has a body now. Still worth saying when the village is not the
        // size the model was tuned at, because that is a thing to weigh when reading it.
        if (session.boundCount() != Simulation.VILLAGER_COUNT) {
            player.sendMessage(Component.text("This village has " + session.boundCount()
                    + " villagers; the model was tuned on " + Simulation.VILLAGER_COUNT
                    + ". Figures are shares of " + session.boundCount() + ".",
                    NamedTextColor.YELLOW));
        }

        // Said at binding rather than discovered halfway through. Only smiths buy
        // diamonds, and a village with none is a village the player cannot sell into -
        // worth knowing before an hour of it has been played.
        int smiths = 0;
        for (Villager villager : world.getNearbyEntitiesByType(
                Villager.class, player.getLocation(), BINDING_RANGE)) {
            if (Counter.canTrade(villager)) {
                smiths++;
            }
        }
        if (smiths == 0) {
            player.sendMessage(Component.text("No armorer, toolsmith or weaponsmith here, so "
                    + "nobody will buy diamonds. Put down a blast furnace, smithing table or "
                    + "grindstone and let a villager take it up.", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text(smiths + " smith" + (smiths == 1 ? "" : "s")
                    + " will buy diamonds, at whatever they each believe they are worth.",
                    NamedTextColor.GREEN));
        }

        lastSeenAlive = session.boundCount();
        long period = SECONDS_PER_TICK * GAME_TICKS_PER_SECOND;
        // The first tick runs at once rather than in ten seconds' time. Villagers are
        // created by a tick, like every other change, so until one has run there are
        // bodies with nobody in them and every command that names a villager fails.
        ticking = getServer().getScheduler().runTaskTimer(this, this::tick, 0L, period);
    }

    /** One simulation tick: look, report, advance, draw. */
    private void tick() {
        if (session == null || world == null) {
            return;
        }
        Map<Integer, Villager> bodies = whoIsAround();
        noticeTheMissing(bodies);
        Map<Integer, Location> positions = new LinkedHashMap<>();
        Map<Integer, Spot> spots = new LinkedHashMap<>();
        bodies.forEach((id, body) -> {
            positions.put(id, body.getLocation());
            spots.put(id, Whereabouts.spotOf(body, market));
        });

        session.survey(session.tick() + 1, bodies);
        // Kept from before the tick so a villager crossing into believing can be told from
        // one who already did. The mark is for the moment it took, not for every repetition.
        Map<Integer, Double> before = new LinkedHashMap<>(session.confidences());
        List<Telling> tellings = session.advance(positions, spots);

        Map<Integer, String> names = new LinkedHashMap<>();
        Map<Integer, Double> gossip = new LinkedHashMap<>();
        bodies.keySet().forEach(id -> {
            names.put(id, session.nameOf(id));
            gossip.put(id, session.gossipOf(id));
        });
        Map<Integer, String> labels = session.labels();
        displays.showBeliefs(world, bodies, names, labels, gossip, session.confidences(),
                session.asks(), Params.defaults().basePrice(),
                showingSpots ? spots : Map.of());
        displays.showWhoKnows(getServer().getScoreboardManager().getMainScoreboard(),
                bodies, session.confidences(), lit);
        displays.fadeMarks(world);
        priceTheCounters(bodies);
        if (market != null) {
            displays.showMarketEdge(world, market);
        }
        session.price().ifPresent(price -> displays.showPrice(price, Params.defaults().basePrice()));

        for (Telling telling : tellings) {
            Villager teller = bodies.get(telling.tellerId());
            Villager listener = bodies.get(telling.listenerId());
            boolean tookHold = telling.newConfidence() >= BELIEVES
                    && before.getOrDefault(telling.listenerId(), 0.0) < BELIEVES;
            if (teller == null || listener == null) {
                continue;
            }
            announceWhisper(telling, teller, listener);

            if (tookHold) {
                // The moment the rumour took, marked once. Every repetition after this one
                // leaves no mark, or the village would be covered in them.
                displays.markBelief(world, listener.getLocation(), telling.type());
                Player watching = watcher == null ? null : getServer().getPlayer(watcher);
                if (watching != null && watching.getWorld().equals(world)
                        && watching.getLocation().distance(listener.getLocation())
                                < WHISPER_VISIBLE_RANGE) {
                    displays.playBeliefTaking(watching, listener.getLocation());
                }
            }
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
            displays.playWhisper(player, teller.getLocation(), telling.type());
        }

        new BukkitRunnable() {
            private int frame = 0;

            @Override
            public void run() {
                if (!teller.isValid() || !listener.isValid() || world == null) {
                    cancel();
                    return;
                }
                displays.showWhisperFrame(world, teller.getLocation(),
                        listener.getLocation(), telling.type());
                if (++frame >= WHISPER_FRAMES) {
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, WHISPER_FRAME_GAP);
    }

    /**
     * Rebuilds the diamond trade on every smith, at what that villager personally would pay.
     *
     * <p>Once a tick rather than continuously, so a menu the player has open cannot change
     * under their hands mid-trade.
     */
    private void priceTheCounters(Map<Integer, Villager> bodies) {
        Map<Integer, Integer> asks = session.asks();
        bodies.forEach((id, body) -> {
            if (Counter.canTrade(body) && asks.containsKey(id)) {
                Counter.setPrice(body, Counter.emeraldsFor(asks.get(id), Params.defaults()));
            }
        });
    }

    /** Puts every bound villager back to normal, so stopping leaves no outlines behind. */
    private void stopGlowing() {
        if (session == null || world == null) {
            return;
        }
        session.bodies().values().forEach(id -> {
            if (world.getEntity(id) instanceof Villager villager) {
                villager.setGlowing(false);
            }
        });
        lit.clear();
    }

    /**
     * Says so when a bound villager stops being there.
     *
     * <p>A villager that has died or wandered out of a loaded chunk is simply skipped, and
     * silently: one session lost fifteen villagers of twenty to zombies over fifty
     * sprinted nights and read as a village that would not gossip. A village emptying out
     * should be the most obvious thing on the screen, not something found afterwards in a
     * survey.
     */
    private void noticeTheMissing(Map<Integer, Villager> bodies) {
        int alive = bodies.size();
        if (alive < lastSeenAlive) {
            String lost = lastSeenAlive - alive == 1 ? "A villager is" : (lastSeenAlive - alive)
                    + " villagers are";
            getLogger().warning(lost + " gone: " + alive + " of " + session.boundCount()
                    + " left. Zombies, most likely.");
            Player player = watcher == null ? null : getServer().getPlayer(watcher);
            if (player != null) {
                player.sendMessage(Component.text(lost + " gone. " + alive + " of "
                        + session.boundCount() + " left — try /difficulty peaceful.",
                        NamedTextColor.RED));
            }
        }
        lastSeenAlive = alive;
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
        if (notReady(player)) {
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
        double gossip = session.gossipOf(nearest);
        player.sendMessage(Component.text("You tell " + session.nameOf(nearest)
                + " that diamonds are " + type.name().toLowerCase() + ".", NamedTextColor.GOLD));

        // Who you tell is worth about a third of whether a rumor takes hold, and gossip
        // predicts it: see E8. Telling a quiet villager is a wasted session, and there is
        // no way to tell one from another by looking.
        player.sendMessage(Component.text(session.nameOf(nearest) + " is "
                + describeTalker(gossip) + " (gossip " + Math.round(gossip * 100) + "%).",
                gossip >= TALKATIVE ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        if (gossip < TALKATIVE) {
            player.sendMessage(Component.text("A quieter villager than you want. "
                    + "/hearsay who lists the talkers.", NamedTextColor.YELLOW));
        }
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

    /**
     * Writes each villager's spot above their head, so the mapping can be checked by
     * walking about rather than trusted. A villager at their workstation should read
     * MARKET or FIELDS, one in bed HOME, and one wandering WELL.
     */
    private void toggleSpots(Player player) {
        showingSpots = !showingSpots;
        player.sendMessage(Component.text(showingSpots
                ? "Showing each villager's spot. Workstation reads MARKET or FIELDS, bed "
                        + "reads HOME, anywhere else reads WELL."
                : "Spots hidden.", NamedTextColor.AQUA));
        if (session != null && world != null && !showingSpots) {
            displays.removeEverything(world);
            displays.forgetTeams(getServer().getScoreboardManager().getMainScoreboard());
            stopGlowing();
        }
    }

    /**
     * Somebody traded with a villager. If it was the diamond trade Hearsay manages, the
     * village has just watched diamonds arrive.
     *
     * <p>Listened to at MONITOR, after anything that might cancel it, so nothing is
     * recorded that did not happen. A cancelled trade is a trade the village never saw.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent event) {
        if (session == null || world == null || !Counter.isManaged(event.getTrade())) {
            return;
        }
        if (!(event.getVillager() instanceof Villager body)) {
            return; // a wandering trader has no mind here
        }
        Integer trader = session.idOf(body.getUniqueId());
        if (trader == null) {
            return; // not one of ours
        }

        // Everyone near enough to see it, which is the same range a whisper carries. E25
        // established that people who witness one event together are not separate sources
        // for it, and a sale in the square is the same shape as a rumour in the square.
        java.util.NavigableSet<Integer> watching = new java.util.TreeSet<>();
        watching.add(trader);
        for (Villager nearby : body.getLocation().getNearbyEntitiesByType(
                Villager.class, VillageSession.TALKING_RANGE)) {
            Integer id = session.idOf(nearby.getUniqueId());
            if (id != null) {
                watching.add(id);
            }
        }

        int diamonds = event.getTrade().getIngredients().get(0).getAmount();
        int emeralds = event.getTrade().getResult().getAmount();
        session.recordTrade(trader, diamonds, emeralds, watching);

        // The running total at this counter, not this one click: shift-clicking fires the
        // event once per item, and telling the player "1 diamond" a dozen times over would
        // describe the transaction they are actually making rather poorly.
        int soFar = session.soldSoFar(trader);
        event.getPlayer().sendActionBar(Component.text(
                session.nameOf(trader) + " takes " + soFar + " diamond"
                        + (soFar == 1 ? "" : "s") + ". " + watching.size() + " watching.",
                NamedTextColor.AQUA));
    }

    /**
     * Crouching and right-clicking a villager asks them what they have heard.
     *
     * <p>Crouching, not an empty hand. An empty hand is exactly how a player opens a trade
     * menu, so asking on an empty hand meant nobody could trade at all — the ask cancelled
     * the trade before it opened. Crouch-to-interact-differently is the idiom the game
     * already uses everywhere, and it leaves plain right-click doing what it has always
     * done.
     *
     * <p>Cancelled so the trade screen does not open on top of the answer. Nothing here
     * touches the simulation — it reads beliefs the village already holds — so no
     * experiment is affected by a player being curious.
     */
    @EventHandler(ignoreCancelled = true)
    public void onAsk(PlayerInteractEntityEvent event) {
        if (session == null || event.getHand() != EquipmentSlot.HAND
                || !(event.getRightClicked() instanceof Villager body)) {
            return;
        }
        if (!event.getPlayer().isSneaking()) {
            return; // a plain right-click is how you trade, and always was
        }
        Integer id = session.idOf(body.getUniqueId());
        if (id == null || notReady(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        List<String> heard = session.whatTheyHeard(id);
        player.sendMessage(Component.text(session.nameOf(id), NamedTextColor.WHITE)
                .append(Component.text(heard.isEmpty()
                        ? " has heard nothing at all." : " has heard:", NamedTextColor.GRAY)));
        for (String said : heard) {
            player.sendMessage(Component.text("  " + said, NamedTextColor.AQUA));
        }
    }

    /** Says why nothing can be answered yet, if anything cannot. */
    private boolean notReady(Player player) {
        if (session == null) {
            player.sendMessage(Component.text("Nothing bound. /hearsay start first.",
                    NamedTextColor.RED));
            return true;
        }
        if (!session.awake()) {
            player.sendMessage(Component.text("The village is still waking up. Try again in "
                    + "a moment.", NamedTextColor.YELLOW));
            return true;
        }
        return false;
    }

    private static String describeTalker(double gossip) {
        if (gossip >= 0.8) {
            return "the village gossip";
        }
        if (gossip >= 0.6) {
            return "talkative";
        }
        if (gossip >= 0.35) {
            return "not much of a talker";
        }
        return "nearly silent";
    }

    /**
     * Lists the bound villagers by how much they talk, so a rumor can be planted in
     * somebody who will pass it on. Nothing about a villager shows this from the outside.
     */
    private void who(Player player) {
        if (notReady(player)) {
            return;
        }
        Map<Integer, Villager> here = whoIsAround();
        List<Integer> byTalkativeness = new ArrayList<>(session.bodies().keySet());
        byTalkativeness.sort((a, b) -> Double.compare(session.gossipOf(b), session.gossipOf(a)));

        player.sendMessage(Component.text("Who talks, most first. The glowing ones are worth "
                + "walking to; their names are above their heads.", NamedTextColor.AQUA));
        Map<Integer, String> labels = session.labels();
        List<Villager> toLight = new ArrayList<>();
        for (int id : byTalkativeness) {
            double gossip = session.gossipOf(id);
            String label = labels.get(id);
            Villager body = here.get(id);
            String where = body == null ? " (gone)"
                    : " " + (int) body.getLocation().distance(player.getLocation())
                            + " blocks " + bearingFrom(player.getLocation(), body.getLocation());
            player.sendMessage(Component.text("  " + session.nameOf(id)
                    + (label == null ? "" : ", " + label) + "  "
                    + Math.round(gossip * 100) + "%  " + describeTalker(gossip) + where,
                    gossip >= TALKATIVE ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            if (body != null && gossip >= TALKATIVE && toLight.size() < MOST_TO_LIGHT) {
                toLight.add(body);
            }
        }

        if (toLight.isEmpty()) {
            player.sendMessage(Component.text("Nobody here is much of a talker. A rumor "
                    + "planted in this village will struggle.", NamedTextColor.YELLOW));
            return;
        }
        displays.highlight(getServer().getScoreboardManager().getMainScoreboard(), toLight);
        toLight.forEach(body -> lit.add(body.getUniqueId()));
        // Lit for a while rather than for good: a village permanently outlined stops
        // telling the player anything, and the glow is meant to answer one question once.
        getServer().getScheduler().runTaskLater(this,
                () -> {
                    displays.stopHighlighting(
                            getServer().getScoreboardManager().getMainScoreboard(), toLight);
                    toLight.forEach(body -> lit.remove(body.getUniqueId()));
                }, 20L * GLOW_SECONDS);
    }

    private static String bearingFrom(Location from, Location to) {
        return Bearing.of(to.getX() - from.getX(), to.getZ() - from.getZ());
    }

    /**
     * Marks the ground the player is standing on as the market, or says where it is.
     *
     * <p>Anyone inside counts as being at the market from the next tick, whatever their
     * workstation says. Nothing about the simulation changes: the region only decides what
     * the plugin reports having seen.
     */
    private void market(Player player, String[] args) {
        String what = args.length > 1 ? args[1].toLowerCase() : "show";
        // "/hearsay market 8" is what a person types, and a bare number cannot mean
        // anything else, so it means the same as "set 8". Insisting on the word would be
        // the command telling the player they had typed it wrong when they had not.
        boolean bareRadius = what.matches("-?\\d+(\\.\\d+)?");
        switch (bareRadius ? "set" : what) {
            case "set" -> {
                String given = bareRadius ? args[1] : (args.length > 2 ? args[2] : null);
                double radius;
                try {
                    radius = given == null ? DEFAULT_MARKET_RADIUS : Double.parseDouble(given);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("That is not a radius: " + given,
                            NamedTextColor.RED));
                    return;
                }
                Location here = player.getLocation();
                try {
                    market = new MarketRegion(here.getX(), here.getY(), here.getZ(), radius);
                } catch (IllegalArgumentException e) {
                    player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
                    return;
                }
                player.sendMessage(Component.text("The market is here, " + market.diameter()
                        + " blocks across. Anyone standing in it is a trader from the next "
                        + "tick, whatever they do for a living.", NamedTextColor.GREEN));
            }
            case "clear" -> {
                market = null;
                player.sendMessage(Component.text("No marked market. Who counts as a trader "
                        + "goes back to being guessed from workstations.", NamedTextColor.YELLOW));
            }
            default -> {
                if (market == null) {
                    player.sendMessage(Component.text("No market marked. Stand where you want "
                            + "it and /hearsay market " + (int) DEFAULT_MARKET_RADIUS
                            + " — that is the radius in blocks.", NamedTextColor.GRAY));
                    return;
                }
                player.sendMessage(Component.text("The market is " + market.diameter()
                        + " blocks across, centred on " + (int) market.x() + ", "
                        + (int) market.y() + ", " + (int) market.z(), NamedTextColor.AQUA));
            }
        }
    }

    private void status(Player player) {
        if (notReady(player)) {
            return;
        }
        Map<Integer, Double> confidences = session.confidences();
        long believers = confidences.values().stream().filter(c -> c >= 0.5).count();

        int alive = whoIsAround().size();
        if (alive < session.boundCount()) {
            player.sendMessage(Component.text("Only " + alive + " of " + session.boundCount()
                    + " bound villagers are still here. The rest are dead or unloaded, and "
                    + "the simulation still counts them.", NamedTextColor.RED));
        }
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
            player.sendMessage(Component.text("A survey of " + session.sightingsRecorded()
                    + " sightings was saved beside it, for sweeping the spot mapping.",
                    NamedTextColor.GRAY));
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
            displays.forgetTeams(getServer().getScoreboardManager().getMainScoreboard());
            stopGlowing();
        }
        session = null;
        displays = null;
        world = null;
        watcher = null;
    }
}
