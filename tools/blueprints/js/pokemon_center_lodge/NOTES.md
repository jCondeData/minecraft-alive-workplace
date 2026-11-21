# Pokémon Center, Mountain Lodge look: Round 2 (lodge)

Round 1 came back BACK, tied with the old lodge on every score (`briefs/cobblemon_50_12_critic.md`, "Pokémon Center ...
round 1"). To ship, the new lodge must beat the old one on at least 3 of the 4 scores (silhouette, depth, interior,
detail), so round 2 aims for a clear step up on each one. Shared notes for the family stay in
`../pokemon_center/NOTES.md`. This file covers the lodge only, so the three designers don't edit the same file.

Candidates: `build/rebuild/pokemon_center_lodge/pokemon_center.nbt` (I, 13 x 15 x 13, 762 blocks) and
`pokemon_center_2.nbt` (II, 21 x 15 x 26, 1,223 blocks), built by `sh build.sh`. Renders (five views, cutaways at
y <= 3 and y <= 7 per level): `build/rebuild/pokemon_center_lodge_r2_renders/`.

## The critic's notes, and what changed

1. **Flat long sides (I right, II left).** The east slope now has a **cross gable**: ridge along x at z 7, y 9, two
   below the hall's ridge. Its roof dies into the main slope in two clean valleys, and the hall rises into it. Its
   whitewashed, timbered face is **jettied** one block out over the east wall, on a sill beam and three brackets. It
   sits flush under its own roof, like the front gable, with a window and the ridge beam's end showing. The west slope
   has a **pitched dormer** at z 8 over the desk. Its roof stops flush with its face: with an overhang, the kit sealed
   the slots either side with frame blocks and hid the window. The chimney stands beside it, and **the winter's wood**
   is stacked under the west eave.
2. **II's wing roof ran into the main slope as a lump.** The wing now **carries on I's cross gable**: the same 45-degree
   roof (ridge y 9) runs east to its own jettied gable end at x 20. The valley it meets the hall in is the cross gable's,
   already clean in I, so there's no wall of lining against the slope anymore. The plan reads as a T, with stepped
   ridges (hall 11, wing 9) and the chimney highest.
3. **The paddock was a bare fenced lawn.** It now has a **lean-to field shelter** on the back fence: log corner posts,
   board sides, a hay window, and hay inside. **Stepping-stone paths** (cobble and mossy slabs) run from the hall's back
   door and from the **wing's new back door** through two open gaps in the fence to the Pasture Block's stone pad. Each
   gap has a lamp post, and there's a log trough by the east path.
4. **The back gable was blank below its window.** The Nurse now has **her own back door** under a **pent porch** on two
   log posts with brackets. A ridge beam and **purlin ends** (x 3 and 9) push out through both gables. The back gable
   window is wider (three panes and a top light), and the back windows have window boxes.
5. **The same red shutter pair on every window.** There are now **three kinds of window**, never the same pair twice on
   a face. Red shutters go at the porch, on the west wall, by the arch, and on the wing's street and gable sides.
   **Window boxes** (a sill with potted tulip, fern, bluet, spruce sapling or daisy) go under the cross gable, at the
   back and on the wing. Plain glass goes in the gables and the dormer.

**Interior (the critic tied it).**
- **Hall:** a **waiting nook** has a couch on the east wall, a coffee table at its end and a bench facing the machine,
  on a white rug. The camera eye at (8, 2, 4) is the bench's seated head height, and its line to the machine is clear.
  The fire corner has armchairs, a lamp table and a red rug.
- **The Nurse's back room** is furnished now: a writing desk with lit candles and its chair, cupboards either side of
  her back door, two chests, a bookcase with a fern, and her lantern.
- **The Trader's wing:** the Shop Counter between two counters, a chest, a cupboard and a bookcase behind them, a
  table with two chairs and a lamp table by the gable window, a red rug for customers, and a lantern on a tie beam.
- **Light:** lanterns hang from the tie beams or stand on side tables, never on the floor (owner rule 2). Every chair
  next to a table opens toward it (checked on the finished `.nbt`).

**Level I's block count.** All of round 1's ceiling lining is gone: 117 boards under the slope that the eye reads only
as a ceiling. The hall now opens to its ridge beam, and the purlins only show their ends. The cross gable, dormer,
back porch, woodpile and window boxes spent that saving. I is **762 blocks** against round 1's 782. That's still well
above the brief's "lean, the old one is 560" and the critic's ~650. **Risk:** `buildsPokemonCenterLodge` (12,000 ticks)
hasn't been run, because this round didn't use Gradle. The ship step must confirm the timing. If it's too slow, the
next things to cut are the ridge beam's middle (10), the back porch's brackets and slabs (4), and the woodpile (4).

## The contract (checked on the finished candidates with a script; all pass)

- **Healing Machine:** exactly one, at (6, 1, 7).
- **PC:** (10, 1..2, 7).
- **Nurse's cell:** (6, 1, 8) is free on a plank floor and can be walked to from outside.
- **Camera:** the cell (8, 2, 4) is open, with a clear line to the machine.
- **Chests by the machine:** (2, 1, 9) and (10, 1, 10), plus (13, 1, 9) in II. Every chest, PC, machine, Pasture Block
  and Shop Counter is within 2 across and 1 up of a walked cell.
- **Front door:** (6, 1, 2) off the porch on the z = 0 side. The other doors are the back door (6, 1, 11) and, in II,
  the wing's (16, 1, 4) and (15, 1, 10).
- **Level II:** keeps 749 of 762 of I's blocks (98.3 %), and no block entity moves. The changes are the arch at
  (11, 1..2, 5), the shutters the wing walls in, and two brackets.
- **Materials:** 35 and 45 stacks. Only vanilla, ours and Cobblemon's blocks.
- **Checks:** `rebuild.py --candidate` prints no CHECK line, and `holes.py` reports 0.

**Pins the ship step must move (new cells):**
- **Lodge II Shop Counter:** **(15, 1, 7)**, faced from (15, 1, 6). Round 1 had it at (15, 1, 6).
- **Lodge II Pasture Block:** **(10, 1, 19)**, the same as round 1. The live test pins it at (6, 1, 15).
- **Machine and PC:** unchanged.
- **Lodge door test:** the front door moves to (6, 1, 2). The test's (6, 1, 10) door is now open floor in front of
  the back door, which is at (6, 1, 11).
- **Declared sizes:** 13 x 15 x 13 and 21 x 15 x 26.
