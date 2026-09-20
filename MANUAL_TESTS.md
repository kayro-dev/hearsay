# Manual tests

What only a person standing in a village can check. Everything else is covered by
`./gradlew test`; this list is for the parts that need eyes.

Run through it after any change to the `paper` module, and before recording anything.

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
| 6 | Stand next to a villager, `/hearsay rumor diamonds scarce` | "You tell <name> that diamonds are scarce" |
| 7 | Wait one tick (10s) | **Gold** text above that villager: `Diamonds scarce? 100%`. Gold because they are certain — you told them. Grey is for villagers who have merely heard it from someone else |
| 8 | Watch that villager walk | The label stays over their head as they move, with no lag and without being left behind at a workstation |
| 9 | Break their workstation so they wander | The label follows, and the villager still walks about normally — the label rides them, so watch that it has not affected their behaviour |
| 10 | Watch the villagers mill about | Grey text appears above others as they stand near each other |
| 11 | Watch a telling happen | A trade sound, a thread of particles between the pair lasting about a second, and an action-bar line `X whispers to Y` within 48 blocks. The server console logs every telling, so check there if you miss one |
| 12 | Watch a grey villager's number climb past 50% | Their text turns from grey to gold |
| 12b | `/hearsay debug` | Every villager gets an aqua label with their spot. Walk round and check it: one at a workstation reads MARKET, a farmer at a composter reads FIELDS, one asleep reads HOME, one wandering reads WELL |
| 12c | Watch a villager walk from bed to work | The label changes HOME → WELL → MARKET as they go |
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
