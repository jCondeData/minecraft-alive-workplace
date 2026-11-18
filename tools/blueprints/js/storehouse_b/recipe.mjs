// Storehouse (Version B, the Canal Warehouse), the recipe: assembles plan.mjs from the parts library (../parts) in
// passes per level (block-out and structure, openings, depth and detail, stores and lamps, ground) and returns the
// structure for the minecraft-builder skill's runner. LEVEL=1|2|3 picks the level (each draws the one before and grows
// it); CUT=<y> builds a cutaway with everything above y removed. Writes furniture.json (the lamps) for rebuild.py.
//   Level I    the Warehouse (crow gable, hoist, Counting Desk, Loft) on its Vault
//   Level II   + the Twin: a lower gable bay east, a cart arch, its own hoist, a valley between the roofs
//   Level III  + the Rack Hall across the back: back-to-back chest racks under a low roof
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { BASEMENT, SIZE, PALETTE as P, MASSES, OPENINGS as O, STORES, LAMPS, TIES, OUTSIDE } from './plan.mjs'
import {
  LiftedCanvas, mix, stair, plinth, floor, wallPanel, window, door, gableRoof, gableWall, bargeTrim, quoins,
  stringCourse, flight, stairwell, railing, cellar, hatchLadder, furnitureParts,
  chestStack, countingDesk, crowSteps, hoistArm,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const plinthStone = mix(P.plinth, 3)
const vaultStone = mix(P.vaultWall, 5)
const brick = P.wall

function ring(c, x0, z0, x1, z1, y0, y1, id, skip = () => false) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++)
    if ((x === x0 || x === x1 || z === z0 || z === z1) && !skip(x, z)) for (let y = y0; y <= y1; y++) c.set(x, y, z, id)
}
const windows = (c, list) => {
  for (const w of list) window(c, w.cells, w.out, w.dress ?? null, { glass: P.glass, wood: P.trim, sill: `${P.trim}_stairs`,
    pots: w.dress === 'sill' ? ['potted_red_tulip'] : [] })
}
const stores = (c, level) => { for (const [cells, ys, facing] of STORES[level]) chestStack(c, cells, ys, facing) }
const lamps = (c, level) => {
  for (const [x, y, z, chain] of LAMPS[level]) furnitureParts.hangingLamp(c, { pos: [x, y, z], chain, facing: 'north', wood: P.trim })
}
const ties = (c, level) => {
  const t = TIES[level]
  for (const z of t.zs) for (let x = t.x0; x <= t.x1; x++) c.set(x, t.y, z, P.frame, { axis: 'x' })
}

// --- Level I: the Warehouse on its Vault ---------------------------------------------------------------------------
function levelOne(c) {
  const W = MASSES.warehouse, V = MASSES.vault
  // Pass 1: block-out and structure. The vault first (its walls are the house's foundations), then the plinth.
  cellar(c, V.x0, V.z0, V.x1, V.z1, V.depth, vaultStone, P.vaultFloor)
  for (let y = -3; y <= -1; y++) c.set(V.pier[0], y, V.pier[1], P.pier)
  plinth(c, W.x0, W.z0, W.x1, W.z1, plinthStone)
  floor(c, ...[W.room[0], W.room[1], W.room[2], W.room[3]], 0, P.floor)
  ring(c, W.x0, W.z0, W.x1, W.z1, 1, W.top, brick)
  quoins(c, [[W.x0, W.z0], [W.x1, W.z0], [W.x0, W.z1], [W.x1, W.z1]], 1, W.top, P.dress, P.dressAlt)
  stringCourse(c, W.x0, W.z0, W.x1, W.z1, W.floorY, P.dress)
  floor(c, W.room[0], W.room[1], W.room[2], W.room[3], W.floorY, P.floor)
  const roof = gableRoof(c, W.roof, P.roof)
  gableWall(c, 'z', W.z1, [W.x0, W.x1], W.top + 1, roof.under, () => brick)
  const light = new Set(O.roundLight.map(([x, y]) => `${x},${y}`))
  crowSteps(c, W.z0, W.x0, W.x1, W.top + 1, W.crow.tops, (x, y) => (light.has(`${x},${y}`) ? P.glass : brick), P.cap)

  // Pass 2: openings
  const fd = O.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.trim, step: fd.step, stepStair: 'stone_brick_stairs' })
  door(c, O.loftDoor.at, 'south', 'north', { wood: P.trim })
  windows(c, O.windows1)
  stairwell(c, O.flight.well)
  flight(c, O.flight.steps, O.flight.rises, P.trim, P.floor)
  railing(c, O.flight.rail, P.trim)
  hatchLadder(c, O.hatch.x, O.hatch.z, -V.depth + 1, 0, O.hatch.facing, P.trim)

  // Pass 3: depth and detail. Stone lintels over the loft openings, kneelers at the gable's feet, the hoist, barge
  // boards under the back rake, the tie beams the loft's lamps hang from.
  for (const x of [3, 5, 7]) c.set(x, W.top, W.z0, P.dress)
  stair(c, W.x0 - 1, W.top, W.z0, 'stone_brick_stairs', 'east')
  stair(c, W.x1 + 1, W.top, W.z0, 'stone_brick_stairs', 'west')
  hoistArm(c, W.hoist, 'north', P.frame, 3)
  bargeTrim(c, 'z', W.roof.along[1], W.roof.across, roof.under, `${P.trim}_stairs`)
  ties(c, 1)

  // Pass 4: the Counting Desk, its posts, the stores and the lamps
  countingDesk(c, [8, 1, 7], 'west', 'z', P.job)
  for (const z of [4, 10]) for (let y = 1; y <= 3; y++) c.set(8, y, z, P.post, { axis: 'y' })
  stores(c, 1)
  lamps(c, 1)

  // Pass 5: ground. Hay by the door, waiting to go up.
  c.set(8, 0, 0, 'hay_block', { axis: 'y' }); c.set(9, 0, 0, 'hay_block', { axis: 'x' })
}

