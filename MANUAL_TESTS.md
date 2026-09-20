# Manual tests

What only a person standing in a village can check. Everything else is covered by
`./gradlew test`; this list is for the parts that need eyes.

Run through it after any change to the `paper` module, and before recording anything.

## Setup

1. Copy `local.properties.example` to `local.properties` and point `server.plugins.dir` at
   your server's `plugins` folder — the folder itself, not the server folder above it. The
   file is gitignored: it is specific to your machine.
2. `./gradlew :paper:deploy`, which copies `Hearsay.jar` into that folder.
3. **Restart the server.** Do not use `/reload` — it is known to leave plugins in a broken
   state, and a plugin that half-reloaded will waste an hour of your evening.

## Checklist

| # | Step | What should happen |
| --- | --- | --- |
| 1 | Start the server | The log says `Hearsay is listening`, with no stack trace |
| 2 | `/hearsay status` before starting | "Nothing bound", not an error |
| 3 | Stand in a village, `/hearsay start` | "Bound N villagers, seed …" with N matching roughly what you can see |
| 4 | `/hearsay start` again | Refuses, telling you to stop first |
| 5 | Watch for a minute | A price bar at the top of the screen; no text above any head yet, because nobody has heard anything |
| 6 | Stand next to a villager, `/hearsay rumor diamonds scarce` | "You tell <name> that diamonds are scarce" |
| 7 | Wait one tick (10s) | Grey text above that villager: `Diamonds scarce? 100%` |
| 8 | Watch the villagers mill about | Text appears above others as they stand near each other |
| 9 | Watch a telling happen | A quiet trade sound, particles between the pair, and an action-bar line `X whispers to Y` when you are within 24 blocks |
| 10 | Watch a villager's number climb past 50% | Their text turns from grey to gold |
| 11 | Watch the price bar | It moves as belief spreads — the bar fills between half the base price and double it |
| 12 | `/hearsay status` | Tick, price, heard and believe counts, all plausible against what you can see |
| 13 | Walk a villager out of the group | Their text follows them, and stops updating once nobody is near them |
| 14 | `/hearsay stop` | "Saved session-….hearsay after N ticks", the price bar goes, all floating text disappears |
| 15 | Check `plugins/Hearsay/sessions/` | The file is there and is readable plain text: a `seed` line, a `params` line, `input plant …` and many `input meet …` lines |
| 16 | Stop the server | No errors on shutdown, and no floating text left behind when you restart |

## The bridge back to the headless tools

The point of saving the session: ask what would have happened without your lie, on a village
you actually played.

| # | Step | What should happen |
| --- | --- | --- |
| 17 | Copy the saved file somewhere convenient | — |
| 18 | Check it reruns identically | The recipe is the run: `RecipeFileTest` covers this headlessly, so a mismatch here means the session recorded something it should not have |
| 19 | Remove the `input plant …` line, save as a second file | Both files load |
| 20 | Compare the two | The timelines should be identical until the tick the rumor was planted, and differ after — every `input meet …` line is the same in both, because Minecraft decided those, not Hearsay |

## Known limits of the spike

These are deliberate, not bugs to report:

- **Villagers who arrive after `/hearsay start` are ignored.** Binding a mind to a body
  halfway through would make the session unreplayable from its recipe.
- **Every observed meeting is reported as happening at the market.** The price needs to know
  where people are, and a pair on its own does not say. Mapping real locations to spots is
  the next piece of work.
- **A villager standing alone at the market is invisible to the price**, because only pairs
  are reported.
- **One village at a time**, and the binding is lost if the server restarts mid-session.
- **A tick is 10 real seconds**, not tied to Minecraft's clock.
