# Guildhall: the brief

Family id(s): `guildhall`, `guildhall_2`, `guildhall_3` (never renamed; "Guildhall", "Guildhall II", "Guildhall III" in
`en_us.json`, `blueprint.aliveworkplace.guildhall*`). Variants: village (none: no village piece), modded (none drawn;
not a home or workshop for `modded_library.py`), looks (none). The Table's style buttons (`BlueprintStyles`) restyle the
drawing automatically.

## What it is for
Where a village's chartered guild meets. A Master given a Guild Charter leads his trade's guild, but its perks (a trade
working 15% faster, five helpers at one build, and so on) wait until a builder **finishes a Guildhall in the village**:
any finished build whose id starts with `guildhall` (`Guilds.isGuildhall`) founds the oldest chartered guild that has
none, and each guild needs its own (`Guilds.round`). When it is founded, the charter goes up in **every empty item frame
inside the build's box** (`Guilds.frameCharter`; builders always hang frames empty). If the hall stops being a finished
building (a creeper) the guild's perks wait until it is repaired. Nobody works or sleeps there: it has no worker, no job
block and no bed by contract. The player gets the blueprint from a Guild Master (I for 12 emeralds, II for 24,
`Guilds.sellGuildhalls`), as a rare Table unlock (`blueprint_unlocks/rare.json`) once its expansion (Milestone 30) is
open (`ExpansionBuildsGameTests`), and from the hall's advice ("<guild> has no Guildhall", `VillageAdvice`). The village
map draws it as a hall (`VillageMaps`, by id). Questbook (`chapters.py`, `built("guildhall")`) and the advancements
`built/guildhall*` go by id.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `guildhall` | Founds a guild; holds the charter's frame(s) | Guild Master sells it, 12 emeralds; rare unlock; questbook |
| II | `guildhall_2` | Founds a guild just the same (a bigger hall); the upgrade of I | Guild Master sells it, 24 emeralds; offered as I's upgrade (`UpgradeOffers`) |
| III | `guildhall_3` | II unchanged above ground plus a cellar (a strongroom) 4 blocks down | Offered by a builder who finished II (`CellarGameTests`); one of the eight `LEVEL_3_CELLARS` |

Ids, the levels' count and the I to II to III chain are fixed: `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`
counts exactly 64 starter upgrades, `CellarGameTests` exactly 8 cellared Level IIIs.

## Must have (each level)
- **Job blocks: none.** No vanilla or mod workstation of any job (no lectern, cartography table, loom, barrel,
  composter...; a bell takes no villager into a job, see "Free"). A Guildhall has no worker, so any job block would draw a jobless villager into
  that job (vanilla's `acquirable_job_site`); no test pins this, the game does. III's cellar has none either.
- **Beds and residents: none required.** No code counts residents here. (Any bed would count as a village bed for the
  Steward, `StewardConditions.beds`; free but pointless.)
- **At least one item frame** (a builder hangs it; drawn as an entity in the template, empty or not, the builder puts it
  up empty): `QaTurnedFramesGameTests.aBuilderHangsTheGuildhallsFrameWhenItIsTurnedAndMirrored` fails with "the Guildhall
  has no item frame to check" without one, then builds I turned a quarter and mirrored and checks every frame hangs.
  Every empty frame in the box receives the charter when the guild is founded, so every frame is a charter frame. Each
  frame hangs on a solid block behind it, turned and mirrored too (`QaTurnedFramesGameTests`,
  `QaLedgerFramesHaulGameTests`).
- **Storage: none required.** No code reads the hall's chests. Every chest drawn must be reachable on foot and set
  against a wall, solid block, bed or chest (`QaBuildReachGameTests`, `QaFurnishedRooms1031GameTests`), and drawn empty.
- **Furniture kinds: none required** (`blueprint_furniture/` has no guildhall file today; `rebuild.py` records every
  spot the new drawing has, and each must match its template, `FurnitureSpotGameTests`). Nothing small on air
  (`FloatingFurnishingsGameTests`); every chair stair faces its table (`QaChairFacingGameTests`).
- **III's cellar:** `StarterBlueprints.GUILDHALL_3` has `basement` **4** (the cellar's floor at y = -4); everything at
  y >= 0 is **exactly Level II's blocks**, except **one trapdoor at y = 0 over a cellar column** (the hatch), which must
  exist (`CellarGameTests`: "changes N block(s) of its Level II", "has no hatch down into its cellar"). The way down is a
  ladder under that hatch from inside the ground floor, standing where the floor above is clear two blocks up. Every
  chest down there is reachable on foot (`QaBuildReachGameTests`).
