# Arena: the brief

Family id(s): `arena`, `arena_2`, `arena_3` (never renamed; `StarterBlueprints.ARENA`, `ARENA_2`, `ARENA_3`, the list
`ARENAS`). Variants: village (none), modded (none: the modded library has no copy of the arenas,
`docs/wiki/furniture-and-storage.md`), looks (none). Styled copies (`BlueprintStyles`) count as the same tier
(`Arenas.tier` goes by `BlueprintStyles.base`). Not in this family: the Stadium Wonder (`wonder/stadium/*`), which can
stand in for an Arena but has its own brief.

ROADMAP 28.16 (the Arena), used by 28.17-28.22 (the Festival Cup). Rebuild: ROADMAP Milestone 49.

## What it is for
The village's battle ground for the **Festival Cup** (Cobblemon only). A Village-rank (or higher) Cobblemon village
with a Village Hall and a finished Arena in its claimed area holds its festivals as a Cup (`Cups.hostProblem`,
`Arenas.find`): on Cup day the delegates of partner villages walk in to the **ring**, a fair of wandering traders opens
on the **fair lane** (by the **notice board** in Arena I), the villagers off work walk to the **seats** and sit facing
the ring, the bard plays and the feast is laid at the ring, bouts are called with the two trainers teleported into
the two **trainer's boxes** facing each other across the ring, their Pokémon come out on the field, a player can
right-click the notice board to watch a player bout, and after the final the Cup banner flies on both faces of Arena
III's **champion's pole**. An Expert (level 4) Trainer Leader sells Arena I's blueprint; II and III are its upgrades.
The hall's "What next?" suggests an Arena to a Cobblemon village of Village rank with a grown-up Trainer Leader and no
Arena of any tier or style.

