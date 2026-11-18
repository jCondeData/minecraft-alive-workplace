// Stone House, the snowy look (ROADMAP 49.10b): assembles plan.mjs from the parts library (../parts) in passes
// (block-out and roofs, openings, depth and detail, interior, cellar, snow and ground) for LEVEL = 1..4, each level the
// one before plus what it adds (II the steep east wing, III the back wing's cross gable, IV the store below).
// Writes furniture.json (the furniture spots) and, for IV, out/cellar.txt. Build all four: build.sh.
//   LEVEL=2 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955   (CUT=<y>: a cutaway)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { IDS, SIZES, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  LiftedCanvas, mix, stair, plinth, floor, wallPanel, window, door, doorway, gableRoof, gableWall, chimney,
  flight, railingRun, furnitureParts, cellar, hatchLadder, quoins, stringCourse, snowLoad, snowSkirt, stormPorch,
  frontDormer,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

// the top of a steep roof's column i courses in from its eave at eaveY (its stair's cell; the ridge's full block)
const steepTop = (eaveY, half, i) => (i === 0 ? eaveY : i >= half ? eaveY + 2 * half - 1 : eaveY + 2 * i)

export function drawLevel(level) {
  const c = new LiftedCanvas(SIZES[level], BASEMENT[level] ?? 0)
  const P = PALETTE
  const M = MASSES.main, Wg = MASSES.wing, B = MASSES.back
  const rubble = mix(P.rubble, 31), base = mix(P.plinth, 7), stack = mix(P.chimney, 13)
  const has = o => (o.from ?? 1) <= level && level <= (o.until ?? 9)
  const gablePick = () => rubble

  // --- Pass 1: block-out and roofs -------------------------------------------------------------------------------
  plinth(c, M.x0, M.z0, M.x1, M.z1, base)
  floor(c, M.x0 + 1, M.z0 + 1, M.x1 - 1, M.z1 - 1, 0, P.floor)
  const main = gableRoof(c, M.roof, P.roof)
  for (const z of [M.z0, M.z1]) wallPanel(c, [M.x0, z], [M.x1, z], 1, main.under[z] - 1, rubble)
  for (const x of [M.x0, M.x1]) gableWall(c, 'x', x, [M.z0, M.z1], 1, main.under, (z, y) => gablePick(y))
  floor(c, M.x0 + 1, M.z0 + 1, M.x1 - 1, M.z1 - 1, M.upperY, P.floor)
  stringCourse(c, M.x0, M.z0, M.x1, M.z1, M.upperY, P.course)

  if (level >= 2) { // the east wing: a steep cross gable, lower than the house
    plinth(c, Wg.x0 + 1, Wg.z0, Wg.x1, Wg.z1, base)
    floor(c, Wg.x0 + 1, Wg.z0 + 1, Wg.x1 - 1, Wg.z1 - 1, 0, P.floor)
    const wing = gableRoof(c, Wg.roof, P.roof, (x, y, z) => !c.get(x, y, z))
    for (const z of [Wg.z0, Wg.z1]) wallPanel(c, [Wg.x0 + 1, z], [Wg.x1, z], 1, wing.under[z] - 1, rubble)
    gableWall(c, 'x', Wg.x1, [Wg.z0, Wg.z1], 1, wing.under, (z, y) => gablePick(y))
    stringCourse(c, Wg.x0 + 1, Wg.z0, Wg.x1, Wg.z1, M.upperY, P.course, (x, z) => x === Wg.x0 + 1 && z > Wg.z0 && z < Wg.z1)
    // the house's rake runs on over the wing's ridge: close the one cell between them
    c.set(Wg.x0 + 1, main.ridgeY - 1, 5, P.roof.full)
    // the tie beam the parlour's lamp hangs from
    const T = Wg.tie
    for (let x = T.x0; x <= T.x1; x++) c.set(x, T.y, T.z, P.log, { axis: 'x' })
  }

  if (level >= 3) { // the back wing: a steep gable as tall as the house, crossing its back slope
    plinth(c, B.x0, B.z0 + 1, B.x1, B.z1, base)
    floor(c, B.x0 + 1, B.z0 + 1, B.x1 - 1, B.z1 - 1, 0, P.floor)
    const R = M.roof, half = (R.across[1] - R.across[0]) / 2
    const mainTop = z => steepTop(R.eaveY, half, Math.min(z - R.across[0], R.across[1] - z))
    for (let x = R.along[0]; x <= R.along[1]; x++) c.clear(x, R.eaveY, R.across[1]) // the back eave, now indoors
    const back = gableRoof(c, B.roof, P.roof, (x, y, z) => !c.get(x, y, z) && (z > R.across[1] || y > mainTop(z)))
    for (const x of [B.x0, B.x1]) wallPanel(c, [x, B.z0 + 1], [x, B.z1], 1, back.under[x] - 1, rubble)
    gableWall(c, 'z', B.z1, [B.x0, B.x1], 1, back.under, (x, y) => gablePick(y))
    stringCourse(c, B.x0, B.z0 + 1, B.x1, B.z1, M.upperY, P.course, (x, z) => z === B.z0 + 1 && x > B.x0 && x < B.x1)
    floor(c, B.x0 + 1, B.z0 + 1, B.x1 - 1, B.z1 - 1, B.ceilingY, P.floor)
  }

  // --- Pass 2: openings ------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: P.sill })
  if (level >= OPENINGS.wingDoorway.level) doorway(c, OPENINGS.wingDoorway.at)
  if (level >= OPENINGS.backDoorway.level) doorway(c, OPENINGS.backDoorway.at)
  for (const w of OPENINGS.windows) if (has(w)) {
    window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, sill: P.sill, pots: w.pots ?? [] })
    if (w.snow && !w.pots) { // snow on the sill: one layer on the upside-down stair under the panes
      const [x, y, z] = w.cells[0], d = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }[w.out]
      c.set(x + d[0], y, z + d[1], P.snow, { layers: '1' })
    }
  }
  const pt = OPENINGS.partition
  for (const [x, z] of pt.cells) wallPanel(c, [x, z], [x, z], 1, M.upperY - 1, P.partition)
  wallPanel(c, [pt.door[0], pt.door[2]], [pt.door[0], pt.door[2]], 3, M.upperY - 1, P.partition)
  door(c, pt.door, 'south', 'south', { wood: P.wood })
  const st = OPENINGS.stairs
  for (const [x, y, z] of st.well) c.clear(x, y, z)
  flight(c, st.flight, st.rises, P.wood, P.floor)
  railingRun(c, st.rail, P.wood)
  // the chamber's dormer over the desk, in the front slope
  frontDormer(c, MASSES.dormer, P.roof, { cheek: P.post, infill: P.dormerFace, glass: P.glass, verge: P.verge, vergeTop: P.vergeTop, snow: P.snow })
  // the storm porch: stone cheeks under the front eave, a birch lintel, a lantern
  const pc = MASSES.porch
  stormPorch(c, pc.x, pc.z, pc.top, { cheek: rubble, lintel: P.trim })

  // --- Pass 3: depth and detail ----------------------------------------------------------------------------------
  const corners = [[M.x0, M.z0], [M.x1, M.z0]]
  if (level < 3) corners.push([M.x0, M.z1], [M.x1, M.z1]); else corners.push([B.x0, B.z1], [B.x1, B.z1])
  if (level >= 2) corners.push([Wg.x1, Wg.z0], [Wg.x1, Wg.z1])
  quoins(c, corners, 1, M.upperY - 1, P.dressed, P.dressedAlt)
  // the west stack: a breast three wide to y 7 stepped in, the stack past the ridge, a dressed cap and smoke
  const ch = MASSES.chimney
  for (let z = ch.breast[0]; z <= ch.breast[1]; z++) for (let y = 0; y <= ch.breastTop; y++) c.set(ch.x, y, z, stack)
  stair(c, ch.x, ch.breastTop + 1, ch.breast[0], P.sill, 'south')
  stair(c, ch.x, ch.breastTop + 1, ch.breast[1], P.sill, 'north')
  chimney(c, ch.x, ch.z, ch.breastTop + 1, ch.top, stack)
  c.set(ch.x, ch.top, ch.z, P.cap)
  // firewood stacked to the eaves either side of the porch, on stone (and, in I, against the east gable)
  for (const [x, z] of [...OUTSIDE.firewood, ...(level < 2 ? OUTSIDE.eastWood : [])]) {
    c.set(x, 0, z, base)
    for (let y = 1; y <= OUTSIDE.firewoodTop; y++) c.set(x, y, z, P.firewood[(x + y) % P.firewood.length], { axis: 'z' })
  }

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

  // --- Pass 6: snow -------------------------------------------------------------------------------------------------
  // snow on the ridges (capping them three wide: the course under each ridge carries it too), along every eave, and,
  // while the back slope is whole (I, II), one full course of it across the back (the snow line); the rakes (the
  // gables' edges) and the dormer keep their stairs, so the gables read as clean rakes and the dormer as its own roof
  const D = MASSES.dormer
  const rake = (x, y, z) => x === M.roof.along[0] || x === M.roof.along[1] || (level >= 2 && x === Wg.roof.along[1]) ||
    (level >= 3 && z === B.roof.along[1])
  const dormer = (x, y, z) => x >= D.cx - 2 && x <= D.cx + 2 && z <= D.face + 1 && y >= D.y0 + 2
  const line = (x, y, z) => level < 3 && y === MASSES.snowLine && z > M.roof.across[1] / 2 && z <= M.roof.across[1] &&
    x > M.roof.along[0] && x < M.roof.along[1]
  // (a rake keeps its stairs but its ridge block takes snow, so each ridge's snow runs its whole length); the wing's
  // eaves stay bare (the house's rake covers one end of them, so their snow would read as a broken piece)
  const roofStairs = `minecraft:${P.roof.stairs}`
  const wingEave = (x, y, z) => level >= 2 && x > Wg.x0 && x <= Wg.x1 && z <= Wg.z1 + 1
  snowLoad(c, P.roof, P.snow, { eaves: true, shoulders: true, tread: line, eaveSkip: (x, y, z) => dormer(x, y, z) || wingEave(x, y, z),
    skip: (x, y, z) => dormer(x, y, z) || (rake(x, y, z) && c.name(x, y, z) === roofStairs) })
  snowSkirt(c, P.snow, OUTSIDE.bare)
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
