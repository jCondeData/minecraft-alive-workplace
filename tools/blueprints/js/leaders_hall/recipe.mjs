// The Leader's Hall (49 rebuild), the recipe: assembles plan.mjs from the parts library (../parts) in passes
// (block-out and structure, openings, depth and detail, interior, ground, light). Writes furniture.json beside it.
// CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, FURNITURE, JOB, LAMP_POSTS, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, stair, plinth, post, beamRing, wallPanel, window, door, gableRoof, gableWall, bargeTrim, flowerBed,
  lampPost, furnitureParts, sparringRing, moonWindow, piers, dais, standard,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const wall = mix(P.wall, 11), base = mix(P.plinth, 5), paving = mix(P.paving, 3)
  const N = MASSES.nave, W = N.walls, Po = MASSES.porch

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  plinth(c, W.x0, W.z0, W.x1, W.z1, base)
  plinth(c, Po.x0, Po.z0, Po.x1, Po.z1, base)
  sparringRing(c, MASSES.ring, { inner: P.ringInner, border: P.ringBorder, paving })
  wallPanel(c, [W.x0, W.z0], [W.x1, W.z0], 1, N.wallTop, wall)
  wallPanel(c, [W.x0, W.z1], [W.x1, W.z1], 1, N.wallTop, wall)
  wallPanel(c, [W.x0, W.z0], [W.x0, W.z1], 1, N.wallTop, wall)
  wallPanel(c, [W.x1, W.z0], [W.x1, W.z1], 1, N.wallTop, wall)
  beamRing(c, W.x0, W.z0, W.x1, W.z1, N.beamY, P.frame)
  piers(c, 'x', W.x0 - 1, N.piers.at, 0, N.piers.y1, P.pier, { cap: P.pierCap, out: 'west' })
  piers(c, 'x', W.x1 + 1, N.piers.at, 0, N.piers.y1, P.pier, { cap: P.pierCap, out: 'east' })
  piers(c, 'z', W.z1 + 1, N.piers.back, 0, N.piers.y1, P.pier, { cap: P.pierCap, out: 'south' }) // round the back wall too (critic's polish)
  const main = gableRoof(c, N.roof, P.roof)
  const gable = (u, y) => (y === N.wallTop && (u === W.x0 || u === W.x1) ? null : wall)
  gableWall(c, 'z', W.z0, [W.x0, W.x1], N.wallTop, main.under, gable)
  gableWall(c, 'z', W.z1, [W.x0, W.x1], N.wallTop, main.under, gable)

  // the porch: two dark posts, a beam ring, its own small gable against the nave's front
  for (const x of [Po.x0, Po.x1]) post(c, x, Po.z0, 1, Po.postTop, P.frame)
  beamRing(c, Po.x0, Po.z0, Po.x1, Po.z1, Po.beamY, P.frame)
  const porchRoof = gableRoof(c, Po.roof, P.roof, (x, y, z) => !c.get(x, y, z))
  gableWall(c, 'z', Po.z0, [Po.x0, Po.x1], Po.beamY + 1, porchRoof.under, () => wall)

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, fd.out, { wood: P.wood })
  for (const [x, y, z] of OPENINGS.steps) stair(c, x, y, z, P.daisStep, 'south')
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, null, { glass: P.glass })
  for (const r of OPENINGS.roundWindows) moonWindow(c, 'z', r.line, 7, r.cy, { glass: P.glass, frame: P.roundFrame })

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  // dark sills under the long walls' windows, a dark beam over each, so the bays read as tall openings
  for (const w of OPENINGS.windows.filter(w => w.out === 'west' || w.out === 'east')) {
    const dx = w.out === 'west' ? -1 : 1
    for (const [x, y, z] of w.cells.filter(p => p[1] === 2)) stair(c, x + dx, 1, z, P.pierCap, w.out === 'west' ? 'east' : 'west', 'top')
  }
  bargeTrim(c, 'z', N.roof.along[0], N.roof.across, main.under, `${P.trim}_stairs`)
  bargeTrim(c, 'z', N.roof.along[1], N.roof.across, main.under, `${P.trim}_stairs`)
  bargeTrim(c, 'z', Po.z0, Po.roof.across, porchRoof.under, `${P.trim}_stairs`)
  c.set(7, 6, 1, 'lantern', { hanging: 'true', waterlogged: 'false' }) // under the porch ridge
  c.set(7, 6, 0, `${P.accent}_wall_banner`, { facing: 'north' }) // the Leader's colour on the porch gable

  // --- Pass 4: interior -----------------------------------------------------------------------------------------
  const D = MASSES.dais
  dais(c, D, { top: P.daisTop, step: P.daisStep })
  c.set(JOB.at[0], JOB.at[1], JOB.at[2], JOB.block, { facing: JOB.facing })
  for (const [x, y, z] of LAMP_POSTS) lampPost(c, x, y, z, `${P.wood}_fence`, 1)
  for (const f of FURNITURE) furnitureParts[f.part](c, f)

  // --- Pass 5: ground ---------------------------------------------------------------------------------------------
  for (const b of OUTSIDE.flowerBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  for (const [x, y, z] of OUTSIDE.standards) standard(c, x, y, z, { pole: `${P.wood}_fence`, colour: P.accent, face: 'north', height: 4 })
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `leaders_hall_cut${cut}` : 'leaders_hall' } }
}
