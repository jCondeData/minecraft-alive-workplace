// Schoolhouse (the Lantern Schoolroom, ROADMAP 49), the recipe: assembles plan.mjs from the shared parts library
// (../parts) in passes (block-out and structure, openings, depth and detail, interior, ground) for LEVEL = 1 | 2. Level
// II is Level I drawn the same way plus the east wing, so every kept cell is the same block.
//   LEVEL=2 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name schoolhouse_2 --data-version 3955
// CUT=<y> builds a cutaway with everything above y removed. Writes furniture.json (the spots) for rebuild.py.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { IDS, SIZES, PALETTE, HALL, PORCH, BELLCOTE, WING, OPENINGS, LECTERN, FURNITURE, OUTSIDE, YARD, DORMER } from './plan.mjs'
import {
  Canvas, mix, plinth, floor, post, beamRing, wallPanel, window, door, doorway, gableRoof, gableWall, bargeTrim,
  flowerBed, furnitureParts, civicParts, schoolBellcote, blackboard, lectern, civicDormer, benchRun,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const at = level => x => (x.level ?? 1) <= level

export function drawLevel(level) {
  const c = new Canvas(SIZES[level])
  const P = PALETTE
  const stone = mix(P.stone, 11)
  const H = HALL.walls, R = HALL.room, Q = PORCH.walls

  // --- Pass 1: block-out and structure -----------------------------------------------------------------------------
  plinth(c, H.x0, H.z0, H.x1, H.z1, stone)
  plinth(c, Q.x0, Q.z0, Q.x1, Q.z1, stone)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  floor(c, Q.x0 + 1, Q.z1, Q.x1 - 1, Q.z1, 0, P.floor)
  // Hall: boarded walls between dark posts, a beam ring, two tie beams across the open room
  wallPanel(c, [H.x0, H.z0], [H.x1, H.z0], 1, HALL.wallTop, P.wall)
  wallPanel(c, [H.x0, H.z1], [H.x1, H.z1], 1, HALL.wallTop, P.wall)
  wallPanel(c, [H.x0, H.z0], [H.x0, H.z1], 1, HALL.wallTop, P.wall)
  wallPanel(c, [H.x1, H.z0], [H.x1, H.z1], 1, HALL.wallTop, P.wall)
  for (const z of HALL.sidePosts) { post(c, H.x0, z, 1, HALL.wallTop, P.frame); post(c, H.x1, z, 1, HALL.wallTop, P.frame) }
  for (const x of [3, 7]) { post(c, x, H.z0, 1, HALL.wallTop, P.frame); post(c, x, H.z1, 1, HALL.wallTop, P.frame) }
  beamRing(c, H.x0, H.z0, H.x1, H.z1, HALL.beamY, P.frame)
  for (const z of HALL.ties) for (let x = R.x0; x <= R.x1; x++) c.set(x, HALL.beamY, z, P.frame, { axis: 'x' })
  // Porch: boarded walls, dark corner posts
  wallPanel(c, [Q.x0, Q.z0], [Q.x1, Q.z0], 1, 3, P.wall)
  wallPanel(c, [Q.x0, Q.z0], [Q.x0, Q.z1], 1, 3, P.wall)
  wallPanel(c, [Q.x1, Q.z0], [Q.x1, Q.z1], 1, 3, P.wall)
  for (const x of [Q.x0, Q.x1]) post(c, x, Q.z0, 1, 3, P.frame)

  // Roofs: the Hall's slate gable (front to the street), the porch's little gable under it
  const main = gableRoof(c, HALL.roof, P.roof)
  const porch = gableRoof(c, PORCH.roof, P.roof, (x, y, z) => !c.get(x, y, z))
  gableWall(c, 'z', H.z0, [H.x0, H.x1], HALL.beamY + 1, main.under, (u, y) => (u === 3 || u === 7) && y <= 7 ? [P.frame, { axis: 'y' }] : P.wall)
  gableWall(c, 'z', H.z1, [H.x0, H.x1], HALL.beamY + 1, main.under, (u, y) => (u === 3 || u === 7) && y <= 7 ? [P.frame, { axis: 'y' }] : P.wall)
  gableWall(c, 'z', Q.z0, [Q.x0 + 1, Q.x1 - 1], 4, porch.under, () => P.wall)

  // Level II: the reading-room wing, lower, its roof ridge along x running into the Hall's east slope
  if (level >= 2) {
    const W = WING.walls, WR = WING.room
    plinth(c, W.x0 + 1, W.z0, W.x1, W.z1, stone)
    floor(c, WR.x0, WR.z0, WR.x1, WR.z1, 0, P.floor)
    wallPanel(c, [W.x0 + 1, W.z0], [W.x1, W.z0], 1, WING.wallTop, P.wall)
    wallPanel(c, [W.x0 + 1, W.z1], [W.x1, W.z1], 1, WING.wallTop, P.wall)
    wallPanel(c, [W.x1, W.z0], [W.x1, W.z1], 1, WING.wallTop, P.wall)
    for (const [x, z] of [[W.x1, W.z0], [W.x1, W.z1], [12, W.z0], [12, W.z1]]) post(c, x, z, 1, WING.wallTop, P.frame)
    for (let x = W.x0 + 1; x <= W.x1; x++) for (const z of [W.z0, W.z1]) c.set(x, WING.beamY, z, P.frame, { axis: 'x' })
    for (let z = W.z0; z <= W.z1; z++) c.set(W.x1, WING.beamY, z, P.frame, { axis: z === W.z0 || z === W.z1 ? 'y' : 'z' })
    // the wing's ridge runs on into the Hall's east slope, kept only above it (main.under: the Hall roof's y by column)
    const wing = gableRoof(c, WING.roof, P.roof, (x, y, z) => x > H.x1 + 1 || (!c.get(x, y, z) && y > (main.under[x] ?? 0)))
    c.set(H.x1, main.under[H.x1] + 1, 7, P.roof.full) // the valley's foot where the ridges meet: no pocket under it
    gableWall(c, 'x', W.x1, [W.z0, W.z1], WING.beamY + 1, wing.under, () => P.wall)
    bargeTrim(c, 'x', WING.roof.along[1], WING.roof.across, wing.under, `${P.trim}_stairs`)
  }

  // --- Pass 2: openings ---------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: 'stone_brick_stairs' })
  doorway(c, OPENINGS.hallDoorway.at, OPENINGS.hallDoorway.height)
  if (level >= 2) doorway(c, OPENINGS.wingDoorway.at, OPENINGS.wingDoorway.height)
  for (const w of OPENINGS.windows.filter(at(level))) {
    if (level >= 2 && w.name === 'hall east 1') { // the wing's doorway takes this bay: the rest of it is wall
      for (const [x, y, z] of w.cells) if (!(z === 8 && y <= 2)) c.set(x, y, z, P.wall)
      continue
    }
    const inner = level >= 2 && w.name.startsWith('hall east') // looks into the wing now: no sill
    window(c, w.cells, w.out, inner ? null : w.dress, { glass: P.glass, wood: P.wood, sill: `${P.trim}_stairs` })
  }

  // --- Pass 3: depth and detail -------------------------------------------------------------------------------------
  bargeTrim(c, 'z', HALL.roof.along[0], HALL.roof.across, main.under, `${P.trim}_stairs`)
  bargeTrim(c, 'z', HALL.roof.along[1], HALL.roof.across, main.under, `${P.trim}_stairs`)
  bargeTrim(c, 'z', PORCH.roof.along[0], PORCH.roof.across, porch.under, `${P.trim}_stairs`)
  schoolBellcote(c, BELLCOTE, {
    base: 'stone_bricks', under: P.roof.full, post: `${P.trim}_fence`, cap: P.cap, finial: 'lightning_rod',
  })
  blackboard(c, { x0: 4, x1: 6, z: H.z1, y0: 2, y1: 4 }, P.board)
  civicDormer(c, { ...DORMER, roofY: x => main.under[x] }, { roof: P.roof, wall: P.wall, post: P.frame, glass: P.glass })

  // --- Pass 4: interior ---------------------------------------------------------------------------------------------
  lectern(c, LECTERN.pos, LECTERN.facing)
  const parts = { ...furnitureParts, bench: civicParts.bench }
  for (const f of FURNITURE.filter(at(level))) parts[f.part](c, f)

  // --- Pass 5: ground -----------------------------------------------------------------------------------------------
  for (const b of OUTSIDE.flowerBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  if (level >= 2) { // the schoolyard: fence on three sides (the wing's wall the fourth), a gate to the street
    const F = YARD.fence
    for (let x = F.x0; x <= F.x1; x++) for (let z = F.z0; z <= F.z1; z++)
      if (x === F.x0 || x === F.x1 || z === F.z0) c.set(x, 0, z, `${P.wood}_fence`)
    c.set(YARD.gate[0], 0, YARD.gate[1], `${P.wood}_fence_gate`, { facing: 'north', open: 'false', in_wall: 'false', powered: 'false' })
    benchRun(c, { ...YARD.bench, facing: 'north' }, { seat: `${P.wood}_stairs` })
    const [tx, tz] = YARD.tree
    c.set(tx, 0, tz, 'moss_block'); c.set(tx, 1, tz, 'flowering_azalea')
  }
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const c = drawLevel(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: `${IDS[level]}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
