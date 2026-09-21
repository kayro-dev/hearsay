# Experiments

Tuning decisions and the measurements behind them. Every entry records the exact command,
the table it produced, and what was changed as a result.

The sweeps are reproducible: the same command gives a byte-identical CSV, because a run is
fully determined by its seed, params and inputs, and the sweep fixes all three. CSVs are
written to `experiments/build/` and are not committed — re-run the command instead.

Unless stated otherwise, every run plants one `("diamond", SCARCE)` rumor at severity 1 on
tick 1, in the seed's gossipiest villager, and runs 200 ticks (50 days) over seeds 1-50.

### What "bubble" means

A **bubble** is a run where the price went above **130** and later came back under **110**.
Both halves matter: a price that runs up and stays up is a change of regime, not a bubble,
and counting one as the other would overstate the case. The definition lives in one place in
the code, `Bubble`, and every command, experiment and test reads it from there.

Two neighbouring measures are named differently on purpose:

| measure | meaning |
| --- | --- |
| **bubble** | went above 130, came back under 110 |
| **elevated** | the price was above 120, saying nothing about whether it came back |
| **peak price** | how far it went, saying nothing about whether it came back |

Entries before E6 use the word "burst" in their column headings for what is now called a
bubble; the thresholds were the same 130 and 110 throughout, so the figures are comparable.

### What the columns mean

| column | meaning |
| --- | --- |
| peak believes | the most villagers, of 20, ever above 50% confidence on the same day |
| p10 / p90 | 10th and 90th percentile of that across seeds, by nearest rank, so each is a count some seed produced |
| half-believes | share of seeds where 10 or more villagers believed at once |
| any believer | mean days with at least one believer |
| days at half peak | mean days with at least half as many believers as that seed's peak |
| median spell | mean over seeds of the median days one villager stays above 50%, counting only spells that ended |
| overshoot | share of seeds where more than 15 of 20 believed at once |
| peak heard | the most villagers ever holding the claim at any strength |

---

## E1 — Which knob moves belief

Rumor-only belief was far weaker than expected: the village reached 10 of 20 people but
nobody stayed above 50% past day 6. The question was whether `dailyDecay` or
`tellThreshold` was the binding constraint.

```
./gradlew :experiments:run --args="--seeds 50 --ticks 200 \
    --decay 0.85,0.90,0.93,0.95,0.97 --tell 0.2,0.3,0.4 --csv build/e1.csv"
```

```
                      peak believes (of 20)            days at    median spell
  decay  tell    mean   p10   p90   half-believes   any believer   half peak   (days)   overshoot   peak heard
   0.85  0.2     2.7     1     4             0%            4.4         3.8      2.2          0%         12.7
   0.85  0.3     2.7     1     4             0%            4.4         3.8      2.4          0%         12.2
   0.85  0.4     2.7     2     4             0%            4.4         3.7      2.4          0%         10.8

   0.90  0.2     3.8     2     6             2%            8.9         6.0      2.5          0%         16.3
   0.90  0.3     3.7     2     6             0%            8.8         6.1      2.8          0%         15.4
   0.90  0.4     3.9     2     6             2%            8.0         5.5      2.8          0%         14.2

   0.93  0.2     5.8     2     9            16%           21.8        12.4      3.3          0%         18.0
   0.93  0.3     5.6     2     9            12%           17.6        11.0      3.6          0%         17.5
   0.93  0.4     5.7     2     9            18%           15.8        10.1      3.7          0%         17.4

   0.95  0.2     8.8     4    13            50%           37.8        21.5      5.2          4%         18.6
   0.95  0.3     8.5     3    14            44%           33.4        20.1      5.2          4%         18.6
   0.95  0.4     8.4     3    13            50%           29.9        18.3      5.5          0%         18.3

   0.97  0.2    12.8     8    17            82%           49.8        34.8     12.0         26%         19.0
   0.97  0.3    12.6     6    18            82%           49.3        33.8     13.6         22%         19.0
   0.97  0.4    13.0     6    17            78%           48.1        32.8     12.3         34%         18.9
```

**Findings.** `dailyDecay` is the only knob that moves peak belief: mean peak goes 2.7,
3.8, 5.7, 8.5, 12.8 across the decay levels, while the three `tellThreshold` values land
within 0.3 of each other at every level. `tellThreshold` instead controls reach and
duration: at decay 0.93 it takes days-with-any-believer from 21.8 down to 15.8, and it
costs 1 to 2 villagers of peak reach throughout. Overshoot is zero below decay 0.95 and
becomes common at 0.97.

**Decision.** Search between 0.92 and 0.93, hold `tellThreshold` at 0.4 for the shorter
duration and tighter reach, and add a stricter duration metric: "days with any believer"
counts a single holdout as though the village still believed, which is why duration at
0.93 looked three times longer than the peak suggested.

---

## E2 — Choosing the default decay

Re-run after adding **days at half peak** and **median spell**, against a target of a peak
of 4-8 believers in most seeds and a grip lasting about a week. The target is deliberately
modest: week 5 adds market feedback, where rising prices confirm scarcity, and that is
meant to do the work of turning a rumor into a bubble.

```
./gradlew :experiments:run --args="--seeds 50 --ticks 200 \
    --decay 0.90,0.91,0.92,0.93,0.94 --tell 0.4 --csv build/e2.csv"
```

```
                      peak believes (of 20)            days at    median spell
  decay  tell    mean   p10   p90   half-believes   any believer   half peak   (days)   overshoot   peak heard
   0.90  0.4     3.9     2     6             2%            8.0         5.5      2.8          0%         14.2
   0.91  0.4     4.2     2     6             6%            9.6         6.8      3.0          0%         14.9
   0.92  0.4     4.7     2     7             6%           11.7         8.0      3.4          0%         15.8
   0.93  0.4     5.7     2     9            18%           15.8        10.1      3.7          0%         17.4
   0.94  0.4     6.8     3    10            38%           21.4        14.1      4.9          0%         17.8
```

**Findings.** The stricter metric resolved the apparent conflict between the two targets.
Under "days with any believer", decay 0.92 looked like 11.7 days and 0.93 like 15.8, both
far past a week. Under "days at half peak" they are 8.0 and 10.1. The gap between the two
measures is the long tail of one or two stragglers still believing after the village has
moved on.

At 0.92 the mean peak is 4.7 with a p90 of 7, so most seeds sit inside the 4-8 band, the
grip lasts 8 days, and an individual believes for a median of 3.4 days. At 0.93 the peak
band reaches 9 and 18% of seeds convince half the village, which is closer to bubble
behaviour than the rumor-only layer should produce on its own.

**Decision.** Defaults set to `tellThreshold` 0.4 and `dailyDecay` 0.92. No seed in fifty
overshoots at that setting, which leaves the headroom for week 5.

> **Note added at E29.** This sweep held `tellThreshold` fixed at 0.4 and varied only the
> decay, and every sweep after it treated the threshold as settled. That was safe only
> because the village here mixes perfectly: a villager meets four different people in two
> days, so a belief is confirmed again before it decays under the threshold and the
> threshold barely matters. Once E23 clustered the village to match recorded traces, a
> villager meets about two, confirmations arrive late or not at all, and the threshold
> decides whether a rumor is ever repeated at all. At 0.4 in a clustered village the
> market ends up doing nearly all the convincing, which E29 measured and fixed by dropping
> the threshold to 0.25. **What this experiment established holds for the mixed model
> only.**

---

## E3 — Does a rumor make a bubble, and does the village stay calm without one

The experiment the project rests on. Every combination runs twice over the same 50 seeds,
once with a rumor planted in the gossipiest villager and once with nothing planted.
Movement and gossip draw from streams that inputs never touch, so the two runs put the
same villagers in the same places on the same ticks, and any difference between them is
the rumor and nothing else.

Target from the concept doc: a bubble in 60 to 80% of seeds with a rumor, and under 10%
without. "Bubble" here is read as half the village believing diamonds are scarce, counted
by `MarketStats`, which tracks a claim rather than a rumor family, because the no-rumor
condition has no family to count.

```
./gradlew :experiments:bubble --args="--seeds 50 --ticks 200 \
    --observation 0,0.1,0.15,0.2,0.3 --sensitivity 0.5,1.0,1.5 --csv build/bubble.csv"
```

```
                    with a planted rumor              without any rumor
   obs   sens   half  >120  peak$  days>120  believers |  half  >120  peak$  days>120  believers
  0.00  0.5      6%   82%  131.9       6.2        4.7 |    0%    0%  103.0       0.0        0.0
  0.00  1.0      6%  100%  163.2      15.3        4.7 |    0%    0%  103.0       0.0        0.0
  0.00  1.5      6%  100%  194.4      20.4        4.7 |    0%    0%  103.0       0.0        0.0

  0.10  0.5     24%   82%  135.1      18.2        5.6 |    0%    0%  103.0       0.0        0.0
  0.10  1.0     72%  100%  188.5      39.6       13.4 |    0%    0%  103.0       0.0        0.0
  0.10  1.5     90%  100%  246.7      46.3       16.7 |    0%    0%  103.0       0.0        0.0

  0.15  0.5     44%   82%  138.5      24.5        7.7 |    0%    0%  103.0       0.0        0.0
  0.15  1.0     90%  100%  198.5      44.0       17.9 |    0%    0%  103.0       0.0        0.0
  0.15  1.5    100%  100%  257.0      47.4       20.0 |    0%    0%  103.0       0.0        0.0

  0.20  0.5     64%   84%  143.5      30.8       12.7 |    0%    0%  103.0       0.0        0.0
  0.20  1.0     98%  100%  204.7      45.8       19.6 |    0%    0%  103.0       0.0        0.0
  0.20  1.5    100%  100%  257.0      47.5       20.0 |    0%    0%  103.0       0.0        0.0

  0.30  0.5     80%   86%  147.3      35.9       16.6 |    0%    0%  103.0       0.0        0.0
  0.30  1.0    100%  100%  206.0      47.0       20.0 |    0%    0%  103.0       0.0        0.0
  0.30  1.5    100%  100%  257.0      47.6       20.0 |    0%    0%  103.0       0.0        0.0
```

**The loop closes.** At `observationWeight` 0, where the market cannot feed back into
belief, a rumor convinces 4.7 villagers on average and half the village in 6% of seeds.
Turning the feedback on takes the same rumor to 13.4 believers and 72% of seeds at 0.10
with sensitivity 1.0. Nothing else changed, so the difference is the belief-price-belief
loop and nothing else.

**Three cells land in the 60-80% band:** observation 0.20 with sensitivity 0.5 at 64%,
observation 0.10 with sensitivity 1.0 at 72%, and observation 0.30 with sensitivity 0.5 at
80%. The current defaults, 0.15 and 1.0, give 90%, above the band.

**The without-rumor zero is structural, not measured.** Market noise is plus or minus 3%,
applied once to the median, and the observation threshold is 10%. A quiet village peaks at
exactly 103 in all 50 seeds, so no villager can ever read anything into the price and no
bubble can start on its own. The "under 10%" target is met, but by a mechanism that cannot
fire rather than by one that rarely fires. Measuring spontaneous panics needs noise that
accumulates, rather than a fresh draw around base each tick.

**The bubbles do not burst.** One trajectory, seed 3 at the current defaults:

```
  day  1  high 101  heard  3  believe  1
  day  7  high 132  heard 14  believe  3
  day 13  high 180  heard 20  believe 11
  day 19  high 205  heard 20  believe 17
  day 25  high 205  heard 20  believe 20
  day 50  high 200  heard 20  believe 20
```

The price climbs to roughly twice base and stays there for the last 30 days of the run.
The arithmetic says it must: a villager who observes a saturated price once a day settles
at a confidence of `d·w / (1 - d(1-w))`, which for decay 0.92 and weight 0.15 is 0.63,
comfortably above the 0.5 believing threshold. Belief locks in, the asks stay high, and the
price holds them there. For decay 0.92 the equilibrium only falls below 0.5 when
`observationWeight` is under about 0.087, and villagers reach the market more than once a
day, so the real figure is lower still. `days>120` of 30 to 47 out of 50 is describing a
permanent change of regime, not a bubble.

**Decision.** None applied here: defaults unchanged pending a decision on whether a bubble
is supposed to deflate. The three cells above hit the stated target as written. Resolved in
E4, where bubbles were made to deflate and these settings were superseded.

---

## E4 — Bubbles that deflate

Three changes since E3, all aimed at making a bubble able to end:

1. **Momentum instead of level.** A villager reads the move since the price they last drew
   a conclusion from, not how far the price stands from base. A steady price, however
   high, is no evidence at all. A villager who has concluded nothing yet measures against
   base, so the first move still registers and the loop can start.
2. **Mean-reverting noise.** `noise(t) = 0.8 × noise(t-1) + step`, so a run of steps in one
   direction compounds instead of being drawn fresh each tick.
3. **Market quorum 5**, up from 3.

Plus a burst metric: the share of seeds where the price peaked above 130 and then came back
under 110 before the run ended, and how long that fall took.

```
./gradlew :experiments:bubble --args="--seeds 50 --ticks 200 \
    --observation 0,0.1,0.15,0.2,0.3 --sensitivity 0.5,1.0,1.5 --csv build/e4.csv"
```

```
                         with a planted rumor                         without any rumor
   obs   sens   half  peak$  burst  burst-days  believers |  half  peak$  burst  believers
  0.00  0.5      4%  126.5    28%        10.4        4.6 |    0%  106.3     0%        0.0
  0.00  1.0      4%  151.4    88%         9.7        4.6 |    0%  106.3     0%        0.0
  0.00  1.5      6%  177.4    98%        11.4        4.6 |    0%  106.3     0%        0.0

  0.10  1.0     12%  153.8    92%        12.7        5.1 |    0%  106.3     0%        0.0
  0.15  1.0     18%  157.2    92%        14.0        5.2 |    0%  106.3     0%        0.0
  0.20  1.0     16%  159.3    92%        15.2        5.5 |    0%  106.3     0%        0.0
  0.30  1.0     26%  164.5    94%        17.4        6.0 |    0%  106.3     0%        0.0
  0.30  1.5     30%  200.9    96%        22.4        6.6 |    0%  106.3     0%        0.0
```

**Bubbles now deflate.** 88 to 98% of seeds run the price past 130 and bring it back under
110, in a mean of 10 to 22 days. The permanent plateau of E3 is gone: the run-up stops
confirming itself the moment it levels off, decay takes over, and the fall then reads as
evidence the other way.

**But belief collapsed**, from 72-100% reaching half the village in E3 to 4-30% here. The
cause is arithmetic rather than anything conceptual. Evidence weight is
`observationWeight × min(1, |move|)`, and that `min(1, ...)` was calibrated against levels,
where a price of 200 against a base of 100 gives a full 1.0. Under momentum a move only
just past the threshold gives about 0.12, so every observation is worth roughly eight
times less than it used to be. The same knob now means something much smaller.

**Note the burst column at `obs 0.00`:** 88 to 98% of seeds burst even with the feedback
loop switched off entirely. A rumor rises and fades on its own, taking asks with it, so
"the price went up and came down" does not by itself demonstrate feedback. What the
feedback adds is size and duration: at sensitivity 1.0, going from observation 0 to 0.30
lifts the mean peak from 151 to 165 and stretches the fall from 9.7 days to 17.4.

