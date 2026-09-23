# Hearsay

**[See a village that was lied to →](https://kayro-dev.github.io/hearsay/)** — one real session, with the same village replayed as if nobody had lied to it.

Minecraft villagers already gossip. I'm teaching them to lie, to remember who was standing there when they heard it, and to panic — then showing what would have happened if the lie was never told.

A belief-driven village economy with deterministic simulation and counterfactual replay. Every run is reproducible from its seed, its settings and the lies you told, so the same village can be replayed with one lie removed to see what it would have done instead.

**A planted rumour bursts the price within a month in about 38% of villages. In villages nobody lied to: 0%.** Left alone, a village almost never talks itself into one: **about 0.001 bursts per 100 village-days** (95% interval 0.000–0.002) — one in 120,000 village-days. That figure was 0.19 until 2026-09-23, when it turned out (E47) that most of those unaided panics were a flaw in how villagers read the price: a price recovering from a dip was taken as a sign of shortage even while still below normal. Villagers now read only the part of a move beyond normal as evidence, and the lie kept its bubbles (34.5% before, 38.0% after, on the same seeds).

Both figures are quoted with the window they were measured in, because they move without one: a share of runs that bubbled depends on how long you watched, and crediting a lie with a bubble five months later credits it with the village's own wandering. Whether a particular lie caused a particular bubble is a question the counterfactual answers — the same village, same seed, same everything, with the lie removed — rather than a claim the model makes.

### Which goods a village trades

**A village run headless trades diamonds alone. A village in the game trades every good there is** — diamonds, gold ingots, iron ingots, wheat and bread. The difference is deliberate, and it does not undermine the figures above.

Headless defaults to diamond because every experiment, every test and every session saved before there were other goods describes that village, and a village with a gold market is a different village rather than the same one with a new detail. The goods a village trades are part of its saved recipe, so a session replays as the village it was.

The calibration still describes the game because **each good behaves identically whether it is traded alone or alongside the others**. That is tested, not assumed: every good is run alone and then in every combination with the rest, and its own events — every conversation, every price, every number, in order — must be exactly equal each time. A lie about gold cannot move the price of diamonds by so much as one rumour's numbering. And each good after diamond is held to diamond's own figures on the same seeds, within their sampling error, before it is added; gold, iron, wheat and bread all met them with no tuning at all.

### Where Hearsay's prices differ from vanilla

Villagers who trade in a village Hearsay is running still keep all their ordinary trades. Hearsay takes over one offer per good and moves its price with what that villager believes. Every managed offer is a fixed bundle for a moving number of emeralds — the number of emeralds is the price.

| good | Hearsay, when nobody believes anything | vanilla |
| --- | --- | --- |
| diamond | **1 for 8 emeralds**, bought by any smith | 1 for 1, bought by an expert toolsmith |
| gold ingot | 24 for 8 emeralds, bought by a cleric | 3 for 1 |
| iron ingot | 32 for 8 emeralds, bought by an armorer | 4 for 1 |
| wheat | 120 for 6 emeralds, bought by a farmer | 20 for 1 |
| bread | 48 for 8 emeralds, sold by a farmer | 6 for 1 |

**Only diamond is repriced.** At vanilla's one emerald a 30% panic cannot be shown at all, and diamonds are not renewable, so pricing them higher cannot be farmed. Gold, iron, wheat and bread are all at vanilla's value per item, because they *are* farmable and paying more than vanilla would turn a farm into an emerald printer. A panic can raise any of them by at most 75%, and only while the village believes it.

Every managed offer is available from a villager's first level, where vanilla puts most of these later. A tool for watching rumours spread should not depend on first levelling up a villager. Vanilla's demand and reputation adjustments are switched off on managed offers only, because they are state held on the villager rather than in the saved session, and a session with them running could not be replayed.

Work in progress.