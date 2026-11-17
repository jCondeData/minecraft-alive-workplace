# The Stone House

Milestone 49, item 49.4: the village's second kind of home, rebuilt from scratch with the rebuild kit
(`tools/blueprints/REBUILD.md`): a blind brief (`tools/blueprints/briefs/stone_house.md`), a blind design, and a critic
who scored old and new side by side and said SHIP in round 2 (`tools/blueprints/briefs/stone_house_critic.md`).
Roadmap items: 26.15, 49.4

## 1. What a player sees

- **Stone House** (11 x 16 x 11, in every Table's starting set): the mason's house. A square two-story house of rubble
  stone (cobble, stone and andesite, moss only where water lies: the plinth, the corners' feet, under the eaves) with
  dressed stone-brick quoins up every corner, a stone-brick floor band all round at the upper floor, a dark slate roof
  with dark oak trim under the gable ends, and a fat stack on the west gable with a stepped breast. A slate hood and a
  lantern over the front door, flower boxes under the front windows, two flower beds by the front wall.
  - Downstairs, the **hall**: the hearth in the west wall with an armchair either side and a red rug before it, a table
    for four by the front window, two hanging lamps. The **box room** behind
    the partition: the downstairs bed, a lamp on its side table, a picture. The **kitchen**, open to the hall: the
    furnace, a counter with the plates, the barrel with a shelf of crockery over it.
  - A straight stair up the east wall to the **chamber** under the slates: the bed to the back wall with its nightstand
    and the chest at its foot, a desk and chair in the front window's light, a cupboard, the clock in the front corner, one lantern from the
    ridge.
- **Stone House II** (13 x 16 x 11): a lower **parlor wing** with a lean-to roof against the east gable, through the
  hall's old east window: a settle under its window, a bed for a grandparent, its nightstand and a lamp.
- **Stone House III** (13 x 16 x 14): the back roof carried on down as a **catslide** over a back room behind the house,
  through the kitchen's old back window: two beds for the children, nightstands, a rug, a lamp.
- **Stone House IV** (13 x 16 x 14, 4 deep below ground): a **store cellar** under the hall, down a ladder by the hearth:
  casks two high along the east wall, shelves on the west, a workbench with a lamp on its side table and a chest at the
  back, a sacking runner from the ladder's foot, and a summer beam across the middle on one post, carrying the hall's floor.
- Beds: 2, 3, 5 and 5. Every piece is a furniture spot, so a builder with the furniture mods builds the mods' pieces in
  their places (37.2); with the building mods its modded copy dresses the floors and chests (37.12).
- A house standing in an older drawing keeps it until its owner presses **Refresh** on the hall's Repairs page (36.21);
  with this redraw a refresh is close to a whole rebuild.

## 2. How it works

- Drawn in JavaScript with the rebuild kit: `tools/blueprints/js/stone_house/` (`plan.mjs`: three concepts, story,
  palette, masses, rooms, openings, every furniture footprint, the walking line; `recipe.mjs`: the passes for levels
  1-4), on the shared parts library `tools/blueprints/js/parts/`. `build.sh` builds the four levels as candidates; the
  live drawings are saved with `tools/blueprints/rebuild.py stone_house <id>` (IV with `--basement 4 --cellar`), which
  runs check.py's checks with the owner's rules, `finish()` and writes `blueprint_furniture/<id>.json`.
- The old Python drawing (`houses.py`, `basements.py`) is no longer saved by `generate.py`; `history.py` ships the old
  drawings in `blueprint_history/stone_house*/` for refreshes.
- The gable trim under the 45-degree rakes is upside-down dark oak stairs, not slabs, so no slab beside a roof stair
  reads as a table with a chair facing away (`QaChairFacingGameTests`).
- `StarterBlueprints.STONE_HOUSE` .. `STONE_HOUSE_4` carry the new sizes. Modded copies: `modded_library.py stone_house
  stone_house_2 stone_house_3 stone_house_4`. Village looks: the styles (`BlueprintStyles`) are applied to the drawing at
  play time, as before; nothing is drawn per style.

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| (none of its own) | | `moddedLibrary` and the furniture switches apply as for every home. |

## 4. Saved data

Nothing new. The ids never change (`aliveworkplace:stone_house` .. `stone_house_4`); standing houses and sites mid-build
keep their drawing until a refresh.

## 5. Items, blocks, jobs, commands

- Blueprints: Stone House, Stone House II, III and IV (`aliveworkplace:stone_house` .. `stone_house_4`).

## 6. Decisions

- The critic's optional notes: the cellar's middle got the summer beam and post (cheap, and the store now has a center);
  a dormer or step in III-IV's long rear roof was left for a later pass (the roof parts' dormer runs only along z).
- Time limits of the GameTests whose builder now does nearly a whole house (the refresh tests from an older drawing,
  12,000 to 20,000 ticks; the crew-scaling test's nine builds, 36,000 to 60,000) were raised for the bigger house; what
  they check is unchanged.

## 7. Known limits

- The showcase scene `rebuild_stone_house` is filmed on GitHub, not in this session.
- check.py's "upper floor with no stairs or ladder" line for `modded/stone_house_4` (and every modded Level 4) is a known
  false alarm: the modded copy's ladders are Carved Wood's.

## 8. Proof

`AtticBedGameTests` (the barrel at 8, 1, 8, a villager in the chamber at 5, 6, 8 sleeps in the upper bed),
`DoorJamGameTests` (door 5, 1, 1, doorstep 5, 0, 0), `FurnishedRoomsGameTests.theStoneHouseReadsAsAHome`,
`BuilderGameTests.buildsStoneHouse`, `buildsStoneHouseIII`, `buildsAStyledStoneHouse`,
`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`, `starterUpgradesKeepMostOfTheirBase`, the basement tests,
`RefreshGameTests` and `RefreshMatchesFreshBuildGameTests`, `QaChairFacingGameTests`. Scene `rebuild_stone_house`.
