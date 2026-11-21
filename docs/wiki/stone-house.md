# The Stone House

Milestone 49, item 49.4: the village's second kind of home, rebuilt from scratch with the rebuild kit
(`tools/blueprints/REBUILD.md`): a blind brief (`tools/blueprints/briefs/stone_house.md`), a blind design, and a critic
who scored old and new side by side and said SHIP in round 2 (`tools/blueprints/briefs/stone_house_critic.md`).
Roadmap items: 26.15, 49.4, 49.10b, B1431

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
- **In its village's culture** (49.10b): a village's Stone House is built in its village's climate, every level.
  Plains villages build the rubble house above; desert, savanna and taiga villages build their own look of the same
  house (the same rooms, beds, job blocks, chests and furniture places, so it works the same):
  - **Desert:** a cube of coursed sandstone under a flat roof behind a coped parapet, a terracotta frieze, a dark
    lattice oriel over the door and a low slotted windcatcher; II adds the parlor as a lower mud-brick block fronted by
    an arcade of two round arches (reached through a doorway from the hall, with the old window's glass kept as a light
    over it), III the back room as a mud-brick block whose roof is a terrace under a pergola.
  - **Savanna:** a ground story of red granite and an upper story of packed mud between gray acacia posts, under one
    square pyramid of golden thatch with a clay pot at its point; II the parlor under a low hip that runs on over a
    veranda, III the back room under a second low hip.
  - **Taiga:** a forester's lodge gable-on to the road, mossy fieldstone below and dark spruce logs above under a broad
    slate gable, a porch on log posts and firewood under the eaves; II carries the roof down over the parlor, III builds
    a log pen on behind.
  Snowy villages build the rubble house too: the snowy look went back to the critic three times and waits for the
  owner's call. The Blueprint Table lists each look beside the house; a look is locked and unlocked with its level.
  Where a culture's look doesn't fit the plot, the Steward tries the other looks, then the house as drawn.
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
- The culture looks (49.10b) are drawn in `tools/blueprints/js/stone_house_<culture>/` from the brief and the house's
  plan (its furniture spots cell for cell; `look_spots.py` says SAME BUILDING at every level); `SHIP=1 build.sh` saves
  them as `structure/looks/<culture>/stone_house[_n]` (`rebuild.py --look`). Critics passed savanna and taiga in round
  1 and desert in round 3; snowy was sent back three times and stays a candidate
  (`tools/blueprints/briefs/stone_house_looks_critic.md`). `BlueprintLooks` lists the Stone House's three looks, so it
  is built by `VillageWaystones.culture` like the Starter Cottage (a culture with no look, plains or snowy, as drawn);
  `StarterBlueprints.STONE_HOUSE_DESERT`, `_SAVANNA` and `_TAIGA` list each look's levels, Level IV with the cellar.
  Scene: `rebuild_stone_house_looks`.

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

- The showcase scenes `rebuild_stone_house` and `rebuild_stone_house_looks` are filmed on GitHub, not in this session.
- check.py's whole-repo run flags the pots round the Level IV looks' walls as "standing on air": it reads the file without its
  4-deep basement, so the earth under them looks empty; `rebuild.py --basement 4` checks them with it and passes (as for
  the Starter Cottage's Level IV looks).
- check.py's "upper floor with no stairs or ladder" line for `modded/stone_house_4` (and every modded Level 4) is a known
  false alarm: the modded copy's ladders are Carved Wood's.

## 8. Proof

`AtticBedGameTests` (the barrel at 8, 1, 8, a villager in the chamber at 5, 6, 8 sleeps in the upper bed),
`DoorJamGameTests` (door 5, 1, 1, doorstep 5, 0, 0), `FurnishedRoomsGameTests.theStoneHouseReadsAsAHome`,
`BuilderGameTests.buildsStoneHouse`, `buildsStoneHouseIII`, `buildsAStyledStoneHouse`,
`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`, `starterUpgradesKeepMostOfTheirBase`, the basement tests,
`RefreshGameTests` and `RefreshMatchesFreshBuildGameTests`, `QaChairFacingGameTests`, `StoneHouseLooksGameTests` (49.10b).
Scenes `rebuild_stone_house` and `rebuild_stone_house_looks`.
