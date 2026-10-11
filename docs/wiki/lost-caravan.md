# The Lost Caravan: the wreck and the trail

The builds the Lost Caravan's story lays out beyond a village, and the objective that walks a player along them: the
settlers' wrecked wagon, five marks of a trail, and the raiders' stockade where it ends. ROADMAP 31.19; the story that
uses them, `arcs/lost_caravan.json`, is 31.20. Builds on the arc engine's `place` (31.4) and quests (31.2). Part of
the 1.5 expansion.

Roadmap items: 31.19

## What a player sees

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
    furthest from the nearest village it knows (the hall, the villages it has caravan roads to, its neighbours);
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
- No new items, blocks, jobs or commands.

## Switches

None of its own: the builds come only from story arcs, under `storyArcs` (and `disabledArcs`).

## Saved data

Each piece is an arc place in `aliveworkplace_stories` (its key, spot, `stages`, `placed`, `origin`). A
`follow_trail` objective saves its `trail`, `then` and `count` with its quest, and its progress (marks reached) as
every objective's.

## Decisions

- 31.19: the stockade's cage gate is an iron door with a lever outside, so the captive (31.20) can't let himself out
  and the raiders don't open it; a player opens it with the lever.
- 31.19: the stockade's chest has the bandit camps' loot until 31.20 fills it with the lost shipment.
- 31.19: crates are composters (a slatted wooden box); the arrows in the arrow-studded crate are tripwire hooks, the
  nearest thing in the game to a shaft stuck in wood.
- 31.19: a mark counts within 6 blocks of its middle, so a player who sees it from the trail's line counts it as they
  walk up; marks are 20 to 30 blocks apart, so two never count at once.
- 31.19: whether the wreck's road spot is fit ground is known only when it's loaded when the arc picks it; if it isn't
  loaded then, the spot is kept and `build` finds fit ground round it (out to 32 blocks) when a player comes near.

## Known limits

- With no fit ground within 32 blocks of its column a piece isn't built (the arc logs it and the place is skipped);
  a skipped mark still counts when the player reaches its column.
- The banner's torn look is drawn with banner patterns (a ragged grey hem), as no banner can be cut.

## Proof

- GameTests: `CaravanTrailGameTests`: the wreck (`theWreckLiesOnUnevenGround`), the stockade
  (`theStockadeLiesOnUnevenGround`) and the five marks (`theFiveMarksLieOnUnevenGround`) on a hump and a hole; a
  player's block or ledger section refuses the ground (`neverOverAPlayersBlocks`); the road, away and between rules
  (`theRulesPlaceTheTrail`); `follow_trail` through a real arc, in order only (`followTrailCountsTheMarksInOrderOnly`).
- Showcase scenes: `caravan_trail`.
