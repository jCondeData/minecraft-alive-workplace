// Starter Cottage, taiga look: the recipe. Assembles plan.mjs from the parts library (../parts) in passes; LEVEL=1..4
// picks the level. Writes furniture.json beside it for rebuild.py.
// CUT=<y> builds a cutaway with everything above y removed.
//   Level I    the Log Cabin: a notched spruce pen, gable-on under a steep dark roof, the porch, the woodpile
//   Level II   + the Loft Pen: B's wing as a taller log pen, its own steep gable to the road, the chimney up a course
//   Level III  + the Cook Shed: B's kitchen as a log lean-to under the pen's eave, the woodpile under its low side
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, LiftedCanvas, mix, trapdoor, dormer, cellar, hatchLadder, stair, plinth, floor, post, window, door, doorway, gableRoof, gableWall, bargeTrim, pentice, chimney,
  woodpile, railingRun, flight, stairwell, furnitureParts, logPen, catslideRoof, lampPost,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const stone = mix(P.stone, 13)
const pots = (c, w) => {
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
const inner = ([a0, a1]) => [a0 + 1, a1 - 1]
// the barge boards' peak: a full block under the ridge's end, where the two upside-down trim stairs meet (as B)
const peak = (c, x, y, z) => { if (c.name(x, y, z) === `minecraft:${P.trim}`) c.set(x, y, z, P.roof.full) }
const furnish = (c, level) => { for (const f of FURNITURE[level]) furnitureParts[f.part](c, f) }
// log ends: the corner logs of a pen cross out one block, alternately along x and z (the notching shows outside)
const logEnds = (c, { x0, z0, x1, z1 }, y0, y1, keep = () => true) => {
  for (let y = y0; y <= y1; y++) {
    const alongX = (y - y0) % 2 === 0
    for (const [x, z, dx, dz] of [[x0, z0, -1, -1], [x1, z0, 1, -1], [x0, z1, -1, 1], [x1, z1, 1, 1]]) {
      const [ex, ez] = alongX ? [x + dx, z] : [x, z + dz]
      if (keep(ex, y, ez) && !c.get(ex, y, ez)) c.set(ex, y, ez, P.log, { axis: alongX ? 'x' : 'z' })
    }
  }
}

// --- Level I: the Log Cabin ------------------------------------------------------------------------------------------
function levelOne(c) {
  const H = MASSES.hall, W = H.walls, R = H.room, Po = MASSES.porch
  // Pass 1, block-out and structure: fieldstone plinth and base course, the porch deck, the floor, the steep roof,
  // the log pen, the plank gables up to the roof
  plinth(c, W.x0, W.z0, W.x1, W.z1, stone)
  c.fill(Po.x0, 0, Po.z0, Po.x1, 0, Po.z1, P.deck)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  for (let x = W.x0; x <= W.x1; x++) for (let z = W.z0; z <= W.z1; z++)
    if (x === W.x0 || x === W.x1 || z === W.z0 || z === W.z1) c.set(x, 1, z, stone)
  const roof = gableRoof(c, MASSES.roof, P.roof)
  logPen(c, { ...W, y0: H.logs[0], y1: H.logs[1] }, P.log)
  for (const z of [W.z0, W.z1]) gableWall(c, 'z', z, [W.x0, W.x1], H.logs[1] + 1, roof.under, () => P.gable)
  for (const z of [W.z0, W.z1]) for (let y = H.logs[1] + 1; y < roof.under[4]; y++) c.set(4, y, z, P.log, { axis: 'y' }) // king post
  const T = MASSES.tie
  for (let x = T.x0; x <= T.x1; x++) c.set(x, T.y, T.z, P.log, { axis: 'x' })
  // the hearth: a fieldstone back in the west wall, the stack outside it up past the eave
  const C = MASSES.chimney
  for (let z = C.z - 1; z <= C.z + 1; z++) for (let y = 1; y <= 3; y++) c.set(W.x0, y, z, stone)
  chimney(c, C.x, C.z, 0, C.top[1], stone, { breast: { axis: 'z', top: C.breastTop } })

  // Pass 2, openings
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.door, step: fd.step })
  windows(c, 1)
  // the king post gives way to the gable windows
  for (const w of OPENINGS.windows[1].filter(w => w.name.endsWith('gable'))) for (const p of w.cells) c.set(...p, P.glass)

  // Pass 3, depth and detail: log ends at the corners, barge boards under both verges, the porch (posts, beam, rail),
  // a lantern from the porch beam, the woodpile under the east eave
  logEnds(c, W, H.logs[0], H.logs[1], (x, y, z) => z >= 1)
  for (const z of MASSES.roof.along) bargeTrim(c, 'z', z, inner(MASSES.roof.across), roof.under, P.trim)
  for (const z of MASSES.roof.along) peak(c, 4, roof.under[4] - 1, z)
  for (const x of Po.posts) post(c, x, Po.z0, 1, Po.beamY - 1, P.log)
  for (let x = Po.x0; x <= Po.x1; x++) c.set(x, Po.beamY, Po.z0, P.log, { axis: 'x' })
  gableWall(c, 'z', Po.z0, [Po.x0, Po.x1], Po.beamY + 1, roof.under, (u, y) => (u === 4 ? [P.log, { axis: 'y' }] : P.gable)) // the pediment
  for (let x = Po.x0 + 1; x < Po.x1; x++) for (let z = Po.z0 + 1; z <= Po.z1; z++) for (let y = Po.beamY; y < roof.under[x]; y++)
    if (!c.get(x, y, z)) c.set(x, y, z, P.gable) // the porch ceiling, and the pocket over it filled (no wedged cells)
  for (const [a, b] of Po.rails) railingRun(c, [[a, 1, Po.z0], [b, 1, Po.z0]], P.wood)
  for (const x of Po.posts) c.set(x, 1, Po.z0, P.log, { axis: 'y' }) // the rail's ends are the posts
  c.set(...OUTSIDE.doorLamp, 'lantern', { hanging: 'true', waterlogged: 'false' })
  // a gabled dormer in the west slope over the couch, lighting the hall (critic round 2, note 1: break the roof plane)
  dormer(c, MASSES.dormer.z, P.roof, P.gable, P.glass, P.log, { face: MASSES.dormer.face, eave: MASSES.dormer.eave, y0: MASSES.dormer.y0 })
  for (const { at: [x, z], pot } of OUTSIDE.sideBoxes) { // a box (an upside-down stair) with flowers, shutters either side
    stair(c, x, 1, z, `${P.wood}_stairs`, 'west', 'top')
    c.set(x, 2, z, pot)
    for (const dz of [-1, 1]) trapdoor(c, x, 2, z + dz, `${P.wood}_trapdoor`, 'east', 'bottom', true)
  }
  const Wp = MASSES.woodpile
  woodpile(c, Wp.x, Wp.z0, Wp.z1, P.log)
  for (let z = Wp.z0; z <= Wp.z1; z++) c.set(Wp.x, 1, z, P.log, { axis: 'x' }) // squared off: one tidy stack (critic note 2)

  // Pass 4, interior
  furnish(c, 1)

  // Pass 5, the porch's pots
  for (const [x, y, z, id] of OUTSIDE.pots) c.set(x, y, z, id)
  return roof
}

