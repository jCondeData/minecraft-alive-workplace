# Basements and cellars

Builds that reach under the ground: a Level 4 for eight homes and workshops that topped out at III, each its Level III
with a furnished, lit basement dug under it, and cellars from Level 1 for the jobs that suit one: the Butcher's
freezer, the Smith's coal store, the Cook's cold larder and the Storehouse's vault (the owner, 2026-10-16: "buildings
should also get basements in a new Level 4 version, but some buildings should get basements in general at any level,
like a butcher maybe has a freezer").

Roadmap items: 26.15

## 1. What a player sees

A builder who finishes a Level III sells its Level 4, like every upgrade. Clicked onto the finished house, it lines up
with it; the builder digs a room out of the ground under the house, walls and floors it, puts in the way down and the
furniture, and leaves the house above as it was. Every basement is three blocks high, lit by lanterns hanging from the
floor above, and has a ladder or a stone stair down from inside.

| Build | The basement | Way down |
|---|---|---|
| Starter Cottage IV | a root cellar: bins of pumpkins and melons, hay, sacks (chests), mushrooms in pots on a shelf, a sorting table | ladder by the kitchen door |
| Stone House IV | a wine cellar: racks of casks, chests and pots of wine, a tasting table with two chairs and a candle | ladder by the front door |
| Burgher's Townhouse IV | a brick-vaulted cellar of casks with the coal store in a corner | stone stair from the hall |
| Noble's Manor IV | a great wine cellar under the hall, held up by two stone columns | stone stair beside the hall's table |
| Supply Shop IV | a stockroom: chests two high, bolts of cloth, crates, a packing table | ladder behind the counter |
| Library IV | an archive: bookshelves round the walls, chests of old ledgers, a reading table | stair along the back of the reading room |
| Healing Center IV | a cool store: shelves of potted herbs and fungi, kelp and moss, chests of remedies | ladder in the back room |
| Lookout Tower IV | an armory and lock-up: a cell behind iron bars, an anvil, chests of kit, hay targets | ladder by the guardroom door |

Cellars from Level 1:

- **Butcher's Shop** (new). A brick-and-plaster shop under a dark oak roof with a red-and-white awning, sides of meat
  hanging in the shop windows, a stone counter, the smoker (the Butcher's) in a brick chimney breast, a fenced pen
  behind. Under it the **freezer**: walls of packed ice, a blue-ice path down the floor, sides of meat and bones on
  chains along the walls, chests of frozen stock, snow heaped in a corner. The ice never melts (packed and blue ice
  don't).
- **Smithy** (and Smithy II): a **coal store** down a ladder by the forge: heaps of coal behind boards, raw iron, chests of ore.
- **Kitchen** (the Steward's workplace): a **cold larder** down a ladder inside the door: a stone counter under sides of
  meat and pumpkins, a shelf of jars, chests of flour and salt.
- **Storehouse** (and II and III): a **vault** down a ladder beside the loft's: chests two high round a stone room, a
  barred strongroom corner.

## 2. How it works

- A blueprint's y = 0 still sits on top of the clicked block; a basement is simply part of the build below y = 0. The
  builder needs nothing new for it: CLEAR already takes away, top down, whatever stands where the blueprint wants air
  or another block, so it digs the room out of the earth (the dug dirt and stone go into his bag, as any cleared block
  does); FOUNDATION, STRUCTURE and DECORATION then build the floor, walls, ladder and furniture bottom up. Progress
  lives in `BuildSite` as for any build, so a site saved mid-dig carries on from its cursor.
- In the file the basement is drawn at the bottom (its floor at the file's y = 0, the ground floor at y = 4).
  `StarterBlueprints.Entry` carries each build's `basement` depth; `StarterBlueprints.withBasement` moves the blocks of
  such a build down by it when the library loads it, so the blueprint the game uses has its basement at y = -4 to -1
  and its size is the part above the ground (what the Blueprint Table and the item show). An upgrade with a basement
  therefore shares its Level III's origin exactly.
- The plan's box takes the basement in (`BuildPlan.bounds`), so its chests count as the build's, not as supply
  chests; the landscaping still goes by the ground floor (a pit is never dug round the house).
- The foundation never fills a spot of the build itself: under a ground floor over the basement the foundation stops
  at the dug room. Worked out again after a reload (the foundation and landscaping lists come from the ground as it
  stands), it would otherwise have filled the room with cobblestone. Ground-floor blocks with no basement under them
  (a porch) get their footing as before.
- Builders never break containers or the bench while clearing: a chest buried where a basement goes stays (its spot is
  skipped). Water or lava met while digging is left alone, as in any clearing.
- No job block in a basement but the job's own: casks are spruce or dark oak log ends, stock is in chests, so a cellar
  never hands a villager a job. The Butcher's Shop has the smoker upstairs.
- Unlocks: the Level 4s come the way every upgrade does (the builder who finishes the III sells it; a builder's level-up
  unlocks the next tiers at the Table). The Townhouse IV and Manor IV are sold by builders only, like their other
  tiers. The Butcher's Shop is a one-tier build in the Blueprint Table, unlocked by a builder's level-up.
- Each new build has a hidden `aliveworkplace:built/<name>` advancement (from `tools/questbook/generate.py`).

## 3. Switches

None. `blueprintUnlocks` decides, as for every build, whether the new ones start locked. The blueprint's "leave the
ground" switch and the foundation leave a basement alone: it is the build, not the ground round it.

## 4. Saved data

None new. Sites and finished builds of the Smithy, Kitchen and Storehouse keep their ids and origins; a Smithy,
Kitchen or Storehouse finished before 26.15 has no cellar, and a repair doesn't dig one (a repair only fills holes, and
the earth where the cellar would be isn't a hole). One started after the update digs it.

## 5. Items, blocks, jobs, commands

Blueprints `aliveworkplace:starter_cottage_4`, `aliveworkplace:stone_house_4`, `aliveworkplace:townhouse_4`,
`aliveworkplace:manor_4`, `aliveworkplace:supply_shop_4`, `aliveworkplace:library_4`, `aliveworkplace:healing_center_4`,
`aliveworkplace:lookout_tower_4` and `aliveworkplace:butchers_shop` (names under `blueprint.aliveworkplace.<name>`, such
as `blueprint.aliveworkplace.butchers_shop`).

## 6. Decisions

- The basement is dug in CLEAR, not FOUNDATION as the roadmap item first said: CLEAR already digs, top down and
  restartable, everything a blueprint wants empty, and never breaks containers; a separate dig in FOUNDATION would have
  been a second clearing pass with its own restart rules (26.15, decided while building it).
- The depth is declared beside the build (`StarterBlueprints.Entry`), not read from the file: a vanilla structure file
  can't hold a block below its own y = 0 or a note of its own, and the library is the one place every use of a
  blueprint passes through (the Table, the item, plans, previews, styles).
- The eight Level 4s are the homes and workshops that topped out at III: Starter Cottage, Stone House, Townhouse,
  Manor, Supply Shop, Library, Healing Center and Lookout Tower. The Storehouse has its vault from Level 1 instead.
- No Butcher's Shop existed, so it is a new one-tier build; the Winery already has its half-sunk cellar, so the Cook's
  cold larder under the Kitchen is the fourth Level 1 cellar.
- Four blocks deep: three of room under the ground floor's own floor, enough for a ladder or a four-step stair.

## 7. Known limits

- Basements are only under the ground floor's own walls; none reaches under a wing or a yard.
- On a slope the basement is dug from the ground floor down like the rest; where the ground falls away its walls show.
- The outline drawn in the world while placing shows only the part above the ground. The Blueprint Table's 3D preview
  draws the basement under the house, centered on the house alone; that view isn't tested.
- A Smithy, Kitchen or Storehouse finished before the update keeps no cellar until a builder is asked to dig it ("Dig
  the cellar", [Cellars](cellars.md), 36.7).

## 8. Proof

GameTests: `BasementGameTests` (`basementsLoadUnderTheGround`: every build with a basement loads with it under the
ground, the right size, a way down and a light, and each Level III's builder sells its Level 4;
`levelFourDigsAndFinishesItsBasement`: Starter Cottage IV over a finished Cottage III on real ground;
`butchersShopGoesUpWithItsFreezer`; `reloadMidDigCarriesOn`: saved and loaded mid-dig and mid-structure, the foundation
never fills the dug room; `noMaterialsStillDigsAndKeepsContainers`; `cellarIsBuiltWithTheGroundLeftAlone`), and
`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`, `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`. QA's `QaBasementGameTests` (3, every build with a basement): its way down comes up through the ground floor; no spot on its floor is left dark (block light 0); it can be walked to from outside and back out, every chest down there within reach.

Showcase scenes: basement