### E4b — restoring the reach

If the weight shrank by roughly eight times, the question is whether the target is still
reachable further up the range.

```
./gradlew :experiments:bubble --args="--seeds 50 --ticks 200 \
    --observation 0.3,0.5,0.75,1.0 --sensitivity 1.0,1.5 --csv build/e4b.csv"
```

```
                         with a planted rumor                         without any rumor
   obs   sens   half  peak$  burst  burst-days  believers |  half  peak$  burst  believers
  0.30  1.0     26%  164.5    94%        17.4        6.0 |    0%  106.3     0%        0.0
  0.30  1.5     30%  200.9    96%        22.4        6.6 |    0%  106.3     0%        0.0
  0.50  1.0     36%  166.4    90%        17.6        7.0 |    0%  106.3     0%        0.0
  0.50  1.5     62%  219.8    98%        19.7        9.8 |    0%  106.3     0%        0.0
  0.75  1.0     70%  180.1    96%        15.1       10.6 |    0%  106.3     0%        0.0
  0.75  1.5     88%  237.2   100%        18.4       15.6 |    0%  106.3     0%        0.0
  1.00  1.0     84%  191.4    98%        16.3       14.9 |    0%  106.3     0%        0.0
  1.00  1.5     98%  256.6    90%        16.2       19.3 |    0%  106.3     0%        0.0
```

**Two cells meet the whole target at once.** Observation 0.75 with sensitivity 1.0 gives
half the village believing in 70% of seeds, a burst in 96% of them taking 15 days to come
back down, and 0% of quiet villages panicking. Observation 0.50 with sensitivity 1.5 gives
62%, 98% and 0%. Both sit inside the concept doc's 60-80% band with a rumor and under 10%
without.

**The 0% without a rumor is still structural.** Carrying the noise over does compound it:
across 50 quiet seeds the peak price ranges 103 to 109, where a single step could only
reach 103. But it never crosses the 110 an observation needs, so no quiet village ever
panics. Making spontaneous panics rare-but-possible rather than impossible needs a larger
`marketNoise` or a `noiseDecay` nearer 0.9; at the current 0.03 and 0.8 the stationary
spread is about 0.029, which puts the threshold three and a half standard deviations away.

**Decision.** None applied here: defaults unchanged pending a choice between the two cells,
and a decision on whether the weight formula should be rescaled against the threshold rather
than the raw move, which would put the useful range of `observationWeight` back near its old
values instead of near 1. Resolved in E5: the rescaling was adopted as `fullMoveSize`, which
made both cells here obsolete, and the settings were chosen again on the rescaled grid.

---

## E5a — Tuning the wobble on its own

E4 left spontaneous panics impossible rather than rare: the carried-over noise never
crossed the threshold an observation needs, so "bubbles need a rumor" was true by
construction. Before anything else is decided, the wobble is tuned on quiet villages alone,
where nothing is planted and anything that happens came out of the noise.

The first pass measured panics as villagers reaching the believing threshold of 0.5 and
read 0% everywhere, while prices in the same runs reached 144 and 181 — far beyond what
noise alone can produce, so beliefs were plainly forming and amplifying below that line.
The measure is now whether anyone holds the claim at all, which catches the mechanism
firing rather than only its end state.

```
./gradlew :experiments:noise --args="--seeds 300 --ticks 200 \
    --noise 0.030,0.033,0.036 --decay 0.82,0.84,0.86,0.88 --csv build/noise-fine.csv"
```

```
  noise   decay   any holder   any believer   burst   peak$   max$
  0.030   0.82         0.7%           0.0%    0.0%   106.8    112
  0.030   0.84         1.7%           0.0%    0.0%   107.1    113
  0.030   0.86         5.7%           0.0%    0.0%   107.7    121
  0.030   0.88        15.7%           0.0%    0.0%   108.5    125

  0.033   0.82         3.0%           0.0%    0.0%   107.5    117
  0.033   0.84        10.0%           0.0%    0.0%   108.1    122
  0.033   0.86        19.3%           0.0%    0.0%   109.1    124
  0.033   0.88        31.7%           0.0%    0.3%   110.5    135

  0.036   0.82        12.7%           0.0%    0.0%   108.6    122
  0.036   0.84        23.3%           0.0%    0.0%   109.6    124
  0.036   0.86        35.3%           0.0%    0.3%   110.9    135
  0.036   0.88        55.7%           0.0%    0.7%   113.0    139
```

The rate is far more sensitive to how long the wobble carries than to how big each step is:
holding the step at 0.030 and moving the carry from 0.82 to 0.88 takes the rate from 0.7%
to 15.7%.

**Decision.** Adopted as defaults: `marketNoise` 0.030 with `noiseDecay` 0.86, giving 5.7%,
in the middle of the 2-8% target. A quiet village still averages a peak of 107.7 and never
bursts, so the mechanism can fire without the village being permanently jumpy. Held in
place by `CalibrationTest`, which fails if any quiet village in seeds 1001-1100 bursts.

---

## E5 — Three conditions, and a candidate

Observation weight is now scaled against a new `fullMoveSize` (0.20) rather than the raw
move: `w = observationWeight × min(1, |move| / fullMoveSize)`. The threshold still decides
what gets noticed; this decides what a noticed move is worth, and puts the useful range of
`observationWeight` back near its old values instead of pinned at the top of its range.

Each setting runs three ways over the same seeds: a rumor with the feedback loop live, the
same rumor with the loop switched off, and a quiet village. All three put the same villagers
in the same places on the same ticks, so the columns differ only by the rumor and the loop.

```
./gradlew :experiments:bubble --args="--seeds 50 --ticks 200 \
    --observation 0.05,0.10,0.15,0.20,0.25,0.30 --sensitivity 0.5,0.75,1.0,1.25,1.5 \
    --noise 0.030 --decay 0.86 --csv build/e5.csv"
```

```
                 rumor + feedback            rumor only               quiet
  obs  sens   half  peak$  burst  days |  peak$  burst  days |  held  peak$  burst
 0.05  0.50    12%  128.6    36%  13.8 | 126.6    28%  10.6 |   0%  107.3     0%
 0.05  0.75    14%  143.7    76%  12.3 | 139.0    66%  10.5 |   0%  107.3     0%
 0.05  1.00    16%  158.2    92%  13.7 | 151.5    88%  10.2 |   0%  107.3     0%
 0.05  1.25    14%  176.0    94%  17.3 | 164.4    96%  11.1 |   0%  107.3     0%
 0.05  1.50    14%  190.5    98%  18.6 | 177.0    98%  11.6 |   0%  107.3     0%

 0.10  0.50    20%  130.1    40%  14.0 | 126.6    28%  10.6 |   2%  107.3     0%
 0.10  0.75    22%  145.9    80%  13.0 | 139.0    66%  10.5 |   2%  107.3     0%
 0.10  1.00    24%  163.2    92%  14.4 | 151.5    88%  10.2 |   2%  107.3     0%
 0.10  1.25    22%  175.6    96%  14.4 | 164.4    96%  11.1 |   2%  107.3     0%
 0.10  1.50    22%  193.4    98%  15.2 | 177.0    98%  11.6 |   2%  107.3     0%

 0.15  0.50    20%  132.0    46%  10.9 | 126.6    28%  10.6 |   2%  107.3     0%
 0.15  0.75    36%  150.6    82%  13.8 | 139.0    66%  10.5 |   2%  107.3     0%
 0.15  1.00    38%  168.1    92%  13.8 | 151.5    88%  10.2 |   2%  107.3     0%
 0.15  1.25    50%  188.9    98%  14.3 | 164.4    96%  11.1 |   2%  107.3     0%
 0.15  1.50    52%  208.6   100%  14.5 | 177.0    98%  11.6 |   2%  107.4     0%

 0.20  0.50    34%  135.1    60%  11.8 | 126.6    28%  10.6 |   2%  107.3     0%
 0.20  0.75    52%  154.5    84%  12.5 | 139.0    66%  10.5 |   2%  107.3     0%
 0.20  1.00    64%  177.3    96%  12.1 | 151.5    88%  10.2 |   2%  107.3     0%
 0.20  1.25    68%  202.1    98%  13.3 | 164.4    96%  11.1 |   2%  107.4     0%
 0.20  1.50    76%  224.7    94%  14.2 | 177.0    98%  11.6 |   2%  107.4     0%

 0.25  0.50    54%  138.7    68%  12.8 | 126.6    28%  10.6 |   2%  107.3     0%
 0.25  0.75    72%  162.9    88%  12.9 | 139.0    66%  10.5 |   2%  107.3     0%
 0.25  1.00    84%  188.1    92%  13.6 | 151.5    88%  10.2 |   2%  107.4     0%
 0.25  1.25   100%  216.3    92%  13.9 | 164.4    96%  11.1 |   2%  107.4     0%
 0.25  1.50   100%  248.6    98%  12.6 | 177.0    98%  11.6 |   2%  107.9     0%

 0.30  0.50    66%  140.6    72%  13.3 | 126.6    28%  10.6 |   2%  107.3     0%
 0.30  0.75    86%  169.2    90%  13.5 | 139.0    66%  10.5 |   2%  107.4     0%
 0.30  1.00   100%  200.4    98%  12.3 | 151.5    88%  10.2 |   2%  107.4     0%
 0.30  1.25   100%  226.9    94%  12.6 | 164.4    96%  11.1 |   2%  107.9     0%
 0.30  1.50   100%  256.1    96%  12.6 | 177.0    98%  11.6 |   2%  110.2     0%
```

**The loop's contribution is now separable.** The rumor-only column depends on sensitivity
alone, as it must, since with the loop off `observationWeight` does nothing. Reading down a
sensitivity column shows what the feedback adds: at sensitivity 0.75, going from no loop to
observation 0.25 lifts the mean peak from 139 to 163, the burst rate from 66% to 88% and
the fall from 10.5 to 12.9 days.

**Candidate: observation 0.25, sensitivity 0.75.** Chosen for its neighbourhood rather than
its own number. It reads 72%, and all four adjacent cells stay in range: 54% below, 84%
above, 52% to one side and 86% to the other. The alternatives in the band sit next to cells
that jump to 100%: 0.20/1.25 at 68% has 0.25/1.25 at 100% beside it, and 0.20/1.50 at 76%
has 0.25/1.50 at 100%.

### Validation on seeds 1001-1100, never swept over

```
./gradlew :experiments:bubble --args="--seeds 100 --first-seed 1001 --ticks 200 \
    --observation 0.20,0.25,0.30 --sensitivity 0.5,0.75,1.0 --noise 0.030 --decay 0.86 \
    --csv build/e5-validate.csv"
```

```
                 rumor + feedback            rumor only               quiet
  obs  sens   half  peak$  burst  days |  peak$  burst  days |  held  peak$  burst
 0.20  0.50    25%  131.8    45%  10.5 | 125.1    22%   8.4 |   5%  107.5     0%
 0.20  0.75    38%  150.2    88%  12.0 | 136.2    65%   7.2 |   5%  107.6     0%
 0.20  1.00    59%  174.7    95%  13.1 | 147.3    85%   7.8 |   5%  107.8     0%

 0.25  0.50    40%  135.5    58%  11.6 | 125.1    22%   8.4 |   5%  107.5     0%
 0.25  0.75    65%  157.5    94%  13.5 | 136.2    65%   7.2 |   5%  107.7     0%
 0.25  1.00    82%  185.9    96%  13.5 | 147.3    85%   7.8 |   5%  107.8     0%

 0.30  0.50    57%  137.9    68%  12.8 | 125.1    22%   8.4 |   5%  107.5     0%
 0.30  0.75    87%  168.4    98%  13.4 | 136.2    65%   7.2 |   5%  107.9     0%
 0.30  1.00   100%  197.8    93%  12.7 | 147.3    85%   7.8 |   5%  108.1     1%
```

**The candidate holds.** On seeds it has never seen, 0.25/0.75 gives 65% against 72% on the
tuning seeds: lower, as out-of-sample results usually are, and still inside the 60-80% band.
Bursts run at 94% taking 13.5 days, and quiet villages produce a holder in 5% of seeds and
never a burst, against the 2% seen on the tuning seeds and the 5.7% the noise sweep was
aimed at.

The neighbourhood is wider out of sample than in: 38% and 87% either side on the
`observationWeight` axis rather than 52% and 86%. The cell is centred but the gradient along
that axis is steep, so this is a setting to re-validate rather than to treat as settled.

**Decision.** Adopted as defaults: `observationWeight` 0.25, `priceSensitivity` 0.75,
`fullMoveSize` 0.20, alongside E5a's `marketNoise` 0.030 and `noiseDecay` 0.86.

Adopted with a caveat that belongs on the record. The `observationWeight` axis is steep:
0.05 either way moves the half-believing rate by twenty points or more, and the
out-of-sample neighbourhood is wider than the in-sample one, 38% and 87% either side rather
than 52% and 86%. The setting is centred in the target band but not comfortably inside it,
so it wants re-validating after any change that touches how evidence is weighed, rather
than nudging. That is what `CalibrationTest` is for: it runs seeds 1001-1100 on every push
and fails if the with-rumor half-believing rate leaves 50-85% or any quiet village bursts.
The band is deliberately wider than the measured 65%, because a band tight enough to pin
today's figure would break on any deliberate retune while catching nothing extra.

---

## E6 — In how many worlds did the lie make the difference

A single counterfactual answers "what would this village have done without the lie". It
cannot answer "how likely was the lie to cause this", because one village is one roll of the
dice. So each village is run up to the tick before the lie, and then carried on many times
under different futures, in **pairs**: world 7 with the lie and world 7 without it are given
the same branch seed, so they face identical future randomness and the only difference
inside a pair is the lie.

Run over the hundred villages `CalibrationTest` uses, which no tuning sweep has touched.

```
./gradlew :experiments:worlds --args="--seeds 100 --first-seed 1001 --pairs 10 \
    --ticks 220 --told-at 41 --csv build/e6.csv"
```

```
Paired worlds over 100 villages (seeds 1001..1100), 10 pairs each, 220 ticks,
lie told on tick 41. That is 2000 worlds, run as 1000 pairs.

  pairs run:                          1000
  bubbled with the lie:               919 (91.9%)
  bubbled without it:                 0 (0.0%)
  the lie made the difference in:     919 (91.9%)
  mean peak price effect:             +51.8
  mean extra cost of a diamond a day: +782.5
  per-village share the lie caused:   p10 70%, median 100%, p90 100%
```

**The answer is 919 of 1000 paired worlds.** In each of those the price ran past 130 and
came back under 110 in the world where the lie was told, and did not in the world where it
was not. No world bubbled without the lie, which matches `CalibrationTest`: the wobble alone
produces a believer in about 5% of villages and has never produced a burst.

The per-village spread matters as much as the total. The median village is one where the lie
caused a bubble in every one of its ten worlds, but the bottom tenth are villages where it
worked only 70% of the time or less. The population answer is not the answer for any
particular village, which is the reason for running pairs rather than reporting one number.

