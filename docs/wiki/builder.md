# Builder

The flagship job: a villager who builds blueprints, block by block, from the chests by their bench.

Roadmap items: 23.1, 23.1a, 23.1b, 23.2, 23.3, 23.4, 23.8, 26.9, B120, 36.1, B173, B178, B185, B132, B197, B203, 36.15, B261, B279, B280, B290, B297, B298, B299, B340, B342, B379, B402, B403, B404, B393, B410, B394, B429, B434, B449, B486, B438, B515, B519, B487, B537, B538, B539, 36.20, 36.21

## 1. What a player sees

You put a Builder's Bench down, and a villager without a job takes it and becomes a Builder. You give that villager a
Blueprint (right-click it), after placing the blueprint on the ground where the building should stand. The Builder
walks to the spot and builds: first clearing the ground, then the foundation, then walls and roof, then the small
things (doors, torches, flowers), then the landscaping. The materials come from chests and barrels near the bench.
Above the Builder's head you can see the progress, what it is short of, and where it takes things from. If the
chests run short, the Builder waits and tells you what is missing. Idle Builders walk over and help other Builders'
builds nearby, and later offer to fix holes in buildings they finished (a creeper's hole, a broken window): the
Builder asks first, "Repair it? [Yes] [Not now]" in chat and on the Village Hall's Repairs page, and repairs only what
you said yes to (36.15). What raiders and village fires broke is put back without asking. A finished build whose
blueprint was redrawn since it was built shows **Refresh to the latest look** on the same page (36.21): a click shows
what changes ("New: 3 decorations, 1 furniture piece · Taken away: 2 decorations"), a second hands it to the nearest
free Builder, who takes away what the old drawing left (floating or invisible pieces included; their items go to the
chests) and puts in the new pieces, as a repair. Chests and what's in them, beds villagers sleep in, job blocks and
blocks a player put there or changed stay. With no Builder free it waits until one is. Builders get
better with each building: they level up like villagers you trade with, and each level makes them faster.
At the Blueprint Table's library, the selected build is shown in 3D beside its materials: it turns slowly, can be
dragged round, and is scaled to fit, for imported and uploaded builds too. A row of style buttons under it (26.11)
recolours the picture and the materials, and Get Blueprint hands the build out in the chosen style.
Once a build is under way, a Book and Quill used on its Builder (or the Blueprint Table they work at, or an old Builder's
Bench) becomes the material list of the rest of it (B279; held in either hand, B394): every block from the stage it is in to the end that isn't standing yet, furniture and item
frames included, with what the bench's chests, the crew's bags and the village (a storehouse, other workers' spare
stock) hold ticked off. A sneak-right-click with an empty hand shows the status and then the whole list in chat. With
nothing left to place, no build, or a building being taken down, the Builder says so and the book stays as it was.
The Scan Tool marks whatever block it right-clicks as a corner, a chest, furnace, trapdoor or bench included: the click
marks it and the block stays shut and unflipped (B335), from the off hand too when the main hand is empty (B374).
A Scan Tool renamed in an anvil names the build exactly as typed, apostrophes and accents too: "Jesse's Café" is
called that in the saved line, on the blueprint, at the Blueprint Table and in every message (B458). Its id and file
stay plain (`scans/<player>/jesse_s_caf`); the name is kept by id in the world's `aliveworkplace_blueprint_names` data
(`blueprint/BlueprintNames`) and sent to players as they join. A second build of the same name is "Jesse's Café 2". An
uploaded file such as `Zoë's Hut.litematic` keeps its name the same way; a plain `zoe_hut.nbt` is still "Zoe Hut", as
before. Builds saved before this keep the names made from their ids. Shape Planner builds are named by the mod, never
typed, so they're unchanged.
A scan keeps the box's item frames, glow item frames, paintings and armor stands (B441), saved empty
(`BlueprintEntities.keepBuildable`): no framed item, no armor or held items on a stand, and nothing else that moves, so a
villager, a mob, a dropped item or a minecart with its load is never copied. The builder puts them up last, as it does a
starter build's frames, each paid with its item from the chests; one with no item there is left out. Scans saved before
this have none and load as before.

## 2. How it works

**One build is one "site".** A site (`BuildSite`) is the whole record of a building in progress. Everything the
Builder knows about it lives in that record, and the record is saved, so a Builder can stop at any tick (server
stopped, chunk unloaded, Builder asleep) and carry on exactly where it was. `BuilderWork` is written to be
restartable at any tick for this reason.

**Stages, in order:** CLEAR (top-down), FOUNDATION, STRUCTURE (bottom-up), DECORATION, LANDSCAPE, DONE.
- CLEAR takes away what is in the way, but never breaks containers, its own bench or any other Builder's Bench (a
  neighbor's bench inside the build's box stays, and the build finishes round it, B132).
- FOUNDATION fills under a build standing on uneven ground. How deep is the `workplaceFoundationDepth` rule.
- STRUCTURE goes bottom-up so every block has something to stand on. A block a player puts mid-build where the
  drawing has another is taken out into the builder's chests and the drawn block laid (a Steward's build keeps the
  player's block instead; tests `QaBuilderDisruptionGameTests`, `StewardSafetyGameTests`).
- DECORATION is what needs something to hang on or stand on (torches, doors, ladders, carpets...), placed after
  the structure is up.
- LANDSCAPE (leveling the ground round the build, dirt) never waits for materials.
- LANDSCAPE digs away ground above the floor and fills holes with dirt, but never above the land just beyond the
  leveled ring (B280): a build standing a block above the lawn round it leaves the lawn as it is, where it once got a
  raised band of dirt round it. A blueprint clicked onto a flower or tall grass stands on the ground under the plant.
- FOUNDATION and LANDSCAPE depend on the terrain, so a site loaded in the middle of those stages makes its list
  again from the terrain; steps already done are skipped.
- A step that cannot be done yet is put off once, then skipped (counted in `skipped`).
- "Done" is checked with `MaterialRules.matches`, which ignores block properties that depend on neighbors
  (a fence's connections, a stair's corner shape). Without that a finished wall could look unfinished forever.
- Nobody is built into a block: before a block goes down the builder checks there is room for it as it will
  really stand, shaped by its neighbors (a corner stair whose neighbor isn't up yet goes down straight, a quarter
  bigger), and a villager or animal that a new block's neighbor left inside a wall is moved to the nearest place to
  stand (B132, `B132GameTests`).

