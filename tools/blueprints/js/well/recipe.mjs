// The Well (`well`, Level II `well_2`), the recipe: assembles plan.mjs from the shared parts library (../parts, ROADMAP
// 49.1) in passes (block-out and structure, the water, depth and detail, interior, ground, light) and returns the
// structure for the minecraft-builder skill's runner. LEVEL=1 (default) draws `well`; LEVEL=2 draws `well_2`, which is
// Level I drawn first and grown (the same function, more passes). Writes furniture.json beside it for rebuild.py.
// CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, FURNITURE, LIGHTS } from './plan.mjs'
import {
  Canvas, mix, steppedPlinth, post, slab, stair, roundCurb, pool, flaredGableRoof, bargeTrim, furnitureParts,
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
  const U = M.uprights, F = M.frame
  for (const x of U.xs) post(c, x, U.z, U.y0, U.y1, P.frame) // on the curb's sides: the front stays open to the water
  for (let x = F.cross[0]; x <= F.cross[1]; x++) c.set(x, F.y, U.z, P.frame, { axis: 'x' }) // the cross beam
  for (const x of F.plates) for (let z = M.hat.along[0]; z <= M.hat.along[1]; z++)
    if (z !== U.z) c.set(x, F.y, z, P.frame, { axis: 'z' }) // the eave plates, the roof's foot
  c.set(...F.king, P.frame, { axis: 'y' }) // the king post under the ridge beam
  const H = M.hat
  for (let z = H.beam[0]; z <= H.beam[1]; z++) c.set(F.king[0], H.beamY, z, P.frame, { axis: 'z' }) // the ridge beam, its ends proud

  // the hat: spruce shingles at 45 degrees, the eaves kicked out in dark oak, a dark ridge cap on the beam
  const hat = flaredGableRoof(c, H, P.roof, P.kick)
  for (let z = H.along[0]; z <= H.along[1]; z++) c.set(F.king[0], hat.ridgeY, z, P.ridge)
  // the open gables: no infill, only a dark brace under the rake where the cross-section has a gap (bargeTrim skips
  // the plates and the beam, which are there already)
  for (const z of H.along) bargeTrim(c, 'z', z, [H.across[0] + 1, H.across[1] - 1], hat.under, P.brace)

  // --- Pass 2: the water --------------------------------------------------------------------------------------------
  pool(c, M.mouth.cells, M.mouth.y)

  // --- Pass 3: depth and detail -------------------------------------------------------------------------------------
  // the rim: stone-brick slabs capping the curb (the uprights stand through it)
  for (const [x, z] of ring) if (!c.get(x, C.rimY, z)) slab(c, x, C.rimY, z, P.rim)
  // the windlass: an axle between the uprights over the mouth, a chain rope to the centre source, the crank outside
  const W = M.windlass
  for (let x = W.x0; x <= W.x1; x++) c.set(x, W.y, W.z, P.axle, { axis: 'x' })
  for (const r of W.rope) c.set(...r, 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(...W.crank, 'lever', { face: 'wall', facing: 'east', powered: 'false' })

  // --- Pass 4: interior (the plan's furniture) ----------------------------------------------------------------------
  // --- Pass 5: ground: the tufts in the corners the round curb leaves --------------------------------------------
  for (const [x, z, plant] of M.tufts) { c.set(x, D.y, z, P.soil, { snowy: 'false' }); c.set(x, D.y + 1, z, plant) }

  if (level >= 2) {
    // --- Level II, the Market Well: two dipping basins in the back corners (a held source each, walled against the
    // curb), lamp posts at the dais corners (each standing on a stone foot where Level I had its corner step), benches
    // along the sides, lanterns at the beam ends, the copper vane on the ridge
    for (const [x, z, walls] of M.basins) {
      c.set(x, D.y, z, P.basin)
      for (const [wx, wz] of walls) c.set(wx, D.y + 1, wz, P.basin)
      c.clear(x, D.y + 1, z)
      pool(c, [[x, z]], D.y + 1)
    }
    for (const [x, z] of M.lamps) {
      c.set(x, D.y, z, P.lampFoot)
      for (let y = D.y + 1; y <= D.y + 2; y++) c.set(x, y, z, P.lampPost)
      c.set(x, D.y + 3, z, 'lantern', { hanging: 'false', waterlogged: 'false' })
    }
    c.set(...M.vane, P.vane, { facing: 'up', powered: 'false', waterlogged: 'false' })
  }
  // the lanterns, hung from the ridge beam (Level I's over the water; Level II's at the beam's ends too)
  for (const p of LIGHTS[level]) c.set(...p, 'lantern', { hanging: 'true', waterlogged: 'false' })
  // the furniture (Level II's benches)
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
