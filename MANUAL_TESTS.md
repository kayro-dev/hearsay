# Manual tests

What only a person standing in a village can check. Everything else is covered by
`./gradlew test`; this list is for the parts that need eyes.

Run through it after any change to the `paper` module, and before recording anything.

## Before recording anything

**`/difficulty peaceful`.** A session sprinted over fifty days lost fifteen villagers of
twenty to zombies and read as a village that simply would not gossip. Hostile mobs will
empty a village faster than a rumor can cross it, and the plugin cannot tell the difference
between a villager who is quiet and one who is dead — it now warns when one goes missing,
but peaceful avoids the question.

Check `/hearsay status` partway through: if the bound count and the count still present
disagree, the village is dying.

## Setup

1. Copy `local.properties.example` to `local.properties` and point `server.plugins.dir` at
   your server's `plugins` folder — the folder itself, not the server folder above it. The
   file is gitignored: it is specific to your machine.
2. **Stop the server before deploying.** Replacing the jar under a running server breaks
   its class loading: whatever the plugin has already used keeps working, and the first
   class it has not needed yet cannot be found. Saving a session is the usual casualty,
   because nothing loads those classes until you stop — so the run you were in the middle
   of is the thing you lose. `deploy` now refuses while the world is locked; `-PforceDeploy`
   overrides it, and you should not want to.
3. `./gradlew :paper:deploy`, which copies `Hearsay.jar` into that folder.
4. **Start the server.** Do not use `/reload` — it is known to leave plugins in a broken
   state, and a plugin that half-reloaded will waste an hour of your evening.

## Checklist

| # | Step | What should happen |
| --- | --- | --- |
| 1 | Start the server | The log says `Hearsay is listening`, with no stack trace |
| 2 | `/hearsay status` before starting | "Nothing bound", not an error |
| 3 | Stand in a village, `/hearsay start` | "Bound N villagers, seed …" with N matching roughly what you can see. If N is under 20 it warns you, and it should: see the limits below |
| 4 | `/hearsay start` again | Refuses, telling you to stop first |
| 5 | Watch for a minute | A price bar reading "Diamonds: no market yet"; no text above any head yet, because nobody has heard anything |
| 5b | Keep watching | The bar shows a price once enough villagers have been seen meeting — a few ticks. It waits on positions, not on beliefs, so it can stay empty while a rumor is already spreading |
| 5c | `/hearsay who` | The bound villagers listed most talkative first, with a percentage, how far away each is, and which way to walk. Up to three of the talkers are outlined in the world for 30 seconds |
| 5d | Look at the village | Every bound villager has their name and gossip percentage above their head, green at 60% or more. The name matches the list from 5c |
| 5e | Walk to a glowing villager | The glow fades after about 30 seconds. Running `/hearsay who` again lights them up once more |
| 5f | Look at the boss bar before any lie | `Diamonds  · 0%  normal · index 100`, white. The change leads, the index follows in grey |
| 5g | Look at any villager's name plate | A second line with what they would charge, as a change: `· 0%  100` while nobody believes anything |
| 6 | Stand next to that villager, `/hearsay rumor diamonds scarce` | "You tell <name> that diamonds are scarce", then a line saying how talkative they are. Below 60% it warns you: telling a quiet villager wastes the session |
| 5h | `/hearsay market` before marking one | "No market marked", and who counts as a trader is still guessed from workstations |
| 5i | Stand in an open square, `/hearsay market 8` | "The market is here, 16 blocks across". A ring of white particles appears at its edge each tick |
| 5j | Watch a villager walk into the ring | From the next tick their name plate reads `MARKET` with `/hearsay debug` on, whatever their workstation is. A farmer in the square is a trader |
| 5k | `/hearsay market set 8` | The same as 5i. Both forms work, because a bare number cannot mean anything else |
| 5l | `/hearsay market 1` and `market 100` | Both refused with the allowed range. A market nobody can stand in two of, or one the size of the village, is a mistake |
| 5m | `/hearsay market clear` | The ring goes, and spots go back to being read from workstations |
| 6a | Plant a rumour, then wait a tick | The villager you told is outlined **warm orange**. Villagers who have merely heard it are outlined **grey**. Everyone else has no outline at all |
| 6b | Watch a whisper happen | A trail of **flame** particles from teller to listener for about a second, and a chime. Warm particles for scarcity; a rumour that diamonds are *abundant* draws **soul fire** in cool blue with a lower chime |
| 6c | Watch someone cross into believing | A `❗` in the panic red appears above them for one tick, with a higher chime. It appears once per villager, not on every repetition |
| 6d | Let the price climb past 130 | The boss bar turns red and reads `▲ +30%` or more. Villagers who believe it show a warm change above their heads; villagers who do not still read `· 0%` |
| 6e | Let a bubble deflate past 85 | The bar turns **blue** and reads `▼ -15%` or worse. Rising is never green and falling is never red: see VISUAL_LANGUAGE.md |
| 6f | `/hearsay stop` | Every outline goes out, every name plate and mark disappears, and no villager is left glowing |
| 7 | Wait one tick (10s) | **Gold** text above that villager: `Diamonds scarce? 100%`. Gold because they are certain — you told them. Grey is for villagers who have merely heard it from someone else |
| 8 | Watch that villager walk | The label stays over their head as they move, with no lag and without being left behind at a workstation |
| 9 | Break their workstation so they wander | The label follows, and the villager still walks about normally — the label rides them, so watch that it has not affected their behaviour |
| 10 | Watch the villagers mill about | Grey text appears above others as they stand near each other |
| 11 | Watch a telling happen | A trade sound, a thread of particles between the pair lasting about a second, and an action-bar line `X whispers to Y` within 48 blocks. The server console logs every telling, so check there if you miss one |
| 12 | Watch a grey villager's number climb past 50% | Their text turns from grey to gold |
| 12b | `/hearsay debug` | Every villager gets an aqua label with their spot. Walk round and check it: one within about ten blocks of their workstation reads MARKET, a farmer near a composter reads FIELDS, one in or near bed reads HOME, one further from both reads WELL |
| 12c | Watch a villager walk from bed to work | The label changes HOME → WELL → MARKET as they go. Ten blocks is a generous radius, so in a tight village the WELL stretch may be short or absent |
| 12d | `/hearsay debug` again | Labels go back to beliefs only |
| 13 | Watch the price bar | It moves as belief spreads — the bar fills between half the base price and double it |
| 14 | `/hearsay status` | Tick, price, heard and believe counts, all plausible against what you can see |
| 15 | `/hearsay stop` | "Saved session-….hearsay after N ticks", the price bar goes, all floating text disappears. If it cannot save it says so and keeps the session running rather than losing it; `/hearsay stop force` ends it anyway |
| 16 | Check `plugins/Hearsay/sessions/` | Two files: `session-….hearsay`, readable plain text with a `seed` line, a `params` line and `input …` lines; and `survey-….csv`, one row per villager per tick |
| 16b | Check the survey has real distances | Columns `toBed` and `toJobSite` should mostly be numbers, with `-1.00` only for villagers with no bed or no job |
| 17 | Stop the server | No errors on shutdown, and no floating text left behind when you restart |

