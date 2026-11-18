// Storehouse (Version A), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes (block-out
// and structure, roofs and gables, openings, depth and detail, stores and lamps, vault and ground) for LEVEL = 1..3,
// each level the one before plus what it adds (II the Cart Shed, III the Back Range). Writes furniture.json and
// out/cellar.txt. Build all three: build.sh.  LEVEL=2 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs ... (CUT=<y>)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { IDS, SIZES, BASEMENT, PALETTE, MASSES, OPENINGS, DESK, STORES, FURNITURE, GOODS } from './plan.mjs'
import {
  LiftedCanvas, mix, plinth, floor, post, wallPanel, window, doorway, gableRoof, gableWall, bargeTrim, quoins,
  stringCourse, cellar, hatchLadder, hoistBeam, door, stair, dormer, louvre, chestRack, tallyDesk, furnitureParts,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function drawLevel(level) {
  const c = new LiftedCanvas(SIZES[level], BASEMENT)
  const P = PALETTE
  const stone = mix(P.stone, 5)
  const H = MASSES.hoist, S = MASSES.shed, R = MASSES.range
  const hw = H.walls, hr = H.room

  // --- Pass 1: block-out and structure --------------------------------------------------------------------------
  // Hoist House: a stone plinth, brick walls up through the loft's knee, a stone band at the loft floor
  plinth(c, hw.x0, hw.z0, hw.x1, hw.z1, stone)
  floor(c, hr.x0, hr.z0, hr.x1, hr.z1, 0, P.floor)
  for (const [a, b] of [[[hw.x0, hw.z0], [hw.x1, hw.z0]], [[hw.x0, hw.z1], [hw.x1, hw.z1]], [[hw.x0, hw.z0], [hw.x0, hw.z1]], [[hw.x1, hw.z0], [hw.x1, hw.z1]]])
    wallPanel(c, a, b, 1, 5, P.brick)
  stringCourse(c, hw.x0, hw.z0, hw.x1, hw.z1, H.loftY, stone)
  floor(c, hr.x0, hr.z0, hr.x1, hr.z1, H.loftY, P.floor)
  quoins(c, [[hw.x0, hw.z0], [hw.x1, hw.z0], [hw.x0, hw.z1], [hw.x1, hw.z1]], 1, 5, P.dressing, P.dressingAlt)

  if (level >= 2) { // Cart Shed: the lower gable beside, set back a block, open bays to the street
    const sw = S.walls
    plinth(c, sw.x0 + 1, sw.z0, sw.x1, sw.z1, stone)
    floor(c, S.room.x0, sw.z0, S.room.x1, S.room.z1, 0, P.shedFloor)
    floor(c, S.room.x0, sw.z0 - 1, sw.x1, sw.z0 - 1, 0, P.shedFloor) // the apron under the hood
    wallPanel(c, [sw.x1, sw.z0], [sw.x1, sw.z1], 1, 4, P.brick)
    wallPanel(c, [sw.x0 + 1, sw.z1], [sw.x1, sw.z1], 1, 4, P.brick)
    quoins(c, [[sw.x1, sw.z1]], 1, 4, P.dressing, P.dressingAlt)
    for (const x of S.posts) post(c, x, sw.z0, 1, 3, P.frame)
    for (let x = sw.x0 + 1; x <= sw.x1; x++) c.set(x, 4, sw.z0, P.frame, { axis: 'x' }) // the bressummer over the bays
    c.set(sw.x1, 4, sw.z0, P.frame, { axis: 'y' })
  }
  if (level >= 3) { // Back Range: across both, the passage under its front eave
    const rw = R.walls
    plinth(c, rw.x0, R.passageZ, rw.x1, rw.z1, stone)
    floor(c, R.room.x0, R.passageZ, R.room.x1, R.room.z1, 0, P.floor)
    wallPanel(c, [rw.x0, rw.z1], [rw.x1, rw.z1], 1, 4, P.brick)
    wallPanel(c, [rw.x0 + 1, rw.z0], [rw.x1 - 1, rw.z0], 1, 4, P.brick)
    stringCourse(c, rw.x0, rw.z0, rw.x1, rw.z1, 4, stone, (x, z) => z === rw.z0 && x > rw.x0 && x < rw.x1)
    quoins(c, [[rw.x0, rw.z1], [rw.x1, rw.z1]], 1, 3, P.dressing, P.dressingAlt)
  }

  // --- Pass 2: roofs and gables ---------------------------------------------------------------------------------
  const main = gableRoof(c, H.roof, P.roof)
  gableWall(c, 'z', hw.z0, [hw.x0, hw.x1], 6, main.under, () => P.brick)
  gableWall(c, 'z', hw.z1, [hw.x0, hw.x1], 6, main.under, () => P.brick)
  bargeTrim(c, 'z', H.roof.along[0], H.roof.across, main.under, `${P.trim}_slab`)
  bargeTrim(c, 'z', H.roof.along[1], H.roof.across, main.under, `${P.trim}_slab`)
  if (level >= 2) {
    const wing = gableRoof(c, S.roof, P.roof, (x, y, z) => !c.get(x, y, z))
    gableWall(c, 'z', S.walls.z0, [S.walls.x0 + 1, S.walls.x1], 5, wing.under, (u, y) => (c.get(u, y, S.walls.z0) ? null : P.brick))
    gableWall(c, 'z', S.walls.z1, [S.walls.x0 + 1, S.walls.x1], 5, wing.under, (u, y) => (c.get(u, y, S.walls.z1) ? null : P.brick))
    bargeTrim(c, 'z', S.roof.along[0], [S.roof.across[0] + 1, S.roof.across[1]], wing.under, `${P.trim}_stairs`)
  }
  if (level >= 3) {
    const back = gableRoof(c, R.roof, P.roof, (x, y, z) => !c.get(x, y, z))
    for (const x of [R.walls.x0, R.walls.x1])
      gableWall(c, 'x', x, [R.passageZ, R.walls.z1], 1, back.under, (u, y) => (c.get(x, y, u) ? null : P.brick))
    bargeTrim(c, 'x', R.roof.along[0], [R.roof.across[0] + 1, R.roof.across[1]], back.under, `${P.trim}_stairs`)
    bargeTrim(c, 'x', R.roof.along[1], [R.roof.across[0] + 1, R.roof.across[1]], back.under, `${P.trim}_stairs`)
    for (const x of R.posts) post(c, x, R.walls.z0 + 1, 1, 3, P.frame) // the range's two inner posts under its beam
    for (let x = R.room.x0; x <= R.room.x1; x++) if (!c.get(x, 4, R.walls.z0 + 1)) c.set(x, 4, R.walls.z0 + 1, P.frame, { axis: 'x' })
  }

  // --- Pass 3: openings -----------------------------------------------------------------------------------------
  const A = OPENINGS.cartArch
  c.clearBox(A.x0, A.y0, A.z, A.x1, A.y1, A.z)
  for (let x = A.x0; x <= A.x1; x++) { c.set(x, 0, A.z, P.dressing); stair(c, x, 0, A.z - 1, P.dressingStairs, 'south') }
  for (let x = A.x0 - 1; x <= A.x1 + 1; x++) c.set(x, 4, A.z, P.frame, { axis: 'x' }) // the lintel, through the band
  // the wagon gate: two board leaves across the arch with a wicket door in the middle (a cart takes the whole gate)
  const mid = (A.x0 + A.x1) / 2
  for (let x = A.x0; x <= A.x1; x++) for (let y = A.y0; y <= A.y1; y++) if (x !== mid || y === A.y1) c.set(x, y, A.z, P.gate, { axis: 'y' })
  door(c, [mid, A.y0, A.z], 'south', 'north', { wood: P.wood })
  window(c, OPENINGS.hatch.cells, 'north', 'shutters', { glass: P.glass, wood: P.wood }) // the loading hatch, doors folded back
  for (const w of OPENINGS.windows) {
    if (w.level > level || (w.until && level > w.until)) continue
    window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, sill: P.dressingStairs })
  }
  if (level >= 2) { // the Cart Shed's two bays: a wagon gate like the Hoist House's, and a glazed bay under a brick sill
    const z = S.walls.z0
    for (let x = 10; x <= 12; x++) for (let y = 1; y <= 3; y++) if (x !== 11 || y === 3) c.set(x, y, z, P.gate, { axis: 'y' })
    door(c, [11, 1, z], 'south', 'north', { wood: P.wood })
    c.set(11, 0, z, P.dressing)
    for (let x = 14; x <= 15; x++) c.set(x, 1, z, P.brick)
    window(c, [[14, 2, z], [15, 2, z], [14, 3, z], [15, 3, z]], 'north', null, { glass: P.glass })
  }
  for (const d of OPENINGS.doorways) if (d.level <= level) doorway(c, d.at)
  const L = OPENINGS.loftLadder
  c.clear(L.x, H.loftY, L.z)
  for (let y = L.y0; y <= L.y1; y++) c.set(L.x, y, L.z, 'ladder', { facing: L.facing, waterlogged: 'false' })

  // --- Pass 4: depth and detail ---------------------------------------------------------------------------------
  const h = OPENINGS.hoist
  hoistBeam(c, h.at, 'north', { log: P.frame, bracket: `${P.trim}_stairs`, reach: h.reach, drop: h.drop })
  for (let z = h.at[2] - h.reach; z < h.at[2]; z++) c.set(h.at[0], h.at[1] + 1, z, P.roof.full) // the hood closed over the beam

  // pilasters: dark oak posts proud of the flat brick on the long sides and the backs, each on a stone footing, and a
  // plinth course of stone stairs one out at the foot of those walls (round 2, the critic's note 1)
  const pilasters = [] // [x, z, top, wall side the course faces]
  const run = (cells, top, facing) => cells.forEach(([x, z]) => pilasters.push([x, z, top, facing]))
  run([[0, 4], [0, 9]], 4, 'east')
  if (level === 1) run([[10, 4], [10, 9]], 4, 'west')
  if (level < 3) run([[3, 12], [7, 12]], 4, 'north')
  if (level === 2) run([[11, 12], [14, 12]], 3, 'north')
  if (level >= 2) run([[17, 4], [17, 10]], 3, 'west')
  if (level >= 3) run([[3, 18], [6, 18], [10, 18], [13, 18]], 3, 'north')
  const course = (x0, x1, z0, z1, facing) => { for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) if (!c.get(x, 0, z)) stair(c, x, 0, z, P.dressingStairs, facing) }
  for (const [x, z, top] of pilasters) { c.set(x, 0, z, P.dressing); post(c, x, z, 1, top, P.frame) }
  course(0, 0, 2, level >= 3 ? 11 : 11, 'east')
  if (level === 1) course(10, 10, 2, 11, 'west')
  if (level < 3) course(1, level >= 2 ? 16 : 9, 12, 12, 'north')
  if (level >= 2) course(17, 17, 3, 11, 'west')
  if (level >= 3) { course(1, 16, 18, 18, 'north'); course(0, 0, 12, 17, 'east'); course(17, 17, 12, 17, 'west') }
  // dormers on both long slopes of the Hoist House (note 3), louvres on the lower ridges (note 4)
  dormer(c, 6, P.roof, P.brick, P.glass, P.frame, { face: 1, eave: 0, y0: 6 })
  dormer(c, 6, P.roof, P.brick, P.glass, P.frame, { face: 9, eave: 10, y0: 6 })
  if (level >= 2) louvre(c, 13, 7, 8, { base: P.roof.full, post: `${P.trim}_fence`, roof: P.roof, finial: 'lightning_rod' })
  if (level >= 3) louvre(c, 8, 15, 7, { base: P.roof.full, post: `${P.trim}_fence`, roof: P.roof, finial: 'lightning_rod' })
  // a landing ledge under the hoist and the hatch (note 2), a canopy over the shed's loading gate (note 5)
  for (let x = 4; x <= 6; x++) c.set(x, 4, 1, `${P.trim}_slab`, { type: 'top', waterlogged: 'false' })
  if (level >= 2) for (let x = 10; x <= 12; x++) stair(c, x, 4, 1, P.roof.stairs, 'south')
  // the tally corner's working look: a slab counter either side of the clerk's spot, crates and sacks of goods
  for (const z of [8, 10]) c.set(7, 1, z, `${P.wood}_slab`, { type: 'top', waterlogged: 'false' })
  for (const [x, y, z, id] of [[3, 1, 3, P.crate], [2, 2, 3, P.sack], [3, 5, 3, P.crate], [7, 5, 3, P.sack], [6, 5, 3, P.crate]]) c.set(x, y, z, id, id === P.crate ? { axis: 'y' } : {})

  // --- Pass 5: stores, the tally desk and the lamps --------------------------------------------------------------
  tallyDesk(c, DESK.at, DESK.facing, DESK.side, DESK.high, { job: P.job, store: P.store, lamp: P.lamp })
  for (const s of STORES) if (s.level <= level) chestRack(c, s.cells, s.y[0], s.y[1], s.facing, P.store)
  for (const f of FURNITURE) if (f.level <= level) furnitureParts[f.part](c, f)
  for (const g of GOODS) if (g.level <= level) for (const [x, y, z] of g.cells) c.set(x, y, z, P.goods, { axis: 'y' })

  // --- Pass 6: the vault ----------------------------------------------------------------------------------------
  const V = MASSES.vault
  const dug = cellar(c, V.x0, V.z0, V.x1, V.z1, V.depth, mix(P.vaultWall, 3), P.vaultFloor)
  hatchLadder(c, V.ladder.x, V.ladder.z, -V.depth + 1, 0, V.ladder.facing, P.wood)
  return { c, dug }
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const { c, dug } = drawLevel(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  fs.mkdirSync(path.join(here, 'out'), { recursive: true })
  fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), dug.join(','))
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: `${IDS[level]}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
