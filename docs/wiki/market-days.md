# Market days

Once a week, in the morning, a village with a [Village Hall](village-hall.md) and a finished Market Square holds a
market: wandering traders come to the square with their usual wares and a blueprint to sell, and leave again later in
the day. This page was written from the code and lane-c-1117-0652's tests (`QaMarketDays1117cGameTests`), since the
feature came before the wiki.

Roadmap items: B1361, B1362

## What a player sees

**The bell and the news.** On market morning a bell rings at the Market Square and every player within the village's
radius of the hall reads in chat (gold): "It's market day in Thornholm: traders have come to the Market Square". A
player outside the village isn't told. The [chronicle](village-hall.md) gets a line: "Market day: 2 traders came to the
Market Square".

**The traders.** Vanilla wandering traders appear on standing spots around the middle of the square and wander about
it. Each sells, besides his own wares, **one blueprint for 6 emeralds** (one use): a starter building, plain or in one
of the [styles](house-looks.md), or one of the [decorations](more-decorations.md). Blueprints bought this way unlock the
build at the [Blueprint Table](blueprint-library.md) ("A market-day trader"). With the Burghers'
[class](classes.md) in force one more trader comes, selling a grand-house blueprint at its own price.

**How many.** Two, three from Town rank on ([ranks](village-hall.md)), plus one for each level of Commerce research,
one more under the Open Gates [edict](edicts.md), and one for the Burghers. A Festival Cup's fair brings the same
traders to the Arena with two more ([Festival Cup](festival-cup.md)).

**How long.** A trader stays 10,000 ticks (about eight and a half minutes) at most, and is gone by dusk (12000)
whenever the market began: a market that begins late in the morning (the village loaded only then) keeps its traders
only until dusk, not into the night (B1362). The Curfew edict's limit is the same or sooner.

## How it works

- **The day.** Every village has its own market day, one in every 7, worked out from where its hall stands, so
  neighbors usually hold theirs on different days. It is the same for that hall forever.
- **The morning.** Market time is 1000 to 5999 of that day. A hall whose round falls in it holds the market once;
  the day held is saved with the hall (its last market day), so a restart or a reload the same morning brings no second
  market. A save from before the field existed holds this morning's market as usual.
- **No square, no market.** Without a finished Market Square in the village's area (a styled one counts) nothing
  happens, and the day isn't spent: if the square is finished later that morning, the market still comes.
- **Told ahead.** The [Seer](legends.md) foretells the next market day, the [Observatory](wonders.md)'s Coming days
  list and the [Gazette](luxury-workshops.md)'s calendar show it: today while this morning's market is still to come,
  else the next market day, never more than a week off. A village with no Market Square in its area, or with markets
  switched off, is told of no market day at all (B1361).
- **[Legends](legends.md)** that visit "on market day" come with the traders.

## Switches

`marketDays` in the config (on by default; [config switches](config-switches.md)). Off: no market, nothing in the
chronicle, and the day isn't spent, so switching it back on that morning brings the market.

## Saved data

- On the Village Hall: the day of its last market (none before the first), so a market comes once a market day.
- The traders are ordinary wandering traders, saved like any; each has a despawn delay and goes when it runs out.

## Items, blocks, jobs, commands

- No new items or blocks: the Market Square blueprint (a starter build) and vanilla wandering traders.
- The blueprints sold are ordinary Blueprint items of the builds named above.

## Decisions

- One fixed day a week per village (from the hall's position), so the Seer and the Gazette can tell it days ahead.
- A missing square doesn't use up the day: building the square that morning is rewarded at once.
- The traders are vanilla wandering traders, so a player trades with them as usual; only the blueprint is added.

## Known limits

- A Market Square counts only inside the village's area: one finished far from the hall holds no market there.

## Proof

- `VillageHallGameTests#marketDayBringsTraders`: the happy path.
- `QaMarketDays1117cGameTests` (9): the morning's edges (999, 1000, 5999, 6000), one market day in every 7 for halls
  all over the map, the next day told against the day it comes, no square then a square finished that morning, who
  is told, the chronicle's count, the count by rank and Commerce, the switch, a styled square, 40 markets' blueprints all real and priced 6, and the
  day held kept over a save.
- `QaMarketDays1117cBugTests` (B1361, B1362, passing since the fixes): the Gazette tells no market day without a
  market, and late-morning traders leave by nightfall.
- `QaCivicMarket1123cGameTests` (QA, lane-c-1123-0953): under Open Gates three traders come to a Hamlet, a player in
  the village reads "It's market day in ...: traders have come to the Market Square" in gold, the chronicle says
  "Market day: 3 traders came to the Market Square", every trader leaves by dusk, two again with the edict lifted; a
  square 200 blocks off holds no market, spends no day and is foretold by nobody, while one in the village holds it.