**Why pairing, in one figure.** The mean peak price effect is +51.8 measured within pairs.
Measured between two unrelated piles of worlds the same effect would be buried under the
spread of peak prices across worlds, which runs from about 104 to 183 in the eight-pair
sample printed by the CLI. The differences are small next to the spread, so pairing is what
makes a thousand worlds enough instead of needing far more.

**Decision.** No parameters changed. This experiment measures the model rather than tuning
it. The figure to quote is "in 919 of 1000 paired worlds", not "the lie caused the crash".

---

## E7 — Village size, and a village somebody actually played

Two things every earlier entry took for granted. Every figure was counted out of twenty,
because the village was always twenty; and every figure came from simulated movement, which
mixes a village far more than real villagers do, since real ones cluster at workstations and
beds.

Three changes made this askable. Village size is now a parameter, and the in-game session
uses however many villagers it bound. Headline figures are shares rather than counts, so a
village of eight and one of thirty can be read side by side. And the experiment runner can
replay the `ObservedMeeting` inputs out of a saved session as its meeting schedule, so a
sweep can run against a real village's traces rather than the model's idea of one.

A trace is only ever run at its own size. Asking for a bigger village leaves the extra
villagers with nobody to meet; asking for a smaller one throws meetings away. Neither tells
you anything about the village that was played.

Running it turned up a fourth change. The market quorum was a count of five, chosen when
every village had twenty, where it meant a quarter of the village. In a village of five it
demanded everybody, and a played session of five villagers showed no price at all, ever: of
its eleven ticks, not one opened a market. The quorum is now a share of the village, 0.25,
which is exactly five at twenty villagers and so leaves everything calibrated there
untouched. That same eleven-tick session now prices every one of its ticks.

### Two rates, kept apart

A **bubble** is counted only if it began within 30 days of the lie: a long enough run
wanders into one eventually whether or not anybody lied, so "did one ever happen" says more
about the length of the run than about the lie.

For a quiet village, two separate rates per 100 days, because conflating them overstates the
case:

| measure | what it counts |
| --- | --- |
| **bubbles** | excursions past 130 that came back under 110, by the `Bubble` definition |
| **belief onsets** | a day when somebody holds the claim after a day when nobody did |

An onset is the mechanism firing, not a panic. Most come to nothing. One unbroken spell of
belief can also carry the price up and down more than once, so bubbles are not a subset of
onsets and the two cannot be ordered — what cannot happen is a bubble in a village where no
belief ever started.

```
./gradlew :experiments:sizes --args="--seeds 50 --sizes 8,12,20,30 --ticks 400 \
    --told-at 41 --window 30 --trace <a saved session> --csv build/e7.csv"
```

Replaying a session of 1499 meetings over 1413 ticks, naming 9 villagers.

```
                            with the lie           quiet village, per 100 days
  movement     size   bubbled   peak believers   bubbles   belief onsets   peak$
  simulated      8       86%              49%      0.00            0.16   153.1
  simulated     12       80%              43%      0.00            0.14   150.6
  simulated     20       62%              32%      0.00            0.12   142.2
  simulated     30       44%              32%      0.00            0.16   140.0
  simulated      9       84%              44%      0.00            0.20   151.5
  played         9       48%              36%      0.04            0.28   131.7
```

**The played row rests on one recorded session of 9 villagers, replayed under 50 seeds.**
Different seeds give those same bodies different personalities, but the meetings are the one
village that was played. This is a single village's evidence, not a sample of villages.

**At the size where the two are comparable, they differ more than the first reading
suggested.** Nine villagers, simulated against played: 84% bubbled against 48%, peak
believers 44% against 36%, peak price 152 against 132. The recorded village is markedly
harder to set off.

An earlier version of this entry read that gap as 62% against 48% and called the two
consistent. That was partly an artefact of the old quorum: at nine villagers a count of five
held the simulated market closed far more than it should have been, which flattered the
agreement. With the quorum scaled properly the simulated model bubbles a good deal more
readily than the village that was played. This still rests on one session, so it is
evidence about one recorded village rather than about real villages.

**The quiet village differs more clearly.** Beliefs start about four times as often on the
recorded trace, 0.28 onsets per 100 days against 0.06, and it is the only row that ever
bubbles on its own at all, at 0.04 per 100 days — roughly one unprompted bubble every 2,500
days. Real villagers cluster, so the same few keep meeting and a belief that forms has a
smaller pool to die out in. The simulated model never produced an unprompted bubble in any
of these runs.

**Bubbles get steadily harder as a village grows**, on the simulated model: 86% at eight,
80% at twelve, 62% at twenty, 44% at thirty, with peak believer share falling from 49% to
32%. A rumor has further to travel, and the median ask has more sellers to move. Village
size is the strongest single influence on the headline figure found so far, which is a good
reason for the in-game session to size itself to the village it binds.

**Decision.** The market quorum becomes a share of the village, 0.25, replacing the count of
five. At twenty villagers it is still five, so nothing calibrated there moves and E1 to E6
stand; below twenty it is the difference between a market and no market at all. No other
default changed.

The 20-villager calibration still sits inside its target band on this stricter measure — 62%
bubbling within 30 days of the lie, against the 60-80% target — so nothing forces a retune.
But the headline figure is far more sensitive to village size than to anything swept in E5,
and a real village of nine bubbles at 48% where the model says 84%. Tuning against recorded
villages rather than simulated movement is the obvious next question, and it needs more than
one session to answer.

---

## E8 — Does it matter who you tell, or only when

Telling two different villagers in one recorded village gave wildly different answers: Sela,
the most talkative villager there at a gossip trait of 1.00, convinced one villager of
fourteen, while Ivy at 0.55 convinced twelve. Read on its own that says personality does not
matter and timing does. It is also two runs, and the two faced different luck as well as
being different people, so it says nothing of the sort.

This takes each villager in turn as the one told, and runs each of them through the same set
of futures, forked from one shared history at the moment before the lie. World *w* uses the
same branch seed whoever is told, so the comparison between villagers is paired and the luck
cancels. That splits the variation in two:

- **within a planter** — the same villager told, across different futures: luck, meaning who
  happened to walk past whom
- **between planters** — the spread of each villager's average across all futures: the
  villager themself

```
./gradlew :experiments:planters --args="--villages 6 --worlds 40 --ticks 300 \
    --told-at 41 --csv build/e8.csv"
```

Six villages of twenty, each villager told in turn, forty futures each: 5,040 runs.
Outcome is the peak share of the village believing.

```
  village   spread within a planter   spread between planters   share explained by who
        1                    0.147                     0.115                     38%
        2                    0.208                     0.108                     21%
        3                    0.229                     0.171                     36%
        4                    0.129                     0.079                     27%
        5                    0.144                     0.124                     43%
        6                    0.144                     0.144                     50%

  mean spread within a planter (luck):        0.171
  mean spread between planters (who):         0.127
  share of variation explained by who is told: 35%
  gossip against a planter's average outcome:  r = 0.64 (n = 120)
```

Ranking all 120 planters by their gossip trait:

| | mean peak believer share |
| --- | --- |
| bottom quarter (gossip ≤ 0.31) | 14.5% |
| top quarter (gossip ≥ 0.80) | 47.7% |

**Luck is the larger factor, but who you tell is not noise.** Timing accounts for about 65%
of the variation and the choice of villager for about 35%, consistently across all six
villages, where the per-village figures run from 21% to 50%.

**The original reading was wrong.** It claimed personality did not predict the outcome. It
does: gossip correlates with a planter's average across futures at r = 0.64, and a
top-quarter talker convinces more than three times the share of the village that a
bottom-quarter one does. The Sela result was one draw of bad luck, not evidence about Sela.
Two runs could not have told the difference, which is the reason for running 5,040.

**Both things are true at once, and that is the interesting part.** Telling the village
gossip is worth roughly three times as much as telling a quiet villager, and it still fails
often: comparing a top-quarter planter one standard deviation below their own average
against a bottom-quarter planter one above theirs, the quiet villager wins 337 times out of
900. The best planter found averaged 77% of the village with a spread of ±16%; the worst
averaged 5%.

**Conclusion.** Neither dominates. Who you tell is a real and sizeable effect, worth about a
third of the variation and well predicted by the gossip trait. Luck is worth the other two
thirds. A claim that a lie's success is mostly down to chance is defensible; a claim that it
does not matter who you tell is not.

**Decision.** No parameters changed. This measures the model rather than tuning it. The
finding does not support building a week of work on "timing beats personality", because that
is not what the model does.

---

## E9 — A village with real spots, and nobody lying

The first session recorded with locations mapped to spots: a villager counts as being at
their bed, their workstation, or the village at large, rather than every meeting being
filed at the market. Ten villagers, 151 ticks, no rumor planted, so anything that happened
happened on its own.

Measured against the headless movement model at the same size and length, over 40 seeds.

### Meetings

| | per tick | per villager per tick |
| --- | --- | --- |
| played, before the mapping (14 villagers) | 1.87 | 0.268 |
| **played, with the mapping (10 villagers)** | **1.04** | **0.208** |
| headless model (10 villagers) | 2.60 | 0.520 |

The bed rule cost **22% of meetings per villager**, against an estimate of 25 to 35%. The
estimate was made in absolute terms for a fourteen-villager village, at 1.2 to 1.4 per tick;
the new per-villager rate scaled back to fourteen villagers would be 1.46, just above that
range. Close enough to have been useful, and wrong in the direction of over-stating the
loss.

**How much the rule filtered cannot be read from the trace.** A meeting that was filtered is
not recorded, so the log holds only what survived. What can be said is that 17 of 157
recorded meetings still name HOME, which are pairs where one villager was at a bed and the
other was not; pairs where both were in bed are the ones that went.

The spots are doing their job: 115 at the village at large, 25 at the market, 17 at home.
Before the mapping every single meeting was filed at the market.

### The market

| | played | headless |
| --- | --- | --- |
| first price | tick 73 | usually within a few ticks |
| ticks with a price | 14 of 151 (9%) | 41% |
| most villagers at the market at once | 3 | — |
| price range over the run | 99 to 104 | — |

**This is the finding.** The real village barely has a market. Its ten villagers reached the
quorum of three on 9% of ticks, and no more than three were ever standing in the market at
the same moment. The headless model, with the same ten villagers over the same 151 ticks,
prices 41% of them. Real villagers spread themselves across bed, workstation and the paths
between, while the movement model sends a quarter of the village to the market by
construction on every tick.

Before the mapping this was invisible, because every meeting was filed at the market and the
whole village therefore stood in it permanently. The market was open 99% of ticks in the
earlier sessions for that reason alone.

### Spontaneous belief

None. No villager formed a belief, no price observation was ever recorded, the price stayed
between 99 and 104, and there were no bubbles. The headless model at this size and length
also produced no holders and no bubbles across all 40 seeds.

The two agree, but for different reasons worth keeping apart. In the model the price simply
never wandered far enough to be worth reading anything into. In the played village the price
existed on only 9% of ticks, so there was hardly a price to read at all.

**Conclusion.** The spot mapping works, and it reveals that a real village is far quieter
than the model in the one place that matters most for prices. Gossip is 40% as frequent per
villager; the market is open a fifth as often. A rumor in this village would have fewer
chances to spread and far fewer chances to move a price, which is consistent with E7 finding
that a recorded village bubbled at 48% where the model said 84%, and suggests the gap is
mostly about market attendance rather than about gossip.

**Decision.** No parameters changed. Two questions follow, neither answerable from one
quiet session: whether the market quorum should be lower again for villages that gather as
loosely as this one, and whether `FIELDS` and the village at large should count toward the
market at all, given that a real villager standing on a path is not at home and not trading.
Both want a session with a rumor in it before being decided.

---

## E10 — A big village, six lies, and no bubble at all

Thirty-one villagers, 663 ticks, six rumors planted, spots mapped from beds and
workstations. The largest session recorded, and the one that shows what is now holding the
model back in a real village.

| | E9, quiet village of 10 | **E10, village of 31, six lies** | headless model |
| --- | --- | --- | --- |
| meetings per villager per tick | 0.208 | **0.655** | 0.520 |
| ticks with a price | 9% | **1%** | 41% |
| most villagers at the market at once | 3 | **8 of 31** | — |
| price range over the run | 99–104 | **97–103** | — |
| peak holders | 0 | **8 of 31 (26%)** | — |
| peak believers | 0 | **4 of 31 (13%)** | — |
| bubbles | 0 | **0** | — |
| price observations | 0 | **0** | — |

**Gossip is healthy. Better than the model, in fact.** At 0.655 meetings per villager per
tick this village talks 26% more than the headless movement model does, and a quarter of it
came to hold the claim. E9's worry that real villages gossip too little is answered: a
village of thirty-one, densely packed, out-talks the model comfortably.

**The market is where it fails.** The quorum at this size is eight, and eight is exactly the
most villagers ever standing in the market at one moment, reached on 5 ticks out of 663. The
price existed on 1% of ticks and never left the range 97 to 103.

**So the feedback loop never ran once.** Zero price observations in 663 ticks. A price has to
move more than 10% from what a villager last read into it before it is evidence of anything,
and this price never moved at all. Belief spread to eight villagers through gossip alone,
had nothing to feed it, and decayed away. That is the "it just faded" seen while playing,
and it is the model behaving exactly as written.

**The counterfactual is empty, and correctly so.** Removing all six lies gives a peak price
of 103 against 103, and an extra cost of 0. The lies changed what villagers believed and
changed nothing about the market, because the market was hardly there.

### What would open the market

Two candidate fixes, measured against this session's own trace.

Lowering the quorum, leaving market membership as it is — whoever was in the last meeting at
a market spot:

| quorum | ticks priced |
| --- | --- |
| 8 (today) | 1% |
| 6 | 11% |
| 5 | 17% |
| 4 | 24% |
| 3 | 26% |
| 2 | 30% |

A rolling window instead, where the market is everyone seen at a market spot in the last N
ticks, keeping the quorum at eight:

| window | ticks priced |
| --- | --- |
| 1 tick | 0% |
| 4 ticks (1 day) | 2% |
| 8 ticks (2 days) | 8% |
| 16 ticks (4 days) | 17% |
| 32 ticks (8 days) | 31% |

Under an eight-tick window the market averages 5.9 sellers whenever it has any.

**Decision.** Nothing changed. The argument between the two is recorded with the proposal
rather than here; what this entry establishes is that market attendance, not gossip, is what
stops a real village from bubbling, and that either fix has to lift the priced share from 1%
to something in the twenties before the loop can run at all.

---

## E11 — Measuring the market instead of inferring it, and how long to remember it

E10 left the market as the thing stopping a real village from bubbling: thirty-one
villagers, a quorum of eight, and the market open on 1% of ticks. Two changes were built for
it.

**Sightings.** The plugin now reports where every villager is standing every tick, as
`VillagerSeen` inputs, rather than leaving their whereabouts to be inferred from who they
were last talking to. A villager alone at a stall used to be invisible. Only changes reach
the log, so a villager who has not moved costs nothing.

**A window.** `marketWindowTicks` gives a villager who has left the market a little grace
before they stop counting as a trader, so a market read from one instant becomes a sample.

### Sweeping the window

