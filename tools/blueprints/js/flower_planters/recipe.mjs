// Flower Planters (the Bower), the recipe: assembles plan.mjs from the shared parts library (../parts, ROADMAP 49) in
// passes (block-out, structure, depth, planting, ground, light) and returns the structure for the minecraft-builder
// skill's runner. Writes furniture.json (no spots: a decoration hands out no furniture) for rebuild.py.
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, PLAN, FLOWERS, PERGOLA, LANTERNS, BENCH } from './plan.mjs'
import { Canvas, mix, slab, tieredBed, boxBoards, beamRun, kneeBrace, hangingLantern, benchRun } from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = mix(P.stone, 7), soil = mix(P.soil, 11), paving = mix(P.paving, 13)
  const cells = (...codes) => PLAN.flatMap((row, z) => [...row].flatMap((ch, x) => (codes.includes(ch) ? [[x, z]] : [])))
  const deal = (list, seed) => { const m = mix(list, seed); return (x, z) => m(x, 0, z) }
  const underBeam = x => PERGOLA.beams.xs.includes(x)
  const pergolaBay = (x, z) => x >= 2 && x <= 6 && z >= 3 && z <= 6
  const gate = deal(FLOWERS.gate, 3), side = deal(FLOWERS.side, 5), bay = deal(FLOWERS.bay, 17)
  const back = (x, z) => FLOWERS.back[x], backShort = deal(FLOWERS.backShort, 23)

  // --- Pass 1: block-out: the posts' plinths, the back bed's piers, the bench (before the beds round it) --------
  for (const [x, z] of cells('P')) c.set(x, 0, z, stone)
  for (const [x, z] of cells('#')) { c.fill(x, 0, z, x, 2, z, stone); slab(c, x, 3, z, P.cap) }
  benchRun(c, BENCH, { seat: `${P.wood}_stairs` })

  // --- Pass 2: structure: posts, beams along z, lintels, rafters across ---------------------------------------------
  for (const [x, z] of PERGOLA.posts) for (let y = 1; y <= PERGOLA.postTop; y++) c.set(x, y, z, P.post, { axis: 'y' })
  const B = PERGOLA.beams, L = PERGOLA.lintels, R = PERGOLA.rafters
  for (const x of B.xs) beamRun(c, 'z', x, B.z0, B.z1, B.y, P.post, `${P.wood}_stairs`)
  for (const z of L.zs) beamRun(c, 'x', z, L.x0, L.x1, L.y, P.post, `${P.wood}_stairs`, false)
  for (const z of R.zs) beamRun(c, 'x', z, R.x0, R.x1, R.y, P.rafter, `${P.wood}_stairs`, false)

  // --- Pass 3: depth: knee braces in every bay (an arch on each side), the gate boxes' boards ------------------------
  for (const [x, y, z, toward] of PERGOLA.braces) kneeBrace(c, x, y, z, `${P.wood}_stairs`, toward)
  boxBoards(c, cells('t').map(([x, z]) => [x, 0, z]), P.wood, 'north')

  // --- Pass 4: planting: three tiers of beds, each flower in its own soil --------------------------------------------
  tieredBed(c, cells('1'), 1, { base: stone, soil, plants: gate })
  tieredBed(c, cells('2'), 2, { base: stone, soil, plants: (x, z) => (pergolaBay(x, z) ? bay(x, z) : side(x, z)) })
  tieredBed(c, cells('3'), 3, { base: stone, soil, plants: (x, z) => (underBeam(x) ? backShort(x, z) : back(x, z)) })

  // --- Pass 5: ground: the path and the court, slabs flush with the gate boxes' boards ------------------------------
  for (const [x, z] of cells('p')) slab(c, x, 0, z, paving(x, 0, z))

  // --- Pass 6: light: two lanterns at the gate under the beams' ends, one on a chain over the court ----------------
  for (const { at: [x, y, z], chain } of LANTERNS) {
    if (c.get(x, y, z)) throw new Error(`lantern spot ${x},${y},${z} is taken`)
    hangingLantern(c, x, y, z, P.lantern, chain)
  }
  return c
}

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `flower_planters_cut${cut}` : 'flower_planters' } }
}