All of these spots are **code constants** in `hall/Arenas.java` (template coordinates from the corner, turned and
mirrored with the build), not read from the blocks. A new drawing puts its spots where it likes; the ship step writes
them into `Arenas` (and the tests that pin them, below) and notes it.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| Arena | `arena` | The ring, the two trainer's boxes, **12 seats**, the notice board (which is also where Arena I's fair stands), two banner poles, a lamp by each box | Cobblemon installed (`COBBLEMON_ONLY`) and the `festivalCup` expansion open (`EXPANSION_SWITCH`); sold for 20 emeralds by an Expert Trainer Leader only (`ModTrades.trainerLeaderTrades`, `BlueprintUnlocks.VILLAGER_SALES`) |
| Arena II | `arena_2` | Seating on both long sides of the ring, **30 seats** in all; a **fair lane** (the Cup's traders stand there instead of at the notice board); a gate (spec) and lighting round the ring (spec) | Upgrade of `arena` (by name, `BlueprintUpgrades.baseOf`); same origin and front |
| Arena III | `arena_3` | A covered grandstand (spec), **52 seats** today; a **trainers' room at each end with a Healing Machine in each**; the **champion's pole** before the gate | Upgrade of `arena_2`; same origin and front |

Names are player text and stay: "Arena", "Arena II", "Arena III" (`blueprint.aliveworkplace.arena*`;
`ArenaGameTests.theArenaIsTranslated` checks "Arena III"). The `built/arena*.json` advancements go by id.

## Must have (each level)
- **The ring** (every tier, the same template cell in every tier, because `Arenas.RING_CENTRE` is one constant for all
  three): a **15 x 9 battle floor** (15 along x, 9 along z, the owner's spec and `QaArenaGameTests`) round the ring
  centre, with **nothing standing on it** (every cell above the 15 x 9 floor is air) and the floor no larger than
  15 x 9. The ring centre is where delegates gather and Pokémon come out, the feast and the bard go, and fireworks go
  up: open, walkable floor reached on foot from the way in.
- **Two trainer's boxes** (every tier, the same two cells in every tier: `Arenas.BOXES` is one constant), one at each
  end of the ring's long (x) axis, facing each other across it. The trainer is teleported onto the box spot
  (`CupBouts.toBox`) and turned to the ring: the box spot is room to stand (air in the cell and the one above, solid
  under it), with a view of the ring. Both boxes are the **same distance from the ring centre** (today Manhattan 11,
  asserted in `ArenaGameTests.assertSpots`).
- **A lamp by each box** (Arena I, `QaArenaGameTests`): a block giving light 10 or more within 4 blocks (x and z) of
  each box, on that box's side of the ring.
- **Seats** (`Arenas.seats(tier)`): **exactly 12 in Arena I, 30 in Arena II, 52 in Arena III** today
  (`ArenaGameTests.theVillagesArenaIsItsHighestTierWithinReach` and `assertSpots`). Arena II must seat both long sides
  of the ring (spec). A different Arena III count is a ship-step change to `Arenas.seats` and both tests (never fewer
  than II's 30). Each seat is:
  - a **stair** (`StairBlock`), the villager sitting on it (`StandSeat`);
  - with its **high back away from the ring** (the sitter faces the ring; `assertSpots` checks the stair's `facing`
    against the direction to the ring, so it holds turned and mirrored);
  - with **air above it**;
  - **walkable to**: a villager walks until within 2.5 blocks of it, then sits (`CupDays.SIT_REACH`); seats are filled
    nearest-villager first.
  - Seats are listed by position in code, so other stairs may exist; but every seat cell the code lists must be one.
- **The notice board** (every tier, the same cell: `Arenas.NOTICE_BOARD` is one constant): a block a player
  right-clicks while a player bout is fought; any block within 2 of the spot counts, a block 3 away must not
  (`CupMatches.noticeBoard`, `QaCupWatchBoard1115d2CompatTests.theNoticeBoardShowsTheBoutAndWatch`). It must be within
  40 of the ring centre, reachable and clickable from outside (the spec: its sign faces the street). In **Arena I the
  fair stands at the board**: traders spawn on standing spots within 4 blocks (x, z) and -2..+3 (y) of it
  (`MarketDays.spot`), so leave open, walkable ground round it.
- **Two banner poles** (Arena I, spec; `QaArenaGameTests`): banners on exactly **two distinct x columns**.
- **The fair lane** (Arena II and III, the same cell in both: `Arenas.FAIR_LANE`): the lane spot is **air with a solid
  block under it** (`assertSpots`), and the Cup's traders spawn on standing spots within 4 blocks of it, so it is open,
  walkable ground with room for several traders, reached on foot.
- **The champion's pole** (Arena III only, `Arenas.CHAMPION_POLE`, the pole's top cell): the pole's top cell is **not
  air**, and the cells **directly in front of it and behind it** (template -z and +z at the same height) are
  **replaceable and dry**: the Cup banner is hung there as wall banners on both faces (`CupChampions.put`). Today the
  front cell is just outside the drawing (z = -1), which is allowed. Before the gate, on the front (spec).
- **Healing Machines (`cobblemon:healing_machine`): Arena III, one in each trainers' room** (spec;
  `ArenaCompatTests.aBuilderBuildsArenaIIIWithItsHealingMachines` checks each at its spot, today (3, 1, 19) and
  (21, 1, 19); the plan must need a Cobblemon block). Each is a real Nurse job site (`ModVillagers.HEALING_MACHINE_POI`)
  and must be walkable to from outside (within 2 blocks of a walkable cell, `QaBuildReachGameTests`).
  - Without Cobblemon a Healing Machine reads as **air** (the main suite builds and checks every tier that way), so
    nothing may rest on it, hang from it or join the build only through it.
- **Trainers' rooms** (Arena III, spec): one at each end, each with its Healing Machine; doors whole, with clear head
  room on both sides (library-wide doorway checks).
- **Job blocks otherwise: none required.** Any other job block drawn is a real job site a villager will claim (the old
  II and III have barrels, which are fishermen's). Every chest, barrel and job block must be walkable to from outside
  (`QaBuildReachGameTests`).
- **Beds and residents: none.** No code counts a resident. A bed drawn must be whole (`BlueprintsStayGameTests`).
- **Storage: none required.** No job reads a container here.
- **Furniture kinds: none** (there is no `blueprint_furniture/arena*.json`; the arenas record no furniture spots).
- **Every block buildable** (`theArenasBlueprintsAreBuildableUpgrades`: none `MaterialRules.Kind.SKIP` except second
  halves), and the full material list fits 16 barrels of 27 stacks (432 stacks, the build tests).

## Fits
- **Plot: at most 29 x 29 every tier** (`ArenaGameTests` asserts it; the spec: "fits `huge_area`"); **Arena I at most
  25 (x) x 19 (z)** (`QaArenaGameTests`, the spec). In practice **at most 25 wide (x)** every tier: the build tests
  put the bench at (27, 2, 1) and barrels at x 26-29 beside a build whose corner is at (0, 2, 0) in `huge_area`.
- **Height: at most 14** every tier: `arenasFindTheirSpotsInEveryRotationAndMirrored` places all 24 turns and mirrors
  in one area and clears only y 0-13 of the build between them (the old tops out at 12). Taller is a ship-step change
  to `clearArea`.
- y = 0 sits on the clicked block; the front is the z = 0 side. Every tier shares the origin and the front.
- **The upgrades grow the one before:** each tier keeps **at least 60%** of the previous tier's non-air blocks
  unchanged at the same cell (`theArenasBlueprintsAreBuildableUpgrades`), and the shared spots (ring, boxes, notice
  board; fair lane in II and III) are the same cells.
- **Declared sizes:** `StarterBlueprints.ARENA` (25, 8, 19), `ARENA_2` (25, 9, 29), `ARENA_3` (25, 12, 29) must equal
  the `.nbt` sizes (asserted); the ship step updates them.
- **Arena III not small:** `QaRenewalGameTests.qaABuildingTooBigForThePlotIsPassedOver` uses `arena_3` as the
  building too big for a vanilla plains small house's or fletcher house's plot: its **shorter side must stay larger
  than those houses' footprint plus 6** (`Renewals.MARGIN` 3 each side; the old 25 passes).
- **Front and way in:** the z = 0 side; on foot from the front to the ring, both boxes, every seat, the notice board
  (from the street side), the fair lane and the trainers' rooms. The champion's pole stands before the gate.
- **Neighbours:** none fixed. No Steward list or village plan places an Arena; the player chooses the spot.
  `Arenas.find` takes the highest tier, then the nearest, in the village's claimed area (its middle by declared size).

## Rules tests check
Function rules on the drawing:
- `ArenaGameTests.aBuilderBuildsTheArena`, `aBuilderBuildsArenaII`, `aBuilderBuildsArenaIII` (main suite, Healing
  Machines as air): built from bare ground in `huge_area` within 30,000 / 45,000 / 60,000 ticks from barrels holding
  exactly the plan, nothing unfinished, then `assertSpots` on the built blocks.
- `ArenaGameTests.arenasFindTheirSpotsInEveryRotationAndMirrored`: every tier placed in 4 rotations x plain and
  mirrored front to back; `assertSpots` each time (ring, boxes, seats, fair lane, pole, board; see the pins below).
- `ArenaGameTests.theVillagesArenaIsItsHighestTierWithinReach`: seat counts 12 / 30 / 52; II has a fair lane and no
  pole; a styled Arena counts.
- `ArenaGameTests.theArenasBlueprintsAreBuildableUpgrades`: sizes as declared, at most 29 x 29, all buildable, all in
  `COBBLEMON_ONLY`, each upgrade keeps 60% of the one before.
- `QaArenaGameTests.everyArenaHasAFifteenByNineRingAndTheFirstFits`: Arena I at most 25 x 19; every tier a 15 x 9 ring
  round `RING_CENTRE.below()`, nothing above it, no larger; Arena I's banners on two x columns and a lamp by each box.
- `ArenaCompatTests.aBuilderBuildsArenaIIIWithItsHealingMachines` (compat): Arena III built for real with Cobblemon, a
  Healing Machine at each `MACHINES` spot.
- Library-wide: `QaBuildReachGameTests` (chests, barrels, beds, job blocks reachable), `BlueprintsStayGameTests`
  (lanterns, candles, torches on a top that holds them; no path or farmland under a solid block; beds whole),
  `B648GameTests` / `QaWindowsAndWards1028BugTests` (no pane or bar joined to air), the doorway checks.
- By id and spot constants only (no drawing; they follow `Arenas` when it changes): `ArenaGameTests`
  (`withoutCobblemonThereIsNoArena`, `theHallSuggestsAnArena`, `theHallCountsTheTrainerLeader`,
  `theArenaIsTranslated`), `ArenaCompatTests.anExpertTrainerLeaderSellsTheArena`, `CupGameTests`, `CupDayGameTests`,
  `QaCupDayGameTests`, `QaCupGameTests`, `CupChampionGameTests`, `QaCupChampionGameTests`,
  `QaCupWatchBoard1115d2CompatTests`, `QaVillageReach1102GameTests.anArenaInAZonePastTheRadiusIsTheVillagesArena`
  (by declared size), `ExpansionBuildsGameTests`, `TablePreviewGameTests`, `StadiumPowerCompatTests`.
- The owner's rules (`check.py`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no CHECK line.
  The old `arena_2` and `arena_3` have one baselined problem each (`owner_rules_baseline.json`); the rebuild has none
  and the ship step removes those lines.

**Old-spec pins** (`ArenaGameTests.assertSpots` and `QaArenaGameTests` check these exact blocks; they describe the old
look, not a function. Don't design to them: the ship step points each check at the new drawing's own marker block,
keeping the kind of check, and notes it):
- the block under the ring centre is a particular centre-spot block, and the 15 x 9 ring floor is one floor block
  inside a contrasting edge line (with at least half of it the floor block);
- the block under each box is a particular dais top on a particular dais block;
- the block under the fair lane is one of three paving blocks;
- the champion's pole top is a particular log with a particular block above it;
- the notice board spot is a particular plank;
- `CupChampionGameTests.POLE` (15, 7, 1) and the Healing Machine spots in `ArenaCompatTests.MACHINES` are the old spots.

Ship-step updates (not the designer's): `Arenas` (ring, boxes, seats rows, fair lane, pole, board), the pins above,
`StarterBlueprints` sizes and their Javadoc, `generate.py`'s `ARENAS` loop (add to `REBUILT`), the showcase scene
`arena` (`tools/showcase/scenes.py` and its harness), `docs/wiki/festival-cup.md`'s 28.16 paragraph (it describes the
old look). An Arena site begun on the old drawing must still finish as begun (REBUILD.md).

## Free
Everything not listed above: the shape, materials, style and colors; how the seating is massed, stepped or roofed;
where the gate, the stands, the grandstand, the trainers' rooms and the fair stand within the plot; what the fair lane,
the boxes, the notice board and the poles look like; and everything Arena II and III add beyond their spec features,
as long as each tier grows out of the last.
