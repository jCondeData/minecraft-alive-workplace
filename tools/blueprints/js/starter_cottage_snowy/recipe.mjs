// Starter Cottage, the snowy look: assembles plan.mjs from the parts library (../parts) in passes. LEVEL=1..4 picks
// the level; each draws the one below first. Writes furniture.json beside it for rebuild.py. CUT=<y> cuts above y.
//   Level I    the log cabin, its mudroom and chimney, the loft over a low ceiling
//   Level II   + the steep cross-wing on the east end: stone below, logs above
//   Level III  + the stone lean-to kitchen, its own door under a snow hood, a doorway through the wing's east wall
//   Level IV   + B's cellar under the hall, in this look's stone
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import { MASSES as B_MASSES, OPENINGS as B_OPENINGS, OUTSIDE as B_OUTSIDE, BASEMENT } from '../starter_cottage_b/plan.mjs'
import {
  Canvas, LiftedCanvas, cellar, hatchLadder, mix, stair, plinth, floor, post, wallPanel, window, door, doorway, gableRoof, gableWall, chimney, flight,
  stairwell, railingRun, logCourse, mudroom, catslideRoof, pentice, lampPost, furnitureParts,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const stone = mix(P.stone, 23)
const chimneyStone = mix(P.chimney, 29)
const CELLAR_WALL = stone
const CELLAR_FLOOR = 'cobblestone'
const CELLAR_BEAM = P.log
const furnish = (c, level) => { for (const f of FURNITURE[level]) furnitureParts[f.part](c, f) }
const windows = (c, level) => { for (const w of OPENINGS.windows[level] ?? []) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood }) }

// log walls round W: a stone course at y 1, logs lying along each wall above it up to y1 (or to the roof's underside
// on the gable ends, which take planks above the eave), log posts at the corners
function cabinWalls(c, W, y1) {
  for (const [a, b] of [[[W.x0, W.z0], [W.x1, W.z0]], [[W.x0, W.z1], [W.x1, W.z1]], [[W.x0, W.z0], [W.x0, W.z1]], [[W.x1, W.z0], [W.x1, W.z1]]]) {
    wallPanel(c, a, b, 1, 1, stone)
    logCourse(c, a, b, 2, y1, P.log)
  }
  for (const [x, z] of [[W.x0, W.z0], [W.x1, W.z0], [W.x0, W.z1], [W.x1, W.z1]]) post(c, x, z, 1, y1, P.log)
}

// --- Level I: the cabin --------------------------------------------------------------------------------------------
function levelOne(c) {
  const H = MASSES.cabin, W = H.walls, R = H.room
  // Pass 1, block-out: the stone plinth, the floor, the steep roof, the log walls up to it, the gables, the loft floor
  plinth(c, W.x0, W.z0, W.x1, W.z1, stone)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  const roof = gableRoof(c, H.roof, P.roof)
  cabinWalls(c, W, roof.under[W.x0] - 1)
  for (const z of [W.z0, W.z1]) gableWall(c, 'z', z, [W.x0 + 1, W.x1 - 1], roof.under[W.x0], roof.under, () => P.gable)
  floor(c, R.x0, R.z0, R.x1, R.z1, H.ceilingY, P.floor)
  // the hearth's stone back in the west wall, and the heavy chimney outside it: three wide to y 6, the stack above
  const C = MASSES.chimney
  wallPanel(c, [W.x0, C.wide[0]], [W.x0, C.wide[1]], 1, 3, stone)
  for (let z = C.wide[0]; z <= C.wide[1]; z++) for (let y = 0; y <= C.wideTop; y++) c.set(C.x, y, z, chimneyStone)
  stair(c, C.x, C.wideTop + 1, C.wide[0], 'stone_brick_stairs', 'south')
  stair(c, C.x, C.wideTop + 1, C.wide[1], 'stone_brick_stairs', 'north')
  chimney(c, C.x, C.z, C.wideTop + 1, C.top, chimneyStone)

  // Pass 2, openings: the mudroom before the door (its own outer oak door), the inner door, small windows
  const M = MASSES.mudroom
  mudroom(c, M, { wall: P.gable, roof: P.roof, door: P.door, floor: stone })
  const d = OPENINGS.innerDoor
  door(c, d.at, d.facing, 'north', { wood: P.door })
  windows(c, 1)

  // Pass 3, detail: lanterns under the mudroom's eaves, a woodpile under the east eave
  for (const p of OUTSIDE.porchLamps) c.set(...p, 'lantern', { hanging: 'true', waterlogged: 'false' })
  for (const p of OUTSIDE.woodpile) c.set(...p, P.log, { axis: 'z' })

  // Pass 4, interior
  furnish(c, 1)
}

