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
| 2nd | **3 — real trades, diamonds only** *(built, E33)* | Depth before breadth. One good, taken all the way to the player's hands |
| 3rd | **measure the selling** *(done, E34)* | A played session and a headless sweep |
| 4th | ~~**4 — reality checks**~~ *(built, turned off, E37)* | There was nothing to damp. Not a prerequisite for anything |
| 5th | **2 — more goods** | **Next.** No longer waiting on stage 4 |

> **The gate that stood in front of stage 2 has been met by the baseline itself.** E37
> measured decay after the peak at **0.91**, settling in 80% of runs, at a mean price of
> 100.8, with the lie's bubbles intact at 38% against 0% for quiet villages (E38). Stage 4
> was to have achieved that and the model was already doing it. **More goods is next.**

**Nothing starts until the step before it is done and measured.** Each of these has a test
it must pass, written down before it is built, and a stage that fails its test is not
followed by the next one — it is followed by the next attempt at itself.

| Step | Done when |
| --- | --- |
| measure the selling | a session recorded at a busy smith and at an isolated one, plus a headless sweep of both weights, reported as an E-number. **Does witnessed selling alone bring decay below 1 while the lie still causes its bubbles against none without it?** The no-lie timeline's price effect is reported separately, so the lie is never credited with the player's own selling |
| stage 4 | **decay below 1, settles before the run ends, and lie-caused bubbles unchanged.** If it fails, the village purse is the next damper and is measured on its own |
| stage 2 | stage 4 passed, on its own terms, first |

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

## Stage 2 — more than one thing to be wrong about — **NEXT**

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

## Stage 3 — trades that actually happen — **BUILT** (E33)

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
| **Level** | **Novice.** See below |
| **Vanilla's Expert toolsmith trade** | **replaced, not joined.** See below |
| **Untouched** | every other trade on every villager, including all the diamond-gear sales |
| **Vanilla adjustments** | demand and reputation set to zero on this trade only, for the determinism reason above |
| **Uses** | vanilla's `maxUses` and restocking, unchanged. See below |

#### Professions and levels

**The three smiths, and no one else.** Armorer, Toolsmith and Weaponsmith are the professions
that plausibly want diamonds, and they are the three vanilla already associates with them. A
librarian buying diamonds would need explaining; a weaponsmith doing it needs none. Villagers
with no profession cannot trade at all and are simply not eligible — they still contribute an
ask to the index, because having an opinion about a price requires no counter.

**Offered at Novice, which is a deliberate break from vanilla.** Vanilla puts its diamond
purchase at Expert, the fourth of five levels, which takes a great deal of trading to reach.
Gating Hearsay's offer the same way would mean most sessions had no tradeable villager at all,
and a research instrument whose measurements depend on first levelling up a villager is not
an instrument. Novice makes it available the moment a village is bound.

The cost is honest and should be in the README beside the price: **Hearsay's diamond trade is
not vanilla's, neither in what it pays nor in when it appears.** The alternative — Apprentice,
say, as a compromise — buys a little fidelity for a lot of friction in every session, and
fidelity to vanilla was never what this project was for.

**Replacing vanilla's Expert trade rather than sitting beside it.** Two trades buying the
same diamond at different prices would let the player take whichever is better, and that is
not a game-balance worry but a measurement one:

- The player's actual receipts would come from a mixture of a Hearsay price and a fixed
  vanilla price, so **"what did the lie earn me" could no longer be attributed cleanly**.
- Vanilla's fixed 1 emerald would sit under Hearsay's price as a **floor**. It only bites
  below an index of about 12, which E31 never reached — but a floor that has not bitten yet
  is still a floor, and a bust measurement with an invisible bottom is worse than no
  measurement.

So where the vanilla trade exists it is overwritten, and where it does not it is created. One
diamond-buying trade per villager, one price, one thing to attribute.

#### Caps on what the village will pay

**At minimum, vanilla's own limits, unchanged.** A `MerchantRecipe` has `maxUses`, and
villagers restock at their workstation twice a day. That is already a cap: a panicking village
will pay its inflated price a fixed number of times and then stop until it restocks. It costs
nothing to adopt, it is behaviour players already understand, and it stops the obvious
exploit of standing at one villager and selling a stack of diamonds into a single panic.

