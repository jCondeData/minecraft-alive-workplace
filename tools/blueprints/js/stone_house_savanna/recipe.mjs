// Stone House, the savanna look (ROADMAP 49.10b): assembles plan.mjs from the parts library (../parts) in passes.
// LEVEL=1..4 picks the level; each draws the one below first and adds to it. Writes furniture.json beside it (and, for
// IV, out/cellar.txt) for rebuild.py. CUT=<y> cuts above y (a cutaway). Build all four: build.sh.
//   Level I    the granite house under its pyramid of thatch, the stack on the west wall, a thatch hood over the door
//   Level II   + the parlour: a mud wing under a low three-sided hip running out over a veranda on acacia posts
//   Level III  + the back room: a mud block under a low hip whose ridge runs into the back of the great roof
//   Level IV   + the cellar under the hall, as the shipped house's, in granite
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { IDS, SIZES, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, LiftedCanvas, mix, DIRS, OPP, stair, plinth, floor, post, beamRing, window, door, doorway, chimney, flight,
  railingRun, wallPanel, furnitureParts, cellar, hatchLadder, hipRoof, hood,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const stone = mix(P.stone, 29), base = mix(P.plinth, 7)

const has = (o, level) => (o.from ?? 1) <= level && level <= (o.until ?? 9)
function windows(c, level, only = () => true) {
  for (const w of OPENINGS.windows) {
    if (!has(w, level) || !only(w)) continue
    window(c, w.cells, w.out, w.dress === 'shutters' ? 'shutters' : null, { glass: P.glass, wood: P.wood })
    if (w.dress !== 'sill') continue
    // a sill (an upside-down acacia stair one block out under the glass) with a potted plant on it
    const [x, y, z] = w.cells.reduce((a, b) => (b[1] < a[1] ? b : a))
    const [ox, oz] = DIRS[w.out]
    stair(c, x + ox, y - 1, z + oz, P.sill, OPP[w.out], 'top')
    c.set(x + ox, y, z + oz, w.pot)
  }
}
const furnish = (c, level) => { for (const f of FURNITURE) if ((f.from ?? 1) === level) furnitureParts[f.part](c, f) } // what this level adds
const pots = (c, list) => { for (const [x, y, z, id] of list ?? []) c.set(x, y, z, id) }
const roofCell = (c, x, y, z) => c.name(x, y, z).includes('bamboo')

/** walls: a wall ring's cells (x0..x1, z0..z1, those on `skip` left out) filled from y0 up to just under the roof
 *  (`under`), or to `top` when given, with `fill`. */
function walls(c, W, y0, fill, { under = null, top = null, skip = () => false } = {}) {
  for (let x = W.x0; x <= W.x1; x++) for (let z = W.z0; z <= W.z1; z++) {
    if ((x !== W.x0 && x !== W.x1 && z !== W.z0 && z !== W.z1) || skip(x, z)) continue
    const y1 = top ?? under(x, z) - 1
    for (let y = y0; y <= y1; y++) c.set(x, y, z, fill)
  }
}

// --- Level I: the granite house under its pyramid of thatch ----------------------------------------------------------
function levelOne(c) {
  const H = MASSES.house
  // Pass 1, block-out and structure: the terracotta plinth, the floors, the granite storey, the beam ring and the
  // upper floor, the great hip, the mud storey up under it, grey acacia posts at the corners and between the bays
  plinth(c, H.x0, H.z0, H.x1, H.z1, base)
  floor(c, H.x0 + 1, H.z0 + 1, H.x1 - 1, H.z1 - 1, 0, P.floor)
  walls(c, H, 1, stone, { top: H.upperY - 1 })
  beamRing(c, H.x0, H.z0, H.x1, H.z1, H.upperY, P.beam)
  floor(c, H.x0 + 1, H.z0 + 1, H.x1 - 1, H.z1 - 1, H.upperY, P.upperFloor)
  const under = hipRoof(c, H.hip, P.roof)
  walls(c, H, H.upperY + 1, P.mud, { under })
  const Q = H.posts
  for (const x of Q.front) post(c, x, H.z0, H.upperY + 1, under(x, H.z0) - 1, P.frame)
  for (const x of Q.back) post(c, x, H.z1, H.upperY + 1, under(x, H.z1) - 1, P.frame)
  for (const z of Q.sides) for (const x of [H.x0, H.x1]) post(c, x, z, H.upperY + 1, under(x, z) - 1, P.frame)
  for (const [x, z] of [[H.x0, H.z0], [H.x1, H.z0], [H.x0, H.z1], [H.x1, H.z1]]) post(c, x, z, 1, H.upperY - 1, P.frame) // the stone storey's corners
  c.set(...OUTSIDE.finial, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })
  // the hearth's stack: mud brick up the west wall, its breast stepped, through the eave, smoking
  const S = MASSES.stack
  chimney(c, S.x, S.z, 0, S.top, P.mudBrick, { breast: { axis: 'z', top: S.breastTop, stair: P.step } })

  // Pass 2, openings: the door under a thatch hood, the windows, the partition and the stair
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.door, step: fd.step, stepStair: P.step })
  hood(c, fd.at[0], OUTSIDE.doorLamp[1] + 1, OUTSIDE.doorLamp[2], P.roof)
  windows(c, 1)
  const pt = OPENINGS.partition
  for (const [x, z] of pt.cells) wallPanel(c, [x, z], [x, z], 1, H.upperY - 1, P.partition)
  wallPanel(c, [pt.door[0], pt.door[2]], [pt.door[0], pt.door[2]], 3, H.upperY - 1, P.partition)
  door(c, pt.door, 'south', 'south', { wood: 'spruce' })
  const st = OPENINGS.stairs
  for (const [x, y, z] of st.well) c.clear(x, y, z)
  flight(c, st.flight, st.rises, 'spruce', P.floor)
  railingRun(c, st.rail, 'spruce')

  // Pass 4, interior: the shipped house's furniture, cell for cell
  furnish(c, 1)
  // Pass 5, ground: potted plants at the corners
  pots(c, OUTSIDE.pots[1])
  return under
}

