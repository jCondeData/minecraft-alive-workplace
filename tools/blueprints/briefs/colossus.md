# Colossus: the brief

Family id(s): the Wonder `aliveworkplace:colossus` (never renamed); its drawing is the stage parts
`wonder/colossus/stage_<n>_<k>` (today `stage_1_0` .. `stage_4_0`, one part a stage), listed with their offsets in
`data/aliveworkplace/wonders/colossus.json`, and the whole blueprint `aliveworkplace:wonder/colossus` the code lays
together from them (`WonderBlueprints.merge`). Variants: village (none), modded (none), looks (none: a Wonder keeps its
own blocks; its plan can only be mirrored, `WonderPlanItem`; no `village_colours`).

A flagship family (ROADMAP 52.6): two designers (A and B) work from this one brief.

## What it is for
A Wonder (Milestone 35): the village's statue of its **Founder**, raised over the village's main road. A player draws
up its plans at the Village Hall's Wonders page once the village is a **City** with the Founder Legend (stand-in: City
rank) and the Master Architect, places the Wonder Plan (its front, z = 0, faces the player), and hands it to a builder:
a **Foundation Stone** goes down just outside the front corner (template (-1, 0, -1), else (-2, 0, -1), (-1, 0, -2),
(-2, 0, -2), (-1, 1, -1); `WonderStages.STONE_SPOTS`), the chests within 8 of it are the stockpile, and a **crew of up
to 12 builders** raises it in **four stages**, each started once the one before is done and its goods are dedicated.
Finished, it is announced to the server, a festival fires a rocket from its top (`WonderCelebration.top`: over the
middle of its footprint), and its powers come on while it stands: everyone in the realm 8 happier (12 at home),
travelers to the village's inn twice as often and twice as likely Experts, 8 beauty (`colossus.json` "powers"; none of
them reads a block). The one power that reads the drawing is the **Founder's plaque** (35.22, `ColossusPlaque`): on
finishing, a row of wall signs over the front is written, in glowing brass-yellow waxed letters, with "Raised in honor of
/ the Founder <name> / who founded / <village> / on day <N> / Finished by its people on day <M> / May it stand a
thousand years". Nobody lives or works in it.

## Levels (its stages)
| Stage | Parts | What it adds (function, not looks) | Unlock / goods (data file, unchanged by the drawing) |
|---|---|---|---|
| I *The Plinth and the Road Arch* | `stage_1_*` | Clears the site (stage I is saved whole, air included, so the builders clear it; only stage I may have a part of air alone) and raises the base with the road arch through it, the braziers and the plaque's signs | City rank, the Founder; goods: Heart of the Sea, Coast trim, 2 Emerald Brooches |
| II *The Robes* | `stage_2_*` | The figure's lower body | Trident, Sponge, Netherite upgrade, 2 Gold Circlets |
| III *The Arms and the Cloak* | `stage_3_*` | The arms and the cloak (most of the copper) | Tide trim, Totem, Wither skull, 2 Noble Robes |
| IV *The Head and the Lantern* | `stage_4_*` | The head with its face, and the raised lantern | Heavy Core, Nether Star, Elytra, 2 Vintage Wines |

The four titles are player text (`wonder.aliveworkplace.colossus.stage.1..4`) and are tested word for word
(`QaWonderDesignsGameTests.qaWonderNames_colossus`, `QaWonders1101GameTests`). Draw each stage so it matches its title;
a design whose stages raise something else means renaming the titles in `en_us.json` and both tests together at the
ship step, a decision to note, not a default.

## Must have
- Job blocks: none (no villager POI block is needed; vanilla villagers may claim any workstation drawn in it).
- Beds and residents: none. Storage: none (the stockpile is the chests round the Foundation Stone, outside the drawing).
- Furniture kinds a job or test needs: none (no `blueprint_furniture/` file).
- **The Founder's plaque: exactly seven `minecraft:dark_oak_wall_sign`, all at the same height and facing the same
  way, facing the front (z = 0 side, "north"), readable from the road in front**
  (`QaWonderDesigns1105GameTests.qaColossusLanternCagedAndRaised`: 7 dark oak wall signs; `ColossusPowerGameTests.
  thePlaqueReadsTheVillageAndItsFoundingDay`: `ColossusPlaque.signs` finds 7 and writes all 7). **No other wall sign of
  any wood anywhere in the Colossus:** `ColossusPlaque.signs` takes the row (same y, same facing) of the *first* wall
  sign in the drawing, so a stray wall sign elsewhere hides the plaque. Each sign needs a block behind it to hang on
  (it must survive, see "stand" below). Read order is left to right for someone facing the signs; the code sorts them
  itself, so they need not touch, but seven in one row is the plaque.
