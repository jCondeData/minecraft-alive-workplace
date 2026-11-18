// Starter Cottage, the desert look: assembles plan.mjs from the parts library (../parts) in passes. LEVEL=1..4 picks
// the level; each draws the one below first. Writes furniture.json beside it for rebuild.py. CUT=<y> cuts above y.
//   Level I    the mud-brick house and its loggia, the flue on the west wall
//   Level II   + the two-storey wing block on the east end, its roof flue
//   Level III  + the low kitchen block east of the wing, its own door, a doorway knocked through the wing's east wall
//   Level IV   + B's cellar under the hall, in sandstone
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import { MASSES as B_MASSES, OPENINGS as B_OPENINGS, OUTSIDE as B_OUTSIDE, BASEMENT } from '../starter_cottage_b/plan.mjs'
import {
  Canvas, LiftedCanvas, cellar, hatchLadder, mix, plinth, floor, wallPanel, window, door, doorway, chimney, flight, stairwell, railingRun, slab,
  flatDeck, parapet, vigas, lampPost, furnitureParts, DIRS,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const wall = mix(P.wall, 17)
const CELLAR_WALL = mix([["sandstone", 5], ["smooth_sandstone", 2], ["cut_sandstone", 1]], 31)
const CELLAR_FLOOR = 'sandstone'
const CELLAR_BEAM = P.viga
const furnish = (c, level) => { for (const f of FURNITURE[level]) furnitureParts[f.part](c, f) }
const windows = (c, level) => {
  for (const w of OPENINGS.windows[level] ?? []) {
    window(c, w.cells, w.out, null, { glass: P.glass })
    if (!w.brow) continue
    const [x, y, z] = w.cells[0]
    const [ox, oz] = DIRS[w.out]
    if (c.inside(x + ox, y + 1, z + oz)) slab(c, x + ox, y + 1, z + oz, P.lip, 'top') // a stone brow: shade over the glass
  }
}

// box: a mud-brick block's walls round W from y0 up to the parapet's top y1, terracotta friezes at `friezes`, sandstone
// corners up to the parapet's top, one even height (the critic: piers sticking up read as a ruin)
function box(c, W, y0, y1, friezes) {
  for (const [a, b] of [[[W.x0, W.z0], [W.x1, W.z0]], [[W.x0, W.z1], [W.x1, W.z1]], [[W.x0, W.z0], [W.x0, W.z1]], [[W.x1, W.z0], [W.x1, W.z1]]]) {
    wallPanel(c, a, b, y0, y1, wall)
    for (const y of friezes) wallPanel(c, a, b, y, y, P.frieze)
  }
  for (const [x, z] of [[W.x0, W.z0], [W.x1, W.z0], [W.x0, W.z1], [W.x1, W.z1]]) for (let y = y0; y <= y1; y++) c.set(x, y, z, y === y1 ? P.pier : P.column)
}

// --- Level I: the house and its loggia -------------------------------------------------------------------------------
function levelOne(c) {
  const H = MASSES.house, W = H.walls, R = H.room, L = MASSES.loggia
  // Pass 1, block-out: plinth under house and loggia, the floor, the walls, the frieze, the deck and its parapet
  plinth(c, W.x0, L.z0, W.x1, W.z1 + 1, P.plinth)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  box(c, W, 1, H.deckY + 1, [H.friezeY])
  flatDeck(c, W.x0 + 1, W.z0 + 1, W.x1 - 1, W.z1 - 1, H.deckY, P.deck)
  // the loggia: columns two deep, a slab lintel in each bay, its roof a step below the house's, a lip on its edge
  for (const x of L.columns) for (let y = 1; y < L.roofY; y++) c.set(x, y, L.z0, P.column)
  for (const z of [L.z1]) for (const x of [L.x0, L.x1]) for (let y = 1; y < L.roofY; y++) c.set(x, y, z, P.column)
  for (let x = L.x0 + 1; x < L.x1; x++) if (!L.columns.includes(x)) slab(c, x, L.roofY - 1, L.z0, P.lip, 'top')
  flatDeck(c, L.x0, L.z0, L.x1, L.z1, L.roofY, P.deck)
  // the hearth's back in the west wall and its flue outside, stepped breast
  const F = MASSES.flue
  wallPanel(c, [W.x0, F.z - 1], [W.x0, F.z + 1], 1, 3, P.stack)
  chimney(c, F.x, F.z, 0, F.top, P.stack, { breast: { axis: 'z', top: F.breastTop, stair: P.breast } })

  // Pass 2, openings: the door behind the loggia, small windows
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.door, step: fd.step, stepStair: P.breast })
  windows(c, 1)

  // Pass 3, depth: one even row of beam ends at the deck's level, just under the parapet, every third block
  vigas(c, 'z', W.z1 + 1, OUTSIDE.vigasBack, H.deckY, P.viga)
  vigas(c, 'x', W.x1 + 1, [3, 6, 9], H.deckY, P.viga)
  vigas(c, 'x', W.x0 - 1, [3, 9], H.deckY, P.viga)
  // the door framed: chiseled jambs and lintel round it inside the loggia, the door bay open to the loggia's roof
  for (let y = 1; y <= 2; y++) for (const x of [3, 5]) c.set(x, y, W.z0, P.pier)
  for (const x of [3, 4, 5]) c.set(x, 3, W.z0, P.pier)
  c.clear(4, L.roofY - 1, L.z0)

  // Pass 4, interior
  furnish(c, 1)

  // Pass 5, ground: the path, water jars in the loggia, potted cactus at its corners
  c.set(...OUTSIDE.path, P.plinth)
  for (const p of OUTSIDE.jars) c.set(...p, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })
  for (const p of OUTSIDE.cactus) c.set(...p, 'potted_cactus')
  for (const [x, y, z, plant] of OUTSIDE.planting[1]) c.set(x, y, z, plant)
}

