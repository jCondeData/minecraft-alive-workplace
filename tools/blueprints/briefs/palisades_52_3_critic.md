# Palisade kit (52.3), round 1: the critic's scorecard

Critic: a fresh subagent that designed neither version (lane-d-1118-0408). Sheets `palisade_sheet.png`,
`palisade_2_sheet.png`, `palisade_gate_sheet.png`, `palisade_tower_sheet.png`; cutaways in
`build/rebuild/palisade*/renders/`. No eye-level shots (no furnished rooms). `selfcheck.py` ALL GREEN. Sizes allowed by
the briefs: palisade 7x4x4, palisade_2 7x4x5, gate 7x9x2, tower 5x10x5. No requirement missed by any of the four.

## palisade: BACK

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 2 | 2 | Front/back/top: a low flat-topped band at y 3; the stake tops that said "palisade" are gone. |
| Depth | 2 | 4 | Back/front: cobble foot, mud-brick revetment, pale walk, dark stakes, berm, wale: a shadow line at every step. |
| Interior | 2 | 2 | Front/cut2: a one-wide walk with a one-block parapet; no player can climb up. |
| Detail | 2 | 3 | Back/front: a disciplined earth-and-timber palette; the toe stairs are buried, the wale reads as a ledge. |

1. Front/cut2_front: the cobble toe stairs at (x,0,0) have mud bricks right on them; make the toe a step you can
   stand on, then the revetment, then the walk.
2. Front/back/top: the stake line ends flat one block over the walk; give the stake tops a shape that reads as a
   palisade from 20 blocks (same in every column, stations kept). Also fixes the 4-high wall between a 9-high gate and
   a 10-high tower reading as a hedge.
3. Back: the oak wale is a bottom stair stepping out at the raiders; make it read as a rail on the stakes' outer face.

## palisade_2: BACK

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 2 | 2 | Front/back: the same low band plus a slab strip and a dirt strip. |
| Depth | 3 | 4 | Level I's section plus the step at z 0 and the ditch bed at z 4. |
| Interior | 2 | 2 | Top: walk two wide, but the open x 3 notch leads nowhere (the revetment is 2 high). |
| Detail | 2 | 3 | Clean palette; the ditch is one straight row of stakes on a curb. |

1. Level I's notes 1 and 2.
2. Top/front: the open cell at (3,2,0) has no purpose: give it a real way up from the ground or close it.
3. Back/right: the ditch reads as a curb; within z 4 make it read as a dug ditch with stakes in it.

## palisade_gate: SHIP

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 2 | 3 | Front/back: a gabled timber gatehouse you would know in outline; sides still thin. |
| Depth | 1 | 2 | Cobble plinths, a pale lintel, an opening above; no overhang front to back. |
| Interior | 1 | 2 | Cut5: a slot over the lintel with 4 stations at the kit's walk height, lit. |
| Detail | 2 | 3 | Hanging lanterns under the tie beam, king post, ridge slab, stone plinths. |

Risks: the way through is 2 high (lintel at y 2: stops a ravager, but an Iron Golem can no longer pass; OLD was 3);
the segment's walk dead-ends on the gate's x 0 / x 6 log posts.

## palisade_tower: BACK

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 3 | 3 | A box, a one-block slit and a pyramid with no overhang; reads the same as OLD from 20 blocks. |
| Depth | 2 | 2 | Front/back: pale posts and the y 4 band flush with the infill. |
| Interior | 2 | 2 | Cut1: the ground floor is a 27-block packed-mud plug with a 1-wide passage; covered deck, 8 stations. |
| Detail | 3 | 3 | Cleaner palette, niche lantern; the 3x3 dark panels are blank from y 1 to 3. |

1. Front/back/sides: set the dark panels back from the pale posts and the y 4 band so the frame casts shadow.
2. Cut1: hollow the plug into a ground room a guard walks into under the deck.
3. Front/sides: openings on the outer faces (back z 4 and sides) that read as loopholes.
4. Front/left/top: make the fighting top read from 20 blocks (a taller open band, or a roof that overhangs or flares).

