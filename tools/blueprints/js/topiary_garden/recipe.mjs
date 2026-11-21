// Topiary Garden, the recipe: assembles plan.mjs from the shared parts library (../parts) in passes (ground, the
// hedge room's walls, the arch, the standard, ornaments and beds, the seat and light) and returns the structure for the
// minecraft-builder skill's runner. Writes an empty furniture.json (a decoration keeps no furniture spots).
// CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, paved, URNS, PENCILS, CUBES, SEAT, BEDS } from './plan.mjs'
import {
  Canvas, mix, clip, hedgeRun, hedgeArch, tieredTopiary, leafColumn, benchRun, plantFlower,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const paving = mix(P.paving, 49)
  const [W, , D] = SIZE

  // --- Pass 1: the ground layer: paving on the walks, lawn and bed soil everywhere else -------------------------
  for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
    if (paved(x, z)) c.set(x, 0, z, paving(x, 0, z))
    else c.set(x, 0, z, P.lawn, { snowy: 'false' })
  }

  // --- Pass 2: the room's walls: clipped yew, low at the front, stepping up to the back ------------------------
  const F = MASSES.frontHedge
  for (const [a, b] of F.runs) hedgeRun(c, 'x', F.z, a, b, 1, F.height, P.hedge)
  for (const [x, z] of F.balls) clip(c, [[x, F.height + 1, z]], P.clipped)
  const S = MASSES.sideHedges
  for (const x of S.xs) hedgeRun(c, 'z', x, 0, D - 1, 1, S.height, P.hedge)
  const B = MASSES.backHedge
  hedgeRun(c, 'x', B.z, B.x0, B.x1, 1, B.height, P.hedge)
  // the exedra sweeps up in the yew itself; a blossom at its peak, clipped balls finishing the back corners
  clip(c, [[4, 6, 8]], P.blossom)
  clip(c, [[0, 5, 8], [8, 5, 8]], P.clipped)

  // --- Pass 3: the arch, two deep through the front hedge -------------------------------------------------------
  const A = MASSES.arch
  hedgeArch(c, { xa: A.xa, xb: A.xb, z0: A.z0, z1: A.z1, spring: A.spring, crown: A.crown }, P.hedge)
  clip(c, [[4, A.spring + 2, 0], [4, A.spring + 2, 1]], P.blossom) // the flowering crown over the way in

  // --- Pass 4: the standard, the hero in the middle of the ring --------------------------------------------------
  const T = MASSES.standard
  const top = T.tiers.length // y of the last tier = y0 + length - 1
  tieredTopiary(c, T.x, T.z, 1, T.tiers, y => (y === top ? P.blossom : P.clipped), P.stem)

  // --- Pass 5: ornaments and beds ---------------------------------------------------------------------------------
  for (const [x, z] of URNS) { c.set(x, 1, z, P.post); c.set(x, 2, z, P.urn) }
  for (const [x, z] of PENCILS) leafColumn(c, x, z, 1, 3, P.clipped, P.blossom)
  for (const [x, z] of CUBES) clip(c, [[x, 1, z], [x, 2, z]], P.clipped)
  BEDS.forEach(([x, z], i) => plantFlower(c, x, 1, z, P.flowers[(i + (x > 4 ? 2 : 0)) % P.flowers.length]))

  // --- Pass 6: the seat and its light ------------------------------------------------------------------------------
  benchRun(c, SEAT, { seat: P.seat, ends: P.post })
  for (const [x, z] of SEAT.lamps) c.set(x, SEAT.y + 1, z, P.lamp, { hanging: 'false', waterlogged: 'false' })
  return c
}

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `topiary_garden_cut${cut}` : 'topiary_garden' } }
}