// --- Level II: the wing block --------------------------------------------------------------------------------------
function levelTwo(c) {
  const K = MASSES.wing, W = K.walls, R = K.room
  // Pass 1: take down what the wing replaces (the east beam ends, the cactus at the corner), then plinth, floor, both
  // storeys of mud brick with a frieze at each floor line, the deck and its parapet
  for (let z = 0; z < SIZE[2][2]; z++) for (let y = 0; y < SIZE[2][1]; y++) c.clear(8, y, z)
  plinth(c, W.x0, W.z0, W.x1, W.z1 + 1, P.plinth)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  box(c, W, 1, K.deckY + 1, [K.deckY - 1])
  for (let x = W.x0 + 1; x < W.x1; x++) for (let z = W.z0 + 1; z < W.z1; z++) c.set(x, K.floorY, z, P.floor)
  flatDeck(c, W.x0 + 1, W.z0 + 1, W.x1 - 1, W.z1 - 1, K.deckY, P.deck)
  // the hall's windows into the wing are closed again (mud brick), the front loggia's east column kept
  for (const [x, y, z] of [[7, 2, 4], [7, 2, 8]]) c.set(x, y, z, wall)
  // the flue in the east wall: up through the deck, the campfire on top, reached from the wing's roof
  const F = K.flue
  chimney(c, F.x, F.z, 0, F.top, P.stack)

  // Pass 2, openings: the doorway from the hall, the stair and its well, the windows
  doorway(c, OPENINGS.hallToWing.at, OPENINGS.hallToWing.height)
  const st = OPENINGS.wingStair
  stairwell(c, st.well)
  flight(c, st.flight, st.rises, P.wood, P.floor)
  railingRun(c, [[10, 5, 5], [9, 5, 5], [9, 5, 8]], P.wood)
  windows(c, 2)

  // Pass 3, depth: beam ends under the wing's deck front and back
  vigas(c, 'z', W.z0 - 1, [9], K.deckY, P.viga)
  vigas(c, 'z', W.z1 + 1, [9], K.deckY, P.viga)
  vigas(c, 'x', W.x1 + 1, [3, 6, 9], K.deckY, P.viga)

  for (const [x, y, z, plant] of OUTSIDE.planting[2]) c.set(x, y, z, plant)

  // Pass 4, interior
  furnish(c, 2)
}

