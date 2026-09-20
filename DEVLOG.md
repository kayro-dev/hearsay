# Devlog

Notes on building Hearsay — what I chose, why, and what I got wrong.

---

## Week 6 — counterfactuals (2026-09-20)

### What changed

Two ways of asking what the lie did.

**A single rerun** answers "what would this village have done without the lie":
`Run.without(input)` re-runs from the same seed and params with one input removed. Because
the streams are separate, everyone walks the same routes in both timelines.

**Paired worlds** answer "how likely was the lie to cause this". The village runs to the
tick before the lie, then carries on many times under different futures. The worlds are
paired: world 7 with the lie and world 7 without it get the same branch seed, so they face
identical future randomness and the only difference inside a pair is the lie. Branch seeds
come from `Seeds.branch(seed, index)`, so naming the seed and the number of pairs reproduces
exactly those worlds. The technique is common random numbers.

`Simulation.resume(state, branchSeed, params, inputs)` carries a world on from a given
state; `Simulation.fork(branchSeed, inputs)` does the same from a live one. `PairedWorlds`
replays the shared history once per world so no two worlds share a state object.

`Comparison` reads two logs and reports the divergence tick, both timelines day by day, peak
prices, days elevated, and the extra cost of buying one diamond each market day.

Two CLI commands, `counterfactual` and `worlds`, which state in words what kind of claim
each figure is: a single run says "in this simulated village", many worlds say "in X of N
paired worlds".

New: `Comparison`, `PairedWorlds`, `Seeds`, `Bubble`, `MarketNoiseSet`, and the
`hearsay.experiments.ManyWorlds` runner. `RandomStream` now derives its seeds through
`Seeds`. Tests went from 70 to 89.

### The resume test found state outside WorldState

The market's wobble was a field on `Simulation`. It was recorded nowhere, so a world rebuilt
from its log started from a calm market it never had. It is now recorded every tick as
`MarketNoiseSet` and restored by `apply`.

Recording it every tick rather than folding it onto `MarketPriceSet` keeps the process
per-tick: the market only opens with a quorum, so a replayed state would otherwise lag
between openings, and advancing the noise only on market days would change the process the
calibration was fitted to. The log grew about 3%; `CalibrationTest` did not move.

The test took two corrections before it could fail:

1. The edit adding the state parameter to the constructor did not apply, so `resume` ignored
   the state it was given. The test passed anyway, because both continuations started from
   the same empty world.
2. Once that was fixed, both continuations went through `resume`, which reset the wobble to
   zero in both. The missing state was invisible because it was missing equally. The test
   only fails when a `fork` carrying everything the simulation knows is compared against a
   `resume` rebuilding from the log alone.

### Then the reliance on that was removed

`Simulation` no longer keeps a tick counter; the tick is read from the world, which always
holds the last tick that happened, since every tick records at least one event. That leaves
the recipe, the four generators, the world and the log, all final, so `fork` is a plain copy
and nothing has to be carried across by hand.

`SimulationStateTest` holds that in place: it asserts the exact set of instance fields and
that every one of them is final. Adding a field makes it fail, which forces the question of
whether the field belongs in `WorldState`.

### One definition of a bubble

A bubble is a run whose price went above 130 and later came back under 110. The thresholds
had been written out separately in five places. They now live in `Bubble` and every command,
experiment and test reads them from there. Two neighbouring measures were renamed to keep
them apart: **elevated** is a price above 120 with no claim about coming back, and **peak
price** says how far it went with no claim about coming back. The experiments runner
`Bubble` was renamed `BubbleSweep` to free the name.

### Numbers

E6, over the hundred villages `CalibrationTest` uses, 10 pairs each, 220 ticks, the lie told
on tick 41:

| measure | value |
| --- | --- |
| pairs run | 1000 |
| bubbled with the lie | 919 (91.9%) |
| bubbled without it | 0 (0.0%) |
| the lie made the difference in | 919 (91.9%) |
| mean peak price effect | +51.8 |
| mean extra cost of a diamond a day | +782.5 |
| per-village share the lie caused | p10 70%, median 100%, p90 100% |

