// The Market Cross, the recipe: assembles plan.mjs from the shared parts library (../parts) in passes (block-out: the
// plinth and the drum; openings: the stair cut; depth and detail: the parapet, its posts, the shaft; ground: benches
// and planting) and returns the structure for the minecraft-builder skill's runner. Writes furniture.json (empty: a
// market cross has no room) for tools/blueprints/rebuild.py.
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES } from './plan.mjs'
import { Canvas, mix, stair, slab, octCells } from '../parts/index.mjs'
import { edgeRing8, vertexCells, mercatDrum, crossShaft } from '../parts/market_cross.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, M = MASSES
  const [cx, cz] = M.centre
  const plinthStone = mix(P.plinth, 7), drumStone = mix(P.drum, 3)

  // --- Pass 1: block-out: the plinth and the drum -------------------------------------------------------------------
  const L = M.plinth
  const plinth = octCells(cx, cz, L.r, L.k)
  const beds = new Set(L.beds.map(([x, z]) => `${x},${z}`))
  for (const [x, z] of plinth) {
    const cut = Math.abs(x - cx) + Math.abs(z - cz) === L.k // the cut corners: a half step down to the ground
    if (beds.has(`${x},${z}`)) c.set(x, L.y, z, P.bed)
    else if (cut && L.slabCorners) slab(c, x, L.y, z, P.plinthSlab)
    else c.set(x, L.y, z, plinthStone)
  }
  const D = M.drum
  const drum = octCells(cx, cz, D.r, D.k)
  const ring = edgeRing8(drum)
  mercatDrum(c, { cells: drum, y0: D.y0, y1: D.y1, vertices: vertexCells(ring), medallions: D.medallions },
    { face: drumStone, core: P.core, pilaster: P.pilaster, medallion: P.medallion, cap: P.cap })

  // --- Pass 2: openings: the stair cut into the front, climbed going south -----------------------------------------
  const S = M.stair
  for (const [y, z] of S.flights) for (let x = S.x0; x <= S.x1; x++) {
    stair(c, x, y, z, P.tread, 'south')
    for (let up = y + 1; up <= D.y1; up++) c.clear(x, up, z) // headroom over each tread, down to the drum's top
  }

  // --- Pass 3: depth and detail: the parapet, its posts and the shaft ----------------------------------------------
  const G = M.parapet.gap
  const lampPosts = new Set(M.posts.lamps.map(([x, z]) => `${x},${z}`))
  const piers = new Set(vertexCells(ring).filter(([x, z]) => !(Math.abs(x - cx) === 2 && Math.abs(z - cz) === 2)
    && !lampPosts.has(`${x},${z}`)).map(([x, z]) => `${x},${z}`)) // the face ends; a cut corner's inside cell stays wall
  for (const [x, z] of ring)
    if (!(x >= G.x0 && x <= G.x1 && z >= G.z0 && z <= G.z1)) c.set(x, M.parapet.y, z, piers.has(`${x},${z}`) ? P.pier : P.parapet)
  for (const [x, z] of M.posts.lamps) c.set(x, M.parapet.y + 1, z, P.lamp, { hanging: 'false', waterlogged: 'false' })
  for (const [x, z, rot] of M.posts.pennants) c.set(x, M.parapet.y + 1, z, P.pennant, { rotation: String(rot) })
  const H = M.shaft
  crossShaft(c, { x: cx, z: cz, y0: H.y0, pillarTo: H.pillarTo, capital: H.capital, flag: H.flag, rotation: H.flagRotation },
    { ...P.shaft, lamp: P.lamp, flag: P.flag })

  // --- Pass 4: ground: the benches against the drum, the azaleas in the plinth's corner beds -----------------------
  for (const b of M.benches) for (const [x, z] of b.cells) stair(c, x, L.y + 1, z, P.seat, b.facing)
  for (const [x, z] of L.beds) c.set(x, L.y + 1, z, P.bush)
  return c
}

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `market_cross_cut${cut}` : 'market_cross' } }
}
