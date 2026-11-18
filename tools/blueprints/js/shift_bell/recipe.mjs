// The Shift Bell, the recipe: assembles plan.mjs from the shared parts library (../parts) in passes (block-out, the
// head and chamber, the cap, light) and returns the structure for the minecraft-builder skill's runner. Writes
// furniture.json (empty: a yard bell has no furniture) for tools/blueprints/rebuild.py.
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, BELL, LIGHTS } from './plan.mjs'
import { Canvas, mix, facedDais, flaredHead, bellChamber, hangingLantern } from '../parts/index.mjs'
import { hipCap } from '../parts/tinker.mjs' // (index.mjs exports two hipCaps; this is the pyramid one)

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, M = MASSES

  // --- Pass 1: block-out: the dais and the waist ------------------------------------------------------------------
  facedDais(c, M.dais, { block: mix(P.dais, 5), footing: P.footing, step: P.step })
  const [ax, ay, az] = M.waist.at
  c.set(ax, ay, az, P.stem, { facing: M.waist.facing }) // the smith's anvil, on its footing block

  // --- Pass 2: the head and the chamber ---------------------------------------------------------------------------
  flaredHead(c, M.head, { stairs: P.corbel, corner: P.corbelCorner, middle: P.floor })
  bellChamber(c, M.chamber, { post: P.post }, BELL)

  // --- Pass 3: the cap (its middle block carries the bell) and the spire ------------------------------------------
  hipCap(c, M.cap.cx, M.cap.cz, M.cap.half, M.cap.y, P.roof, { apex: [P.spire, { facing: 'up', powered: 'false', waterlogged: 'false' }] })

  // --- Pass 4: light: a lantern under each corner of the head ------------------------------------------------------
  for (const [x, y, z] of LIGHTS) hangingLantern(c, x, y, z, P.lamp)
  return c
}

export default function build(api) {
  const c = draw()
  const [bx, by, bz] = BELL.at
  if (c.name(bx, by, bz) !== 'minecraft:bell' || c.name(bx, by + 1, bz) === 'minecraft:air')
    throw new Error('the bell must be at (1, 3, 1) with a block over it (ReformStories.placeBell)')
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": []}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `shift_bell_cut${cut}` : 'shift_bell' } }
}
