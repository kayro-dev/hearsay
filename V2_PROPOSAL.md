# v2 — a marketplace you can stand in

Proposal only. No code, no defaults changed. The calibrated settings from E1–E30 stay as
they are; nothing is retuned without a sweep, and each stage says which sweeps it invalidates.

### The order, and why it is not the numbering

The stages keep the numbers they were first given, because E28, E31 and E32 refer to them
by those numbers and rewriting history to tidy a document is a bad trade. **The numbers are
names. This is the order:**

| | Stage | Why here |
| --- | --- | --- |
| 1st | **1 — a marked market** *(built)* | Small, and it made every existing measurement honest |
| 2nd | **3 — real trades, diamonds only** | Depth before breadth. One good, taken all the way to the player's hands |
| 3rd | **4 — reality checks** | Damping needs a true supply to damp against, and stage 3 is what creates one |
| 4th | **2 — more goods** | Only once the model is damped |

**Why goods come last.** Every good added before stage 4 is a good whose market parameters
must be fitted, and stage 4 changes how belief moves, so all of it would be fitted twice.
Four goods calibrated before damping is four calibrations thrown away. One good taken to the
end tells us whether the idea works at all, and that is worth more than four goods that
half-work.

**The gate between the second and third.** Stage 4 is not finished when it is built; it is
finished when **swing decay is below 1 and the lie still causes its bubbles** — E32's
baseline of 1.05, never settling, with 5 bubbles against 0. Goods do not begin until both
hold.

---

Each stage is useless without the ones before it.

---

## Stage 1 — a market you can walk into — **BUILT**

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

**As built.** `MarketRegion` in core is the geometry, with a radius between 2 and 64 blocks
and height included — a villager in a cellar under the square is not at the market, and a
flat circle would say they were. `/hearsay market set|clear` marks it, the edge is drawn as
a ring of particles once a tick, and `Whereabouts.spotOf` lets the region win over the
workstation. Core is untouched apart from the geometry, exactly as proposed.

---

## Stage 2 — more than one thing to be wrong about — *fourth, after the model is damped*

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

### Price resolution, solved the way vanilla already solves it

The problem: four items at 8, 2, 1 and 1 emeralds means three of them have almost no room to
move. A 30% rise on bread is a third of an emerald, and Minecraft trades whole items.

Vanilla has the answer already, and it is worth copying rather than inventing around.
**A trade has two sides, and which side carries the price depends on which is worth more.**

| Kind | Trade reads | A rise shows as |
| --- | --- | --- |
| **Expensive goods** (diamond, gold) | *n emeralds → 1 item* | the emerald count going up: 8 → 11 |
| **Cheap goods** (bread, iron) | *1 emerald → n items* | the item count coming **down**: 6 bread → 4 bread |

So bread at **6 per emerald** is normal, and **4 per emerald is +50%**, which is a visible,
whole-number change that needs no fractional emerald. The index stays continuous internally
and is rounded only at the point of display, so the simulation keeps its resolution and the
trade menu is always a whole number of things.

This also fixes something the current model papers over. Rounding a continuous index to a
whole number of items is **lossy in a way that matters at the cheap end**: between 6 and 5
bread per emerald there is a 20% step, so small price moves on cheap goods are invisible
until they are large enough to cross one. That is not a flaw to be engineered away — it is
what a real market with coarse denominations does — but it means **cheap goods will bubble
less readily than dear ones**, and a sweep must measure that rather than assume the four
items behave alike.

**What this adds to stage 2's work.** `Params` gains, per item, a normal price *and* which
way round the trade goes. The display rule — emeralds up, or items down — belongs beside
`PriceMood` in core, since the dashboard and the trade menu must agree about what "+50%"
means for bread.

### Vanilla's own price adjustments, on trades Hearsay manages

