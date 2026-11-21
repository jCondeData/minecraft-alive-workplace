# Royal Hall: the brief

Family id(s): `realm/royal_hall`, `realm/royal_hall_2` (never renamed; "Royal Hall", "Royal Hall II" in `en_us.json`,
`blueprint.aliveworkplace.realm.royal_hall*`). Variants: village (none: no worldgen piece), modded (none: no
`structure/modded/realm/` copy is made), looks (none drawn: the capital's style is applied by `BlueprintStyles`, a block
swap of the drawing, so every block kind should have a sensible swap; `QaRealmLegendsBuildsGameTests` checks a
`stonework` Royal Hall).

## What it is for
The great hall of a realm's capital. It isn't in the Blueprint Table: the capital's owner draws it on the capital's
Realm tab for a Blank Blueprint (`RoyalHalls.draw`), in the capital's style; with one standing the tab draws the Royal
Hall II (the upgrade, on the same origin and front). A builder puts it up like any blueprint. Finished with its origin
within 32 blocks of the capital's Village Hall (`RoyalHalls.RANGE`), it doubles the realm treasury's cap; the II
triples it (`RoyalHalls.multiplier`, by the finished build's id, any style). No job works there and the code counts no
block inside it: its use to players is a seat of state and the realm's vault (the treasury's coins are virtual, the
vault chests are where a ruler keeps the realm's real goods). On the village map it is a hall (`VillageMaps`, by id).
Advancements `built/realm/royal_hall*` go by id.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `realm/royal_hall` | the great hall with a seat of state, and the realm's vault (a room of chests) reached from the hall | a Blank Blueprint at the capital's Realm tab (free in creative); counts x2 |
| II | `realm/royal_hall_2` | grows out of I on the same origin: more room for the realm (a tower or wing of rooms) and a larger treasury (more chests), with a second way in on the east side | drawn only once a Royal Hall stands; counts x3 |

## Must have (each level)
- Job block(s): none.
- Beds and residents: none required (no code counts beds here).
- Storage: **at least four chests** in each level, in every style (`RealmBuildsScene` fails a hall with fewer "vault
  chests"). Builders never copy contents. II should hold more than I (the treasury grows).
- A **vault**: a room of the chests, entered from the hall by a **door** (a real door block) next to the **seat of
  state**, which a villager can walk through both ways (see the tests below).
- The **seat of state** on a raised platform (a dais) inside the hall.
- Furniture kinds a job or test needs: none counted; the rooms are filmed furnished by `SCENE=rebuild_royal_hall`
  (furniture spots from `blueprint_furniture/realm/royal_hall.json`).
- Level II: an **east door** from outside into the building that a villager can walk in and out of.

## Fits
- Plot: Level I at most 19 x 26 x 25 (x, height, z); Level II at most 27 x 26 x 25. The walk-in tests place both in
  a 30 x 30 x 30 test area at origin (1, y, 2) and turned 180 degrees at (23, y, 27), so neither may grow past 27 wide
  or 25 deep; the height must stay under 27. y = 0 sits on the clicked block; the front is the z = 0 side.
- Front and way in: the main door on the front (z = 0) side, with a way up to it from the ground. Level II adds a door
  on the east (high x) side.
- Neighbours: it stands near the capital's Village Hall (within 32 blocks); no party walls, no village-plan placement.

## Rules tests check
- `QaDoorwayBugGameTests.aVillagerWalksOutOfTheRoyalHallsVaultOntoTheDais` and
  `QaBuilderFixes1027bGameTests.aVillagerWalksIntoTheTurnedRoyalHallsVaultFromTheDais`: a nitwit walks from a cell
  inside the vault, through the vault's door (the door must be at the given cell), to a cell on the dais, and the
  other way with the building turned 180 degrees. The three cells (vault floor, door, dais) are pinned in the tests
  and move with the new drawing; the walk must work with the door shut.
- `QaDoorwayBugGameTests.aVillagerWalksInByRoyalHallIIsEastDoor` and
  `QaBuilderFixes1027bGameTests.aVillagerWalksOutByRoyalHallIIsEastDoor`: from a cell outside the II's east side,
  through its east door (pinned), to a cell inside, and back out.
- `QaRealmLegendsBuildsGameTests` (cap, stacking, unfinished, map kind) and `RealmBuildsScene` (placed as drawn and in
  the five styles; at least four chests; a hall on the map) read only the ids, the size and the chest count.
- The owner's rules (`check.py`) and STYLE.md's checks; `holes.py` must pass.

## Free
Everything else: the form, the materials, the roof, the rooms beyond the hall, dais and vault, how II grows from I.
