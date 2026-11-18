// Starter Cottage, savanna look: the recipe. Assembles plan.mjs from the parts library (../parts) in passes; LEVEL=1..4
// picks the level. Writes furniture.json beside it for rebuild.py.
// CUT=<y> builds a cutaway with everything above y removed.
//   Level I    the Veranda House: the hall under one low hip, the veranda under its front eave
//   Level II   + the Mud Tower: B's wing as a two-storey mud house under the hall's thatched hip, the corner stack
//   Level III  + the Cookhouse: B's kitchen under a three-sided hip leaning on the tower, its door under the eave
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, LiftedCanvas, mix, cellar, hatchLadder, stair, plinth, floor, post, window, door, doorway, chimney, flight, stairwell, railingRun,
  furnitureParts, hipRoof,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const stone = mix(P.stone, 7)
const pots = (c, w) => { // a sill (an upside-down stair one block out) with a potted plant on it, under a window
  if (!w.sill) return
  const [x, y, z] = w.cells.reduce((a, b) => (b[1] < a[1] ? b : a))
  const [ox, oz] = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }[w.out]
  stair(c, x + ox, y - 1, z + oz, `${P.wood}_stairs`, { north: 'south', south: 'north', west: 'east', east: 'west' }[w.out], 'top')
  c.set(x + ox, y, z + oz, w.sill)
}
const windows = (c, level) => {
  for (const w of OPENINGS.windows[level] ?? []) {
    window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood })
    pots(c, w)
  }
}
const furnish = (c, level) => { for (const f of FURNITURE[level]) furnitureParts[f.part](c, f) }

// --- Level I: the Veranda House --------------------------------------------------------------------------------------
function levelOne(c) {
  const H = MASSES.hall, W = H.walls, R = H.room, V = MASSES.veranda
  // Pass 1, block-out and structure: terracotta plinth and veranda deck, the floor, the hip, the mud walls up to it
  plinth(c, W.x0, W.z0 - 1, W.x1, W.z1, stone)
  c.fill(V.x0, 0, V.z0, V.x1, 0, V.z1, P.deck)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  const under = hipRoof(c, MASSES.hip, P.roof)
  for (let x = W.x0; x <= W.x1; x++) for (let z = W.z0; z <= W.z1; z++) {
    if (x !== W.x0 && x !== W.x1 && z !== W.z0 && z !== W.z1) continue
    for (let y = 1; y < under(x, z); y++) c.set(x, y, z, P.mud)
    if (under.top(x, z)) c.set(x, under(x, z), z, P.frame, { axis: x === W.x0 || x === W.x1 ? 'z' : 'x' }) // the wall plate
  }
  // grey acacia posts at the corners and between the bays, a lintel log along the tall front and back
  for (const x of H.posts) for (const z of [W.z0, W.z1]) post(c, x, z, 1, under(x, z) - 1, P.frame)
  for (const z of [6]) for (const x of [W.x0, W.x1]) post(c, x, z, 1, under(x, z) - 1, P.frame)
  for (const z of [W.z0, W.z1]) for (let x = W.x0 + 1; x < W.x1; x++) if (under(x, z) > 4) c.set(x, 4, z, P.frame, { axis: 'x' })
  // the hearth: a mud-brick back in the west wall, the stack outside it through the eave
  const C = MASSES.chimney
  for (let z = C.z - 1; z <= C.z + 1; z++) for (let y = 1; y <= 3; y++) c.set(W.x0, y, z, P.mudBrick)
  chimney(c, C.x, C.z, 0, C.top, P.mudBrick, { breast: { axis: 'z', top: C.breastTop, stair: 'mud_brick_stairs' } })

  // Pass 2, openings
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.door, step: fd.step, stepStair: 'mud_brick_stairs' })
  windows(c, 1)

  // Pass 3, depth and detail: the veranda's posts under the front eave, a lantern over the step
  for (const x of V.posts) post(c, x, V.z0, 1, under(x, V.z0) - 1, P.frame)
  c.set(...OUTSIDE.doorLamp, 'lantern', { hanging: 'true', waterlogged: 'false' })

  // Pass 4, interior
  furnish(c, 1)

  // Pass 5, the veranda's life: pots of savanna plants, hay bales to sit on in the shade
  for (const [x, y, z, id] of OUTSIDE.pots) c.set(x, y, z, id)
  for (const p of OUTSIDE.hay) c.set(...p, 'hay_block', { axis: 'y' })
  return under
}

