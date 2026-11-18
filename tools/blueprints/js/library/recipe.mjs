// Library (the Lamplit Nave, ROADMAP 49), the recipe: assembles plan.mjs from the shared parts library (../parts) in
// passes (block-out and structure, roofs and gables, openings, depth and detail, interior, the archive) for LEVEL =
// 1..4, each level the one before plus what it adds (II the Aisle, III the Enchanting Tower, IV the Archive). Writes
// furniture.json (and out/cellar.txt for IV). Build all four: build.sh.  LEVEL=3 CUT=<y> node ... for a cutaway.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { IDS, SIZES, BASEMENT, PALETTE, MASSES, JOBS, ENCHANT, SHELVES, OPENINGS, FURNITURE, STORES, ARCHIVE_LAMPS } from './plan.mjs'
import {
  LiftedCanvas, mix, stringCourse, stair, slab, plinth, floor, wallPanel, window, door, gableRoof, gableWall, bargeTrim, cellar,
  buttress, shelfWall, enchantRing, boxPyramidRoof, lancet, hangingPot, furnitureParts,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function drawLevel(level) {
  const c = new LiftedCanvas(SIZES[level], BASEMENT[level])
  const P = PALETTE
  const wall = mix(P.wall, 11), base = mix(P.plinth, 4)
  const N = MASSES.nave, A = MASSES.aisle, T = MASSES.tower
  const nw = N.walls, nr = N.room
  const empty = (x, y, z) => !c.get(x, y, z)

  // --- Pass 1: block-out and structure --------------------------------------------------------------------------
  // Nave: a dark plinth, tall stone walls, dark boards
  plinth(c, nw.x0, nw.z0, nw.x1, nw.z1, base)
  floor(c, nr.x0, nr.z0, nr.x1, nr.z1, 0, P.floor)
  wallPanel(c, [nw.x0, nw.z0], [nw.x1, nw.z0], 1, N.wallTop, wall)
  wallPanel(c, [nw.x0, nw.z1], [nw.x1, nw.z1], 1, N.wallTop, wall)
  wallPanel(c, [nw.x0, nw.z0], [nw.x0, nw.z1], 1, N.wallTop, wall)
  wallPanel(c, [nw.x1, nw.z0], [nw.x1, nw.z1], 1, N.wallTop, wall)
  for (const [x, z] of [[nw.x0, nw.z0], [nw.x1, nw.z0], [nw.x0, nw.z1], [nw.x1, nw.z1]]) for (let y = 1; y <= N.wallTop; y++) c.set(x, y, z, y % 2 ? P.dressing : 'stone_bricks')
  stringCourse(c, nw.x0, nw.z0, nw.x1, nw.z1, 3, P.band)
  // tie beams across the hall at the wall head
  for (const z of N.tieBeams) for (let x = nr.x0; x <= nr.x1; x++) c.set(x, N.beamY, z, P.beam, { axis: 'x' })

  if (level >= 2) { // Aisle: a lower cross wing off the east wall, set back a block from the front
    const aw = A.walls
    plinth(c, aw.x0 + 1, aw.z0, aw.x1, aw.z1, base)
    floor(c, A.room.x0, A.room.z0, A.room.x1, A.room.z1, 0, P.floor)
    wallPanel(c, [aw.x0 + 1, aw.z0], [aw.x1, aw.z0], 1, A.wallTop, wall)
    wallPanel(c, [aw.x0 + 1, aw.z1], [aw.x1, aw.z1], 1, A.wallTop, wall)
    wallPanel(c, [aw.x1, aw.z0], [aw.x1, aw.z1], 1, A.wallTop, wall)
    for (const z of [aw.z0, aw.z1]) for (let y = 1; y <= A.wallTop; y++) c.set(aw.x1, y, z, y % 2 ? P.dressing : 'stone_bricks')
    stringCourse(c, aw.x0 + 1, aw.z0, aw.x1, aw.z1, 3, P.band, (x, z) => x === aw.x0 + 1 && z > aw.z0 && z < aw.z1)
  }

  if (level >= 3) { // Enchanting tower: a tall square room behind the nave, its own wall a block behind the nave's
    const tw = T.walls
    plinth(c, tw.x0, tw.z0, tw.x1, tw.z1, base)
    floor(c, T.room.x0, T.room.z0, T.room.x1, T.room.z1, 0, P.floor)
    for (const [a, b] of [[[tw.x0, tw.z0], [tw.x1, tw.z0]], [[tw.x0, tw.z1], [tw.x1, tw.z1]], [[tw.x0, tw.z0], [tw.x0, tw.z1]], [[tw.x1, tw.z0], [tw.x1, tw.z1]]])
      wallPanel(c, a, b, 1, T.wallTop, wall)
    for (const [x, z] of [[tw.x0, tw.z1], [tw.x1, tw.z1], [tw.x0, tw.z0], [tw.x1, tw.z0]]) for (let y = 1; y <= T.wallTop; y++) c.set(x, y, z, y % 2 ? P.dressing : 'stone_bricks')
    for (const z of T.tieBeams) for (let x = T.room.x0; x <= T.room.x1; x++) c.set(x, T.beamY, z, P.beam, { axis: 'x' })
    // a band of dark brick at the tower's eaves
    for (let x = tw.x0; x <= tw.x1; x++) for (const z of [tw.z0, tw.z1]) c.set(x, T.wallTop, z, P.pier)
    for (let z = tw.z0; z <= tw.z1; z++) for (const x of [tw.x0, tw.x1]) c.set(x, T.wallTop, z, P.pier)
  }

  // --- Pass 2: roofs and gables ---------------------------------------------------------------------------------
  if (level >= 3) {
    const apex = boxPyramidRoof(c, T.roof, P.roof, empty)
    c.set(apex[0], apex[1] + 1, apex[2], P.post) // the finial
  }
  const naveRoof = { ...N.roof, along: [0, level >= 3 ? nw.z1 : N.roof.along[1]] }
  const main = gableRoof(c, naveRoof, P.roof, empty)
  const gable = (line) => gableWall(c, 'z', line, [nw.x0, nw.x1], N.wallTop + 1, main.under, () => wall)
  gable(nw.z0); gable(nw.z1)
  bargeTrim(c, 'z', 0, N.roof.across, main.under, P.pierCap)
  if (level < 3) bargeTrim(c, 'z', N.roof.along[1], N.roof.across, main.under, P.pierCap)
  if (level >= 2) {
    const wing = gableRoof(c, A.roof, P.roof, empty)
    gableWall(c, 'x', A.walls.x1, A.roof.across, A.wallTop + 1, wing.under, () => wall)
    // where the wing's roof meets the nave's slope: close the gap between them with the nave's own tiles
    gableWall(c, 'x', nw.x1 + 1, A.roof.across, A.wallTop + 1, wing.under, (u, y) => (c.get(nw.x1 + 1, y, u) ? null : P.roof.full))
  }

  // --- Pass 3: openings -----------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: P.sill })
  for (const [x, y] of OPENINGS.rose) c.set(x, y, nw.z0, P.glass)
  for (const [x, y] of [[5, 8], [7, 8], [4, 9], [8, 9], [5, 10], [7, 10], [6, 7], [6, 11]]) c.set(x, y, nw.z0, P.pier) // the rose's dark ring
  for (const [x, z] of OPENINGS.frontLancets.cells) lancet(c, [[x, z]], ...OPENINGS.frontLancets.y, P.glass)
  for (const [za, zb] of OPENINGS.sideWindows.zs) for (const [x, out] of [[nw.x0, 'west'], [nw.x1, 'east']]) {
    if (level >= 2 && x === nw.x1 && za >= A.walls.z0 && zb <= A.walls.z1) continue // the aisle's side: inside now
    const [y0, y1] = OPENINGS.sideWindows.y
    window(c, [[x, y0, za], [x, y0, zb], [x, y1, za], [x, y1, zb]], out, 'sill', { glass: P.glass, sill: P.sill })
  }
  if (level < 3) for (const [x, z] of OPENINGS.backWindow.cells) lancet(c, [[x, z]], ...OPENINGS.backWindow.y, P.glass)
  if (level >= 2) {
    const ar = A.arch
    for (let z = ar.z0; z <= ar.z1; z++) for (let y = 1; y <= ar.top; y++) c.clear(ar.x, y, z)
    for (let z = ar.z0; z <= ar.z1; z++) c.set(ar.x, ar.top + 1, z, P.dressing)
    window(c, OPENINGS.aisleFront.cells.flatMap(([x, z]) => [[x, 2, z], [x, 3, z]]), 'north', 'sill', { glass: P.glass, sill: P.sill, pots: ['potted_cornflower', 'potted_cornflower'] })
    const ag = OPENINGS.aisleGable
    for (const [x, z] of ag.cells) lancet(c, [[x, z]], ...ag.y, P.glass)
    for (const [x, y, z] of ag.frame) c.set(x, y, z, P.pier) // a dark surround, a kneeler course at each eave
    for (const z of [A.walls.z0, A.walls.z1]) stair(c, A.walls.x1, A.wallTop + 1, z, P.pierCap, z === A.walls.z0 ? 'south' : 'north')
  }
  if (level >= 3) {
    for (const x of T.ways) for (const z of [nw.z1, T.walls.z0]) for (let y = 1; y <= 2; y++) c.clear(x, y, z)
    for (const cells of OPENINGS.towerLancets) lancet(c, cells, 3, 8, P.glass)
  }

  // --- Pass 4: depth and detail ---------------------------------------------------------------------------------
  // buttresses down both long walls of the nave (the aisle takes the east ones it covers)
  for (const z of N.buttresses) {
    buttress(c, nw.x0 - 1, z, 0, 3, P.pier, P.pierCap, 'east')
    if (!(level >= 2 && z > A.walls.z0 && z < A.walls.z1)) buttress(c, nw.x1 + 1, z, 0, 3, P.pier, P.pierCap, 'west')
  }
  if (level >= 2) buttress(c, A.walls.x1 + 0, A.walls.z0 - 1, 0, 2, P.pier, P.pierCap, 'south')
  if (level >= 3) for (const x of [T.walls.x0 - 1, T.walls.x1 + 1]) for (const z of [13, 17])
    buttress(c, x, z, 0, 8, P.pier, P.pierCap, x < 6 ? 'east' : 'west')
  // the porch: two dark posts a block either side of the door, a three-course gabled hood, lanterns hung under it
  const po = OPENINGS.portal, [p0, p1] = po.cols, mid = fd.at[0]
  for (let x = p0; x <= p1; x++) if (x !== mid) c.set(x, 0, po.z, P.pier)
  for (const x of po.cols) for (let y = 1; y <= po.top; y++) c.set(x, y, po.z, P.post)
  for (let k = 0; k <= (p1 - p0) / 2; k++) {
    const y = po.top + 1 + k, a = p0 + k, b = p1 - k
    if (a === b) { slab(c, a, y, po.z, P.roof.slab); break }
    stair(c, a, y, po.z, P.roof.stairs, 'east'); stair(c, b, y, po.z, P.roof.stairs, 'west')
    for (let x = a + 1; x < b; x++) c.set(x, y, po.z, P.roof.full)
  }
  stair(c, mid, po.top, po.z, `${P.wood}_stairs`, 'south', 'top') // the porch's lintel
  for (const x of [p0 + 1, p1 - 1]) furnitureParts.hangingLamp(c, { pos: [x, po.top, po.z], facing: 'north', chain: 0, wood: P.wood })
  // the back gable (I and II): buttresses carried round, two sill windows either side of the banner
  if (level < 3) {
    for (const x of OPENINGS.backButtresses) buttress(c, x, nw.z1 + 1, 0, 3, P.pier, P.pierCap, 'north')
    for (const x of OPENINGS.backWindows) window(c, [[x, 4, nw.z1], [x, 5, nw.z1]], 'south', 'sill', { glass: P.glass, sill: P.sill })
  }

  // --- Pass 5: interior: shelves, lecterns, the enchanting ring, furniture ------------------------------------------
  for (const s of SHELVES) if (s.level <= level && s.level !== 4) shelfWall(c, s.cells, s.y[0], s.y[1], P.shelf)
  for (const j of JOBS) if (j.level <= level) c.set(...j.at, P.job, { facing: j.facing, has_book: 'false', powered: 'false' })
  if (level >= 3) enchantRing(c, ENCHANT.at, P.shelf, P.table, ENCHANT.gap)
  for (const f of FURNITURE) {
    if (f.level > level || (f.until && level > f.until)) continue
    if (f.part === 'hangingPot') hangingPot(c, f)
    else furnitureParts[f.part](c, f)
  }
  for (const s of STORES) if (s.level <= level && s.at[1] >= 0) c.set(...s.at, P.store, { facing: s.facing, type: 'single', waterlogged: 'false' })

  // --- Pass 6: the archive (IV) ---------------------------------------------------------------------------------
  let dug = null
  if (level >= 4) {
    const V = MASSES.archive
    dug = cellar(c, V.x0, V.z0, V.x1, V.z1, V.depth, base, P.archiveFloor)
    for (const s of SHELVES) if (s.level === 4) shelfWall(c, s.cells, s.y[0], s.y[1], P.shelf)
    for (const s of STORES) if (s.at[1] < 0) c.set(...s.at, P.store, { facing: s.facing, type: 'single', waterlogged: 'false' })
    for (const p of ARCHIVE_LAMPS) furnitureParts.hangingLamp(c, { pos: p, facing: 'north', chain: 0, wood: P.wood })
    const L = V.ladder
    for (let y = -V.depth + 1; y <= 0; y++) c.set(L.x, y, L.z, 'ladder', { facing: L.facing, waterlogged: 'false' })
    c.set(L.x + 1, 0, L.z, P.pier) // the ladder's back at the floor layer
  }
  return { c, dug }
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const { c, dug } = drawLevel(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  fs.mkdirSync(path.join(here, 'out'), { recursive: true })
  if (dug) fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), dug.join(','))
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: `${IDS[level]}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