- **The lantern: glowstone caged in glass, raised to the head.** At least one `minecraft:glowstone`; every glowstone's
  four horizontal neighbours (past any glowstone beside it) are glass (any block whose id contains `glass`: panes or
  blocks), and the lowest glowstone is no lower than the eyes (`qaColossusLanternCagedAndRaised`). The showcase
  (`WonderColossusScene.nightChecks`, scene `wonder_colossus`) wants **at least 3 glowstone within the top 5 layers**
  (y from top - 4 to top) of the whole Wonder. At least 4 glass panes or glass blocks in all
  (`QaWonderDesignsGameTests.qaWonderFeatures_colossus`).
- **The face, looking to the front**: at least two `minecraft:emerald_block` (the pupils), the first found at y >= 34,
  all at one height and depth (the test reads the first's y and z); and **a nose**: at least 2 blocks within one column
  of the eyes' middle (|x - eyes' middle| <= 1), from the eyes' height down to 8 below, standing 1 to 3 blocks in front of
  the eyes toward the nearer of the front/back edges (`qaColossusNoseStandsOutPastTheEyes`). So the face sits nearer the
  front (z = 0) than the back, and the nose comes toward the front. No other emerald blocks lower down (the eye height
  is read from whichever is found first).
- **Nine lit braziers: exactly 9 `minecraft:campfire`, all lit** (`qaColossusLanternCagedAndRaised`; at least 4 in
  `qaWonderFeatures_colossus`). Campfires are never cleared by builders and keep their lit state as drawn.
- **Copper that weathers: 333 to 407 copper blocks in all** (any of `cut_copper`, `cut_copper_stairs`,
  `cut_copper_slab`, `copper_block`, `chiseled_copper`, `copper_grate`), **none waxed and none pre-weathered**, and at
  least **150 of them cut copper** (block, stairs or slab) (`QaWonderDesigns1105GameTests.qaColossusCopperWeathers`,
  `qaWonderFeatures_colossus`). The daily upkeep count forgives weathered copper (`MaterialRules.unweathered`).
