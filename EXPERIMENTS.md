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
