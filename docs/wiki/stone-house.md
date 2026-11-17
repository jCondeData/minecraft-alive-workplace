# The Stone House

Milestone 49, item 49.4 (the owner, 2026-11-16: "rebuild all the current builds with this skillset and logic"). The
village's second kind of home, designed again from nothing with the rebuild kit (`tools/blueprints/REBUILD.md`): a blind
brief, a blind design ("the Mason's House"), and a critic who compared it with the old one and said SHIP. The ids, the
bed counts and what each level adds stay as they were, so the Steward, the Hall, the questbook and old saves read it the
same way.
Roadmap items: 49.4

## 1. What a player sees

- **Stone House** (11 x 16 x 11, in every Blueprint Table's starting set and in village chest loot): a square
  two-storey house of rubble stone (cobble, stone and andesite, moss only where water lies: the plinth, the feet of the
  corners and under the eaves) with dressed stone corners, a ledge of stone slabs round it at the upper floor, and a
  45-degree slate roof (deepslate tiles) with dark boards under the gable edges. A fat stone chimney stack climbs the
  west gable past the ridge, smoking. The front door sits under a little slate hood with a lantern, two windows either
  side with flower pots on stone sills, flower beds at the corners.
  - Downstairs, the **hall**: a hearth in the west wall with an armchair either side and a rug before it, a table for
    four lit by two hanging lamps, blinds over the front windows. The way from the
    door to the stair stays clear.
  - Behind a plank partition and its door, the **box room**: the downstairs bed, a lamp on a side table by its head, a
    picture.
  - The **kitchen**, open to the hall: a furnace (the stove), a counter with crockery, a barrel with a shelf over it.
  - Up a straight stair along the east wall (a railing at its head), the **bedroom under the slates**: one bed, its
    nightstand, a chest at its foot, a desk and chair in the front window's light, a cupboard, a tall clock against
    the back wall, a rug, a lantern hung from the ridge.
- **Stone House II** (13 x 16 x 11): a lean-to **parlor wing** on the east gable, reached from the hall through the old
  east window: a settle under its window, a bed for a grandparent with its nightstand, a hanging lamp. Three beds.
- **Stone House III** (13 x 16 x 14): the back slope of the roof carried on down (a catslide) over a **back room**
  through the kitchen: two beds heads to the back wall, nightstands, a rug, a lamp. Five beds.
- **Stone House IV** (13 x 16 x 14, a store 4 deep under it): a ladder down by the hearth through a trapdoor to a
  **store** under the whole house: barrels two high along the east wall, shelves on the west, a workbench with a lamp
  and a chest at the back, a sacking runner from the ladder, a spruce post in the middle, two lanterns hung from the
  hall's floor. Five beds (no more than III).
- Every piece is a furniture spot: a builder with the furniture mods builds their pieces in its place (37.2); with the
  building mods the modded copy dresses the floors and chests (37.12).
- **Houses already standing** keep the drawing they were built from. The Village Hall's Repairs page offers each a
  **Refresh** to the new look (36.21); its beds, chests and job blocks stay where they are.

## 2. How it works

- Drawn in JavaScript with the rebuild kit: `tools/blueprints/js/stone_house/` (`plan.mjs`: the three concepts, story,
  palette, masses, rooms, openings and every furniture footprint; `recipe.mjs`: the passes for LEVEL 1-4) on the shared
  parts library `tools/blueprints/js/parts/`. `build.sh` builds the four levels as candidates; the live drawings are
  saved by `python3 tools/blueprints/rebuild.py stone_house <id> --nbt out/<id>.nbt` (IV with `--basement 4 --cellar
  2,2,8,8`), which runs check.py's checks with the owner's rules and writes `blueprint_furniture/<id>.json`.
- `generate.py` no longer draws the Stone House (its old Python drawing in `houses.py` stays only as history).
- `StarterBlueprints.STONE_HOUSE` to `STONE_HOUSE_4` carry the new sizes; ids and upgrades (`BlueprintUpgrades`: id +
  `_2`, `_3`, `_4`) are unchanged.
- Modded copies: `structure/modded/stone_house*.nbt` (`modded_library.py stone_house stone_house_2 stone_house_3
  stone_house_4`). No village pieces: a village's style is applied to the drawing by `BlueprintStyles`.
- The old drawings ship in `blueprint_history/stone_house*/` and `blueprint_history/modded/stone_house*/`
  (`history.py`), so a standing house and a site mid-build keep theirs and are offered the refresh.

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| (none of its own) | | `moddedLibrary` and the furniture switches apply as for every home. |

## 4. Saved data

Nothing new. The ids are unchanged; houses built from the old drawings keep them (the old drawings ship in
`blueprint_history`).

## 5. Items, blocks, jobs, commands

- Blueprints: Stone House, Stone House II, III and IV (`aliveworkplace:stone_house`, `_2`, `_3`, `_4`).
- Job block: the kitchen's barrel (a Fisherman's workstation for a jobless villager, as before).

## 6. Decisions

- The design brief, the blind design and the critic's scorecards are in `tools/blueprints/briefs/stone_house.md` and
  `stone_house_critic.md` (with the old-vs-new sheet beside them).
- The gable boards are thin trapdoors, not top slabs: a top slab beside a roof stair reads to the chair checks as a
  table with a chair turned away.
- Three small moves at ship, after the critic: the box room's door is at x 4, not 3 (every older drawing has a chest at
  (3, 1, 6), which a refresh keeps, so a refreshed house's box room kept no way in); the lamp's side table went beside
  the bed's head, out of the door's way; and the clock went up to the bedroom, since in IV the hatch up from the store
  opens at (2, 1, 5) and needs (3, 1, 5) clear.
- The critic's optional note "something in the cellar's middle" became the spruce post; the other one (a dormer or step
  in III-IV's long back roof) is left for later.

## 7. Known limits

- The showcase scene `rebuild_stone_house` is filmed on GitHub, not in this session.
- A refresh of an old Stone House is close to a whole rebuild (the plan changed), so it takes a builder longer than a
  refresh of a lightly changed build.

## 8. Proof

Every test that pinned the old drawing's cells was moved to the new ones, keeping what it checks:
`AtticBedGameTests` (the barrel now in the kitchen, 8,1,8; the villager on the bedroom floor), `DoorJamGameTests` (the
door and step are where they were, 5,1,1 and 5,0,0), `FurnishedRoomsGameTests.theStoneHouseReadsAsAHome` (the bedroom at
y 6: one bed, one nightstand, one chest, one hung lantern). The builds whole (`BuilderGameTests.buildsStoneHouse`,
`buildsStoneHouseIII`, `buildsAStyledStoneHouse`), the basement tests (IV), the refresh tests from every older drawing,
the furniture spot, floating-furnishing and chair-facing checks all run on the new drawings. Scene
`rebuild_stone_house`; `furnished_home`'s Stone House stills.
