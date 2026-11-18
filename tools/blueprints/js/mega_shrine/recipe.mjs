// The Mega Shrine, the recipe: assembles plan.mjs from the shared parts library (../parts) in passes (block-out, the
// stairs and terrace, the arch, the courts, crystals and light) and returns the structure for the
// minecraft-builder skill's runner. Writes furniture.json (empty: a shrine has no furniture) for
// tools/blueprints/rebuild.py. Build: see package.json ("npm run build"); CUT=<y> builds a cutaway above y.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, PEDESTAL } from './plan.mjs'
import { Canvas, mix, stair, slab, roundArch, twinFlight } from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const lamp = (c, x, y, z, id, hanging) => c.set(x, y, z, id, { hanging: String(hanging), waterlogged: 'false' })
const cluster = (c, x, y, z, id) => c.set(x, y, z, id, { facing: 'up', waterlogged: 'false' })

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, M = MASSES
  const low = mix(P.plinth, 52), arch = mix(P.arch, 7)
  const plinth = (x, y, z) => (y === 0 ? low(x, y, z) : P.plinthTop) // weathering low down only, clean bricks above
  const [W] = SIZE

  // --- Pass 1: block-out: the plinth ---------------------------------------------------------------------------
  const L = M.plinth
  c.fill(L.x0, 0, L.z0, L.x1, L.top, L.z1, plinth)

  // --- Pass 2: the Altar Stair (straight up the front to the stand), its cheeks, the Back Stair, the Terrace ----
  const S = M.altar
  for (let x = S.x0; x <= S.x1; x++) for (let i = 0; i < S.n; i++) {
    for (let y = 0; y < i; y++) c.set(x, y, S.z0 + i, plinth)
    stair(c, x, i, S.z0 + i, P.stairs, 'south') // climbed going south, toward the pedestal
  }
  for (const x of M.cheeks) {
    c.set(x, 2, 1, P.wall); lamp(c, x, 3, 1, P.lamp, false) // a lantern on the cheek's post
    stair(c, x, 0, 0, P.stairs, 'south') // the plinth step proud of the cheek
  }
  for (const x of M.corners) { stair(c, x, 0, 1, P.stairs, 'south'); slab(c, x, 0, 0, P.apron) }
  const F = M.backFlight
  twinFlight(c, F, { stairs: P.stairs, fill: plinth, landing: P.terrace })
  c.set(F.cx, 1, F.z, P.panel) // a carved panel in the back's stepped gable
  const T = M.terrace
  c.fill(T.x0, T.y, T.z0, T.x1, T.y, T.z1, P.terrace)
  c.set(PEDESTAL[0], PEDESTAL[1] - 1, PEDESTAL[2], P.chiseled) // under the pedestal (the test's rule)

  // --- Pass 3: the Keystone Arch (two rings deep), its floor, its buttresses ------------------------------------
  const A = M.arch
  roundArch(c, { ...A, z: M.archBack }, { full: arch, stairs: P.stairs, key: P.panel }) // the back ring
  const under = roundArch(c, A, { full: arch, stairs: P.stairs, key: P.chiseled })
  for (const s of [-1, 1]) c.set(A.cx + s * (A.r - 1), T.y, A.z, P.terrace) // the ring's floor out to the piers
  for (const [x, z] of M.buttresses) {
    c.set(x, 0, z, plinth)
    stair(c, x, 1, z, P.stairs, 'south') // leaning on the pier's front
  }

  // --- Pass 4: the Back Court: tile slabs, a bollard at each back flight's foot ---------------------------------
  const B = M.backCourt
  for (let x = 0; x < W; x++) if (!B.bollards.includes(x)) slab(c, x, 0, B.z, P.apron)
  for (const x of B.bollards) { c.set(x, 0, B.z, P.wall); lamp(c, x, 1, B.z, P.lamp, false) } // a lantern on a post

  // --- Pass 5: the pedestal, crystals and light -------------------------------------------------------------------
  c.set(...PEDESTAL, P.pedestal)
  for (const [x, z] of M.geodes) { c.set(x, L.top, z, P.geode); cluster(c, x, L.top + 1, z, P.cluster) }
  for (const s of [-1, 1]) cluster(c, PEDESTAL[0] + s, PEDESTAL[1], PEDESTAL[2], P.cluster) // beside the pedestal
  for (const [x, y, z] of under) if (x !== A.cx) lamp(c, x, y, z, P.lamp, true) // hung from the crown
  return c
}

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `mega_shrine_cut${cut}` : 'mega_shrine' } }
}
