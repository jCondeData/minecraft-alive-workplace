# Portals and shrines (52.4), round 1: the critic's scorecard

Critic: a fresh subagent that designed none of the candidates (lane-d, 2026-11-18). Judged from existing PNGs and the
`.nbt`s only (no renderer, no Gradle: a GameTest build was running). OLD = the live drawings (the 52.4 triage renders,
`triage_52_4/`); NEW = the candidates in `build/rebuild/{nether_gate,waystone_plaza,mega_shrine}/`. No eye-level
shots (no furnished rooms). For these pieces "Interior" means **Use**, as in the triage: the portal and the
netherworker's corner, the stone a traveler steps up to, the pedestal a trainer climbs with the crowd in front.

Sheets (OLD left, NEW right, not to the same scale): `nether_gate_sheet.png`, `nether_gate_2_sheet.png`,
`waystone_plaza_sheet.png` (plains; every look in `build/rebuild/waystone_plaza/grid.png`), `mega_shrine_sheet.png`
(NEW front/right/top/low use vanilla stand-ins for the Mega Showdown blocks; back/left show them as gaps). Render
caveats as in the triage: blackstone draws warm brown, modded blocks are left out, podzol draws as a hole.

## Checks run on the NEW `.nbt`s (by script, this session)

| | Result |
|---|---|
| `check.py` (`check` + `owner_problems`) on all 8 candidates | nothing, all 8 |
| `holes.py` on all 8 candidates | 0 (exit 0) |
| Nether Gate I | 13 x 16 x 9 (limit 13 x 16 x 13); one obsidian frame only (10 obsidian, gilded corners; the only obsidian with air above is its sill), inside 2 x 3 at (4,2,3), axis X, empty; the cells in front (4..5, 2, 2) open on solid footing, reached up the front steps; one `nether_brazier` at (10,2,5), chest (11,2,3) and barrel (11,2,4) beside it; netherworker stands at (10,2,4) through the lodge's crimson door (10,2,2); two lit soul campfires (placeable); 677 blocks, ~31 stacks |
| Nether Gate II | 15 x 16 x 14 (limit 15 x 16 x 14, exactly); the same one frame; keeps **653 of I's 677 blocks (96.5 %)**, II >= I on every axis; 10 containers, all within 8/4 of the brazier; 16 nether wart, all `age=3`; ~41 stacks (< 108) |
| Waystone Plaza, all 5 looks | 7 x 5 x 7; exactly one waystone, lower (3,2,3) + upper (3,3,3), the culture's kind (`waystone`, `sandy_`, `mud_bricks_`, `deepslate_`, `mossy_`), `origin=village`, `waterlogged=false`; an open standing spot on all four sides at ring 1 (N (3,2,2), S (3,2,4), W (2,2,3), E (4,2,3)), SE spot (4,2,4) nearer a SE village than the stone; no POI, bed or storage; front middle (3,0,0) walkable; all four sides reached on foot from the front. Hanging lanterns hang from bottom-half eave stairs (full underside), placed last by `BuildPlan.hangingLast` |
| Mega Shrine | 7 x 6 x 7; the seven ids present; one pedestal, at (3,3,3), on `chiseled_mega_meteorid_block` (3,2,3); trainer cell (3,3,2) and (3,4,2), (3,5,2) free, footing `mega_meteorid_brick`; lanterns on wall posts or hanging (none on a floor); the trainer's cell is reached on foot from the crowd spot (3,1,-4), but only by a zig-zag of corner stairs (see risks) |

**Brief requirements missed: none**, in any family. Ship-step reminders: Nether Gate sizes change, so
`StarterBlueprints.NETHER_GATE` becomes `(13, 16, 9)` and `NETHER_GATE_2` `(15, 16, 14)`; regenerate `modded/nether_gate*.nbt`
with `modded_library.py`; the harness frame call at (4,2,3), axis X, stays. Remove `mega_shrine` from
`owner_rules_baseline.json`.

## nether_gate: SHIP

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 3 | 4 | Front/back: a two-tier roofed gate with horned ridge ends and a lower lodge roof tucked under the eave, a shape no other workshop has; left/right still read as a narrow tower (piers 3 deep, 16 tall). |
| Depth | 2 | 4 | Front/left: a two-step dais, piers with basalt corners, the frame recessed between them, crimson eave beams a block proud, two flared eaves; every face has a shadow line. |
| Use | 3 | 4 | Cut y3 front/top: the netherworker has a walled lodge with a door, the brazier against its back wall and the chest and barrel beside it; the portal is climbed to up broad steps. |
| Detail | 3 | 4 | Front: gilded frame corners and frieze, soul lanterns hanging from the beam ends and on the finials, soul-fire pedestals; three materials (black stone, crimson, quartz) instead of one. |

