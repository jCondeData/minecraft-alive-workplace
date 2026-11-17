// The Lily Pond, the recipe: assembles plan.mjs from the shared parts library (../parts, ROADMAP 49) in passes
// (block-out and structure, the water, depth and detail, ground and planting, light) and returns the structure for
// the minecraft-builder skill's runner. Writes furniture.json (no spots: a pond has no furniture) for rebuild.py.
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, PLAN, WATER_Y, STAGE, BOULDERS, TREE, PADS, PLANTS } from './plan.mjs'
import { Canvas, mix, slab, stair, pool, lampPost, tree, canopy, lilyPads, boulders } from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const base = mix(P.base, 11), kerb = mix(P.kerb, 5), rock = mix(P.rock, 13), bed = mix(P.bed, 3)
  const pathStone = mix(P.path, 17), leaf = mix(P.leaves, 23)
  const lawn = (x, y, z) => c.set(x, y, z, P.lawn, { snowy: 'false' })
  const cells = (...codes) => PLAN.flatMap((row, z) => [...row].flatMap((ch, x) => (codes.includes(ch) ? [[x, z, ch]] : [])))

  // --- Pass 1: block-out and structure: the knoll's base, the bank, the kerb, the pond bed -----------------------
  for (const [x, z] of cells('K', 'G', 'M', 'R', 'D', 'T')) c.set(x, 0, z, base)
  for (const [x, z] of cells('W', 'L')) c.set(x, 0, z, bed)
  for (const [x, z] of cells('K')) c.set(x, 1, z, kerb)
  for (const [x, z] of cells('G')) lawn(x, 1, z)
  for (const [x, z] of cells('M')) c.set(x, 1, z, P.moss)
  for (const [x, z] of cells('R')) c.set(x, 1, z, rock)

  // the stage's frame: log posts standing in the pond, a pier of logs and a plank on the bank
  for (const [x, z] of cells('L')) c.set(x, 1, z, `${P.wood}_log`, { axis: 'y' })
  for (const [x, z] of cells('D')) {
    const kind = STAGE.pier[z]
    c.set(x, 1, z, kind === 'log' ? `${P.wood}_log` : `${P.wood}_planks`, kind === 'log' ? { axis: 'y' } : {})
  }
  // the tree's trunk and branch go in with the structure: the crown hangs off them later
  tree(c, TREE.logs, [], P.log, leaf)

  // --- Pass 2: the water: still sources, every one shut in (pool() throws if one could run out) --------------------
  pool(c, cells('W').map(([x, z]) => [x, z]), WATER_Y)

  // --- Pass 3: depth and detail: the stage's deck and step, the boulder heap, the crown ---------------------------
  for (const [x, z] of STAGE.deck) slab(c, x, 2, z, `${P.wood}_slab`)
  const [sx, sy, sz, sf] = STAGE.step
  stair(c, sx, sy, sz, `${P.wood}_stairs`, sf)
  boulders(c, BOULDERS.stones, rock, BOULDERS.caps, P.cap)
  const [lx, ly, lz] = TREE.lantern
  c.set(lx, ly, lz, 'lantern', { hanging: 'true', waterlogged: 'false' }) // before the crown, so it keeps its cell
  tree(c, [], canopy(TREE.crown), P.log, leaf)

  // --- Pass 4: ground and planting: the lawn ring, the path stones, flowers in their own soil, the pads ----------
  const flower = mix(P.flowers.map(f => [f, 1]), 29)
  for (const [x, z] of cells('f')) { lawn(x, 0, z); c.set(x, 1, z, flower(x, 1, z)) }
  for (const [x, z] of cells('g')) lawn(x, 0, z)
  for (const [x, z] of cells('p')) c.set(x, 0, z, pathStone(x, 0, z)) // flush with the lawn, a step below the kerb
  for (const [x, z] of cells('s')) c.set(x, 0, z, base)
  for (const [x, z] of cells('r')) c.set(x, 0, z, rock)
  for (const [x, y, z, plant] of PLANTS) {
    const soil = c.name(x, y - 1, z)
    if (soil !== 'minecraft:grass_block' && soil !== 'minecraft:moss_block') throw new Error(`${plant} at ${x},${y},${z} has no soil`)
    c.set(x, y, z, plant)
  }
  lilyPads(c, PADS, WATER_Y + 1, P.pad)

  // --- Pass 5: light: a lantern on a post at the stage's front corner (the branch's lantern is already hung) ------
  const [px, py, pz, ph] = STAGE.lamp
  lampPost(c, px, py, pz, `${P.wood}_fence`, ph)
  return c
}

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `lily_pond_cut${cut}` : 'lily_pond' } }
}