Four deliberate breakages, to check the new tests can fail:

| Mutation | Caught by |
| --- | --- |
| Every world gets the same branch seed | `differentPairsTellDifferentStories` |
| The two worlds in a pair get different branch seeds | `insideAPairEveryoneWalksTheSameRoutesInBothWorlds` |
| `apply` does not restore the wobble from the log | `theWobbleCarriesOverInsteadOfBeingDrawnFresh` |
| A mutable field is added to `Simulation` | both `SimulationStateTest` cases |

The second was not caught at first. The divergence-tick test could not see it: with the lie
told on the first tick of the continuation, unpaired worlds diverge at that same tick anyway.
The property that distinguishes them is that both worlds in a pair walk identical routes for
the whole run, not only up to the lie. A test for that was added and the mutation re-run.

Separately, the CLI's per-pair column and its own summary disagreed: the column tested
whether the price peaked above 130, the summary tested the full bubble. Both now go through
`Pair.lieMadeTheDifference`.

**Next:** the dashboard, reading the CSVs the CLI writes.

---

## Week 5 — prices, and the loop that closes (2026-09-20)

### What changed

The random-walk `PriceChanged` scaffolding is gone. Prices are decisions the simulation
makes and records.

Each villager has an asking price driven by belief:
`ask = basePrice × (1 + priceSensitivity × scarcityBelief)`, where `scarcityBelief` nets
scarce against abundant, weights confidence by severity at 1x, 1.5x and 2x, and is clamped
to the range -1 to 1. The market price is the median ask of the villagers at `MARKET`,
needing a quorum of 5, nudged by noise, recorded as `MarketPriceSet`.

Villagers at the market read the price and take it as evidence, recorded as
`PriceObserved`. Evidence is the **move since the price that villager last drew a
conclusion from**, not the level: a steady price is no evidence however high it is. A
villager who has concluded nothing measures against base, so a first move still registers.
Weight is `observationWeight × min(1, |move| / fullMoveSize)`, combined through the same
rule as rumors.

Market noise carries over: `noise(t) = noiseDecay × noise(t-1) + step`.

Belief can now start without anybody speaking, so rumors gained a `RumorOrigin`: a family
is `PLANTED` or `OBSERVED`, and the invariant became that every belief traces to one or the
other. An observation-born rumor is told and exaggerated like any other.

New: `MarketPriceSet`, `PriceObserved`, `RumorOrigin`, `MarketStats`, and `Params` gained
`basePrice`, `priceSensitivity`, `observationWeight`, `observationThreshold`,
`fullMoveSize`, `marketNoise`, `noiseDecay`, `marketQuorum`. `Params` gained withers, since
twelve positional fields make every call site fragile. `WorldState.gossipiestVillager()`
replaced three copies of the same helper.

Two experiment runners: `:experiments:bubble` runs each setting three ways over the same
seeds (rumor with feedback, the same rumor without it, a quiet village), and
`:experiments:noise` tunes the wobble on quiet villages alone.

Tests went from 63 to 70.

### Numbers

Defaults adopted from E5: `priceSensitivity` 0.75, `observationWeight` 0.25,
`fullMoveSize` 0.20, `marketNoise` 0.030, `noiseDecay` 0.86, `marketQuorum` 5.

The loop's effect, holding everything else fixed and switching only the feedback on, at
sensitivity 0.75 over 50 seeds:

| | mean peak price | burst rate | fall, days |
| --- | --- | --- | --- |
| rumor, no feedback | 139.0 | 66% | 10.5 |
| rumor + feedback | 162.9 | 88% | 12.9 |

On seeds 1001-1100, which no sweep had touched:

| condition | half believing | burst | quiet holders |
| --- | --- | --- | --- |
| rumor + feedback | 65% | 94% | - |
| quiet village | - | 0% | 5% |

Three stages the numbers went through, each recorded in EXPERIMENTS.md:

