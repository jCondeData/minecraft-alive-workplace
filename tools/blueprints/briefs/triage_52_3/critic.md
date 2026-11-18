# 52.3 Walls: the critic's triage

Milestone 52's rule: a small piece is rebuilt only when it looks weak next to the rebuilt village. A piece that holds
up is ticked with this sheet and gets no new drawing. The rebuilt village it is judged against: Well and Well 2,
Well House, Fountain, Lily Pond, Footbridge, the Daub House (`house_t1_b`), and the 52.2 rebuilds (gazebo, fire pit,
stag statue and the rest). Their shared bar: a stepped stone base, a two-tone roof with trim, lanterns on posts at the
corners, flowers in their own beds, and depth on every face.

How this was judged: each live `.nbt` was rendered (Lodestone, `front`, `left`, `top`, `back` and `low` views). Each
sheet `<piece>_sheet.png` in this folder shows the piece on the left ("OLD" = the live drawing) beside a rebuilt
village piece on the right ("NEW": the Well House beside the stone wall, the wall tower and the palisade tower; the
Fountain beside stone wall II; the Daub House beside wall tower II; the Footbridge beside the palisade and the gate;
the rebuilt gazebo beside palisade II). They are not to the same scale. Scores use REBUILD.md's scorecard (1-5). For
walls, "Interior" means **Use**: a wall walk you can stand on, crenels to shoot from, a gate that works as a gate, and
a tower you can climb to a lookout (sentries and raids, `guard/`). Verdict: **HOLDS UP** means 3 or more on every
score and no plainer than the rebuilt pieces; anything else is **REBUILD**. A family's levels are judged together: if
one level is REBUILD, the family is.

These pieces are laid in kits (`data/aliveworkplace/wall_kits/`): **palisade** (segment `palisade`, corner
`palisade_tower`, gate `palisade_gate`, hamlet to village) and **stone** (segment `stone_wall`, corner `wall_tower`,
gate `gatehouse`, which is not in 52.3). A segment repeats end to end along the whole wall line (`city/Walls`), so any
weakness in it is multiplied by the length of the wall.

| Piece | Sil. | Depth | Use | Detail | Verdict | Biggest weaknesses / evidence |
|---|---|---|---|---|---|---|
| stone_wall | 2 | 1 | 3 | 2 | **REBUILD** | Front/back: a flat 7 x 4 stone-brick slab with a plain merlon row, no plinth, batter, string course or coping. Left: a 3-deep box. The inner face is as flat as the outer. Use works: a 1-wide walk at y 4 behind a 2-high parapet, reached by a ladder (front), but repeated down a whole wall line it reads as a cardboard cutout beside the Well House's stepped base. |
| stone_wall_2 | 3 | 3 | 3 | 3 | (rebuilt with level 1) | Front/back/low: adds a timber hoarding over the walk (spruce posts, stripped-log shutters hanging outward, a lantern) under a flat dark-oak lid. The hoarding is the best idea in the set: keep it. But the lid has no pitch, eave or trim (top), and it sits on level 1's flat wall, which it must keep. |
| wall_tower | 2 | 2 | 3 | 2 | **REBUILD** | Front/left/back: a 5 x 5 stone-brick shaft, 8 high, with one band of logs, one arrow slit, a door and a merlon crown. No base course, no corbels under the crown, no taper. Top: a ladder inside to a plank floor and crenelated roof works. Baselined owner-rule problem: a lantern standing on the plank floor (`lantern@1,7,1`). Plainer than any rebuilt piece. |
| wall_tower_2 | 3 | 2 | 3 | 2 | (rebuilt with level 1) | Front/low: adds a log-framed lookout and a dark-oak stair pyramid, a better outline. But the pyramid has no trim, overhang or finial (top), and the stone shaft below is level 1's flat box. Wall tower II next to the Daub House's two-tone roof and framed gables looks unfinished. |
| palisade | 2 | 2 | 3 | 2 | **REBUILD** | Front/back: a flat 7 x 4 spruce-log sheet topped with fence posts. The stakes are not pointed or staggered, and the outer face (back) is one blank log plane. Low: the walk is a 1-wide slab ledge with a ladder. You can stand and look over, but there's nothing else. It doesn't read as a stockade from 20 blocks. |
| palisade_2 | 2 | 3 | 3 | 2 | (rebuilt with level 1) | Front/left: adds plank buttress boxes inside and a firmer walk. Back/low: the outer ditch is a coarse-dirt strip laid at y 0 with dripstone spikes, a raised lump with soil sides (the same y 0 fault as the old fire pit). Still a flat log sheet from outside. |
| palisade_gate | 2 | 1 | 2 | 2 | **REBUILD** | Front/back: two log pillars and a fence-rail bridge over a 3-wide gap closed by three 1-high fence gates. It's a farm gate, not a gate work: no doors, frame or roof. The walk over it has no way up and isn't tied into the segments' walk. Front/low: the lanterns stand on the bare ground (baselined owner-rule problem, 2). The weakest piece of the set. |
| palisade_tower | 3 | 3 | 3 | 3 | **REBUILD** | Front/left: the best of the set. A 5 x 5 log tower with cobble corner feet, a stripped-log band, an open fenced lookout and a lantern-lit hip roof. Top/back: walls and roof are both dark wood (spruce log and dark oak, near one-wood), and the roof has no trim or overhang. All one log tone from the ground up. Plainer than the rebuilt gazebo and Well House, and it must match the rebuilt palisade and gate as their corner. |

**Count:** none hold up. All five families are rebuilt: stone wall (with level 2), wall tower (with level 2),
palisade (with level 2), palisade gate and palisade tower.

## Notes for the rebuilds (most important first)

1. **Segments repeat.** `stone_wall` and `palisade` are laid end to end along the whole wall line (`city/Walls`), so a
   flat face shows dozens of times. Each needs depth on both faces (a base course, buttresses or posts every few
   blocks, coping or pointed stakes) that still joins seamlessly to the next copy. The brief must keep the sizes the
   code reads (`StarterBlueprints`: palisade 7 x 6 x 2, palisade_2 7 x 6 x 4, stone_wall 7 x 6 x 3, stone_wall_2
   7 x 9 x 4, wall_tower 5 x 10 x 5, wall_tower_2 5 x 12 x 5, palisade_gate 7 x 6 x 2, palisade_tower 5 x 10 x 5),
   unless it changes them on purpose with the code and tests.
2. **The walk must be continuous.** Segment, tower and gate walks should meet at the same height so a sentry can
   walk the wall. Today the gate's walk can't be reached, and the tower's floors don't line up with the segment walks.
3. **Gate works.** The palisade gate is a fence-gate gap. `guard/Gates` shuts kit gates at night, so the brief must
   say which blocks are the gate (doors or gates it opens and shuts) and keep that working, with a frame, a walk above
   and lamps on posts or hung (not on the ground).
4. **Kits read as one.** Each kit's three pieces share one palette and one base course. The palisade kit is all
   spruce and dark oak from the ground up (palisade_tower, back), so it needs a stone foot and a second roof tone.
5. **Keep the good ideas:** stone_wall_2's timber hoarding, palisade_2's outer spike ditch (paved or edged at y 0, not
   raised soil), wall_tower_2's lookout room and palisade_tower's open lookout and stripped-log band.

Renders used: the scratch render folder of this session (`<name>_<view>.png`, not committed). The sheets in this folder
are the record.
