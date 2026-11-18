// Starter Cottage (Version B), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes and
// returns the structure for the minecraft-builder skill's runner. LEVEL=1..4 picks the level (default 1): each level
// draws the one below first and then its own passes, so every level grows out of the last cell for cell.
// Writes furniture.json beside it for rebuild.py. CUT=<y> builds a cutaway with everything above y removed.
//   Level I    the Saltbox: hall, outshot, chimney
//   Level II   + the cross-wing (parlour and chamber), the chimney two courses higher
//   Level III  + the kitchen lean-to with its own door, a doorway knocked through the wing's east wall
//   Level IV   + the cellar under the hall (a LiftedCanvas: the file holds it at the bottom)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, LiftedCanvas, mix, stair, plinth, floor, post, wallPanel, window, door, doorway, gableRoof, catslideRoof,
  pentice, gableWall, bargeTrim, chimney, flowerBed, woodpile, railingRun, flight, stairwell, cellar, hatchLadder,
  lampPost, furnitureParts,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const stone = mix(P.stone, 11)
const pots = (c, w) => { // a sill (an upside-down stair one block out) with a potted flower on it, under a window
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
const inner = ([a0, a1]) => [a0 + 1, a1 - 1] // trim only between the overhangs: no loose tabs at the corners
const furnish = (c, level) => { for (const f of FURNITURE[level]) furnitureParts[f.part](c, f) }
// the barge boards' peak: a full block of the trim's wood under the ridge's end, where the two upside-down trim stairs
// meet (one stair there left a notch at the gable's peak: the critic's note on II's front gable)
const peak = (c, x, y, z) => { if (c.name(x, y, z) === `minecraft:${P.trim}`) c.set(x, y, z, P.trim.replace(/_stairs$|_slab$/, '_planks')) }

// --- Level I: the Saltbox --------------------------------------------------------------------------------------------
function levelOne(c) {
  const H = MASSES.hall, W = H.walls, R = H.room
  // Pass 1, block-out and structure: the plinth (a block wider at the front, under the step and the sills), the floor,
  // the saltbox roof, then the walls up to it
  plinth(c, W.x0, W.z0 - 1, W.x1, W.z1, stone)
  plinth(c, 0, W.z0 + 5, 0, W.z1, stone) // a skirt under the bed nook's shutters
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  const roof = catslideRoof(c, H.roof, P.roof)
  // the catslide's last course kicks up level with the one above it (a slab), so the back windows show beneath it
  const kz = H.roof.across[1]
  for (let x = H.roof.along[0]; x <= H.roof.along[1]; x++) { c.clear(x, roof.under[kz], kz); c.set(x, roof.under[kz] + 1, kz, P.roof.slab, { type: 'bottom', waterlogged: 'false' }) }
  plinth(c, W.x0, kz, W.x1, kz, stone) // an apron under the back sills
  const top = z => roof.under[z] - 1 // the highest wall cell under the roof at z
  // front wall (tall, under the short slope): posts, birch panels, the beam the eave sits on
  wallPanel(c, [W.x0, W.z0], [W.x1, W.z0], 1, 3, P.infill)
  for (const x of H.frontPosts) post(c, x, W.z0, 1, 3, P.frame)
  for (let x = W.x0; x <= W.x1; x++) c.set(x, H.beamY, W.z0, P.frame, { axis: 'x' })
  // back wall (low, under the catslide)
  wallPanel(c, [W.x0, W.z1], [W.x1, W.z1], 1, top(W.z1), P.infill)
  for (const x of H.backPosts) post(c, x, W.z1, 1, top(W.z1), P.frame)
  // the gables: panels up to the roof, posts, a beam at y 4 where the wall is tall enough, a king post under the ridge
  for (const [x, posts] of [[W.x0, H.westPosts], [W.x1, H.eastPosts]]) {
    for (let z = W.z0 + 1; z < W.z1; z++) for (let y = 1; y <= top(z); y++) c.set(x, y, z, P.infill)
    for (const z of posts) post(c, x, z, 1, Math.min(3, top(z)), P.frame)
    for (let z = W.z0; z <= W.z1; z++) if (top(z) >= H.beamY) c.set(x, H.beamY, z, P.frame, { axis: 'z' })
    for (let y = H.beamY + 1; y <= top(H.roof.ridge); y++) c.set(x, y, H.roof.ridge, P.frame, { axis: 'y' })
  }
  // the hearth: a stone back in the west wall, the stack outside it through the verge and out past the ridge
  const C = MASSES.chimney
  wallPanel(c, [W.x0, C.z - 1], [W.x0, C.z + 1], 1, 3, stone)
  chimney(c, C.x, C.z, 0, C.top[1], stone, { breast: { axis: 'z', top: C.breastTop } })

  // Pass 2, openings: the front door with its step, the windows
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.door, step: fd.step })
  windows(c, 1)

  // Pass 3, depth and detail: dark barge boards under both verges, a lantern under the eave over the step
  bargeTrim(c, 'x', H.roof.along[0], inner(H.roof.across), roof.under, P.trim)
  bargeTrim(c, 'x', H.roof.along[1], inner(H.roof.across), roof.under, P.trim)
  for (const x of H.roof.along) peak(c, x, roof.under[H.roof.ridge] - 1, H.roof.ridge)
  c.set(...OUTSIDE.doorLamp, 'lantern', { hanging: 'true', waterlogged: 'false' })

  // Pass 4, interior
  furnish(c, 1)

  // Pass 5, ground: a stone path to the step between two flower beds, and the door's hood
  c.set(...OUTSIDE.path, stone)
  for (const b of OUTSIDE.flowerBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  // the door hood: a little shingled gable on two spruce posts over the step
  const hd = OUTSIDE.doorHood
  for (const x of hd.posts) for (let y = 1; y < hd.y; y++) c.set(x, y, hd.z, `${P.wood}_fence`)
  stair(c, hd.posts[0], hd.y, hd.z, P.roof.stairs, 'east')
  stair(c, hd.posts[1], hd.y, hd.z, P.roof.stairs, 'west')
  c.set(OUTSIDE.path[0], hd.y, hd.z, P.roof.full)
  c.set(OUTSIDE.path[0], hd.y + 1, hd.z, P.roof.slab, { type: 'bottom', waterlogged: 'false' })
  return roof
}

