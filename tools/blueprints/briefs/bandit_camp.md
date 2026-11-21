# Bandit Camp: the brief

Family id(s): `camp/bandit_camp` (one template, never renamed)   Variants: village (none), modded (none), looks (none).
One drawing, set down turned any of the four ways at random.

## What it is for
The **lair of the bandits** (`raider_cultures/bandits.json`, `threat/Lairs`, `guard/BanditCamps`), and the same
template serves two stories. It is never built by a builder and never offered at the Table
(`BlueprintLibrary.isWorldgenPiece`: everything under `camp/` is set down at once).
- **A lair** (32.3): now and then (12% a day) bandits make camp 80 to 104 blocks from a Village-rank hall. The camp is
  set down in one go with a **Bandit Chief** (a vindicator in iron, named) and a band of up to 8 pillagers and
  vindicators standing in it. Players near the village read "Smoke rises beyond %s: bandits have made camp %s. Until
  their chief falls, they'll raid the village at night." While it stands, the village's night raids come from it.
  Kill the chief and it is **broken up**: the band vanishes, the loot chest is the player's, and after 40 ticks the
  camp **comes down by itself**, top first, over 100 ticks, its chests spilling their items, and the land it covered
  comes back as it was (B209, B225, `threat/CampCleanup`, `threat/CampGround`). With `campRuins` on it stays as ruins
  instead, its fire put out.
- **The Bandit King's three camps** (31.16, `arcs/bandit_king.json`): placed by the arc engine 80 to 104 blocks out,
  each with a "Captain of the Bandit King" and 3 or 4 bandits; the story sets **his war banner** beside each camp, and
  the player breaks the three banners to end chapter 1. Pell the deserter waits by the first camp.
- **A bounty captain's hideout** (31.13, `Bounties.HIDEOUT`): placed 120 to 200 blocks out when a player comes near,
  with the captain at the chief's spot, his band round the fire and his own banner on a pole by the fire.