// --- Level II: the Loft Pen ------------------------------------------------------------------------------------------
function levelTwo(c) {
  const K = MASSES.pen, W = K.walls, R = K.room
  // Pass 1: take down what the pen replaces (the cabin's east eave column, its woodpile and the overhang's ends at
  // x 7), then the pen: plinth, base course, two storeys of logs with the floor between, its steep roof and gables
  for (let y = 0; y < SIZE[2][1]; y++) for (let z = 0; z < SIZE[2][2]; z++) c.clear(8, y, z)
  for (let y = 5; y <= 6; y++) c.clear(7, y, 0) // (the porch's post and beam stay)
  for (let y = 4; y <= 6; y++) c.clear(7, y, 11)
  for (const z of [1, 11]) for (let y = 1; y <= 3; y++) c.clear(7, y, z) // the cabin's east log ends
  plinth(c, W.x0, W.z0, W.x1, W.z1, stone)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  const outside = (x, z) => x === W.x1 || z === W.z0 || z === W.z1 // the cabin's east wall stays the parlour's west
  for (let x = W.x0; x <= W.x1; x++) for (let z = W.z0; z <= W.z1; z++) if (outside(x, z)) c.set(x, 1, z, stone)
  const roof = gableRoof(c, K.roof, P.roof, (x, y, z) => !c.get(x, y, z) || c.name(x, y, z).includes('dark_oak'))
  logPen(c, { ...W, y0: 2, y1: K.floorY - 1 }, P.log, (x, y, z) => !outside(x, z))
  for (let x = W.x0 + 1; x < W.x1; x++) for (let z = W.z0 + 1; z < W.z1; z++) c.set(x, K.floorY, z, P.floor)
  logPen(c, { ...W, y0: K.floorY, y1: K.plateY }, P.log)
  for (const z of [W.z0, W.z1]) gableWall(c, 'z', z, K.roof.across, K.plateY + 1, roof.under, (u) => (u === 9 ? [P.log, { axis: 'y' }] : P.gable))
  // the cabin's chimney goes up a course to draw clear of the pen's roof; the campfire moves up with it (y 11)
  const C = MASSES.chimney
  chimney(c, C.x, C.z, C.top[1], C.top[2], stone)

  // Pass 2, openings: the doorway from the hall, the stair and its well, the windows
  doorway(c, OPENINGS.hallToWing.at, OPENINGS.hallToWing.height)
  const st = OPENINGS.wingStair
  stairwell(c, st.well)
  flight(c, st.flight, st.rises, P.wood, P.floor)
  railingRun(c, [[10, 5, 5], [9, 5, 5], [9, 5, 8]], P.wood)
  windows(c, 2)
  for (const [y, z] of [[2, 8], [3, 8]]) c.set(7, y, z, P.log, { axis: 'z' }) // the outshot's east window by the new doorway

  // Pass 3, depth and detail: log ends, barge boards, a pent roof over the parlour window, the woodpile moved round
  // to the pen's back
  logEnds(c, W, 2, K.plateY, (x, y, z) => x > W.x0 || z < W.z0 || z > W.z1)
  for (const z of K.roof.along) bargeTrim(c, 'z', z, inner(K.roof.across), roof.under, P.trim)
  for (const z of K.roof.along) peak(c, 9, roof.under[9] - 1, z)
  pentice(c, [[8, 0], [9, 0], [10, 0]], K.floorY, P.roof, 'south')
  woodpile(c, 12, 4, 8, P.log)

  // Pass 4, interior
  furnish(c, 2)
}