Minecraft already moves trade prices for two reasons of its own: **demand**, which raises the
price of a trade the player has used heavily and decays back over time, and **reputation**,
which lowers prices for a player a village likes and raises them for one it does not.

Both would be operating on the same number Hearsay is trying to set, and **the recommendation
is to disable both on managed trades** — but the trade-offs are real and worth stating.

| | Disabling vanilla's adjustments | Leaving them on |
| --- | --- | --- |
| **The claim** | The price is belief plus noise, and nothing else. A counterfactual answers cleanly | The price is belief plus noise plus demand plus reputation, and the counterfactual can no longer say which moved it |
| **Determinism** | Preserved. Every input is recorded | Vanilla's demand lives in the villager entity, not in the recipe. A replayed session would not reproduce, which breaks the project's central guarantee |
| **What the player feels** | One mechanism, legible: prices move because the village believes something | Two mechanisms fighting, and the stronger one is invisible. A player who trades heavily would see prices rise and reasonably conclude their lie worked |
| **Realism** | A village that has never heard of supply | Vanilla's demand is genuinely a supply effect, and losing it makes the economy thinner |
| **Effort** | Set the trade's demand and price multiplier to zero each time it is rebuilt, and leave unmanaged trades alone | None |

**The determinism row is the one that decides it.** Vanilla's demand is state held on the
villager entity and not in the recipe, so a session with it enabled could not be replayed,
and replay is the thing this whole project is built on. Everything else is a preference;
that one is a contradiction.

**The honest cost.** Hearsay's economy then has no supply side at all: prices move because
people believe things, never because anyone ran out. That is a fair description of what this
project models, and it should be said plainly in the README rather than left for someone to
discover. **Stage 2 should not silently become an economics simulator.** If a supply effect
is ever wanted, it belongs in the model as a recorded event, not borrowed from the entity.

**Unmanaged trades are left completely alone.** A villager's other offers keep vanilla's
behaviour, so nothing about the rest of the game changes.

---

## Stage 3 — trades that actually happen — *next*

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

### The concrete plan

#### Which trade Hearsay takes over

Checked against the wiki rather than assumed, because the answer changes the design:
**vanilla villagers do not sell raw diamonds at all.** Three professions sell *enchanted
diamond gear* — Armorer, Toolsmith, Weaponsmith — and exactly one trade anywhere in the game
buys raw diamonds: the **Toolsmith at Expert level, 1 diamond for 1 emerald, and it appears
on only about 5% of them.**

That settles the direction. **Hearsay manages the buy side: the villager buys diamonds and
pays emeralds.** The alternative — villagers selling diamonds — would make diamonds
renewable and quietly rewrite the game's economy, which is far too large a side effect for a
rumour simulator. Buying changes nothing about what exists in the world; it changes what
somebody will pay for it.

It also gives the player the right verb. A scarcity panic means **the village will pay more
for diamonds**, so the player sells into the panic they started, and "what did the lie earn
me" becomes a number they can feel.

| | |
| --- | --- |
| **Managed** | one trade per eligible villager: *1 diamond → n emeralds*, n from the index |
| **Eligible** | bound villagers with a smith profession: Armorer, Toolsmith, Weaponsmith |
| **Added, not replaced** | the vanilla trade exists on ~5% of Toolsmiths, so it is normally created. Where it exists, it is overwritten |
| **Untouched** | every other trade on every villager, including all the diamond-gear sales |
| **Vanilla adjustments** | demand and reputation set to zero on this trade only, for the determinism reason above |

**The normal price is 8 emeralds, not vanilla's 1.** Vanilla prices a diamond at one emerald,
which is absurd on its face and leaves no room to move — at 1 emerald a 30% panic is
unrepresentable. 8 is what the concept doc says a diamond is worth and what E1–E32 were all
calibrated at. It should be said plainly in the README that Hearsay's diamond trade is not
vanilla's.

