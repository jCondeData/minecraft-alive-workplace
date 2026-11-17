// The Sundial, the recipe: assembles plan.mjs from the shared parts library (../parts) in passes (block-out, the dial,
// detail, ground, light) and returns the structure for the minecraft-builder skill's runner. Writes furniture.json
// (empty: a dial has no furniture) for tools/blueprints/rebuild.py.
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES } from './plan.mjs'
import { Canvas, mix, stair, flowerBed, cradleProfile, cradle, rod, pylon } from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, M = MASSES
  const dais = mix(P.dais, 11)

  // --- Pass 1: block-out: the dais and its two steps -------------------------------------------------------------
  const D = M.dais
  c.fill(D.x0, D.y, D.z0, D.x1, D.y, D.z1, dais)
  for (let x = D.steps.x0; x <= D.steps.x1; x++) {
    stair(c, x, D.y, D.steps.front, P.step, 'south') // climbed going south, onto the dais
    stair(c, x, D.y, D.steps.back, P.step, 'north')
  }

  // --- Pass 2: the dial: the cradle, its hour lines, the rod and its pylons ---------------------------------------
  const K = M.cradle
  const profile = cradleProfile(K.cx, K.half)
  cradle(c, { profile, z0: K.z0, z1: K.z1 }, (role, z, p) => {
    if (role === 'face' && K.frames.includes(z)) return P.frame // the end slices: a sandstone lip round the white
    if (role === 'face' && K.ribs.includes(z) && p.y >= K.ribFrom) return P.frame // the rims' middle rib
    return P[role]
  })
  for (const { x, z } of M.pylons) pylon(c, x, z, 1, M.rod.y, P.pylon)
  rod(c, M.rod.x, M.rod.y, M.rod.z0, M.rod.z1, P.rod)

  // --- Pass 3: ground: the four sun beds in the dais's corners ---------------------------------------------------
  M.sunBeds.forEach(([x0, z0], i) => {
    const outerX = x0 === 0 ? 0 : x0 + 1, outerZ = z0 === 0 ? 0 : z0 + 1
    for (let z = z0; z <= z0 + 1; z++) flowerBed(c, x0, x0 + 1, z, i % 2 ? P.flowers : [...P.flowers].reverse(), P.soil)
    c.set(outerX, 1, outerZ, P.bush)
  })
  return c
}

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `sundial_cut${cut}` : 'sundial' } }
}