// --- Level II: the cross-wing ------------------------------------------------------------------------------------------
function levelTwo(c) {
  const K = MASSES.wing, W = K.walls, R = K.room, H = MASSES.hall
  // Pass 1: take down what the wing replaces (the hall's east verge and its trim, the flower at the wing's corner),
  // then the wing's plinth, rubble ground storey, floor beam, timber upper storey and plate, its roof and gables
  for (let y = 0; y < SIZE[2][1]; y++) for (let z = 0; z < SIZE[2][2]; z++) c.clear(8, y, z)
  for (const [x, y, z] of [[7, 1, 0], [7, 3, 11], [7, 3, 10]]) c.clear(x, y, z)
  plinth(c, W.x0, W.z0 - 1, W.x1, W.z1, stone)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  wallPanel(c, [W.x0, W.z0], [W.x1, W.z0], 1, 3, stone)
  wallPanel(c, [W.x1, W.z0], [W.x1, W.z1], 1, 3, stone)
  wallPanel(c, [W.x0 + 1, W.z1], [W.x1, W.z1], 1, 3, stone)
  post(c, W.x0, W.z1, 1, 3, P.frame) // the hall's back corner, now the wing's too
  for (let x = W.x0; x <= W.x1; x++) for (let z = W.z0; z <= W.z1; z++) {
    const edge = x === W.x0 || x === W.x1 || z === W.z0 || z === W.z1
    if (edge) c.set(x, K.floorY, z, P.frame, { axis: (x === W.x0 || x === W.x1) && (z === W.z0 || z === W.z1) ? 'y' : (z === W.z0 || z === W.z1) ? 'x' : 'z' })
    else c.set(x, K.floorY, z, P.floor)
  }
  // upper storey: birch panels between posts on every side, the plate ring on top
  for (let y = K.floorY + 1; y < K.plateY; y++) {
    for (let x = W.x0; x <= W.x1; x++) { c.set(x, y, W.z0, P.infill); c.set(x, y, W.z1, P.infill) }
    for (let z = W.z0; z <= W.z1; z++) { c.set(W.x0, y, z, P.infill); c.set(W.x1, y, z, P.infill) }
  }
  for (const [x, z] of [[W.x0, W.z0], [W.x1, W.z0], [W.x0, W.z1], [W.x1, W.z1]]) post(c, x, z, K.floorY + 1, K.plateY - 1, P.frame)
  for (const z of K.eastPosts) post(c, W.x1, z, K.floorY + 1, K.plateY - 1, P.frame)
  for (const z of [4, 7]) post(c, W.x0, z, K.floorY + 1, K.plateY - 1, P.frame)
  for (let x = W.x0; x <= W.x1; x++) for (let z = W.z0; z <= W.z1; z++) {
    if (x !== W.x0 && x !== W.x1 && z !== W.z0 && z !== W.z1) continue
    const corner = (x === W.x0 || x === W.x1) && (z === W.z0 || z === W.z1)
    c.set(x, K.plateY, z, P.frame, { axis: corner ? 'y' : (z === W.z0 || z === W.z1) ? 'x' : 'z' })
  }
  const roof = gableRoof(c, K.roof, P.roof, (x, y, z) => !c.get(x, y, z))
  for (const zLine of [W.z0, W.z1]) gableWall(c, 'z', zLine, K.roof.across, K.plateY + 1, roof.under, (u, y) =>
    u === 9 && y === K.plateY + 2 ? [P.frame, { axis: 'y' }] : P.infill)
  // the hall's chimney goes up two courses to draw clear of the wing's taller roof; the campfire moves up with it
  const C = MASSES.chimney
  chimney(c, C.x, C.z, C.top[1], C.top[2], stone)

  // Pass 2, openings: the doorway from the hall, the stair and its well, the windows
  doorway(c, OPENINGS.hallToWing.at, OPENINGS.hallToWing.height)
  const st = OPENINGS.wingStair
  stairwell(c, st.well)
  flight(c, st.flight, st.rises, P.wood, P.floor)
  railingRun(c, [[10, 5, 5], [9, 5, 5], [9, 5, 8]], P.wood)
  windows(c, 2)

  // Pass 3, depth and detail: a pentice over the parlour window, barge boards under both verges, the hall's east gable
  // lights (now inside the wing) closed with birch
  pentice(c, [[8, 0], [9, 0], [10, 0]], H.beamY, P.roof, 'south')
  bargeTrim(c, 'z', K.roof.along[0], inner(K.roof.across), roof.under, P.trim)
  bargeTrim(c, 'z', K.roof.along[1], inner(K.roof.across), roof.under, P.trim)
  const mid = (K.roof.across[0] + K.roof.across[1]) / 2
  for (const z of K.roof.along) peak(c, mid, roof.under[mid] - 1, z)
  for (const [y, z] of [[5, 4], [6, 4], [5, 6], [6, 6], [2, 8], [3, 8]]) c.set(7, y, z, P.infill) // and the outshot's east window by the new doorway

  // Pass 4, interior
  furnish(c, 2)
}