Against the recorded village of thirty-one, whose trace predates sightings and so still
infers position from meetings:

| window | ticks priced | peak price | peak believers | bubbles |
| --- | --- | --- | --- | --- |
| 0 | 1% | 103 | 4 | 0 |
| 4 | 3% | 104 | 4 | 0 |
| 8 | 8% | 105 | 4 | 0 |
| 16 | 14% | 105 | 4 | 0 |
| 32 | 27% | 108 | 4 | 0 |

Against the headless model, 20 villagers, 30 seeds, a lie on tick 41:

| window | ticks priced | peak price | peak believers | bubbled within 30 days |
| --- | --- | --- | --- | --- |
| 0 | 44% | 160.3 | 53% | 87% |
| 2 | 97% | 160.7 | 56% | 93% |
| 4 | 100% | 165.6 | 58% | 93% |
| 8 | 100% | 159.6 | 54% | 90% |
| 32 | 100% | 159.6 | 52% | 90% |

And what each window does to the calibration, on the hundred seeds the sweeps never touch:

| window | half-believing | quiet villages bursting | mean market size, of 20 |
| --- | --- | --- | --- |
| 0 | 63% | 0% | 8.2 |
| 2 | 65% | 1% | 11.4 |
| 4 | 64% | 0% | 15.0 |
| 8 | 64% | 0% | 18.3 |
| 16 | 59% | 0% | 19.6 |

### What moved

**The calibration barely notices.** Half-believing stays between 59% and 65% at every
window, well inside the 50-85% the regression check allows, and quiet villages still almost
never burst. Whatever else the window does, it does not retune the model.

**The window opens the market by filling it with the whole village.** That last column is
the reason not to use one. At a window of eight the market averages 18.3 villagers out of
20, and at sixteen it is 19.6: the market stops being a place some villagers are and becomes
the village. That is precisely the degenerate state E9 was written to remove, arrived at by
a different road. In the headless model it also props the market open on 100% of ticks,
including at night, which a market should not be.

**And on the real village it did not help anyway.** Every window from 0 to 32 leaves the
peak price at 103 to 108, peak believers at 4, and no bubble at all. Widening the window
adds sellers who believe nothing, so it opens the market and dilutes the believers inside it
in the same motion. Coverage rose from 1% to 27% and bought nothing.

**Conclusion: the window was the wrong fix, and building it was how that became clear.**
The market being shut was a symptom of not knowing where anybody was, and the answer to
that is to look, which is what sightings do. `marketWindowTicks` defaults to 0, leaving
every earlier experiment and the calibration exactly as they were; the parameter stays
because it costs nothing and a future session may yet show a use for it.

**Decision.** No defaults changed: `marketWindowTicks` is 0, which is the behaviour of every
run before it existed. The open question is whether sightings alone lift a real village's
market, and that cannot be answered from traces recorded before sightings existed. It needs
one session recorded with the current plugin.

A bug worth recording, since the first sweep reported 100% of ticks priced at every window
and it was wrong. A villager's last visit to the market started at `Long.MIN_VALUE`, and
`tick - Long.MIN_VALUE` overflows to a negative number, which compares as "just now" — so
every villager who had never once been to the market counted as permanently standing in it.
Guarded now, with a test that a villager never seen anywhere is not in the market whatever
the window.

---

## E12 — The rumor never leaves the people you told

The first session recorded with sightings: 26 villagers, 617 ticks, three rumors planted,
where every villager's position is reported every tick rather than inferred from who they
were talking to. It settles what was still open in E11, and finds something worse.

### Where villagers actually are

| spot | villager-ticks | share |
| --- | --- | --- |
| the village at large | 9,889 | 62% |
| home | 5,239 | 33% |
| **at a workstation** | **888** | **5%** |

Mean villagers in the market at any tick: **1.3 of 26**. The most ever there at once was 7,
which is exactly the quorum, reached on 3 ticks out of 617. The price existed on 3 ticks and
ranged 103 to 105.

**So sightings did not open the market, and that answers E11's open question.** The market
was not shut because we could not see who was in it. It was shut because almost nobody is in
it: a Minecraft villager spends about a twentieth of their life within three blocks of their
workstation, and two thirds of it walking about the village.

### The rumor never gets past the first hop

| | tellings by the villagers who were told | tellings by anyone else |
| --- | --- | --- |
| **played** | **31** | **0** |
| headless model, same size and length | 20 | 137 (87%) |

Every single telling in the played session came from one of the three villagers the player
spoke to. Nobody who heard it second hand ever passed it on. In the model at the same size
and length, 87% of telling is second hand — that is what spreading means.

The chain is one thing causing the next. The market never opens, so the price never moves,
so no villager ever reads anything into it: zero price observations in 617 ticks. Second-hand
belief arrives at roughly credulity times one, decays 8% a day, and with nothing to reinforce
it falls under the 0.4 telling threshold before its holder happens to be standing next to
somebody. Belief reached 8 villagers and 3 believers, then went to nothing: holders on each
of the last six days were 0, which is why no text was left above any head.

**"Workers spread it better" is not what happened.** Time spent at a workstation correlates
with meetings at r = 0.14, which is nothing, and the villager who told the most, at 12
tellings, spent no ticks at a workstation at all. All three of the biggest tellers were
simply the three the player told.

**Conclusion.** Gossip is not the problem and never was: this village meets plenty. The
market is the problem, and sightings have now ruled out the explanation that it was a
measurement artefact. Three blocks from a workstation is not where Minecraft villagers
spend their time, so a market defined that way is empty, and a market that is empty breaks
the feedback loop that the whole model rests on.

**Decision.** Nothing changed. The mapping from where a villager stands to what spot that
makes them is the thing to fix, and it cannot be swept from these traces, because a trace
records the spot that was decided and not the position it was decided from.

---

## E13 — Sweeping the rule that decides where a villager is

The first sweep of the one number that had never been swept. A survey records where every
villager stood and how far they were from their bed and their workstation, so a played
session's entire input stream can be built again under a different rule and run: who was
near enough to whom to talk, and what spot each of them counted as.

One session: 14 villagers, 449 ticks, 5,893 sightings, two rumors planted. Talking range
held at 6 blocks throughout; what varies is how near a villager must be to their bed or
workstation to count as being there, and whether a villager out walking the village counts
as somewhere to trade.

```
  range  wandering counts   meetings/tick   at market   ticks priced   peak$   believers   bubbles
      3                 no            2.47         4.9            21%     107           2         0
      3                yes            2.47        11.0            82%     130           2         0
      6                 no            2.46         6.3            35%     108           2         0
      6                yes            2.46        11.0            76%     122           2         0
     10                 no            2.43         6.2            39%     129           1         0
     10                yes            2.43        10.5            76%     122           2         0
     16                 no            2.39         6.3            43%     129           2         0
     16                yes            2.39        10.1            74%     122           1         0
     24                 no            2.35         6.2            43%     129           6         0
     24                yes            2.35         9.3            73%     142           6         2
```

**Widening the range is the fix; counting wandering as trade is not.** Going from 3 blocks
to 10 doubles the share of ticks with a price, 21% to 39%, and lifts the peak from 107 to
129. It does that by taking the market from 4.9 villagers to 6.2 of 14, which is 44% of the
village — almost exactly the share the headless model puts in its market by construction.
Counting the village at large as a marketplace also opens the market, to 73-82% of ticks,
but it does so by putting 9 to 11 of 14 villagers in it, 66% to 79% of the village. That is
the degenerate market of E9 and E11 arrived at for the third time, and it should be refused
for the third time.

**Three blocks was simply too tight.** E12 measured villagers spending 5% of their time at a
workstation and read it as a fact about Minecraft villagers. It was mostly a fact about the
number three: at ten blocks the same villagers are in the market 44% of the time.

**It is necessary and might not be sufficient.** The peak price settles at 129 across
ranges 10 to 24 with wandering off, and a bubble needs 130. No cell without wandering
produced one. The market now opens; the price still barely reaches the level that counts as
a bubble, and the remaining gap is a small village with few believers rather than an empty
market.

**This rests on one session with two lies.** Believers move between 1 and 6 across cells
that differ little otherwise, which is more noise than signal at this sample size. The
direction is clear and the size of the effect is not.

**Decision.** `SpotMapper.AT_A_PLACE` raised from 3 to 10, the smallest range that reaches
the market attendance the model was built around. Wandering stays out of the market. This
changes nothing headless, where movement was never mapped from positions; it changes every
in-game session.

A bug worth recording, because it nearly cost the session. The survey was written with
`String.format` and no locale, and the server's JVM writes decimals with a comma, so every
number split into two columns and the file was unreadable: `139,46` where `139.46` was
meant. Every row held exactly 13 fields rather than 8, which made it repairable without
loss, and the sweep above ran on the repaired file. Every file-writing format call in the
project is now pinned to `Locale.ROOT`, with a test that writes a survey under a
comma-decimal locale and reads it back.

---

## E14 — The wider range worked; the village is the problem now

First session recorded at a range of ten blocks. 18 villagers, 544 ticks, one rumor.

**The market fix did what E13 said it would.** Prices on 191 of 544 ticks, 35%, against 1%
in E12 and 21% at three blocks in E13's sweep. The price moved for the first time in an
in-game session, ranging 92 to 108 rather than sitting at 103 to 105.

**And the rumor still went nowhere.** Nine tellings in 544 ticks, peaking at four villagers
holding it and one believing it, then fading. Zero price observations.

| | played | headless, same size and length |
| --- | --- | --- |
| meetings per villager per tick | 0.237 | 0.570 |
| tellings | 9 | 143 |
| price observations | 0 | 197 |
| peak holders | 4 of 18 | 18 of 18 |
| peak price | 108 | 165 |

### Why

The survey says what kind of village this was:

| | count, of 18 |
| --- | --- |
| villagers with no workstation | 13 |
| villagers with no bed | 10 |
| **villagers with neither** | **9** |

Half the village has nowhere to work and nowhere to sleep, so half the village does nothing
but wander. They never gather, which is why this village meets at 0.237 per villager per
tick against the model's 0.570 — less than half — where E10's dense village of 31 managed
0.655 and out-talked the model.

From there the chain is arithmetic. Few meetings give few tellings, nine of them. Few
tellings give one believer. One believer cannot move a median: they ask 175 at full
confidence, but the market averages 2.5 sellers and the rest ask 100, so the middle of the
three is 100. The price therefore wanders between 92 and 108, and an observation needs it
past 111 or under 90. It crossed neither line once in 544 ticks, so nothing was ever
reinforced, and belief decayed exactly as it should.

**This is not a parameter.** Every previous session pointed at something in the model — the
quorum, the window, the mapping range — and each of those turned out to be real and was
fixed. This one points at the village. Villagers with no bed and no workstation are not a
village that a model of village gossip can say anything about, and no setting will make
them behave like one.

**Decision.** Nothing changed. What this needs is a village with beds and workstations for
its villagers, not another sweep. E10 already showed what a dense village does to the
gossip side: 0.655 meetings per villager per tick, better than the model. Put that village
together with the ten-block range and the market that now opens on a third of ticks, and
the loop has everything it needs for the first time.

---

## E15 — The village died

A dense village with beds and workstations in every house, 20 villagers, 573 ticks, two
rumors. It read as another flat session: 5 tellings, 2 believers, prices on 26% of ticks
ranging 92 to 105, no observations, no bubble.

The survey says what actually happened.

```
villagers visible per tick, of 20:
  tick   1: 20      tick 201: 9
  tick 101: 12      tick 451: 5
```

Fifteen of the twenty stopped being there, one after another, and none came back. Five
villagers were present for all 573 ticks; the rest were visible for between 59 and 427 of
them. Fifty days were sprinted, which is fifty nights of hostile mobs, and a village without
the lighting and walls to survive them loses villagers steadily.

So the meeting rate of 0.178 per villager per tick, the lowest yet recorded, was measured
against a village that was mostly no longer there. Infrastructure was not the problem this
time: 15 of 20 villagers had both a bed and a workstation, against 9 of 18 with neither in
E14.

**The plugin was silent about it.** A bound villager who has died is skipped, along with one
standing in an unloaded chunk, and the simulation goes on counting twenty minds for five
bodies. That is the same phantom-villager error E7 fixed at binding time, arriving by a
different route: not a village smaller than the model thought, but a village shrinking while
the model was not looking.

**Decision.** No model change. The plugin now warns, in the log and on screen, whenever the
number of bound villagers still present falls, and `/hearsay status` says so plainly when
some are missing. MANUAL_TESTS.md gained an instruction to set the difficulty to peaceful
before recording: hostile mobs empty a village faster than a rumor can cross it, and no
amount of tuning distinguishes a villager who is quiet from one who is dead.

---

## E16 — Villagers only ever talk to their nearest neighbour

A healthy village at last: 21 villagers, peaceful, 472 ticks, every one of them present on
every single tick. It gossips better than the model — 0.598 meetings per villager per tick
against 0.570 — the market opens on 33% of ticks, and the rumor still went nowhere. Eleven
tellings, four holders, one believer, no observations, price 93 to 108.

Everything previously blamed is ruled out. The village is intact, it has beds and
workstations, it meets more than the model does, its meeting network is as broad as the
model's (18.4 distinct partners each against 20.0), and the villager who was told gets more
chances than the model's does: 40 meetings in the first 40 ticks against 24.

### Where it breaks

| | told the biggest talker, played | headless, same size |
| --- | --- | --- |
| tellings by the villager who was told | 21 | 22 |
| tellings by anybody else | **8** | **181** |
| distinct villagers who heard it first-hand | **6** | 10 |
| median confidence on first hearing | **0.36** | 0.45 |
| of those, above the 0.4 telling threshold | 2 | 6 of 10 |

The villager who is told does the same amount of telling in both. What differs is everything
after: 8 second-hand tellings against 181.

Twenty-one tellings reached only six distinct people. `ProximityPairing` gives each villager
their *nearest* free partner, and in a real village the nearest person is the same person
tick after tick: the busiest pairs in this session met 126, 119, 70 and 65 times. The
headless model shuffles whoever is at a spot before pairing them, so partners rotate.

So the rumor is told over and over to the same handful, while the teller's own confidence
decays. By the time it reaches somebody new, it lands at 0.36 — under the threshold to be
passed on — where the model's first hearings land at 0.45.

### Rebuilding the same session with partners that rotate

Same positions, same spots, same everything but who pairs with whom among those in range.

| pairing | meetings | tellings | holders | peak price | observations |
| --- | --- | --- | --- | --- | --- |
| nearest | 2962 | 39 | 11 | 112 | 17 |
| rotating, seed 1 | 2949 | 42 | 12 | 112 | 18 |
| rotating, seed 2 | 2945 | 76 | 19 | **138** | **205** |
| rotating, seed 3 | 2953 | 107 | 19 | **164** | 155 |

Two of the three rotations light the feedback loop that has never once run in a played
session. The number of meetings barely moves; who they are between is what matters.

**Decision.** Adopted. `ProximityPairing` now shuffles those within range, seeded by the
caller, and the plugin reseeds every tick from the session seed. A run still reproduces from
its recipe, since meetings are recorded as inputs either way.

