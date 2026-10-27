# The Lost Caravan

A story arc that starts when a caravan on a long road goes missing: the settlers' wrecked wagon, five marks of a trail,
the raiders' stockade where it ends, the carter caged there and walked home, and the lost goods. ROADMAP 31.19 (the
builds and `follow_trail`) and 31.20 (the story, `arcs/lost_caravan.json`). Builds on the arc engine's `place` (31.4),
quests (31.2), reputation (31.11) and caravans (27.x). Part of the 1.5 expansion.

Roadmap items: 31.19, 31.20

## What a player sees

- **Overdue**: when a caravan sets off on a road longer than 300 blocks, 1 in 20 goes missing (config
  `lostCaravanChance`), if neither village has a story arc running. Its goods never arrive: the arc holds them. Players
  near either hall read "The caravan from Thornholm to Ashford is a day overdue", both chronicles note it with the
  goods it carried, and the sending hall hands out a map marked with the wreck. Chapter 1 ends when a player comes
  within 12 blocks of the wreck ("The caravan's wreck: tracks lead away from the road").
- **The tracks**: the quest "Follow the tracks" (`follow_trail`, below) leads mark by mark to the stockade. The raiders,
  a Captain, two bandits with axes and two with crossbows, wait there.
- **The captive**: the carter, a villager of the sending village with one of the village names (Tobin, say), is locked
  in the stockade's cage. Flip the lever and the iron gate opens: he thanks whoever stands nearest and follows them,
  as a rallied guard does: he walks after them, catches up at once from more than 32 blocks, and waits where he is
  when they're more than 64 away. The chapter is done when he comes within 24 blocks of either village's hall.
- **The lost goods** (a second quest, not needed to go on): the stockade's chest holds the lost shipment's own goods
  (the bandits' loot isn't rolled there). Carry every stack of them to within 24 blocks of the receiving village's
  hall: they go into its store (or are set down on its hall without one) and the quest pays 8 emeralds and +50
  standing there.
- **The ending**: the carter joins the village he reached, with no job yet: he takes a free workstation like any
  villager and starts at Journeyman, as a hired traveler does. Everyone who helped (found the wreck, followed the
  marks, opened the cage, walked him home) gets +150 standing in both villages and the honor Wayfinder, and +100
  friendship with him; both chronicles tell it.
- **No rescue**: after 7 days from the loss without the carter home, he finds his own way back to the sending
  village with nothing (a plain villager by its hall, no head start), the goods are gone from the chest, and the arc
  fails; both villages are told.
- **The wreck** (`story/caravan_wreck`, 13 x 12): the settlers' covered wagon lies on its side a third of the way along
  the caravan's road (or 150 blocks out from the village when the road there is water or rough ground). Its floor
  stands up like a wall, its white canvas arches out torn at the back with a hoop showing, one upper wheel lies flat in
  the air and the other is broken in the grass. Crates, barrels, hay, a sack and a pumpkin have spilled out of its open
  end, and the lantern that lit their way lies in the grass behind. Ruts show where it slid off the road.
- **The trail**: five marks, about 3 x 3 each, evenly spaced between the wreck and the stockade: trampled coarse dirt
  and a dropped lantern; a smashed barrel with its staves and lid scattered; a torn white banner on a fence post; cart
  ruts of path blocks; a crate stuck with arrows.
- **Following the tracks** (`follow_trail`): walk up to the marks in order. At each, the action bar says where the
  tracks go next ("The tracks go on to the north-east"), and after the last, towards the stockade. A mark reached out
  of turn counts nothing: you have to find them in order. The quest's line is "Follow the tracks", counting 0 to 5.
