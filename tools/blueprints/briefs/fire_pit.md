# Fire Pit: the brief

Family id(s): `fire_pit` (never renamed)   Variants: village (none drawn: a style such as `cherry` is applied to the
drawing automatically by `BlueprintStyles`, e.g. when a Wandering Trader offers it styled, `MarketDays.addBlueprintOffer`),
modded (none: decorations are never dressed by `modded_library.py`), looks (none)

One level only: there is no `fire_pit_2`, and no code expects one (the starter-upgrade count of 64 in
`BuilderGameTests.starterUpgradesKeepMostOfTheirBase` must not change, so don't add a Level II).

## What it is for
A small village decoration (ROADMAP 26.14, `StarterBlueprints.MORE_DECORATIONS`): a place round a fire. A finished
Fire Pit inside a Village Hall's claimed area adds **2 beauty points** to the village (`Decorations.POINTS`), and each
point of beauty adds 1 % wellbeing (up to 10 %). It hands out no job and houses nobody. The player gets the blueprint
from the Blueprint Table once it is unlocked (it starts locked; the way in is market day / a found blueprint), from a
Wandering Trader's extra offer (`MarketDays`), or in creative from the mod's tab (`ModItems`, `DECORATIONS`); a Builder
builds it where the player clicks. Finishing it grants the hidden advancement `built/fire_pit`. It shows on the village
map as a decoration (`VillageMaps`, kind `DECORATIONS`, by id). The Steward never plans one and the Grand Rebuild never
rebuilds it (`GrandRebuild.never`).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `fire_pit` | 2 beauty points (`Decorations.POINTS`) | Blueprint Table: starts **locked**, hint `screen.aliveworkplace.table.hint.market` (`MoreDecorationsGameTests.flowerPlantersStartUnlockedTheRestComeFromMarketDay`); Wandering Trader offer pool; no upgrade |

## Must have
- Job block(s): **none, and none allowed.** No block in the template may be a villager POI (any job-site block, a bell,
  a bed, a beehive or bee nest...): `MoreDecorationsGameTests.moreDecorationsHoldNoJobBlock` and
  `QaDecorations1108GameTests.theTableListsEveryNewDecorationWithANameMaterialsAndAWayToGetIt` check
  `PoiTypes.forState` on every block. (A campfire is not a POI; it is allowed.)
- Beds and residents: none (beds are POIs, so forbidden).
- Storage: none read. A container drawn in is free, but builders never break containers on a take-down, so it would be
  left standing and fail the take-down test below: **don't draw any container** (chest, barrel, shulker, pot with
  items...).
- Furniture kinds a job or test needs: none. There is no `blueprint_furniture/fire_pit.json` and no job reads one.
- **The fire.** No code or test requires a fire, but the name promises one and the take-down test exists for it. If
  there is fire it must be a **campfire or soul campfire** (blueprints place them lit on purpose,
  `MaterialRules` keeps their `lit`). Plain `fire` and `soul_fire` are in `MaterialRules.NEVER_PLACE`: a builder can't
  place them, so `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable` fails. Every fire must come down with the
  build (see the take-down rule). A fire on a netherrack/magma "eternal flame" is therefore impossible.
- Liquids: none needed (it is the one decoration `QaTakeDown1105GameTests` exempts from "pours no liquid"). If any water
  or lava is drawn, each must be a source poured from one bucket, held in so it never flows out, and gone after the
  take-down.
- Anything else the code or a test reads: nothing. No bell, sign, lectern, door or bench seat is read.

## Fits
- Plot: at most **9 x 9 footprint (x by z) and 8 tall** (`MoreDecorationsGameTests.moreDecorationsAreDecorationsWithBeautyAndAName`:
  `size.x <= 9, size.z <= 9, size.y <= 8`). y = 0 sits on the top of the clicked block; the front is the z = 0 side.
- Declared size: `StarterBlueprints.FIRE_PIT` is `new Vec3i(7, 3, 7)` today. If the new drawing's size differs, the
  ship step changes that `Vec3i` to match (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
  The wiki table in `docs/wiki/more-decorations.md` lists the size too and is updated at ship time.
- Front and way in: no door. A path to it (`Paths.doorstep`'s fallback) starts one block in front of the middle of the
  z = 0 edge, so that spot should be ground a villager can walk onto from the front. A villager (the builder) must be
  able to walk in and reach every block to place and later remove it.
- Neighbours: placed freely by the player as a decoration; walls and roads go round its footprint.

## Rules tests check
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: the `.nbt`'s size equals
  `StarterBlueprints.FIRE_PIT`'s size, and no block is one a builder can't place (`MaterialRules.Kind.SKIP`: fire, soul
  fire, light, barrier and the like).
- `MoreDecorationsGameTests.buildsFirePit`: a builder with the plan's materials builds it at origin helper (9, 2, 9) in
  `big_area` (18 tall) and finishes within **9,000 ticks**. Keep the block count modest for a 2-point decoration.
- `QaDecorations1104fGameTests.firePitFromExactlyItsList`: stocked with **exactly** the player's material list, the
  builder finishes within 12,000 ticks with **every block as drawn** (`plan.unfinished` empty) and **0 skipped steps**.
  So every block must be placeable from a reachable standing spot, keep its drawn state once placed (no block that
  changes, grows, flows, falls or pops off its support), and cost only items the list asks for.
- `QaDecorations1104fGameTests.firePitTakenDownLeavesNoFireAndGivesBackItsCost` (origin (9, 2, 9), checks helper
  x/z 0..21, y 1..12): built from exactly its list, then the placed blueprint sneak-handed back to the builder; within
  16,000 ticks every built block is gone, **100 ticks later there is no fire, campfire or soul fire and no fluid
  anywhere in or round it**, the builder's bag is empty, and **every item paid comes back** in exactly the counts paid
  (nothing made, nothing lost: e.g. a block that drops something other than itself when broken, or a lit campfire that
  sets something burning, breaks this).
- `QaDecorations1108GameTests.theTableListsEveryNewDecorationWithANameMaterialsAndAWayToGetIt`: the Table lists it locked
  with a real hint; its materials list is non-empty and asks for nothing a survival player can't get (no farmland,
  dirt path, petrified slab, infested blocks, budding amethyst, light, spawner...); no creative-only block or POI in the
  template; the name "Fire Pit" (lang key `blueprint.aliveworkplace.fire_pit`, unchanged) and the `built/fire_pit`
  advancement exist.
- `MoreDecorationsGameTests.moreDecorationsAreDecorationsWithBeautyAndAName`: in `DECORATIONS`, beauty >= 1, a name, and
  the 9 x 8 x 9 limit.
- `MoreDecorationsGameTests.moreDecorationsHoldNoJobBlock`: no POI block.
- By id only (the drawing doesn't matter): `Decorations` (2 beauty), `VillageMaps` (decoration kind), `MarketDays`,
  `BlueprintUnlocks`, `GrandRebuild`.
- The owner's rules (`check.py`) and STYLE.md's checks: `rebuild.py fire_pit fire_pit --candidate` prints no CHECK line,
  and `holes.py` passes.

## Free
Everything not listed above: whether and how the fire is surrounded, seating, lighting, ground, materials, shapes,
height and footprint within 9 x 8 x 9, and style.
