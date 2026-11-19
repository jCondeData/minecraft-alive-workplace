// Noble's Manor, the taiga look (ROADMAP 49.10f): assembles plan.mjs from the parts library (../parts) in passes
// (block-out and structure, the roof, openings, depth and detail, interior, the cellar, garden and ground) for LEVEL =
// 1..4, each level the one before plus what it adds (II the kitchen garden, III the ballroom wing, IV the cellar).
// Writes furniture.json (the furniture spots) and, for IV, out/cellar.txt. Build all four: build.sh.
//   LEVEL=3 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955   (CUT=<y>: a cutaway)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { IDS, SIZES, BASEMENT, PALETTE, MASSES, WINDOWS, BAY, OPENINGS, FURNITURE, OUTSIDE, BRACKET_LAMPS } from './plan.mjs'
import {
  LiftedCanvas, mix, stair, slab, floor, wallPanel, door, flight, railingRun, furnitureParts, cellar, hatchLadder,
  bayWindow, hearthFire, lampPost, pentPorch,
} from '../parts/index.mjs'
import { unionRoof, fillUp, hangTo, hip45 } from '../parts/manor_cold.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const leaves = id => [id, { persistent: 'true', distance: '1', waterlogged: 'false' }]
const OUT = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }
const BACK = { north: 'south', south: 'north', west: 'east', east: 'west' }

// firewood stacked lengthwise along a wall line x (one wood, the logs lying along it: bark, not a grid of ends), two
// high in the middle
function stackWood(c, x, z0, z1, log) {
  for (let z = z0; z <= z1; z++) c.set(x, 0, z, log, { axis: 'z' })
  for (let z = z0 + 1; z <= z1 - 1; z++) c.set(x, 1, z, log, { axis: 'z' })
}

