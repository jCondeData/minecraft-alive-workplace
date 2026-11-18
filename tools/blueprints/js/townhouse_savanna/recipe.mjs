// Burgher's Townhouse, the savanna look (ROADMAP 49.10b): assembles plan.mjs from the parts library (../parts) in passes
// (block-out and structure, roofs, openings, depth and detail, interior, cellar and ground) for LEVEL = 1..4, each level
// the one before plus what it adds. Writes furniture.json (the furniture spots) and, for IV, out/cellar.txt for
// rebuild.py. CUT=<y> cuts above y (a cutaway). Build all four: build.sh.
//   Level I    the trader's house: granite storey and veranda, the mud-and-acacia hall storey, the great hip of thatch
//   Level II   + the counting house under its own hip, its veranda carried on along the street
//   Level III  + the lookout tower under a pyramid of thatch, an acacia in the yard
//   Level IV   + the vault under the shop
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { IDS, SIZES, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  LiftedCanvas, mix, stair, plinth, floor, post, beamRing, wallPanel, window, door, doorway, chimney, flight,
  railingRun, furnitureParts, cellar, hatchLadder, hipRoof, verandaEave, acaciaTree,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE

/** walls: a wall ring's cells (x0..x1, z0..z1) from y0 up to y1, or up to just under the roof when `under` is given
 *  (`skip(x, z)` leaves a cell out). */
function walls(c, { x0, z0, x1, z1 }, y0, fill, { y1 = null, under = null, skip = () => false } = {}) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if ((x !== x0 && x !== x1 && z !== z0 && z !== z1) || skip(x, z)) continue
    const top = y1 ?? under(x, z) - 1
    for (let y = y0; y <= top; y++) c.set(x, y, z, fill)
  }
}
const lantern = (c, p) => c.set(...p, 'lantern', { hanging: 'true', waterlogged: 'false' })
const pot = (c, p) => c.set(...p, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })

