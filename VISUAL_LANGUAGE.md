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

## Price

The index is built so **100 is normal**, which makes the raw number the less useful half of
the story. The change leads everywhere — boss bar, floating text, dashboard — and the index
follows in grey.

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

A villager carries their name, what they would charge, and what they believe.

| State | Outline | Meaning |
| --- | --- | --- |
| Has heard it | grey glow | They know the rumour exists. It has not convinced them |
| Believes it | warm glow | Confidence at or above `BELIEVES` (0.5). They will act on it |
| Has not heard it | none | Most of the village, most of the time |

Glow is a scoreboard team colour, so it shows through walls and needs nothing drawn each
tick. Only two states glow: a village where everything is outlined is a village where the
outline means nothing.

Names are always shown, in **green at 60% gossip or more**, white below. That is not a mood
— it is a fact about the villager, fixed at creation — so it deliberately uses a colour the
price bands never use.

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
