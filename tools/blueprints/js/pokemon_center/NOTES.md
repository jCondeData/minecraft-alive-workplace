# Pokémon Center rebuild: design notes (ROADMAP 49, blind design)

Brief: `tools/blueprints/briefs/pokemon_center.md`. Drawn without seeing the old family. Three folders, one per look,
each `plan.mjs` (story, palette, masses, rooms, openings, furniture, walking line) then `recipe.mjs` (passes), built by
`build.sh` into `build/rebuild/<folder>/` as candidates:

| Look | Folder | I | II |
|---|---|---|---|
| default | `js/pokemon_center/` | The Red Dome, 13 x 16 x 13 | + Trade Hall wing, garden, domed pavilion, 20 x 16 x 26 |
| lodge | `js/pokemon_center_lodge/` | The Mountain Lodge, 13 x 15 x 13 | + Trader's log wing, paddock, 21 x 15 x 26 |
| plaza | `js/pokemon_center_plaza/` | The Sunny Plaza, 13 x 11 x 13 | + paved court, fountain, market hall, 20 x 11 x 26 |

New shared part: `js/parts/pokemon_clinic.mjs` (healingMachine, pcTower, pastureBlock, shopCounter with their states;
deskRun, a run of counter spots; ringDome, a dome stepped in rings with a pale vault inside and a red skin outside).

## Three rough concepts (the default look)

- **A. The Red Dome.** Massing: one round hero, an octagonal white drum (x 1..11, z 1..11) under a stepped red dome
  (eave y 6, crown y 11) with a small glazed eye on top (to y 14); a glass shop-front and a white canopy at the street.
  Roof: a dome. Plan: lobby in front, the desk across the drum with the machine in its middle under the eye, the
  Nurse's room behind. Story: the first Nurse wanted every trainer to see the machine the moment he walks in.
- **B. The Long Porch.** Massing: a white hall, red gable along the street, a veranda on posts. Roof: one gable. Plan:
  a hall with the desk at the back. Story: the village's own house shape painted in the clinic's colors. Safe, and
  indistinguishable from every other gable in the village at 20 blocks.
- **C. The Cross Clinic.** Massing: a Greek-cross plan, four red gables round a pyramid-capped crossing tower over the
  desk. Roof: four gables and a pyramid. Plan: one arm each for entrance, waiting, desk, staff. Strong from above, but
  the arms are 3-block rooms on a 13 x 13 plot and the tower crowds them.

