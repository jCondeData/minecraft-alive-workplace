// Stone House (ROADMAP 49), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes
// (block-out and structure, roofs and gables, openings, depth and detail, interior, cellar and ground) for LEVEL = 1..4,
// each level the one before plus what it adds (II the parlour wing, III the catslide outshot, IV the store below).
// Writes furniture.json (the furniture spots) and, for IV, out/cellar.txt. Build all four: build.sh.
//   LEVEL=2 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955   (CUT=<y>: a cutaway)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { IDS, SIZES, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  LiftedCanvas, mix, plinth, floor, wallPanel, window, door, doorway, gableRoof, catslideRoof, gableWall, bargeTrim,
  chimney, hood, flowerBed, flight, railingRun, furnitureParts, cellar, hatchLadder, quoins, stringCourse, hearthFire,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function drawLevel(level) {
  const c = new LiftedCanvas(SIZES[level], BASEMENT[level] ?? 0)
  const P = PALETTE
  const M = MASSES.main, Wg = MASSES.wing, O = MASSES.outshot
  // rubble, weathered only where water lies: the plinth course, the corners' feet and under the front and back eaves
  const clean = mix(P.rubble, 49), worn = mix(P.weathered, 11), base = mix(P.plinth, 7)
  const near = (v, a, b) => Math.abs(v - a) <= 1 || Math.abs(v - b) <= 1
  const rubble = (x, y, z) => (y <= 1 || (y >= M.kneeTop - 1 && (z === M.z0 || z === M.z1 || z === O.z1)) ||
    (y <= 3 && near(x, M.x0, M.x1) && near(z, M.z0, level >= 3 ? O.z1 : M.z1)) ? worn : clean)(x, y, z)
  const has = o => (o.from ?? 1) <= level && level <= (o.until ?? 9)

  // --- Pass 1: block-out and structure ---------------------------------------------------------------------------
  plinth(c, M.x0, M.z0, M.x1, M.z1, base)
  floor(c, M.x0 + 1, M.z0 + 1, M.x1 - 1, M.z1 - 1, 0, P.floor)
  for (const [a, b] of [[[M.x0, M.z0], [M.x1, M.z0]], [[M.x0, M.z1], [M.x1, M.z1]], [[M.x0, M.z0], [M.x0, M.z1]], [[M.x1, M.z0], [M.x1, M.z1]]])
    wallPanel(c, a, b, 1, M.kneeTop, rubble)
  floor(c, M.x0 + 1, M.z0 + 1, M.x1 - 1, M.z1 - 1, M.upperY, P.floor)
  stringCourse(c, M.x0, M.z0, M.x1, M.z1, M.upperY, P.dressed)
  if (level >= 2) { // the parlour wing against the east gable
    plinth(c, Wg.x0 + 1, Wg.z0, Wg.x1, Wg.z1, base)
    floor(c, Wg.x0 + 1, Wg.z0 + 1, Wg.x1 - 1, Wg.z1 - 1, 0, P.floor)
    wallPanel(c, [Wg.x1, Wg.z0], [Wg.x1, Wg.z1], 1, 4, rubble)
  }
  if (level >= 3) { // the outshot behind
    plinth(c, O.x0, O.z0 + 1, O.x1, O.z1, base)
    floor(c, O.x0 + 1, O.z0 + 1, O.x1 - 1, O.z1 - 1, 0, P.floor)
    wallPanel(c, [O.x0, O.z1], [O.x1, O.z1], 1, 4, rubble)
  }

  // --- Pass 2: roofs and gables ----------------------------------------------------------------------------------
  let main
  if (level >= 3) main = catslideRoof(c, O.roof, P.roof) // the back slope carried down over the outshot
  else main = gableRoof(c, { ...M.roof, steep: false }, P.roof)
  // gables: rubble from the knee walls up to the slates (and the outshot's ends in III)
  const zs = level >= 3 ? [M.z0, O.z1] : [M.z0, M.z1]
  for (const x of [M.x0, M.x1]) gableWall(c, 'x', x, zs, 1, main.under, () => rubble)
  if (level >= 2) {
    const wing = catslideRoof(c, Wg.roof, P.roof, (x, y, z) => !c.get(x, y, z))
    for (const z of [Wg.z0, Wg.z1]) gableWall(c, 'z', z, [Wg.x0 + 1, Wg.x1], 1, wing.under, () => rubble)
  }
  bargeTrim(c, 'x', M.roof.along[0], level >= 3 ? O.roof.across : M.roof.across, main.under, `${P.trim}_trapdoor`)
  bargeTrim(c, 'x', M.roof.along[1], level >= 3 ? O.roof.across : M.roof.across, main.under, `${P.trim}_trapdoor`)

  // --- Pass 3: openings ------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: P.sill })
  if (level >= OPENINGS.wingDoorway.level) doorway(c, OPENINGS.wingDoorway.at)
  if (level >= OPENINGS.backDoorway.level) doorway(c, OPENINGS.backDoorway.at)
  for (const w of OPENINGS.windows) if (has(w))
    window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, pots: w.pots ?? [], sill: P.sill })
  if (level >= 3) c.set(5, 6, 9, rubble), c.set(5, 7, 9, rubble) // the chamber's back window now looks into the outshot roof
  if (level >= 3) c.set(3, 2, 9, rubble), c.set(3, 3, 9, rubble) // the box room's back window, now indoors
  // the box room's partition: plank walls to the ceiling, a door from the hall
  const pt = OPENINGS.partition
  for (const [x, z] of pt.cells) wallPanel(c, [x, z], [x, z], 1, M.upperY - 1, P.partition)
  wallPanel(c, [pt.door[0], pt.door[2]], [pt.door[0], pt.door[2]], 3, M.upperY - 1, P.partition)
  door(c, pt.door, 'south', 'south', { wood: P.wood })
  const st = OPENINGS.stairs
  for (const [x, y, z] of st.well) c.clear(x, y, z)
  flight(c, st.flight, st.rises, P.wood, P.floor)
  railingRun(c, st.rail, P.wood)

  // --- Pass 4: depth and detail ----------------------------------------------------------------------------------
  // dressed quoins up every corner (long and short stones), the wing's and outshot's too
  const corners = [[M.x0, M.z0], [M.x1, M.z0]]
  if (level < 3) corners.push([M.x0, M.z1], [M.x1, M.z1]); else corners.push([O.x0, O.z1], [O.x1, O.z1])
  quoins(c, corners, 1, 4, P.dressed, P.dressedAlt)
  quoins(c, [[M.x0, M.z0], [M.x1, M.z0]], 6, M.kneeTop, P.dressed, P.dressedAlt)
  if (level < 3) quoins(c, [[M.x0, M.z1], [M.x1, M.z1]], 6, M.kneeTop, P.dressed, P.dressedAlt)
  if (level >= 2) quoins(c, [[Wg.x1, Wg.z0], [Wg.x1, Wg.z1]], 1, 4, P.dressed, P.dressedAlt)
  // the floor band pushed out a block: a ledge of dressed slabs round the house at the upper floor, broken by the stack,
  // the parlour wing (II) and the outshot (III)
  for (let x = M.x0 - 1; x <= M.x1 + 1; x++) for (let z = M.z0 - 1; z <= M.z1 + 1; z++) {
    if (x > M.x0 - 1 && x < M.x1 + 1 && z > M.z0 - 1 && z < M.z1 + 1) continue
    if (x === MASSES.chimney.x && Math.abs(z - MASSES.chimney.z) <= 1) continue
    if (level >= 2 && x === M.x1 + 1 && z >= Wg.z0 - 1) continue
    if (level >= 3 && z === M.z1 + 1) continue
    c.set(x, M.upperY, z, P.band, { type: 'top', waterlogged: 'false' })
  }
  // the west stack: a stepped breast, the stack past the ridge, a dressed cap, smoke
  const ch = MASSES.chimney
  c.set(ch.x, 0, ch.z, base)
  chimney(c, ch.x, ch.z, 1, ch.top - 1, rubble, { breast: { axis: 'z', top: 4, stair: P.sill } })
  c.set(ch.x, ch.top, ch.z, P.dressed)
  c.set(ch.x, ch.top + 1, ch.z, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  // a slate hood over the front door, its lantern hung under it
  hood(c, fd.at[0], 4, 0, P.roof)

  // --- Pass 5: interior (the plan's furniture) ---------------------------------------------------------------------
  for (const f of FURNITURE) if (has(f)) furnitureParts[f.part](c, f)

  // --- Pass 6: the store (IV) and the ground -----------------------------------------------------------------------
  let dug = null
  if (level >= 4) {
    const C = MASSES.cellar
    dug = cellar(c, C.x0, C.z0, C.x1, C.z1, C.depth, mix(P.cellarWall, 3), P.cellarFloor)
    hatchLadder(c, C.ladder.x, C.ladder.z, -C.depth + 1, 0, C.ladder.facing, P.wood)
    // a spruce post in the store's middle under the hall's floor, beside the runner (the critic's note at ship)
    for (let y = -C.depth + 1; y <= -1; y++) c.set(5, y, 4, `${P.wood}_log`, { axis: 'y' })
  }
  for (const b of OUTSIDE.flowerBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  return { c, dug }
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const { c, dug } = drawLevel(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  if (dug) fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), dug.join(','))
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: `${IDS[level]}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