| stage | half believing, with a rumor | bursts | quiet villages |
| --- | --- | --- | --- |
| E3, evidence from the price level | 90% | never deflated | 0%, structurally impossible |
| E4, evidence from the move | 18% | 92% | 0%, structurally impossible |
| E5, move scaled by `fullMoveSize` | 72% (65% out of sample) | 88% | 5% hold a belief, 0% burst |

E3's bubbles could not deflate. A villager observing a saturated price once a day settles
at `d·w / (1 - d(1-w))`, which for decay 0.92 and weight 0.15 is 0.63, above the 0.5
believing threshold, so belief locked in and held the price up. E4 fixed that by measuring
moves rather than levels, which also shrank every observation's weight by about eight times,
because `min(1, |move|)` had been calibrated against levels. E5's `fullMoveSize` separated
the two: the threshold decides what is noticed, `fullMoveSize` decides what a noticed move
is worth.

Noise tuning, 300 quiet villages per cell: the panic rate is far more sensitive to how long
the wobble carries than to how big each step is. Holding the step at 0.030 and moving the
carry from 0.82 to 0.88 takes the rate from 0.7% to 15.7%. Chosen: 0.030 at 0.86, giving
5.7%.

Seven deliberate breakages, to check the new tests can fail:

| Mutation | Caught by |
| --- | --- |
| The whole village reads the price, not just those at the market | `onlyVillagersStandingAtTheMarketReadThePrice` |
| The market opens with one seller | `theMarketOnlyOpensWithAQuorum` |
| Scarcity lowers the ask | `moreScarcityBeliefAlwaysMeansAHigherAsk`, `anAbundantBeliefPullsTheAskBelowBase` |
| Noise ignores its parameter | `withoutRumorsOrNoiseThePriceNeverLeavesBase` |
| Evidence read from the level, not the move | `aSteadyPriceIsNoEvidenceHoweverHighItIs` |
| A falling price still means scarcity | `aRisingPriceMeansScarcityAndAFallingOneMeansPlenty` |
| Noise drawn fresh instead of carried over | `theWobbleCarriesOverInsteadOfBeingDrawnFresh` |

The last one was not caught at first: the carry-over had no test at all. One was added and
the mutation re-run to confirm it fails.

`CalibrationTest` runs seeds 1001-1100 on every push and fails if the with-rumor
half-believing rate leaves 50-85% or any quiet village bursts. It takes about two seconds.
Verified against three mutations: `observationWeight` at 0.05 trips the lower bound, at 0.50
the upper, and noise at 0.080 carried at 0.95 trips both tests.

### Choices made along the way

- `scarcityBelief` is clamped, so half sure of the worst version and certain of the mildest
  saturate at the same ask.
- Observation weight is not subject to `repeatWeight`; the design gives `w` explicitly and
  it has no source term.
- Noise state lives in `Simulation`, not `WorldState`: it is part of the random process,
  and no event carries it.
- A villager who forgets a claim entirely loses their price anchor with it.
- `RumorStats` counts one rumor family; `MarketStats` counts a claim however it was reached,
  because a run with no rumor planted has no family to count and would read zero by
  construction.

**Next:** counterfactual replay — what the price would have been without the lie.

---

## Week 4, part 2 — measuring belief (2026-09-20)

### What changed

`plantedConfidence` moved onto the `RumorPlanted` event. `WorldState` now consults no
`Params` at all, and `Simulation.replay(log)` takes only the events: a log rebuilds the
same world whatever the knobs are set to today.

`RumorStats` separates **heard** (holds the claim at any strength) from **believes**
(confidence at or above 0.5). The threshold lives in `RumorStats`, not `Params`, because
it changes nothing about the run. Added cumulative `everHeard`, the severity spread per
day, and `ticksUntilHalfHeard` / `ticksUntilHalfBelieves` in place of one combined figure.
The reproduction number is defined on heard, not believes.

`Belief` gained `chain`, a sorted set of every villager the belief passed through, with
the holder excluded. A telling whose claim already came through the listener is an echo
and changes nothing. A belief now keeps the most severe version its holder has heard,
recorded on `RumorTold` as `keptRumorId` next to `toldRumorId`. `sourceId` continues to
mean "who last told me".

