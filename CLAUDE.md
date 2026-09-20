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

## Workflow
- I'm a Software Engineering student and this is my portfolio project.
  Explain design decisions briefly so I understand and can defend them.
- Make small, reviewable changes. Run ./gradlew test after every change.
- Done: deterministic loop, event log, replay, drift test, CI (weeks 1-2).
  Villagers, traits, belief structures, movement, meetings, narrator (week 3).
- Current milestone: rumors (week 4) - gossip at meetings, beliefs that change.