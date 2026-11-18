# The Starter Cottage

Milestone 49, item 49.3: every village's first home, rebuilt from scratch with the rebuild kit
(`tools/blueprints/REBUILD.md`): a blind brief (`tools/blueprints/briefs/starter_cottage.md`), two blind designs (it is
a flagship), and critics who scored old and new side by side; both designs said SHIP in round 2
(`tools/blueprints/briefs/starter_cottage_critic.md`) and the owner picked Version B, the saltbox and its cross-wing.
Roadmap items: 26.15, 37.4, 49.3

## 1. What a player sees

- **Starter Cottage** (9 x 11 x 12, in every Table's starting set, sold by a Novice Builder, a new colony's first
  build): a one-room **saltbox**. A tall timber-framed front (spruce posts, pale birch panels, a cobble plinth) under a
  short roof slope, and a long **catslide** sweeping down at the back over a low back wall. A cobble stack up the west
  gable with the campfire on top, a little gabled hood on two posts over the front door with a lantern under the eave,
  flower beds either side of the stone path, window boxes under the front windows.
  - The **hall**, under the tall front: the hearth in the west wall with a red rug and a two-seat couch before it, a
    table for two under the front east window (both chairs at it), the clock, a picture, a lamp from the ridge.
  - The **outshot**, under the catslide: the bed nook (the red bed and its nightstand) in the west, the work corner in
    the east (the Fisherman's barrel, the Carpenter's crafting table, a chest, a counter with the plates and the furnace,
    a shelf).
- **Starter Cottage II** (13 x 12 x 12): a two-story **cross-wing** across the east end, its gable to the road: stone
  below, timber above, a pent roof over the parlor window. Through a doorway from the hall, the **parlor** (a second
  table for two, a cupboard, a picture) and a stair up the east wall to the **chamber** under the wing's roof: two light
  blue beds with a nightstand between them, a cupboard, a rug, two lamps. The hall's stack goes up two courses to clear
  the wing's roof, the campfire with it.
- **Starter Cottage III** (18 x 12 x 12): a stone **kitchen** leaning on the wing's east side under its own lean-to roof,
  with its own oak door on the front and a lamp post by it, a doorway knocked through the wing's east wall: the smoker
  between two counters with the plates, a shelf, a chest, a little flue.
- **Starter Cottage IV** (18 x 12 x 12, 4 deep below ground): a **store cellar** under the hall, down a ladder through a
  hatch in the hall floor: two chests and shelves along the back, kelp and hay at the sides, pumpkins and a melon, a
  summer beam across the ceiling, a lantern.
- Beds: 1, 3, 3 and 3. Jobs at Level I: exactly a Fisherman (the barrel) and a Carpenter (the crafting table). Every
  piece is a furniture spot, so a builder with the furniture mods builds the mods' pieces in their places (37.2); with
  the building mods its modded copy dresses the walls, floors and chests (37.12).
- A cottage standing in an older drawing keeps it until its owner presses **Refresh** on the hall's Repairs page
  (36.21). With this redraw a refresh is close to a whole rebuild, and what a refresh never moves stays where the old
  drawing had it: chests, barrels, furnaces and smokers, and a bed someone sleeps in.

## 2. How it works

- Drawn in JavaScript with the rebuild kit: `tools/blueprints/js/starter_cottage_b/` (`plan.mjs`: three concepts, story,
  palette, masses, rooms, openings, every furniture footprint, the walking line; `recipe.mjs`: the passes for levels
  1-4), on the shared parts library `tools/blueprints/js/parts/`. `build.sh` builds the four levels as candidates;
  `LIVE=1 build.sh` saves the live drawings through `tools/blueprints/rebuild.py` (IV with `--basement 4 --cellar`), which
  runs check.py's checks with the owner's rules, `finish()` and writes `blueprint_furniture/<id>.json`. Version A
  (`js/starter_cottage_a/`) stays in the repo, unshipped.
- The old Python drawing (`starter.py`, `basements.py`) is no longer saved by `generate.py`; `history.py` ships the old
  drawings in `blueprint_history/starter_cottage*/` (and `modded/`) for refreshes.
- The barge trim under the verges is upside-down dark oak stairs, with a full dark oak block under each ridge end: a top
  slab beside a roof stair reads as a table with a chair facing away (`QaChairFacingGameTests`).
- `StarterBlueprints.STARTER_COTTAGE` .. `STARTER_COTTAGE_4` carry the new sizes, and so do the two loot tables that carry
  Level I's (`settlers_wagon`, `village_builders_workshop`). Modded copies: `modded_library.py starter_cottage
  starter_cottage_2 starter_cottage_3 starter_cottage_4`. Village looks: the styles (`BlueprintStyles`) are applied to the
  drawing at play time, as before; nothing is drawn per style.

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| (none of its own) | | `moddedLibrary` and the furniture switches apply as for every home. |

## 4. Saved data

Nothing new. The ids never change (`aliveworkplace:starter_cottage` .. `starter_cottage_4`); standing cottages and sites
mid-build keep their drawing until a refresh.

## 5. Items, blocks, jobs, commands

- Blueprints: Starter Cottage, Starter Cottage II, III and IV (`aliveworkplace:starter_cottage` .. `starter_cottage_4`).

## 6. Decisions

- The critic's left-to-fix list for B, done at ship: the bed nook's west shutter had taken the catslide's last verge
  stair (a sill and pot now); II's bare stone east wall has a second shuttered window with a window box (III closes it
  again, the kitchen leans there); the cellar has shelves, pumpkins, a melon and a summer beam; the notch at the gable
  peaks is a full block of trim.
- The kitchen's door is at x 13, not 14: every older Level III keeps a barrel at (14, 1, 4), and a refresh never moves a
  container (as the Stone House found).

## 7. Known limits

- The showcase scene `rebuild_starter_cottage` is filmed on GitHub, not in this session.
- A refreshed pre-49.3 cottage keeps its old chests, barrels, furnace and smoker and an occupied bed where the old
  drawing had them; some of those cells are wall or roof in the new drawing (the old Level I bed at x 7 is in the new east
  wall line, II's old upstairs bed and chest stand where the new catslide is).
- check.py's "upper floor with no stairs or ladder" line for `modded/starter_cottage_4` (and every modded Level 4) is a
  known false alarm: the modded copy's ladders are Carved Wood's.

## 8. Proof

`BuilderGameTests.buildsStarterCottage` and the upgrade tests (`builderUpgradesAFinishedCottage`,
`builderGrowsACottageSideways`), `BasementGameTests.levelFourDigsAndFinishesItsBasement`,
`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`, `starterUpgradesKeepMostOfTheirBase`,
`QaTableJobsGameTests.theStarterCottageShowsTheOwnersExample`, `DoorwayHeadroomGameTests`, `ChimneyCampfireGameTests`,
`FurnishedHomesGameTests`, `FurnitureSpotGameTests`, `QaChairFacingGameTests`, the refresh tests. Scene
`rebuild_starter_cottage`.
