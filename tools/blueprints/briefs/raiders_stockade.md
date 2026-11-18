# Raiders' Stockade: the brief

Family id(s): `story/raiders_stockade` (one structure, never renamed; `CaravanTrail.STOCKADE` and
`arcs/lost_caravan.json`'s `place` with key `stockade` load it by this id)
Variants: village (none), modded (none), looks (none)

Not in this family: the Caravan Wreck (`story/caravan_wreck`) and the five trail marks (`story/trail_1..5`) of the
same story, the Bandit Fort (`story/bandit_fort*`), the Bandit Camp (`camp/bandit_camp`), the Prisoner Cage
(`legend/prisoner_cage`, a different cage with different rules) and the palisade wall pieces (`palisade*`).

## What it is for
The end of the Lost Caravan story (ROADMAP 31.19 and 31.20). A caravan on a long road goes missing. The player finds the
wreck and follows five marks to this stockade, 120 to 180 blocks beyond the wreck on the side away from the villages.
The game puts it down whole once a player comes near. No builder builds it, and it is not on the Blueprint Table
(`BlueprintLibrary.isWorldgenPiece` hides every `story/` id). When it is placed, `LostCaravanStory.setUp` does three
things:
- It fills **its chest** with the lost shipment's goods.
- It locks **the carter** (a named villager of the sending village) **inside its cage** behind an **iron door**.
- The arc spawns a guard round the middle of the stockade: a raider captain, two vindicator bandits and two pillager
  bandits, each kept within 10 blocks of its spot.

The player fights the guard, flips **the lever** to open the iron door, and the carter follows whoever stands nearest
the open door (within 8 blocks) out of the stockade and home. The player can carry the goods from the chest to the
receiving village (a second quest). If nobody opens the cage within 7 days, the chest is emptied and the carter goes
home on his own.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| (only) | `story/raiders_stockade` | The loot chest, the cage with its iron door, its straw marker and its lever, and the ground the guard spawns on | None. `ArcEffects.build` places it whole with `StructureTemplate.placeInWorld`: no materials list, no builder. No upgrade exists or is asked for. |

## Must have
- Job block(s): none. No workstation is read.
- Beds and residents: none counted. The carter is spawned by code (below). No code reads a bed. The old drawing's one
  bed spot in `blueprint_furniture/story/raiders_stockade.json` is decoration, not a requirement.
- **The loot chest: plain `minecraft:chest` at template (7, 1, 14)** (`CaravanTrail.LOOT_CHEST`), holding the loot
  table `aliveworkplace:chests/bandit_camp` in its block entity (`LootTable`):
  - **It must be the chest nearest the stockade's middle.** `LostCaravanStory.setUp` (and `ownWay`, which empties it)
    take the nearest `Blocks.CHEST` within 11 blocks across (x and z) and template y -1..4 of the middle column at
    template y 1. That middle is `((minX+maxX)/2, 1, (minZ+maxZ)/2)`: (9, 1, 8) at 19 x 17.
  - Safest: **the only plain chest** in the build. Barrels and trapped chests are not `Blocks.CHEST`, so they are free.
  - It is a single chest.
- **The cage's iron door: `minecraft:iron_door`, lower half at template (14, 1, 10)** (`CaravanTrail.CAGE_DOOR`), upper
  half at (14, 2, 10), **shut** (`open=false`) as placed:
  - `setUp` takes the iron door (lower half) nearest the middle, within 11 across and y -1..4, so it is **the only
    iron door** in the build.
  - `LostCaravanStory.freed` looks for the lower half within 4 blocks of the carter and lets him go only while it is
    `OPEN`.
- **The straw marker: a `minecraft:hay_block` inside the cage.** It decides where the carter stands:
  - `setUp` takes the hay block nearest the door, within 4 blocks across and y -2..+3 of the door's lower half.
  - The carter stands **2 blocks from the door towards that hay**, along whichever axis (x or z) the hay is further
    out on. With the door at (14, 1, 10), the hay at (15, 1, 13) gives **(14, 1, 12)**.
  - The showcase scene hard-codes that cell as door + (0, 0, 2), so keep the carter's cell at **(14, 1, 12)**: the hay
    at z 12 or more and nearer in x than in z (for example (15, 1, 13), (14, 1, 13), (13, 1, 14)).
  - No other hay block may be nearer the door than this one.
- **The carter's cell (14, 1, 12):**
  - Air at (14, 1, 12) and (14, 2, 12), so the villager doesn't suffocate.
  - A solid block under it at (14, 0, 12).
  - The cells round it (`restrictTo(inside, 1)`) keep him in. The cage must close him in so that only the iron door
    lets him out (the story says "locked in the cage"; no code checks that it is closed).
- **The way out.** Once the door opens he walks after the player (`follow`: a walk target on the player, a teleport
  only past 32 blocks). There must be a walkable path, 2 blocks high, from (14, 1, 12) through the open iron door, across
  the yard and out of the stockade to the front, with no closed fence gate on it (villagers can't open fence gates).
  The showcase's last step films him walking out to the front.
- **The lever: `minecraft:lever` at template (15, 2, 9)**, pinned by the test. It is the player's only way to open the
  cage (wiki: "Flip the lever and the iron gate opens"). An iron door can't be opened by hand. **The lever must
  actually power the door.** A lever powers only the block it is fixed to (strongly; that block then works the door if
  it touches either half) and the blocks right beside the lever itself. **The old drawing's lever doesn't work:** it
  stands on a block at (15, 1, 9), diagonal to the door, and neither touches a door half.
- **Iron bars: `minecraft:iron_bars` at template (13, 1, 10)**, pinned by the test ("the cage's bars").
- **The guard's ground.** Each of the five guards spawns at the top block (`MOTION_BLOCKING_NO_LEAVES`) of a random
  column within ±3 x and ±3 z of the middle (`ArcEffects.offset`). Every column there stands on what a mob can stand on:
  - A roof or canopy over any of those 49 columns puts a raider on top of it.
  - The cage over them puts one on the cage's roof.
  - Then `restrictTo` keeps each guard within 10 blocks of where it spawned.
- **Pinned blocks** the test checks by id (`CaravanTrailGameTests.theStockadeLiesOnUnevenGround`), template frame,
  front z = 0:
  - `minecraft:dark_oak_log` at (0, 2, 6) ("the palisade");
  - `minecraft:dark_oak_fence_gate` at (8, 1, 0) ("the gate", on the front edge);
  - `minecraft:campfire` at (9, 1, 8) ("the fire", in the middle column);
  - `minecraft:stripped_dark_oak_log` at (4, 3, 12) ("the tent's ridge");
  - `minecraft:iron_bars` at (13, 1, 10);
  - `minecraft:lever` at (15, 2, 9);
  - `minecraft:iron_door` at (14, 1, 10) and (14, 2, 10), shut;
  - `minecraft:chest` at (7, 1, 14) with loot `aliveworkplace:chests/bandit_camp`.

  These are requirements as they stand. A design that needs one somewhere else moves it in the test at the ship step:
  same block, same meaning, new spot (REBUILD.md step 4, never dropped). `CAGE_DOOR` and `LOOT_CHEST` are constants in
  `CaravanTrail` and move with them. A pinned block that is not at its spot fails the test, not the critic.
- Storage: the one loot chest above. Other containers are free, but none of them is read.
- Furniture kinds a job or test needs: none. If the rebuild's plan records furniture spots (`rebuild.py` writes
  `blueprint_furniture/story/raiders_stockade.json`), `FurnishedRoomsGameTests`' rules apply to them: a bed's head to a
  wall, each piece on what it stands on, nothing in a door's way. `story/` builds are exempt from the "light in a
  furnished room" rule (`FurnishedRoomsGameTests.unlit`).
- Anything else the code or a test reads: nothing. No bell, sign, lectern or banner is read.

## Fits
- **How it lands** (`ArcEffects.build`):
  - It is turned a random quarter (`level.random.nextInt(4)`), so everything must work in all four rotations; the
    tests use rotation 0.
  - It is **centred** on a ground column: origin = ground - (sizeX / 2, 0, sizeZ / 2).
  - **Template y = 0 is the ground layer itself.** It replaces the top ground block, unlike a blueprint, which builds on
    the block above.
  - Before placing, every non-air block in the box above y = 0 is removed. Under each column, up to 4 air or
    replaceable blocks below y = 0 are filled with dirt.
  - It is only placed where `CaravanTrail.fits` passes: dry; few columns more than 1 off the ground's height (and few
    tree trunks), at most `columns × 20 / 195` of each; nothing a player set; only natural ground and plants in the
    box. The bigger or taller the box, the fewer places in the world it fits.
