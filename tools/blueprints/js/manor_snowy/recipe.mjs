// Noble's Manor, the snowy look (ROADMAP 49.10f): assembles plan.mjs from the parts library (../parts) in passes
// (block-out and structure, the roof, openings, depth and detail, interior, the cellar, garden and ground, snow) for
// LEVEL = 1..4, each level the one before plus what it adds (II the winter garden, III the ballroom wing, IV the
// cellar). Writes furniture.json (the furniture spots) and, for IV, out/cellar.txt. Build all four: build.sh.
//   LEVEL=3 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955   (CUT=<y>: a cutaway)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { IDS, SIZES, BASEMENT, PALETTE, MASSES, WINDOWS, BAY, OPENINGS, FURNITURE, OUTSIDE, BRACKET_LAMPS } from './plan.mjs'
import {
  LiftedCanvas, mix, stair, slab, floor, wallPanel, window, door, quoins, flight, railingRun, furnitureParts,
  cellar, hatchLadder, mullionWindow, bayWindow, stormPorch, snowLoad, snowSkirt, rakeVerge, hearthFire,
  lampPost,
} from '../parts/index.mjs'
import { unionRoof, fillUp, hangTo } from '../parts/manor_cold.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const leaves = id => [id, { persistent: 'true', distance: '1', waterlogged: 'false' }]

