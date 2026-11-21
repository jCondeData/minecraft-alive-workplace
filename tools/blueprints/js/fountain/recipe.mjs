// The Fountain (`fountain`), the recipe: assembles plan.mjs from the shared parts library (../parts, ROADMAP 49) in
// passes (block-out and structure, the water, depth and detail, ground, light) and returns the structure for the
// minecraft-builder skill's runner. Writes furniture.json beside it for rebuild.py (no spots: an open fountain).
// CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, PLANTS, LAMP } from './plan.mjs'
import { Canvas, mix, stair, slab, post, octCells, ringCells, bowl, pool, lampPost } from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, M = MASSES
  const [cx, cz] = M.centre
  const base = mix(P.base, 11), step = mix(P.step, 13)

  // --- Pass 1: block-out and structure ----------------------------------------------------------------------------
  // the basin's foot: a rounded octagon of stone at y 0 (its floor under the water and the curb's foot)
  const B = M.basin
  const outer = octCells(cx, cz, ...B.outer), water = octCells(cx, cz, ...B.water).filter(([x, z]) => x !== cx || z !== cz)
  for (const [x, z] of outer) c.set(x, B.y, z, base)
  // the steps: the octagon's four straight sides, a stair rising toward the basin
  const S = M.steps
  for (let u = -S.half; u <= S.half; u++) {
    stair(c, cx + u, S.y, cz - S.r, step, 'south'); stair(c, cx + u, S.y, cz + S.r, step, 'north')
    stair(c, cx - S.r, S.y, cz + u, step, 'east'); stair(c, cx + S.r, S.y, cz + u, step, 'west')
  }
  // the column: its foot in the water, the shaft up to the tazza, the shaft between the bowls
  const K = M.column
  c.set(cx, K.foot, cz, P.foot)
  for (const [y0, y1] of K.shafts) for (let y = y0; y <= y1; y++) c.set(cx, y, cz, P.shaft)

  // --- Pass 2: the water: the basin (curb ring capped white), the tazza, the crown -------------------------------
  const curb = ringCells(outer, octCells(cx, cz, ...B.water))
  for (const [x, z] of curb) slab(c, x, B.waterY, z, P.rim)
  pool(c, water, B.waterY)
  const T = M.tazza
  bowl(c, { cx, cz, y: T.y, water: octCells(cx, cz, ...T.water).filter(([x, z]) => x !== cx || z !== cz), core: [[cx, cz]], reach: 4 },
    { boss: P.boss, flare: P.flare, lip: P.rim })
  // the tazza's four inner corners are full blocks, not slabs: each touches two of the bowl's sources, so a corner
  // left open while the water goes in fills with a new source, and a slab put down there comes out waterlogged and
  // spills over the side (BuilderGameTests.buildsFountain)
  for (const [dx, dz] of [[1, 1], [1, -1], [-1, 1], [-1, -1]]) c.set(cx + dx, T.y, cz + dz, P.boss)
  const R = M.crown
  bowl(c, { cx, cz, y: R.y, water: [[cx, cz]], reach: 4 }, { boss: P.boss, flare: P.flare, lip: P.rim })
  c.set(cx, R.y - 1, cz, P.boss) // the crown's boss on the shaft, under its one source
  // the tazza's lanterns: one hung from each of its four tips, over the basin's water (lit from above at night)
  // (each tip's flare is a full block, a boss the lantern hangs from: a stair's underside has no middle to hold it)
  for (const [dx, dz] of [[2, 0], [-2, 0], [0, 2], [0, -2]]) {
    c.set(cx + dx, T.y - 1, cz + dz, P.boss)
    c.set(cx + dx, T.y - 2, cz + dz, 'lantern', { hanging: 'true', waterlogged: 'false' })
  }

  // --- Pass 3: ground: the planters in the octagon's cut corners --------------------------------------------------
  M.planters.forEach(([ox, oz, sx, sz], i) => {
    const inner = [ox - sx, oz - sz]
    for (const [x, z] of [[ox, oz], [ox - sx, oz], [ox, oz - sz], inner]) c.set(x, 0, z, P.soil)
    c.set(ox, 1, oz, PLANTS.bush)
    c.set(ox - sx, 1, oz, PLANTS.flowers[i][0]); c.set(ox, 1, oz - sz, PLANTS.flowers[i][1])
    // --- Pass 4: light: a lamp post on the planter's inner corner, beside the basin
    lampPost(c, inner[0], LAMP.postY[0], inner[1], P.lampPost, LAMP.postY[1] - LAMP.postY[0] + 1)
  })
  return c
}

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `fountain_cut${cut}` : 'fountain' } }
}