**Built: A**, the strongest, not the safest: nothing else in a village is round and domed, so the clinic is known by
its outline from any side; the eye puts daylight (and the chandelier's chain) straight onto the machine, so the shape
says where the job is. Risk taken: a voxel dome can read as a lumpy mound. Iterations on the renders: (1) a per-column
dome read as a strawberry, with white lining showing through the steps; (2) rebuilt as `ringDome`, concentric
octagonal rings (6, 8, 9, 10, 10, 11, 11), a red outer course and a white inner course, and any lining cell the sky can
see sideways (including behind a stair the game turns into an outer corner) turned red; (3) the drum's quoins cut back
to the eight octagon vertices so the white walls read as plaster, not speckled diorite; (4) the garden pavilion's cap
shrunk to two blocks out from the Pasture Block (an open shrine, so `holes.py open` doesn't take it for a room).

## The two looks (each another shape, growing the same way)

- **Mountain Lodge.** A log hall on a mossy rubble socle under a 45-degree red nether-brick gable whose front reaches
  two blocks out over a porch on log posts (steps, deck, brackets, boarded ceiling, lanterns), a whitewashed timbered
  front gable with a big window into the hall, red shutters on every window, a fire in the west wall with a free
  stack past the ridge, tie beams with lanterns. II: a lower log wing with its own gable to the east (Trader), the
  hall's back door to a fenced paddock (an open gap, not a gate: villagers can't work gates) with the Pasture Block on
  a stone pad, a log trough and hay.
- **Sunny Plaza.** A white hall under a low hipped brick-tile roof, a loggia in front (round middle arch with a
  keystone breaking the red band, square side bays, benches, lanterns) whose flat roof is a terrace behind a sandstone
  balustrade, and a bell-cote over the middle arch with a lantern hung where a bell would be (no bell: a bell is a
  village meeting point). II: the hall's back door opens onto a paved court at floor level: a fountain, a lawn bed in
  a kerb with the Pasture Block, lamp posts, potted azaleas, benches, a low wall with a gate gap, and a market hall on
  the east side (glazed arcade front, a lane for buyers, the counter, the Trader behind, a low tile gable). Iteration:
  the first market hall was an open arcade under a lean-to (a 10-high blank wall outside and an `open` hit), so it
  became glazed with a gable; the loggia's ceiling came down to y 4 so its shade stays a porch.

## The contract (checked on every candidate with a script, all pass)

Healing Machine only at (6, 1, 7); PC (10, 1..2, 7); Nurse cell (6, 1, 8) free on a floor and walked to from
outside; camera cell (8, 2, 4) open with a clear line to the machine; chests by the machine; one Shop Counter and one
Pasture Block in II, reachable; II keeps 98-100 % of I and every block entity in place; I > 300 blocks; under 108
stacks; only vanilla, ours and Cobblemon's blocks; `rebuild.py --candidate` prints no CHECK line; `holes.py` 0.

Pins the ship step must move (brief, "Rules tests check"): Shop Counter and Pasture Block are new cells per look
(default (15, 1, 7) / (9, 1, 19); lodge (15, 1, 6) / (10, 1, 19); plaza (16, 1, 18) / (4, 1, 21)); the lodge door test's
(6, 1, 10) door becomes (6, 1, 2) with the outside start in front of the porch; the plaza's arch check re-aims at the
keystone (6, 4, 0) over the middle arch; the declared sizes change (default 13 x 16 x 13 / 20 x 16 x 26, lodge
13 x 15 x 13 / 21 x 15 x 26, plaza 13 x 11 x 13 / 20 x 11 x 26).

Not drawn here: the five village variants (`village/<style>_pokemon_center`), a separate contract built by
`village.py`; left as they are.

## Round 2 (the default look; critic's notes, `briefs/cobblemon_50_12_critic.md`, "Pokémon Center ... round 1")

Renders: `build/rebuild/pokemon_center_r2_renders/` (five views per level, cutaways y <= 3 and y <= 7 per level). I is
718 blocks (round 1: 762), II 1,324; II keeps 98.9 % of I by block and position and every block entity in place.

1. **Flat drum walls (I right, back).** The three flat faces (west, east, back) now stand one block proud on the skirt's
   ring as bays (`BAYS` in plan.mjs): a diorite pilaster at each octagon vertex from the stone plinth to the cornice
   (the red band carried round the bay between them, the cornice's lip capping it), and the windows set back a block in
   the drum's wall behind an open reveal with a moulded diorite sill at its foot (west and east: one each; back: three,
   between two piers). The front's canopy piers are the fourth bay, so the octagon reads as four proud bays with the
   diagonals recessed between them. II: the east bay is the wing's west wall and keeps I's pilasters there.
2. **Uneven eave rings (I left, right).** The red eave ring that hung over the cornice's lip (and stepped unevenly
   against the ring above, which was round where the drum is octagonal) is gone: `ringDome` takes the rings from the
   recipe (`rings`, new and optional) so the dome's last ring is the drum's own wall ring and the dome springs straight
   off the white cornice, whose lip is now the one even overhang all round. Profile 10, 10, 9, 9, 8, 6: one stair course
   on the drum, a steep two-course shoulder, then flatter rings to the eye (tried 9, 8, 7, 6: a stepped pyramid; and a
   high shoulder: a tower; both read less as a dome).
3. **Interior (I cutaway y <= 3).** The white lining ring is gone (note 5); the lobby has two rows of four waiting
   seats facing the desk either side of the runner (couch spots), azaleas in the corners behind them, the lamps and
   ferns by the door; the Nurse's corner behind the desk has her writing table under the east back window with a lamp
   on it and a chair at it, a bookcase with a tulip, two cupboards and the two remedy chests.
4. **Bare garden (II back).** Four kerbed beds of soil (coarse dirt in a stone-brick kerb) in the garden's corners,
   planted with roses, peonies, lilac, tulips, poppies, daisies, alliums and bluets (`GARDEN` in plan.mjs); the walks
   from both back doors meet a cross walk, a spur leaves it between two lamp posts and climbs the pavilion's steps to
   the Pasture Block, and a walk goes on from the pavilion's far side to the gate.
5. **Block count (I).** 762 -> 718 with the bays and furniture added. The lining went first: `ringDome`'s new optional
   `coffered` leaves the rings over the hall with neither lining nor the block behind each step, so the vault seen from
   the hall is the dome's own stepped courses (each step a coffer shut by the tall half of the stair outside it; a coffer
   stays filled where a stair shows it a side), and the eave ring went with note 2. Detail was not trimmed. 718 is
   above the ~650 asked: the next cuts would be detail (the eye, the bays) or the dome's shoulder (690 with a high
   shoulder, which reads as a tower); left for the critic and `buildsPokemonCenter`'s 12,000-tick run to judge.

Part changes (`parts/pokemon_clinic.mjs`): `ringDome` gained two optional inputs, `rings` and `coffered`; without them it
draws exactly as before. Pins unchanged from round 1: Healing Machine (6, 1, 7), Nurse cell (6, 1, 8), PC (10, 1..2, 7),
Shop Counter (15, 1, 7), Pasture Block (9, 1..2, 19). Checks run: `build.sh` (no CHECK line), `holes.py` on both
candidates (0), a walk from the street reaches the Nurse's cell, the machine's and PC's fronts, and every chest, job
block and the Pasture Block within reach.