- **At least 500 `polished_diorite` + `smooth_stone`** together (`qaWonderFeatures_colossus`: "a figure of polished
  diorite and smooth stone").
- These block ids are requirements only because tests read them; how they are used and where is free (other than the
  rules above). If a design truly needs a different set, the ship step changes `QaWonderDesignsGameTests.features`,
  `QaWonderDesigns1105GameTests`, the wiki (`docs/wiki/wonders.md`, "nine braziers, seven signs") and `docs/guide/`
  together: a decision to note, not a default.

## Fits
- Plot: **exactly 47 high** (top block at y = 46; `QaWonderDesignsGameTests.qaWonderSizes_colossus`, tolerance 0),
  starting at y = 0 (the bottom layer sits on the clicked block). Width and depth are free within the placement rules
  (`WonderPlans.placeRefusal`: the footprint's centre within 64 of the hall, every corner within 96, over no building,
  build site or other Wonder); the showcase gallery frames it as 29 x 47 x 29 (`WonderGalleryScene.WONDERS`, a scene
  number the ship step updates if the footprint changes). Each saved part is at most 48 a side
  (`WonderBlueprintGameTests.wonderPartsLoad_colossus`), which a 47-high Wonder meets with one part a stage.
- **The road arch** (`qaWonderSizes_colossus`): looking from front to back through the *whole* depth of the Wonder, a
  passage **exactly 5 columns (x) wide** where at least 5 of the 6 cells y = 0..5 are open (no block at all, from the
  front edge to the back edge), and **exactly 6 high at its crown** (some column open for all of y = 0..5). No other x
  column of the whole bounding box may be open that way, the outermost ones included (a side column with nothing drawn
  low down, under an overhang or beside a narrower base, would widen the count). So the floor of the passage is the ground under the Wonder
  (nothing drawn at y = 0 there) and nothing at all, not a lamp or a sign, may stand in it front to back.
- **The base is about 13 high**: the highest layer (y 0..19) whose blocks span 13 or more along x must be y = 11, 12 or
  13 (`qaWonderSizes_colossus`, "the stepped plinth's height", 13 +-1). Every layer above y = 13 must span less than 13
  along x, up to y = 19.
- Front and way in: the front is the z = 0 side, toward the road and the player who placed the plan; the face, the
  plaque and the arch's mouth read from there. The Steward's roads do **not** route through the arch: they keep off a
  Wonder's whole footprint (`Roads`, `WonderPlans.footprints`, B753); the arch is for the player's own road and walkers.
- Neighbours: the Foundation Stone and its stockpile chests stand just outside the front-left corner (template -1..-2,
  0..1, -1..-2): leave that corner's outside reachable (it is outside the drawing). At a dedication the village gathers
  round the stone and the guards line up outside the Wonder's corner (`Dedications`).

## Rules tests check
- `WonderBlueprintGameTests` (generated per Wonder): `wonderFileLoads_colossus` (the data file loads, every stage has
  goods); `wonderPartsLoad_colossus` (every part loads, at most 48 a side, none empty but a stage-I clearing part, all
  in `wonder/colossus/`, out of the Blueprint Table, every name has its line); `wonderStagesOnlyAdd_colossus` (no part
  sets air where an earlier stage put a block); **`wonderBlockCount_colossus`: the sum of every part's non-air blocks
  within 10% of 9,000 (8,100 to 9,900)**; `wonderMaterials_colossus` (no retired job block, `StationsSpecGameTests.gone()`;
  no calcite; nothing a player can't get).
- `QaWonderDesignsGameTests` (per Wonder): `qaWonderSizes_colossus` (47 high, the arch, the base; above);
  `qaWonderFeatures_colossus` (above); **`qaWonderStagesGrounded_colossus`: after every stage, every block joins y = 0
  through blocks touching by face, edge or corner** (the head and lantern held by the stage they come in, never by a
  later one); **`qaWonderStagesStand_colossus`: built for real stage by stage on stone, every block can stay by
  vanilla's rules** (signs on a backing, lanterns hung or standing on a full top, nothing that falls over air);
  `qaWonderDoorways_colossus` (every door whole, two clear blocks on both its sides); `qaWonderNothingFree_colossus` (no
  container, lectern, hive, composter or brewing stand holds anything); `qaWonderBedsWhole_colossus`;
  `qaWonderNames_colossus` (the four titles).
- `QaWonderDesigns1105GameTests`: `qaColossusCopperWeathers`, `qaColossusNoseStandsOutPastTheEyes`,
  `qaColossusLanternCagedAndRaised` (above).
- `ColossusPowerGameTests.thePlaqueReadsTheVillageAndItsFoundingDay`: places the Wonder at test origin (0, 1, 0) in
  `huge_area` and finds 7 plaque signs in the drawing, builds them on a dark oak backing and writes them.
- `QaBuilderFixes1111bGameTests.withTheSwitchOffEveryStageOfEveryWonderIsRefused` asserts `wonder/colossus/stage_1_0`
  .. `stage_4_0` exist on disk: **keep at least one part per stage named `stage_<n>_0`**. `QaBuilderFixes1107dBugTests`
  asks for `stage_1_0` .. `stage_3_0` by id.
- Whole-Wonder glass rule, `QaWindowsAndWards1028BugTests` (B648): every pane or bar, joined as the game joins it in
  the whole Wonder, meets a block at both ends (no pane ending in open air).
- `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`: per part, a standing lantern, candle or torch stands
  on a full top; no dirt path or farmland under a solid block.
- The daily upkeep (`WonderUpkeep`, `wonderStanding` 90%): every drawn block is counted against the world once a day;
  under 90% standing its powers stop. So **everything drawn must stay as placed with nobody tending it** (no leaves
  that decay, crops, fire, snow layers, falling or flowing blocks); neighbour-shaped properties, `lit` and weathered
  copper are forgiven (`MaterialRules.matches`).
- Building it: high work gets scaffolding from the crew (35.6) and the showcase checks scaffolding went up and none was
  left; a one-cell space boxed in by panes where a block goes is handled (`LanternCageGameTests`, B308). Nothing to draw.
- The owner's rules (`check.py`, run per stage on the stages before it, upper floors reachable on the last) and
  STYLE.md's checks; `holes.py` skips a Wonder's stage parts on their own. The Colossus is not in
  `owner_rules_baseline.json`: the rebuild must stay clean.

## For the ship step (not the designer)
- A Wonder is drawn as four whole builds of one size, stage n being stage n-1 with more on it; `kit.wonder_stages`
  splits and saves the parts and `wonders.py`'s `save_wonder` rewrites `colossus.json`'s "parts" (ids and offsets) and
  keeps every other field (requirements, goods, crew 12, powers) as it is. A candidate must not overwrite the live parts.
- **Saves**: a Colossus begun or finished on a live server is counted against the *current* drawing (`WonderUpkeep.
  blocks`, `ColossusPlaque.signs`), with no `blueprint_history` fallback, so a new drawing would read an old finished
  Colossus as damaged (powers off) and an unfinished one's later stages would not fit its built ones. Shipping needs a
  fallback to the drawing a site was begun with (as `Refreshes` does for buildings), or the owner's decision.
- The wiki (`docs/wiki/wonders.md`), `docs/guide/` pages, `docs/design/M35.md`, the gallery scene's size and the
  `wonder_colossus` scene describe the old look.

## Free
Everything else: the figure's pose, proportions and dress, the base's shape and steps, the arch's form within its
5 x 6 opening, where the braziers and plaque stand, every material beyond the listed ids, interiors, the footprint
within the placement rules, and how the four stages divide the work, as long as every rule above holds after every stage.