So it is the stage for one scene: an outlaw camp out in the wild, a fire whose smoke the village can see, a chief and
his men, and a chest worth fighting for (the code's own words: "tents round a fire", "the chest in the chief's tent").
How it tells that is free.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| (one) | `camp/bandit_camp` | The camp: its fire (the camp's middle), its loot chest, the spots its chief and band stand on | None: set down by `Lairs.found`, `ArcEffects.build` or `Bounties.check`, never built, upgraded or sold. `banditCamps` and `bountyBoard` in the config turn them off |

## Must have
Coordinates are template cells (x, y, z) in the file as drawn, before any turn.

- **The fire: a lit campfire at (7, 1, 6)**, and no other campfire within 9 blocks of it.
  - It is the camp's middle for everything: `Lairs.FIRE` (the lair's saved position, where the band is kept within
    14 blocks of), `Bounties.FIRE` (the bounty band stands round it), `CampCleanup` (finds the camp's turn and origin
    from it), and `FIRE_SPOT = (7, 1, 6)` in B209GameTests, QaCampInUse1109dGameTests, QaCampInUse1111dGameTests,
    QaCampSweep1109dGameTests and QaCampCleanupMutants1117dGameTests.
  - **Keep it at (7, 1, 6).** A lair standing in a player's save is recorded by its fire; when it breaks, the cleanup
    works the camp's origin out from `Lairs.FIRE`. A fire moved in the drawing sends every camp already standing on the
    owner's server to the wrong origin when it comes down.
  - **Open sky above it**: no block with collision anywhere above the fire in the drawing (no roof, beam, hanging
    lantern). The cleanup's sweep of old worlds finds a camp by a campfire as the top block of its column
    (`CampCleanup.sweep`, heightmap `MOTION_BLOCKING_NO_LEAVES`); a covered fire is never found
    (B209GameTests.b209ACampBrokenUpBeforeTheFixIsClearedAfterLoad, QaCampSweep1109dGameTests).
  - **Lit**: the sweep with `campRuins` on skips an unlit fire, and the ruins put out a lit one
    (B209GameTests.b209WithCampRuinsOnABrokenCampStaysAsRuins, B225GameTests.b225RuinsKeepTheirTentsButNotAFlatScar read
    `LIT` at the fire: it must be a campfire block there).
  - At y = 1, never in the ground layer (see "The ground layer" below).
- **The loot chest: exactly one container that carries `LootTable: "aliveworkplace:chests/bandit_camp"`** in its block
  entity, a plain `minecraft:chest`, within 8 blocks (x and z) of the fire and at y 1 to 4
  (LairGameTests.theCaptainsNameIsOnHimOnTheDefencePageAndInTheChronicle looks there for it).
  - `Lairs.found` gives the culture's loot to every container in the box that already has a loot table, and only to
    those; the Bandit King's camps and the bounty hideout get no such pass, so the table in the file is their loot.
    A chest without one is empty. More than one loot chest is a change to the reward (the owner's call), not a design one.
  - The code's story puts it in the chief's shelter ("the chest in the chief's tent is yours"); no code checks where.
- **Two chests in all** (`minecraft:chest`, single), the loot chest and one more without a loot table, both within
  9 blocks (x and z) of the fire, at y 1 to 6. QaCampCleanup1031GameTests.aCampsChestLootAllSpillsOutNoneLostNoneDoubled
  finds exactly 2 chest block entities round the fire, fills each with known items and checks every item lies on the
  land once the camp is down; B209GameTests.b209ABrokenCampComesDownAndIsForgotten and
  QaLairBreakUp1110dGameTests.aCampClearedByTheThreatCommandPacksUpItsTents count 2 chests (`Blocks.CHEST`). Other
  containers (a barrel) are free and uncounted; a trapped chest counts as a chest in the first test, not the others.
- **The chief's spot (7, 1, 9)** (`Lairs.CHIEF_SPOT`, `Bounties.CHIEF`): the lair's chief and the bounty captain are
  moved there. Feet cell (7, 1, 9) and head cell (7, 2, 9) free (no block with collision, no fluid), a solid floor at
  (7, 0, 9). An occupied cell lifts him up to 4 blocks (`Lairs.standOn`, `Bounties.stand`), onto whatever is there.
  A Beast Rider captain sits on a ravager, so give the spot room.
- **The band's spots** (`Lairs.BAND_SPOTS`, in order): (4, 1, 7), (10, 1, 7), (7, 1, 3), (6, 1, 4), (8, 1, 4),
  (3, 1, 6), (11, 1, 6), (8, 1, 3). Each with feet and head cells free and a solid floor under it, for the same reason.
- **The chief's spot and the band's spots may move**, if the plan names the new cells: the ship step then changes
  `Lairs.CHIEF_SPOT` and `BAND_SPOTS` and `Bounties.CHIEF` with them. They are read only when a camp is set down, so
  no save depends on them. The fire may not move (above).
- **Ground round the fire the code stands men and banners on**, measured in the world after the camp is turned, so
  keep it true on every side:
  - The bounty band is stood about 3 blocks out from the fire in several directions (`Bounties.raise`: offsets
    (3, 0), (-2, 2), (0, -3), (2, 2), (-3, -1), (3, -2) for a band of six); a cell with a block in it lifts the man onto
    it. Open ground (feet and head free, a floor under) 2 to 3 blocks round the fire on every side.
  - The bounty captain's banner pole goes on the first open ground within 3 blocks of (9, 1, 6) (`Bounties.POLE`; a
    free cell, free above, on a block with a solid top, from y 0 to 3). With none there, the hideout has no banner
    (QaBountyCamp1108dGameTests). (9, 1, 6) may move with the ship step changing `Bounties.POLE`.
  - The Bandit King's war banner goes on the first open column 2 to 6 blocks straight out from the fire (east, west,
    north, south, or the diagonals), whose top is free sky with a block with a solid top under it, no more than 2
    blocks above or below the fire (`BanditKingStory.raiseBanner`). A camp with no such column gets no banner, and
    chapter 1 of the story can't end. No test sets the camp down for this; it is the code's rule.
  - The Bandit King's captains, bandits and Pell are spawned on the **top of the heightmap** in random columns within
    3 blocks of the camp's middle (`ArcEffects` spawn). Where those columns are roofed, they stand on the roof.