// --- Level III: the kitchen block ---------------------------------------------------------------------------------
function levelThree(c) {
  const K = MASSES.kitchen, W = K.walls, R = K.room
  // Pass 1: a low block against the wing's east wall (which is its west wall): plinth, stone floor, mud walls with
  // the frieze, the deck a terrace under the wing's windows, its parapet on the three open sides, sandstone corners
  plinth(c, W.x0, W.z0 - 1, W.x1, W.z1 + 1, P.plinth)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, 'smooth_sandstone')
  for (const [a, b] of [[[W.x0, W.z0], [W.x1, W.z0]], [[W.x0, W.z1], [W.x1, W.z1]], [[W.x1, W.z0], [W.x1, W.z1]]]) {
    wallPanel(c, a, b, 1, K.deckY + 1, wall)
    wallPanel(c, a, b, K.friezeY, K.friezeY, P.frieze)
  }
  for (const z of [W.z0, W.z1]) for (let y = 1; y <= K.deckY + 1; y++) c.set(W.x1, y, z, y > K.deckY ? P.pier : P.column)
  flatDeck(c, R.x0, R.z0, R.x1, R.z1, K.deckY, P.deck)
  // the flue behind the smoker, up through the parapet, smoking
  chimney(c, K.flue.x, K.flue.z, 0, K.flue.top, P.stack)

  // Pass 2, openings: its own oak door, the doorway knocked through the wing's east wall, the windows
  const kd = OPENINGS.kitchenDoor
  door(c, kd.at, kd.facing, 'north', { wood: P.door, step: kd.step, stepStair: P.breast })
  doorway(c, OPENINGS.wingToKitchen.at, OPENINGS.wingToKitchen.height)
  windows(c, 3)
  vigas(c, 'z', W.z1 + 1, [14], K.deckY, P.viga)
  lampPost(c, ...OUTSIDE.kitchenLamp, P.parapet)

  for (const [x, y, z, plant] of OUTSIDE.planting[3]) c.set(x, y, z, plant)

  // Pass 4, interior
  furnish(c, 3)
}

// --- Level IV: the cellar (B's, cell for cell: a LiftedCanvas holds it at the bottom of the file) ---------------------
function levelFour(c) {
  const Cl = B_MASSES.cellar
  const inside = cellar(c, Cl.x0, Cl.z0, Cl.x1, Cl.z1, Cl.depth, CELLAR_WALL, CELLAR_FLOOR)
  const L = B_OPENINGS.cellarLadder
  hatchLadder(c, L.x, L.z, -Cl.depth + 1, 0, L.facing, P.wood)
  const S = B_OUTSIDE.cellarStores
  for (const p of S.kelp) c.set(...p, 'dried_kelp_block')
  for (const p of S.hay) c.set(...p, 'hay_block', { axis: 'y' })
  for (const p of S.pumpkin) c.set(...p, 'pumpkin')
  for (const p of S.melon) c.set(...p, 'melon')
  for (let x = Cl.x0 + 1; x < Cl.x1; x++) c.set(x, Cl.beam.y, Cl.beam.z, CELLAR_BEAM, { axis: 'x' })
  furnish(c, 4)
  return inside
}

export function draw(level = 1) {
  const c = level === 4 ? new LiftedCanvas(SIZE[4], BASEMENT) : new Canvas(SIZE[level])
  levelOne(c)
  if (level >= 2) levelTwo(c)
  if (level >= 3) levelThree(c)
  if (level >= 4) c.cellarInside = levelFour(c)
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const id = level === 1 ? 'starter_cottage' : `starter_cottage_${level}`
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