export function drawLevel(level) {
  const c = new LiftedCanvas(SIZES[level], BASEMENT[level] ?? 0)
  const P = PALETTE, Wg = MASSES.wing, H = MASSES.hall, T = MASSES.tower, B = MASSES.ballroom
  const has = o => (o.from ?? 1) <= level && level <= (o.until ?? 9)
  const III = level >= 3
  const footing = mix(P.footing, 5), stack = mix(P.stack, 11)
  const chequer = (x, y, z) => P.hallFloor[(x + z) & 1]
  const parquet = (x, y, z) => P.ballFloor[x & 1]
  const pad = (b, y, id) => floor(c, b.x0, b.z0, b.x1, b.z1, y, id)
  // a log wall: logs lying along the wall between upright corner posts (a notch's crossing logs read as a checkerboard
  // of end grain on every corner in the renders: the Townhouse's and Terrace's lesson)
  const logWall = (cells, y0, y1, corners = []) => {
    const isCorner = (x, z) => corners.some(([cx, cz]) => cx === x && cz === z)
    for (const [x, z, axis] of cells) for (let y = y0; y <= y1; y++)
      c.set(x, y, z, isCorner(x, z) ? P.band : P.log, { axis: isCorner(x, z) ? 'y' : axis }) // round 2: pale stripped corner posts frame each dark mass
  }
  const ringCells = b => {
    const out = []
    for (let x = b.x0; x <= b.x1; x++) for (let z = b.z0; z <= b.z1; z++) {
      if (!(x === b.x0 || x === b.x1 || z === b.z0 || z === b.z1)) continue
      out.push([x, z, (z === b.z0 || z === b.z1) && !(x === b.x0 || x === b.x1) ? 'x' : 'z'])
    }
    return out
  }
  const cornersOf = b => [[b.x0, b.z0], [b.x1, b.z0], [b.x0, b.z1], [b.x1, b.z1]]
  const foot = cells => { for (const [x, z] of cells) { c.set(x, 0, z, footing); c.set(x, 1, z, footing) } }
  const band = (cells, y, corners) => {
    for (const [x, z, axis] of cells) if (!corners.some(([cx, cz]) => cx === x && cz === z) && c.name(x, y, z) === `minecraft:${P.log}`)
      c.set(x, y, z, P.band, { axis })
  }
  const roofIds = new Set(Object.values(P.roof).map(id => `minecraft:${id}`))
  const isRoof = (x, y, z) => roofIds.has(c.name(x, y, z))
  const gable = (cells, y0, axis) => fillUp(c, cells, y0, (x, y, z) => (c.get(x, y, z) ? null : [P.log, { axis }]), { stop: isRoof })
  const row = (axis, line, a, b) => Array.from({ length: b - a + 1 }, (_, i) => (axis === 'x' ? [a + i, line] : [line, a + i]))

  // --- Pass 1: block-out and structure: log pens on their fieldstone footing ----------------------------------------
  // West wing
  const wingRing = ringCells(Wg.walls)
  foot(wingRing)
  pad({ x0: 2, x1: 6, z0: 2, z1: 14 }, 0, footing)
  floor(c, 2, 2, 6, 7, 0, P.floor)
  floor(c, 2, 9, 6, 14, 0, P.kitchenFloor)
  logWall(wingRing, 2, Wg.wallTop, cornersOf(Wg.walls))
  wallPanel(c, [2, 8], [6, 8], 1, 4, P.partition)
  floor(c, 2, 2, 6, 14, Wg.upper, P.floor)
  wallPanel(c, [2, 7], [6, 7], 6, 9, P.partition)
  floor(c, 2, 2, 6, 14, Wg.ceiling, P.lining)
  // Hall
  const hallCells = [...row('x', 5, 8, 10).map(([x, z]) => [x, z, 'x']), ...row('x', 5, 16, 18).map(([x, z]) => [x, z, 'x']),
    ...row('x', 13, 8, 18).map(([x, z]) => [x, z, 'x']), ...row('z', 19, 5, 13).map(([x, z]) => [x, z, 'z'])]
  const hallCorners = [[19, 5], [19, 13]]
  foot(hallCells)
  floor(c, 8, 6, 18, 12, 0, chequer)
  logWall(hallCells, 2, H.wallTop, III ? [] : hallCorners)
  floor(c, 8, 6, 18, 12, 5, P.floor)
  // Porch tower: fieldstone to the study's floor, logs above
  const towerRing = ringCells(T.walls)
  foot(towerRing)
  floor(c, 12, 2, 14, 4, 0, P.lobbyFloor)
  for (const [x, z] of towerRing) for (let y = 2; y <= T.stoneTop; y++) c.set(x, y, z, footing)
  logWall(towerRing, T.stoneTop + 1, T.top, cornersOf(T.walls))
  floor(c, 12, 2, 14, 4, 5, P.floor)
  floor(c, 12, 2, 14, 4, T.ceiling, P.lining)
  // Ballroom (III)
  const ballRing = ringCells(B.walls)
  if (III) {
    foot(ballRing)
    floor(c, 20, 2, 26, 14, 0, parquet)
    logWall(ballRing, 2, B.wallTop, cornersOf(B.walls))
    const G = B.gallery
    floor(c, G.x0, G.z0, G.x1, G.z1, G.y, P.floor)
  }

  // --- Pass 2: the roof: one broad 45-degree slate surface; the tower's pyramid -------------------------------------
  const tower = (x, z) => !(x >= T.walls.x0 && x <= T.walls.x1 && z <= T.walls.z1)
  const stacks = MASSES.stacks
  const inStack = (x, z) => Object.values(stacks).some(k => x >= k.x0 && x <= k.x1 && z >= k.z0 && z <= k.z1)
  const along = H.along[III ? 3 : 1]
  const masses = [
    { ...Wg.roof, walls: Wg.walls },
    ...(III ? [{ ...B.roof, walls: B.walls }] : []),
    { ...H.roof, along, walls: { x0: along[0], x1: III ? along[1] : 19, z0: 5, z1: 13 }, keep: tower },
  ]
  const top = unionRoof(c, masses, P.roof, { pitch: 1, keep: (x, y, z) => !inStack(x, z) })
  gable(row('x', 1, 1, 7), Wg.wallTop + 1, 'x')
  gable(row('x', 15, 1, 7), Wg.wallTop + 1, 'x')
  gable(row('z', 7, 5, 13), Wg.wallTop + 1, 'z')
  gable(row('z', 19, 5, 13), H.wallTop + 1, 'z')
  if (III) { gable(row('x', 1, 19, 27), B.wallTop + 1, 'x'); gable(row('x', 15, 19, 27), B.wallTop + 1, 'x') }
  const rakes = [['x', 0, 0, 8], ['x', 16, 0, 8], ...(III ? [['x', 0, 18, 28], ['x', 16, 18, 28]] : [['z', 20, 4, 14]])]
  // round 2: a solid verge on every rake row: a full tile under each rake stair and the ridge (a two-deep slate band
  // stepping with the slope, no half-steps or hanging stairs), the log gable stopping under it
  for (const [axis, line, a, b] of rakes) for (const [x, z] of row(axis, line, a, b)) {
    const t = top(x, z)
    if (!t || c.get(x, t.y - 1, z)) continue
    if (t.ridge || t.d > 0) c.set(x, t.y - 1, z, P.roof.full)
  }
  const S = T.cap
  hip45(c, S.cx, S.cz, S.r, S.y0, P.roof)
  // where the cap overhangs the hall's slope (behind the tower and beside it), the tower's walls carried out under it
  // (upright logs at the corners, so no end grain shows)
  for (let x = S.cx - S.r; x <= S.cx + S.r; x++) for (let z = S.cz - S.r; z <= S.cz + S.r; z++) {
    const edge = Math.max(Math.abs(x - S.cx), Math.abs(z - S.cz)) === S.r
    const t = edge ? top(x, z) : null
    if (!t) continue
    const back = z === S.cz + S.r && Math.abs(x - S.cx) < S.r
    for (let y = t.y + 1; y < S.y0; y++) c.set(x, y, z, P.log, { axis: back ? 'x' : 'y' })
  }

  // --- Pass 3: openings ----------------------------------------------------------------------------------------------
  const O = OPENINGS
  door(c, O.frontDoor.at, O.frontDoor.facing, 'north', { wood: P.door, step: O.frontDoor.step, stepStair: P.capStairs })
  const A = O.arch
  for (let x = A.x0; x <= A.x1; x++) {
    for (let y = A.y0; y <= A.y1; y++) c.clear(x, y, A.z)
    c.set(x, A.y1 + 1, A.z, P.band, { axis: 'x' })
  }
  for (const d of [O.parlourDoor, O.kitchenDoor, O.frontChamberDoor, O.greatChamberDoor, O.studyDoor, O.gardenDoor])
    door(c, d.at, d.facing, d.facing, { wood: P.door })
  if (III) {
    for (const d of O.ballroomDoors) door(c, d.at, d.facing, d.facing, { wood: P.door, hinge: d.hinge })
    door(c, O.galleryDoor.at, O.galleryDoor.facing, 'west', { wood: P.door })
    door(c, O.ballroomGardenDoor.at, O.ballroomGardenDoor.facing, 'south', { wood: P.door })
  }
  const G = O.grandStair
  for (const p of G.well) c.clear(...p)
  flight(c, G.flight, G.rises, P.joinery, P.partition)
  railingRun(c, O.railing, P.joinery)
  // windows: panes in the logs, stripped-log mullions, lintel and transom, a spruce sill
  for (const w of WINDOWS) if (has(w)) {
    const [y0, y1] = w.y
    const lo = Math.min(...w.lights), hi = Math.max(...w.lights)
    const at = (u, y) => (w.axis === 'x' ? [u, y, w.line] : [w.line, y, u])
    const along = w.axis === 'x' ? 'x' : 'z'
    const [ox, oz] = OUT[w.out]
    for (let u = lo; u <= hi; u++) {
      for (let y = y0; y <= y1; y++) {
        if (y === w.transom) c.set(...at(u, y), P.band, { axis: along })
        else if (w.lights.includes(u)) c.set(...at(u, y), P.glass)
        else c.set(...at(u, y), P.band, { axis: 'y' })
      }
      c.set(...at(u, y1 + 1), P.band, { axis: along })
      const [x, y, z] = at(u, y0 - 1)
      stair(c, x + ox, y, z + oz, P.sill, BACK[w.out], 'top')
    }
  }
  bayWindow(c, 'x', BAY.line, [BAY.x0, BAY.x1], 'north', { y0: BAY.y0, g0: BAY.g0, g1: BAY.g1, top: BAY.top },
    { pier: P.post, apron: P.deck, glass: P.glass, lintel: P.band, cap: P.roof.slab })

  // --- Pass 4: depth and detail --------------------------------------------------------------------------------------
  // the stripped band at the chambers' floor; log ends past the notched corners
  band(wingRing, Wg.upper, cornersOf(Wg.walls))
  // round 2: the tower's log storey framed like the gables: stripped bands at its floor and its ceiling line
  band(towerRing, T.stoneTop + 1, cornersOf(T.walls)); band(towerRing, T.ceiling, cornersOf(T.walls))
  if (III) band(ballRing, 5, cornersOf(B.walls))
  // (the corners are notched by the logs crossing course by course: no loose log ends past them, which read as a grid)
  // the verandas: a spruce deck, log posts, a slate lean-to running up under the hall's eave, lanterns, ferns, a bench
  for (const v of MASSES.verandas) {
    for (let x = v.x0; x <= v.x1; x++) {
      for (let z = 3; z <= 4; z++) c.set(x, 0, z, P.deck)
      stair(c, x, 4, 3, P.roof.stairs, 'south')
      c.set(x, 4, 4, P.roof.full); c.set(x, 5, 4, P.roof.full) // the lean-to closed up to the hall's eave
    }
    for (const x of [v.x0, v.x1]) for (let y = 1; y <= 3; y++) c.set(x, y, 3, P.post, { axis: 'y' })
    const mx = (v.x0 + v.x1) / 2
    c.set(mx, 3, 3, 'lantern', { hanging: 'true', waterlogged: 'false' })
    stair(c, mx, 1, 4, `${P.joinery}_stairs`, 'south')
  }
  for (const [x, z] of OUTSIDE.fernPots) c.set(x, 1, z, P.fernPot)
  // the front door's pent on two log posts
  const dp = MASSES.doorPent
  pentPorch(c, { x0: dp.x0, x1: dp.x1, z: dp.z, y: dp.y, toward: 'south', posts: dp.posts, lamps: [13] },
    { roof: P.roof, post: P.post, bracket: P.bracket })
  for (const [x, y, z] of OUTSIDE.banners) c.set(x, y, z, `${P.banner}_wall_banner`, { facing: 'north' })
  // the fieldstone stacks
  for (const k of Object.values(stacks)) {
    for (let x = k.x0; x <= k.x1; x++) for (let z = k.z0; z <= k.z1; z++) {
      for (let y = 0; y <= k.top; y++) c.set(x, y, z, stack)
      if (x === k.flue[0] && z === k.flue[1]) c.set(x, k.top + 1, z, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
      else slab(c, x, k.top + 1, z, P.cap)
    }
  }
  for (const h of MASSES.hearths) {
    const [x, , z] = h.at
    slab(c, x, 0, z, P.hearthSlab)
    hearthFire(c, h.at, h.facing)
    c.set(x, 2, z, stack); c.set(x, 3, z, stack)
    for (const bx of h.breast ?? []) for (let yy = 1; yy <= 3; yy++) c.set(bx, yy, z, stack)
  }
  if (III) {
    for (const [x, y, z, side] of BRACKET_LAMPS[3]) {
      c.set(x, y + 1, z, P.band, { axis: 'x' }); c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
      c.spot('lamp', [x, y, z], side === 'west' ? 'east' : 'west', [[0, 0, 0]], P.joinery)
    }
    railingRun(c, [[B.gallery.x0, 6, B.gallery.z0], [B.gallery.x1, 6, B.gallery.z0]], P.joinery)
  }
  for (const f of OUTSIDE.firewood) stackWood(c, f.x, f.z0, f.z1, P.firewood)

  // --- Pass 5: interior (the shipped furniture, cell for cell) --------------------------------------------------------
  const pieces = [...FURNITURE[1], ...(III ? FURNITURE[3] : []), ...(level >= 4 ? FURNITURE[4] : [])]
  let dug = null
  if (level >= 4) {
    const C = MASSES.cellar
    dug = cellar(c, C.x0, C.z0, C.x1, C.z1, C.depth, mix(P.cellarWall, 3), P.cellarFloor)
    hatchLadder(c, C.hatch.x, C.hatch.z, -C.depth + 1, 0, C.hatch.facing, P.joinery)
    for (const z of C.beams) for (let x = C.x0 + 1; x <= C.x1 - 1; x++) c.set(x, -1, z, P.beam, { axis: 'x' })
    for (const x of [4, 10]) c.set(x, -3, 14, 'decorated_pot', { facing: 'north', waterlogged: 'false', cracked: 'false' })
    for (const [x, y, z] of C.casks) c.set(x, y, z, 'barrel', { facing: x < 7 ? 'east' : 'west', open: 'false' })
  }
  for (const f of pieces) {
    if (f.part === 'jukebox') { c.free(f.pos); c.set(...f.pos, 'jukebox', { has_record: 'false' }); continue }
    furnitureParts[f.part](c, f.part === 'hangingLamp' ? hangTo(c, f) : f)
  }

  // --- Pass 6: garden and ground --------------------------------------------------------------------------------------
  floor(c, 8, 14, 18, 15, 0, P.kitchenFloor) // the flagged yard behind the hall
  const [rx, ry, rz] = OUTSIDE.rainButt
  c.set(rx, ry, rz, 'barrel', { facing: 'up', open: 'false' })
  if (level >= 2) garden(c, level, P)
  return { c, dug }
}

// The kitchen garden (II, and III's east half): a spruce fence on a mossy footing with a gate on the walk; four beds of
// sweet berries and ferns kerbed in stripped logs round a lantern post, little firs at the cross, coarse-dirt walks, a
// bench at the end of the walk, firewood stacked outside the west fence.
function garden(c, level, P) {
  const g = MASSES.garden, ge = MASSES.gardenEast, Gp = P.garden
  const III = level >= 3
  const footing = mix(P.footing, 9)
  const fenceAt = (x, z) => { c.set(x, 0, z, footing); c.set(x, 1, z, P.fence) }
  const lawn = (x0, x1, z0, z1, walk) => {
    for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) c.set(x, 0, z, walk(x, z) ? Gp.path : Gp.soil, walk(x, z) ? {} : { snowy: 'false' })
  }
  lawn(g.x0 + 1, g.x1 - 1, 16, g.z1 - 1, (x, z) => x === 13 || z === 19)
  for (let z = 16; z <= g.z1; z++) fenceAt(g.x0, z)
  for (let x = g.x0; x <= (III ? ge.x1 : g.x1); x++) fenceAt(x, g.z1)
  c.set(g.gate[0], 0, g.gate[1], Gp.path)
  c.set(g.gate[0], 1, g.gate[1], P.gate, { facing: 'south', open: 'false', in_wall: 'false', powered: 'false' })
  for (let z = III ? 16 : 14; z <= g.z1; z++) fenceAt(g.x1, z)
  if (III) {
    c.set(g.x1, 0, 19, Gp.path)
    c.set(g.x1, 1, 19, P.gate, { facing: 'east', open: 'false', in_wall: 'false', powered: 'false' })
    for (let z = 16; z <= ge.z1; z++) fenceAt(ge.x1, z)
  }
  const bed = (x0, x1, z0, z1) => {
    for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
      const edge = x === x0 || x === x1 || z === z0 || z === z1
      if (edge) c.set(x, 0, z, Gp.kerb, { axis: z === z0 || z === z1 ? 'x' : 'z' })
      else c.set(x, 1, z, (x + z) % 2 ? 'sweet_berry_bush' : 'fern', (x + z) % 2 ? { age: '3' } : {})
    }
  }
  for (const k of OUTSIDE.knots) bed(k.x0, k.x1, k.z0, k.z1)
  for (const [x, z] of OUTSIDE.yews) { c.set(x, 1, z, ...leaves(Gp.fir)); c.set(x, 2, z, ...leaves(Gp.fir)) }
  const [lx, lz] = OUTSIDE.crossLamp
  lampPost(c, lx, 1, lz, P.fence, 2)
  const bn = OUTSIDE.bench
  for (let x = bn.x0; x <= bn.x1; x++) stair(c, x, 1, bn.z, `${Gp.bench}_stairs`, 'south')
  stackWood(c, g.x0 - 1, 17, 22, P.firewood)
  if (III) {
    lawn(ge.x0 + 1, ge.x1 - 1, ge.z0, ge.z1 - 1, (x, z) => x === 23 || z === 19)
    for (const k of OUTSIDE.eastKnots) bed(k.x0, k.x1, k.z0, k.z1)
    lampPost(c, 23, 1, 17, P.fence, 2)
  }
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