**A decision to name rather than bury.** The index is set by every villager standing in the
market, but only smiths will have a counter. Those are different populations. The proposal
keeps them different on purpose: the price is *the village's opinion*, and the smiths are
merely the ones who trade on it. The alternative — only smiths contribute an ask — would
shrink the market back to the handful of villagers E17 to E19 spent five experiments
fighting. If binding finds no smith at all, `/hearsay start` should say so, the way it
already warns about village size.

#### How a player trade becomes an input

Paper fires [`PlayerTradeEvent`](https://jd.papermc.io/paper/1.21.11/io/papermc/paper/event/player/PlayerTradeEvent.html)
when a player trades with a villager, carrying the villager and the `MerchantRecipe` used.
That is the hook.

1. The event fires. The plugin checks the villager is bound and the recipe is the managed one.
2. It schedules `PlayerTraded(nextTick, villagerId, DIAMOND, emeralds, count)` the way
   `/hearsay rumor` schedules a `PlantRumor` today — on the tick that has not happened yet,
   never mid-tick, so inputs always arrive at a tick boundary.
3. The next `step()` turns it into an event, and the villager treats the price they just
   paid as evidence, through the same combining rule `PriceObserved` uses.
4. `RecipeFile` writes it as an input line. Format version 6.

**Trade menus are rebuilt once a tick**, in the same place the displays are, from the
villager's own asking price — not the market price. A villager who believes the lie pays
more than one who does not, which is visible, explicable, and gives the player a reason to
shop around. Rebuilding on a tick rather than continuously also means a menu the player has
open cannot change under their hands mid-trade.

**Determinism.** The player is outside the simulation and their trades are inputs, so a
played session still reproduces from seed + params + inputs. This is the arrangement
`MeetingSource.EXTERNAL` already established for meetings; trades are the second thing to
arrive the same way, and the machinery needs no change.

#### Which experiments re-run

| Experiment | Why |
| --- | --- |
| **None of E1–E32** | Nothing about how villagers gossip, move or price changes. A headless run has no player in it, so every sweep stands |
| `CalibrationTest` | Unchanged, and must stay passing: it is the proof that adding a player did not disturb the village |
| **New: trade evidence** | How much a trade convinces, swept as `observationWeight` was, against the quiet-village rate. **A player who can start a panic by buying twice is a bug** |
| **New: the player's own footprint** | Paired worlds with the trades kept and the lie removed, to check the lie is still separable from the buying. If it is not, the headline claim is in trouble and better found in a sweep than in a session |

**What must not change.** The quiet-village rate, the burst band, and the paired-worlds
separation. A player who trades heavily should be able to move the price — that is the
point — but a village nobody lied to and nobody traded with must behave exactly as it does
today.

---

### "What did the lie earn me"

Once trades are input events, the counterfactual can price the player's own conduct. With
the lie and without it, over the same recorded trades:

```
You bought 14 diamonds and sold 60 bread.

  With the lie          you spent 96 emeralds and took 71
  Without it            you would have spent 112 and taken 58

  The lie earned you    +29 emeralds
```

**The caveat goes in the output, not in a footnote.** The counterfactual holds the player's
trades fixed: it asks what those same purchases would have cost in a village nobody had lied
to. It does **not** ask what the player would have done differently, because a player who
saw different prices would have traded differently, and the model has no way to guess how.
So the wording is *"the same trades, at the prices an honest village would have offered"*,
never *"what you would have made"*.

That distinction is the difference between a real counterfactual and a fantasy, and putting
it in the sentence rather than in small print is the point. A figure that quietly means
something narrower than it sounds is worse than no figure.

**What it needs.** Nothing beyond stage 3: the trades are already inputs, both timelines
already run, and the arithmetic is a sum over `PlayerTraded` events priced against each
timeline's index. It is the cheapest output in this document and probably the most
convincing, because it turns "the price index reached 138" into a number in the player's
pocket.

---

## Stage 4 — something true to be wrong about — *third*

E32 measured what E31 only suggested: the swings grow by about 5% each, and the village
never settles. The cause is structural rather than a setting. **Every piece of evidence a
villager can have is either gossip or the price, and both are made of belief.** A rumour can
only be contradicted by another rumour; a price cannot be wrong, because the price is
whatever the village thinks it should be. A loop with no external reference has nothing to
converge on, so it wanders until something stops it, and nothing does.

The concept doc's **reality checks** are the damping mechanism, and stage 3 is what makes
them possible: once trades are real, the village has a true supply for the first time, and a
belief about scarcity can be **wrong** rather than merely unpopular.

**The shape of it.** A villager who believes diamonds are scarce, and who then sees diamonds
— in a chest they have access to, in a trade another villager is offering, in what the
player hands them — has met evidence that does not come from anybody's opinion. That
evidence moves confidence the way a telling does, through the same combining rule, with one
difference that matters: **it is not a rumour and starts no family.** Nothing can be
exaggerated in the retelling of it, because it was not told.

**What changes in core.**
- A new input, `RealityChecked(tick, villagerId, item, sawHowMany)`, alongside `PlayerTraded`.
  An input rather than a derived event, because the world outside the simulation is what
  produced it.
- Confidence combines as it does today, with the source being the world rather than a
  villager. It belongs in no chain, and so is never a repeat — but for the opposite reason
  the market is never a repeat: the market is everyone's opinion at once, and this is
  nobody's.
- **Asymmetry worth deciding before building.** Seeing plenty of a thing believed scarce
  should weigh more than seeing a little of a thing believed plentiful, because absence is
  weak evidence and presence is strong. That is a parameter and it must be swept, not
  guessed.

**Determinism and replay.** Preserved by the same argument as stage 3. What a villager saw
is an input, recorded in the recipe, so a session replays exactly. It also makes a third
counterfactual available: **what would this village have believed if it had never looked?**

**Old recipes.** Keep loading; they contain no checks. Format version 7.

**Success test, and it is the point of doing this at all.** E32's four numbers are the
baseline. Stage 4 works if, on a village of the same size with the same single lie:

| | E31 baseline | stage 4 must reach |
| --- | --- | --- |
| swing decay | **1.05** | **below 1**, and convincingly — say 0.85 or lower |
| settled within 10% | **never** | **some day before the run ends** |
| biggest swing | 112% | no requirement; a first panic may be as large as it likes |
| bubbles caused by the lie | 5 against 0 | **unchanged** — damping must not cost the claim |

That last row is the trap. A mechanism that damps the oscillation by making villagers hard
to convince would pass the first two rows and destroy the project: the quiet-village
guarantee and the paired-worlds separation must hold exactly as they do now. **Damping the
swing is not the same as muting the village**, and only running both sets of measures
together can tell them apart.

**Experiments to re-run.** All of the calibration, because this changes how belief moves.
`CalibrationTest`'s band, the quiet-village rate, and E29's gossip share would all need
re-deriving on a village that can now be contradicted by the world.

## What this does to the project's claim

Today the counterfactual answers one question: what would this village have done if nobody
had lied? After stage 3 it answers a harder one: what would it have done if nobody had lied
**and I had not traded**. Those are three timelines rather than two, and the paired-worlds
machinery already runs many worlds, so it can carry them.

That is the argument for doing stage 3 at all. Stages 1 and 2 make the world richer; stage 3
makes the question sharper, and a sharper question is worth more than a bigger world.

## Order of work

1. ~~Stage 1, which is small and makes the existing measurements honest.~~ **Built.**
2. Play sessions in a real marketplace and re-measure the in-game figures.
3. Stage 2's indices, fitted before any emerald mapping is chosen.
4. Stage 3, with the trade-evidence sweep done before it is enabled by default.
5. Stage 4, measured against E32's baseline, with the paired-worlds separation checked at
   every step so damping never quietly becomes muting.
