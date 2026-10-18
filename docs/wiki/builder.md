# Builder

The flagship job: a villager who builds blueprints, block by block, from the chests by their bench.

Roadmap items: 23.1, 23.1a, 23.1b, 23.3, 23.4, 23.8, 26.9, B120, 36.1, B173, B185, B132

## 1. What a player sees

You put a Builder's Bench down, and a villager without a job takes it and becomes a Builder. You give that villager a
Blueprint (right-click it), after placing the blueprint on the ground where the building should stand. The Builder
walks to the spot and builds: first clearing the ground, then the foundation, then walls and roof, then the small
things (doors, torches, flowers), then the landscaping. The materials come from chests and barrels near the bench.
Above the Builder's head you can see the progress, what it is short of, and where it takes things from. If the
chests run short, the Builder waits and tells you what is missing. Idle Builders walk over and help other Builders'
builds nearby, and later fix holes in buildings they finished (a creeper's hole, a broken window). Builders get
better with each building: they level up like villagers you trade with, and each level makes them faster.
At the Blueprint Table's library, the selected build is shown in 3D beside its materials: it turns slowly, can be
dragged round, and is scaled to fit, for imported and uploaded builds too. A row of style buttons under it (26.11)
recolours the picture and the materials, and Get Blueprint hands the build out in the chosen style.

## 2. How it works

**One build is one "site".** A site (`BuildSite`) is the whole record of a building in progress. Everything the
Builder knows about it lives in that record, and the record is saved, so a Builder can stop at any tick (server
stopped, chunk unloaded, Builder asleep) and carry on exactly where it was. `BuilderWork` is written to be
restartable at any tick for this reason.

**Stages, in order:** CLEAR (top-down), FOUNDATION, STRUCTURE (bottom-up), DECORATION, LANDSCAPE, DONE.
- CLEAR takes away what is in the way, but never breaks containers or the bench.
- FOUNDATION fills under a build standing on uneven ground. How deep is the `workplaceFoundationDepth` rule.
- STRUCTURE goes bottom-up so every block has something to stand on.
- DECORATION is what needs something to hang on or stand on (torches, doors, ladders, carpets...), placed after
  the structure is up.
- LANDSCAPE (leveling the ground round the build, dirt) never waits for materials.
- FOUNDATION and LANDSCAPE depend on the terrain, so a site loaded in the middle of those stages makes its list
  again from the terrain; steps already done are skipped.
- A step that cannot be done yet is put off once, then skipped (counted in `skipped`).
- "Done" is checked with `MaterialRules.matches`, which ignores block properties that depend on neighbors
  (a fence's connections, a stair's corner shape). Without that a finished wall could look unfinished forever.
- Nobody is built into a block: before a block goes down the builder checks there is room for it as it will
  really stand, shaped by its neighbours (a corner stair whose neighbour isn't up yet goes down straight, a quarter
  bigger), and a villager or animal that a new block's neighbour left inside a wall is moved to the nearest place to
  stand (B132, `B132GameTests`).

**Materials.** Each block costs an item (`MaterialRules`; some blocks you cannot hold in survival use their base
material). A fixed set of blocks is never placed (bedrock, command blocks, spawners, portals, barrier, fire...).
A Builder takes what it needs from the chests and barrels within `supplyRadius` of the bench (and 4 blocks up or
down), carries up to 27 stacks in its bag (`BuilderBag`), and empties the bag at the chests when it finishes or
stops helping. Builders never copy what is inside containers. The blueprint's tooltip and a Book and Quill list what
is still missing.

**Speed.** `workplaceBuildDelay` ticks per block (default 8) times a share by Builder level 1 to 5:
100%, 100%, 85%, 70%, 55%, 40% (index 0 is unused). Then the village's mood, traits, edicts and so on change it
(`work/Pace`). XP: 1 per 5 blocks placed and 10 for finishing a build (`BuilderLevels`).

**Queue and crew.** A Builder takes up to 5 more blueprints on top of its current one (`MAX_QUEUE`). Up to 3 idle
Builders (`MAX_HELPERS`; Guilds can change this) help one site, each claiming steps ahead of the lead. The owner
asked that time shrink with each Builder: tested at 2 builders = 50%, 4 = 29% of the time alone (ROADMAP 23.1a).

**Who may order.** With `workplaceBuilderOwnership` on, a Builder takes orders only from the player who hired it,
their friends (`/workplace friend add|remove|list`) and operators.

**Upkeep and paths.** An idle Builder looks over buildings it (or its employer) finished within
`maxSiteDistance`, every 1200 ticks, and fills only the holes with its usual materials; things players put there
are left alone. After a build, it can lay a dirt path to the village bell or hall (at most 96 blocks long).

**Safe at height.** A Builder with a build (lead or helper) takes no fall damage: he stands on the build's own
upper rows (a palisade's stake tops, a tower's platform before its parapet) and can step, jump or be shoved off them.
Any other harm still hurts him, and a Builder with no build falls like any villager (B120).

**Table preview (26.9).** Selecting a library entry sends `table_request_details`; the server answers with the
materials and a `table_preview` packet (`TableServer.preview`): a palette of block-state ids and x, y, z, palette
index per cell. Only blocks a player could see are sent (air, invisible blocks, jigsaw and structure blocks are
dropped, and a cell whose six neighbours all occlude is left out). If more than `PREVIEW_CAP` (4096) cells remain, the
build is grouped into 2×2×2 cells (then 3, …), each showing its highest block, until it fits. The result is worked out
once per blueprint and kept (48 at most); unknown, sold-only, world-generation and closed-expansion ids get nothing.
The client (`TablePreview`) draws the cells with the block renderer into the screen with a scissor box.
**Basements in the preview (36.1).** A build with a basement comes out of the library with its cellar under y = 0
(`StarterBlueprints.withBasement`); the packet carries `below`, how many blocks lie under the ground (0 for most), and
cells are keyed with floor division, so at scale 2 or more the first cellar layer (y = -1) never folds into the ground
floor's cell. The client fits and centres the build by its whole height (`sizeY + below`), and for a build with a
basement draws a faint green ground plane at y = 0 after the blocks: the footprint and a little past it, with a
brighter rim, so the part under it reads as underground. The detail pane's size line then reads "17 × 12 × 11 · N
blocks, and 4 below ground" (`screen.aliveworkplace.table.size_below`, wrapped). A build without a basement sends the
same packet as before (plus `below` = 0) and draws as before.

**Table styles (26.11).** `table_details` also carries the styles the build takes (`TableServer.styles`: each of
`BlueprintStyles.all()` whose swaps change at least one of its blocks, via `BlueprintStyles.changes`; none for a Wonder,
or for a locked build). A style button asks `table_request_details` for the styled id
(`aliveworkplace:styled/<style>/<ns>/<path>`, the id the sneak-click style screen sets), and the server answers with
that style's materials and preview, each cached per styled id. `table_take` with a styled id gives `BlueprintItem.create`
of that id, the same `BLUEPRINT` component the style screen sets. A styled id whose style the build doesn't take, or a
locked build in a style, is refused (`TableServer.styleTaken`; tests in `TableGameTests`).

**Stall watch.** If a site with a Builder on shift makes no progress for 600 ticks (30 s), a log line starting
"Builder stalled" is written. The soak test counts these lines.

## 3. Switches

Config file keys (`WorkplaceConfig`):

| Key | Default | What it does |
|---|---|---|
| `supplyRadius` | 8 | Chests and barrels this close to a workstation are that villager's supply chests |
| `maxSiteDistance` | 48 | How far from its bench a Builder takes a build (also how far upkeep looks) |
| `builderPaths` | true | Builders lay a dirt path from each finished building to the bell or hall |
| `builderRepairs` | true | Idle Builders repair buildings they finished when blocks go missing |

Game rules (per world, `/gamerule`):

| Rule | Default | What it does |
|---|---|---|
| `workplaceFreeMaterials` | false | Builders place blocks without needing materials |
| `workplaceBuildDelay` | 8 (min 1) | Ticks per block for a level 1 Builder |
| `workplaceFoundationDepth` | 12 (min 0) | How deep to fill under a build on uneven ground; 0: no foundations |
| `workplaceBuildersHelp` | true | Idle Builders help builds near their bench |
| `workplaceBuilderOwnership` | true | Only the owner, friends and operators give orders |
| `workplaceKeepWorkLoaded` | true | Builds keep running while the ordering player is online but far away |
| `workplaceLevelGround` | 2 (0 to 8) | Blocks around a finished build that get leveled; 0: leave the ground |
| `workplaceAllowUploads` | true | Anyone may upload blueprint files at a Blueprint Table (operators always can) |

## 4. Saved data

Per dimension, in the world's saved data `aliveworkplace_build_sites` (`BuildSiteManager`; at most 2000 finished
sites are remembered). Each site (`BuildSite.save`) holds:

- `id`, `owner`, `owner_name`, `structure`, `placement`: which blueprint, for whom, where and turned how.
- `stage` (default CLEAR), `cursor` (0), `retrying`, `deferred` (list), `skipped` (0), `placed` (0).
- `shown_progress`: the most progress shown so far, so the bar never goes backwards after a restart. Saves from
  0.138.0 and earlier lack it; it is worked out from the cursor (B64).
- `builder` (the Builder's id; none by default), `queued` (false), `deconstruct` (false), `repair` (false).
- `no_level_ground` (absent means leveling is on), `bench` (the lead's bench; absent in pre-0.6 saves).
- `steward`, `hall` (a Steward's build from a Village Hall; absent for a player's own), `waiting_since` (absent:
  not waiting) and `player_blocks` (0): used by Stewards (27.19).

On the villager (attachments in `registry/ModAttachments`):
- `builder_job` (`BuilderJob`): `site` and `helper` (false by default).
- `builder_bag`: the 27-slot bag, empty by default.
- `builder_employer`: who the Builder works for (fields not listed here).

Friends are saved in `aliveworkplace_friends`. Not saved, worked out again: the plan, status, missing list, helpers'
claims and trip counts.

## 5. Items, blocks, jobs, commands

- Block: Builder's Bench (`aliveworkplace:builders_bench`), the job block. Profession: Builder (`entity.minecraft.villager.builder`).
- Items: Blueprint (`aliveworkplace:blueprint`; "Blueprint: %s" when named), Blank Blueprint (`aliveworkplace:blank_blueprint`, bought from
  villagers for emeralds), Builder's Tea (`aliveworkplace:builders_tea`). The Blueprint Table makes and uploads blueprints.
- Hand over: right-click the Builder with a Blueprint. Sneak-right-click with an empty hand: status.
- Commands: `/workplace blueprints`, `/workplace blueprint`, `/workplace import`, `/workplace sites`,
  `/workplace cancel <site>` (the blueprint stays placed; hand it back to carry on), `/workplace friend add|remove|list`,
  `/workplace strip`. Test and QA only: `/workplace soak`, `/workplace benchmark`.

## 6. Decisions

- Builder helpers must scale: about half the time with two builders, and so on (owner, 2026-10-04, ROADMAP 23.1a).
- Builder speed is fine: about 3.5 in-game days for the 22 starter builds (owner, 2026-10-04, 23.1a).
- Helpers only play a toss sound, no flying item, and a cap limits how many sounds play at once (owner, 2026-10-05,
  23.1b).
- "What do you need?" must be readable at a glance, from the site or the Builder (23.3, approved 2026-10-04).
- Placing must feel good: turn, mirror, ghost preview, move, cancel with materials returned (23.8, approved
  2026-10-04).
- A builder's steps need exactly the build's material list, nothing more (B22), and it fetches from its own site's
  bench chests and keeps its bench, never swapping to another builder's (B22, B28).
- Progress never drops after a restart (B46, B64).
- Unfinished blocks are never claimed done: `MaterialRules.matches` ignores neighbor-dependent properties.
- An upgrade is a whole new blueprint on the same spot. Where its new room's roof runs in under the old roof's edge,
  the wall is carried up to that edge, so the roofs meet with no open blocks between them; `tools/blueprints/holes.py`
  checks every blueprint for roof holes, gaps between roofs and stray empty blocks (owner's report, 2026-10-14).
- Every doorway a build leaves lets a villager through: a villager is 1.95 tall, so under a lintel two blocks up nothing
  in the doorway or just inside or outside it may raise the floor (a carpet, a slab, a stair or seat, a closed
  trapdoor). Carpets start a block in from the door, seats stand aside, and where a stair must start right inside (the
  Manor's) the door has a fanlight (an open trapdoor flat on the outside face) in place of a lintel: a stair's back half
  is a full block up, and a villager climbing onto it still has its back in the doorway. `tools/blueprints/check.py` refuses every blueprint
  that breaks it (B170, B173).
- No villager is left standing inside a block in a growing village. A baby fits in nooks a grown villager doesn't
  (under a roof's stair being laid, beside a lantern on a fence post), and when it grows up vanilla finds no room for
  the bigger body there; a villager standing still in a doorway can have the door shut on it by a neighbour walking
  through. Twice a second every villager looks whether its body is inside a block; found there twice in a row, it opens
  the wooden door it is shut in or else steps (with a puff) to the nearest spot within four blocks with a floor and
  room to stand. A villager only pressed against walls is never moved (B185).

## 7. Known limits

- Only builds that fit within `maxSiteDistance` (48) of the bench are taken.
- Blocks on the never-placed list (spawners, portals, bedrock, command blocks...) are skipped, not substituted.
- Steps that cannot be done after being put off once are skipped, so a build can finish with a few blocks missing
  (the `skipped` count).
- Upkeep looks only every 1200 ticks and only at buildings within range of the bench.
- A site in unloaded chunks, queued, or without a Builder is not watched for stalls.
- The table preview leaves out fluids (water and lava have no block model) and draws blocks at full brightness.
- Not tested here: the pack-server soak result of the last run (see the soak scene's pass or fail on the showcase).

## 8. Proof

GameTests (`src/gametest/java/.../test/`): `BuilderGameTests` (108 tests), `BuilderCrewGameTests` (3),
`BuilderChaosGameTests` (1, stuck recovery), `B185GameTests` (6, villagers let out of a block), `BuildSiteSaveGameTests` (2), `BuilderBenchGameTests` (2),
`BuildSiteStepsGameTests` (2), `BuildReserveGameTests` (5), `StallWatchGameTests` (7), `BuilderKeepsBenchGameTests`,
`TerrainStallGameTests`, `BuildersShareSpareGameTests`, `PokemonBuildsGameTests`, `TablePreviewGameTests` (7, the table's
3D preview data; 36.1: Starter Cottage IV's preview has cells below the ground and its fitted height counts them, and
at scale 2 no cellar block shares a cell with a ground-floor block), `DoorwayHeadroomGameTests` (2, B173: a villager
walks in by the Healing Center's front door onto its carpet, and by the Manor's front door onto the stair right inside:
`DoorwayHeadroomGameTests.aVillagerWalksInByTheHealingCentersFrontDoor`,
`DoorwayHeadroomGameTests.aVillagerWalksInByTheManorsFrontDoorOntoItsStair`).

Showcase scenes (`tools/showcase/scenes.py`): `builders` (three builders, three starter builds), `soak` (10 builders,
every starter build), `preview` (ghost preview and status), `placing` (turn, mirror, cancel, slope), `missing` (the
missing-materials tooltip and list), `table`, `table_preview` (the library's 3D preview), `table_basement` (Starter Cottage IV and the Storehouse with their cellars), `shapes`, `scan`, `style_menu`, `crew` (four builders on one build),
`pace` (speed cap), `partners_engine`.
