# Devlog

Notes on building Hearsay — what I chose, why, and what I got wrong.

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