## nether_gate_2: SHIP

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 3 | 4 | Front/back: the gate plus a taller Haul House behind the lodge, three roofs stepping up; right view weakest: the Haul House's big dark roof hides the gate roof behind it. |
| Depth | 2 | 4 | Right/back: timber frame proud of quartz infill on a blackstone plinth, the wart terrace walled and stepped down behind the portal. |
| Use | 3 | 4 | Cut y3 top: eight more containers in the Haul House, a basalt path between two soul-sand wart beds, the Haul House door opening onto the terrace. |
| Detail | 2 | 3 | Back: shroomlights in the path, lantern pillar at its end, ripe wart; but the Haul House's eight warped-trapdoor "shutters" flank blank quartz walls (no window between them, right/back). |

Family verdict: **SHIP** (both levels beat OLD on all four, by 1-2). Optional polish in the ship step, not a return:
(a) right/back: put a glass pane between each pair of shutters at (11, 3-4, 12) and (13, 3-4, 9), or drop the shutters;
(b) cut y3 top: the cell (11,2,5) behind the brazier is a dead end reachable only diagonally; the Haul House is reached
from the terrace door, which is fine, but a 1-wide opening (10,2,6)->(11,2,6) inside would let the netherworker walk
from his brazier to the haul without going round.

## waystone_plaza (5 looks): SHIP

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 2 | 4 | Front/left/grid: a pavilion with a two-tone hipped roof (dark hips, an oculus over the stone) instead of a flat pad with four sticks; desert gets a flat sandstone roof of its own. |
| Depth | 2 | 4 | Front/right: log posts, a one-block eave all round, bench backs and arms, a cobbled plinth, the stone on a stepped dais. |
| Use | 3 | 4 | Cut y2 front: real benches (three stairs between stone arms) on both sides facing the stone, under shelter, lit; the stone reached from front and back, standing room on all four sides. |
| Detail | 2 | 4 | Grid: flowers in their own corner beds (dead bush on sand, ferns on podzol), hanging lanterns at the eave corners, each culture's palette held. |

All five looks are the same plan (143 identical cells), only re-materialed; the brief allows it, and the desert roof
already differs. Not a return.

## mega_shrine: BACK

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 2 | 3 | Front/low: a round arch over the pedestal reads "shrine" from 20 blocks; left/right: the arch is one block thick, a thin hoop on edge over a low cross-shaped dais. |
| Depth | 3 | 3 | Low: the front face of the dais is a flat two-high wall (slab, then chiseled deepslate); the arch is a single skin; the cross plan's arms and stair edges give the sides some shadow. |
| Use | 3 | 3 | Low/front: the arch frames the trainer and the pedestal for the crowd (better than OLD), but there is no way up the front middle (a 1.5-block riser from the slab at z 0 to the wall top at z 1), so the trainer climbs a zig-zag of corner stairs; OLD had a straight flight. |
| Detail | 2 | 4 | Low: amethyst flanking the pedestal, soul lanterns hanging under the arch and on wall posts (no floor lamps), a meteorite keystone over the pedestal. |

Beats OLD on two scores (silhouette, detail), so **BACK**. Notes, most important first:

1. **Low/front: give the front middle a real flight.** Today (2..4, 0, 0) are bottom slabs and (2..4, 1, 1) full blocks
   (top at y 2): a 1.5 riser nobody can climb, and the front reads as a wall. Make x 2-4 a straight walk from the
   ground to the trainer's cell (3,3,2) (e.g. stairs at z 0, z 1 and z 2 rising one each), so the trainer walks
   straight up toward the crowd's line of sight, as the showcase scene films it.
2. **Left/right: give the arch thickness.** A second ring of blocks at z 2 or z 4 (or stair returns on its back and
   front faces) so it isn't a one-block hoop on edge; keep (3,3..5,2) free for the trainer.
3. **Low: break the flat front of the dais.** With note 1's flight in the middle, step or mould the arms at z 1
   (stairs or slabs proud of the wall) so the front face has the shadow the sides have.

## Gameplay and test risks seen in the blocks

- **Nether Gate build time.** I has 677 blocks (OLD 138) and is 16 tall; II has 1061 (OLD 498).
  `buildsNetherGate` allows 30,000 ticks and `buildsNetherGateII` 40,000. Run both at ship; the top (y 15) is also
  the last row of `big_area` at origin y 2 (y 17), so nothing may grow by a row.
- **Nether Gate II:** eight open warped trapdoors on the Haul House's outer walls at y 3-4 (shutters). Nobody walks
  there and a click only closes them, so no path risk; flagged only because they flank no window.
- **Nether Gate:** the brazier sits on the only way past it inside the lodge ((10,2,5) with the barrel at (11,2,4)): a
  villager can reach the chest, barrel and brazier from (10,2,3..4), fine for the job; the Haul House is reached by the
  terrace door (9,2,9).
- **Waystone Plaza:** the roof (y 3-4) hides the stone from a distance; a player finds a pavilion, not a standing
  stone. Clearance under the eave is 2.5 blocks at the front and back openings: enough for villagers and players.
- **Mega Shrine:** the trainer's route (corner slab, (0,0,1) stair, (1,1,1) stair, (2,2,1) stair, (3,2,1) top) is
  climbable by the pathfinder by this script, but it is a three-turn stair; if it fails in game, the show waits its 600
  ticks. Note 1 removes the risk.
