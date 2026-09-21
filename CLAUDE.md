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
  Counterfactual replay and paired worlds (week 6).
  In-game spike: external meetings, Paper plugin, saved recipes (week 7).
  The average-ask price rule, and neighbourhoods fitted to recorded traces so the
  model mixes the way a played village does (E20, E23).
- Tuning is decided by seed sweeps in the experiments module, not by hand. Every
  decision is recorded in EXPERIMENTS.md with its command and table. CalibrationTest
  holds the defaults to the behaviour they were chosen for; if it fails, the model
  has been retuned, deliberately or otherwise.
- State must be complete: anything a decision reads lives in WorldState, or a forked
  world silently loses it. ResumeTest is what catches that; Simulation.fork carries
  simulation-local state and must be updated if any is ever added.
- Minecraft owns the bodies, Hearsay owns the minds. In MeetingSource.EXTERNAL the
  plugin reports who met whom as inputs; core never decides movement. Such a run is
  reproducible from seed + params + inputs, not from the seed alone.
- `core` has no Paper code and no Paper dependency. Anything worth testing lives there
  as plain Java; the paper module is only positions, screens and commands.
- The dashboard is `./gradlew :cli:run --args="dashboard --file PATH"`, which writes a
  self-contained HTML page. It embeds its numbers rather than fetching CSVs, because a
  page opened from file:// cannot fetch anything at all; the page builder lives in core
  as DashboardPage so it can be tested, and the cli command is a thin wrapper.
- Current milestone: playing enough sessions to write the thing up.