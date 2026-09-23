# Visual language

Everything the player sees means one thing and means it everywhere. This file is the
definition; the code follows it, and where the two disagree the code is wrong.

## The one rule

**Warm is a village frightened. Cool is a village that thinks there is plenty. White is an
ordinary day.**

Not green and red. A stock ticker paints a rising price green because a rising price is
good news for whoever owns the thing. Here a rising price is a village that has talked
itself into a shortage that may not exist, and colouring that as good news would tell the
player the opposite of what is happening. Rising runs warm the way an alarm does.

## One claim at a time

A village trading four goods holds eight claims and four prices. **The world shows one claim
at a time**: the heads, the glow, the ❗ when a rumour takes and the boss bar all follow the
same one, and the bar names its good ("Wheat ▲ +12%"). Planting a rumour switches them to
the claim just planted; `/hearsay watch <good> [scarce|abundant]` switches them by hand. A
head carrying every good is the clutter the labels were cut down to remove. What a villager
thinks about everything else is one crouch-right-click away, and never on display.

Switching is display only — `OnDisplay` in core is tested to change nothing — so watching
wheat instead of diamonds is never a different experiment.

## Price

The index is built so **100 is normal**, which makes the raw number the less useful half of
the story. **The change is what the player is shown**, everywhere: boss bar, above heads,
dashboard headline.

The raw number is not shown in the world at all. It has no unit — "138" is 138 of nothing —
so no honest label can be put on it: it is a scale, not a currency, and calling it a price
would imply emeralds it does not mean. The dashboard still prints it beside the change,
because that is an analysis document and a reader there wants the absolute, and it is called
an index there because that is what it is.

**When real trades arrive this changes.** The number becomes emeralds, it belongs in the
world again, and it is called a price — because then that is what it will be.

| Band | Reads as | Colour | Arrow | Where it starts |
| --- | --- | --- | --- | --- |
| Panic | `▲ +30%` | `#b02a1e` deep red | ▲ | `Bubble.PEAK_ABOVE` |
| Alarmed | `▲ +20%` | `#d1622a` burnt orange | ▲ | `Bubble.ELEVATED` |
| Rising | `△ +8%` | `#d99a2b` amber | △ | 5% over normal |
| Normal | `· 0%` | `#b9b3a8` bone | · | within 5% |
| Easing | `▽ -8%` | `#6fa8dc` pale blue | ▽ | 5% under normal |
| Glut | `▼ -20%` | `#4a7fd4` deep blue | ▼ | `Bubble.TROUGH_BELOW` |

Defined once, in `PriceMood`. The bands are the same ones `Bubble` counts bubbles and busts
at, so the picture and the statistics can never drift apart, and they scale with
`basePrice` rather than assuming 100.

## Villagers

A villager carries their name, and what they would charge if it is not the ordinary price.
**Nothing else.** The label once carried four numbers at once — how talkative they are, what
they charge, the raw index, and how sure they were — and a player reading four numbers off
one head is reading none of them.

Each of the three that went has somewhere better to be: how talkative somebody is is what
`/hearsay who` is for and what the green name says, the raw index is on the boss bar, and
how sure they are is what the glow says. A villager asking the ordinary price shows only
their name, so **a number above a head means somebody believed something**.

| State | Outline | Meaning |
| --- | --- | --- |
| Has heard it | grey glow | They know the rumour exists. It has not convinced them |
| Believes it | warm glow — **red** in game | Confidence at or above `BELIEVES` (0.5). They will act on it. Team colours are sixteen named colours, and the alarmed orange rounds to red |
| Has not heard it | none | Most of the village, most of the time |

Glow is a scoreboard team colour, so it shows through walls and needs nothing drawn each
tick. Only two states glow: a village where everything is outlined is a village where the
outline means nothing.

Names are always shown, in **green at 60% gossip or more**, white below. That is not a mood
— it is a fact about the villager, fixed at creation — so it deliberately uses a colour the
price bands never use.

## The market

A marked market is drawn as a ring of `END_ROD` particles at its edge, once per simulation
tick rather than continuously. Bone white — the colour of an ordinary price — because the
market itself has no opinion. What happens inside it is what has the opinion.

It is a fact to be checked occasionally, not something that should be glowing at the player
all evening.

## Telling

When one villager tells another, in this order:

1. **A trail of particles** from the teller to the listener, in the claim's colour: warm for
   scarcity, cool for abundance. About a second, drawn over several frames, because a single
   frame once every ten seconds is invisible.
2. **A quiet chime**, played to the player rather than into the world, so villager chatter
   cannot cover it and a friendly-creature volume setting cannot silence it.
3. **A mark above the listener**, but only the first time they cross into believing it. A
   mark every time anyone repeated anything would be constant and would say nothing.

| Thing | Scarcity | Abundance |
| --- | --- | --- |
| Particle | `FLAME`, warm | `SOUL_FIRE_FLAME`, cool |
| Chime | amethyst, higher pitch | amethyst, lower pitch |
| Mark | `❗` in the panic colour | `❄` in the glut colour |

## Sound

Three sounds, and no more. Anything that happens every tick must be silent.

| Sound | When | Why |
| --- | --- | --- |
| Amethyst chime | a telling you are near | rare enough to mean something |
| Higher chime | somebody starts believing | the moment the rumour took |
| Nothing at all | the market settling | it happens every tick |

## What the player is never shown

The simulation knows things the player should have to work out. Belief chains, who is in
whose chain, the counterfactual. Those live in the dashboard and the CLI, after the fact.
In the world the player sees what they could see by standing there and watching.