The CLI picks the gossipiest villager as the default planter, found by running one tick
with no inputs. Traits come from the movement stream, which inputs never touch, so the
probe sees the same village the real run gets.

Tests went from 33 to 42, with `RumorStatsTest` added.

### Numbers

Seed 42, planting in the gossipiest villager, 20 days:

```
day  2   4 heard /  3 believe   severity 4x1
day  6   6 heard /  1 believe   severity 5x1 1x2
day  7   8 heard /  0 believe   severity 6x1 2x2
day 20   9 heard /  0 believe   severity 7x1 2x2
ever heard 16/20   peak believes 3/20
half heard tick 42   half believes never reached
```

Across seven seeds, `half believes` was never reached on any of them, and peak believers
ran 1 to 5 out of 20.

Before and after the echo and severity rules, 200 ticks, planting in the gossipiest
villager:

| seed | ever heard | peak believes | days with a believer | echoes | severity kept |
| --- | --- | --- | --- | --- | --- |
| 42 | 16 → 16 | 3 → 3 | 6 → 6 | 0 | 0 |
| 7 | 19 → 16 | 5 → 5 | 7 → 6 | 2 | 0 |
| 1 | 17 → 17 | 2 → 2 | 6 → 6 | 1 | 0 |
| 99 | 15 → 14 | 2 → 2 | 6 → 6 | 3 | 0 |
| 2024 | 18 → 19 | 3 → 3 | 6 → 6 | 4 | 3 |
| 3 | 19 → 18 | 1 → 1 | 7 → 6 | 1 | 0 |
| 11 | 20 → 20 | 5 → 5 | 16 → 9 | 14 | 9 |

Peak believers is unchanged on every seed. Echoes number 0 to 14 per 200-tick run. Seed 11
is the outlier: 14 echoes had been sustaining a believer for seven extra days.

Confidence on first hearing by distance from the planted source, seven seeds:

| hop | mean confidence | n |
| --- | --- | --- |
| 1 | 0.348 | 68 |
| 2 | 0.227 | 49 |
| 3 | 0.184 | 11 |

It does not reach 0.5 even at hop 1, before any decay.

All tellings, seven seeds, under the formula in force at the time:

| kind | tellings | mean confidence gain |
| --- | --- | --- |
| first hearing | 128 | 0.288 |
| new source, claim already held | 131 | 0.073 |
| repeat from the same source | 62 | 0.068 |

Distinct sources a villager ever hears a claim from: 56 villagers heard from one source,
27 from two, 19 from three, 12 from four, 7 from five, 1 from six.

Two deliberate breakages, to check the new tests can fail:

| Mutation | Caught by |
| --- | --- |
| Ignore the chain, so echoes convince | `hearingItBackFromSomeoneItPassedThroughConvincesNobody` |
| Always keep the version just told | `aBeliefNeverWalksBackToAMilderVersionOfTheClaim` |

Both tests assert their fixture actually contains an echo and a milder telling, so neither
can pass by finding nothing to check.

### Choices made along the way

- Echo detection requires the listener to still hold the claim. A villager who forgot it
  entirely loses the history with it, and hearing it again is new.
- A tie on severity goes to what was just said.
- `RumorStats` attributes a telling to the family of `keptRumorId`, not `toldRumorId`,
  since that is what a later snapshot finds in the listener's head.
- Echo tellings are still recorded in the log with the confidence unchanged.

**Next:** one evidence-combining rule in place of the first-hearing and repeat branches.

---

## Week 4 — rumors (2026-09-20)

### What changed

Rumors are their own records, separate from beliefs. A `Rumor` has a claim, a severity of
1 to 3, and a `parentId` linking it to the rumor it grew from, so rumors form a family
tree. Ids come from a counter in `WorldState`, never from randomness. A `Belief` gained a
`rumorId`, so any belief traces to the exact version of the rumor it came from.

**Input events vs derived events.** `RumorPlanted` is the first event that comes from
outside the simulation; moves, meetings, tellings and mutations are decided by it. Inputs
are scheduled as `Input` values (`PlantRumor`) rather than events: an input is a request
with no id, and the simulation turns it into an event when the tick arrives.
`Event.isInput()` separates the two in a log.