- **Every block from y = 1 up stays as it was placed** until the camp comes down. The cleanup takes away only blocks
  still exactly as the blueprint set them (`MaterialRules.matches`) and leaves the rest standing for good. So nothing
  that falls (sand, gravel, concrete powder, anvils, scaffolding), decays (leaves that aren't persistent), melts (snow,
  ice), grows (saplings, crops, berries), flows (water, lava: the camp's ground must be dry anyway) or drops off for
  want of support. B225GameTests.b225ACampOnASlopeLeavesTheLandAsItWas checks the land is as it was, block for block.
- **No entities in the file** (no mobs, armor stands, item frames, paintings): the code spawns the chief and band
  itself, and the cleanup removes blocks only, so an entity would be left behind on the bare land after the camp goes.
- **Tests that count the camp's kinds of block.** These were set from the old drawing; the ship step resets each
  number to the new drawing's (keeping what it checks: all of them gone after, all of them kept as ruins):
  - B209GameTests (`count`, within 9 blocks x and z of the fire, y 1 to 6) and QaLairBreakUp1110dGameTests: campfires
    == 1, chests == 2, wool > 20, beds == 10 (five beds) as set down, all 0 once down. B209GameTests also swaps one wool
    block of the camp for gold to show a player's block stays.
  - B225GameTests.b225RuinsKeepTheirTentsButNotAFlatScar: more than 20 wool blocks stand on as ruins.
  - Wool and beds are free. A camp with no wool or no beds means the ship step must point those helpers at a kind of
    block the new camp has (same meaning: "the camp's shelters"), never drop the checks. Having at least 21 wool blocks
    and some beds within the count window keeps the tests as they are.
- **No furniture, job block, bell or sign is read.** `blueprint_furniture/camp/bandit_camp.json` is written by the kit
  from the drawing (its beds); nothing reads it for a camp (`FurnishedRoomsGameTests` skips `camp/`).

## Fits
- **How it is set down** (`Lairs.found` → `clearAndPlace`; the arc and the bounty go through `ArcEffects.build`, which
  does the same): the template is **turned a random way** and placed with **its middle (`size / 2` on x and z) on the
  chosen ground column**. **Template y = 0 is the ground layer itself**: it sits in place of the world's top block.
  Everything in the box from y = 1 up is removed first (grass, bushes, the side of a rise, tree leaves); under the box,
  every replaceable cell from y = 0 down to 3 below is filled with dirt, so a dip becomes level ground.
- **The site**: a lair's ground is loaded, dry (no water at or above the top block), within 32 blocks of the hall's
  height, and fairly flat over a **15 x 13 window round the middle** (`Lairs.fits(…, 7, 6)`: at most 20 of its 195
  columns more than 1 block higher or lower, or with something solid 1 or 2 blocks above the ground). The arc and the
  bounty want the whole box natural (only ground at y = 0, only air or plants above, `ArcEffects.natural`), searching
  columns out to 32 blocks for it. Any biome on land: grass, desert sand, snow; the tests set it on a grassy slope
  rising a block every four (B225GameTests, QaCampCleanup1031GameTests) and on two layers of sand
  (QaCampCleanup1031GameTests.aCampOnDesertSandLeavesTheSandAsItWas).
- **Plot: the file is 15 (x) by 13 (z), at most 12 tall (y 0 to 11)**, the fire at (7, 1, 6) its middle; the camp needn't
  fill it.
  - 15 x 13 is the ground the site test checked; a camp wider than that is set down over ground nobody checked and
    clears whatever stands there. (Turned a quarter, the camp is 13 x 15 over that window; it always was.)
  - The tests set the camp's middle at (12, 1, 12) of the 22 x 22 `big_area` with the hall at (1, 2, 1)
    (LairGameTests, B208GameTests, QaLairEngineGameTests, QaPeacefulLairGameTests...), at (13, 1, 13) of `big_area`
    (PeopleGameTests, QaHeartStories1031GameTests), and at (15..20, 1, 15..20) of the 30 x 30 `huge_area`; the old-world
    tests put it down unturned with the fire at (13, 2, 17) of `huge_area`. 15 x 13 stays inside every one of them.
  - B225GameTests and QaCampCleanup1031GameTests compare the land block for block from x and z 4 to 26 and up to
    y 14 of the area, with the camp's ground at y 3: a camp taller than 12 rises past what they look at, and past the
    `big_area` roof (18 tall) where other tests set it.
  - The file's size must stay 15 x 13 so `size / 2` stays the fire: a different size moves the fire off the middle of
    the checked ground and off the arc's spot (`p.spot`, the box's middle).
