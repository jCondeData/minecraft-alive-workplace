// Park Bench, the recipe: assembles plan.mjs (the Blossom Seat) from the shared parts library (../parts) in passes
// (block-out and structure, seat, the tree, ground, light) and returns the structure for the minecraft-builder skill's
// runner. Also writes furniture.json (empty: a decoration keeps no furniture spots).
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES } from './plan.mjs'
import { Canvas, mix, raisedBed, benchRun, tree, canopy } from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, M = MASSES
  const stone = mix(P.stone, 11), paving = mix(P.paving, 5)

  // --- Pass 1: block-out and structure: the bed and the apron ----------------------------------------------------
  const s = M.seat
  raisedBed(c, M.bed, {
    rim: stone, cap: P.cap,
    soil: (x, z) => (x === M.trunk.x && z === M.trunk.z ? P.rootSoil : P.soil),
    skipCap: (x, z) => z === M.bed.z0 && x >= s.x0 && x <= s.x1, // the bench's back stands there
  })
  const bd = M.bed
  for (let x = bd.x0; x <= bd.x1; x++) for (let z = bd.z0; z <= bd.z1; z++) // the bed's base course, a ring (the soil
    if (x === bd.x0 || x === bd.x1 || z === bd.z0 || z === bd.z1) c.set(x, 0, z, stone) // above needs nothing under it)
  c.fill(M.plinth.x0, 0, M.plinth.z, M.plinth.x1, 0, M.plinth.z, stone) // the seat's plinth, one with it
  const a = M.apron
  // the front row slabs (a half step up off the grass), the row before the seat full blocks: the sitter's feet stand at
  // y 1, half a block under the stair seat (y 1.5), the height of a real bench (the critic's note)
  for (let x = a.x0; x <= a.x1; x++) for (let z = a.z0; z <= a.z1; z++)
    if (z === a.z1) c.set(x, 0, z, stone)
    else c.set(x, 0, z, paving, { type: 'bottom', waterlogged: 'false' })

  // --- Pass 2: the seat: stair seats backed by trapdoor slats on the bed's rim, chiseled arms ----------------------
  benchRun(c, s, { seat: P.seat, back: P.back, ends: P.arm, endCap: P.cap })

  // --- Pass 3: the tree: trunk out of the bed, limbs, the crown --------------------------------------------------
  const t = M.trunk
  const logs = []
  for (let y = t.y0; y <= t.y1; y++) logs.push([t.x, y, t.z, 'y'])
  tree(c, logs, [], P.log, P.leaf)
  tree(c, M.limbs, [], P.limb, P.leaf)
  // the lantern first, so the crown leaves its cell
  const [lx, ly, lz] = M.lantern
  c.set(lx, ly, lz, P.lantern, { hanging: 'true', waterlogged: 'false' })
  tree(c, [], canopy(M.crown), P.log, P.leaf)

  // --- Pass 4: ground: petals in the bed either side of the trunk -------------------------------------------------
  for (const [x, y, z, facing, n] of M.petals) c.set(x, y, z, P.petals, { facing, flower_amount: String(n) })
  return c
}

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + c.spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `park_bench_cut${cut}` : 'park_bench' } }
}
