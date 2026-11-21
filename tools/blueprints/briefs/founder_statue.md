# Founder's Statue: the brief

Family id(s): `legend/founder_statue` (never renamed; one drawing, no `_2`)   Variants: village (none: the id is never
styled, and `legend/` builds are kept off the village map by `hall/VillageMaps.kindOf`), modded (none), looks (none)

Not in this family: the Stag Statue (a player's decoration), the Colossus (the Founder's 47-high Wonder, 35.21) and the
Mega Shrine. They are other builds; nothing here changes them.

## What it is for
A monument to the village's Founder, a Mythic Legend (ROADMAP 29.23, `legend/Founder`). Nobody places it by hand and no
player can pick it from the Blueprint Table. It goes up in two ways:

1. **Built by the village.** Once a Founder has settled in a village (and isn't on strike), the hall's round
   (`Founder.round` → `Founder.statue`) hands the statue to the village's least busy builder. The spot is open ground
   near the Village Hall, and the builder takes the materials from the village's chests. Once it is finished and while
   it stands, the village gains **5 beauty** (`Founder.beauty`, read by `hall/Decorations.beauty`) and every villager
   of the village is **+5 mood** with the reason "the Founder's statue" (`Founder.statueMood`, read by
   `people/Moods`). Break the figure's head and both stop. A village gets only one: no second one is queued while one
   is under way or has been finished.
2. **Set down whole beside a blessed colony's camp** (33.23, `colony/ColonyArrival.raiseStatue`). When settlers sent
   with the Founder's blessing arrive, the statue is placed instantly from the template (not built) beside their camp,
   facing the way the camp faces. It adds no beauty there. It shows that the colony was founded with the Founder's
   blessing.

The chronicle line is "%s asked for a statue by the hall, and %s began it". The game calls it "the Founder's statue",
so a player must read it as **a statue of a person, the Founder**. The last critic scored the old one REBUILD because
its figure read as a grave cross, not a person (`briefs/triage_52_5/critic.md`). Nobody lives or works in it, and it
hands out no job.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `legend/founder_statue` | While it stands, 5 beauty and +5 mood for the village (from `legends/founder.json`'s `statue` power: `beauty` 5, `mood` 5) | None. Started by `Founder.statue` for a settled Founder with the `statue` power, or placed by `ColonyArrival.raiseStatue` for a blessed colony. It is not in `StarterBlueprints`, so it is not on the Table, in the creative tab or in the market-day pool, and there is no upgrade file, `built/` advancement or loot table for it. |

One level only. Never add a `legend/founder_statue_2`: no code reads one.

## Must have
- **The head on the middle column, in the top three layers.** `Founder.stands` decides whether a finished statue
  counts. It takes the build's bounding box in the world and its middle column (`BoundingBox.getCenter()` x and z), and
  looks at the top three layers of the template (`maxY`, `maxY - 1`, `maxY - 2`). If any of those three cells is not
  air, the statue stands. So:
  - The figure's head (or the top of the figure) must fill the middle column within the template's top three layers.
    The template's top layer is its declared height. A height with nothing drawn at the top fails.
  - Nothing else may be the only thing in those cells. Breaking the figure's head must be what stops the beauty, and
    `FounderGameTests` knocks exactly those three cells out and expects 0 beauty and no mood.
  - Make the width and the depth **both odd**. With an even side the middle column moves with the rotation, and
    `Founder.spot` turns the statue to face the hall, so it can face any of the four directions.
- Job block(s): **none.** Draw no villager point-of-interest block (no job block, bed, bell, barrel, lectern, composter,
  cauldron...). Villagers would claim it from a monument, and no code wants one here.
- Beds and residents: none. No code counts beds here.
- Storage: **none.** No job reads a container here. Builders never break containers, and the colony's camp code looks
  for the first chest in the camp box, not here. A chest would only be left behind.
- Furniture kinds a job or test needs: none. There is no `blueprint_furniture/legend/founder_statue.json` and none is
  needed.
- Anything else the code or a test reads:
  - **A marker block for the colony tests.** These look for the statue by searching for one block kind only the statue
    uses. The marker is pinned in the tests today (see "Rules tests check").
  - Nothing else: no water, bell, sign, lectern, door or light level is read for this family.
- Every block must be one a survival builder can place, get from a chest and keep in place:
  - No block `MaterialRules.classify` calls SKIP.
  - No creative-only or unobtainable block.
  - Nothing that changes by itself once placed: no dirt beside grass, no growing plants, no concrete powder near water,
    no ice.
  - Every standing or hanging block rests on something drawn.
  - Every block can be reached by a builder.

## Fits
- **Plot:** at most **9 (x) by 9 (z)**, both sides odd (see above).
- **Height:** free, as long as the head is in the top three layers.
- **The old box is 7 x 8 x 7. A 7 x 7 footprint is the safe choice for live saves.**
  - `BuildSiteManager.Finished` keeps no size, so for every statue already finished on the owner's live server,
    `Founder.stands` measures the old placement with the **new** drawing's size.
  - With a 7 x 7 footprint and a height from 4 to 10, the old statues' middle column and head still fall in the cells
    it checks, and they keep counting.
  - Any other footprint or height means the ship step must change `Founder.stands` so statues finished from the old
    drawing keep their 5 beauty and +5 mood.
- **The bigger it is, the harder it is to place.**
  - `Founder.spot` searches rings 5 to 24 blocks from the hall for a footprint plus a 1-block border that is level within
    1 block, dry, not on leaves, and touching no other site, finished building or the hall.
  - `ColonyArrival.beside` / `near` need even, dry, loaded ground beside the camp, past the starter cottage and the
    storehouse.
  - A smaller footprint finds a spot in a crowded City more often.
- **Placement:** y = 0 sits on the ground block (the first open cell above solid ground). The front is the z = 0 side.
- **Front and way in:**
  - The **front faces the Village Hall.** `Founder.spot` places the front's middle on the ring and turns it with
    `rotationFacing(toward the hall)`, so the side villagers and players see from the hall is z = 0.
  - Beside a colony camp, the front faces the camp's way, in line with the camp's front.
  - No door. When the builder finishes, his path back starts one block in front of the middle of the z = 0 edge
    (`build/Paths.doorstep`'s fallback when there is no door), so that spot should be ground a villager can step onto.
- **Neighbours:**
  - Free-standing, with a 1-block clear border that `Founder.spot` keeps.
  - Beside a colony it stands 2, 4 or 7 blocks (`ColonyArrival.GAPS`) past the camp's side or the starter builds.
  - No village plan or Steward rule places anything against it.

## Rules tests check
- **`FounderGameTests.theStatueIsQueuedBuiltAndWorthBeautyAndMood`** (batch `founderStatue`, `huge_area`, hall at helper
  (15, 2, 15), `VillageHalls.RADIUS` 14, build delay 2, 24,000 ticks):
  - The hall's round queues the statue for the builder, and a second call queues nothing.
  - The spot's box, grown by 1, doesn't hold the hall, and its `minY` is the floor + 1. The footprint must find a spot on
    the 5-block ring round the hall inside the 30 x 30 area. A builder's bench at (24, 2, 24) and villagers at (17, 2, 17)
    and (13, 2, 17) must not end up inside it, so keep it well within 9 x 9.
  - Built from the plan's materials, which are put in **3 barrels of 27 slots, so the Material List must fit 81 stacks**.
    Stacks past 81 are never stocked and the build never finishes.
  - Once finished, it is recorded as finished, `Founder.stands` is true, beauty is exactly 5, village beauty is at least
    5, the neighbour's mood is 5 with the reason "the Founder's statue", and no second statue is queued.
  - Then the three middle-column cells at `maxY - 2`..`maxY` are set to air, and beauty must be 0 and mood null.
- **Showcase `legend_founder`** (`JobScenes`, nightly showcase):
  - Same set-up, with the materials in as many barrels as needed and build delay 2.
  - The builder has **6,000 ticks** to finish, then `stands` and beauty 5 are checked.
  - The old one placed 113 blocks and passes every night. Keep the block count modest (about that), or the ship step
    raises the scene's budget.
  - The scene text in `JobScenes` and `tools/showcase/scenes.py` describes the old looks, so the ship step rewrites it.
- **`RealmLegendsGameTests.aBlessedColonyHasItsStatueAndItsChronicleLines`** and
  **`aColonyWithoutTheFounderHasNoStatue`** (`colony_area`, 200 ticks):
  - They find the statue by the first `minecraft:waxed_cut_copper` block within the area ±24 blocks (up to 4 above
    it). Blessed: it must be within 40 blocks (Manhattan) of the colony's hall. Unblessed: none may exist anywhere there.
  - The marker must be a block kind that only the statue has in that scene: not in `camp/colony_camp`, the Village Hall
    or the starter builds.
  - The designer is free to drop that block. The ship step then points the marker in this test and in
    `devclient/ColonyScene` (the `founder_colony` showcase scene) at a block only the new drawing has.
- **Clean-up lists:** `RealmLegendsGameTests.statueBlock` and `QaColonyWordsVerify1103GameTests.statueBlock` remove the
  statue's blocks placed outside the test area by name (the old drawing's block kinds). The ship step updates both lists
  to the new drawing's block kinds, or the statue's blocks are left in the world for later batches.
- **Every shipped structure is checked:**
  - `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`: a standing lantern, candle or torch needs a top
    that can hold it, and no dirt path or farmland may sit under a solid block.
  - `B648GameTests.b648NoShippedGlassEndsInTheAir` and `QaWindowsAndWards1028BugTests`: no pane or iron bars joined on
    one side and open to the air.
  - `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`: every countable material named in the Material
    List needs its plural in the lang file.
- **The owner's rules and STYLE.md's checks:** `check.py` (corner posts on runs, no lantern standing on a floor, no
  crockery outside a kitchen or a laid table), `holes.py` and STYLE.md's checks. `rebuild.py --candidate` prints no
  CHECK line.
- **Ship-step bookkeeping (not the designer's):**
  - Add `founder_statue` to `generate.py`'s `REBUILT` so `legend_sites.py` stops overwriting the new `.nbt`.
  - Update the wiki's mention in `docs/wiki/legends.md` (33.23).
  - Add `rebuild_founder_statue` to `RebuildScene.FILMED` and `scenes.py` if it is filmed. There are no rooms, so the
    four sides only.

## Free
Everything not listed above is free: the Founder's pose, dress and what he holds, what he stands on, everything around
him (paving, planting, seats, lights, an inscription), the materials and the style. The limits: the footprint stays
within the plot, the head is on the middle column in the top three layers, the figure faces z = 0, and a player reads
it as a statue of a person.
