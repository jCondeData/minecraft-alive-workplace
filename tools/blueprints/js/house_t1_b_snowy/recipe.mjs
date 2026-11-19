// Daub House, snowy look (49.10d), the recipe: assembles plan.mjs in passes (block-out and structure, roofs and gables,
// openings, depth and detail, interior, snow and ground) on the parts library (../parts), the kept insides from
// ../parts/daub_looks.mjs. Writes furniture.json. Build: build.sh. CUT=<y>: a cutaway.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, WINDOWS, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, stair, gableRoof, gableWall, window, quoins, snowLoad, snowSkirt, stormPorch, frontDormer, deepWindow,
  wideStack,
} from '../parts/index.mjs'
import { daubInterior, daubSpots } from '../parts/daub_looks.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, H = MASSES.hall, K = MASSES.kitchen
  const plinth = mix(P.plinth, 3), stack = mix(P.stack, 5)
  const ring = (x, z) => x === H.x0 || x === H.x1 || z === H.z0 || z === H.z1

  // --- Pass 1: block-out and structure: the hall's ground storey, its band, the kitchen's walls ----------------------
  for (let x = H.x0; x <= H.x1; x++) for (let z = H.z0; z <= H.z1; z++) {
    const thick = z <= H.z0 + 1 || z >= H.z1 - 1
    if (!ring(x, z) && !thick) continue
    c.set(x, 0, z, plinth)
    for (let y = 1; y <= H.groundTop; y++) c.set(x, y, z, P.wall)
    if (ring(x, z)) c.set(x, H.bandY, z, P.band)
  }
  for (let x = K.x0; x <= K.x1; x++) for (let z = K.z0; z <= K.z1; z++) {
    if (!(x === K.x1 || z === K.z0 || z === K.z1)) continue
    c.set(x, 0, z, plinth)
    for (let y = 1; y <= K.wallTop; y++) c.set(x, y, z, y === H.bandY ? P.band : P.wall)
  }
  c.fill(K.x0, K.ceilingY, K.z0 + 1, K.x1 - 1, K.ceilingY, K.z1 - 1, P.ceiling) // the kitchen's ceiling (its lamp's)

  // --- Pass 2: roofs and gables --------------------------------------------------------------------------------------
  const main = gableRoof(c, H.roof, P.roof)
  // knee walls under the eaves' first course, the gables up to the roof's underside (the wall stops under each stair)
  for (let x = H.x0; x <= H.x1; x++) for (const z of [H.z0, H.z1]) for (let y = H.bandY + 1; y < main.under[z]; y++) c.set(x, y, z, P.wall)
  for (const x of [H.x0, H.x1]) gableWall(c, 'x', x, [H.z0, H.z1], H.bandY + 1, main.under, () => P.wall)
  const wing = gableRoof(c, K.roof, P.roof, (x, y, z) => !c.get(x, y, z))
  gableWall(c, 'x', K.x1, [K.z0, K.z1], K.wallTop + 1, wing.under, () => P.wall)
  // the hall's east rake overhangs the kitchen's roof: carry the hall's gable out under it, so no slot shows
  for (let z = K.roof.across[0]; z <= K.roof.across[1]; z++)
    for (let y = wing.under[z] + 1; y < main.under[z]; y++) if (!c.get(H.x1 + 1, y, z)) c.set(H.x1 + 1, y, z, P.roof.full) // dark: the rake reads as one band

  // --- Pass 3: openings: the deep front windows, the shuttered ones, the dormer, the storm porch ---------------------
  for (const w of WINDOWS) {
    if (w.deep) deepWindow(c, w.cells, w.out, { glass: P.glass, lintel: P.lintel, snow: P.snow, layers: '1' })
    else window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, sill: P.sill })
    if (w.dress === 'shutters') { // a stone sill under the shutters too
      const [x, y, z] = w.cells[0], d = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }[w.out]
      stair(c, x + d[0], y - 1, z + d[1], P.sill, { north: 'south', south: 'north', west: 'east', east: 'west' }[w.out], 'top')
    }
  }
  frontDormer(c, MASSES.dormer, P.roof, { cheek: P.post, infill: P.dormerFace, glass: P.glass, verge: P.roof.stairs, vergeTop: P.roof.full, snow: P.snow })
  // the dormer's pocket opens into the loft over the children's rug (its boards would leave the rug a low ceiling)
  for (let z = MASSES.dormer.face + 2; z <= 4; z++) if (c.name(MASSES.dormer.cx, 7, z) === `minecraft:${P.dormerFace}`) c.clear(MASSES.dormer.cx, 7, z)
  const pc = MASSES.porch
  for (let y = 1; y <= 2; y++) c.clear(pc.x, y, pc.z + 1) // the door's reveal through the outer leaf
  stormPorch(c, pc.x, pc.z, pc.top, { cheek: plinth, lintel: P.lintel })
  c.set(pc.x - 1, 0, pc.z, plinth); c.set(pc.x + 1, 0, pc.z, plinth); c.set(pc.x, 0, pc.z + 1, plinth)

  // --- Pass 4: depth and detail: quoins, the stack, firewood ----------------------------------------------------------
  quoins(c, [[H.x0, H.z0], [H.x1, H.z0], [H.x0, H.z1], [H.x1, H.z1], [K.x1, K.z0], [K.x1, K.z1]], 1, H.groundTop, P.quoin, P.quoinAlt)
  const S = MASSES.stack
  for (let x = S.x0; x <= S.x1; x++) for (let y = 0; y <= S.breastTop; y++) c.set(x, y, S.breastZ, stack)
  // the stack three flues wide through the slope, stepping in to its middle flue above the ridge's height
  wideStack(c, { x0: S.x0, x1: S.x1, z: S.z, y0: 1, y1: S.shoulder - 1 }, stack, { flues: [], cap: null })
  stair(c, S.x0, S.shoulder, S.z, P.sill.replace('_stairs', '_stairs'), 'east'); stair(c, S.x1, S.shoulder, S.z, P.sill, 'west')
  wideStack(c, { x0: S.flues[0], x1: S.flues[0], z: S.z, y0: S.shoulder, y1: S.top }, stack, { flues: S.flues })
  for (let x = S.x0; x <= S.x1; x++) c.set(x, S.breastTop, S.breastZ, P.band) // the band carried round the breast
  // firewood: one wood, logs laid along the kitchen's wall (bark out), below the window's sill
  for (const f of OUTSIDE.firewood) for (let x = f.x0; x <= f.x1; x++) {
    c.set(x, 0, f.z, plinth)
    for (let y = 1; y <= f.top; y++) c.set(x, y, f.z, P.firewood, { axis: 'x' })
  }

  // --- Pass 5: interior: the shipped Daub House's insides, cell for cell ----------------------------------------------
  daubInterior(c, P, { step: OUTSIDE.step })

  // --- Pass 6: snow: on the ridges, along the eaves, the dormer's own; the rakes keep their stairs; one drift ----------
  const D = MASSES.dormer
  const dormer = (x, y, z) => x >= D.cx - 2 && x <= D.cx + 2 && z <= D.face + 2 && y >= D.y0 + 1
  const rake = (x, y, z) => x === H.roof.along[0] || x === H.roof.along[1] || x === K.roof.along[1]
  const roofStairs = `minecraft:${P.roof.stairs}`
  // (a 45-degree roof has no full block under its stairs: only the true eave rows may take the snow-laden lip)
  const eave = (x, y, z) => y === H.roof.eaveY && (z === H.roof.across[0] || z === H.roof.across[1] ||
    (x >= K.x0 && (z === K.roof.across[0] || z === K.roof.across[1])))
  snowLoad(c, P.roof, P.snow, { eaves: true, tread: () => false, eaveSkip: (x, y, z) => dormer(x, y, z) || !eave(x, y, z),
    skip: (x, y, z) => dormer(x, y, z) || (rake(x, y, z) && c.name(x, y, z) === roofStairs) })
  snowSkirt(c, P.snow, OUTSIDE.bare)
  // 49.10d critic round 2: close the dormer's apex notch, so the birch behind it doesn't show above the dormer
  if (c.name(MASSES.dormer.cx, 8, 0) === 'minecraft:air') c.set(MASSES.dormer.cx, 8, 0, P.roof.full)
  return c
}

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + daubSpots(c).map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `house_t1_b_cut${cut}` : 'house_t1_b' } }
}
