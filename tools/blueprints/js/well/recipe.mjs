// The Well (`well`, Level II `well_2`), the recipe: assembles plan.mjs from the shared parts library (../parts, ROADMAP
// 49.1) in passes (block-out and structure, the water, depth and detail, interior, ground, light) and returns the
// structure for the minecraft-builder skill's runner. LEVEL=1 (default) draws `well`; LEVEL=2 draws `well_2`, which is
// Level I drawn first and grown (the same function, more passes). Writes furniture.json beside it for rebuild.py.
// CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, FURNITURE } from './plan.mjs'
import {
  Canvas, mix, steppedPlinth, post, slab, stair, roundCurb, pool, flaredGableRoof, gableWall, bargeTrim, furnitureParts,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(level = 1) {
  const c = new Canvas(SIZE[level])
  const P = PALETTE, M = MASSES
  const dais = mix(P.dais, 3), step = mix(P.step, 5), curb = mix(P.curb, 7)

  // --- Pass 1: block-out and structure ----------------------------------------------------------------------------
  const D = M.dais
  steppedPlinth(c, D.x0, D.z0, D.x1, D.z1, dais, step, D.y)
  const C = M.curb
  const ring = roundCurb(c, C.cx, C.cz, C.r, C.y, curb, M.mouth.cells)
  const U = M.uprights
  for (const z of U.zs) post(c, U.x, z, U.y0, U.y1, P.frame)
  for (const z of U.zs) for (let x = U.tie[0]; x <= U.tie[1]; x++) if (x !== U.x) c.set(x, U.tieY, z, P.frame, { axis: 'x' })
  const H = M.hat
  for (let z = H.beam[0]; z <= H.beam[1]; z++) c.set(U.x, H.beamY, z, P.frame, { axis: 'z' }) // the ridge beam, its ends proud

  // the hat: steep spruce shingles, bell-cast in dark oak at the eaves, the ridge on the beam
  const hat = flaredGableRoof(c, H, P.roof, P.kick)
  for (let z = H.along[0]; z <= H.along[1]; z++) c.set(U.x, hat.ridgeY, z, P.ridge) // a dark ridge cap, the eave line's twin
  // spandrels: plank infill in each gable over the tie beam, round the upright
  for (const z of U.zs)
    gableWall(c, 'z', z, [U.tie[0], U.tie[1]], U.tieY + 1, hat.under, (u, y) => (u === U.x || y >= H.beamY ? null : P.infill))

  // --- Pass 2: the water --------------------------------------------------------------------------------------------
  pool(c, M.mouth.cells, M.mouth.y)

  // --- Pass 3: depth and detail -------------------------------------------------------------------------------------
  // the rim: stone-brick slabs capping the curb (the uprights stand through it)
  for (const [x, z] of ring) if (!c.get(x, C.rimY, z)) slab(c, x, C.rimY, z, P.rim)
  // the windlass: an axle between the uprights, a chain rope to the water, the crank on the front upright toward the road
  const W = M.windlass
  for (let z = W.z0; z <= W.z1; z++) c.set(W.x, W.y, z, P.axle, { axis: 'z' })
  c.set(...W.rope, 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(...W.crank, 'lever', { face: 'wall', facing: 'north', powered: 'false' })

  // --- Pass 4: interior (the plan's furniture) ----------------------------------------------------------------------
  // --- Pass 5: ground: the tufts in the corners the round curb leaves --------------------------------------------
  for (const [x, z, plant] of M.tufts) { c.set(x, D.y, z, P.soil, { snowy: 'false' }); c.set(x, D.y + 1, z, plant) }

  if (level >= 2) {
    // --- Level II, the Market Well: lamp posts at the dais corners (each standing on a stone foot where Level I had
    // its corner step), benches along the sides, lanterns at the beam ends, the copper vane on the ridge
    for (const [x, z] of M.lamps) {
      c.set(x, D.y, z, P.lampFoot)
      for (let y = D.y + 1; y <= D.y + 2; y++) c.set(x, y, z, P.lampPost)
      c.set(x, D.y + 3, z, 'lantern', { hanging: 'false', waterlogged: 'false' })
    }
    c.set(...M.vane, P.vane, { facing: 'up', powered: 'false', waterlogged: 'false' })
  }
  // the furniture (Level I's lantern over the water; Level II's benches and beam-end lanterns)
  for (const f of FURNITURE[level]) furnitureParts[f.part](c, f)
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const id = level === 1 ? 'well' : `well_${level}`
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