**Separate random streams.** `RandomStream` derives one `Random` per subsystem — movement,
gossip, mutation, price — from the run seed, using a SplitMix64 mix rather than adding
small offsets.

Telling happens at meetings: both villagers get a turn, lower id first, each offering
their strongest belief above the 0.3 threshold with probability `gossip × confidence`.
Listeners update by `credulity × tellerConfidence` on first hearing, and close half the
remaining gap on a repeat; believing the opposite claim halves the effect. Beliefs fade
daily through one `DayEnded` event. A telling has a 5% chance of growing the rumor by one
severity.

New types: `Params` (all tuning knobs), `Rumor`, `Input`, `PlantRumor`, `RandomStream`,
`Run`, `RumorStats`, and events `RumorPlanted`, `RumorMutated`, `RumorTold`, `DayEnded`.
`Narrator` now keeps its own `WorldState` and applies events to it, so the "before" figure
in `0% → 42%` comes from the same `apply()` the simulation uses.

Tests went from 15 to 33: `SimulationTest` 5, `VillageTest` 7, `NarratorTest` 8,
`RumorTest` 8, `RunTest` 5.

### Four follow-up changes

- `DayEnded` now carries `decay` and `forgetThreshold` as fields, read by `apply()`
  instead of the run's `Params`.
- A rumor already at severity 3 no longer mutates. Previously it produced a child
  identical to its parent. The mutation roll is skipped entirely rather than rolled and
  discarded.
- `Run` bundles seed, params, inputs, tick count and log in one record. `Run.rerun()`
  decides the whole run again from the recipe; `Run.without(input)` drops one input, which
  is the starting point for week 6's counterfactual.
- The CLI takes an optional third argument: which villager to plant the rumor in.

### Numbers

Planting in each villager in turn, seed 42, 200 ticks:

| planted in | gossip | tellings | peak believers |
| --- | --- | --- | --- |
| 0 (Mira) | 0.05 | 0 | 1 |
| 9 | 0.14 | 1 | 2 |
| 15 | 0.10 | 0 | 1 |
| 17 | 0.07 | 0 | 1 |
| 18 (Pim) | 0.93 | 30 | 11 |
| 10 | 0.97 | 22 | 10 |
| 1 | 0.65 | 26 | 12 |

Three of twenty villagers never pass the rumor on at all: their confidence decays below
the 0.3 telling threshold before they mention it. Villager 0, the design's default
planter, is one of them on seed 42. Across seeds 1-30 planting in villager 0, the rumor
was told at least once in 28.

Planted in villager 18, seed 42: 30 tellings, 3 rumors, peak 11/20 believers, half the
village at tick 23, reproduction number 1.24. Beliefs have faded from everyone by tick
200.

Four deliberate breakages, to check the new tests can fail:

| Mutation | Caught by |
| --- | --- |
| Point gossip and mutation at the movement generator | the stream separation test, and nothing else |
| Tell someone who was not at the meeting | `rumorsOnlyTravelThroughMeetings` |
| A grown rumor records `NO_PARENT` | `apply()` throws on the missing parent |
| `apply()` silently stores a grown rumor as planted | `everyRumorLeadsBackToAPlantedOne`, after it was strengthened |

The fourth mutation was not caught by the original lineage test, which only checked that
every chain ended somewhere planted. It now cross-checks the log: planted rumors in state
must equal `RumorPlanted` events, and each `RumorMutated` event's parent must match what
was stored.

### Which params still leak into replay

Replaying one log under retuned params, checking whether the rebuilt state is unchanged.
Run of 20 ticks, so beliefs are still alive at the end:

| knob | replay unchanged |
| --- | --- |
| tellThreshold, repeatFactor, contradictionFactor, mutationChance | yes (they only affect deciding) |
| dailyDecay 0.9 → 0.2 | yes, since the move onto `DayEnded` |
| forgetThreshold 0.05 → 0.4 | yes, since the move onto `DayEnded` |
| plantedConfidence 1.0 → 0.5 | **no** |