## The bridge back to the headless tools

The point of saving the session: ask what would have happened without your lie, on a village
you actually played.

| # | Step | What should happen |
| --- | --- | --- |
| 18 | Find the file | It is in `<server>/plugins/Hearsay/sessions/`, named `session-<seed>-<time>.hearsay`. Note the capital H: the folder is named after the plugin |
| 19 | Run the counterfactual on it | `./gradlew :cli:run --args="counterfactual --file <path>"` prints both timelines day by day, diverging at the tick you planted the rumor |
| 20 | Check the divergence tick | It equals the tick in the file's `input plant …` line. Anything earlier means the two timelines differ for some reason other than your lie |
| 21 | Try `worlds --file <path>` | It refuses and explains why: a played session has only the future that happened |

## Known limits of the spike

These are deliberate, not bugs to report:

- **The simulation always has twenty villagers, however many bodies it finds.** Bind a
  village of nine and eleven simulated villagers sit at home forever, never meeting anyone.
  They do not distort the price, which is made of whoever stands in the market, but every
  figure counted out of twenty reads low, and "half the village believes" — ten of twenty —
  cannot happen at all. Village size wants to become a parameter, which means recalibrating,
  so for now the plugin warns and the numbers should be read against the bound count.
- **Villagers who arrive after `/hearsay start` are ignored.** Binding a mind to a body
  halfway through would make the session unreplayable from its recipe.
- **A tick is ten seconds of game time, not wall-clock.** `/tick sprint 20d` sprints the
  simulation along with the world, which is a quick way to get a long session.
- **Every observed meeting is reported as happening at the market.** The price needs to know
  where people are, and a pair on its own does not say. Mapping real locations to spots is
  the next piece of work.
- **Where everyone stands is reported every tick**, not only when they meet, so a villager
  alone at a stall is part of the market. Only changes reach the log, so a villager who has
  not moved costs nothing.
- **Two villagers both in bed do not meet.** Two beds are two rooms, which is the same rule
  the headless model uses for home. Expect quiet nights.
- **One village at a time**, and the binding is lost if the server restarts mid-session.
- **Many worlds cannot be asked of a played session.** Forking needs futures to roll, and
  Minecraft decided where everybody walked exactly once. A played session answers "what
  would have happened without my lie"; it cannot answer "how likely was that".
- **The session saves on `/hearsay stop` and on server shutdown**, but a crash loses it.