// --- Level II: the cross-wing --------------------------------------------------------------------------------------
function levelTwo(c) {
  const K = MASSES.wing, W = K.walls, R = K.room
  // Pass 1: take down what the wing replaces (the east eave and the woodpile at x 8), then plinth, floor, the cobble
  // storey, the log floor beam, the log storey and plate, the steep roof and its plank gables
  for (let y = 0; y < SIZE[2][1]; y++) for (let z = 0; z < SIZE[2][2]; z++) c.clear(8, y, z)
  for (let y = 1; y < SIZE[2][1]; y++) for (let z = W.z0; z <= W.z1; z++) c.clear(7, y, z)
  plinth(c, W.x0, W.z0, W.x1, W.z1, stone)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  for (const [a, b] of [[[W.x0, W.z0], [W.x1, W.z0]], [[W.x0, W.z1], [W.x1, W.z1]], [[W.x0, W.z0], [W.x0, W.z1]], [[W.x1, W.z0], [W.x1, W.z1]]]) {
    wallPanel(c, a, b, 1, 3, stone)
    logCourse(c, a, b, K.floorY, K.floorY, P.log)
    logCourse(c, a, b, K.floorY + 1, K.plateY, P.log)
  }
  for (const [x, z] of [[W.x0, W.z0], [W.x1, W.z0], [W.x0, W.z1], [W.x1, W.z1]]) post(c, x, z, K.floorY, K.plateY, P.log)
  for (let x = R.x0; x <= R.x1; x++) for (let z = R.z0; z <= R.z1; z++) c.set(x, K.floorY, z, P.floor)
  const roof = gableRoof(c, K.roof, P.roof, (x, y, z) => !c.get(x, y, z))
  for (const z of [W.z0, W.z1]) gableWall(c, 'z', z, [W.x0 + 1, W.x1 - 1], K.plateY + 1, roof.under, () => P.gable)
  // the hall's roof meets the wing's west wall: close the hall's gable ends where the wing's wall took the eave
  for (const z of [MASSES.cabin.walls.z0, MASSES.cabin.walls.z1]) for (let y = 5; y <= 6; y++) if (!c.get(6, y, z)) c.set(6, y, z, P.gable)

  // Pass 2, openings: the doorway from the hall, the stair and its well, the windows
  doorway(c, OPENINGS.hallToWing.at, OPENINGS.hallToWing.height)
  const st = OPENINGS.wingStair
  stairwell(c, st.well)
  flight(c, st.flight, st.rises, P.wood, P.floor)
  railingRun(c, [[10, 5, 5], [9, 5, 5], [9, 5, 8]], P.wood)
  windows(c, 2)

  // Pass 4, interior
  furnish(c, 2)
}

// --- Level III: the kitchen lean-to ----------------------------------------------------------------------------------
function levelThree(c) {
  const K = MASSES.kitchen, W = K.walls, R = K.room
  // Pass 1: plinth, stone floor, the lean-to roof tucked under the wing's eave, rubble walls up to it, log corners
  plinth(c, W.x0, W.z0 - 1, W.x1, W.z1 + 1, stone)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, 'stone_bricks')
  const roof = catslideRoof(c, K.roof, P.roof)
  const top = x => roof.under[x] - 1
  for (let x = W.x0; x <= W.x1; x++) for (const z of [W.z0, W.z1]) for (let y = 1; y <= top(x); y++) c.set(x, y, z, stone)
  for (let z = W.z0 + 1; z < W.z1; z++) for (let y = 1; y <= top(W.x1); y++) c.set(W.x1, y, z, stone)
  for (const z of [W.z0, W.z1]) post(c, W.x1, z, 1, top(W.x1), P.log)
  // the flue behind the smoker, up through the slope, smoking
  chimney(c, K.flue.x, K.flue.z, 0, K.flue.top, chimneyStone)

  // Pass 2, openings: its own oak door under a snow hood, the doorway knocked through the wing's east wall, windows
  const kd = OPENINGS.kitchenDoor
  door(c, kd.at, kd.facing, 'north', { wood: P.door, step: kd.step })
  doorway(c, OPENINGS.wingToKitchen.at, OPENINGS.wingToKitchen.height)
  windows(c, 3)
  const hd = OUTSIDE.kitchenHood
  pentice(c, Array.from({ length: hd.x1 - hd.x0 + 1 }, (_, i) => [hd.x0 + i, hd.z]), hd.y, P.roof, 'south')
  lampPost(c, ...OUTSIDE.kitchenLamp, `${P.wood}_fence`)

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
