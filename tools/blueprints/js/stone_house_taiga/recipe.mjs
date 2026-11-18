// Stone House, the taiga look (ROADMAP 49.10b): assembles plan.mjs from the parts library (../parts) in passes
// (block-out and roof, openings, depth and detail, interior, cellar and ground) for LEVEL = 1..4, each level the one
// before plus what it adds (II the east slope swept on over the parlour, III the log pen behind, IV the store below).
// Writes furniture.json (the furniture spots) and, for IV, out/cellar.txt. Build all four: build.sh.
//   LEVEL=2 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955   (CUT=<y>: a cutaway)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { IDS, SIZES, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  LiftedCanvas, mix, stair, plinth, floor, wallPanel, window, door, doorway, gableRoof, gableWall, chimney,
  flight, railingRun, furnitureParts, cellar, hatchLadder, quoins, flowerBed, logPen, pentPorch, beamEnds,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function drawLevel(level) {
  const c = new LiftedCanvas(SIZES[level], BASEMENT[level] ?? 0)
  const P = PALETTE
  const M = MASSES.main, Wg = MASSES.wing, B = MASSES.back
  const stone = mix(P.stone, 17), base = mix(P.plinth, 5), stack = mix(P.chimney, 11)
  const has = o => (o.from ?? 1) <= level && level <= (o.until ?? 9)
  const logX = [P.log, { axis: 'x' }]

  // --- Pass 1: block-out and roof ----------------------------------------------------------------------------------
  plinth(c, M.x0, M.z0, M.x1, M.z1, base)
  floor(c, M.x0 + 1, M.z0 + 1, M.x1 - 1, M.z1 - 1, 0, P.floor)
  const main = gableRoof(c, { ...M.roof, along: level >= 3 ? B.along : M.roof.along }, P.roof)
  // the fieldstone storey, the log sill ring at the upper floor with one more course on the sides, the log gables
  for (const [a, b] of [[[M.x0, M.z0], [M.x1, M.z0]], [[M.x0, M.z1], [M.x1, M.z1]], [[M.x0, M.z0], [M.x0, M.z1]], [[M.x1, M.z0], [M.x1, M.z1]]])
    wallPanel(c, a, b, 1, M.upperY - 1, stone)
  logPen(c, { x0: M.x0, z0: M.z0, x1: M.x1, z1: M.z1, y0: M.upperY, y1: main.under[M.x0] - 1 }, P.log)
  for (const z of [M.z0, M.z1]) gableWall(c, 'z', z, [M.x0 + 1, M.x1 - 1], main.under[M.x0], main.under, () => logX)
  floor(c, M.x0 + 1, M.z0 + 1, M.x1 - 1, M.z1 - 1, M.upperY, P.floor)

  if (level >= 2) { // the parlour: fieldstone under the east slope carried on down
    plinth(c, Wg.x0 + 1, Wg.z0, Wg.x1, Wg.z1, base)
    floor(c, Wg.x0 + 1, Wg.z0 + 1, Wg.x1 - 1, Wg.z1 - 1, 0, P.floor)
    const under = { [Wg.x0 + 1]: main.under[Wg.x0 + 1] }
    for (const [x, y] of Wg.slope) {
      under[x] = y
      for (let z = Wg.along[0]; z <= Wg.along[1]; z++) stair(c, x, y, z, P.roof.stairs, 'west')
    }
    for (let x = Wg.x0 + 1; x <= Wg.x1; x++) for (const z of [Wg.z0, Wg.z1]) wallPanel(c, [x, z], [x, z], 1, under[x] - 1, stone)
    wallPanel(c, [Wg.x1, Wg.z0], [Wg.x1, Wg.z1], 1, under[Wg.x1] - 1, stone)
  }

  if (level >= 3) { // the log pen behind, under the roof run back to its own gable
    plinth(c, B.x0, B.z0 + 1, B.x1, B.z1, base)
    floor(c, B.x0 + 1, B.z0 + 1, B.x1 - 1, B.z1 - 1, 0, P.floor)
    for (const [a, b] of [[[B.x0, B.z0 + 1], [B.x0, B.z1]], [[B.x1, B.z0 + 1], [B.x1, B.z1]], [[B.x0, B.z1], [B.x1, B.z1]]]) wallPanel(c, a, b, 1, 1, stone)
    logPen(c, { x0: B.x0, z0: B.z0, x1: B.x1, z1: B.z1, y0: 2, y1: main.under[B.x0] - 1 }, P.log, (x, y, z) => z === B.z0)
    gableWall(c, 'z', B.z1, [B.x0 + 1, B.x1 - 1], main.under[B.x0], main.under, () => logX)
    floor(c, B.x0 + 1, B.z0 + 1, B.x1 - 1, B.z1 - 1, B.ceilingY, P.floor)
  }

  // --- Pass 2: openings ------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: P.sill })
  if (level >= OPENINGS.wingDoorway.level) doorway(c, OPENINGS.wingDoorway.at)
  if (level >= OPENINGS.backDoorway.level) doorway(c, OPENINGS.backDoorway.at)
  for (const w of OPENINGS.windows) if (has(w)) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood })
  const pt = OPENINGS.partition
  for (const [x, z] of pt.cells) wallPanel(c, [x, z], [x, z], 1, M.upperY - 1, P.partition)
  wallPanel(c, [pt.door[0], pt.door[2]], [pt.door[0], pt.door[2]], 3, M.upperY - 1, P.partition)
  door(c, pt.door, 'south', 'south', { wood: P.wood })
  const st = OPENINGS.stairs
  for (const [x, y, z] of st.well) c.clear(x, y, z)
  flight(c, st.flight, st.rises, P.wood, P.floor)
  railingRun(c, st.rail, P.wood)
  // the porch: a slate pent across the gable on log posts, a lantern by the door
  pentPorch(c, MASSES.porch, { roof: P.roof, post: P.post, bracket: P.trim })

  // --- Pass 3: depth and detail ----------------------------------------------------------------------------------
  const corners = [[M.x0, M.z0], [M.x1, M.z0], [M.x0, M.z1], [M.x1, M.z1]]
  quoins(c, corners, 1, M.upperY - 1, P.dressed, P.dressedAlt)
  if (level >= 2) quoins(c, [[Wg.x1, Wg.z0], [Wg.x1, Wg.z1]], 1, 3, P.dressed, P.dressedAlt)
  // the sill's ends crossing out at the log storey's corners
  const ends = [[M.x0 - 1, M.upperY, M.z0, 'x'], [M.x1 + 1, M.upperY, M.z0, 'x'], [M.x0 - 1, M.upperY, M.z1, 'x'], [M.x1 + 1, M.upperY, M.z1, 'x']]
  if (level < 3) ends.push([M.x0, M.upperY, M.z1 + 1, 'z'], [M.x1, M.upperY, M.z1 + 1, 'z'])
  else ends.push([B.x0 - 1, M.upperY, B.z1, 'x'], [B.x1 + 1, M.upperY, B.z1, 'x'])
  beamEnds(c, ends, P.log)
  // the west stack: a fieldstone breast, the stack up through the eave past the ridge, a mossy cap and smoke
  const ch = MASSES.chimney
  for (let z = ch.breast[0]; z <= ch.breast[1]; z++) for (let y = 0; y <= ch.breastTop; y++) c.set(ch.x, y, z, stack)
  stair(c, ch.x, ch.breastTop + 1, ch.breast[0], P.sill, 'south')
  stair(c, ch.x, ch.breastTop + 1, ch.breast[1], P.sill, 'north')
  chimney(c, ch.x, ch.z, ch.breastTop + 1, ch.top, stack)
  c.set(ch.x, ch.top, ch.z, P.cap)
  // firewood stacked under the west eave, either side of the box room's window
  const wp = MASSES.woodpile
  for (const z of wp.zs) {
    c.set(wp.x, 0, z, base)
    for (let y = 1; y <= wp.top; y++) c.set(wp.x, y, z, P.firewood[(y + z) % P.firewood.length], { axis: 'x' })
  }
  for (const b of OUTSIDE.fernBeds) flowerBed(c, b.x0, b.x1, b.z, P.plants, P.bedSoil)

  // --- Pass 4: interior (the plan's furniture) ---------------------------------------------------------------------
  for (const f of FURNITURE) if (has(f)) furnitureParts[f.part](c, f)

  // --- Pass 5: the store (IV) ----------------------------------------------------------------------------------------
  let dug = null
  if (level >= 4) {
    const C = MASSES.cellar
    dug = cellar(c, C.x0, C.z0, C.x1, C.z1, C.depth, mix(P.cellarWall, 3), P.cellarFloor)
    hatchLadder(c, C.ladder.x, C.ladder.z, -C.depth + 1, 0, C.ladder.facing, P.wood)
    const Bm = C.beam
    for (let x = C.x0 + 1; x <= C.x1 - 1; x++) if (!c.get(x, -1, Bm.z)) c.set(x, -1, Bm.z, P.log, { axis: 'x' })
    for (let y = -C.depth + 1; y <= -2; y++) c.set(Bm.post, y, Bm.z, P.log, { axis: 'y' })
  }
  return { c, dug }
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
