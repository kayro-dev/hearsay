package hearsay.paper;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import hearsay.ClaimType;
import hearsay.MarketRegion;
import hearsay.PriceMood;
import hearsay.Spot;

import org.bukkit.entity.Villager;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the player sees. Kept apart from the simulation so that what the village
 * believes and what is drawn above its heads cannot drift into each other.
 */
final class Displays {

    /** Confidence at or above which a villager is shown as believing rather than informed. */
    private static final double BELIEVES = 0.5;

    /** How high above a villager the moment-it-took mark sits, clear of their name plate. */
    private static final float MARK_HEIGHT = 1.1f;

    private final java.util.Set<UUID> marks = new java.util.LinkedHashSet<>();

    /** The gossip level worth walking across the village for. Matches the plugin's advice. */
    private static final double TALKATIVE = 0.6;

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
     *
     * <p>The change leads and the index follows in grey, because 138 says nothing until
     * you have worked out it means 38% dear. Colour and wording come from
     * {@link PriceMood}, which the dashboard reads too, so the bar and the page never
     * disagree about what counts as a panic.
     */
    void showPrice(int price, int basePrice) {
        float lowest = basePrice * 0.5f;
        float highest = basePrice * 2.0f;
        float fraction = Math.clamp((price - lowest) / (highest - lowest), 0f, 1f);
        priceBar.progress(fraction);

        PriceMood mood = PriceMood.of(price, basePrice);
        priceBar.color(barColour(mood));
        priceBar.name(Component.text("Diamonds  ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(PriceMood.describe(price, basePrice))
                        .color(TextColor.fromHexString(mood.hex())))
                .append(Component.text("   " + mood.label().toLowerCase(java.util.Locale.ROOT)
                        + " · index " + price, NamedTextColor.GRAY)));
    }

    /**
     * The nearest boss-bar colour to the mood. Minecraft offers six and none of them are
     * the exact hex, so this is the closest warm one for a rising price and the closest
     * cool one for a falling one, never green: a rising price here is a village
     * frightened, not a village doing well.
     */
    private static BossBar.Color barColour(PriceMood mood) {
        return switch (mood) {
            case PANIC -> BossBar.Color.RED;
            case ALARMED -> BossBar.Color.RED;
            case RISING -> BossBar.Color.YELLOW;
            case NORMAL -> BossBar.Color.WHITE;
            case EASING -> BossBar.Color.BLUE;
            case GLUT -> BossBar.Color.BLUE;
        };
    }

    /**
     * Writes what each villager believes above their head.
     *
     * <p>The label rides the villager as a passenger rather than being moved to where they
     * were last seen. A simulation tick is ten seconds apart, so a label that was teleported
     * each tick trailed well behind anyone walking, and stayed where a villager had been
     * standing when they moved on.
     *
     * <p>Every villager is named, whether or not they have heard anything. Until a
     * villager's name was written above them there was no way to tell which of them
     * {@code /hearsay who} was talking about, and the choice of who to lie to is worth
     * about a third of whether a rumor takes hold (E8, E27). A name nobody can match to a
     * body is not a name.
     */
    void showBeliefs(World world, Map<Integer, Villager> bodies, Map<Integer, String> names,
                     Map<Integer, Double> gossip, Map<Integer, Double> confidences,
                     Map<Integer, Integer> asks, int basePrice, Map<Integer, Spot> spotsToShow) {
        bodies.forEach((id, body) -> {
            if (!body.isValid()) {
                removeLabel(world, id);
                return;
            }
            labelFor(world, id, body).text(labelText(names.get(id), gossip.get(id),
                    confidences.get(id), asks.get(id), basePrice, spotsToShow.get(id)));
        });
    }