- **Plot:**
  - Today **19 (x) × 17 (z)**, and `theStockadeLiesOnUnevenGround` pins it:
    - it asserts the corner lands at helper (6, 3, 7) for a middle at (15, 3, 15), which only 18-19 × 16-17 gives;
    - it checks the ground under all 19 × 17 columns.
  - **Keep 19 × 17** and the test needs no change. Another size means the ship step changes that corner and that
    footprint in the test.
  - Hard limits from the test ground (`tall_area`, 30 × 30 × 30, grass at helper y 3): at most **27 wide (x)** (a
    player's cobblestone at helper (1, 4, 15) must stay outside the box) and **29 deep (z)**. At most **26 high**
    (template y 0 sits at helper y 3).
  - The chest, the iron door and the hay must stay within 11 blocks across of the middle and at template y 0..4,
    whatever the size.
- **Front and way in:** the front is the template's z = 0 side. The test pins the fence gate on it at (8, 1, 0). The
  showcase films the stockade from the front (from -z, about 22 blocks out and 12 up) and the carter walking out that
  way, so the way in and out is on the front. The rotation is random in the game, so nothing faces a village.
- Neighbours: none. It stands alone in the wild, 120 to 180 blocks past the wreck. The last trail mark lies on the line
  from the wreck towards it.

## Rules tests check
- **`CaravanTrailGameTests.theStockadeLiesOnUnevenGround`** (`tall_area`, batch `raidersStockadeGround`). The stockade
  is built through `ArcEffects.build` at middle (15, 3, 15), on grass with a 2-high hump at helper x 11-12, z 11-12 and a
  2-deep hole at x 18-19, z 17-18:
  - it is placed (not skipped) with its corner at (6, 3, 7);
  - **`lies` over all 19 × 17 columns:**
    - **no air at template y 0 or y -1** in any column. The template must not store air at y 0: a pit or sunken floor
      that leaves air fails. Structure voids at y 0 keep the ground and pass.
    - **No `minecraft:dirt` or `minecraft:grass_block` at template y 1 or 2 anywhere** (the "hump still stands" check).
      Raised beds or mounds of dirt or grass fail; other ground blocks are not checked.
    - the player's cobblestone at helper (1, 4..5, 15) is untouched.
  - the eight pinned blocks above, with the iron door shut and the chest's loot table `aliveworkplace:chests/bandit_camp`.
- **`LostCaravanGameTests`, `QaLostCaravanGameTests`, `QaLostCaravanThreats1031GameTests`** build their own stand-in cage
  (door at helper (25, 2, 22), hay at (26, 2, 25), chest at (20, 2, 25), middle at (23, 2, 23)), not this template.
  Their stand-in is the shape the code expects, and the real build must match it: the hay 1 across and 3 back from
  the door, the carter 2 back from the door, the only chest and only iron door near the middle.
- Every shipped structure (this one too):
  - `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`: every standing lantern, candle and torch rests on
    a block whose top holds its middle; no dirt path or farmland under a solid block; every bed has both halves.
  - `B648GameTests.b648NoShippedGlassEndsInTheAir`: **no iron bar or glass pane joined on exactly one side with air on
    the opposite side**. A run of bars ends against a block, not in the air.
  - `BlueprintListCacheGameTests` and `BlueprintsStayGameTests` count every `.nbt`: the file stays.
- The owner's rules (check.py) and STYLE.md's checks:
  - `rebuild.py --candidate` prints no CHECK line;
  - `holes.py` passes (a cage's open bar walls may need an ALLOW line);
  - `story/raiders_stockade` (baselined at 2) comes off `owner_rules_baseline.json` once rebuilt.
- Showcase (not a GameTest; its scene fails on the nightly page). Scenes `caravan_trail` (step `03_stockade`) and
  `lost_caravan` (`03_freed`, `04_home`) check:
  - an iron door at `CAGE_DOOR`;
  - 32 oak logs in the chest at `LOOT_CHEST` after the arc's own build (so it must be the nearest chest);
  - the carter standing at `CAGE_DOOR + (0, 0, 2)`, filmed from 4.5 blocks in front of the door (-z), looking into the
    cage.
- Ship step, not the designer's:
  - The id has a folder, so the live save is `rebuild.py raiders_stockade story/raiders_stockade`.
  - `generate.py`'s fixtures function (`test_hut`) must stop redrawing the old stockade: guard its
    `raiders_stockade().save(...)` with `if "raiders_stockade" not in REBUILT`, as the Shift Bell's is.
  - Move any test pin the design moved, then run `CaravanTrailGameTests` and the Lost Caravan tests.

## Free
Everything not listed above: what the enclosure, the cage, the tent, the fire's surroundings and the yard are made of
and how they are shaped, roofed, floored and lit; the height of everything; whatever else stands inside the plot (except
another plain chest, another iron door or a nearer hay block); and the whole look.