// --- Level II: the Mud Tower -----------------------------------------------------------------------------------------
function levelTwo(c) {
  const T = MASSES.tower, W = T.walls, R = T.room
  // Pass 1: take down what the tower replaces (the hip's east eave column and the veranda's east end)
  for (let y = 0; y < SIZE[2][1]; y++) for (let z = 0; z < SIZE[2][2]; z++) c.clear(8, y, z)
  plinth(c, W.x0, W.z0 - 1, W.x1, W.z1, stone)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  // ground storey in mud brick (the hall's east wall, x 7, stays: it is the parlour's west wall below the floor)
  for (let x = W.x0; x <= W.x1; x++) for (let z = W.z0; z <= W.z1; z++) {
    const edge = x === W.x1 || z === W.z0 || z === W.z1 || (x === W.x0 && (z === W.z0 || z === W.z1))
    if (edge) for (let y = 1; y <= 3; y++) c.set(x, y, z, P.mudBrick)
  }
  // the floor at y 4: an acacia beam ring that shows outside as a band, planks inside
  for (let x = W.x0; x <= W.x1; x++) for (let z = W.z0; z <= W.z1; z++) {
    const edge = x === W.x0 || x === W.x1 || z === W.z0 || z === W.z1
    if (edge) c.set(x, T.floorY, z, P.frame, { axis: (z === W.z0 || z === W.z1) ? 'x' : 'z' })
    else c.set(x, T.floorY, z, P.floor)
  }
  // upper storey in packed mud, the flat roof terrace and its gapped parapet, the toron under it
  // upper storey in packed mud under the same thatched hip as the hall (the critic: one roof language, not a keep),
  // eaves out a block all round; grey acacia posts at the corners and between the bays, as the hall's
  const under = hipRoof(c, T.hip, P.roof, (x, y, z) => !c.get(x, y, z) || !c.name(x, y, z).includes('bamboo'))
  for (let x = W.x0; x <= W.x1; x++) for (let z = W.z0; z <= W.z1; z++)
    if (x === W.x0 || x === W.x1 || z === W.z0 || z === W.z1) for (let y = T.floorY + 1; y < under(x, z); y++) c.set(x, y, z, P.mud)
  for (const [x, z] of [[W.x0, W.z0], [W.x1, W.z0], [W.x0, W.z1], [W.x1, W.z1], [W.x1, 5], [W.x1, 7], [W.x0, 5]])
    post(c, x, z, T.floorY + 1, under(x, z) - 1, P.frame)
  // the stack up the front east corner, through the eave, with the campfire on it (lit from the roof)
  chimney(c, T.stack.x, T.stack.z, 0, T.stack.top, P.mudBrick)

  // Pass 2, openings: the doorway from the hall, the stair and its well, the windows
  doorway(c, OPENINGS.hallToWing.at, OPENINGS.hallToWing.height)
  const st = OPENINGS.wingStair
  stairwell(c, st.well)
  flight(c, st.flight, st.rises, P.wood, P.floor)
  railingRun(c, [[10, 5, 5], [9, 5, 5], [9, 5, 8]], 'spruce')
  windows(c, 2)
  for (const [y, z] of [[2, 8], [3, 8]]) c.set(7, y, z, P.mud) // the outshot's east window by the new doorway

  // Pass 3, depth: an acacia shade over the parlour's front window (slabs on two beam ends)
  for (const x of [8, 10]) c.set(x, 3, 0, P.beam)
  for (let x = 8; x <= 10; x++) c.set(x, 4, 0, P.roof.slab, { type: 'bottom', waterlogged: 'false' })

  // Pass 4, interior
  furnish(c, 2)
}

// --- Level III: the Cookhouse ---------------------------------------------------------------------------------------
function levelThree(c) {
  const K = MASSES.cookhouse, W = K.walls, R = K.room
  // first, the tower's windows that now look into the cookhouse's roof or room: mud again, their shutters and pot gone
  for (const y of [5, 6]) { c.set(11, y, 8, P.mud); c.clear(12, y, 7); c.clear(12, y, 9) }
  for (const y of [2, 3]) { c.set(11, y, 7, P.mudBrick); c.clear(12, y, 6); c.clear(12, y, 8) }
  for (const y of [1, 2]) c.clear(12, y, 7)
  // Pass 1: plinth, mud-brick floor, the hip (only east of the tower), the mud walls up to it with acacia corner posts
  plinth(c, W.x0, W.z0 - 1, W.x1, W.z1 + 1, stone)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.mudBrick)
  const under = hipRoof(c, K.hip, P.roof, x => x >= W.x0)
  for (let x = W.x0; x <= W.x1; x++) for (let z = W.z0; z <= W.z1; z++) {
    if (x !== W.x1 && z !== W.z0 && z !== W.z1) continue
    for (let y = 1; y < under(x, z); y++) c.set(x, y, z, P.mud)
  }
  for (const z of [W.z0, W.z1]) post(c, W.x1, z, 1, under(W.x1, z) - 1, P.frame)
  // the flue: a mud-brick stack in the east wall behind the smoker, up through the eave, smoking
  chimney(c, K.flue.x, K.flue.z, 0, K.flue.top, P.mudBrick)

  // Pass 2, openings: its own door under the deep front eave, the doorway knocked through the tower, the windows
  const kd = OPENINGS.kitchenDoor
  door(c, kd.at, kd.facing, 'north', { wood: P.door, step: kd.step, stepStair: 'mud_brick_stairs' })
  doorway(c, OPENINGS.wingToKitchen.at, OPENINGS.wingToKitchen.height)
  windows(c, 3)

  // Pass 4, interior
  furnish(c, 3)

  // Pass 5: water jars (decorated pots) by the cookhouse door, in the eave's shade
  c.set(15, 1, 3, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })
}

// --- Level IV: the cellar (B's, in this look's stone) ----------------------------------------------------------------
function levelFour(c) {
  const Cl = MASSES.cellar
  const inside = cellar(c, Cl.x0, Cl.z0, Cl.x1, Cl.z1, Cl.depth, stone, P.mudBrick)
  const L = OPENINGS.cellarLadder
  hatchLadder(c, L.x, L.z, -Cl.depth + 1, 0, L.facing, P.wood)
  for (const p of OUTSIDE.cellarStores.kelp) c.set(...p, 'dried_kelp_block')
  for (const p of OUTSIDE.cellarStores.hay) c.set(...p, 'hay_block', { axis: 'y' })
  for (const p of OUTSIDE.cellarStores.pumpkin) c.set(...p, 'pumpkin')
  for (const p of OUTSIDE.cellarStores.melon) c.set(...p, 'melon')
  for (let x = Cl.x0 + 1; x < Cl.x1; x++) c.set(x, Cl.beam.y, Cl.beam.z, P.frame, { axis: 'x' }) // a summer beam
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