// --- Level II: the Twin --------------------------------------------------------------------------------------------
function levelTwo(c) {
  const T = MASSES.twin, W = MASSES.warehouse
  // the Warehouse's east overhang, its barge board and kneeler come down: the Twin's roof takes their place
  for (let z = W.roof.along[0]; z <= W.roof.along[1]; z++) c.clear(10, W.top, z)
  c.clear(10, 6, 12); c.clear(10, W.top, 1)
  plinth(c, T.x0, T.z0, T.x1, T.z1, plinthStone)
  floor(c, T.room[0], T.room[1], T.room[2], T.room[3], 0, P.floor)
  ring(c, T.x0, T.z0, T.x1, T.z1, 1, T.top, brick, (x) => x === T.x0) // the party wall x 9 is the Warehouse's
  quoins(c, [[T.x1, T.z0], [T.x1, T.z1]], 1, T.top, P.dress, P.dressAlt)
  stringCourse(c, T.x0, T.z0, T.x1, T.z1, T.floorY, P.dress, (x) => x === T.x0)
  floor(c, T.room[0], T.room[1], T.room[2], T.room[3], T.floorY, P.floor)
  const roof = gableRoof(c, T.roof, P.roof, (x, y, z) => x >= 10 || !c.get(x, y, z))
  for (let z = T.roof.along[0]; z <= T.roof.along[1]; z++) c.set(9, 8, z, P.roof.full) // the valley
  gableWall(c, 'z', T.z1, [T.x0, T.x1], T.top + 1, roof.under, () => brick)
  crowSteps(c, T.z0, T.x0, T.x1, T.top + 1, T.crow.tops, (x, y) => (x === 12 && y === 9 ? P.glass : brick), P.cap)
  bargeTrim(c, 'z', T.roof.along[1], T.roof.across, roof.under, `${P.trim}_stairs`)
  c.clear(8, 6, 12) // the Twin's trim stops at the valley
  stair(c, T.x1 + 1, T.top, T.z0, 'stone_brick_stairs', 'west')

  // openings: the party doors, the cart arch, the loft door, windows
  for (const cells of O.partyDoors) for (const [x, y, z] of cells) c.clear(x, y, z)
  const A = O.cartArch
  for (let x = A.x0; x <= A.x1; x++) { c.clear(x, 1, A.z); c.clear(x, 2, A.z); stair(c, x, 0, A.z - 1, 'stone_brick_stairs', 'south') }
  c.set(12, 3, A.z, P.dress) // a keystone between the arch's shoulders
  stair(c, A.x0, 3, A.z, 'stone_brick_stairs', 'west', 'top')
  stair(c, A.x1, 3, A.z, 'stone_brick_stairs', 'east', 'top')
  for (let x = A.x0; x <= A.x1; x++) door(c, [x, 1, A.z], 'south', 'north', { wood: P.trim, hinge: x === A.x1 ? 'right' : 'left' })
  door(c, O.twinLoftDoor.at, 'south', 'north', { wood: P.trim })
  windows(c, O.windows2)
  for (const x of [10, 12, 14]) c.set(x, T.top, T.z0, P.dress)
  hoistArm(c, T.hoist, 'north', P.frame, 3)
  ties(c, 2)
  stores(c, 2)
  lamps(c, 2)
  for (const [x, y, z] of OUTSIDE.hay) c.set(x, y, z, 'hay_block', { axis: 'y' })
}

// --- Level III: the Rack Hall ---------------------------------------------------------------------------------------
function levelThree(c) {
  const R = MASSES.rackHall
  plinth(c, R.x0, R.z0, R.x1, R.z1, plinthStone)
  floor(c, R.room[0], R.room[1], R.room[2], R.room[3], 0, P.floor)
  ring(c, R.x0, R.z0, R.x1, R.z1, 1, R.top, brick, (x, z) => z === R.z0) // the back walls of I and II stand already
  quoins(c, [[R.x0, R.z1], [R.x1, R.z1]], 1, R.top, P.dress, P.dressAlt)
  const roof = gableRoof(c, R.roof, P.roof, (x, y, z) => z > R.z0 && !c.get(x, y, z))
  for (const x of [R.x0, R.x1]) gableWall(c, 'x', x, [R.z0, R.z1], R.top + 1, roof.under, () => brick)
  for (const x of [R.x0 - 1, R.x1 + 1]) {
    bargeTrim(c, 'x', x, R.roof.across, roof.under, `${P.trim}_stairs`)
    for (let z = R.roof.across[0]; z <= R.z0; z++) for (let y = 0; y <= 4; y++) if (c.name(x, y, z).endsWith('_stairs')) c.clear(x, y, z)
  }
  for (const [x, y, z] of O.hallDoors) { c.clear(x, y, z); c.clear(x, y + 1, z) }
  const bd = O.backDoor
  door(c, bd.at, bd.facing, 'south', { wood: P.trim, step: bd.step, stepStair: 'stone_brick_stairs' })
  windows(c, O.windows3)
  stores(c, 3)
  lamps(c, 3)
}

export function draw(level) {
  const c = new LiftedCanvas(SIZE[level], BASEMENT)
  levelOne(c)
  if (level >= 2) levelTwo(c)
  if (level >= 3) levelThree(c)
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const id = level === 1 ? 'storehouse' : `storehouse_${level}`
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