- **The raiders' stockade** (`story/raiders_stockade`, 19 x 17): 120 to 180 blocks on from the wreck, on the side away
  from the villages. A palisade of sharpened dark oak round a trampled yard, its gate standing open under the ominous
  banner, a lookout platform on the front corner, a gray tent with a bedroll, a fire ringed with log seats and the
  banner on a pole, a loot chest with barrels of plunder, an archery target, and a cage of iron bars with an iron
  gate, a lever outside it and straw inside (the captive's, 31.20).
- Each piece is built only when a player comes within 96 blocks of it. It lies on the land as it is: a hump in its
  footprint is cut away and a hollow filled, and it is never put over anything a player built.

Renders: [the wreck](../media/caravan-wreck.png), [the stockade](../media/raiders-stockade.png) and
[from above](../media/raiders-stockade-top.png), [the five marks](../media/trail-marks.png).

## How it works

- `story/CaravanTrail` holds the builds' ids (`CaravanTrail.WRECK`, `CaravanTrail.STOCKADE`, `CaravanTrail.MARKS`),
  their ground test and the objective.
- **Placing rules** for an arc's `place` (`ArcEffects.spot`):
  - the wreck: `{"road": 0.333, "or": 150}`: a third of the way to the village the road goes to; when that spot is
    loaded and isn't fit ground (`CaravanTrail.fitAround`), or there's no road, 150 blocks out the road's way (or any
    way);
  - the stockade: `{"after": "wreck", "distance": [120, 180], "away": true}`: of sixteen ways round the wreck, the one
    furthest from the nearest village it knows (the hall, the villages it has caravan roads to, its neighbors);
  - the marks: one `place` with `"each"` (one template per place, keys `trail`, `trail#2` ... `trail#5`) and the rule
    `{"between": ["wreck", "stockade"]}`: evenly along the line, the ends left out.
- **Ground** (`CaravanTrail.fits`, used by `ArcEffects.build` for these builds): dry; as flat as the bandit camps'
  test allows for the footprint's size (no more columns over a block above or below the middle's ground than 20 in
  195, and at least one; a step of one is cut or filled); no tree trunk; no section a player built in
  (`PlayerBuilt`) from 4 below its ground to its top, and only natural ground, plants and trees in its box
  (`BanditFort.natural`). The build is tried on the arc's column, then round it out to 32 blocks; once placed, what
  stood above its ground is cleared and the ground under it filled as a camp's is. Its bottom layer is drawn only
  where it changes the ground (paths, trampled earth), so the world's own ground stays elsewhere.
- **`follow_trail`** (`CaravanTrail.FollowTrail`): `{"type": "follow_trail", "trail": "trail", "then": "stockade"}`.
  Its count is the number of marks the arc placed (`count`, 5 by default). Every 2 seconds `QuestTracker` calls
  `CaravanTrail.check` for each player: the next mark of each open `follow_trail` counts when the player is within 6
  blocks of it, across the ground; the action bar then gives the way (`CaravanTrail.direction`) to the next mark, or
  to the `then` place after the last. Marks are found live from the arc's places, so a mark moved by `build` to fit
  ground counts where it stands.
- Every arc build that isn't the fort or the trail's now also refuses ground a player built on (`PlayerBuilt`).

## Items, blocks, jobs, commands

- Blueprints `aliveworkplace:story/caravan_wreck`, `aliveworkplace:story/raiders_stockade` and
  `aliveworkplace:story/trail_1` to `aliveworkplace:story/trail_5` (drawn in `tools/blueprints/caravan.py`; not in the
  blueprint library).
- The stockade's chest uses the bandit camps' loot table `aliveworkplace:chests/bandit_camp`.
- Arc `aliveworkplace:lost_caravan` (`arcs/lost_caravan.json`), its effect `lost_caravan` (steps `overdue`, `ending`)
  and the objective `deliver_goods` (`{"type": "deliver_goods"}`; the village's name is filled in when it's posted).
- No new items, blocks, jobs or commands; `/workplace story start lost_caravan` starts it without a lost caravan (no
  goods, and the wreck on the village's nearest road).

## Switches

- `lostCaravanChance` (20, 0 to 1000): one caravan in this many on a road over 300 blocks goes missing; 0: none.
- The arc runs under `storyArcs`, `arcsAtOnce` and `disabledArcs` (`lost_caravan`) like every arc.

## Saved data

- The arc is saved with every arc (`aliveworkplace_stories`). Its own data is in the arc's new `extra` tag (empty, and
  not written, for every other arc and for saves from before 31.20): `to` (the receiving hall), `goods` (each stack's
  item id and count), `carter_set`, `leader` (who opened the cage), `home` (the hall he reached) and `delivered`.
  The carter is the arc's role `carter` (his UUID and name); flags `wreck_found` and `tobin_home`.
Each piece is an arc place in `aliveworkplace_stories` (its key, spot, `stages`, `placed`, `origin`). A
`follow_trail` objective saves its `trail`, `then` and `count` with its quest, and its progress (marks reached) as
every objective's.

## Decisions