// --- Level II: the parlour and its veranda ---------------------------------------------------------------------------
function levelTwo(c) {
  const W = MASSES.parlour, V = W.veranda, H = MASSES.house
  // take down what the wing replaces: the great hip's east eave, the corner pot, the bedroom's east window
  for (let z = H.hip.z0; z <= H.hip.z1; z++) c.clear(H.hip.x1, H.hip.eaveY, z)
  c.clear(10, 0, 9)
  for (const w of OPENINGS.windows.filter(w => w.until === 1)) for (const [x, y, z] of w.cells) {
    if (y > H.upperY) c.set(x, y, z, P.mud) // the bedroom's: closed (the hall's becomes the doorway, its glass a light over it)
    for (const dz of [-1, 1]) c.clear(x + 1, y, z + dz) // its shutters, now in the parlour or under its roof
  }
  // Pass 1: plinth and floor, the veranda's deck, the low hip (kept east of the house), mud walls up under it with
  // acacia posts at the corners and between the bays
  plinth(c, W.x0 + 1, W.z0, W.x1, W.z1, base)
  floor(c, W.x0 + 1, W.z0 + 1, W.x1 - 1, W.z1 - 1, 0, P.floor)
  c.fill(V.x0, 0, V.z0, V.x1, 0, V.z1, P.deck)
  const under = hipRoof(c, W.hip, P.roof, (x, y, z) => x > W.x0 && !c.get(x, y, z))
  walls(c, W, 1, P.mud, { under, skip: x => x === W.x0 })
  for (const z of W.posts) post(c, W.x1, z, 1, under(W.x1, z) - 1, P.frame)
  for (const z of [W.z0, W.z1]) post(c, W.x0 + 1, z, 1, under(W.x0 + 1, z) - 1, P.frame)
  // Pass 2, openings: the hall's east window knocked through, the parlour's windows
  doorway(c, OPENINGS.wingDoorway.at)
  windows(c, 2, w => (w.from ?? 1) === 2)
  // Pass 3, depth: the veranda's posts under the front eave, a lantern hung from it, a hay bale to sit on
  for (const x of V.posts) post(c, x, V.z0, 1, under(x, V.z0) - 1, P.frame)
  c.set(...OUTSIDE.verandaLamp, 'lantern', { hanging: 'true', waterlogged: 'false' })
  for (const p of OUTSIDE.hay[2]) c.set(...p, 'hay_block', { axis: 'y' })
  pots(c, OUTSIDE.pots[2])
  // Pass 4, interior
  furnish(c, 2)
}