**A finite village purse — a pool of emeralds the village can pay out, which empties — belongs
in stage 4, not here.** The reason is not that it is a bad idea. It is that **a purse is
itself a damping mechanism**: as it empties, payouts fall, the price falls, and the swing
shrinks. Introducing it in stage 3 would put a second damper in the model at the exact moment
stage 4 sets out to measure whether reality checks damp anything. The result would be
uninterpretable — decay below 1, and no way to say which mechanism did it.

So: vanilla limits in stage 3, and the purse held back as a **stage 4 candidate to be
measured on its own**, after reality checks have been given their chance. If reality checks
bring decay under 1 by themselves, the purse may not be wanted at all. If they do not, it is
the next thing to try, and it will be testable precisely because it was kept out of the way.

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

#### Selling into the panic, and what the neighbours make of it

A player who sells diamonds is **doing something in public**, and what they are doing is
producing diamonds in a village that believes there are none. Villagers who witness it have
seen evidence — not of a price, but of the world — and it points the other way: **abundance**.

This is the mechanic that lets a bubble be punctured by the person who started it. Talk the
village into a panic, sell into it, and the selling itself is the counter-evidence that ends
the panic. Whether that is satisfying or merely fiddly is a question for playing it; whether
it is *right* is not in doubt, because the alternative is a village that watches a player
empty a shulker box of diamonds onto the counter and goes on believing there are none.

**How it works, reusing what is already there.** E25 built witnessed provenance: a telling is
one event heard by everyone standing there, and each listener's chain records who was present.
A public sale is the same shape. The villagers within the talking range of the trade witness
it, and each takes it as evidence of `ABUNDANT` — weighted by how many diamonds changed
hands, since one diamond is a curiosity and a stack is a glut.

It belongs in no chain and starts no rumour family, for the same reason a reality check will
not: **it was not told to anyone.** Nobody can exaggerate it in the retelling, because there
was no retelling.

**This is stage 4 arriving early, and that is an argument for it rather than against.** A sale
witnessed is a reality check with a very narrow aperture — the one piece of true supply the
player can produce on demand. Building it in stage 3 means the damping idea gets tested
cheaply, on one mechanism, before stage 4 commits to the general case. If witnessed selling
alone moves E32's decay figure, that is the strongest possible evidence that reality checks
are the right answer. If it does not, stage 4 starts with something important already known.

**Two weights, swept together.** How much a trade convinces the villager who made it, and how
much a witnessed sale convinces the ones who merely saw it. They are different questions —
one is about a price you paid, the other about goods you saw — and they interact, so they are
swept as a pair rather than one after the other. Both against the quiet-village guarantee.

**The index becomes a price, and gets its name.** Until now the 100-is-normal number has had
no unit, so the player is shown only the change and the word for it — "138" is 138 of
nothing, and calling it a price would imply emeralds it did not mean. Once a diamond has an
emerald figure on a counter, the number in the world **is** emeralds, and the boss bar says
`Diamonds  ▲ +38%  alarmed · 11 emeralds`. The 100-based scale stays as the internal unit
and keeps the name index in the dashboard and the experiments, where it is read by somebody
comparing runs rather than somebody buying something.

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
| **New: trade evidence** | How much a trade convinces the villager who made it, swept as `observationWeight` was, against the quiet-village rate. **A player who can start a panic by buying twice is a bug** |
| **New: witnessed selling** | How much a public sale convinces the villagers who saw it. Swept **as a pair** with the above, since one is about a price paid and the other about goods seen, and they interact |
| **New: does selling puncture a bubble?** | The oscillation measures from E32, on a session where the player sells into the peak. This is stage 4's question asked early, with one mechanism instead of the general case |
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

## Stage 4 — something true to be wrong about — **BUILT and turned off** (E35, E37)

> **E37 withdrew the reason for this stage.** Measured from the largest swing rather than
> from the start of the run, the village already settles: swings shrink by 9% each, four in
> five settle within 10% of normal, and the mean price over the last quarter is 100.8.
> E32's "never settles" was the build-up being averaged in with the aftermath. There was
> nothing to damp.
>
> The mechanism is built, tested and inert at `checkWeight` 0. What it might still be good
> for is **making a lie refutable** — a village that can see thirty-two diamonds disbelieving
> a rumour that they are gone — which the sweep shows working. That is a feature about truth
> and wants justifying on its own terms, not inherited from a mismeasurement.

E32 measured that the swings grow by about 5% each and the village never settles. E33 and
E34 then established *why* the obvious fix does not work, which is what this design is built
on.