// --- Level III: the kitchen ------------------------------------------------------------------------------------------
function levelThree(c) {
  const K = MASSES.kitchen, W = K.walls, R = K.room
  // Pass 1: plinth (with an apron under the step, the lamp post and the shutters), stone floor, the lean-to roof
  // tucked under the wing's eave, the rubble walls up to it, log corners
  plinth(c, W.x0, W.z0 - 1, W.x1, W.z1 + 1, stone)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.kitchenFloor)
  const roof = catslideRoof(c, K.roof, P.roof)
  const top = x => roof.under[x] - 1
  for (let x = W.x0; x <= W.x1; x++) for (const z of [W.z0, W.z1]) for (let y = 1; y <= top(x); y++) c.set(x, y, z, stone)
  for (let z = W.z0 + 1; z < W.z1; z++) for (let y = 1; y <= top(W.x1); y++) c.set(W.x1, y, z, stone)
  for (const z of [W.z0, W.z1]) post(c, W.x1, z, 1, top(W.x1), P.frame)
  bargeTrim(c, 'z', K.roof.along[0], inner(K.roof.across), roof.under, P.trim)
  bargeTrim(c, 'z', K.roof.along[1], inner(K.roof.across), roof.under, P.trim)
  // the wing's back east window now looks into the kitchen's roof: closed with birch, its shutters gone
  for (const y of [5, 6]) { c.set(11, y, 8, P.infill); c.clear(12, y, 7) }
  // and the parlour's back east window (Level II) now looks into the kitchen: stone again, its shutters and pot gone
  for (const y of [2, 3]) { c.set(11, y, 7, stone); c.clear(12, y, 6); c.clear(12, y, 8) }
  for (const y of [1, 2]) c.clear(12, y, 7)
  // the flue: a stack in the east wall behind the smoker, up through the slope, smoking
  chimney(c, K.flue.x, K.flue.z, 0, K.flue.top, stone)

  // Pass 2, openings: its own oak door, the doorway knocked through the wing's east wall, the windows
  const kd = OPENINGS.kitchenDoor
  door(c, kd.at, kd.facing, 'north', { wood: P.door, step: kd.step })
  doorway(c, OPENINGS.wingToKitchen.at, OPENINGS.wingToKitchen.height)
  windows(c, 3)

  // Pass 3 and 5: the lamp post at the kitchen door
  lampPost(c, ...OUTSIDE.kitchenLamp, `${P.wood}_fence`)

  // Pass 4, interior
  furnish(c, 3)
}

// --- Level IV: the cellar ----------------------------------------------------------------------------------------------
function levelFour(c) {
  const Cl = MASSES.cellar
  const inside = cellar(c, Cl.x0, Cl.z0, Cl.x1, Cl.z1, Cl.depth, stone, P.cellarFloor)
  const L = OPENINGS.cellarLadder
  hatchLadder(c, L.x, L.z, -Cl.depth + 1, 0, L.facing, P.wood)
  for (const p of OUTSIDE.cellarStores.kelp) c.set(...p, 'dried_kelp_block')
  for (const p of OUTSIDE.cellarStores.hay) c.set(...p, 'hay_block', { axis: 'y' })
  for (const p of OUTSIDE.cellarStores.pumpkin) c.set(...p, 'pumpkin')
  for (const p of OUTSIDE.cellarStores.melon) c.set(...p, 'melon')
  // a summer beam across the ceiling, wall to wall, under the hall's floor
  for (let x = Cl.x0 + 1; x < Cl.x1; x++) c.set(x, Cl.beam.y, Cl.beam.z, P.frame, { axis: 'x' })
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
