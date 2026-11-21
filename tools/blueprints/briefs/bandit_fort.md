# Bandit Fort: the brief

Family id(s): `story/bandit_fort`, `story/bandit_fort_2`, `story/bandit_fort_3` (never renamed: `BanditFort.STAGES`
and `arcs/bandit_king.json` load them by these ids)
Variants: village (none), modded (none), looks (none)

Not in this family: the Bandit Camp (`camp/bandit_camp`, the arc's chapter 1 camps), the Raiders' Stockade
(`story/raiders_stockade`) and the rest of 52.5's story pieces; each is rebuilt on its own.

## What it is for
The Bandit King's stronghold, the second chapter of his story arc (ROADMAP 31.15 and 31.16, `docs/wiki/bandit-king.md`).
No builder builds it and it is not on the Blueprint Table or the village map (`BlueprintLibrary` and `VillageMaps` hide
every `story/` id). The arc sets it down whole, one level each dawn, 110 to 150 blocks from the village hall on natural
ground: Level I is the bandits' walled yard with its gate, fire and tents and four bandits; Level II adds two
watchtowers with a pillager on each, and a stable; Level III adds **a keep for the Bandit King** with his throne, his
banner over its door, his treasure chest and a postern door at the back. The players storm it: the King comes out of the
keep to fight in the yard, at half health he blows his horn and four bandits come out of the keep's door, and the
village gets the treasure chest when he falls. A player who wins Pell the deserter over gets the postern left open (a
back way in). If the King outlasts the storm, the fort is left a ruin (code knocks out a share of its blocks).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `story/bandit_fort` | An outer wall round a yard, a gate in the front, a fire, tents; 4 bandits spawned in the yard | None: placed whole by the arc (`ArcEffects.build` / `BanditFort.place`), no materials, no builder. Placed the first dawn of chapter 2 once a player is within 96 blocks. |
| II | `story/bandit_fort_2` | Everything of I, plus two watchtowers on the front corners (each with a ladder up and a platform a pillager stands on) and a stable; 2 pillagers spawned on the platforms | The next dawn after I. |
| III | `story/bandit_fort_3` | Everything of I and II, plus the King's keep (door facing the yard, his banner over it, a throne, the treasure chest) and a postern door in the outer wall behind it; the King spawned before his throne | The next dawn after II. Its arrival sets the `fort_done` flag that starts chapter 3 (the storm). |

**Each level is a whole template placed over the one before it in the same box, same corner, same turn** (not a
delta). The old files save air in every empty cell (no structure voids), so a later level replaces whatever the earlier
one had that it doesn't redraw. Keep it that way: **no structure voids anywhere** in any of the three. That is also what
keeps old saves working: on the owner's live server an arc halfway through will set the new Level II or III over an
old Level I or II, and only a full box (air included) wipes the old drawing cleanly.

## Must have (each level)
- Job block(s): none. No villager works here; nothing looks for a workstation.
- Beds and residents: none counted. The residents are mobs the code spawns at fixed spots (below), not villagers. Beds
  are free (if any are drawn, they become furniture spots and the furniture rules below apply).
- Storage: **Level III only: the treasure chest at (18, 1, 24)**, a plain `minecraft:chest` (tests check
  `ChestBlockEntity` and `is(Blocks.CHEST)`, so no trapped chest and no barrel), single (only the block at (18, 1, 24)
  gets the `chests/bandit_king` loot, so a double chest's other half would be empty). It belongs to the keep (31.15:
  "the King's keep ... and the treasure chest"). The code fills it with loot when Level III is placed; the ruin and the
  King's fall leave chests standing. No other container is read; others are free.
- Furniture kinds a job or test needs: none. Today's `blueprint_furniture/story/bandit_fort*.json` hold only the tents'
  beds (5 per level); no code reads them. If the rebuild's plan records furniture spots, every furniture test applies to
  them (see Rules).
- **Fixed spots the Java reads** (`guard/BanditFort.java`, template coordinates, front z = 0; all are turned with the
  fort, so they hold in every rotation). Moving one means changing the Java constant and the tests with it, so keep
  them:

  | Spot | Template cell | Levels | What must be there |
  |---|---|---|---|
  | `YARD`, the yard's middle | (14, 1, 14) | all | Open yard: air at (14, 1..2, 14) on a solid (14, 0, 14). Every fort man and the King is leashed within **17 blocks** of it (`KEEP`), so the walls and the keep are inside that reach and the yard is where they fight. A test teleports a player onto it. |
  | `YARD_MEN`, 4 bandits (vindicator, pillager, vindicator, pillager) | (12, 1, 8), (16, 1, 8), (11, 1, 14), (17, 1, 14) | all three (they spawn at I and stand there when II and III are set over them) | Air at y 1 and y 2, a solid block at y 0 under each. A later level must not put a block in these cells (the man standing there would suffocate). |
  | `TOWER_MEN`, the watchtower platforms | (2, 6, 2) and (26, 6, 2) | II and III | A pillager is spawned standing at y 6: **a solid floor at (2, 5, 2) and (26, 5, 2)**, air at y 6 and y 7 above each. Leashed within 2 blocks of the spot (the wiki: "the tower pillagers keep to their platforms"), so a platform at least that wide with a parapet is the intent. In Level I these cells are free. |
  | `KEEP_DOOR`, outside the keep's door | (14, 1, 15) | III | Where the horn's 4 bandits appear: at the door spot offset ±1 in world x and 0 or −1 in world z, which in the four rotations covers **x 13..15, z 14..16**. All of those cells need air at y 1 and y 2 on a solid y 0. So the keep's door opens onto open yard, with nothing standing in the 3 x 3 in front of it. |
  | `THRONE`, the King's place before his throne | (14, 1, 21) | III | The King spawns standing here: walkable (air or a carpet) at y 1, air at (14, 2, 21), a solid (14, 0, 21). Inside the keep, reachable on foot from the keep's door (he comes out to fight in the yard). |
  | `TREASURE` | (18, 1, 24) | III | The treasure chest (above). In I and II the cell is free. |
  | `POSTERN`, the postern door | lower half (14, 1, 28), upper half (14, 2, 28) | III | **A door** (any `DoorBlock`, both halves), **placed shut**; the code opens both halves (`BanditFort.setPostern`) when the story says so. z = 28 is the back edge of the box: it is a door in the outer wall behind the keep, a back way in (Pell's secret). In I and II the cells are free (the old drawing has wall there). |

- **Blocks the tests read by id** (`BanditFortGameTests.stageOneStands` / `stageTwoStands` / `keepStands`, and the
  showcase scene's checks). These pin a block to a cell; they are the only places this brief names a block. Keep them
  at these cells, or the ship step moves the test (and the showcase check) with the drawing: the same meaning at the
  new cell, never dropping a check:

  | Level from | Cell | Block | The test's meaning |
  |---|---|---|---|
  | I (and still in II, III) | (0, 1, 7) | `spruce_log` | "no palisade": the outer wall on the west side |
  | I (and still in II, III) | (14, 1, 0) | `spruce_fence_gate` | "no gate": the gate, in the middle of the front edge |
  | I (and still in II, III) | (14, 1, 11) | `campfire` | "no fire" |
  | I (and still in II, III) | (5, 3, 7) | `stripped_dark_oak_log` | "no tent" |
  | II (and still in III) | (0, 7, 0) | `spruce_log` | "no watchtower post": the west tower at the front corner |
  | II (and still in III) | (1, 3, 2) | `ladder` | "no ladder up the west tower" (also the showcase's `02_towers` check) |
  | II (and still in III) | (27, 3, 2) | `ladder` | "no ladder up the east tower" |
  | II (and still in III) | (27, 1, 15) | `hay_block` | "no stable" |
  | III | (14, 1, 17) | `dark_oak_door` (lower half) | "no keep door": the keep's door, facing the yard |
  | III | (14, 4, 16) | `black_wall_banner` | "no banner over the door" (also the showcase's `03_keep` check) |
  | III | (14, 2, 24) | `dark_oak_stairs` | "no throne" |

  A ladder needs a block behind it to hang on; a wall banner hangs on the block on its back side.
- **The ground layer**: template **y = 0 is the ground itself**, not the floor on top of it (`BanditFort.at` puts y = 0
  at the top ground block; unlike a village blueprint, it replaces that block). **Every one of the 29 x 29 cells at
  y = 0 must be a solid, non-air block in all three levels** (`groundHolds`: "the ground under the fort is open"). The
  code fills up to 4 blocks of air under y = 0 with dirt; nothing may go below y = 0.
- Anything else the code or a test reads: nothing. No bell, sign, lectern, water or pen is read. The fire is read only
  by the test above (the ruin puts every campfire out).

## Fits
- Plot: **exactly 29 x 29 (x by z), at most 18 high (y 0 to 17)**, the same box for all three levels. The template's
  size stays **29 x 18 x 29**: the code centres the fort with `HALF = 14`, `SIZE = 29`, `HEIGHT = 18`, and the arc
  centres it by the template's own size, which the tests require to land on the same corner (`fort.origin` at helper
  (1, 3, 1)). The tests set it on grass at helper y = 3 in a 30-tall area (`tall_area`), and one test builds a nether
  portal at helper y = 22 above Level I, so nothing may rise above template y = 17.
- How it lands: centred on a ground block 110 to 150 blocks from the hall (`BanditFort.site`), on natural ground that
  passes the bandit camps' flatness test widened to 29 x 29, with nothing a player built in the box. **Turned at random
  a quarter** (`ArcEffects`: `level.random.nextInt(4)`), so everything must work in all four rotations (the showcase and
  tests use no rotation). Level I clears the box of grass and plants first (`Lairs.clearAndPlace`).
- Front and way in: **the front is the z = 0 side; the gate is in the middle of it at (14, 1, 0)**. The keep's door
  faces the yard (towards the front). The postern is in the back edge at (14, 1, 28). No path or step outside the box is
  expected.
- Neighbours: none. It stands alone in the wild; nothing of the village is planned beside it. In the tests a player's
  cobblestone stands one block outside the west edge (helper (0, 4..5, 15)) and must be untouched, so nothing may lie
  outside the 29 x 29.

## Rules tests check
- `BanditFortGameTests.threeStagesPlaceInOrderOnUnevenGround` (`tall_area`, middle at helper (15, 3, 15), uneven ground:
  a hill near the front, a 2-deep hole at the back left; batch `banditFortStages`): places I, II, III in turn with no
  rotation. After I and after III: every y = 0 cell of the footprint is solid with ground under it, and the hill is gone:
  **template (9, 1, 3) and (9, 2, 3) must be air in Levels I and III** (keep them air in all three). After each level:
  the id pins of that level and every earlier one (the table above); **4 fort men after I, 6 after II**; after III, one
  King, the treasure chest's loot table is `chests/bandit_king`, the postern is a door placed **shut**, and opening it
  opens both halves; every man is leashed near the yard; the player's wall outside stands.
- `BanditFortGameTests.theArcPlacesAStageADawn` (batch `banditFortArc`): the same three levels through the arc
  (`ArcEffects.build`), same corner; I's ground and pins, II's pins, III's pins; the postern placed **open** when the
  story's flag is set; one King.
- `QaBanditFortGround1108dGameTests.eachStagesMenStandOnTheirSpots` (batch `qaFortMenSpots1108d`): after I, a man stands
  exactly on each of the 4 yard spots; after II, a pillager stands exactly on (2, 6, 2) and on (26, 6, 2).
- `QaBanditKing1028GameTests.qaFortResumesFromASaveMidArc` (batch `qaBanditFortSaved`, flat ground): two levels, a save
  and reload, then III on the same corner: the chest with its loot, the postern a door and open, **7 men** (4 + 2 + the
  King).
- `BanditKingStoryGameTests.banditKingStoryWalk`: the whole story with the three levels placed; the postern shut until
  Pell talks, then open; one King found within 30 blocks of the yard; a player stands on the yard; after the King falls
  the treasure cell is still a `chest`.
- `QaPortalThreatBugTests.aBanditOfTheFortStaysOutOfAPortal` (batch `qaPortalFort`): Level I on flat ground has men; a
  portal is built at helper (10, 22, 10), above the fort's top.
- Showcase `bandit_fort` (`JobScenes`, steps `01_palisade`, `02_towers`, `03_keep`): 4 men after I; a ladder at
  (1, 3, 2) after II; one King and a black wall banner at (14, 4, 16) after III.
- Every shipped structure (these three are read too):
  - `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`: standing lanterns, candles and torches on a
    block whose top holds their middle; no dirt path or farmland under a solid block; every bed with its other half.
  - `B648GameTests.b648NoShippedGlassEndsInTheAir`, `QaWindowsAndWards1028BugTests.qaNoShippedGlassEndsInTheAirAsTheGameJoinsIt`
    and `QaBuilders1028GameTests.qaB648NoShippedGlassEndsInTheAirAsTheGameJoinsIt`: no glass pane or iron bar joined on
    one side with air on the other.
  - `StationsSpecGameTests.noStructureOfOursUsesAGoneBlock`: none of the mod's removed blocks.
  - If furniture spots are recorded: `FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`,
    `FurnishedRoomsGameTests` (each piece on what it stands on, nothing in a door's way, a bed's head to a wall;
    `story/` is exempt from "every furnished room has a light"), `FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`,
    `QaFurnishedRooms1031GameTests` (no lantern standing on a room's floor, a chest spot against a wall or a bed).
  - `BlueprintListCacheGameTests` and `BlueprintsStayGameTests` count every `.nbt`: the three files stay.
- The owner's rules (`check.py`) and STYLE.md's checks: `rebuild.py --candidate` prints no CHECK line for each level
  (`story/bandit_fort_3` comes off `owner_rules_baseline.json` once rebuilt), and `holes.py` passes.
- Ship step (not the designer's): the live saves are `rebuild.py bandit_fort story/bandit_fort`, `... story/bandit_fort_2`
  and `... story/bandit_fort_3`; `generate.py`'s unconditional `bandit_fort` loop must stop saving the old drawing
  (`REBUILT`); the recipe needs a `furniture.json` (`{"spots": []}` if none); the showcase scene text
  (`JobScenes` and `tools/showcase/scenes.py`) and `docs/wiki/bandit-king.md` describe the old look and change to match.

## Free
Everything not listed above: what the wall, gate, towers, stable, tents and keep are made of and how they are shaped,
roofed and laid out (other than the pinned cells), what else stands in the yard, and the whole look, inside the
29 x 18 x 29 box, with y = 0 solid ground and no structure voids.