- **The ground layer (y = 0)**:
  - **No block entity in it** (no chest, barrel, campfire, bed, banner, sign, skull...). The cleanup never removes
    y = 0, and the land's return skips any block with contents (`CampGround.restore`), so it would stay for good.
  - Each cell is either a solid ground block the camp draws, or **left out of the file** (no block saved there, never
    saved as air: air at y = 0 would dig a hole in the world's ground). Left out, the world's ground stays, and a
    hollow there is filled with dirt.
  - **Open camp ground on every side of the fire, out to the edges**: ground-layer cells with 2 free blocks above them,
    on all four sides (the camp lands any way round on a slope). The tests need them:
    QaCampCleanup1031GameTests.aPlayersBlockOnTheCampsPadStaysWhenTheLandGoesBack looks within 7 of the fire for an
    open pad cell where the slope was cut (the high side), one over a filled hollow (the low side) and one of the
    camp's own drawn floor blocks that isn't plain dirt (at a cell where x + z is a multiple of 3, so a few of them,
    not one); B225GameTests.b288GrassCreptOverTheCampsFloorStillGoes needs plain dirt at floor level with air above it
    on the low side (a cell left out over the hollow, or drawn as `minecraft:dirt`).
  - **Ground-type blocks don't stand alone above y = 0.** B225GameTests.b225RuinsKeepTheirTentsButNotAFlatScar treats
    a column whose only blocks from y = 1 up are dirt, grass block, smooth stone, coarse dirt, dirt path, gravel,
    cobblestone or fire as open land and expects it back as it was, while the code keeps every column with a camp block
    standing as ruins. A full block of one of those at y >= 1 is fine only in a column that also holds something else.
- **The camp's turn is found by matching**: `CampCleanup.turn` tries all four turns about the fire and takes the one
  that matches the world best (a quarter of the blocks for a known camp, half for an old one). A camp that looks the
  same turned a quarter about its fire still comes down, but keep the right turn the clear best.
- Front and way in: no front. It is turned at random, so it works from every side; the band's and the banners' ground
  (above) must exist whichever way it lands.
- Neighbours: nothing is placed beside it. It stands alone 80 to 104 blocks (a lair, a King's camp) or 120 to 200
  blocks (a hideout) from the hall, away from other villages.

## Rules tests check
- Set down and broken up (the drawing's fire, chests and counts, above):
  - B209GameTests: `b209ABrokenCampComesDownAndIsForgotten` (counts as set down; all gone after; a player's block stays;
    the camp's middle over its ground column; the ground back; loot spilled; a new camp after),
    `b209WithCampRuinsOnABrokenCampStaysAsRuins` (counts kept, fire out), `b209AReloadMidClearingCarriesOn` (half down
    after a reload, beds 10 until the end), `b209ACampBrokenUpBeforeTheFixIsClearedAfterLoad`,
    `b209AnOldUnturnedCampComesAllTheWayDown`, `b209ABountyFarOffDoesntStopTheSweep`,
    `b209AnOldCampWithExactlyHalfStandingIsFound` (the fire is one of the blueprint's standing blocks at the middle).
  - B225GameTests: `b225ACampOnASlopeLeavesTheLandAsItWas`, `b288GrassCreptOverTheCampsFloorStillGoes`,
    `b225AReloadMidClearingStillPutsTheLandBack`, `b225RuinsKeepTheirTentsButNotAFlatScar` (more than 300 of the
    23 x 23 columns round the camp open and as they were), `b225ACampFromBeforeTheFixIsBlendedIntoTheLand`.
  - QaCampCleanup1031GameTests: `aCampsChestLootAllSpillsOutNoneLostNoneDoubled` (2 chests; every item on the land, no
    higher than 6 above the fire), `aPlayersBlockOnTheCampsPadStaysWhenTheLandGoesBack`,
    `aCampOnDesertSandLeavesTheSandAsItWas` (no hole, nothing falling), `afterABreakUpALaterWarbandMakesANewCampElsewhere`
    (a campfire at the lair's middle).
  - QaLairBreakUp1110dGameTests.`aCampClearedByTheThreatCommandPacksUpItsTents` (the same counts) and
    `aNewCampOnABrokenCampsGroundIsNotKnownFromTheOldReport`.
  - The old-world sweep, the template set down unturned with the fire at the test's spot: QaCampSweep1109dGameTests,
    QaCampInUse1109dGameTests, QaCampInUse1111dGameTests, QaCampCleanupMutants1117dGameTests (a campfire at the middle),
    QaCampBlend1107dGameTests, M38MutantGameTests.`m38AClearingSavedWithAnOddTurnStillComesDown` (a campfire in the area).
  - LairGameTests.`theCaptainsNameIsOnHimOnTheDefencePageAndInTheChronicle`: the loot chest within 8 of the fire, y 1-4
    above its floor, holds `chests/bandit_camp`.
- By id only (the drawing doesn't matter beyond loading and the fire): QaCampsDesert1104d3GameTests (the chief in iron
  and 3 or 4 men, the "Smoke rises" line), B208GameTests, B1072GameTests, QaLairBounty1108dGameTests,
  QaConvertedCaptainSaves1109eGameTests, QaLairFallWarning1110dBugTests, QaLairFallOtherBands1111dGameTests,
  QaPeacefulLairGameTests, QaPeacefulLair1103dGameTests, QaLairBlipSentryIron1101dGameTests, PeopleGameTests, QaHeartStories1031GameTests,
  HallSpecGameTests, StewardCivicRulesGameTests, BanditKingStoryGameTests, QaBanditKing1028GameTests,
  QaKingLairs1104d3GameTests (the arc's three `camp/bandit_camp` places), QaBountyCamp1108dGameTests (the pole and band
  rules above, on flat ground), ThreatGameTests (the culture's loot id).
- Every shipped template (all of `structure/`, `camp/` included):
  - `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`: a standing lantern, candle or torch on a block
    whose top holds its middle; no dirt path or farmland under a solid block; no half bed.
  - `B648GameTests.b648NoShippedGlassEndsInTheAir`, `QaBuilders1028GameTests.qaB648NoShippedGlassEndsInTheAirAsTheGameJoinsIt`,
    `QaWindowsAndWards1028BugTests.qaNoShippedGlassEndsInTheAirAsTheGameJoinsIt`: a glass pane or iron bar joined on only
    one side doesn't end in air.
  - `StationsSpecGameTests.noStructureOfOursUsesAGoneBlock`: the template loads and uses no removed block.
- The owner's rules (`check.py`; `camp/bandit_camp` is not in `owner_rules_baseline.json`, so it must be clean: posts at
  fence, wall and pane corners; a lantern on a table, slab, post or hanging, never on the floor; plates only by a stove
  or on a table with a chair), STYLE.md's checks, `holes.py`, and `rebuild.py --candidate` printing no CHECK line.
- Showcase scenes that film it (`JobScenes`): `camp_cleared` (the fire at the lair's middle gone, the grassy rise back
  with grass on every column), `scouts`, `defence_page`.

## Ship step (not design rules; for the lane that ships it)
- `generate.py` stops drawing `bandit_camp` from `bandits.py`; `holes.py`'s ALLOW entry `("camp/bandit_camp", (5, 1, 11))`
  goes or moves to the new drawing's own.
- `rebuild.py` saves block names and states only and fills every empty cell with air: the shipped file must get the
  loot chest's `LootTable` back and its unused ground-layer cells taken out (not air), as `legend_sites._keep_world_ground`
  and `js/traveller_camp/frames.py` do for the Legend camps.
- The count helpers above get the new drawing's numbers; moved spots go into `Lairs` and `Bounties`.
- **Camps already standing in saves.** `CampCleanup` takes a broken camp down by matching it against the *current*
  `camp/bandit_camp`. A camp set down from the old drawing on the owner's server, broken after the new one ships,
  matches almost nothing of it: its tents and chests would stay for good, and so would the old-world sweep's camps.
  Before shipping, the cleanup needs to know the drawing a camp was set down with (fall back to the old drawing in
  `blueprint_history/camp/bandit_camp/` when the current one doesn't match), with a test that an old-drawing camp still
  comes down. Keeping the fire at (7, 1, 6) is what lets that fallback find the old camp's origin.

## Free
Everything not listed above: what the camp is made of and holds, its shelters, their number, shape and height, its
plan within 15 x 13 x 12, which ground-layer cells it draws and in what, where the chests stand (within the limits
above), lighting, props, the chief's banner, planting and edges.
