# Mega Shrine: the brief

Family id(s): `mega_shrine` (never renamed; one level, no `mega_shrine_2`)   Variants: village (none drawn: a zone's
style is applied by `BlueprintStyles`), modded (none: decorations aren't dressed by `modded_library.py`), looks (none)

## What it is for
A village decoration for packs with the **Mega Showdown** mod (ROADMAP 38.7), where the village's festival show is held.
It is listed at the Blueprint Table (and the creative tab) only while Mega Showdown's `pedestal` block exists and the
`villageMegas` switch is on (`StarterBlueprints.MEGA_ONLY`, `BlueprintLibrary.megaOk`, `MegaShrines.blocksExist`). A
finished shrine within a Village Hall's area adds **2 beauty** (`hall/Decorations`, by id). On a **festival day**, for
the festival's first hour and a half (1,500 ticks), a village with a finished shrine holds its show there
(`trainer/MegaShrines`): the villagers off work **gather in front of the shrine** instead of at the square, a village
trainer **walks up to the pedestal**, waits (at most 600 ticks) until the crowd has come, and **Mega Evolves their ace
at the pedestal** (light, sound and a chat line); every villager off work within 12 blocks of the pedestal gets +3 mood
for the festival's length. The shrine counts **only while its pedestal stands**: a broken pedestal holds no show.
Nobody lives or works here. It has a hidden `aliveworkplace:built/mega_shrine` advancement.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `mega_shrine` | 2 beauty; the stage of the festival's Mega Evolution show | With Mega Showdown's blocks only (`MEGA_ONLY`), behind `villageMegas`; in `BlueprintUnlocks` with the decorations. No Steward rule, no loot table. No upgrade exists or may be added |

## Must have
- **The pedestal: one `mega_showdown:pedestal` at exactly (3, 3, 3)** (`MegaShrines.PEDESTAL_SPOT`): the code finds the
  pedestal of a finished shrine at the placement's origin + (3, 3, 3) and holds no show unless that block is the
  pedestal (`MegaShrines.standing`). The compat tests set the pedestal there by hand and assert its place
  (`MegaShrineCompatTests.theFestivalShowIsHeldAtAFinishedShrine`: the pedestal at origin (2, 1, 5) + (3, 3, 3) =
  (5, 4, 8)). Moving it means changing `PEDESTAL_SPOT` and every test that uses it; don't.
- **Directly under the pedestal, at (3, 2, 3): `mega_showdown:chiseled_mega_meteorid_block`**
  (`QaMegaShrine1104d4CompatTests.theShrineIsSevenBySixBySevenWithItsBlocksAndBeautyTwo`: "under the pedestal").
- **The trainer's stand at (3, 3, 2)**, the cell right in front of the pedestal (`MegaShrines.TRAINER_SPOT`): the
  trainer is sent there (`WALK_TARGET`) and must get within 3.5 blocks of it to start. So (3, 3, 2) and (3, 4, 2) must
  be free to stand in, (3, 2, 2) must be solid footing, and there must be a **walkable way up to it from the front**
  (z = 0 side) that a villager can climb (steps or slabs, nothing 1.5 tall). The tests spawn the trainer at origin +
  (3, 3, 2) (`(5, 4, 7)` everywhere).
- **The crowd gathers at (3, 1, -4)** (`MegaShrines.CROWD_SPOT`), 4 blocks in front of the plot's front edge, outside the
  drawing. The front must face open ground: nothing in the drawing should block walking from there to the shrine's
  steps, and the pedestal should be visible from there (within the 12-block watch range: it is).
- **Blocks the guide names, each at least once** (the same test asserts every one is in the drawing):
  `mega_showdown:pedestal`, `mega_showdown:chiseled_mega_meteorid_block`, `mega_showdown:mega_meteorid_brick`,
  `minecraft:deepslate_brick_stairs`, `minecraft:deepslate_brick_wall`, `minecraft:amethyst_cluster`,
  `minecraft:soul_lantern`. (This is a materials rule only because a test reads it; how much of each and where is
  free. If the design truly needs a different set, the ship step changes that test and `docs/wiki/raid-dens-megas-cuisine.md`
  together, which is a decision to note, not a default.)
- Mega Showdown's other blocks (`polished_mega_meteorid_block` and the rest) are allowed; any `mega_showdown:` id used
  must exist in Mega Showdown for 1.21.1 (check the ids the old drawing used, listed above, before adding new ones).
- Job blocks: none, and no villager POI block. Beds: none. Storage: none.
- Furniture kinds a job or test needs: none (no `blueprint_furniture/mega_shrine.json`).
- The owner's rules: the old drawing has 3 baselined problems (soul lanterns standing on the dais floor). The rebuild
  must have none, and the ship step removes `mega_shrine` from `owner_rules_baseline.json`.

## Fits
- Plot: **exactly 7 (x) x 6 (y) x 7 (z)** (`StarterBlueprints.MEGA_SHRINE` and
  `QaMegaShrine1104d4CompatTests.theShrineIsSevenBySixBySevenWithItsBlocksAndBeautyTwo` assert it). With the pedestal at
  y = 3, there are two cells above it (y 4-5) inside the plot.
- y = 0 sits on the clicked block; the front is the z = 0 side, where the crowd stands and the trainer climbs.
- Neighbours: placed by a player near the village's square or hall; nothing is placed beside it by a plan.

## Rules tests check
- `QaMegaShrine1104d4CompatTests.theShrineIsSevenBySixBySevenWithItsBlocksAndBeautyTwo`: 7 x 6 x 7; the seven block ids
  above present; the pedestal stands on `chiseled_mega_meteorid_block`; beauty 2.
- `MegaShrineCompatTests`, `QaMegaShrineCompatTests`, `QaMegaShrineGapsCompatTests`, `QaMegaShrineWaitCompatTests`,
  `QaMegaShrineBugCompatTests`, `QaMegaShrine1104d4CompatTests`: record a finished shrine at origin (2, 1, 5), set the
  pedestal at origin + **(3, 3, 3)**, the trainer at origin + **(3, 3, 2)**, watchers in front; they don't place the
  rest of the drawing, so they pin only those two spots and the front.
- `MegaShrineGameTests.hiddenWithoutMegaShowdown`, `QaMegaShrineGameTests`: without Mega Showdown it's hidden and no
  show runs; by id only.
- The showcase scene `mega_shrine` (`JobScenes`) places the drawing and films the trainer at the pedestal with the crowd
  in front: the pedestal and the trainer's stand must read from the front.
- The owner's rules (`check.py`) and STYLE.md's checks: `rebuild.py --candidate` prints no CHECK line, `holes.py` passes.
  Note the render (`shoot.mjs`) and possibly the parts runner don't know `mega_showdown:` blocks: they render as gaps,
  so judge the renders knowing what stands there.

## Free
Everything else: the shape round the pedestal (dais, ring of stones, arches, a canopy within the 6-block height),
materials beyond the listed ids, lights (hung or on posts, not on the floor), planting, and the approach, as long as the
pedestal is at (3, 3, 3) on its chiseled block, the trainer can walk up to (3, 3, 2) from the front, and the crowd can
see it from 4 blocks out.