    /**
     * Who they are, what they would charge, and where they are if it was asked for.
     *
     * <p>The asking price leads as a change rather than an index, in the colours
     * {@link PriceMood} gives it, so a villager talked into a panic is visibly warm from
     * across the village and one who thinks diamonds are plentiful is visibly cool. A
     * villager asking the ordinary price is white and says nothing, which is most of them
     * most of the time.
     */
    private static Component labelText(String name, Double gossip, Double confidence,
                                       Integer ask, int basePrice, Spot spot) {
        Component label = Component.text(name == null ? "?" : name,
                gossip != null && gossip >= TALKATIVE ? NamedTextColor.GREEN : NamedTextColor.WHITE);
        if (gossip != null) {
            label = label.append(Component.text(
                    " " + Math.round(gossip * 100) + "%",
                    gossip >= TALKATIVE ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        }
        if (ask != null) {
            PriceMood mood = PriceMood.of(ask, basePrice);
            label = label.append(Component.newline())
                    .append(Component.text(PriceMood.describe(ask, basePrice))
                            .color(TextColor.fromHexString(mood.hex())))
                    .append(Component.text("  " + ask, NamedTextColor.GRAY));
        }
        if (spot != null) {
            label = label.append(Component.newline())
                    .append(Component.text(spot.name(), NamedTextColor.AQUA));
        }
        if (confidence != null) {
            label = label.append(Component.newline()).append(
                    Component.text("believes " + Math.round(confidence * 100) + "%")
                            .color(confidence >= BELIEVES ? NamedTextColor.GOLD : NamedTextColor.GRAY));
        }
        return label;
    }

    /**
     * One frame of a whisper: a thread of particles between two villagers, in the claim's
     * colour — warm for a shortage, cool for a glut, as VISUAL_LANGUAGE.md sets out.
     *
     * <p>Drawn repeatedly over about a second by the caller. A single frame of a handful of
     * particles, once every ten seconds, was there but almost impossible to catch.
     */
    void showWhisperFrame(World world, Location teller, Location listener, ClaimType type) {
        Particle particle = type == ClaimType.SCARCE ? Particle.FLAME : Particle.SOUL_FIRE_FLAME;
        int steps = 20;
        for (int step = 0; step <= steps; step++) {
            double along = step / (double) steps;
            Location point = teller.clone().add(
                    (listener.getX() - teller.getX()) * along,
                    (listener.getY() - teller.getY()) * along + 1.4,
                    (listener.getZ() - teller.getZ()) * along);
            world.spawnParticle(particle, point, 1, 0.04, 0.04, 0.04, 0);
        }
    }

    /**
     * A ring of particles around the marked market, so the player can see where it is.
     *
     * <p>Drawn once per simulation tick rather than continuously: the boundary is a fact
     * to be checked occasionally, not something that should be glowing at you all evening.
     * Bone white, the colour of an ordinary price, because the market itself has no
     * opinion — what happens inside it is what has the opinion.
     */
    void showMarketEdge(World world, MarketRegion market) {
        int steps = Math.max(24, (int) (market.radius() * 4));
        for (int step = 0; step < steps; step++) {
            double around = step * 2 * Math.PI / steps;
            world.spawnParticle(Particle.END_ROD,
                    market.x() + Math.cos(around) * market.radius(),
                    market.y() + 0.4,
                    market.z() + Math.sin(around) * market.radius(),
                    1, 0, 0, 0, 0);
        }
    }

    /**
     * A mark over somebody who has just crossed into believing it, and only then.
     *
     * <p>A mark every time anyone repeated anything would be on constantly and would say
     * nothing. This fires on the moment the rumour took, which happens once per villager
     * per claim, and fades on its own.
     */
    void markBelief(World world, Location where, ClaimType type) {
        boolean scarce = type == ClaimType.SCARCE;
        TextDisplay mark = world.spawn(where.clone().add(0, MARK_HEIGHT, 0), TextDisplay.class,
                display -> {
                    display.setBillboard(Display.Billboard.CENTER);
                    display.setSeeThrough(true);
                    display.text(Component.text(scarce ? "❗" : "❄")
                            .color(TextColor.fromHexString(
                                    (scarce ? PriceMood.PANIC : PriceMood.GLUT).hex())));
                });
        marks.add(mark.getUniqueId());
    }

    /** Clears marks older than the caller cares to keep. Called once a tick. */
    void fadeMarks(World world) {
        for (UUID id : new ArrayList<>(marks)) {
            Entity entity = world.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
            marks.remove(id);
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
    void playWhisper(Player player, Location where, ClaimType type) {
        // Higher for a shortage, lower for a glut: the same two directions the colours use.
        player.playSound(where, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER,
                1.0f, type == ClaimType.SCARCE ? 1.4f : 0.8f);
    }

    /** The one sound that marks a rumour taking hold, distinct from merely hearing it. */
    void playBeliefTaking(Player player, Location where) {
        player.playSound(where, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 1.0f, 1.9f);
    }

    /**
     * Outlines every villager by what they know: grey for having heard it, warm for
     * believing it, nothing at all for the rest.
     *
     * <p>Through scoreboard teams, because a team gives the outline its colour and shows it
     * through walls without anything being drawn each tick. Only two states glow on
     * purpose: a village where everything is outlined is a village where the outline means
     * nothing, so villagers who have not heard the rumour are left plain.
     */
    void showWhoKnows(Scoreboard scoreboard, Map<Integer, Villager> bodies,
                      Map<Integer, Double> confidences, Collection<UUID> alreadyLit) {
        Team heard = team(scoreboard, "hearsay_heard", NamedTextColor.GRAY);
        Team believes = team(scoreboard, "hearsay_believes",
                TextColor.fromHexString(PriceMood.ALARMED.hex()));

        bodies.forEach((id, body) -> {
            if (!body.isValid() || alreadyLit.contains(body.getUniqueId())) {
                return; // /hearsay who is lighting this one for its own reasons
            }
            String entry = body.getUniqueId().toString();
            heard.removeEntry(entry);
            believes.removeEntry(entry);

            Double confidence = confidences.get(id);
            if (confidence == null) {
                body.setGlowing(false);
                return;
            }
            (confidence >= BELIEVES ? believes : heard).addEntry(entry);
            body.setGlowing(true);
        });
    }

    /**
     * Outlines these villagers in the gossip colour, so a name in a list becomes a body to
     * walk to. Green, which the price bands never use, because how talkative somebody is
     * is a fact about them and not a mood.
     */
    void highlight(Scoreboard scoreboard, Collection<Villager> bodies) {
        Team talkers = team(scoreboard, "hearsay_talkers", NamedTextColor.GREEN);
        for (Villager body : bodies) {
            if (body.isValid()) {
                talkers.addEntry(body.getUniqueId().toString());
                body.setGlowing(true);
            }
        }
    }

    void stopHighlighting(Scoreboard scoreboard, Collection<Villager> bodies) {
        Team talkers = scoreboard.getTeam("hearsay_talkers");
        for (Villager body : bodies) {
            if (body.isValid()) {
                if (talkers != null) {
                    talkers.removeEntry(body.getUniqueId().toString());
                }
                body.setGlowing(false);
            }
        }
    }

    private static Team team(Scoreboard scoreboard, String name, TextColor colour) {
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.registerNewTeam(name);
        }
        team.color(NamedTextColor.nearestTo(colour));
        return team;
    }

    /** Takes every villager out of every team this plugin made, on the way out. */
    void forgetTeams(Scoreboard scoreboard) {
        for (String name : List.of("hearsay_heard", "hearsay_believes", "hearsay_talkers")) {
            Team team = scoreboard.getTeam(name);
            if (team != null) {
                team.unregister();
            }
        }
    }

    void removeEverything(World world) {
        for (int id : new ArrayList<>(labels.keySet())) {
            removeLabel(world, id);
        }
        fadeMarks(world);
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