# Round 2 (fresh critic): palisade BACK, palisade_tower BACK

Brief: no written requirement missed; the verdicts rest on the scores and two functional problems.

palisade I: Silhouette 2/3, Depth 2/3, Interior 2/3, Detail 2/2 (alone it would pass). palisade II: Silhouette 2/2,
Depth 3/3, Interior 2/3, Detail 2/2. II must keep I, so the family goes back.

1. Front/back/top, both levels: replace the copper lightning-rod points: they draw every strike within ~128 blocks onto
   the walk (guards and builders turned into witches, B928/B952), make the wall a lightning ring, and cost 21 copper
   ingots a segment. A wooden point, the same in every column and not sturdy (a spruce fence post fits).
2. Back/right, II: make the outside read as a ditch, not a striped curb (vary stake heights or a low outer lip; break up
   the flat oak band), within z 4.
3. Back, both levels: one shadow line on the outer face (move the stake line in a row so the wale or a berm stands
   proud, or another way).
4. Top, II (risk): the x 3 notch makes II's columns differ, so two overlapping upgraded copies disagree; make it agree
   at any shift or note the upgrade is for single segments.

palisade_tower: Silhouette 3/3, Depth 2/4, Interior 2/3, Detail 3/2.

1. All views: replace the trapdoor infill at y 1-3 with full blocks (pathfinding counts trapdoors passable,
   `work/ShutTrapdoors` only fixes shut ones, `VillageProtection.open` lets anyone use them, `MaterialRules` ignores
   `open`; a click on (1,1,4) opens a 1 x 1.6 gap to the outside). Get the shadow another way.
2. Front: replace the doorway's open-trapdoor leaves at (2,0,0) and (2,1,0) (a click blocks the way in): a plain
   opening, or a real door accepted as a breach (the brief allows it).
3. Front/back/left/top: eaves or a flare past the posts; bring the dark-oak finial into the palette.

## Applied at ship (palisade_gate)

The lintel at (2..4, 2, 0..1) is oak top slabs, not stripped oak logs: a 2-high way through left no room for a
pillager over a carpet in a broken gate's gap (`QaSiegeGatewaySaves1107e.aCarpetInTheGapLeavesTheWayOpen`). Now 2.5
high; a ravager fits under it, as under the old gate. Stations (4), holes and owner rules unchanged.

# Round 3 (fresh critic): palisade SHIP, palisade_tower BACK (to the owner as it stands)

No lightning rods (fence finials), no trapdoor infill (only the open ladder hatch at (2,4,3)), the tower's door at
(2,0..1,0) on the village face, 7 dripstones at z 4 on coarse dirt. selfcheck ALL GREEN.

palisade I (7x5x4): Silhouette 2/3, Depth 2/4, Interior 2/3, Detail 2/3. SHIP.
palisade_2 (7x5x5, keeps 84%): Silhouette 2/3, Depth 3/4, Interior 2/3, Detail 2/3. SHIP. The ditch is still one even
row of stakes on a curb.

Risk (also in OLD): archers on the walk can't see out: by `BattleStations.view`'s line, targets 2-4 out are hidden by
the stake log at z 3 and 5-18 out by the connected fence rail at y 4 (collider to 5.5). Filed as a bug.

palisade_tower: Silhouette 3/3, Depth 2/3, Interior 2/3, Detail 3/3. BACK after its second return, so it is not
shipped: the old tower stays live and the round-3 candidate goes to the owner as it stands
(`build/rebuild/palisade_tower/`, recipe `js/palisade_tower/`). Notes if it is drawn again: (1) the fighting top reads
as OLD's from 20 blocks (a two-high open stage, a top stepping out over a set-back ground floor, or a steeper roof);
(2) posts in front of the infill so every face gets a shadow (no trapdoors, nothing a click opens); (3) tie the
stripped-oak posts to the segment's palette.
