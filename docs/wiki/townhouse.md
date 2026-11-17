# The Burgher's Townhouse

Milestone 49, item 49.5: the grade II home for Burghers (34.15), rebuilt from scratch with the rebuild kit
(`tools/blueprints/REBUILD.md`): a blind brief (`tools/blueprints/briefs/townhouse.md`), a blind design, and a critic
who scored old and new side by side and said SHIP (`tools/blueprints/briefs/townhouse_critic.md`).
Roadmap items: 34.15, 26.15, 49.5

## 1. What a player sees

- **Burgher's Townhouse** (11 x 22 x 14, grade II; builders sell it once the village has Artisans): a tall merchant's
  house standing gable-end to the street. A stone-brick ground storey on a cobble plinth with dressed andesite quoins;
  over it a timber-framed floor of white plaster between dark oak posts, jettied a block out over the street on dark
  brackets, with lanterns hung under the jetty either side of the door; a steep red roof of polished granite with barge
  boards on both gables, two dormers down the west slope, a hoist beam with a chain and lantern at the front gable's
  peak, and a stepped chimney stack up the back gable. Flower boxes under the shop windows, flower beds by the step.
  - Downstairs, the **shop**: the counting table and two chairs by the front window, a chest of stock, barrels of goods
    by the stair. Behind it the **kitchen**: the hearth set into the back wall at the foot of the chimney stack (a
    two-high fireplace opening; the stack is its flue), the furnace, counters with plates, a shelf of crockery, a table
    for four.
  - Up the stair, the **hall** over the street: a settle with a rug and a tea table, the clock between the front
    windows, a picture, blinds at every front window. Behind a plank partition, the **chamber**: the parents' bed to the
    back wall, its nightstand and a chest.
  - Up a second stair, the **attic** under the steep tiles: the children's two beds heads to the front gable, a chest
    between them, and the store at the back (barrels, a cupboard).
- **Townhouse II** (18 x 22 x 14, grade III): a **counting-house wing** on the east with its own small street gable and
  door under a hood: the clerks' table for four, ledger cupboards, a chest and a clock below; two beds above under the
  open roof, through the chamber's old east window.
- **Townhouse III** (19 x 26 x 19, grade III): a square stone **strongroom tower** behind the wing, under a steep
  pyramid roof with a copper finial, a ledge at every floor: the strongroom (a chest and barrels), a library, a bedroom
  and the lookout study at the top, up a ladder.
- **Townhouse IV** (19 x 26 x 19, 4 deep below ground): a **vault** under the shop, down a ladder from the shop floor:
  casks two high along the east wall, shelves, a chest and a workbench with its lamp, two stone ribs across it with
  lanterns hung from them.
- Beds: 3, 5, 6 and 6. Every piece is a furniture spot, so a builder with the furniture mods builds the mods' pieces in
  their places (37.2); with the building mods its modded copy dresses the floors and chests (37.12).
- A house standing in an older drawing keeps it until its owner presses **Refresh** on the hall's Repairs page (36.21);
  with this redraw a refresh is close to a whole rebuild.

## 2. How it works

- Drawn in JavaScript with the rebuild kit: `tools/blueprints/js/townhouse/` (`plan.mjs`: three concepts, story,
  palette, masses, rooms, openings, every furniture footprint, the walking line; `recipe.mjs`: the passes for levels
  1-4), on the shared parts library `tools/blueprints/js/parts/`. `build.sh` builds the four levels as candidates; the
  live drawings are saved with `tools/blueprints/rebuild.py townhouse <id>` (IV with `--basement 4 --cellar`), which
  runs check.py's checks with the owner's rules, `finish()` and writes `blueprint_furniture/<id>.json`.
- The old Python drawing (`houses.py`, `basements.py`) is no longer saved by `generate.py`; `history.py` ships the old
  drawings in `blueprint_history/townhouse*/` (and `modded/townhouse*/`) for refreshes.
- `StarterBlueprints.TOWNHOUSE` .. `TOWNHOUSE_4` carry the new sizes. Modded copies: `modded_library.py townhouse
  townhouse_2 townhouse_3 townhouse_4`. Village looks: the styles (`BlueprintStyles`) are applied to the drawing at play
  time, as before; nothing is drawn per style. The chimney (a campfire on the stack's solid column) stays whole in
  every style.

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| (none of its own) | | `villageClasses`, `moddedLibrary` and the furniture switches apply as for every grander home. |

## 4. Saved data

Nothing new. The ids never change (`aliveworkplace:townhouse` .. `townhouse_4`); standing houses and sites mid-build
keep their drawing until a refresh. Its grade still comes from `homes/townhouse.json`.

## 5. Items, blocks, jobs, commands

- Blueprints: Burgher's Townhouse, Townhouse II, III and IV (`aliveworkplace:townhouse` .. `townhouse_4`).

## 6. Decisions

- The critic's first note: the kitchen fire stood open under the chamber's floor, apart from the stack. It is now set
  into the back wall in front of the chimney breast, a two-high opening under a stone lintel, so hearth and chimney are
  one thing.
- The critic's second note (the right roof slope of I and II is one plane) is left for a later pass: from II the
  counting-house wing's roof meets that slope, so a dormer there would not survive the upgrade.
- The attic stair's well is open over its foot step too (the head clears the attic floor on the first step up:
  `QaBuildReachGameTests`).
- The Townhouse's refresh tests run in the tall area (30 tall): the huge area is 20 under its barrier ceiling.
- Two hanging lamps hang a block lower than first drawn (the attic's front one, the wing bedroom's) so the nightstands
  under the slopes get light above 7 (check.py's dark-room rule).

## 7. Known limits

- The showcase scene `rebuild_townhouse` is filmed on GitHub, not in this session.
- check.py's "upper floor with no stairs or ladder" lines for `modded/townhouse_3` and `_4` are the known false alarm
  for modded copies (their ladders are Carved Wood's). Its two crockery lines for `modded/townhouse_4` are a false
  alarm too: check.py's loader reads the furniture spots of a drawing with a basement 4 blocks off, so the chairs at
  the shop and hall tables are not seen.

- Refresh from an older drawing leaves the upper half of some doors as air (the partition door, the wing door; cause
  not yet found); the Stone House's refresh tests show the same. Its four Townhouse refresh
  tests fail on that door alone until the refresh is fixed.

## 8. Proof

`HomeGradeGameTests` (a bed in the chamber, 5, 6, 10, counts grade II, III in its upgrades), `QaBuildReachGameTests`, `QaGranderHomesGameTests`,
`QaGranderHomes1107GameTests.everyBedOfTheTownhouseGivesItsGrade`, `QaGranderHomes1107bGameTests`,
`BuilderGameTests.buildsTownhouse`, `buildsTownhouseII`, `buildsTownhouseIII`,
`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`, `StyleGameTests.chimneysStayWholeInEveryStyle`,
`QaWindowsAndWards1028GameTests.qaABuiltTownhousesGlassJoinsAfterAReloadMidStructure`, `FurnishedHomesGameTests`,
`B648GameTests`, `QaRefreshFurnishedGameTests`, the basement tests. Scene `rebuild_townhouse`.
