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
- The player-facing colours, icons and sounds are defined in VISUAL_LANGUAGE.md, and the
  code follows it rather than the other way round. PriceMood in core is the one definition
  of what a price means, shared by the dashboard and the plugin so they cannot disagree.
- Current milestone: v2, proposed in V2_PROPOSAL.md, taken depth first rather than breadth
  first. The stage numbers are names rather than an order. Built: stage 1 (the marked
  market) and stage 3 (real diamond trades, E33). **The order from here, and no step starts
  until the one before it is done and measured:**
  1. measure the selling — a played session plus a headless sweep of both trade weights,
     reported as an E-number. Does witnessed selling alone bring decay below 1 while the lie
     still causes its bubbles? The no-lie timeline's price effect reported separately.
  2. ~~stage 4, reality checks, as a damper~~ — built, then turned off. E37 found the
     village already settles on its own once decay is measured from the largest swing
     rather than from the start, so there was nothing to damp. Inert at checkWeight 0.
     Swing decay and the quiet-village rate are both length-dependent and must only be
     compared between runs of the same length; MarketStats.ENOUGH_SWINGS enforces the
     minimum.
  3. **stage 2, more goods — in progress, plan in V2_PROPOSAL.md.** Goods are independent
     markets (no spillover), added one at a time behind gates: diamond, then gold, iron,
     wheat and bread; diamond, gold (E39), iron (E40), wheat (E42) and bread (E44) are done,
     and all four pass again under the level gate (E48). Bulk selling measured (E45). PinnedLogsTest holds five diamond
     logs bit for bit, through a frozen printer, and must never be updated to match a
     change — if it fails, stage 2 has changed diamond. IndependenceTest demands each
     good's events be equal with and without the others. Params.goods records which
     goods a village trades; diamond alone by default. Each new good
     joins only when every earlier good's events are bit-identical with and without it and
     it meets diamond's thirty-day targets with shared parameters; one that misses has found
     an independence bug, not a reason to tune. Managed trades are fixed bundles at vanilla
     value per item (only diamond is repriced), so no farm becomes an emerald printer.
- **Every target is a `Target`, never a bare threshold.** It judges only an `Estimate`,
  which cannot exist without its sample size and 95% interval; it names the smallest sample
  it may be judged on; and a band says whether it must be DEMONSTRATED (interval inside) or
  merely NOT_CONTRADICTED (interval reaching). A new good is judged by
  `./gradlew :experiments:goods --args="--good X"` against diamond on the same seeds, and
  comparisons made together share their 5% (five at 95% each fail an identical good 23% of
  the time; see E42). That fix is for comparisons only: for a DEMONSTRATED band a wider
  interval fails more, not less, so a band that fails a good model too often needs more
  data, not shared confidence (E43). `./gradlew :experiments:guard` measures
  CalibrationTest's false-failure rate and power against the real model on fresh seeds.
- Figures that are shares of runs move with run length and must not be compared across
  lengths. The quiet-village rate is 0.001 bursts per 100 village-days [0.000-0.002] over
  120,000 village-days (E48, `./gradlew :experiments:goods`); it was 0.19 (E38) until E47
  found most of it was the E45 rebound bug. A bubble is only laid at a lie's door within
  thirty days of it.
- Player-facing features built between stages must not touch the simulation's decisions or
  the calibration, and each needs a test proving it read-only, so no experiment re-runs.
- **`levelGate` is on by default since 2026-09-23 (E47, E48):** only the part of a price move
  beyond normal is evidence, so a glut's recovery is not a famine and a bubble's unwinding is
  not a glut. It removed E45's selling-rebound panics and kept the lie's bubbles; the cost is
  that a bubble comes down on decay alone, about 45% more slowly, and no longer overshoots.
  Every experiment before E47 was measured at levelGate 0, and old recipes read as 0.
  PinnedLogsTest holds the pre-stage-2 logs under levelGate 0, the rule they were recorded
  under; DefaultLogsTest pins the same five villages under the current defaults. Neither is
  ever updated to match a change.
- `trendAnchor` (E46) is built and inert at 0, like the reality checks: reading against a
  trailing average follows momentum and made E45 worse.
- E32's "never settles" is withdrawn (E37): measured from the largest swing, the village
  settles on its own. Measure oscillation after the peak, and never let a damper mute the
  village — only the oscillation measures beside the paired-worlds separation can tell
  damping from muting.