- 31.20: a lost caravan still leaves (the chronicle notes it setting out, its goods taken from the store); it's never
  put on the road. Its goods are kept as item and count: what a caravan carries has no data of its own.
- 31.20: the arc is an event arc (`"trigger": {"event": "caravan_lost"}`) started by `LostCaravanStory.lose` from
  `Caravans.send`; it skips the cooldown but not one arc per village nor `arcsAtOnce`. It runs in the sending village;
  its first effect (`lost_caravan` step `overdue`) records the destination and goods, so the wreck's `road` rule lies
  on the lost caravan's own road.
- 31.20: the raiders spawn when chapter 2 begins (at the stockade, once it's built), so they're there when the
  trail is followed to them.
- 31.20: the carter is caged when the stockade is built, whatever the chapter, and he counts as home only after his
  cage was opened. The "7 days" run from the day the caravan went missing.
- 31.20: the delivery takes every stack at once, when the player comes within 24 blocks of the receiving hall
  carrying them all; a part load counts nothing.
- 31.20: the carter joins with no job and the Journeyman head start (`HEAD_START` 3), so he takes whatever free
  workstation the village has, as a hired traveler does.

- 31.19: the stockade's cage gate is an iron door with a lever outside, so the captive (31.20) can't let himself out
  and the raiders don't open it; a player opens it with the lever.
- 31.19: the stockade's chest had the bandit camps' loot until 31.20 filled it with the lost shipment.
- 31.19: crates are composters (a slatted wooden box); the arrows in the arrow-studded crate are tripwire hooks, the
  nearest thing in the game to a shaft stuck in wood.
- 31.19: a mark counts within 6 blocks of its middle, so a player who sees it from the trail's line counts it as they
  walk up; marks are 20 to 30 blocks apart, so two never count at once.
- 31.19: whether the wreck's road spot is fit ground is known only when it's loaded when the arc picks it; if it isn't
  loaded then, the spot is kept and `build` finds fit ground round it (out to 32 blocks) when a player comes near.

## Known limits

- 31.20: a carter left in his cage where nobody is when the 7 days run out is taken away when his chunk next loads
  (an arc's carter no running arc knows leaves as he loads); his stand-in is home at once.

- With no fit ground within 32 blocks of its column a piece isn't built (the arc logs it and the place is skipped);
  a skipped mark still counts when the player reaches its column.
- The banner's torn look is drawn with banner patterns (a ragged gray hem), as no banner can be cut.

## Proof

- GameTests: `CaravanTrailGameTests`: the wreck (`theWreckLiesOnUnevenGround`), the stockade
  (`theStockadeLiesOnUnevenGround`) and the five marks (`theFiveMarksLieOnUnevenGround`) on a hump and a hole; a
  player's block or ledger section refuses the ground (`neverOverAPlayersBlocks`); the road, away and between rules
  (`theRulesPlaceTheTrail`); `follow_trail` through a real arc, in order only (`followTrailCountsTheMarksInOrderOnly`).
- GameTests: `LostCaravanGameTests`: a caravan from a Storehouse on a 320-block road stays on the road with the
  chance 0 and goes missing when forced, the arc holding its goods, the wreck on its road, both chronicles told; not on
  a short road nor with an arc running (`aShipmentGoesMissingWhenForced`); every chapter by its objective, the carter
  caged with the shipment's own goods in the chest, following once the gate opens, waiting at 100 blocks, catching up
  from 49, joining at Journeyman with standing, Wayfinder and friendship in both villages, the arc's data saved and read
  back (`eachChapterMovesOnAndTobinComesHome`); the delivery (`theDeliveryQuestPays`); the failure path
  (`afterSevenDaysHeFindsHisOwnWay`).
- QA (`QaLostCaravanGameTests`, 16 tests): the 300/301-block road edge, `lostCaravanChance` 0, 1 and its clamps, an arc in
  either village keeping the caravan on the road, both halls told and only the sender's map, the trail out of order
  and twice, the action bar's directions, marks placed at 96 blocks and not 97 and never over a player's blocks,
  Tobin's catch-up past 32 and wait past 64, Journeyman with and without a free workstation, save and reload of Tobin
  and the trail, the chest holding exactly what left the store, the delivery paying once and only at the receiving
  hall, only helpers thanked, and the day-7 failure (nothing on day 6).
- Showcase scenes: `caravan_trail`, `lost_caravan`.