// --- Level III: the back room ----------------------------------------------------------------------------------------
function levelThree(c) {
  const B = MASSES.backRoom, H = MASSES.house
  // take down what the back room replaces: the great hip's back eave and everything a block out of the back wall (the
  // windows' shutters), the corner pots; the windows that now look into the back room closed
  for (let x = H.hip.x0; x < H.hip.x1; x++) for (let y = 1; y <= H.hip.eaveY; y++) c.clear(x, y, H.hip.z1)
  c.clear(H.hip.x1, H.hip.eaveY, H.hip.z1)
  for (const p of [[0, 0, 9], [10, 0, 9]]) c.clear(...p)
  for (const w of OPENINGS.windows.filter(w => w.until === 2)) for (const [x, y, z] of w.cells) c.set(x, y, z, y <= 4 ? stone : P.mud)
  // Pass 1: plinth and floor, the low hip (kept behind the house's wall and clear of the parlour's), mud walls up
  // under it with acacia posts
  plinth(c, B.x0, B.z0 + 1, B.x1, B.z1, base)
  floor(c, B.x0 + 1, B.z0 + 1, B.x1 - 1, B.z1 - 1, 0, P.floor)
  const under = hipRoof(c, B.hip, P.roof, (x, y, z) => z > B.z0 && !c.get(x, y, z))
  walls(c, B, 1, P.mud, { under, skip: (x, z) => z === B.z0 })
  for (const x of B.posts) post(c, x, B.z1, 1, under(x, B.z1) - 1, P.frame)
  for (const x of [B.x0, B.x1]) post(c, x, 11, 1, under(x, 11) - 1, P.frame)
  // Pass 2, openings: the kitchen's back window knocked through, the back room's windows
  doorway(c, OPENINGS.backDoorway.at)
  windows(c, 3, w => (w.from ?? 1) === 3)
  pots(c, OUTSIDE.pots[3])
  // Pass 4, interior
  furnish(c, 3)
}

// --- Level IV: the cellar (the shipped house's, cell for cell, in granite) --------------------------------------------
function levelFour(c) {
  const C = MASSES.cellar
  const dug = cellar(c, C.x0, C.z0, C.x1, C.z1, C.depth, mix(P.cellarWall, 3), P.cellarFloor)
  hatchLadder(c, C.ladder.x, C.ladder.z, -C.depth + 1, 0, C.ladder.facing, 'spruce')
  furnish(c, 4) // first: the beam goes round what the shelves already hold
  const Bm = C.beam // the summer beam across the store carrying the hall's floor, on one post in the middle
  for (let x = C.x0 + 1; x <= C.x1 - 1; x++) if (!c.get(x, -1, Bm.z)) c.set(x, -1, Bm.z, P.beam, { axis: 'x' })
  for (let y = -C.depth + 1; y <= -2; y++) c.set(Bm.post, y, Bm.z, P.frame, { axis: 'y' })
  return dug
}

export function draw(level = 1) {
  const c = level >= 4 ? new LiftedCanvas(SIZES[4], BASEMENT[4]) : new Canvas(SIZES[level])
  levelOne(c)
  if (level >= 2) levelTwo(c)
  if (level >= 3) levelThree(c)
  const dug = level >= 4 ? levelFour(c) : null
  return { c, dug }
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const { c, dug } = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  if (dug) { fs.mkdirSync(path.join(here, 'out'), { recursive: true }); fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), dug.join(',')) }
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: `${IDS[level]}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