export function drawLevel(level) {
  const c = new LiftedCanvas(SIZES[level], BASEMENT[level] ?? 0)
  const has = o => (o.from ?? 1) <= level && level <= (o.until ?? 9)
  const stone = mix(P.stone, 29), base = mix(P.plinth, 7)
  const H = MASSES.house, V = MASSES.veranda, Wg = MASSES.wing, WV = Wg.veranda, T = MASSES.tower
  const upper = { x0: H.x0, z0: H.zUp, x1: H.x1, z1: H.z1 }

  // --- Pass 1: block-out and structure ---------------------------------------------------------------------------
  // House: the granite ground storey, the beam ring out to the jetty line, the mud hall storey, the attic's beam ring
  plinth(c, H.x0, H.zUp, H.x1, H.z1, base)
  floor(c, H.x0 + 1, H.z0 + 1, H.x1 - 1, 7, 0, P.floor)
  floor(c, H.x0 + 1, 8, H.x1 - 1, H.z1 - 1, 0, P.kitchenFloor)
  walls(c, { x0: H.x0, z0: H.z0, x1: H.x1, z1: H.z1 }, 1, stone, { y1: H.firstY - 1 })
  beamRing(c, H.x0, H.zUp, H.x1, H.z1, H.firstY, P.frame)
  floor(c, H.x0 + 1, H.z0, H.x1 - 1, H.z1 - 1, H.firstY, P.upperFloor)
  walls(c, upper, H.firstY + 1, P.mud, { y1: H.atticY - 1 })
  beamRing(c, H.x0, H.zUp, H.x1, H.z1, H.atticY, P.frame)
  floor(c, H.x0 + 1, H.z0, H.x1 - 1, H.z1 - 1, H.atticY, P.upperFloor)
  // the great hip, the knee walls up under it
  const under = hipRoof(c, H.hip, P.roof)
  walls(c, upper, H.atticY + 1, P.mud, { under })
  // grey acacia posts: the stone storey's corners, the hall storey's bays, the knee walls' corners
  for (const [x, z] of [[H.x0, H.z0], [H.x1, H.z0], [H.x0, H.z1], [H.x1, H.z1]]) post(c, x, z, 1, H.firstY - 1, P.frame)
  const Q = H.posts
  for (const x of Q.front) post(c, x, H.zUp, H.firstY + 1, H.atticY - 1, P.frame)
  for (const x of Q.back) post(c, x, H.z1, H.firstY + 1, H.atticY - 1, P.frame)
  for (const z of Q.side) for (const x of [H.x0, H.x1]) post(c, x, z, H.firstY + 1, H.atticY - 1, P.frame)
  for (const [x, z] of [[H.x0, H.zUp], [H.x1, H.zUp], [H.x0, H.z1], [H.x1, H.z1]]) post(c, x, z, H.atticY + 1, under(x, z) - 1, P.frame)
  for (const p of MASSES.ridgePots) pot(c, p)
  // the hearth's stack: mud brick through the back eave, its breast stepped, smoking
  const S = MASSES.stack
  chimney(c, S.x, S.z, 0, S.top, P.mudBrick, { breast: { axis: 'x', top: 4, stair: P.step } })

  if (level >= 2) { // the counting house: granite below, mud and acacia above, under its own hip kept off the house
    plinth(c, Wg.x0, Wg.z0, Wg.x1, Wg.z1, base)
    floor(c, Wg.x0 + 1, Wg.z0 + 1, Wg.x1 - 1, Wg.z1 - 1, 0, P.floor)
    walls(c, Wg, 1, stone, { y1: H.firstY - 1 })
    beamRing(c, Wg.x0, Wg.z0, Wg.x1, Wg.z1, H.firstY, P.frame)
    floor(c, Wg.x0 + 1, Wg.z0 + 1, Wg.x1 - 1, Wg.z1 - 1, H.firstY, P.upperFloor)
    const wu = hipRoof(c, Wg.hip, P.roof, (x, y, z) => x > Wg.hip.x0 && !c.get(x, y, z))
    walls(c, Wg, H.firstY + 1, P.mud, { under: wu })
    for (const [x, z] of [[Wg.x1, Wg.z0], [Wg.x1, Wg.z1]]) post(c, x, z, 1, H.firstY - 1, P.frame)
    for (const x of Wg.posts.front) post(c, x, Wg.z0, H.firstY + 1, wu(x, Wg.z0) - 1, P.frame)
    for (const z of Wg.posts.side) post(c, Wg.x1, z, H.firstY + 1, wu(Wg.x1, z) - 1, P.frame)
    // its veranda, a step back from the house's, under the same thatch eave
    c.fill(WV.x0, 0, WV.z0, WV.x1, 0, WV.z1, P.deck)
    for (const x of WV.posts) post(c, x, WV.z0, 1, WV.eaveY - 1, P.frame)
    verandaEave(c, { x0: WV.x0, x1: WV.x1, zEave: WV.z0, zWall: WV.z1, y: WV.eaveY, returns: ['east'] }, P.roof)
  }

  if (level >= 3) { // the lookout tower under its pyramid of thatch
    plinth(c, T.x0, T.z0, T.x1, T.z1, base)
    floor(c, T.x0 + 1, T.z0 + 1, T.x1 - 1, T.z1 - 1, 0, P.kitchenFloor)
    walls(c, T, 1, stone, { y1: H.firstY - 1 })
    const tu = hipRoof(c, T.hip, P.roof, (x, y, z) => !c.get(x, y, z))
    walls(c, T, H.firstY + 1, P.mud, { under: tu })
    for (const y of T.rings) beamRing(c, T.x0, T.z0, T.x1, T.z1, y, P.frame)
    for (const y of T.floors) floor(c, T.x0 + 1, T.z0 + 1, T.x1 - 1, T.z1 - 1, y, P.upperFloor)
    for (const [x, z] of [[T.x0, T.z0], [T.x1, T.z0], [T.x0, T.z1], [T.x1, T.z1]]) post(c, x, z, 1, tu(x, z) - 1, P.frame)
    pot(c, T.pot)
  }

  // --- Pass 2: openings ------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: P.step })
  if (has(OPENINGS.wingDoor)) {
    const wd = OPENINGS.wingDoor
    door(c, wd.at, wd.facing, 'north', { wood: P.wood, step: wd.step, stepStair: P.step })
  }
  for (const w of OPENINGS.windows) if (has(w))
    window(c, w.cells, w.out, w.dress ?? null, { glass: P.glass, wood: P.shutter, pots: w.pots ?? [], sill: P.sill })
  for (const d of OPENINGS.doorways) if (has(d)) for (const at of d.at) doorway(c, at)
  const pt = OPENINGS.partition
  wallPanel(c, [pt.x0, pt.z], [pt.x1, pt.z], H.firstY + 1, H.atticY - 1, P.partition)
  door(c, pt.door, 'south', 'south', { wood: P.wood })
  const st = OPENINGS.stairs, at = OPENINGS.attic
  for (const [x, y, z] of [...st.well, ...at.well]) c.clear(x, y, z)
  flight(c, st.flight, st.rises, P.wood, P.upperFloor)
  flight(c, at.flight, at.rises, P.wood, P.partition, H.firstY + 1)
  railingRun(c, st.rail, P.wood)
  railingRun(c, at.rail, P.wood)
  if (level >= 3) hatchLadder(c, T.ladder.x, T.ladder.z, T.ladder.y0, T.ladder.top, T.ladder.facing, P.wood)

  // --- Pass 3: depth and detail: the house's veranda on its posts under a low thatch eave --------------------------
  c.fill(V.x0, 0, V.z0, V.x1, 0, V.z0, P.deck)
  for (const x of V.posts) post(c, x, V.z0, 1, V.eaveY - 1, P.frame)
  verandaEave(c, { x0: V.x0, x1: V.x1, zEave: V.z0, zWall: V.z1, y: V.eaveY, returns: level >= 2 ? ['west'] : ['west', 'east'] }, P.roof)
  for (let l = 1; l <= Math.min(level, 2); l++) for (const [x, y, z] of OUTSIDE.lamps[l] ?? []) lantern(c, [x, y, z]) // from the eave's full course

  // --- Pass 4: interior (the plan's furniture) -------------------------------------------------------------------
  for (const f of FURNITURE) if (has(f)) furnitureParts[f.part](c, f)
  for (const f of FURNITURE) if (has(f) && f.throat) c.clear(...f.throat)

  // --- Pass 5: the vault (IV) and the ground ---------------------------------------------------------------------
  let dug = null
  if (level >= 4) {
    const Cv = MASSES.cellar
    dug = cellar(c, Cv.x0, Cv.z0, Cv.x1, Cv.z1, Cv.depth, mix(P.cellarWall, 3), P.cellarFloor)
    hatchLadder(c, Cv.ladder.x, Cv.ladder.z, -Cv.depth + 1, 0, Cv.ladder.facing, P.wood)
    for (const z of Cv.ribs) { // two ribs across the vault, springing from upside-down stairs at the walls
      for (let x = Cv.x0 + 2; x <= Cv.x1 - 2; x++) c.set(x, -1, z, 'polished_granite')
      stair(c, Cv.x0 + 1, -1, z, P.rib, 'west', 'top')
      stair(c, Cv.x1 - 1, -1, z, P.rib, 'east', 'top')
    }
  }
  for (let l = 1; l <= level && l <= 3; l++) {
    for (const [x, y, z, id] of OUTSIDE.pots[l] ?? []) if (!c.get(x, y, z)) c.set(x, y, z, id)
    for (const p of OUTSIDE.jars[l] ?? []) if (!c.get(...p)) pot(c, p)
    for (const p of OUTSIDE.hay[l] ?? []) if (!c.get(...p)) c.set(...p, 'hay_block', { axis: 'y' })
  }
  if (level >= 3) acaciaTree(c, MASSES.yard.tree, P.tree)
  return { c, dug }
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const { c, dug } = drawLevel(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  if (dug) { fs.mkdirSync(path.join(here, 'out'), { recursive: true }); fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), dug.join(',')) }
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: `${IDS[level]}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
