// Guildhall, the recipe: assembles plan.mjs from the parts library in passes (block-out, openings, depth and detail,
// interior). LEVEL=guildhall|guildhall_2|guildhall_3 picks the level (each grows out of the last); CUT=<y> cuts above y.
// Writes furniture.json and frames.json (the charter frames, for frames.py) beside it.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { LEVELS, PALETTE as P, ARCADE, HALL as H, WING as W, CELLAR, OPENINGS as O, FRAMES, FURNITURE } from './plan.mjs'
import {
  LiftedCanvas, mix, plinth, floor, post, beamRing, wallPanel, window, gableRoof, gableWall, bargeTrim, louvre,
  flight, railing, door, cellar, hatchLadder, stair, furnitureParts, arcadeRun, jettyCorbels,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const ID = process.env.LEVEL ?? 'guildhall'

export function draw(id = ID) {
  const L = LEVELS[id]
  const c = new LiftedCanvas(L.size, L.base)
  const stone = mix(P.stone, 3), rubble = mix(P.plinth, 5)
  const log = (x, y, z, axis) => c.set(x, y, z, P.frame, { axis })

  // --- Pass 1: block-out ---------------------------------------------------------------------------------------
  plinth(c, H.x0, H.z0, H.x1, H.z1, rubble)
  floor(c, 2, 3, 14, 10, 0, mix([['stone_bricks', 3], ['polished_andesite', 2]], 9)) // the market floor
  for (const side of ['front', 'west', 'east']) {
    const a = ARCADE[side]
    arcadeRun(c, side === 'front' ? 'x' : 'z', a.line, a.piers, ARCADE.top, { pier: stone, course: P.course, arch: P.arch, cap: P.cap })
  }
  wallPanel(c, [H.x0, H.z1], [H.x1, H.z1], 1, ARCADE.top, stone) // the back wall
  // the hall: floor jettied out a block over the arcade, oak posts, plaster panels, a beam ring, tie beams
  floor(c, H.x0, H.z0, H.x1, H.z1, H.floorY, P.floor)
  for (let x = H.x0; x <= H.x1; x++) log(x, H.floorY, H.z0, 'x') // the jetty's bressumer
  jettyCorbels(c, 'x', H.z0, H.frontPosts, ARCADE.top, `${P.trim}_stairs`, 'south')
  wallPanel(c, [H.x0, H.z0], [H.x1, H.z0], 6, H.wallTop, P.infill)
  wallPanel(c, [H.x0, H.z1], [H.x1, H.z1], 6, H.wallTop, P.infill)
  wallPanel(c, [H.x0, H.z0], [H.x0, H.z1], 6, H.wallTop, P.infill)
  wallPanel(c, [H.x1, H.z0], [H.x1, H.z1], 6, H.wallTop, P.infill)
  for (const x of H.frontPosts) post(c, x, H.z0, 6, H.wallTop, P.frame)
  for (const x of H.backPosts) post(c, x, H.z1, 6, H.wallTop, P.frame)
  for (const z of H.sidePosts) { post(c, H.x0, z, 6, H.wallTop, P.frame); post(c, H.x1, z, 6, H.wallTop, P.frame) }
  beamRing(c, H.x0, H.z0, H.x1, H.z1, H.beamY, P.frame)
  for (const x of H.ties) for (let z = H.z0 + 1; z < H.z1; z++) log(x, H.beamY, z, 'z')

  // roofs: the gablet first (it owns its cells), then the long slate roof around it
  const g = gableRoof(c, H.gablet, P.roof)
  const main = gableRoof(c, H.roof, P.roof, (x, y, z) => !c.get(x, y, z))
  for (let x = 5; x <= 11; x++) for (let z = H.z0; z <= 5; z++) for (let y = H.beamY + 1; y < g.under[x]; y++)
    if (c.get(x, y, z)?.id.includes('deepslate')) c.clear(x, y, z) // open the hall up into the gablet
  gableWall(c, 'z', H.z0, [4, 12], H.beamY + 1, g.under, (u) => (u === 8 ? [P.frame, { axis: 'y' }] : P.infill))
  for (const xLine of [H.x0, H.x1]) gableWall(c, 'x', xLine, [0, 12], H.beamY + 1, main.under, (u, y) =>
    (u === 3 || u === 9) && y <= 13 ? [P.frame, { axis: 'y' }] : y === 14 && u >= 4 && u <= 8 ? [P.frame, { axis: 'z' }] : P.infill)
  louvre(c, 8, 6, main.ridgeY, { base: P.roof.full, post: `${P.trim}_fence`, roof: P.roof, finial: 'lightning_rod' })
  bargeTrim(c, 'x', 0, [0, 12], main.under, `${P.trim}_slab`)
  bargeTrim(c, 'x', 16, [0, 12], main.under, `${P.trim}_slab`)
  bargeTrim(c, 'z', 0, [4, 12], g.under, `${P.trim}_slab`)

  // --- Pass 2: openings ------------------------------------------------------------------------------------------
  // mid rails removed where glass goes; windows (an east one only while no wing stands against that gable)
  for (const w of O.windows) {
    if (w.level === 1 && L.wing) continue
    window(c, w.cells, w.out, w.dress ?? null, { glass: P.glass, wood: P.wood })
  }
  for (const [x, y, z] of O.stairs.well) c.clear(x, y, z)
  // the stair is boxed in stone behind a door, so the open market below never reaches the hall
  for (let y = 1; y <= ARCADE.top - 1; y++) { for (let x = 2; x <= 9; x++) c.set(x, y, 9, stone); c.set(1, y, 10, stone); c.set(9, y, 10, stone) }
  for (let x = 1; x <= 9; x++) c.set(x, ARCADE.top, 9, P.course)
  c.set(9, ARCADE.top, 10, P.course); c.set(1, ARCADE.top, 10, P.course)
  c.clear(9, 1, 10); c.clear(9, 2, 10)
  door(c, [9, 1, 10], 'west', 'east', { wood: P.wood })
  flight(c, O.stairs.flight, O.stairs.rises, P.wood, P.under)
  railing(c, O.rail, P.wood)

  // --- Pass 3: the wing (II and III) ------------------------------------------------------------------------------
  if (L.wing) {
    for (let y = 0; y < L.size[1]; y++) for (let z = 0; z < L.size[2]; z++) if (y >= H.beamY) c.clear(16, y, z) // I's east overhang
    bargeTrim(c, 'x', 16, [0, 12], main.under, `${P.trim}_slab`) // (nothing left to trim there: no-op guard)
    for (let y = H.beamY - 1; y < L.size[1]; y++) for (let z = 0; z < L.size[2]; z++) { const b = c.get(16, y, z); if (b && b.id.includes('dark_oak_slab')) c.clear(16, y, z) }
    plinth(c, W.x0, 2, W.x1, W.z1, rubble)
    floor(c, W.x0 + 1, 3, W.x1 - 1, W.z1 - 1, 0, P.floor)
    wallPanel(c, [W.x0, 2], [W.x1, 2], 1, ARCADE.top, stone)
    wallPanel(c, [W.x0, W.z1], [W.x1, W.z1], 1, ARCADE.top, stone)
    wallPanel(c, [W.x0, 2], [W.x0, W.z1], 1, ARCADE.top, stone)
    wallPanel(c, [W.x1, 2], [W.x1, W.z1], 1, ARCADE.top, stone)
    for (const [x, z] of [[W.x0, 2], [W.x1, 2], [W.x0, W.z1], [W.x1, W.z1]]) for (let y = 1; y <= ARCADE.top; y++) c.set(x, y, z, P.cap)
    floor(c, W.x0, W.z0, W.x1, W.z1, H.floorY, P.floor)
    for (let x = W.x0; x <= W.x1; x++) log(x, H.floorY, W.z0, 'x')
    jettyCorbels(c, 'x', W.z0, [W.x0, 20, W.x1], ARCADE.top, `${P.trim}_stairs`, 'south')
    for (const [a, b] of [[[W.x0, W.z0], [W.x1, W.z0]], [[W.x0, W.z1], [W.x1, W.z1]], [[W.x0, W.z0], [W.x0, W.z1]], [[W.x1, W.z0], [W.x1, W.z1]]])
      wallPanel(c, a, b, 6, H.wallTop, P.infill)
    for (const x of [W.x0, 20, W.x1]) { post(c, x, W.z0, 6, H.wallTop, P.frame); post(c, x, W.z1, 6, H.wallTop, P.frame) }
    for (const z of [4, 8]) post(c, W.x1, z, 6, H.wallTop, P.frame)
    beamRing(c, W.x0, W.z0, W.x1, W.z1, H.beamY, P.frame)
    for (let z = W.z0 + 1; z < W.z1; z++) log(20, H.beamY, z, 'z') // the wing's tie beam
    const wr = gableRoof(c, W.roof, P.roof)
    for (const zLine of [W.z0, W.z1]) gableWall(c, 'z', zLine, [W.x0, W.x1], H.beamY + 1, wr.under, (u, y) =>
      (u === 18 || u === 22) && y <= 13 ? [P.frame, { axis: 'y' }] : y === 15 && u >= 19 && u <= 21 ? [P.frame, { axis: 'x' }] : P.infill)
    bargeTrim(c, 'z', 0, [W.x0, W.x1], wr.under, `${P.trim}_stairs`)
    bargeTrim(c, 'z', 12, [W.x0, W.x1], wr.under, `${P.trim}_stairs`)
    for (const w of O.wingWindows) window(c, w.cells, w.out, null, { glass: P.glass, wood: P.wood })
    door(c, O.wingDoor, 'south', 'north', { wood: P.wood, step: [O.wingDoor[0], 0, 1], stepStair: 'stone_brick_stairs' })
    const [gx, gy, gz] = O.wingGround
    c.clear(gx, gy, gz); c.clear(gx, gy + 1, gz)
    for (const [x, y, z] of O.wingUpper) { c.clear(x, y, z); c.clear(x, y + 1, z) }
  }

  // --- Pass 4: the strongroom (III) -----------------------------------------------------------------------------
  if (L.cellar) {
    const C = CELLAR
    cellar(c, C.x0, C.z0, C.x1, C.z1, C.depth, mix(P.vault, 7), P.vaultFloor)
    for (const x of [5, 11]) for (let y = -3; y <= -1; y++) { c.set(x, y, 3, 'polished_deepslate'); c.set(x, y, 10, 'polished_deepslate') }
    for (const x of [3, 4, 9]) c.set(x, -3, 10, 'chest', { facing: 'north', type: 'single', waterlogged: 'false' })
    for (const z of [5, 6, 7]) c.set(14, -3, z, 'chest', { facing: 'west', type: 'single', waterlogged: 'false' })
    for (const [x, z] of [[4, 6], [8, 6], [12, 6]]) c.set(x, -1, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
    c.set(7, -2, 10, `${P.cloth}_wall_banner`, { facing: 'north' }); c.set(9, -2, 10, `${P.cloth}_wall_banner`, { facing: 'north' })
    hatchLadder(c, C.hatch[0], C.hatch[1], -3, 0, 'north', P.wood)
  }

  // --- Pass 5: interior -----------------------------------------------------------------------------------------
  for (const f of FURNITURE) if (!f.wing || L.wing) furnitureParts[f.part](c, f)
  c.set(8, 4, 7, 'bell', { attachment: 'ceiling', facing: 'north', powered: 'false' }) // the market bell
  return c
}

export default function build(api) {
  const L = LEVELS[ID]
  const c = draw(ID)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const frames = FRAMES.filter(f => (!f.wing || L.wing) && (!f.cellar || L.cellar))
  fs.writeFileSync(path.join(here, `frames_${ID}.json`), JSON.stringify({ basement: L.base, frames }, null, 1))
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${ID}_cut${cut}` : ID } }
}
