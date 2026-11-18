// The Palisade Gate (`palisade_gate`), the recipe: assembles plan.mjs from the shared parts library (../parts,
// ROADMAP 52.3) in passes (block-out and structure, the roof, depth and detail, light) and returns the structure for
// the minecraft-builder skill's runner. Writes furniture.json beside it for rebuild.py. CUT=<y> cuts above y.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, LIGHTS } from './plan.mjs'
import { Canvas, post, fenceGates, gateGable } from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, M = MASSES

  // --- Pass 1: block-out and structure: the jambs on their stone feet, the lintel across the way --------------------
  for (const J of M.jambs) for (let x = J.x0; x <= J.x1; x++) for (const z of [0, 1]) {
    c.set(x, 0, z, P.foot)
    post(c, x, z, 1, M.jambTop, x === J.frameX ? P.frame : P.jamb)
  }
  const L = M.lintel
  // a top slab, so the way under it is 2.5 high: a pillager (1.95) still walks through over a carpet in the gap
  for (let x = L.x0; x <= L.x1; x++) for (const z of L.zs) c.set(x, L.y, z, P.lintel, { type: 'top', waterlogged: 'false' })
  // --- Pass 2: the roof: the gable over both jambs, its two gables boarded round a king post ------------------------
  gateGable(c, M.gable, P.roof, { infill: P.boards, infillProps: { axis: 'x' }, king: P.king, cap: P.cap })
  // --- Pass 3: the gateway: three spruce fence gates, drawn shut, on the back row ----------------------------------
  const G = M.gateway
  fenceGates(c, G.xs, G.y, G.z, P.gate, G.facing)
  // --- Pass 4: light: lanterns hung under the front gable over the way in -------------------------------------------
  for (const p of LIGHTS) c.set(...p, 'lantern', { hanging: 'true', waterlogged: 'false' })
  return c
}

export default function build(api) {
  const id = 'palisade_gate'
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