Measured again through the real code afterwards, separating the pairing from who was told:

| planter | pairing | tellings | heard by | holders | peak price | observations |
| --- | --- | --- | --- | --- | --- | --- |
| Mira, gossip 0.45 | nearest | 4 | 3 | 3 | 108 | 0 |
| Mira, gossip 0.45 | rotating | 8-9 | 2-4 | 3-4 | 108 | 0 |
| Lark, gossip 0.96 | nearest | 40 | 8 | 9 | 128 | 14 |
| Lark, gossip 0.96 | rotating | 35-50 | 7-13 | 11-18 | 112-147 | 19-152 |

Rotating roughly doubles the telling and widens who hears it, and it does not rescue a
quiet planter: Mira never gets past one believer however the pairing falls. The session that
prompted all this had been planted in Mira, because the plugin plants in whoever is nearest
and there is no way to tell a talker from a quiet villager by looking at them.

So a second change, which E8 already argued for without anyone acting on it: `/hearsay who`
lists the bound villagers by how much they talk, and `/hearsay rumor` now says how talkative
the villager you told is and warns when they are not. Who you tell is worth about a third of
whether a rumor takes hold, and until now the player had no way to influence it.

---

## E17 — Four short of a bubble

The first played session where the rumor genuinely spread. 19 villagers, all present
throughout, four rumors planted in villagers chosen for how much they talk.

| | E16's session | **this one** |
| --- | --- | --- |
| tellings | 11 | **76** |
| villagers holding the claim | 4 of 21 | **13 of 19** |
| believers, at 0.5 or above | 1 | 3 |
| price range | 93–108 | 93–107 |
| price observations | 0 | **0** |

Rotating partners and telling talkative villagers did what E16 said they would: telling went
up sevenfold and two thirds of the village came to hold the claim. The price still did not
move, and the loop still did not run.

### Why, exactly

At the moment of peak belief, the market held 12 sellers of 19. Six of them held the claim,
at a median confidence of 0.19, which makes an ask of 114. The other six asked the base
price of 100.

The median of six asks at 100 and six at 114 is 107.

An observation needs 111. The session's highest price was 107. **The market was one holder
short of moving the median from 107 to 114, which would have crossed the threshold and
started the feedback that everything else depends on.**

That is not a model that is broken. It is a model sitting exactly on a knife edge, which
usually means a number is mis-scaled rather than a mechanism being wrong.

### The candidate

`observationThreshold` is the last market parameter never swept. It was set to 0.10 in the
week 5 design and has survived every sweep since because no sweep ever varied it: E5 swept
`observationWeight` and `fullMoveSize` around it, E11 the window, E13 the mapping range.

Three ways off the knife edge, in order of how much evidence stands behind them:

- **Lower `observationThreshold`.** At 107 against a threshold of 111, a threshold of 5%
  would have fired. The risk is that quiet villages become jumpy, which is exactly what the
  E5a noise sweep measured and can measure again.
- **Raise `priceSensitivity`**, so a holder at 0.19 asks more than 114. This moves every
  price in the model, headless included, and would invalidate the calibration.
- **Shrink the market**, so holders are the median sooner. The market is 64% of this
  village at a ten-block range, against the 44% E13 measured on a different one and the
  ~41% the headless model assumes.

**Decision.** Nothing changed. Proposed: sweep `observationThreshold` against the recorded
sessions and the headless model together, reporting the spontaneous-panic rate alongside,
since the whole point of the threshold is to stop a village reading meaning into noise.

Also fixed here: `/hearsay who` crashed with "No villager with id 0" when run in the first
ten seconds after binding. Villagers are created by the first tick, like every other change,
and the first tick was scheduled ten seconds out, so for those ten seconds there were bodies
with nobody in them. The first tick now runs immediately, and every command that names a
villager says the village is still waking up rather than throwing.

---

## E18 — The threshold sweep, and what it ruled out

`observationThreshold` was the last market parameter never swept. Swept against two played
sessions and the headless model together, with the spontaneous side reported beside the
bubble side.

Played sessions, replayed with only the threshold changed:

| threshold | session A peak$ / observations | session B peak$ / observations |
| --- | --- | --- |
| 0.15 | 107 / 0 | 108 / 0 |
| 0.10 (today) | 107 / 0 | 108 / 0 |
| 0.07 | 107 / 0 | 113 / 14 |
| 0.05 | 130 / 326 | 134 / 371 |
| 0.03 | 132 / 645 | 119 / 616 |

And what that costs, on the hundred calibration seeds:

| threshold | half-believing | quiet villages bursting | quiet villages forming any belief |
| --- | --- | --- | --- |
| 0.10 | 63% | 0% | **5%** |
| 0.07 | 68% | 0% | 78% |
| 0.05 | 63% | 2% | 100% |
| 0.03 | 68% | 0% | 100% |

**Lowering it works by making noise meaningful, which is the opposite of the point.**
Holding the threshold at 0.05 and quietening the market instead kills it again: at a noise
of 0.020 the same played session drops back to 105 and no observations at all. The threshold
only helps because the wobble is then large enough to cross it, and a village that reads
meaning into its own wobble reads it with or without a lie — the quiet column says exactly
that, going from 5% to 100%.

**Raising `priceSensitivity` instead does almost nothing in-game and wrecks the model.**
From 0.75 to 1.50 the played sessions move 107 to 109, while headless half-believing goes
65% to 100%. It raises what holders ask and does not change the median, because the median
seller is not a holder.

**Counting the village at large as the market is now refused with evidence rather than
taste.** Rebuilding one session both ways:

| market is | with the five lies | with no lie at all |
| --- | --- | --- |
| workstations only | peak 137, 115 observations, 1 bubble | peak 109, 0 observations, 0 bubbles |
| the whole village | peak 135, 250 observations, 1 bubble | **peak 135, 259 observations, 1 bubble** |

A village-wide market produces the same bubble whether or not anybody lied. That is the
claim of the whole project, gone.

**An unexplained discrepancy, recorded rather than smoothed over.** The rebuild of session A
under its own rule reaches 137 with a bubble, where the session as recorded reached 107 with
nothing. The pairing is not the cause: rebuilt meetings match the recorded ones exactly,
3840 of 3840, which also confirms the rotation from E16 was live. The rebuild produces
slightly more meetings than were recorded, and at this operating point that is apparently
enough to flip the outcome. Something between 107 and 137 turns on a difference of a few
dozen meetings in four thousand.

**Decision.** Nothing changed. The threshold stays at 0.10, the market stays at
workstations, sensitivity stays at 0.75. What this sweep establishes is that the in-game
model sits on a knife edge where the loop either just fails to start or just does, and that
the two obvious ways to push it over both work by letting noise do what the lie is supposed
to do. The next thing to understand is the discrepancy above, because a model this sensitive
to a handful of meetings is not one to tune further until it is understood.

---

## E19 — The rebuild was wrong, and the median was the wall

**Correction to E18.** The unexplained 137-against-107 gap was my analysis, not the
simulation. The rebuild scripts wrote a `VillagerSeen` only when a villager's surveyed spot
*changed*, on the reasoning that an unchanged sighting is a no-op. It is not. An
`ObservedMeeting` moves both participants to the meeting spot unconditionally, so a villager
who meets someone at a stall is left standing at the market in the world state. The plugin
reports every villager every tick, which puts them back the moment they are seen elsewhere.
Thinning those repeats away left villagers stranded wherever they last spoke to anyone, and
a market quietly filled up with people who had walked off. Same inputs, repeats removed:
peak 107 with no observations becomes peak 137 with 115 observations and a bubble.

Everything reconstructed from the survey now reproduces the session exactly: 3840 of 3840
meetings, and 13318 of 13319 spots, the one exception being a villager at 10.0 blocks from
a workstation where the recorded distance rounds the other way. That one flip changes
nothing. **The session's real answer is 107.**

Redone from the recorded inputs, with nothing rebuilt:

| session | market is | with the lies | with no lie at all |
| --- | --- | --- | --- |
| 701 ticks, 5 lies, 19 villagers | workstations only | peak 107, 0 obs | **peak 107, 0 obs** |
| | the whole village | peak 134, 165 obs, 2 bubbles | peak 134, 197 obs, 1 bubble |
| 472 ticks, 1 lie, 21 villagers | workstations only | peak 108, 0 obs | **peak 108, 0 obs** |
| | the whole village | peak 135, 131 obs, 1 bubble | peak 135, 131 obs, 1 bubble |

The village-wide market is rejected again and more cleanly than in E18: identical prices
with and without the lie, to the point. But the left-hand column is the finding. **The lie
changes the price by nothing at all.** Not weakly, not marginally — the same 107, the same
zero observations, whether five people were lied to or nobody was.

**Why, and it is not a tuning problem.** The price is the median ask of whoever is standing
in the market. In the best session 13 of 19 villagers ended up holding the rumor, but the
market at any moment is about 12 people and roughly half of them hold it. A median is
decided by the villager in the middle, and the villager in the middle is a non-believer
asking the base price. Belief can reach nearly everyone in the village and still move the
median by zero, because the median does not count how strongly anyone feels, only which
side of the middle they fall.

Every fix tried since E13 — the quorum as a fraction, the wider radius, the rotation, the
sightings, the threshold — was downstream of this. They all changed how many villagers
believe. None of them could change a median that was never going to move until believers
were a majority of the people standing in the market at once.

**Decision.** Nothing changed; this is a change to the price rule and wants proposing first.
Defaults untouched.

---

## E20 — Median, mean or trimmed mean, decided by measurement

E19 found that the price rule, not the tuning, is what kept the lie from moving the price
in-game. Three candidates, measured behind a temporary seam that was reverted afterwards;
nothing in this entry is implemented.

**The argument before the measurement.** `askingPrice` clamps belief to [-1, 1], so every
ask sits between 25 and 175 at today's sensitivity. Asks are bounded by construction: there
is no heavy tail and no villager who can drag anything. The median's stated justification,
that one extreme villager cannot move the market, defends against something the clamp had
already made impossible. Against that, the median's cost is total — its sensitivity to any
one villager's ask is exactly zero until believers pass half the market.

The trimmed mean drops the highest and lowest ask. That predicts a dead zone at the *onset*:
with one believer among twelve, the believer **is** the maximum, so trimming discards
precisely the signal. It trades the median's dead zone in the middle for one at the start,
which is the phase that has to start the loop.

**The measurement.** Headless seeds 1001-1100 as `CalibrationTest` measures them, and both
played sessions run with and without their lies:

| rule | half-believing | lie bubbles | quiet bursts | quiet beliefs | session A: lie / no lie | obs | session B: lie / no lie | obs |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| median | 63% | 92% | 0% | 5% | **107 / 107** | 0 | **108 / 108** | 0 |
| **mean** | 62% | 88% | 0% | 5% | **133 / 107** | 178 | **124 / 108** | 154 |
| trimmed | 61% | 93% | 0% | 5% | 127 / 107 | 163 | **109 / 108** | 0 |

The onset prediction holds exactly. The trimmed mean works in session A, where five lies had
been planted and believers were many, and does nothing at all in session B, where one lie
left believers few — 109 against 108, a single point. The mean separates the lie from the
absence of it in both.

Paired worlds on held-back seeds, to check the rule does not cost the counterfactual:

| rule | lie moved the price | median gap | bubble only with the lie | bubble only without |
| --- | --- | --- | --- | --- |
| median | 200/200 | +54 | 182 | 0 |
| mean | 200/200 | +56 | 185 | 0 |
| trimmed | 200/200 | +57 | 184 | 0 |

**Why the bug was invisible headless for five weeks.** All three rules score the same in the
model, because there a planted rumor reaches most of the village and a majority does move a
median. In-game belief reaches about half, and the market is a small wandering subset of
that, so the median never crosses. The headless model was never able to see this defect, and
no amount of seed sweeping would have found it. Only the played sessions did.

**The full suite passes under the mean unchanged** — 145 tests including `CalibrationTest`,
the drift test, resume and the counterfactuals. The quiet column is identical under every
rule, because with nobody believing, all asks are equal and mean, median and trimmed mean
are the same number. The change costs nothing where nothing is believed.

**Recommendation: the mean.** It is the only rule that answers the question the project
asks — does the lie move the price — in both played sessions, it needs no retuning, and the
robustness the median was bought for was never at risk.

**Decision. Adopted.** The price is the average ask from now on. `CalibrationTest` and the
whole suite pass unchanged, and `MarketTest.aMinorityOfBelieversStillMovesThePrice` pins the
behaviour: eight villagers in the market, two of them certain of the worst, and the price has
to read 119 rather than 100. It fails under the median, which is how it was checked.

Old logs replay exactly as before, because `MarketPriceSet` stores the settled price rather
than recomputing it. Old *recipes* will now re-derive different prices, which is the point.

---

## E21 — The ceiling is confidence, and the band and the village disagree

First session played under the average-ask rule: 21 villagers, 5 lies, 632 ticks, peak 121.

**The market is fixed.** At the peak, 13 villagers were standing in the market and all 13 of
them believed. The thing every experiment from E13 to E19 was chasing is no longer the
problem, and the rule change did what E20 said it would.

**The ceiling now is how much they believe, not how many.** At the peak, 17 of 21 villagers
held the rumor, and their confidences were `0.92, 0.29, 0.17` and then **thirteen at 0.13**.
An ask at 0.13 is 110, so a village that has entirely fallen for the lie still prices it at
about 115. Coverage is total; conviction is nil.

**Why it never compounds.** First hearing lands a belief near the tell threshold of 0.40,
and `dailyDecay` 0.92 carries it under within about two days. Below the threshold it is
never told again, so it settles just above `forgetThreshold` and sits there. Across the
whole session only ever **0 or 1 villagers at a time** were above the threshold, and 48 of
the 57 tellings came from the three villagers the player lied to directly. The rumor is not
an epidemic; it is a broadcast, and the player is the transmitter. That is exactly what
"I still needed to lie a couple of times" feels like from inside the game.

**200 was never reachable.** The hard cap is `basePrice * (1 + priceSensitivity)` = **175**,
since `askingPrice` clamps belief to [-1, 1]. 150 needs the average villager in the market
at two thirds of total conviction.