`plantedConfidence` is still read from `Params` inside `apply(RumorPlanted)`, so it is the
one knob whose retuning changes what an old log replays to. Moving it onto the
`RumorPlanted` event would close it and leave `WorldState` needing no params at all.

### Deviations from the week 4 design

- Inputs are `Input` values, not events. Ids come from the state counter, so an input
  carrying one could collide with an id the simulation assigns to a mutation.
- `Params` gained `plantedConfidence`; the design did not say how sure a villager is when
  a rumor is planted in them.
- `WorldState` takes `Params` and `Simulation.replay` requires them.
- `Narrator` keeps a mirror `WorldState`, and defers the mutation line until after the
  telling it happened during, to match the design's example output.
- `Narrator.of` vs `Narrator.withMeetings`: meetings are not narrated by default.
- `ClaimType.ABUNDANT` severity words are plentiful / everywhere / worthless.
- Tests plant in villager 18, not 0, for the reason in the table above.

**Next:** prices that follow beliefs.

---

## Week 3 — villagers, movement and meetings (2026-09-20)

### What changed

Twenty villagers, created through events like everything else. The first tick records a
`VillagerCreated` per villager with traits (`gossip`, `credulity`, `greed`, each 0 to 1)
rolled from the seeded random, so the log still builds the whole world from nothing.

One tick is now one part of a day — `MORNING`, `MIDDAY`, `EVENING`, `NIGHT`. Each tick
every villager picks one of four spots (`WELL`, `MARKET`, `FIELDS`, `HOME`), weighted by
the part of the day, and whoever shares a public spot is shuffled and paired into
`VillagersMet` events. An odd villager out meets nobody that tick.

Added: `Event` now permits `VillagerCreated`, `VillagerMoved`, `VillagersMet` alongside
`PriceChanged`. New types `Traits`, `Spot`, `DayPart`, `Claim`, `ClaimType`, `Belief`,
`Villager`. `Claim` implements `Comparable` so each villager's beliefs sit in a `TreeMap`
rather than a hash map. Nothing writes beliefs yet — rumors are week 4.

`Narrator` turns events into lines and returns strings without printing; a new `cli`
module does the printing, so `core` stays a library. Run it with
`./gradlew :cli:run --args="42 8"`.

Tests went from 3 to 15: `SimulationTest` 4, `VillageTest` 7, `NarratorTest` 4.

### Numbers

A tick now emits about 31 events instead of 1, so `SimulationTest` runs 2,000 ticks
(500 days, ~62,000 events) rather than the 10,000 it used when a tick was one event.
`VillageTest` runs 400 ticks.

Two deliberate breakages, to check the new tests can fail:

| Mutation | Caught by |
| --- | --- |
| Change a villager's spot without recording an event | both replay tests |
| Pair villagers who are not at the same spot | the meeting and no-double-meeting tests |

### The HOME finding

With meetings held at all four spots, `HOME` produced 1,370 of 3,675 meetings over 100
days (37%), 920 of them at night. Cause: `HOME` was one shared location, and the night
weights put ~95% of the village there, so each night was a uniform random pairing over
nearly the whole village.

`HOME` no longer pairs, on the grounds that home is twenty separate houses rather than
one room. Measured across seeds 42, 7, 1, 99 and 2024, 100 days each, with a rumor that
always transmits, starting from villager 0:

| | meetings (5 runs) | ticks to reach all 20, per seed | mean |
| --- | --- | --- | --- |
| home pairs | 18,400 | 7, 10, 6, 7, 6 | 7.2 |
| home skipped | 11,515 | 10, 7, 9, 10, 9 | 9.0 |

Meetings fell 37% and mean spread time rose 25%. The effect is a shift in the
distribution, not a per-seed guarantee: seed 7 got faster (10 to 7), because removing the
home draws changes the random sequence and therefore the whole world.

An earlier figure of 7 vs 13 ticks was wrong. It was measured by filtering home meetings
out of a log that had been generated with home pairing, which is not the same world as
one generated without it.