### What E34 proved, and it changes the mechanism

A player selling into a panic moved the peak by six points in the right direction and moved
the decay from 1.72 to 1.64 — real, and nowhere near 1. The reason is one figure: across ten
sales, **the biggest single jump in anyone's belief in plenty was 0.004.** Four thousandths.

The fault is not the weight. Turning it up is what E33 swept, and past a witness weight of
0.14 a village nobody lied to starts bursting — the player becomes able to start a panic
without telling a lie, which is worse than the problem.

**The fault is the shape of the rule.** Every piece of evidence in this model *adds*:

```
new = 1 − (1 − old) × (1 − weight)
```

That only ever pushes confidence upward, and decay only ever pulls it down. A price made of
nothing but shoves and fading has no level to come to rest at, which is exactly what "decay
above 1, never settles" is describing. **A sale is an event. Events shove. Nothing converges
under shoving.**

### The mechanism: belief is pulled toward what can be seen

A reality check is not another shove. It is an **anchor**, and it works by attraction:

```
new = old + checkWeight × (whatTheStockImplies − old)
```

Confidence moves a *fraction of the way toward* the truth rather than a step away from where
it was. That is a contraction: repeated checks converge on the implied value instead of
wandering, and the distance from it shrinks geometrically. **This is the only rule in the
model that can bring a swing down, because it is the only one that knows where down is.**

A villager who believes diamonds are gone and can see two hundred of them does not merely
gain some belief in plenty — their belief in scarcity is *drawn toward* what is in front of
them. A villager who believes it and sees an empty village stays where they are.

### What the villager is actually looking at

**The village's visible stock**: diamonds in containers inside the marked market region,
counted by the plugin. A standing fact, still there tomorrow, unlike a sale.

| | |
| --- | --- |
| **Who checks** | villagers standing in the market region, which stage 1 already defines |
| **How often** | once a day, at `DayEnded`, beside the fading that already happens then |
| **What they see** | how many diamonds are in reach, as a count |
| **What it implies** | plenty above a threshold, scarcity below it, in proportion between |
| **New input** | `RealityChecked(tick, villagerId, item, sawHowMany)` |

A player who wants to calm a village stocks a chest by the market and leaves it there. A
player who wants to start a panic empties one. **Both are things you do to the world rather
than things you say**, which is the distinction the project has been missing.

### What changes in core

- `RealityChecked` as an input, recorded in the recipe, so replay is exact and a third
  counterfactual becomes available: *what would they have believed if they had never looked?*
- One new rule in `Simulation`, the attracting one above. It does not replace the evidence
  rule; tellings still shove, and that is right, because a rumour is not a measurement.
- Two parameters: `checkWeight` — how far toward the truth one look moves you — and how much
  stock reads as plenty. Both swept before adoption, against the quiet-village guarantee.
- **Asymmetry to decide by sweep, not by guess.** Seeing plenty where scarcity was believed
  should probably weigh more than seeing little where plenty was believed, because absence
  is weak evidence and presence is strong. That is a parameter and it must be measured.

### Determinism, old recipes, experiments

Replay is preserved by the same argument as trades: what a villager saw is an input, written
into the recipe. Old recipes keep loading and contain no checks; format version 7.

**All of the calibration re-runs**, because this changes how belief moves: the burst band,
the quiet-village rate, E29's gossip share. That is the cost of the only mechanism that can
damp anything, and it is why it is worth doing once, properly, rather than tuning around.

### The test it has to pass

E32's baseline, on a village of the same size with the same single lie:

| | baseline | stage 4 must reach |
| --- | --- | --- |
| swing decay | **1.05** (E32), 1.64 with selling (E34) | **below 1**, convincingly — 0.85 or lower |
| settles within 10% | **never** | **before the run ends** |
| lie-caused bubbles | **5 against 0** | **unchanged** |
| quiet villages bursting | 0.33% | **unchanged** |

The last two rows are the trap. A mechanism that damps by making villagers hard to convince
would pass the first two and destroy the project. **Damping the swing is not muting the
village**, and only running the oscillation measures beside the paired-worlds separation can
tell them apart.

**If it fails**, the village purse is the next damper — a finite pool of emeralds that empties
as the village buys — held back from stage 3 precisely so it could be measured on its own
rather than confounding this.

## What this does to the project's claim

