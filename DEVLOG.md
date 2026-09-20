# Devlog

Notes on building Hearsay — what I chose, why, and what I got wrong.

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