### Deviations from the week 3 design

- `Claim` implements `Comparable`; a `TreeMap` needs an ordering and records do not get
  one for free.
- `Villager` is a mutable final class, not a record, matching `WorldState`: mutated only
  from `apply()`, with hand-written `equals` so the drift test compares villagers.
- Villagers are born at `HOME`; the design gave no birth spot. They move the same tick.
- `Narrator` lives in `core`, not `cli`, since it does no I/O.
- The narrator skips `VillagerMoved` and `PriceChanged`.
- `Belief.NO_SOURCE = -1` for a belief held from birth.
- `ClaimType` has `SCARCE` and `ABUNDANT`; one value cannot be contradicted.
- Movement weights live on `DayPart` as exhaustive switches, so a new `Spot` or `DayPart`
  will not compile until its weights are chosen.

**Next:** rumors — gossip at meetings, beliefs that change and can be false.

---

## Week 2 — the event log (2026-09-20)

The central rule landed this week: **the simulation never changes the world directly.**
Every change happens in two steps. The simulation decides what happens, using the one
seeded `Random`, and writes it down as an event. Then the event is applied to the world
state. `record()` in `Simulation` is the single door every change comes through.

That rule buys replay. Throw the world away, re-apply the event list to a fresh
`WorldState`, and you get an identical result — which is the drift test:

```java
Simulation sim = new Simulation(42);
sim.run(10_000);
assertEquals(sim.state(), Simulation.replay(sim.log()));
```

The part worth understanding is that replay never touches the random generator.
`PriceChanged` carries the already-decided `delta`, so replaying re-reads the dice
results instead of re-rolling them. That's also why the counterfactual will work later:
drop one event from the list and replay, and every remaining event keeps its original
value. If I replayed by re-running `step()` instead, removing one event would shift
every subsequent roll and I'd be comparing two unrelated worlds rather than measuring
the effect of one rumor.

Shipped: `Event` (sealed), `PriceChanged` (record), `WorldState` with `apply()`, the
event-sourced `Simulation`, and replay. Three tests green in CI.

### A test that can't fail is worse than no test

The lesson of the week came from a test I nearly left alone.

`differentSeedsGiveDifferentStates` compared two simulations' *final states*. That was
fine in week 1, when the state was a hash-like accumulator. But the world is now a tick
counter and one clamped price, and after 10,000 ticks both runs have the same tick — so
the whole assertion rested on two final prices differing. Those prices live in a range a
few hundred wide. It passed on luck: 22 versus 309.

So I moved it to compare the event logs instead. Two different walks can land on the same
price by chance; they cannot produce the same 10,000 events.

Then the part I want to keep doing: before trusting the new version, I checked that it
*can* fail. Same seed produced equal logs, different seeds unequal ones. That check
mattered more than it looks. `assertNotEquals(a.log(), b.log())` only means anything
because `PriceChanged` is a `record` — records derive `equals` from their components, and
`List.equals` walks element by element. Had I written `PriceChanged` as an ordinary class,
two distinct objects would never compare equal, `assertNotEquals` would pass on every run,
and the test would stay green against a completely broken simulation.

I picked `record` for immutability, because that's what an event should be: a fact that
happened. It quietly made the test honest as a side effect. Worth remembering that test
validity can depend on a data-modelling choice made somewhere else entirely for another
reason.

### On the clamp

`WorldState.apply()` floors the price with `Math.max(1, ...)`. A price sitting at the
floor can only move up, so long runs drift higher — a quiet upward bias in what's supposed
to be an unbiased walk.

Not fixing it, because the random walk is scaffolding. Real prices will come from
villagers' beliefs, not random steps. The lesson generalises past this one line: every
clamp or limit in a simulation shapes its behaviour, so each one in the real model needs
to be a deliberate choice, and the tuning experiments should measure what it does.

**Next:** 20 villagers with traits, belief lists, memories, and meeting spots.

---

## Week 1 — determinism

Gradle project, `core` as pure Java 21, a tick loop whose randomness all comes from one
seeded `Random`, and a test that the same seed gives the same result. CI on every push.