Today the counterfactual answers one question: what would this village have done if nobody
had lied? After stage 3 it answers a harder one: what would it have done if nobody had lied
**and I had not traded**. Those are three timelines rather than two, and the paired-worlds
machinery already runs many worlds, so it can carry them.

That is the argument for doing stage 3 at all. Stages 1 and 2 make the world richer; stage 3
makes the question sharper, and a sharper question is worth more than a bigger world.

## Between stages: personality labels — **BUILT**

A readable name for what a villager is like, from the three traits they already have. Purely
a reading of `Traits`; it decides nothing and is worth no experiment.

**The first proposal was wrong, and the arithmetic said so.** It gave a label to anybody past
a fixed threshold of 0.2 or 0.8. With two tails on each of three traits that labels
1 − 0.6³ ≈ 78% of a village, and measurement on the calibration seeds confirmed exactly 78%:
a median of sixteen villagers in twenty carrying a name, which is a name on nobody.

**Labels are relative and rationed instead.** Each name belongs to the single most extreme
villager in *that* village for *that* trait, and a village supports only about one name per
five people:

| | |
| --- | --- |
| **How many** | `max(1, villagers / 5)` — four in a village of twenty, one in a hamlet |
| **First** | the Town Crier, always, out of turn. Whoever talks most is who the player needs |
| **Then** | the most extreme remaining traits, ranked by distance from 0.5 |
| **Floor** | 0.70 high or 0.30 low, so a village of mild people is left unnamed |
| **Never** | two villagers with one name, or one villager with two |

The names are the Town Crier and the Quiet One for gossip, the Worrier and the Sceptic for
credulity, the Hoarder and the Haggler for greed.

**Why gossip goes first**: who *spreads* a rumour matters more than who believes it. E8 put
the planter at about a third of the variance, and E27 showed a single well-aimed lie beating
four scattered ones. **Why nothing is named for what it believes**: beliefs change every
day, and a name that changed with them would be useless for the one thing a player needs it
for, which is choosing whom to tell.

**A measurement worth keeping.** The floor barely does anything at twenty villagers — the
maximum of twenty uniform draws averages about 0.95, so it clears 0.70, 0.80 and 0.85
alike, and removing it entirely changes nothing. It earns its keep only in small villages,
and the rationing is what does the real work. That is worth knowing, because it means
raising the floor is not a lever on how many names a village carries; the divisor is.

**Where it shows.** Beside the percentage in `/hearsay who`, and on the second line of the
villager's name plate. Not replacing the gossip figure, which is the number the player acts
on — the label is what makes the number memorable.

## The rest of v2, not yet scheduled

Everything below is in the concept doc and none of it has a date. It is listed so the shape
of the whole is visible, and so that nothing here is mistaken for something that was
forgotten. **None of it is started until the four stages above are done**, because each of
them would add a mechanism to a model that is still oscillating, and a model that cannot
settle is not one to build more onto.

| | What it is | Why it waits |
| --- | --- | --- |
| **Player credibility** | the village learns whether *you* are worth believing, and a player caught lying is trusted less next time | needs a history of the player's claims against what turned out to be true, which needs stage 4's truth |
| **Professions** | a farmer and a librarian believe different things about different goods, and weight each other accordingly | needs more than one good, so it waits on stage 2 |
| **Relationships and trust** | who a villager believes depends on who they like, not only on who is in their chain | the largest of these, and it changes every number in EXPERIMENTS.md |
| **Households** | families who share a home share what they hear, ahead of the village | overlaps the neighbourhoods from E23 and may replace them |
| **Claims about people** | rumours about villagers rather than goods: who is a thief, who is generous | the belief machinery carries it already; what it lacks is anything for such a claim to *do* |
| **Corrections** | a villager who learns they were wrong tells the people they told | the natural partner to reality checks, and worth nothing before them |
| **Notice board** | a place the player can post a claim to the whole village at once | trivial to build and easy to abuse; it wants credibility first, or it is a panic button |

## Order of work

1. ~~Stage 1, which is small and makes the existing measurements honest.~~ **Built.**
2. Play sessions in a real marketplace and re-measure the in-game figures.
3. Stage 2's indices, fitted before any emerald mapping is chosen.
4. Stage 3, with the trade-evidence sweep done before it is enabled by default.
5. Stage 4, measured against E32's baseline, with the paired-worlds separation checked at
   every step so damping never quietly becomes muting.
