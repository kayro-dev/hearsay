# v2 — a marketplace you can stand in

Proposal only. No code, no defaults changed. The calibrated settings from E1–E30 stay as
they are; nothing is retuned without a sweep, and each stage says which sweeps it invalidates.

Three stages, in this order, because each one is useless without the one before it.

---

## Stage 1 — a market you can walk into

`/hearsay market set <radius>` marks a region around where the player is standing. Any
villager inside it counts as `Spot.MARKET`, whatever their workstation says.

**What changes in core.** Nothing. This is entirely a plugin change: `Whereabouts.spotOf`
already decides a villager's spot and already reports it as a `VillagerSeen` input, and a
region test is one more rule inside that method. Core keeps taking sightings and believing
them, which is the arrangement CLAUDE.md already describes — Minecraft owns the bodies.

**Determinism and replay.** Untouched. A region is not simulation state; it only changes
which `VillagerSeen` inputs the plugin produces, and those are already recorded. A session
replays from its recipe exactly as before, because the recipe contains the sightings the
region produced rather than the region itself.

**Old recipes.** Load unchanged. They contain sightings, not regions.

**Experiments to re-run.** None of the headless ones, since the model has no regions. The
in-game measurements that depend on where villagers stand would want repeating once a real
marketplace exists — the market-size figures behind E17's quorum, and the mapping radius
from E13 — but as new sessions rather than as re-runs.

**Why first.** E13 to E19 were all downstream of not knowing who was in the market. A
region answers that by fiat instead of by inference, and it is the one change here that
makes the existing model more honest rather than bigger.

---

## Stage 2 — more than one thing to be wrong about

A price index per item, each with a normal price in emeralds:

| Item | Normal price |
| --- | --- |
| Diamond | 8 |
| Gold ingot | 2 |
| Iron ingot | 1 |
| Bread | 1 |

**Emeralds are the currency and never a good.** There is no rumour about emeralds and no
index for them; a village cannot panic about the thing it prices everything else in.

**What changes in core.** The larger of the two stages.

- `Claim` already carries an item, so beliefs are per-item today. What is single is the
  *market*: `marketPrice`, `marketNoiseLevel` and `lastObservedPrice` are one value each.
  All three become per-item, keyed by the item, in `WorldState` and on `Villager`.
- `MarketPriceSet`, `MarketNoiseSet` and `PriceObserved` gain an item. They already carry
  their own numbers, so this is one more field of the same kind.
- `Params.basePrice` becomes a lookup: a normal price per item, with the table above as the
  default. `priceSensitivity` stays one number — how far belief moves a price is a fact
  about villagers, not about diamonds.
- Each item gets **its own noise stream**, derived like the others in `RandomStream`. One
  shared wobble would make every price move together, and a village would look like it had
  an opinion when it only had weather.

**Determinism and replay.** Preserved, with one condition: iteration over items must be by
a fixed order, not a hash. Every map in `WorldState` is already sorted for exactly this
reason and the item maps must follow. `RandomStream` gains entries and they must be
**appended**, as `NEIGHBOURHOOD` was in E23, or every existing seed produces a different
village.

**Old recipes.** Keep loading. `RecipeFile` already defaults fields absent from older
versions — the `mixing` and `villagers` fields do this today. An old recipe names no item,
and the reader supplies diamond, which is what those sessions were about. The format
version goes to 5.

**Experiments to re-run.** This is the expensive stage.
- `CalibrationTest` measures one item's bubbles. It keeps working on diamonds and should
  gain nothing until the multi-item behaviour is understood.
- **E5, E18, E24, E25, E29 need re-running per item**, because the market parameters were
  fitted to a base price of 100 and a bread priced at 1 has no room to move — a price that
  can only be 1 or 2 is a different instrument. `PriceMood` already scales its bands by base
  price, which is the same problem solved once; `Bubble` does not, and would need to.
- A new question no experiment covers: **does belief about one item leak into another?**
  It should not, and a sweep should confirm it does not before any of this is trusted.

**A risk worth stating now.** Four items at 8, 2, 1 and 1 emeralds means three of them have
almost no price resolution. A 30% move on bread is a third of an emerald, and Minecraft
trades in whole items. Either prices become ratios reported against a finer internal index,
or the cheap goods cannot bubble at all. **I would fit the indices first and only then
decide how they map to emeralds.**

---

## Stage 3 — trades that actually happen

Belief-driven prices change villagers' real trade menus, and a player trading with a
villager comes back into the simulation as an input event.

**What changes in core.**
- A new input, `PlayerTraded(tick, villagerId, item, emeralds, count)`. It is an input and
  not a derived event, for the same reason `PlantRumor` is: the player did it, the
  simulation did not decide it.
- A villager who trades learns something. The natural rule is the one already in the model:
  a trade at a price is evidence about that price, combined by the same rule
  `PriceObserved` uses. **This is the part most likely to need its own experiment**, because
  it closes a second feedback loop — the player can now talk the village into a panic by
  buying, without ever telling a lie.
- Nothing about menus lives in core. Turning an index into a `MerchantRecipe` is the
  plugin's job.

**Determinism and replay.** Preserved, and this is the stage where that claim earns its
keep. A session is reproducible from seed + params + inputs, and player trades are inputs,
so a played session replays exactly — including a counterfactual with the trades kept and
the lie removed. That is a genuinely new question the project can then ask: **did the lie
move the price, or did I move it by buying?**

**Old recipes.** Keep loading; they contain no trades. Format version 6.

**Experiments to re-run.** None of the existing ones, since nothing about gossip changes.
One new experiment is needed before this ships: how much evidence a trade carries, swept the
way `observationWeight` was in E24 and E29, against the same quiet-village guarantee. A
player who can start a panic by buying bread twice is a bug, not a feature.

---

## What this does to the project's claim

Today the counterfactual answers one question: what would this village have done if nobody
had lied? After stage 3 it answers a harder one: what would it have done if nobody had lied
**and I had not traded**. Those are three timelines rather than two, and the paired-worlds
machinery already runs many worlds, so it can carry them.

That is the argument for doing stage 3 at all. Stages 1 and 2 make the world richer; stage 3
makes the question sharper, and a sharper question is worth more than a bigger world.

## Order of work

1. Stage 1, which is small and makes the existing measurements honest.
2. Play sessions in a real marketplace and re-measure the in-game figures.
3. Stage 2's indices, fitted before any emerald mapping is chosen.
4. Stage 3, with the trade-evidence sweep done before it is enabled by default.