// --- Level III: the Cook Shed ---------------------------------------------------------------------------------------
function levelThree(c) {
  const K = MASSES.shed, W = K.walls, R = K.room
  // Pass 1: the Loft Pen's woodpile comes away (the shed stands on it), then plinth and base course, a stone floor,
  // the lean-to tucked under the pen's eave, log walls up to it on the fieldstone course
  for (let z = 4; z <= 8; z++) for (const y of [0, 1]) c.clear(12, y, z)
  plinth(c, W.x0, W.z0 - 1, W.x1, W.z1 + 1, stone)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, 'stone_bricks')
  const roof = catslideRoof(c, K.roof, P.roof)
  const top = x => roof.under[x] - 1
  for (let x = W.x0; x <= W.x1; x++) for (const z of [W.z0, W.z1]) {
    c.set(x, 1, z, stone)
    for (let y = 2; y <= top(x); y++) c.set(x, y, z, P.log, { axis: 'x' })
  }
  for (let z = W.z0 + 1; z < W.z1; z++) { c.set(W.x1, 1, z, stone); for (let y = 2; y <= top(W.x1); y++) c.set(W.x1, y, z, P.log, { axis: 'z' }) }
  for (const z of [W.z0, W.z1]) for (let y = 2; y <= top(W.x1); y++) c.set(W.x1, y, z, P.log, { axis: (y % 2) ? 'z' : 'x' }) // notched corners
  for (const z of K.roof.along) bargeTrim(c, 'z', z, inner(K.roof.across), roof.under, P.trim)
  if (!c.get(12, 5, 10)) c.set(12, 5, 10, P.log, { axis: 'x' }) // the pen's log ends over the shed's back verge, closed up
  // the pen's back east window now looks into the shed's roof: logs again, its shutters gone
  for (const y of [5, 6]) { c.set(11, y, 8, P.log, { axis: 'z' }); c.clear(12, y, 7) }
  // and the parlour's back east window now looks into the shed: logs again, its shutters and pot gone
  for (const y of [2, 3]) { c.set(11, y, 7, P.log, { axis: 'z' }); c.clear(12, y, 6); c.clear(12, y, 8) }
  for (const y of [1, 2]) c.clear(12, y, 7)
  // the flue: a fieldstone stack in the east wall behind the smoker, up through the slope, smoking
  chimney(c, K.flue.x, K.flue.z, 0, K.flue.top, stone)

  // Pass 2, openings: its own oak door, the doorway knocked through the pen's east wall, the windows
  const kd = OPENINGS.kitchenDoor
  door(c, kd.at, kd.facing, 'north', { wood: P.door, step: kd.step })
  doorway(c, OPENINGS.wingToKitchen.at, OPENINGS.wingToKitchen.height)
  windows(c, 3)

  // Pass 3 and 5: the lamp post at the shed door, the woodpile under the shed's low eave
  lampPost(c, ...OUTSIDE.kitchenLamp, `${P.wood}_fence`)
  woodpile(c, K.woodpile.x, K.woodpile.z0, K.woodpile.z1, P.log)

  // Pass 4, interior
  furnish(c, 3)
}

// --- Level IV: the cellar (B's, in this look's stone) ----------------------------------------------------------------
function levelFour(c) {
  const Cl = MASSES.cellar
  const inside = cellar(c, Cl.x0, Cl.z0, Cl.x1, Cl.z1, Cl.depth, stone, 'cobblestone')
  const L = OPENINGS.cellarLadder
  hatchLadder(c, L.x, L.z, -Cl.depth + 1, 0, L.facing, P.wood)
  for (const p of OUTSIDE.cellarStores.kelp) c.set(...p, 'dried_kelp_block')
  for (const p of OUTSIDE.cellarStores.hay) c.set(...p, 'hay_block', { axis: 'y' })
  for (const p of OUTSIDE.cellarStores.pumpkin) c.set(...p, 'pumpkin')
  for (const p of OUTSIDE.cellarStores.melon) c.set(...p, 'melon')
  for (let x = Cl.x0 + 1; x < Cl.x1; x++) c.set(x, Cl.beam.y, Cl.beam.z, P.log, { axis: 'x' }) // a summer beam
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
