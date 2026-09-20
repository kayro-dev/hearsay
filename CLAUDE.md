# Hearsay

A belief-driven Minecraft village economy. Villagers hold possibly false beliefs,
rumors spread and mutate between them, and prices follow beliefs. Every event is
logged so any crash can be replayed and compared against a counterfactual.

## Architecture rules
- `core` is pure Java 21 with no Minecraft dependencies.
- The simulation is deterministic: all randomness comes from one seeded Random
  owned by Simulation. Never use Math.random(), system time, or unordered
  collections (HashMap/HashSet iteration order) in simulation logic.
- Event sourcing: the world changes only through events. Simulation decides and
  records events; WorldState.apply() is the only place state changes.
- Replay must always reproduce the exact state (see the drift test).
- Input events vs derived events. Input events come from outside the simulation
  (RumorPlanted today, every player action later). Derived events are decided by
  the simulation (moves, meetings, tellings, mutations). Replay re-applies the
  whole log; a counterfactual re-runs from seed + params + input events with one
  input removed and lets everything derived be decided again. The full recipe for
  any run is seed + params + input events.
- Separate random streams per subsystem: movement, gossip, mutation and price
  each get their own Random derived from the seed. Never share one generator
  across subsystems. If they shared, planting a rumor would shift every later
  draw and villagers would walk somewhere else, so a counterfactual would differ
  for reasons having nothing to do with the rumor.

## Workflow
- I'm a Software Engineering student and this is my portfolio project.
  Explain design decisions briefly so I understand and can defend them.
- Make small, reviewable changes. Run ./gradlew test after every change.
- Done: deterministic loop, event log, replay, drift test, CI (weeks 1-2).
  Villagers, traits, belief structures, movement, meetings, narrator (week 3).
  Rumors, input vs derived events, separate random streams (week 4).
  Prices from belief, market observation, the feedback loop (week 5).
- Tuning is decided by seed sweeps in the experiments module, not by hand. Every
  decision is recorded in EXPERIMENTS.md with its command and table. CalibrationTest
  holds the defaults to the behaviour they were chosen for; if it fails, the model
  has been retuned, deliberately or otherwise.
- Current milestone: counterfactual replay (week 6) - what the price would have
  been without the lie.