**The model cannot see any of this.** Headless on the calibration seeds it reaches a peak of
**159 median and 178 at p90**, and identically at 50 days and 158 days. Meeting density is
the same in both worlds (5.7 per tick in the model, 5.5 played) and so is social mixing
(the played village now covers 81% and 99% of all possible pairs, against the model's 100%).
The difference is purely that the model's beliefs sit well clear of the tell threshold, so
decay never touches them, while the village's hover on it and fall through.

Sweeping the two knobs that govern that, against both played sessions and the seeds:

| tellThreshold | dailyDecay | session A peak / tellings | session B peak / tellings | half-believing | quiet bursts |
| --- | --- | --- | --- | --- | --- |
| 0.40 | 0.92 (today) | 121 / 57 | 133 / 77 | 62% | 0% |
| 0.40 | 0.95 | 131 / 83 | 133 / 119 | 96% | 0% |
| **0.30** | **0.95** | **170 / 287** | **170 / 395** | **98%** | **0%** |
| 0.30 | 0.92 | 122 / 93 | 152 / 270 | 76% | 0% |
| 0.25 | 0.92 | 121 / 92 | 144 / 308 | 85% | 0% |
| 0.20 | 0.95 | 140 / 803 | 142 / 422 | 99% | 0% |

`dailyDecay` is the binding constraint, not the threshold: every row at 0.92 leaves session A
at about 121 whatever the threshold is, and every row at 0.95 moves it. Lowering the
threshold alone buys more tellings and no price, because the price follows conviction and
telling a village of 0.13-believers more often still leaves them at 0.13.

**The conflict, stated plainly.** No setting satisfies both. What makes the played village
work puts headless half-believing at 96-99%, far outside the 50-85% band `CalibrationTest`
enforces. The band came from E5, fitted to the model alone, and the model is now the less
faithful of the two worlds — it cannot reproduce a village that has been played.

Worth separating the two guarantees the band is doing duty for. **A village nobody lied to
still never bursts: 0% in every row above, without exception.** That is the hard claim of
the project and it is untouched. The 50-85% band is the softer one, that not every rumor
takes over, and it is the only thing in conflict.

**Decision.** Nothing changed. Defaults stand at 0.40 and 0.92 pending a call on whether the
band or the village gives way.

---

## E22 — The worlds differ in mixing, not in any parameter

E21 ended with a proposed retune to 0.30/0.95. Rejected before adoption: it takes both
played sessions to about 170 against a hard cap of 175, trading a village that never takes
off for one that always saturates. This entry diagnoses the difference instead.

**Hypothesis.** Real villagers are clustered, so inside a belief's roughly two-day life above
the tell threshold they hear from few independent sources, while headless mixing delivers
confirmations fast enough to compound.

**Measured in both worlds.** A window of 8 ticks is two days. A telling counts as
independent when the teller is not already in the listener's belief chain, which is exactly
the case the evidence rule gives full weight rather than `repeatWeight`.

| world | distinct partners per 2 days | independent share of tellings | second independent source before decay drops the belief under the threshold |
| --- | --- | --- | --- |
| model, mean of 10 seeds | **4.17** | 69-71% | **49%** |
| played, 21 villagers, 632 ticks | **2.12** | 45.6% | **13%** (2 of 16) |
| played, 19 villagers, 701 ticks | **2.45** | 61.0% | **5%** (1 of 20) |

**The hypothesis holds.** A real villager meets about half as many distinct people in the
window, a smaller share of what they hear is independent, and the compounding step — a
second independent confirmation arriving while the belief is still strong enough to matter —
happens **five to ten times less often**. Belief in the model compounds; belief in the
village decays before its second source arrives. That is the whole gap, and it is structural.

**No decay value bridges it.** Sweeping between the two endpoints rather than jumping:

| dailyDecay | session A peak | session B peak | half-believing | quiet bursts | headless peak median |
| --- | --- | --- | --- | --- | --- |
| 0.920 (today) | 121 | 133 | 62% | 0% | 159 |
| 0.925 | 121 | 128 | 71% | 0% | 165 |
| 0.930 | 122 | 134 | 79% | 0% | 169 |
| 0.935 | 122 | 130 | 89% | 0% | 172 |
| 0.940 | 136 | 131 | 93% | 0% | 176 |
| 0.945 | 131 | 128 | 96% | 0% | 178 |
| 0.950 | 131 | 133 | 96% | 0% | 180 |

The played columns are flat inside their own noise across the whole range, while headless
half-believing runs 62% to 96% and the headless median peak climbs to within four of the
cap. **Decay saturates the model without helping the village**, which is the retune rejected
above, arrived at from the other direction. There is no value to adopt here.

**Why no parameter could.** `pickSpot` is an independent weighted roll per villager per
tick, with no memory of where that villager was or who they are, and `holdMeetings` shuffles
everyone at a spot and pairs them uniformly. That is a perfectly mixed population by
construction. Real villagers have one fixed bed and one fixed workstation and shuttle
between them, so two villagers at the same end of the village meet constantly and two at
opposite ends essentially never do. Mixing is not a knob in the model; it is an assumption
baked into the movement rule.

### Proposal, not implemented

**Neighbourhoods, with mixing as the one new parameter.** At creation each villager is given
a neighbourhood from the movement stream. `holdMeetings` pairs within (spot, neighbourhood)
rather than within spot, and a new parameter `mixing` is the chance a villager is drawn into
the village-wide pool instead of their own neighbourhood. `mixing = 1` reproduces today's
behaviour exactly, so the change is a generalisation rather than a replacement and every
existing log still replays.

`mixing` is then fitted so the model's distinct-partners-per-two-days matches the traces,
about 2.1 to 2.5 rather than 4.17 — fitted against recorded surveys, not guessed. This is
trace-driven calibration used to fit one structural parameter, rather than to replace the
model, which matters because paired worlds and counterfactuals need a model that can run
without a recording.

Rejected alternatives: **stickiness toward a preferred workstation** raises repeat
encounters but barely lowers distinct partners, since everyone at the market still mixes
uniformly, and it is disjoint social circles that the traces show. **Replacing headless
calibration with the traces outright** overfits to two villages and leaves the counterfactual
machinery with no model to run.

**A caveat worth stating in advance.** Making the model mix like the village will lower
headless half-believing at today's parameters, because that is the mechanism being removed.
The 50-85% band would have to be re-derived on the faithful model. It is entirely possible
the honest outcome is that the model comes to agree with the village — that a rumor told to
a clustered population of twenty rarely compounds — in which case the in-game price ceiling
is a true property of a village this size, and a visible bubble needs a bigger or denser
village rather than a different number.

**Decision.** Nothing changed. Defaults stand at 0.40 and 0.92. The quiet-village guarantee
is untouched and measured at 0% in every row above.

---

## E23 — Neighbourhoods, fitted to the traces, and the model stops flattering itself

E22 found the two worlds differ in mixing rather than in any parameter. This builds the
neighbourhoods, fits them to a recorded session, validates on what was held back, and
re-derives the calibration band on the model that results.

**What was built.** Each villager is given a neighbourhood at creation. `holdMeetings` now
splits whoever is at a spot into a village-wide pool and one pool per neighbourhood, and
pairs within each. A new `mixing` parameter is the chance of landing in the village-wide
pool. Neighbourhoods are drawn from a **stream of their own**, appended last in
`RandomStream` so no other subsystem's ordinal moves, and the mixing roll is drawn from
that same stream, so the movement stream is untouched at any value of `mixing`.

The count is **derived from village size**, about one neighbourhood per three villagers,
rather than being a second knob. Seven villagers to a neighbourhood was tried first and
rejected on measurement, not taste: at that size the model floors at 2.9 distinct partners
even at `mixing = 0`, so the observed 2.12 was unreachable at any value.

**Mixing at 1.0 is the old village exactly**, confirmed rather than asserted: half-believing
62%, quiet bursts 0%, headless peak median 159, played sessions 121 and 133 — every figure
from E21 and E22 reproduced to the digit. `NeighbourhoodTest` pins the meeting count for
seed 42 at 1137 so a seed recorded before E23 cannot quietly stop replaying.

**The fit**, against session A alone, on distinct partners per two days only:

| mixing | partners per 2 days |
| --- | --- |
| 1.00 | 4.21 |
| 0.30 | 2.20 |
| **0.25** | **2.07** (played A: **2.12**) |
| 0.20 | 1.96 |

**The validation**, on what the fit never saw:

| | model | played | fitted on? |
| --- | --- | --- | --- |
| partners per 2 days, 21 villagers | 2.07 | 2.12 | yes |
| **second independent source in time, 21 villagers** | **11%** | **13%** | **no** |
| partners per 2 days, 19 villagers | 2.13 | 2.45 | no, other session |
| second independent source in time, 19 villagers | 16% | 5% | no, other session |

The held-out number is the one that matters, and it lands: 11% against 13%, from a fit that
only ever saw a partner count. The second session is looser — the model comes out more
clustered than it was and its confirmation rate sits above rather than below — but both are
in the right country, and both are a world away from the 49% the mixed model gave.

**A limitation, recorded rather than buried.** Clustering also cut total meetings roughly in
half, 1137 to 602 for the same seed, because a pool of three yields one pair and leaves
someone standing alone. Real villages do not lose meetings that way: they run at 5.5 per
tick, the mixed model at 5.7, and the clustered model now at 3.0. The model therefore
matches the *diversity* of real meetings and the confirmation rate that follows from it,
while under-producing meetings by about half. Letting the odd villager out of each
neighbourhood pair with other leftovers would fix the density, but it would also create
exactly the cross-neighbourhood ties this entry exists to remove, so it needs its own fit
rather than a patch. **This is the first thing to fix.**

### The re-derived band

On the faithful model, seeds 1001-1100:

| | mixed (before) | clustered (now) |
| --- | --- | --- |
| half the village believes | 62% | **1%** |
| the lie bursts the price | 92% | **39%** |
| peak price, median / p90 | 159 / 178 | **129 / 145** |
| second independent source in time | 49% | 15% |
| **a village nobody lied to bursts** | **0%** | **0%** |

`CalibrationTest` held half-believing between 50% and 85%. That band is not a
mis-measurement to be widened; it described a village that mixes perfectly, and no such
village exists. It is replaced by the rate at which the lie bursts the price, measured at
39% and held between 25% and 60%. **The quiet-village guarantee is untouched and still
measures 0%.**

The counterfactual survives, weaker and intact. On held-back seeds 2001-2200, the lie moved
the price in **200 of 200** paired worlds either way; the median gap falls from +56 to +20,
bubbles that happen only because of the lie from 185 to 81, and bubbles that happen only
without it stay at **0**.

**The model now agrees with the village.** Its median peak is 129 where the played sessions
reached 121 and 133. The ~120 ceiling is not a bug in the plugin, a mis-set parameter or a
measurement artefact. It is what a clustered village of twenty does, and five weeks of
sweeps could not see it because the model was flattering itself.

### Which bridge would break the ceiling

The diagnosis names its own answer. Belief dies because a villager's neighbours all share
their chain, so re-hearing carries `repeatWeight` rather than full weight, and an
independent second source rarely arrives in time. Anything that breaks the ceiling must
deliver **chain-independent evidence across neighbourhood lines**.

Exactly one thing in the model already does. `Belief.MARKET` is a source id no villager
holds, which "sits harmlessly in a chain and simply never matches a teller" — **the market
price is independent evidence by construction, and it reaches every villager standing in
the market whatever neighbourhood they came from.** It is the only bridge the village has.

A rolling market window was the obvious candidate and is **rejected on measurement**: across
0, 4, 8 and 16 ticks the clustered model's median peak goes 129, 128, 126, 126 and
half-believing stays at 1%. It makes the market a better *sample*; it does not make the
market a better *source*.

`observationWeight` does, and it is the lever with real authority — the parameter doc
already warns it moves half-believing by twenty points per 0.05. On the clustered model:

| observationWeight | the lie bursts the price | half-believing | peak median / p90 | **quiet villages burst** |
| --- | --- | --- | --- | --- |
| 0.25 (today) | 39% | 1% | 129 / 145 | **0%** |
| 0.30 | 58% | 7% | 132 / 149 | **0%** |
| **0.35** | **62%** | **53%** | **139 / 162** | **0%** |
| 0.40 | 65% | 95% | 153 / 175 | **0%** |

At 0.35 a clustered village behaves the way the mixed one used to, and **the quiet-village
guarantee holds at every value** — unlike every earlier attempt at this, which bought spread
by letting noise do the lie's work.

**Recommendation, not adopted:** `observationWeight` 0.35, validated against played sessions
before anything is changed. It is the bridge because it is the only source that crosses
neighbourhoods, and the numbers say it crosses them without inventing panics.

**Decision.** Adopted: neighbourhoods, `mixing` 0.25, one neighbourhood per three villagers,
and the re-derived band. Not adopted: the observation weight, pending played-session
validation and your call.

---

## E24 — Group conversations, and a validation that fails honestly

E23 fitted the clustering but halved the village's meetings, because a neighbourhood pool of
three yielded one pair and left somebody standing alone.

**What changed.** Neighbours at the same spot are now one conversation rather than a queue:
every pair in the group happens, so three at the well give three meetings. The village-wide
pool that `mixing` feeds is still paired off two by two, runs first, and keeps id order, so
a village at `mixing = 1.0` is untouched and every seed recorded before E23 still replays.
`VillageTest` gained the new invariant and lost the old one: the same *pair* may never be
recorded twice in a tick, but a villager now meets several people at once, which is what a
group is.

**The refit**, on session A alone, against both targets at once (2.12 partners per two days,
5.55 meetings per tick), 30 seeds:

| mixing | partners per 2 days | meetings per tick | combined error |
| --- | --- | --- | --- |
| 0.00 | 2.54 | 5.97 | 0.274 |
| 0.02 | 2.43 | 5.73 | 0.177 |
| 0.04 | 2.36 | 5.54 | 0.114 |
| **0.05** | **2.34** | **5.44** | **0.124** |
| 0.06 | 2.35 | 5.37 | 0.139 |
| 0.10 | 2.36 | 5.07 | 0.201 |

A shallow basin rather than a spike, so 0.05 is taken over the 0.04 minimum for having
stable neighbours on both sides.

**The validation, and it does not all pass.**

| | model | played | fitted on? |
| --- | --- | --- | --- |
| partners per 2 days, 21 villagers | 2.34 | 2.12 | yes |
| meetings per tick, 21 villagers | 5.44 | 5.55 | yes |
| partners per 2 days, 19 villagers | 2.57 | 2.45 | **no** |
| meetings per tick, 19 villagers | 5.40 | 5.48 | **no** |
| **second independent source in time, 21 villagers** | **30%** | **13%** | **no** |
| **second independent source in time, 19 villagers** | **38%** | **5%** | **no** |

Density and diversity now hold on the session that was never fitted, to within 5%. **The
confirmation rate does not, and it went the wrong way**: E23's pairing gave 11% against 13%,
and group conversations take it to 30%. Fixing the density broke the thing the density was
supposed to serve.

**The trade-off is structural, not a bad fit.** Sweeping neighbourhood size with groups on
(20 seeds, 21 villagers, targets 2.12 / 5.55 / 13%):

| villagers per neighbourhood | partners | meetings per tick | second source |
| --- | --- | --- | --- |
| 2 | 1.55-1.86 | 3.3-3.9 | **9-13%** |
| **3** | **2.37-2.61** | **4.9-6.1** | 27-45% |
| 4 | 3.3-3.5 | 6.7-8.6 | 38-51% |
| 5 | 4.1-4.5 | 8.5-11.1 | 41-54% |

Groups of two match the confirmation rate and lose the density; groups of three match the
density and lose the confirmation rate. **No size gives both**, because in this model the
same thing produces them: a bigger group is more meetings *and* more simultaneous sources.

**Why, and it is worth its own entry later.** Independence is judged by whether the teller
is in the listener's chain. In a group of three where A has told both B and C, C telling B
counts as a fully independent second source, though the information is A's and B knows it
already. Real neighbours are correlated in exactly this way, which is why a real village
confirms so rarely. **The model has no notion of correlated evidence, only of repeated
tellers**, and that — not the group size — is what the 30% against 13% is measuring. The
measurement in E22 shares the definition, so the played figures rest on 36 onsets across
both sessions and are themselves uncertain.

### The observation weight

Swept from 0.30 to 0.40, with the played sessions and paired worlds beside the seeds:

| observationWeight | bursts | **quiet bursts** | half-believing | peak med/p90 | session A | session B | paired: moved / only-lie / only-quiet |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 0.30 | 47% | **0%** | 5% | 131 / 149 | 143 | 149 | 2/2, 2, 0 |
| **0.32** | **55%** | **0%** | 22% | 133 / 153 | **138** | **138** | **2/2, 2, 0** |
| 0.34 | 60% | **0%** | 39% | 138 / 156 | 146 | 145 | 2/2, 2, 0 |
| 0.35 | 69% | **2%** | 51% | 141 / 156 | 150 | 155 | 2/2, 2, 0 |
| 0.38 | 72% | 1% | 82% | 146 / 163 | 154 | 152 | 2/2, 2, 0 |
| 0.40 | 79% | 2% | 89% | 149 / 164 | 168 | 184 | 2/2, 1, 0 |

**0.35 and above bursts villages nobody lied to.** E23 read 0.35 as safe on the old
structure and it is not on this one, which is the reason for sweeping again rather than
carrying the number over. The quiet-village guarantee is the floor here, and it ends the
argument above 0.34.

**0.32 adopted.** It sits with both neighbours quiet-clean, two steps below the cliff rather
than on its edge. Both played sessions rise from 121 and 133 to 138, which is a bubble that
can be watched rather than a number that clears the bar. Paired worlds on the recorded
inputs separate the lie from its absence in both sessions, with two bubbles caused by the
lie and **none at all without it**. The burst rate of 55% sits inside the 25-60% band E23
derived, so the band stands unchanged.

The played-session and paired-world columns are measured on real recorded inputs rather than
on the model, so the choice of 0.32 does not rest on the confirmation rate the model gets
wrong. That it also lands inside E23's band is worth noting but is the weaker evidence.

**Decision.** Adopted: group conversations, `mixing` 0.05, `observationWeight` 0.32. The
band is unchanged and the quiet-village guarantee is untouched at 0%. Outstanding: the model
counts correlated neighbours as independent evidence, which is the next real question and
should be settled before the clustering is fitted any further.

---

## E25 — Witnessed provenance, and the confirmation rate lands

E24 left the model counting correlated neighbours as independent evidence: in a group where
A had told both B and C, C telling B later counted as a fresh source though the information
was A's. That put the model's confirmation rate at 30% against a played 13%.

**The rule, and what it deliberately is not.** Villagers do not track where a belief really
came from, because real people cannot and being taken in by correlated sources is much of
what this project is about. What they know is **who was standing there**. A telling to a
group is now one utterance: one decision to speak, one chance for the rumor to grow, one set
of witnesses, however many were listening. Each listener's chain records everyone present,
so two people who heard the same telling are repeats to each other ever afterwards.
Correlation nobody witnessed still fools them, by design.

`RumorTold` carries its witnesses, so a log still replays without the rule being
recomputed. For a conversation between two the witnesses are just the pair, and the chain
comes out exactly as it did before — which is why **a perfectly mixed village is untouched**
and every seed recorded before E23 still replays.

**Bootstrap first, since the played rates rest on 36 onsets.** 10,000 resamples:

| | confirmed | rate | 95% interval |
| --- | --- | --- | --- |
| played, 21 villagers | 2 of 16 | 13% | **0% - 31%** |
| played, 19 villagers | 1 of 20 | 5% | **0% - 15%** |
| played, both | 3 of 36 | 8% | **0% - 19%** |

Wide enough that E24's 30% was a real disagreement and E23's 11% was never as precise a hit
as it looked.

**Re-measured under witnessed provenance:**

| | E24 | now | played | inside the interval? |
| --- | --- | --- | --- | --- |
| second independent source, 21 villagers | 30% | **4%** | 13% [0-31%] | **yes** |
| second independent source, 19 villagers | 38% | **4%** | 5% [0-15%] | **yes** |
| partners per 2 days, 21 / 19 | 2.34 / 2.57 | 2.34 / 2.57 | 2.12 / 2.45 | unchanged |
| meetings per tick, 21 / 19 | 5.44 / 5.40 | 5.44 / 5.40 | 5.55 / 5.48 | unchanged |

**No refit was needed.** The rule changes chains, not movement, so the two structural
targets `mixing` was fitted on are untouched and 0.05 still stands. The model now sits at
the low end of both intervals rather than far above them.

**Re-swept `observationWeight`**, and the cliff moved down a step, from 0.35 to 0.34:

| observationWeight | bursts | **quiet bursts** | half-believing | peak med/p90 | session A | session B | paired |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 0.29 | 38% | **0%** | 0% | 129 / 143 | 134 | 139 | 2/2, 2, 0 |
| 0.31 | 47% | **0%** | 6% | 132 / 145 | 136 | 148 | 2/2, 2, 0 |
| **0.32** | **55%** | **0%** | 11% | 134 / 146 | **138** | **138** | **2/2, 2, 0** |
| 0.33 | 53% | **0%** | 22% | 135 / 149 | 147 | 142 | 2/2, 2, 0 |
| 0.34 | 55% | **1%** | 31% | 136 / 149 | 146 | 145 | 2/2, 2, 0 |
| 0.40 | 80% | 2% | 88% | 146 / 158 | 168 | 184 | 2/2, 1, 0 |

**0.32 survives unchanged**, with 0.31 and 0.33 both quiet-clean on either side and the
cliff two steps above. Suppressing the false confirmations did not cost the loop: the burst
rate is still 55%, inside E23's 25-60% band, both played sessions still reach 138, and
paired worlds on the recorded inputs still separate the lie in both with **no bubble
without it**.

That the sweep came back to the same number on a model that now matches the played
confirmation rate is worth more than the number. E24 chose 0.32 on played sessions while the
model behind it was wrong; the choice now rests on a model that is not.

**Decision.** Adopted: witnessed provenance. `mixing` stays 0.05, `observationWeight` stays
0.32, the band stays 25-60%, and the quiet-village guarantee is untouched at 0%.

---

## E26 — The first session that worked, and a size the weight was never fitted on

A played session of 26 villagers, 236 ticks, 6 lies. **It ran on the jar built before E23**,
so none of the clustering, witnessed provenance or the new observation weight was in it:
its recipe says `observationWeight=0.25` and carries no `mixing` field at all.

**It is the first session where the whole thing worked.**

| | with the 6 lies | with no lie at all |
| --- | --- | --- |
| peak price | **145** (tick 90) | 107 |
| lowest price | 69 | 93 |
| bubbles | **1** | 0 |
| holders | 23 of 26 | 0 |
| tellings | 111 | 0 |

A bubble that ran up to 145, came back down through the base to 69, and would not have
happened at all without the lie. The average-ask rule from E20 did this on its own; what
changed on the player's side was the **village size**, 26 against the 19 and 21 of every
earlier attempt. E23 predicted exactly this — that a visible bubble needs a bigger village
rather than a different number — and it is the one prediction here that was made before
the fact rather than after.

**The 98 at the start is the noise, working as designed.** At tick 2 the market read 98 with
a noise level of −0.015, no believers at all and seven sellers, all asking the base price.
The first lie was planted at tick 4. A village with nothing to believe wobbles a few percent
either way, which is what makes a bubble measurable against something.

**A caution the earlier sessions could not show.** `observationWeight` was chosen in E24 and
confirmed in E25 against sessions of 19 and 21 villagers. Against all three sessions at once:

| observationWeight | 26 villagers | 21 villagers | 19 villagers |
| --- | --- | --- | --- |
| 0.25 (what it played at) | 145 / 107 | 121 / 108 | 133 / 107 |
| **0.28** | **151 / 107** | **134 / 108** | **133 / 107** |
| 0.30 | 169 / 107 | 143 / 108 | 149 / 107 |
| 0.32 (today's default) | **172** / 107 | 138 / 108 | 138 / 107 |
| 0.34 | **172** / 107 | 146 / 108 | 145 / 107 |

The no-lie column is 107 or 108 at every weight and every size, so the quiet-village
guarantee holds on recorded inputs as well as on seeds. But **0.32 pins a village of 26 to
172 against a cap of 175**, which is the saturation rejected in E22. The weight was fitted
on villages of about twenty and does not transfer upward.

0.28 bursts all three sessions, separates the lie in all three, and saturates none. Headless
it reads 36% bursts, 0% quiet, inside the 25-60% band.

**A limitation of every played-session check so far, stated plainly.** A recorded session
runs in `MeetingSource.EXTERNAL`, where the plugin supplies the meetings and `holdMeetings`
never runs — so `mixing` has no effect on any of them. Every played-session column in E23,
E24, E25 and here is measuring `observationWeight` alone. The clustering is validated only
by the structural measures: partners per two days, meetings per tick, and the confirmation
rate against its bootstrap interval.

**Decision. Adopted: `observationWeight` 0.28.** All three recorded sessions burst, none
saturates, and the no-lie column stays at 107-108 throughout. Headless it reads 36% bursts
against the 25-60% band and 0% quiet, so `CalibrationTest` holds unchanged. The quiet-village
guarantee is untouched.

---

## E27 — Who hears it first, on the first session run under the new defaults

25 villagers, 346 ticks, 4 lies, the first session played on the E26 build
(`observationWeight` 0.28, `mixing` 0.05, witnessed provenance).

| | with the 4 lies | with no lie at all |
| --- | --- | --- |
| peak price | **128** (tick 261) | 105 |
| holders | 19 of 25 | 0 |
| tellings | 29 | 0 |
| bubbles | 0 | 0 |

The lie moved the price by 23 and reached three quarters of the village, but stopped two
short of the 130 a bubble needs. The interesting part is why, and it is not a parameter.

**Where the lies went.** The four went to villagers with gossip 0.49, 0.40 and **0.04** at
ticks 5 to 7, and to the most talkative villager in the village, gossip **0.96**, at tick
236 of 346. The peak came at tick 261, immediately after that last one. Three early lies
into quiet villagers did almost nothing; one late lie into a talker did all of it, with only
a quarter of the run left to work in.

**The same session, replayed with only the timing changed:**

| | peak | bubbles | tellings |
| --- | --- | --- | --- |
| as played, the talker hears it at tick 236 | 128 | 0 | 29 |
| **the same four lies, the talker hears it at tick 5** | **156** | **1** | **91** |
| **one lie only, into the talker, at tick 5** | **147** | **1** | **46** |

**One well-aimed lie beats four badly-aimed ones.** A single rumor told to the right villager
at the start bursts the price; four told mostly to quiet people does not, and tripling the
tellings is what the aim buys, not the count.

This is E8 arriving from the other direction. That experiment measured luck at about 65% and
the choice of planter at about 35% across 5,040 runs, and this is what that 35% looks like
inside one village: 29 tellings against 91, from the same rumor in the same place, told
first to someone else.

**Decision.** Nothing changed; no parameter is implicated. What it changes is how the plugin
should be used, and `/hearsay who` exists precisely to make this choice rather than guess it.

---

## E28 — A boom, a bust, and the market doing the talking

24 villagers, 349 ticks, two lies: tick 4 into a villager with gossip 0.83, tick 213 into
the most talkative in the village at 0.96. Better aimed than E27, and the result is stranger.

| | with the two lies | with no lie at all |
| --- | --- | --- |
| peak price | 125 (tick 253) | 105 |
| **lowest price** | **73** (tick 151) | 94 |
| holders | 19 of 24 | 0 |
| tellings | 15 | 0 |
| price observations | 94 | 0 |

**The village went round the whole cycle:**

| tick | price | believe scarce | believe abundant |
| --- | --- | --- | --- |
| 50 | 113 | 17 | 0 |
| 100 | 110 | 2 | 0 |
| **150** | **80** | 1 | **17** |
| 200 | 90 | 6 | 17 |
| 250 | 117 | 9 | 1 |
| 300 | 115 | 19 | 2 |

A scarcity panic, then a *glut* panic, then a second scarcity wave. Of the 94 observations,
**46 concluded the diamonds were abundant** — nobody lied about that. The falling price was
read as evidence of plenty, which lowered asks, which lowered the price further. **The
feedback loop runs in both directions**, and a deflating bubble does not merely deflate: it
overshoots into a bust at 73, well under the base of 100.

The counterfactual is flat. With no lie the price never leaves 94-105 and not one villager
believes anything. **Both the boom and the bust are caused by the single lie**, the bust by
way of the boom it had to come down from, which is as clean a demonstration of the
counterfactual as the project has produced.

**But the market did the talking, not the villagers.** Fifteen tellings against ninety-four
observations, and ten of the fifteen came from one villager. Against the session in E26, 111
tellings against 109 observations, the balance has gone right over. Belief here spread
mostly by watching the price, not by anyone repeating a rumor.

That is worth being uncomfortable about. The project is about rumors spreading, and at
`observationWeight` 0.28 in a village of this size the market is carrying the story while
gossip barely moves. It is not wrong — the loop is supposed to close — but a village where
belief arrives almost entirely through the price is a different claim from the one on the
tin. Nothing in E26's sweep would have caught it, because that measured peaks and bursts and
never the ratio of telling to observing.

**Why the peak stops short of 130.** The abundance beliefs fight the scarce ones directly
through `contradictionFactor`, and the bust at tick 150 wipes out the first wave. The second
lie has to start from a village that has just convinced itself of the opposite.

**Decision.** Nothing changed. Two things for the list: `MarketStats` counts bubbles and has
no notion of a bust, though this run had a larger one than its bubble; and the ratio of
tellings to observations wants watching as a measure in its own right, since no sweep so far
has looked at it.

---

## E29 — Making gossip the mechanism again, and a bug that was hiding behind the threshold

E28 recorded 15 tellings against 94 price observations and no measure to hold it to. This
adds one, sweeps against it, and corrects a hypothesis of mine along the way.

**The measure.** `MarketStats.shareFromGossip()` is the share of the evidence in a run that
came from one villager telling another, as against the price telling everyone at once. 1.0
is a rumor that spread; 0.0 is a village that watched the market. `busts()` is added
beside `bubbles()` at the same time, since E28's bust was bigger than its bubble and went
uncounted.

**My hypothesis was wrong.** I expected `observationWeight` 0.28 to be too high for villages
of this size. It is not the lever at all:

| observationWeight | headless share from gossip | 24v played | 25v played | 26v played |
| --- | --- | --- | --- | --- |
| 0.20 | 0.43 | 0.22 | 0.59 | 0.73 |
| 0.24 | 0.40 | 0.21 | 0.47 | 0.74 |
| 0.28 (then) | 0.38 | 0.24 | 0.48 | 0.68 |
| 0.32 | 0.41 | 0.55 | 0.51 | 0.60 |

Flat within noise across the whole range, and at 0.20 the session that prompted the question
still reads 0.22. Weighing the market less does not make anyone gossip more; it only makes
the same gossip worth proportionally more, and the tellings never happened either way.

**The lever is `tellThreshold`, which E21 had already pointed at for another reason.**

| tellThreshold | headless share | bursts | quiet | 24v | 25v | 26v |
| --- | --- | --- | --- | --- | --- | --- |
| 0.40 (then) | 0.38 | 36% | 0% | 0.24 / 125 | 0.48 / 128 | 0.73 / 145 |
| 0.35 | 0.42 | 37% | 0% | 0.17 / 122 | 0.43 / 127 | 0.64 / 148 |
| 0.30 | 0.47 | 38% | 0% | 0.29 / 123 | 0.67 / 123 | 0.74 / 162 |
| **0.25** | **0.53** | **45%** | **0%** | **0.45 / 127** | **0.63 / 130** | **0.66 / 153** |

E21 measured that a freshly heard rumor lands near 0.4 and decays under it within about two
days, after which it is never repeated. The consequence was not only a low price; it was
that the market had to do all the convincing. At 0.25 the chain has room to live: gossip
becomes the majority of the evidence headless, and the session that prompted this goes from
0.24 to 0.45.

**Adopted: `tellThreshold` 0.25.** Villagers now repeat things they are only a quarter sure
of, which is what people do. Validated on held-back seeds 2001-2200: the lie still moves the
price in 198 of 200 paired worlds, the median gap rises from +19 to +21, and bubbles caused
only by the lie go from 63 to 68. The burst rate of 45% sits inside the 25-60% band, so
`CalibrationTest` is unchanged, and quiet villages still burst 0% of the time on the
calibration seeds.

**One nuance about that guarantee, since it came up in the same run.** On the wider seed set
2001-2200, one paired world in 200 bubbles *without* the lie while its partner does not.
That is true at 0.40 as well as 0.25, so it is not a regression, but the guarantee is
"0% on the hundred calibration seeds", not "never on any seed". About half a percent of
villages do talk themselves into it unaided.

### A real bug, hiding behind the old threshold

Lowering the threshold made `RumorStats` throw. A rumor born of somebody reading the market
price had no family registered — `familyOf` was filled in for planted and mutated rumors and
not for observed ones — so the first time such a rumor was *told on*, the lookup returned
null. It had been unreachable for as long as observed beliefs sat below the telling
threshold and were never repeated. Fixed, with a regression test checked by putting the bug
back.

It is worth noting how it surfaced. No sweep found it, and no test caught it; it appeared
because a parameter moved and made a path reachable for the first time. Every experiment
before this one ran with that path dead.

**Decision.** Adopted: `tellThreshold` 0.25, `shareFromGossip()`, `busts()`, and the
`RumorStats` fix. Nothing else changed, and the quiet-village guarantee is untouched.

---

## E30 — Fuzzing the parameters, and saying what the guarantee actually is

Two corrections to how this project checks itself, neither of them a tuning change.

**A parameter fuzz test.** Every sweep here runs the defaults or a neighbourhood of them,
which leaves whole paths unreached: E29's `familyOf` bug lived in one of them for
twenty-eight experiments. `ParameterFuzzTest` draws random settings across the whole valid
range with random seeds, and asserts only what must hold whatever the knobs say — that
nothing throws, that confidences are confidences, that every belief points at a rumor that
exists, that nobody is in their own chain, and that the log replays to the world it
describes. It asserts no rates and no bands, so tuning can never make it fail.

It was checked by putting E29's bug back, and it catches it. It also catches it **with the
old `tellThreshold` of 0.4**, because the fuzz randomises the threshold rather than
inheriting it — so it would have found the bug before the retune rather than because of it,
which is the whole point. A second test asserts the fuzz actually reaches the path in
question, since a fuzz run that never has an observed rumor told on would pass for the
wrong reason.

**The quiet-village guarantee, measured rather than asserted.** `CalibrationTest` demanded
that no village nobody lied to ever bursts, across a hundred seeds. That was a claim about a
hundred seeds dressed up as a law. Measured:

| seeds | quiet villages that bubbled |
| --- | --- |
| 1001-1300 | 1 of 300 |
| 2001-2200 | 1 of 200 |
| 5000-5399 (never used before) | 1 of 400 |
| **all nine hundred** | **3, about 0.33%** |

A village that panics unaided is **rare, not impossible**. The test now runs three hundred
seeds and allows up to four, which at the measured rate cries wolf about three times in a
thousand, and would still catch a rate that climbed to 3% in 98 runs out of 100. It keeps
its teeth: raising `observationWeight` to 0.45 fails it.

The README says the same thing in the same terms, since this is the project's central claim
and it should not be stated more strongly there than the measurements support.

**E2 has a note added.** That experiment fixed `tellThreshold` at 0.4 and swept only the
decay, and everything after it treated the threshold as settled. That was safe only for a
perfectly mixed village, where a belief is confirmed again before it decays under the
threshold. In a clustered village the threshold decides whether a rumor is repeated at all.
The finding held for the model E2 was run on, and stopped holding at E23 without anyone
noticing until E29.

**Decision.** No parameter changed. Tests added, a guarantee restated as a rate, and an old
experiment annotated with the conditions it actually holds under.

---

## E31 — The first session where everything worked at once

22 villagers, 708 ticks, **one lie**, planted at tick 4 into a villager with gossip 0.87.
The first session played with a marked market, the E29 telling threshold and the E26
observation weight all in place at the same time.

| | with the lie | with no lie |
| --- | --- | --- |
| peak price | **156** (▲ +56%) | 109 (△ +9%) |
| lowest price | **44** (▼ -56%) | 92 (▽ -8%) |
| bubbles | **5** | 0 |
| busts | **4** | 0 |
| held the rumour | **19 of 22** | 0 |
| evidence from gossip | **57%** | — |

One lie, told once, to one villager. The village then spent 177 days going up and down:
five separate bubbles and four busts, from 156 down to 44 and back. With the lie removed
and everything else identical, the price never leaves 92 to 109 and **not one villager ever
believes anything**.

**What changed, and it was not the tuning.** Three things landed together and this is the
first run with all of them:

- **A marked market (stage 1).** Who counts as a trader is now a place rather than an
  inference. The market opened on 261 of 708 ticks with a mean of 11.8 sellers against a
  quorum of 6 — a real, well-populated market, where E17 to E19 were fighting a market of
  six that kept falling below quorum.
- **`tellThreshold` 0.25 (E29).** Gossip carries 57% of the evidence, against 24% in the
  session that prompted that change. The rumour is spread by villagers, not by the price.
- **`observationWeight` 0.28 (E26).** Peak 156 rather than the 172 that pinned an earlier
  session against the 175 cap.

**The aim mattered as much as the settings.** E27 predicted this: one lie into a talkative
villager at the start beats four scattered ones. Here it is, with a single telling at tick 4
reaching 19 of 22 villagers.

**A bust deeper than the bubble, again.** The low of 44 is further from normal than the peak
of 156, which E28 first recorded and `busts()` now counts. Four of them. A village that has
been talked up gets talked down harder, because the fall is evidence of plenty on exactly
the terms the climb was evidence of scarcity.

**Five bubbles rather than one** is a longer run than any before — 177 days against 59 — and
the loop does not settle. Whether that is right is a question worth asking: a real panic
burns out, and this village oscillates for as long as it is left running. Nothing here
measures how a bubble should decay over months, and nothing should be changed until
something does.

**Decision.** Nothing changed. This session is the one the dashboard now publishes.

---

## E32 — Measuring the oscillation instead of tuning it

E31 ran five bubbles and four busts over 177 days and never settled. Counting bubbles said
"five" and said nothing about whether the village was calming down, which is the more
interesting question. Three measures now answer it, and E31 is their baseline.

**What is measured.** A **swing** is one leg of the price's wandering, from a turning point
to the next, where a turning point is a reversal of at least a tenth of the normal price —
enough to keep the ordinary wobble from counting as a change of mind. **Swing decay** is how
much each leg shrinks against the one before, averaged geometrically, because these are
ratios and a run that halves and then doubles has gone nowhere. **Settling** is the tick
after which the price never again leaves ±10% of normal, measured from the end backwards so
it answers "when did it settle and stay settled" rather than "when did it first cross
normal on the way past".

### E31's baseline

| | |
| --- | --- |
| swings | **20** |
| biggest swing | **112%** of normal |
| each swing against the last | **1.05** |
| settled within 10% | **never** |

| swing | from | to | size | days |
| --- | --- | --- | --- | --- |
| 2 | · −3% | ▲ +36% | 39% | 3 |
| 5 | ▲ +26% | ▼ −23% | 49% | 18 |
| 6 | ▼ −23% | ▲ +34% | 57% | 33 |
| 9 | ▲ +42% | ▼ −45% | 87% | 20 |

**A ratio of 1.05 is the finding.** Above 1 the village is winding up, not calming down.
Not dramatically — 5% a swing is slow — but it is the wrong side of 1, and over 177 days it
took the swings from 39% to 87%. Nothing in the model damps them, and the numbers say so
rather than the impression saying so.

**Why there is nothing to damp them, which is the hypothesis this was built to test.** Every
piece of evidence a villager can have is either something another villager told them or the
price itself. Gossip comes from belief and the price comes from belief, so **the village has
no anchor to anything true**. A rumor cannot be contradicted by the world, only by another
rumor, and a price cannot be wrong because the price is whatever the village believes it
should be. A loop with no external reference has nothing to converge on.

That is a coherent model of a rumour, and it is what this project set out to build. It is
not a coherent model of a market, and the difference is what stage 4 of V2_PROPOSAL.md
proposes to fix.

**Decision.** Nothing changed and nothing tuned. The oscillation is now a measurement rather
than an impression, and these four numbers are what any damping mechanism has to beat.

---

## E33 — The two trade weights, and what selling does not do

Stage 3 built. Sweeping both weights together, as the proposal said they must be, since one
is about goods you took and the other about goods you watched somebody else take. Forty
seeds each, a village lied to and sold into hard, beside a village **nobody lied to** that
the player sold into just as hard.

| tradeWeight | witnessWeight | peak (p50) | swing decay | settled | **quiet village bursts** |
| --- | --- | --- | --- | --- | --- |
| 0.14 | 0.07 | 130 | 1.37 | 52% | **0%** |
| 0.14 | 0.14 | 129 | 1.25 | 50% | **0%** |
| 0.14 | 0.28 | 130 | 1.20 | 37% | **2%** |
| **0.28** | **0.14** | **130** | **1.19** | **50%** | **0%** |
| 0.28 | 0.28 | 130 | 1.20 | 55% | **10%** |
| 0.42 | 0.07 | 129 | 1.19 | 52% | **0%** |
| 0.42 | 0.14 | 129 | 1.19 | 50% | **2%** |
| 0.42 | 0.28 | 130 | 1.21 | 52% | **7%** |

**The guarantee sets the ceiling, and it is the witness weight that threatens it.** At a
witness weight of 0.28 a village nobody lied to bursts in up to 10% of seeds, against the
0.33% measured in E30. A player selling hard enough could start a panic without telling a
single lie — which is the bug the proposal named in advance. **The trader's own weight
barely matters to it; what matters is how much a crowd makes of watching.** 0.14 holds the
rate at zero and is adopted, with the trader at 0.28.

**And the finding that matters more: witnessed selling does not damp the oscillation.**
Decay sits between 1.19 and 1.44 at every setting, against E32's baseline of 1.05 without
any trading at all. Selling into a panic does not calm the village — **it makes the swings
bigger**, because it is one more shock arriving from outside rather than a force pulling the
price back to anything.

That was the cheap version of stage 4's question, asked early and on purpose, and the answer
is no. **A single narrow channel of truth is not enough.** Seeing one player's diamonds tells
a villager something about that player, not about the world; it is evidence that arrives and
then stops, so there is nothing for the price to converge *on*. Stage 4 starts knowing this,
which is worth more than starting blind, and it says something about what the reality checks
will have to be: **a standing fact about supply rather than a series of events.** A villager
who can see how many diamonds are in the village has an anchor. One who merely remembers a
sale has another rumour, told by the world.

**Decision.** `tradeWeight` 0.28 and `witnessWeight` 0.14 adopted, the highest pair that
leaves the quiet-village rate untouched. Nothing else changed; no headless experiment needed
re-running, since a headless run has no player in it.

---

## E34 — Selling into a panic, played rather than swept

21 villagers, 34 ticks, two lies at ticks 9 and 11, and ten sales at six different counters
with between 1 and 14 villagers watching each. The first session in which a player sold
into a panic they had started.

### Who moved the price

| timeline | peak |
| --- | --- |
| as played — the lies and the sales | **162** |
| **the sales alone**, on a village nobody lied to | **105** |
| the lies alone, on a village nobody sold to | 168 |
| neither | 105 |

**The lie contributed +57. The selling contributed −6.** Reported separately, because the
two are measured against different timelines and neither may take credit for the other.

**The sales alone did nothing at all**: 105 against the 105 of a village left entirely
alone. A player selling hard into a village nobody had lied to did not move its peak by a
single point, which is the quiet-village guarantee holding on real inputs rather than on
seeds.

**And the selling worked in the right direction.** 162 against the 168 the lies would have
reached unaided: selling into the panic took six points off it. Small, but it is the first
time the mechanism has been seen doing what it was built to do — the player puncturing a
bubble they started, with their own diamonds as the evidence.

### But it does not damp the oscillation

| timeline | swings | decay | settled |
| --- | --- | --- | --- |
| as played | 4 | **1.64** | never |
| the lie alone | 4 | **1.72** | never |
| the sales alone | 2 | 1.50 | yes |
| neither | 1 | — | yes |

Selling brings decay from 1.72 to 1.64 — **the right direction, and nowhere near 1**. This
is the played confirmation of what E33's sweep predicted, and the answer to the question the
roadmap gated stage 4 on: **no, witnessed selling alone does not bring decay below 1.**

**Why, and it is visible in one number.** The biggest single jump in anyone's belief in
plenty, across all ten sales, was **+0.004**. Four thousandths. A sale is a shove so light
that decay erases it before the next one lands, where the lie is a standing story the
village keeps retelling itself. That is the difference between an event and a fact, and it
is exactly the distinction stage 4 has to be built on.

### A recording fault worth fixing first

Every bulk sale appears in the recipe as **twelve separate sales of one diamond**, because
shift-clicking a trade fires the event once per use and the plugin records each. Grouped by
counter and moment they read correctly — 12 diamonds at tick 16, 6 watching — but the model
does not group them.

That matters because the evidence saturates: a sale of *n* counts as `min(1, n/16)`. Twelve
diamonds sold one at a time apply that twelve times over and compound to about the same as
one sale of twelve, by coincidence at this size. At a stack they diverge badly: 64 diamonds
one at a time convinces about three times as much as 64 in one go, which is backwards — **a
single large sale should be the stronger sight, not the weaker one.**

The fix belongs in the plugin, not the model: accumulate the sales at one counter within a
tick and schedule one input carrying their total. Nothing in core changes, no experiment
re-runs, and the saturation starts meaning what it says.

**Decision.** Nothing tuned. Stage 4 is now justified on played evidence as well as swept:
a reality check has to be a standing fact about supply, because an event this light cannot
hold a village's attention. The bulk-sale grouping is fixed first, since every future
measurement of selling depends on the count being right.