export function drawLevel(level) {
  const c = new LiftedCanvas(SIZES[level], BASEMENT[level] ?? 0)
  const P = PALETTE, Wg = MASSES.wing, H = MASSES.hall, T = MASSES.tower, B = MASSES.ballroom
  const has = o => (o.from ?? 1) <= level && level <= (o.until ?? 9)
  const III = level >= 3
  const base = mix(P.plinth, 7), stack = mix(P.stack, 13)
  const chequer = (x, y, z) => P.hallFloor[(x + z) & 1]
  const parquet = (x, y, z) => P.ballFloor[x & 1]
  const ring = (b, y0, y1, id) => {
    for (let x = b.x0; x <= b.x1; x++) for (let z = b.z0; z <= b.z1; z++)
      if (x === b.x0 || x === b.x1 || z === b.z0 || z === b.z1) for (let y = y0; y <= y1; y++) c.set(x, y, z, id)
  }
  const pad = (b, y, id) => floor(c, b.x0, b.z0, b.x1, b.z1, y, id)
  const roofIds = new Set(Object.values(P.roof).map(id => `minecraft:${id}`))
  const isRoof = (x, y, z) => roofIds.has(c.name(x, y, z))
  const gable = (cells, y0, id = P.wall) => fillUp(c, cells, y0, (x, y, z) => (c.get(x, y, z) ? null : id), { stop: isRoof })
  const row = (axis, line, a, b) => Array.from({ length: b - a + 1 }, (_, i) => (axis === 'x' ? [a + i, line] : [line, a + i]))

  // --- Pass 1: block-out and structure --------------------------------------------------------------------------------
  // West wing: the parlour and kitchen under two chambers, a boarded ceiling over them, the attic in the roof
  pad(Wg.walls, 0, base)
  floor(c, 2, 2, 6, 7, 0, P.floor)
  floor(c, 2, 9, 6, 14, 0, P.kitchenFloor)
  ring(Wg.walls, 1, Wg.wallTop, P.wall)
  wallPanel(c, [2, 8], [6, 8], 1, 4, P.partition)
  floor(c, 2, 2, 6, 14, Wg.upper, P.floor)
  wallPanel(c, [2, 7], [6, 7], 6, 9, P.partition)
  floor(c, 2, 2, 6, 14, Wg.ceiling, P.lining)
  // Hall: one storey under its roof, the Long Gallery's floor at y 5 over it
  pad({ x0: 8, x1: 19, z0: 5, z1: 13 }, 0, base)
  floor(c, 8, 6, 18, 12, 0, chequer)
  wallPanel(c, [8, 5], [10, 5], 1, H.wallTop, P.wall)
  wallPanel(c, [16, 5], [18, 5], 1, H.wallTop, P.wall)
  wallPanel(c, [8, 13], [18, 13], 1, H.wallTop, P.wall)
  wallPanel(c, [19, 5], [19, 13], 1, H.wallTop, P.wall)
  floor(c, 8, 6, 18, 12, 5, P.floor)
  // Porch tower: the lobby, the study over it, a boarded ceiling under the spire
  pad(T.walls, 0, base)
  floor(c, 12, 2, 14, 4, 0, P.lobbyFloor)
  ring(T.walls, 1, T.top, P.wall)
  floor(c, 12, 2, 14, 4, 5, P.floor)
  floor(c, 12, 2, 14, 4, T.ceiling, P.lining)
  // Ballroom (III): one tall room open to its collar beams, the musicians' gallery at the back
  if (III) {
    pad(B.walls, 0, base)
    floor(c, 20, 2, 26, 14, 0, parquet)
    ring(B.walls, 1, B.wallTop, P.wall)
    const G = B.gallery
    floor(c, G.x0, G.z0, G.x1, G.z1, G.y, P.floor)
  }

  // --- Pass 2: the roof: one steep slate surface over the wing, the hall and (III) the ballroom; the spire ----------
  const tower = (x, z) => !(x >= T.walls.x0 && x <= T.walls.x1 && z <= T.walls.z1)
  const stacks = MASSES.stacks
  const inStack = (x, z) => Object.values(stacks).some(k => x >= k.x0 && x <= k.x1 && z >= k.z0 && z <= k.z1)
  const keep = (x, y, z) => !inStack(x, z)
  const along = H.along[III ? 3 : 1]
  const masses = [
    { ...Wg.roof, walls: Wg.walls },
    ...(III ? [{ ...B.roof, walls: B.walls }] : []),
    { ...H.roof, along, walls: { x0: along[0], x1: III ? along[1] : 19, z0: 5, z1: 13 }, keep: tower },
  ]
  const top = unionRoof(c, masses, P.roof, { pitch: 1, keep }) // round 3: 45 degrees, so every gable is one clean stair line
  // gables and the walls carried up to the roof: the wing's two, the gallery's west wall, the hall's east gable (I-II)
  // or its party wall with the ballroom (III), the ballroom's two
  gable(row('x', 1, 1, 7), Wg.wallTop + 1)
  gable(row('x', 15, 1, 7), Wg.wallTop + 1)
  gable(row('z', 7, 5, 13), Wg.wallTop + 1)
  gable(row('z', 19, 5, 13), H.wallTop + 1)
  if (III) { gable(row('x', 1, 19, 27), B.wallTop + 1); gable(row('x', 15, 19, 27), B.wallTop + 1) }
  // round 2: no rake overhang: each gable's roof stands in the wall's own plane, so the white wall steps exactly under
  // its dark verge (a full tile under every stair) and the gable and its rake read as one slope
  const S = T.spire
  // round 3: a needle spire on the tower's walls: eaves at y 12 (two out from the middle), a second ring at y 14, a one-
  // block shaft to y 15, an end rod finial at y 16, each ring a full course over the one below (steep, no 2x2 top)
  const ringAt = (r, y) => {
    for (let dx = -r; dx <= r; dx++) for (let dz = -r; dz <= r; dz++) {
      const x = S.cx + dx, z = S.cz + dz
      if (Math.max(Math.abs(dx), Math.abs(dz)) === r) stair(c, x, y, z, P.spire.stairs, Math.abs(dx) >= Math.abs(dz) ? (dx < 0 ? 'east' : 'west') : (dz < 0 ? 'south' : 'north'))
      else { c.set(x, y, z, P.spire.full); c.set(x, y + 1, z, P.spire.full) }
    }
  }
  ringAt(2, S.y0); ringAt(1, S.y0 + 2)
  // where the hall's slope meets the tower's sides one course over its eave, a tile closes the corner
  for (const x of [T.walls.x0 - 1, T.walls.x1 + 1]) { const t = top(x, T.walls.z1); if (t && !c.get(x, t.y + 1, T.walls.z1)) c.set(x, t.y + 1, T.walls.z1, P.roof.full) }
  c.set(S.cx, S.y0 + 4, S.cz, P.finial, { facing: 'up' })

  // --- Pass 3: openings ----------------------------------------------------------------------------------------------
  const O = OPENINGS
  door(c, O.frontDoor.at, O.frontDoor.facing, 'north', { wood: P.door, step: O.frontDoor.step, stepStair: P.dressStairs })
  const A = O.arch
  for (let x = A.x0; x <= A.x1; x++) {
    for (let y = A.y0; y <= A.y1; y++) c.clear(x, y, A.z)
    c.set(x, A.y1 + 1, A.z, P.dress)
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
  for (const w of WINDOWS) if (has(w)) {
    const [y0, y1] = w.y
    const single = w.lights.length === 1 && y1 - y0 <= 2 && w.transom === undefined
    if (single) {
      const cells = []
      for (let y = y0; y <= y1; y++) cells.push(w.axis === 'x' ? [w.lights[0], y, w.line] : [w.line, y, w.lights[0]])
      window(c, cells, w.out, 'shutters', { glass: P.glass, wood: P.shutter })
      const [x, , z] = cells[0]
      const [ox, oz] = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }[w.out]
      stair(c, x + ox, y0 - 1, z + oz, P.dressStairs, { north: 'south', south: 'north', west: 'east', east: 'west' }[w.out], 'top')
      c.set(x, y1 + 1, z, P.dress)
    } else {
      mullionWindow(c, w.axis, w.line, w.lights, y0, y1, w.out, { glass: P.glass, mullion: P.dress, head: P.dress, sill: P.dressStairs, transom: w.transom })
    }
  }
  bayWindow(c, 'x', BAY.line, [BAY.x0, BAY.x1], 'north', { y0: BAY.y0, g0: BAY.g0, g1: BAY.g1, top: BAY.top },
    { pier: P.dress, apron: P.dress, glass: P.glass, lintel: P.dress, cap: P.dressSlab })

  // --- Pass 4: depth and detail --------------------------------------------------------------------------------------
  const corners = [[1, 1], [7, 1], [1, 15], [7, 15]]
  quoins(c, corners, 1, Wg.wallTop, P.quoin, P.quoinAlt)
  quoins(c, [[11, 1], [15, 1]], 1, T.top, P.quoin, P.quoinAlt)
  if (!III) quoins(c, [[19, 5], [19, 13]], 1, H.wallTop, P.quoin, P.quoinAlt)
  else quoins(c, [[19, 1], [27, 1], [19, 15], [27, 15]], 1, B.wallTop, P.quoin, P.quoinAlt)
  // grey bands at the chambers' floor (wing, tower) and under the tower's spire: only over plain wall
  const band = (b, y) => {
    for (let x = b.x0; x <= b.x1; x++) for (let z = b.z0; z <= b.z1; z++)
      if ((x === b.x0 || x === b.x1 || z === b.z0 || z === b.z1) && c.name(x, y, z) === `minecraft:${P.wall}`) c.set(x, y, z, P.band)
  }
  band(Wg.walls, Wg.upper); band(T.walls, Wg.upper); band(T.walls, T.top)
  if (III) band(B.walls, B.wallTop)
  // the storm porch before the door, a stone hood over it with snow on its crown
  const pc = MASSES.porch
  stormPorch(c, pc.x, pc.z, pc.top, { cheek: P.dress, lintel: P.collar })
  stair(c, pc.x - 1, pc.top + 1, pc.z, P.dressStairs, 'east')
  stair(c, pc.x + 1, pc.top + 1, pc.z, P.dressStairs, 'west')
  c.set(pc.x, pc.top + 1, pc.z, P.dress)
  c.set(pc.x, pc.top + 2, pc.z, P.snow, { layers: '2' })
  for (const [x, y, z, col] of OUTSIDE.banners) c.set(x, y, z, `${P.banner}_wall_banner`, { facing: 'north' })
  // the stacks: the kitchen's up the wing's west flank past its ridge, the hall's at the back
  for (const k of Object.values(stacks)) {
    for (let x = k.x0; x <= k.x1; x++) for (let z = k.z0; z <= k.z1; z++) {
      for (let y = 0; y <= k.top; y++) c.set(x, y, z, stack)
      if (x === k.flue[0] && z === k.flue[1]) c.set(x, k.top + 1, z, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
      else slab(c, x, k.top + 1, z, P.dressSlab)
    }
  }
  // the hearths (fires on slabs, in stone surrounds): the hall's in its back wall, the kitchen's in the west wall
  for (const h of MASSES.hearths) {
    const [x, y, z] = h.at
    slab(c, x, 0, z, P.hearthSlab)
    hearthFire(c, h.at, h.facing)
    c.set(x, 2, z, P.dress); c.set(x, 3, z, P.dress)
    for (const bx of h.breast ?? []) for (let yy = 1; yy <= 3; yy++) c.set(bx, yy, z, P.dress)
  }
  // III: lanterns on stone brackets along the ballroom's walls, collar beams for its chandeliers
  if (III) {
    for (const [x, y, z, side] of BRACKET_LAMPS[3]) { // the lamp faces into the room, away from its wall
      c.set(x, y + 1, z, P.dress); c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
      c.spot('lamp', [x, y, z], side === 'west' ? 'east' : 'west', [[0, 0, 0]], P.joinery)
    }
    const Cl = B.collars
    for (const z of Cl.zs) for (let x = Cl.x0; x <= Cl.x1; x++) c.set(x, Cl.y, z, P.collar, { axis: 'x' })
    railingRun(c, [[B.gallery.x0, 6, B.gallery.z0], [B.gallery.x1, 6, B.gallery.z0]], P.joinery)
  }

  // --- Pass 5: interior (the shipped furniture, cell for cell) --------------------------------------------------------
  const pieces = [...FURNITURE[1], ...(III ? FURNITURE[3] : []), ...(level >= 4 ? FURNITURE[4] : [])]
  let dug = null
  if (level >= 4) { // the cellar first: its lamps hang from the floors over it
    const C = MASSES.cellar
    dug = cellar(c, C.x0, C.z0, C.x1, C.z1, C.depth, mix(P.cellarWall, 3), P.cellarFloor)
    hatchLadder(c, C.hatch.x, C.hatch.z, -C.depth + 1, 0, C.hatch.facing, P.joinery)
    for (const z of C.ribs) {
      for (let x = C.x0 + 1; x <= C.x1 - 1; x++) c.set(x, -1, z, P.dress)
      stair(c, C.x0 + 1, -2, z, P.dressStairs, 'west', 'top')
      stair(c, C.x1 - 1, -2, z, P.dressStairs, 'east', 'top')
    }
    for (const x of [4, 10]) c.set(x, -3, 14, 'decorated_pot', { facing: 'north', waterlogged: 'false', cracked: 'false' })
    for (const [x, y, z] of C.casks) c.set(x, y, z, 'barrel', { facing: x < 7 ? 'east' : 'west', open: 'false' })
  }
  for (const f of pieces) {
    if (f.part === 'jukebox') { c.free(f.pos); c.set(...f.pos, 'jukebox', { has_record: 'false' }); continue }
    furnitureParts[f.part](c, f.part === 'hangingLamp' ? hangTo(c, f) : f)
  }

  // --- Pass 6: garden and ground --------------------------------------------------------------------------------------
  for (const [x, z] of OUTSIDE.shrubs) {
    c.set(x, 0, z, P.dress)
    c.set(x, 1, z, ...leaves(P.garden.yew))
    c.set(x, 2, z, P.snow, { layers: '2' })
  }
  for (const [x, z] of OUTSIDE.lampPosts) lampPost(c, x, 0, z, P.dressWall, 2)
  floor(c, 8, 14, 18, 15, 0, P.dress) // the terrace behind the hall
  const [rx, ry, rz] = OUTSIDE.rainButt
  c.set(rx, ry, rz, 'barrel', { facing: 'up', open: 'false' })
  if (level >= 2) garden(c, level, P)

  // --- Pass 7: snow ---------------------------------------------------------------------------------------------------
  const rakeRow = (x, z) => z <= 2 || z >= 14 || (!III && x >= 18) // round 3: the lips stop a block short of every gable (no shelf at a rake's foot)
  // at 45 degrees no tile stands under a tread, so only the true eave course (the overhang column) takes a snow lip
  const isEave = (x, y, z) => { const t = top(x, z); return !!t && t.d === 0 && t.y === y }
  const onCap = (x, z) => x >= T.walls.x0 - 1 && x <= T.walls.x1 + 1 && z <= T.walls.z1 + 1
  snowLoad(c, P.roof, P.snow, { eaves: true, tread: () => false, eaveSkip: (x, y, z) => rakeRow(x, z) || onCap(x, z) || !isEave(x, y, z) })
  snowSkirt(c, P.snow, OUTSIDE.bare)
  return { c, dug }
}

// The winter garden (II, and III's east half): calcite walls with a stone coping course and piers, snow on them; four
// knots of clipped spruce round a lamp, spruce yews at the cross, gravel walks, a bench at the end of the walk.
function garden(c, level, P) {
  const g = MASSES.garden, ge = MASSES.gardenEast, Gp = P.garden
  const III = level >= 3
  const snowOn = (x, y, z, n = '1') => c.set(x, y, z, P.snow, { layers: n })
  const wallAt = (x, z, pier) => {
    c.set(x, 0, z, P.dress)
    c.set(x, 1, z, pier ? P.dress : P.wall)
    c.set(x, 2, z, P.dress)
    if (pier) { c.set(x, 3, z, P.dress); snowOn(x, 4, z, '2') } else snowOn(x, 3, z, '2')
  }
  // the ground: lawn under snow, gravel walks on the cross
  for (let x = g.x0 + 1; x <= g.x1 - 1; x++) for (let z = 16; z <= g.z1 - 1; z++) {
    const walk = x === 13 || z === 19
    c.set(x, 0, z, walk ? Gp.path : Gp.soil, walk ? {} : { snowy: 'true' })
  }
  // the walls: west (x 7, behind the wing), south (z 24), east (x 19; at III a gate into the east half)
  for (let z = 16; z <= g.z1; z++) wallAt(g.x0, z, z === 16 || z === g.z1)
  for (let x = g.x0; x <= (III ? ge.x1 : g.x1); x++) if (x !== g.gate[0]) wallAt(x, g.z1, x === g.x0 || x === g.gate[0] - 1 || x === g.gate[0] + 1 || x === g.x1 || (III && x === ge.x1))
  for (let z = III ? 16 : 14; z <= g.z1; z++) if (!(III && z >= 18 && z <= 20)) wallAt(g.x1, z, z === 14 || z === 16 || z === g.z1 || (III && (z === 17 || z === 21)))
  if (III) for (let z = 16; z <= ge.z1; z++) wallAt(ge.x1, z, z === 16 || z === ge.z1)
  // the knots: clipped spruce hedges round a lawn, snow on the hedge tops
  const knot = (x0, x1, z0, z1) => {
    for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
      const edge = x === x0 || x === x1 || z === z0 || z === z1
      if (edge) { c.set(x, 1, z, ...leaves(Gp.hedge)); snowOn(x, 2, z) } else snowOn(x, 1, z)
    }
  }
  for (const k of OUTSIDE.knots) knot(k.x0, k.x1, k.z0, k.z1)
  for (const [x, z] of OUTSIDE.yews) { c.set(x, 1, z, ...leaves(Gp.yew)); c.set(x, 2, z, ...leaves(Gp.yew)); snowOn(x, 3, z, '2') }
  for (let x = g.x0 + 1; x <= g.x1 - 1; x++) for (let z = 16; z <= g.z1 - 1; z++)
    if (!c.get(x, 1, z) && c.name(x, 0, z) === `minecraft:${Gp.soil}`) snowOn(x, 1, z)
  const [lx, lz] = OUTSIDE.crossLamp
  lampPost(c, lx, 1, lz, P.dressWall, 2)
  // the bench at the end of the walk, facing up it (arms of trapdoors)
  const bn = OUTSIDE.bench
  for (let x = bn.x0; x <= bn.x1; x++) { c.clear(x, 1, bn.z); stair(c, x, 1, bn.z, `${Gp.bench}_stairs`, 'south') }
  // the walk from the hall's garden door (z 14..15 is the terrace) and, at III, the east half
  if (III) {
    for (let x = ge.x0 + 1; x <= ge.x1 - 1; x++) for (let z = ge.z0; z <= ge.z1 - 1; z++) {
      const walk = x === 23 || z === 19
      c.set(x, 0, z, walk ? Gp.path : Gp.soil, walk ? {} : { snowy: 'true' })
    }
    for (const z of [18, 19, 20]) c.set(g.x1, 0, z, Gp.path)
    for (const k of OUTSIDE.eastKnots) knot(k.x0, k.x1, k.z0, k.z1)
    for (let x = ge.x0 + 1; x <= ge.x1 - 1; x++) for (let z = ge.z0; z <= ge.z1 - 1; z++)
      if (!c.get(x, 1, z) && c.name(x, 0, z) === `minecraft:${Gp.soil}`) snowOn(x, 1, z)
    lampPost(c, 23, 1, 22, P.dressWall, 2)
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