- A way in on the z = 0 side that a 1.95-tall villager walks through (`QaDoorwayClearanceGameTests`); any upper floor
  reached by ladder or stairs from inside (B149).

## Fits
- **Plot, I: at most 24 wide x 28 tall x 22 deep.** `BuilderGameTests.buildsGuildhall` builds it in `huge_area` at
  helper (5, 2, 8) (so x <= 25, z <= 22 deep); `QaTurnedFramesGameTests` places it turned and mirrored from corner
  (6, 2, 6) of `huge_area` (so both footprint sides <= 24).
- **Plot, II and III: at most 28 wide x 28 tall x 22 deep** above ground (`buildsGuildhallII`, `tall_area` 30 x 30 x
  30 at helper (2, 2, 8)). **II is at least I's size on every axis and keeps at least 60% of I's solid blocks at the same
  template positions** (`starterUpgradesKeepMostOfTheirBase`); **III is exactly II's size**, plus 4 below
  (`CellarGameTests`). y = 0 sits on the clicked block; the front is the z = 0 side.
- **Material budget: at most 108 stacks per level** (`buildStarter` stocks 4 barrels of 27, each item in whole stacks:
  "test needs more barrels"). Today: I 1,372 blocks, II 2,011.
- **Build time:** I within **50,000 ticks** (`buildsGuildhall`) and within **24,000 ticks** turned and mirrored (free
  materials, build delay 1); II within **60,000 ticks** (`buildsGuildhallII`). The whole plan must finish with no block
  left wrong (`assertBuilt`).
- **Declared sizes:** `StarterBlueprints.GUILDHALL` (19, 15, 14), `GUILDHALL_2` (26, 20, 14), `GUILDHALL_3` (26, 20, 14,
  basement 4) today; the ship step changes them to the new drawing (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
- **Neighbours:** the Steward never builds it (no steward rule); it belongs to the village whose hall is nearest its
  middle or, failing that, its corner (`BuildSiteManager.finishedInVillage`). Free.

## Rules tests check
- `BuilderGameTests.buildsGuildhall`, `buildsGuildhallII`: built from bare ground from the supply report; every block
  right at the end.
- `QaTurnedFramesGameTests.aBuilderHangsTheGuildhallsFrameWhenItIsTurnedAndMirrored`: >= 1 frame, all hung when I is
  finished turned 90 degrees and mirrored front to back.
- `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (II over I), `CellarGameTests` (III over II: size, basement 4,
  no block changed but the hatch, the hatch present, offered after II).
- By id only: `GuildGameTests` (`isGuildhall`, two blueprints for sale, perks wait and come on, II repairs it),
  `GuildHarvestGameTests`, `GuildServiceGameTests`, `GuildPerkGameTests`, `QaTonicsGuildsGameTests`,
  `GuildTrainersCompatTests` (they record a finished Guildhall at an origin; nothing is built),
  `CivicTalkGameTests.aCharteredGuildWithoutItsGuildhallIsAdvised`, `ExpansionBuildsGameTests`,
  `TablePreviewGameTests`, `QaBlueprintLibraryVerify1108bGameTests` (rare build named in the wiki).
- Library-wide: `QaBuildReachGameTests`, `QaDoorwayClearanceGameTests`, `BlueprintsStayGameTests` (lamps, paths, beds),
  `QaFurnishedRooms1031GameTests`, `QaChairFacingGameTests`, `FloatingFurnishingsGameTests`, `FurnitureSpotGameTests`,
  `B648GameTests` / `QaWindowsAndWards1028BugTests` / `QaBuilders1028GameTests` (no pane or bar joined to air),
  `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `StyleGameTests.chimneysStayWholeInEveryStyle`.
- The owner's rules (`check.py`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no CHECK line.
  Saves stay safe: the old drawings go to `blueprint_history/` in the ship step.

## Free
Everything else: shape, plan, roof, materials, colors, storeys, rooms, how many frames, banners, chests or a bell (a
vanilla bell is a gathering point, read by no test), what II adds and what the cellar holds.