**Materials.** Each block costs an item (`MaterialRules`; some blocks you cannot hold in survival use their base
material). A fixed set of blocks is never placed (bedrock, command blocks, spawners, portals, barrier, fire...).
A Builder takes what it needs from the chests and barrels within `supplyRadius` of the bench (and 4 blocks up or
down), carries up to 27 stacks in its bag (`BuilderBag`), never more of a thing than the stretch of work ahead still
needs (B132: one block's worth too many on every trip left two Builders sharing a Storehouse each waiting for what the
other carried; B519: a helper at the chests puts back what its stretch no longer needs, such as the walls' last
planks once the crew is on the lanterns), and empties the bag at the chests when it finishes or
stops helping. A build placed mirrored (the mirror button, or a Steward's street mirroring its houses) keeps its double chests whole: each half swaps left and right with the mirror, whichever way the chest faces (B438). The mod's own facing blocks turn and mirror with the build as vanilla's do: a Storehouse built turned a quarter has its Storehouse block facing the way the building faces, and the same goes for the Village Hall, Mailbox, Shop Counter and Grave blocks (B448). One built facing the old way, in a turned build from before, still counts as built: it is never broken or repaired, so what it holds stays. Item frames, glow item frames, paintings and armor stands are put up turned and mirrored with the build too: each hangs on the wall the blueprint hangs it on and faces the way the turned, mirrored blueprint faces it, so a Guildhall placed mirrored and turned a quarter gets its item frame, a two-wide painting hangs where it was drawn, and an armor stand faces the way it was scanned (B439). Builders never copy what is inside containers, nor what a block holds in its look (B429, B434, B449): a scanned lava or powder snow cauldron is built as an empty cauldron (it asks only a cauldron), a ready composter at level 0, a full beehive or bee nest with no honey, a charged respawn anchor uncharged, a chiseled bookshelf or brewing stand showing no books or bottles, and a grave empty (never the dead villager it held). A fluid a block keeps in its data is never copied either (B486): a scanned Create fluid tank, spout, basin or item drain holding lava, honey, chocolate or a potion, or any mod's tank, is built empty, keeping its size and windows as drawn. Crops are planted, never built ripe: wheat and other crops, stems, nether wart, a sweet berry bush and cocoa go in at age 0, a pitcher crop one block tall as a planted pod (B560), cave vines without glow berries; a lectern or jukebox shows no book or disc, a Mailbox shows no mail and gives no comparator signal (B536), a furnace, smoker or blast furnace is built cold (a campfire stays lit as drawn), and a bee nest or hive holds no bees (B493-B496). A crop that grew, a furnace lit or a Mailbox given mail since still counts as built. A water cauldron is still built full, as the shipped blueprints have it (water is free in vanilla). One filled by a player since still counts as built. The blueprint's tooltip and a Book and Quill list what
is still missing (the blueprint and the book in either hand, B365). A new build leaves out the chests inside its own box (they are what it puts up, its owner's), but a
repair counts them (`BuildPlan.supplyExclude`, B261): the building stands, so a Storehouse Shed being repaired, or a
shed the bench's chests stand in, hands the Builder what its chests hold.

**Whichever table (B290).** Beyond its bench's chests a Builder counts and takes the village's stock
(`BuilderWork.spareElsewhere`, `Village.stashes`): the overhead "Short of" line, the status's "Needs" and the Book and
Quill list all count it, as the fetching always did. A Storehouse is shared by `Village.sharesStorehouse`: the same side
as before (`Village.sameSide`), or, where one side is the village's own, the owner of the Village Hall whose village it
is in stands for it: an unowned Storehouse (a Steward's build) is the hall owner's, and an unhired Builder is on the
Storehouse owner's side when his build is theirs or the hall is. Before, a hired Builder never counted an unowned
Storehouse and an unhired one never counted a player's, so the Storehouse fed a Builder only while his table stood by it
(its chests were then his own), and a Builder's Workshop's table, once taken, split the village's stock. A sneak-click on
a Blueprint Table with an empty hand moves the Builder nearest the player (within 6 blocks, `BenchMoves`) there: the
old table's place is let go, the new one taken (`Stations.assign`), and the active and queued sites' bench follows. The
Village Hall's free job blocks page lists the village's Builders as takers of a free table, and the status says
`message.aliveworkplace.status.table`.

**A Storehouse with its own table (B297, B298, B299).** A Storehouse's chests are the Storehouse's, even when another
worker's bench stands nearer by them: `Village.stashes` lets the Storehouses take their chests first. Before, a Builder
at a Blueprint Table one block from the Storehouse's chests made them his, kept back for his own builds, and a Builder
at the Builder's Workshop's table counted none of them. A player's Storehouse in a village whose hall no player owns is
that village's store, so the village's own Builder on a village build (or none) counts it too. When the status says
"Needs", it also names the nearest Storehouse he doesn't count and why (`Village.unusedStorehouse`): more than the
village's 48 blocks from his table (the distance it gives is rounded up, so one just past the reach reads past it:
48.3 blocks says 49, B355), someone else's, or no chest round it he can read.

**Speed.** Exactly `workplaceBuildDelay` ticks per block (default 8; B515: it was one tick more) times a share by Builder level 1 to 5:
100%, 100%, 85%, 70%, 55%, 40% (index 0 is unused). Then the village's mood, traits, edicts and so on change it
(`work/Pace`). XP: 1 per 5 blocks placed and 10 for finishing a build (`BuilderLevels`).

**Queue and crew.** A Builder takes up to 5 more blueprints on top of its current one (`MAX_QUEUE`). Up to 3 idle
Builders (`MAX_HELPERS`; Guilds can change this) help one site, each claiming steps ahead of the lead. A helper that dies drops only its bag; the steps it claimed go back to the crew and the lead finishes the build (B516). The owner
asked that time shrink with each Builder: tested at 2 builders = 50%, 4 = 29% of the time alone (ROADMAP 23.1a).

**Who may order.** With `workplaceBuilderOwnership` on, a Builder takes orders only from the player who hired it,
their friends (`/workplace friend add|remove|list`) and operators.

**Upkeep and paths.** An idle Builder looks over buildings it (or its employer) finished anywhere in the village his
table stands in, or outside it within `maxSiteDistance` (36.20), every 1200 ticks, and fills only the holes with its usual materials; things players put there
are left alone.

**Asking before a repair (36.15).** With `repairAsk` on (the default) a Builder who finds blocks missing doesn't start:
the building goes on the list (`RepairAsks`) with the question for its owner, sent in chat to the owner (or, for a build
nobody hired the Builder for, the village's leader) as "Bram found 5 blocks missing from the Starter Cottage at x, y, z.
Repair it? [Yes] [Not now]". The Village Hall's **Repairs** tab (an anvil, one of the mod's spare pages) lists the
village's damaged builds, the questions waiting first, then repairs going on, agreed ones waiting for a free Builder,
paused ones and those left for now. A click on one opens it: **Yes, repair it** and **Not now** while it waits, **Pause**
and **Stop the repair** while a Builder repairs it, **Carry on** when paused. Yes sets the nearest free Builder who looks
after it to it at once. Not now leaves it as it is, and it is asked about again only once more goes missing (put right by hand meanwhile,
it comes off the list, and the next damage is asked about afresh); Pause
leaves it until Carry on. Pausing or stopping ends the repair there and then (what is put back stays, no blueprint is
handed out) and the Builder goes straight on to the next job in its queue. Handing a Builder a blueprint mid-repair
starts the new job at once instead of queueing it behind the repair; the repair is put aside, still agreed, and taken
up again when the Builder is free. Its owner, their friends, the village's leader and operators may answer; anyone
else is told it isn't theirs. Not asked: what threats took (32.16a: a gate a ram broke, a block a village fire burned),
repaired without asking as before, and a damaged Wonder's parts (35.8). `repairAsk` off: Builders repair by
themselves, as before 36.15. After a build, it can lay a dirt path to the village bell or hall (at most 96 blocks long).

**Safe at height.** A Builder with a build (lead or helper) takes no fall damage: he stands on the build's own
upper rows (a palisade's stake tops, a tower's platform before its parapet) and can step, jump or be shoved off them.
Any other harm still hurts him, and a Builder with no build falls like any villager (B120).

**Table preview (26.9).** Selecting a library entry sends `table_request_details`; the server answers with the
materials and a `table_preview` packet (`TableServer.preview`): a palette of block-state ids and x, y, z, palette
index per cell. Only blocks a player could see are sent (air, invisible blocks, jigsaw and structure blocks are
dropped, and a cell whose six neighbors all occlude is left out). If more than `PREVIEW_CAP` (4096) cells remain, the
build is grouped into 2×2×2 cells (then 3, …), each showing its highest block, until it fits. The result is worked out
once per blueprint and kept (48 at most); unknown, sold-only, world-generation and closed-expansion ids get nothing.
The client (`TablePreview`) draws the cells with the block renderer into the screen with a scissor box.
**Placement preview requests (B340).** A held blueprint's ghost preview asks with `preview_request`
(`PreviewNetworking`); the same blueprint asked again within 100 ticks isn't sent again. Only real blueprints are
remembered for that guard, and each player's entries older than 100 ticks are dropped when they ask again, so made-up
ids from a modified client leave nothing behind.
**Basements in the preview (36.1).** A build with a basement comes out of the library with its cellar under y = 0
(`StarterBlueprints.withBasement`); the packet carries `below`, how many blocks lie under the ground (0 for most), and
cells are keyed with floor division, so at scale 2 or more the first cellar layer (y = -1) never folds into the ground
floor's cell. The client fits and centers the build by its whole height (`sizeY + below`), and for a build with a
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

**Getting unstuck (23.2).** A Builder that can't walk to where it has to stand (no path at all, or a path it can't
follow) waits a moment, then hops onto that spot: it never digs or breaks its way out, so no block of the build and no
block of yours is ever broken getting it free. It gets out of a hole three deep, a well of water with sheer sides, an
island in a moat of lava, a ring of fences or a pen with its gate shut, a room with a wooden or an iron door, and the
top of its own build, in the tests within a few seconds (about 1.5 s for each of the harder traps), and goes on
building. The same holds when its chunk
unloads, or the server restarts, while it is trapped. Tests: `BuilderChaosGameTests` (five seeds, a new trap every 150
to 300 ticks through the starter cottage) and `QaBuilderStuckGameTests` (each harder trap three times through a build).

## 3. Switches

Config file keys (`WorkplaceConfig`):

| Key | Default | What it does |
|---|---|---|
| `supplyRadius` | 8 | Chests and barrels this close to a workstation are that villager's supply chests |
| `maxSiteDistance` | 128 | How far from its bench a Builder takes a build outside the village (anywhere in the village is in reach; also how far upkeep looks) |
| `builderPaths` | true | Builders lay a dirt path from each finished building to the bell or hall |
| `builderRepairs` | true | Idle Builders repair buildings they finished when blocks go missing |
| `repairAsk` | true | Builders ask before a repair (Repair it? Yes / Not now) and repair only what was agreed; off: they repair by themselves. Threat damage is never asked about |

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
- `builder` (the Builder's id; none by default), `queued` (false), `deconstruct` (false), `repair` (false),
  `refresh_from` (36.21: the older drawing a refresh brings the build on from, its key in `blueprint_history`; absent:
  not a refresh, as in every older save).
- `no_level_ground` (absent means leveling is on), `bench` (the lead's bench; absent in pre-0.6 saves).
- `steward`, `hall` (a Steward's build from a Village Hall; absent for a player's own), `waiting_since` (absent:
  not waiting) and `player_blocks` (0): used by Stewards (27.19).
- `rechecks` (0): how often the build went back for blocks broken mid-build; `left_out` (empty): the structure and
  decoration blocks given up for good, which that look leaves be (B508; older saves have neither).

On the villager (attachments in `registry/ModAttachments`):
- `builder_job` (`BuilderJob`): `site` and `helper` (false by default).
- `builder_bag`: the 27-slot bag, empty by default.
- `builder_employer`: who the Builder works for (fields not listed here).

Per finished building (36.15): `repair_ask` (`asked`, `yes`, `not_now` or `paused`; absent: never asked, as in every
save from before 36.15) and `repair_missing` (the blocks missing when it was last looked at). It is cleared when the
repair finishes, when nothing is missing any more, and when the building is taken down or built again on its spot.
`refresh_asked` (36.21, true while a refresh waits for a free Builder; absent: false) is kept with it the same way.

Friends are saved in `aliveworkplace_friends`. Not saved, worked out again: the plan, status, missing list, helpers'
claims and trip counts.

## 5. Items, blocks, jobs, commands

- Block: Builder's Bench (`aliveworkplace:builders_bench`), the job block. Profession: Builder (`entity.minecraft.villager.builder`).
- Items: Blueprint (`aliveworkplace:blueprint`; "Blueprint: %s" when named), Blank Blueprint (`aliveworkplace:blank_blueprint`, bought from
  villagers for emeralds), Builder's Tea (`aliveworkplace:builders_tea`). The Blueprint Table makes and uploads blueprints.
- Hand over: right-click the Builder with a Blueprint. Sneak-right-click with an empty hand: status. A placed Blueprint sneak-handed over its finished building: take it down (a busy Builder queues it and says so: "Builder will take down Test Hut after Cottage (number 1 in line). It needs no materials.", B514). A Blueprint, a Book and Quill or a
  Wonder Plan in the off hand works too when the main hand is empty.
- Commands: `/workplace blueprints`, `/workplace blueprint`, `/workplace import`, `/workplace sites`,
  `/workplace cancel <site>` (the blueprint stays placed; hand it back to carry on; a canceled take-down says "Canceled the
  take-down of Test Hut." and its blueprint, handed back plainly or sneaking, carries on taking it down, its tooltip
  reading "Give it to a Builder villager to carry on taking the building down"; moved to a new spot it's a plain
  blueprint again, B526), `/workplace friend add|remove|list`,
  `/workplace strip`, `/workplace repair yes|notnow <x> <y> <z>` (what the chat's [Yes] and [Not now] run, 36.15).
  Test and QA only: `/workplace soak`, `/workplace benchmark`.

## 6. Decisions

- Builder helpers must scale: about half the time with two builders, and so on (owner, 2026-10-04, ROADMAP 23.1a).
- Builder speed is fine: about 3.5 in-game days for the 22 starter builds (owner, 2026-10-04, 23.1a).
- Helpers only play a toss sound, no flying item, and a cap limits how many sounds play at once (owner, 2026-10-05,
  23.1b).
- "What do you need?" must be readable at a glance, from the site or the Builder (23.3, approved 2026-10-04).
- Placing must feel good: turn, mirror, ghost preview, move, cancel with materials returned (23.8, approved
  2026-10-04).
- A builder's steps need exactly the build's material list, nothing more (B22), and it fetches from its own site's
  bench chests and keeps its bench, never swapping to another builder's (B22, B28); no build breaks a bench, and a
  builder employed at a bench put down by a command holds it at once (B132).
- Progress never drops after a restart (B46, B64).
- Unfinished blocks are never claimed done: `MaterialRules.matches` ignores neighbor-dependent properties.
  Nor is a hole: before a build is called done (once LANDSCAPE has run out), the Builder looks over every structure
  and decoration block once. If one laid earlier is gone and its spot is open (a player, a mob or an explosion broke
  it mid-build; the same test the repair round uses, crops aside), the Builder goes back to the first such block and
  over the lists again, passing what still stands, and lays what's missing. It goes back at most twice a build; a
  block broken again after that is counted in skipped and left for the repair round. Blocks already given up
  (deferred, then skipped) are not gone back for (B508, `aPlankBrokenMidBuildIsBackWhenTheBuildEnds`).
- An upgrade is a whole new blueprint on the same spot. Where its new room's roof runs in under the old roof's edge,
  the wall is carried up to that edge, so the roofs meet with no open blocks between them; `tools/blueprints/holes.py`
  checks every blueprint for roof holes, gaps between roofs and stray empty blocks (owner's report, 2026-10-14).
- Every doorway a build leaves lets a villager through: a villager is 1.95 tall, so under a lintel two blocks up nothing
  in the doorway or just inside or outside it may raise the floor (a carpet, a slab, a stair or seat, a closed
  trapdoor). Carpets start a block in from the door, seats stand aside, and where a stair must start right inside (the
  Manor's) the door has a fanlight (an open trapdoor flat on the outside face) in place of a lintel: a stair's back half
  is a full block up, and a villager climbing onto it still has its back in the doorway. A bed counts too: it is 9/16
  high, under a villager's step, so its path runs over a bed like a slab, and no bed stands right inside a door (the
  Carpenter's Workshop and the builder's and carpenter's village looks). `tools/blueprints/check.py` refuses every
  blueprint that breaks it (B170, B173, B178). A whole block counts as well: a door opens onto a floor at its own
  level, never onto a window's old sill, a dais or a raised floor (that leaves a villager 1.00), and a wooden door
  never opens onto a wall (B487: the Healing Center III's garden door, the Mountain Lodge II's back door, the Royal
  Hall's vault door, now a block up level with the dais, and Royal Hall II's east door). Tents are two high only under their ridge: nothing stands in a tent's
  mouth there, and no carpet lies under the low sides (the War Camp's tents, B178).
- Not only in doorways: anywhere a villager walks, a step (a carpet, a shut trapdoor, a bottom slab or stair, a bed)
  leaves it its 1.95 under whatever is over it. The game lets a villager walk where the two cells its body is in are
  open, so a carpet under a ceiling two blocks up (1.94 of room) or a seat under a shut trapdoor is a place it walks
  to and can't get onto. `tools/blueprints/check.py` flags every such step a player can get to (B197).
- Nothing a build leaves is broken or changed by the game itself once placed: every bed has its head one block
  towards its facing (the Observatory's, B537); a standing lantern, candle or torch stands on a block whose top holds
  its middle, never a lectern, a chest or a bottom slab (the Great Library's lantern on a lectern, B538); and no dirt
  path or farmland lies under a block the game counts as solid (a fence post, a wall, a tent's wool, a bed), since the
  game turns it to dirt (the Hanging Gardens' paths, B539). The blueprint kit draws grass or coarse dirt there by
  itself, `tools/blueprints/check.py` flags all three, and `BlueprintsStayGameTests` reads every shipped blueprint for
  them with the game's own rules.
- No villager is left standing inside a block in a growing village. A baby fits in nooks a grown villager doesn't
  (under a roof's stair being laid, beside a lantern on a fence post), and when it grows up vanilla finds no room for
  the bigger body there; a villager standing still in a doorway can have the door shut on it by a neighbor walking
  through. Twice a second every villager looks whether its body is inside a block; found there twice in a row, it opens
  the wooden door it is shut in or else steps (with a puff) to the nearest spot within four blocks with a floor and
  room to stand. A villager only pressed against walls is never moved (B185).
- A villager walks in by a shut wooden door every time: it opens the door and carries on to where it was going. Vanilla
  works a villager's path out again whenever a block on it changes shape (a door opening or shutting), and aimed the
  new path at the last spot the villager had worked any path out for, which was often a bed, bell or workstation it had
  only looked at; the villager then turned round, shut the door behind it and walked off round the building. A path
  worked out again now goes where the villager is walking (B203).

- Builders ask before repairing a house, and a repair can be stopped, paused or put aside for another job (owner,
  2026-10-19, 36.15). Defense repairs after a threat (32.16a) stay automatic, as the owner hadn't objected to them: a
  wall or gate a siege broke goes back without a question.

## 7. Known limits

- **How far a build may be (36.20, the owner's "village-wide").** Anywhere in the village the builder's table stands in
  is in reach, however big the village: within the Village Hall's radius (`villageHallRadius`) or in a cell painted into
  one of the City Plan's zones, the table itself standing in either (a table in a zone painted in the plan's corner,
  past the radius, reaches the whole village too; B550, B557; QA's `QaReachZoneCornerGameTests` and
  `QaVillageReachBugTests`). Outside the village, a build's middle must be within `maxSiteDistance` (128) of the
  table. A spot past it is refused with its distance rounded up, so one just past the reach reads past it, and naming the
  village when the table stands in one: "That spot is outside Mapleridge and 140 blocks from the builder's Blueprint
  Table — keep it within 128." (`assign.too_far_village`; with no village round the table, `assign.too_far`: "That spot
  is 140 blocks from the builder's Blueprint Table — keep it within 128."; B378). Farmers', Miners' and other area jobs'
  spots follow the same rule and say it the same way. A far site in chunks nobody has loaded waits until someone does:
  nothing loads chunks for it. A world from before keeps its sites, and a config file holding the old default 48 moves to
  128 (format 5); any other number stays.
- Blocks on the never-placed list (spawners, portals, bedrock, command blocks...) are skipped, not substituted.
- Steps that cannot be done after being put off once are skipped, so a build can finish with a few blocks missing
  (the `skipped` count).
- Upkeep looks only every 1200 ticks and only at buildings within range of the bench, so a damaged build is asked
  about at a Builder's next round, not the moment it breaks.
- The question in chat goes only to a player who is online when the Builder asks; otherwise it waits on the Repairs page.
- A refresh (36.21) knows only the older drawings the mod ships (`data/aliveworkplace/blueprint_history/`, written by
  `tools/blueprints/history.py`: every earlier version of the blueprints changed since 1.0, and every version of the
  Stone House line). A build of an older drawing not shipped, an imported or uploaded build, and a modded-furniture
  variant drawn only in the `modded/` files show no button. The Repairs page lists refreshes after the repairs, so with
  more builds than fit, the last ones are counted under "more" rather than shown.
- A site in unloaded chunks, queued, or without a Builder is not watched for stalls.
- The table preview leaves out fluids (water and lava have no block model) and draws blocks at full brightness.
- Not tested here: the pack-server soak result of the last run (see the soak scene's pass or fail on the showcase).

## 8. Proof

`RefreshGameTests` (36.21, through the Village Hall's Repairs page): a Stone House built from its first shipped drawing
refreshed to the current one (every leftover gone or turned into the new piece, every new piece there, no button
after); a small build's refresh keeping a chest and its diamonds, a bed and its sleeper's claim, a composter and the
owner's own blocks, the leftovers' items in the store; no free Builder (it waits, the first one free takes it up);
short of planks (the leftovers away, then the Builder waits and says what's missing, as for a repair); a build of the
latest drawing (no button); saved and reloaded mid-way (it finishes).
`QaRefreshOldHouseGameTests` (36.21, the tester's, through the same page): the preview's exact counts by kind and every
sentence of the feature; the nearer of two free Builders gets it; two hall screens pressing the button start one
refresh; a player who doesn't own the build can't start one; every leftover's item reaches the store exactly once (also
across a save and reload mid-way, and none for a leftover a player broke first); a player's block where a new piece
goes stays; with `builderRepairs` off a waiting refresh is still taken up; a Builder dying mid-way leaves the build on
the page for another to finish.

Compat tests (QA, lane E, from "Builders never copy what is inside containers"): `QaPastureCopyCompatTests` (a scanned
Cobblemon Pasture Block with a Machop let out in it is built holding no Pokémon and lets out no second Machop),
`QaCreateItemsHeldCompatTests` (a scanned Create depot, deployer and basin each holding a diamond are built empty),
`QaCreateFluidsCompatTests`, `QaCreateDrainFluidsCompatTests` and `QaCreateSchematicFluidsCompatTests` (B486: a Create
fluid tank, spout, basin and item drain each holding a bucket of lava, scanned or uploaded as a schematic, are built from
exactly their material list holding no lava). `BuiltEmptyGameTests.aFluidHeldInBlockDataIsNotCopied` (B486, without
Create: Create's, Forge's and Fabric's saved fluids are dropped, the tank's size and capacity kept).

GameTests (`src/gametest/java/.../test/`): `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds` (B537-B539: in every shipped blueprint no standing lantern, candle or torch is on a top that can't hold it, no dirt path or farmland under a solid block, no half bed; with QaWonderDesignsGameTests' qaWonderStagesStand and qaWonderBedsWhole now run for the Great Library, the Hanging Gardens and the Observatory too), `QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo` (QA, from B149: in every build the Table lists but the modded looks, Wonder stages and the two camps of B424, every chest, barrel, bed and job block is within reach of a spot walked to from the ground outside), `QaStageFourBugTests.scanToolMarksACornerOnAChestOrJobBlock` (B335, a Scan Tool click on a chest, barrel, crafting table, furnace, trapdoor or Builder's Bench marks the corner), `BuilderGameTests` (108 tests), `BuilderCrewGameTests` (3),
`BuilderChaosGameTests` (5 seeds, stuck recovery), `QaBuilderStuckGameTests` (6, 23.2: a 3-deep hole, an iron-door room, a deep well of water, a lava moat, a fence pen with a shut gate, and the hole through a restart, each three times: out within 600 ticks, nothing placed or of the trap broken, the build whole), `B185GameTests` (6, villagers let out of a block), `BuildSiteSaveGameTests` (2), `BuilderBenchGameTests` (2),
`BuildSiteStepsGameTests` (2), `BuildReserveGameTests` (5), `StallWatchGameTests` (7), `BuilderKeepsBenchGameTests`, `B132BenchGameTests` (2, a neighbor's bench outlasts a build;
a bench put down and employed at in one command is held),
`TerrainStallGameTests`, `BuildersShareSpareGameTests`, `PokemonBuildsGameTests`, `QaJobBuildsGameTests` and `QaJobBuildsCompatTests` (28.13: each job's Journeyman sells their build and a Novice or an Apprentice doesn't; in all ten builds the job block takes a villager sneak-clicked with the job's item), `QaStageFiveGameTests.madeUpPreviewIdsAreNotRemembered` (B340: 5000 made-up preview ids remembered as 0, a real one
answered, its repeat at once ignored and answered after 100 ticks), `TablePreviewGameTests` (7, the table's
3D preview data; 36.1: Starter Cottage IV's preview has cells below the ground and its fitted height counts them, and
at scale 2 no cellar block shares a cell with a ground-floor block), `DoorwayHeadroomGameTests` (7, B173: a villager
walks in by the Healing Center's front door onto its carpet, and by the Manor's front door onto the stair right inside:
`DoorwayHeadroomGameTests.aVillagerWalksInByTheHealingCentersFrontDoor`,
`DoorwayHeadroomGameTests.aVillagerWalksInByTheManorsFrontDoorOntoItsStair`; B178: three villagers walk from the War
Camp's yard up to the beds of two canvas tents and into the Warlord's tent up to its barrel,
`DoorwayHeadroomGameTests.villagersWalkIntoTheWarCampsTents`; B197: a villager in the Starter Cottage sits down on the
chair under the front window, where the blinds were,
`DoorwayHeadroomGameTests.aVillagerSitsDownOnTheStarterCottagesChairUnderTheWindow`; B203: with the front doors shut
as built, a villager opens them and walks in while it keeps looking at a spot outside, as villagers look at beds and
bells, `DoorwayHeadroomGameTests.aVillagerOpensTheHealingCentersShutFrontDoorAndWalksIn`,
`DoorwayHeadroomGameTests.aVillagerOpensTheManorsShutFrontDoorAndClimbsItsStair`, and a jobless villager opens the
Healing Center's door and takes its brewing stand as a cleric,
`DoorwayHeadroomGameTests.aJoblessVillagerOpensTheHealingCentersShutDoorAndTakesItsBrewingStand`).

`B261GameTests` (8, B261: a Storehouse Shed missing its hay bale and bottom ladder is repaired from its own chest,
`aStorehouseShedIsRepairedFromItsOwnChests`; a shed repaired from the chest, barrel, double chest (its second half),
trapped chest or chest one block above the bench inside it, and after a reload; a new build still leaves its own box's
chests out). `RepairAskGameTests` (9, 36.15: the ask with nothing started and the page's Yes and Not now,
`theBuilderAsksBeforeRepairing`; Yes on the hall's page repairs it, `yesOnTheHallPageRepairsIt`; Not now from the chat
command and asked again once more goes missing, a stranger refused, `notNowLeavesItUntilMoreGoesMissing`; Stop and
Pause mid-repair with the builder going straight to its queued job, `aRepairGoingOnStopsAndPausesFromTheHall`; a new
blueprint puts the repair aside, `aNewJobPutsTheRepairAside`; the switch off, `withTheSwitchOffTheyRepairByThemselves`;
a threat's damage not asked about, `whatAThreatTookIsRepairedWithoutAsking`; save and reload,
`answersSurviveASaveAndReload`; the text, `repairAskWordsAreWritten`).
`BuilderSiteListGameTests` (8, B279 and B394's off-hand book on the bench: a Book and Quill on the builder mid-build is the site's remaining steps less the
chest, `aBookAndQuillOnTheBuilderWritesWhatIsLeft`; the bag and a storehouse come off, `theBagAndTheVillageStoreComeOff`;
every stage and after a save and reload, `everyStageAndAfterAReload`; a finished and an idle builder say so,
`aFinishedOrIdleBuilderSaysSo`; the empty-hand chat list, `anEmptyHandShowsTheWholeListInChat`; the bench,
`aBookAndQuillOnTheBenchWritesTheList`; taking down and the book's words, `takingDownNeedsNothing`).
`QaOffHandBookGameTests` (2, QA on B394: an off-hand Book and Quill doesn't stop the Postal Desk handing out waiting
parcels, `anOffHandBookDoesNotStopThePostalDeskHandingOutParcels`; on a bench with no build it says so and is kept,
`anOffHandBookOnABenchWithNoBuildSaysSoAndIsKept`).
`B290GameTests` (6, B290: a hired Builder at a workshop table 34 blocks from the village's unowned Storehouse, in a
village whose hall his employer owns, counts its stone overhead, in the list and the status and builds from it,
`aHiredBuilderAtAWorkshopTableBuildsFromTheVillageStorehouse`; a player's Storehouse feeds the village's own Builder on
that player's build, `aPlayersStorehouseFeedsTheVillagesBuilderOnTheirBuild`; a stranger's stays theirs,
`aStrangersStorehouseStaysTheirs`; a sneak-click moves the Builder and his build to another table, both standing, after
a reload too, `aSneakClickMovesTheBuilderToAnotherTable`; nobody near moves nobody, `aSneakClickWithNoBuilderNearMovesNobody`;
the hall offers a free table to a Builder, `theHallOffersAFreeTableToTheVillagesBuilder`).
`B297GameTests` (6, B297-B299: the owner's layout, a player's Storehouse with its own table one block from its chests
and a workshop table 31 blocks off: a Builder at each counts and takes it, `bothBuildersCountAndTakeTheStorehouseWithItsOwnTable`;
alone at the workshop table, the Storehouse saved and loaded, `aBuilderAtTheWorkshopTableTakesFromAStorehouseWithAFreeTable`;
the village's own Builder and the player's Storehouse, `theVillagesOwnBuilderCountsThePlayersStorehouse`; the status's
reasons, `theStatusSaysAStorehouseIsTooFar`, `theStatusSaysAStrangersStorehouseIsntHis`, `theStatusSaysAStorehouseHasNoChests`).
`QaBuilderStockGameTests` (9, QA from B226/B290/B297's specs: stock split between the table's chest and the Storehouse
builds the hut, `theStockSplitBetweenTheTableAndTheStorehouseBuildsTheHut`; a Storehouse emptied after it was counted
holds the Builder until filled again, `aStorehouseEmptiedAfterItWasCountedHoldsTheBuilderUntilItIsFilledAgain`; the same
with the Builder saved and loaded while waiting, `aBuilderWaitingOnTheStorehouseFinishesAfterAReload`; a Storehouse
right at the village's reach is counted, `aStorehouseRightAtTheVillagesReachIsCounted`; `villageRadius` 0 shares
nothing, `withNoVillageSharingTheStorehouseIsNotTaken`; a Storehouse broken mid-job stops feeding him until placed back,
`aStorehouseBrokenMidJobStopsFeedingTheBuilderUntilPlacedBack`; barrels do as chests, `aStorehouseOfBarrelsFeedsTheBuilder`;
a Storehouse with its Porter counts once, `aStorehouseWithItsPorterIsCountedOnce`; a built Terminal Nook (37.17) feeds
the village's Builder, `aBuiltTerminalNookFeedsTheVillagesBuilder`).
`QaBuilderStockBugTests` (1, B355: a Storehouse just past the reach, 20.4 blocks off in a 20-block village, is too far and
the status says so with numbers that agree, the distance rounded up to 21, `aStorehouseJustPastTheReachIsTooFarAndTheNumbersSaySo`).
`QaBuilderLimitsBugTests` (1, B378: a spot 48.5 blocks from the table is refused with a number past the 48-block reach,
`aSpotJustPastTheReachIsRefusedWithANumberPastTheReach`).
`QaChairFacingGameTests` (4, QA from B148: in every listed build each stair chair beside a table opens toward it, 251
chairs, and still does turned four ways and mirrored, 2008; a Manor chair turned round is caught).
`QaMirroredBuildsGameTests` (2, QA from B438: every starter build's doors, beds and double chests stay whole in all four
turns, plain and mirrored; a builder builds the mirrored Tinker's Workshop with its double chest joined) and
`QaTurnedFramesGameTests` (2, QA from B439: every starter build's item frames and paintings hang on their wall in all four turns, plain and mirrored; a builder finishes the Guildhall turned a quarter and mirrored with its item frame), `B439GameTests` (1: an item frame on a wall and on a floor, a glow item frame, a one- and a two-wide painting and an armor stand each stand where the blueprint puts them and face its way in every turn and all three mirror settings, also after a save and load), `QaTurnedModBlocksGameTests` (2, QA from B448: every facing block in every starter build faces its blueprint facing turned with the build, in all four turns, plain and mirrored; a builder builds the Storehouse turned a quarter with its Storehouse block turned), `B448GameTests` (3: every block of ours with a facing turns and mirrors like vanilla's and keeps its other state; an old-facing Storehouse block in a finished turned build, and in one going up, stays built through a restart, keeping its orders, nothing skipped), `MirroredChestGameTests` (2, B438: a double chest facing any way stays joined in every turn and both mirrors, plain or
trapped, and joins when placed).
`QaDoorwayClearanceGameTests` (5, QA from B173: a villager slid through a doorway with a carpet under the lintel is
caught, and so are the Village Hall's and Supply Shop's with one moved there; villagers walk in by the shut front doors of
the Supply Shop, turned too, and the Village Hall; the sweep over every build's doors is B487's test on tests/qa-doorways-1025).
`QaDoorwayBugGameTests` (5, QA from B173, fixed by B487: the sweep over every listed build's doors, and walks in by the
Healing Center III's garden door, Royal Hall II's east door and the lodge look II's front door, and out of the Royal
Hall's vault) and `QaBuilderFixes1027bGameTests` (B487's four: a villager walks in by the Healing Center IV's garden
door turned a quarter, out by the lodge look II's front door turned the other way, out by Royal Hall II's east door,
and into the Royal Hall's vault from the dais with the hall turned half round; all four fail on the builds before the
fix).
`QaWallTowerGameTests` (7, QA from B283: hall rounds open nothing on a Palisade Tower half way up, two towers decorating
or a finished one; a tower saved and loaded mid-decoration stays one tower and is finished where it stands; its ladders
balance when its builder is killed holding three and every second of a real build).
`QaScanNameBugTests.aScanNamedWithAnApostropheAndAnAccentKeepsItsName` (B458, QA: a Scan Tool named "Jesse's Café" in an anvil saves a build called that), `BlueprintNamesGameTests` (3, B458: two scans of one accented name are "…" and "… 2" on the blueprint, the saved line and after the saved names are written and read back; an unnamed scan is still "<Player>s Build"; an upload `Zoë's Hut.litematic` is "Zoë's Hut" and a plain `zoe_hut` file "Zoe Hut"). QA's `QaBuilderFixes1027GameTests` (7 of its 9 here, scanned with the Scan Tool's clicks and built by a builder turned a quarter and mirrored, a restart mid-build: a room's east-facing double chest and west-facing trapped double chest stay joined and are built empty, B438; its Storehouse, Mailbox, Shop Counter and Grave face their drawn facing mirrored and turned, nothing skipped, and only those five data blocks, with the Village Hall, count as built facing any way (a wrong-facing furnace or stairs doesn't), B448; a room's glow item frame, item frame, painting and armor stand are on its material list without what they held, and built again empty, once each, facing the mirrored, turned way and paid from the chest, B441; a Scan Tool named "日本の家" or "cozy den" names its build exactly that, also after the saved names are read back, B458). `QaBuilderVerifyScan1026bBugTests.aScannedMailboxWithMailIsBuiltEmptyAndShowsNoMail` (B536, QA: a Mailbox scanned holding mail is built with has_mail false). `QaBlueprintLootGameTests` (1, QA: a scanned build of a lectern with a book, a jukebox with a disc, a chiseled bookshelf
with a book, a decorated pot of diamonds, a campfire cooking beef, a sign and a named chest of gold, built by a Builder:
every block comes out empty, the sign keeps its words and the chest its name,
`aBuildOfBlocksHoldingItemsComesOutEmptyWithItsWordsAndNames`).
`QaBuilderClicksGameTests` (8, QA from B279/B335/B337/B342/B344's specs: a Book and Quill on the Builder's Blueprint
Table writes the exact list less the table's chest, `aBookAndQuillOnTheBuildersTableWritesTheListLessItsChest`; a table
with no build or no Builder keeps the book, `aTableWithNoBuildOrNoBuilderKeepsTheBook`; the book from the off hand,
`aBookAndQuillFromTheOffHandOnTheBuilder`; the list after a save and reload mid-structure, book and chat,
`theListAfterAReloadMidStructure`; two books with a full inventory, `aStackOfBooksWithAFullInventoryKeepsTheList`; a
placed blueprint from the off hand, `aPlacedBlueprintFromTheOffHand`; Scan Tool corners on a chest then a barrel, neither
opened, `scanToolCornersOnAChestThenABarrel`; the Storehouse board asks for nothing more after a hand-over,
`theBoardOpenedAgainAfterAHandOverAsksForNothingMore`).
`QaBuildsGameTests` (9, QA from B280/B300's specs and the builder's invariants: a cottage clicked onto a flower on a bump
leaves the lawn level, `aCottageClickedOntoAFlowerOnABumpLeavesTheLawnLevel`; a stall on tall grass leaves the lower
terrace beside it low, `aStallClickedOntoTallGrassLeavesTheLowerTerraceBesideItLow`; pots whose support breaks mid-build
are set down, `potsWhoseSupportIsBrokenMidBuildAreSetDown`, also across a reload, `aPotUnderminedThenAReloadMidDecorationIsStillSetDown`;
a reload mid-foundation or mid-landscape loses and makes nothing, `aReloadMidFoundationLosesAndMakesNothing`,
`aReloadMidLandscapeLosesAndMakesNothing`; the lead takes over a block two crew members hold,
`theLeadTakesABlockOverFromTwoMembersHoldingItAtOnce`, while a member on another column keeps its block and column,
`aMemberOnAnotherColumnKeepsItWhenTheLeadTakesAHoleOver`; `workplaceLevelGround` 0 leaves the ground alone,
`levelGroundZeroLeavesTheGroundAlone`).
`QaBuilderSwitchesGameTests` (4, QA of the builder's switches: `workplaceBuilderOwnership` off lets a stranger give a
hired Builder a blueprint, `ownershipOffLetsAStrangerGiveTheBuilderABlueprint`; `workplaceLevelGround` 8 levels 8 blocks
out and no further, `levelGroundEightLevelsEightBlocksOutAndNoFurther`; `workplaceFoundationDepth` 0 puts nothing in a
dip and uses no extra cobblestone, `foundationDepthZeroPutsNothingIntoTheDip`; the Blueprint Table hides the Mega Shrine
and Train Station while their expansions are closed, `theTableHidesTheM38AndM39BuildsWhileTheirExpansionsAreClosed`).
`QaBuilderLimitsGameTests` (11, QA of the builder's limits from the player guide, each from both sides through the
player's clicks: a Book and Quill on a Blueprint Table nobody works at opens the table, from either hand; five blueprints
line up as numbers 1 to 5 and the sixth is refused with "already has 5 blueprints lined up" and kept; the line keeps its
order over a save and each `/workplace cancel` takes up the next and hands the blueprint back still placed; a spot 48
blocks from the table is taken and 49 refused; chests 8 blocks off (and 4 up) count and 9 off don't; 4 under the table count and 5 under or 5 up don't; four idle builders
make a crew of three helpers, never four; a hired builder killed with a build and two in line drops all three blueprints
where he fell and his owner reads builder_died with the build's name and the spot; a hut placed overlapping the one under
way, or in the Nether, is refused with its sentence and kept). `QaScanEdgesGameTests` (4, QA: a Scan Tool box with nothing
built in it saves nothing and keeps the Blank; with no Blank in survival nothing is saved and the player is told; in
creative no Blank is needed nor used; in survival the save takes the Blank from any slot, the first included). `QaLevelUpWordsGameTests` (1, QA: each level-up from
Apprentice to Master tells the owner the level_up sentence with the new level's speed, 18%, 43%, 82% and 150%). `QaNeverPlacedGameTests` (1: a blueprint with a spawner, bedrock and budding
amethyst between cobblestone asks only for the cobblestone and is finished with none of the three placed). `QaBlueprintHandlingGameTests` (3: place, four quarter turns back to the
start, pick up, the leveling switch off/on/off kept through placing, turning and the hand-over; a building taken down
with the chest full loses nothing; an upgrade lines up over its own finished building, turned or not, never when
sneaking and never over another kind of building). `QaTakeDownGameTests` (8: a camp, a turned and mirrored hut and a hut with its table broken come down losing no block; a plain hand-over leaves the building standing; a stranger can't have it taken down; a take-down handed to a busy Builder waits its turn; one canceled halfway carries on when its blueprint is handed back as it came; the placed blueprint's tooltip tells how) and `QaTakeDownBugTests` (2: a canceled take-down's blueprint comes back still placed, B513; a queued take-down is announced as one, with no materials sentence, B514). `TakeDownCancelGameTests` (2, B526: a take-down canceled halfway says it's a take-down, its blueprint goes through a save and comes back to a plain hand-back carrying on the take-down; the take-down mark is kept at its spot and through a save, dropped when moved, and an old blueprint reads as plain) and QA's `QaBuilderFixes1026BugTests` (1: handed back as the cancel line says, the take-down carries on). QA's `QaBuilderFixes1026GameTests` (5: the lead killed while two helpers work ends the build once, one blueprint on the ground, builder_died with the hut and the spot, the helpers stop and nothing is doubled or lost; a helper killed then a save and load still finishes the hut whole, no blueprint dropped, no builder_died; a queued take-down and a queued build each say their own exact line; and two scaffolding cases, on its page). `QaShapePlannerBugTests` (2, B379: drawing without a Blank Blueprint and a failed save each speak of the shape, never of a scan). `QaShapePlannerGameTests` (6, the Shape Planner's screen clicked as the client
does: each side 1 to 32, no Blank Blueprint or no block draws nothing and takes nothing, two draws give the same build
for two blanks, a hollow and a solid box of one size are two builds, a blank in the first slot is used and a full bag
gets the blueprint at the player's feet, a builder builds a drawn box from the chest). `QaScanRoundTripGameTests` (1, from
the Scan Tool's guide page: a hand-built room with two double chests lying either way, a door, a bed, a wall torch, a
hanging lantern, an upside-down stair and a ladder, scanned with real clicks and built again a quarter turned, comes out
block for block turned, the chests still double, `aScannedRoomIsBuiltAgainTurnedWithEveryPieceWhole`). `QaScanPotGameTests`
(1: a decorated pot of four pottery sherds on a stone, scanned with real clicks and built from a chest holding one plain pot,
shatters into no sherd when broken with a pickaxe, while the scanned pot itself gives its four,
`aScannedSherdPotGivesNoFreeSherds`). `ScanEntitiesGameTests` (2, B441: a room scanned
with real clicks holding a framed diamond, a painting, an armor stand in a helmet, a cow, a villager, a dropped diamond
and a loaded chest minecart keeps only the frame, painting and stand, empty; built again a quarter turned from a chest it
gets an empty frame facing the turned way and the painting, both paid, and no stand with none in the chest; with one, a
bare stand, the helmet left in the chest) and `QaScanFramesGameTests` (1, QA from B441: a scanned room's item frame and
armor stand are built again).

Showcase scenes (`tools/showcase/scenes.py`): `repair_ask` (36.15: the question in chat, the Repairs page, Yes),
`refresh_old_house` (36.21: a Stone House of its first drawing, the Refresh button with what changes, the house after), `builders` (three builders, three starter builds), `soak` (10 builders,
every starter build), `preview` (ghost preview and status), `placing` (turn, mirror, cancel, slope), `missing` (the
missing-materials tooltip and list), `table`, `table_preview` (the library's 3D preview), `table_basement` (Starter Cottage IV and the Storehouse with their cellars), `shapes`, `scan`, `style_menu`, `crew` (four builders on one build),
`pace` (speed cap), `partners_engine`, `builder_list` (B279: the list of a build under way, as a book and in chat),
`builder_table_move` (B290: the Storehouse counted at a workshop table, and a sneak-click moving the builder).
