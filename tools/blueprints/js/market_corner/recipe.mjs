// Market Corner, the recipe: assembles plan.mjs from the shared parts library (../parts) in passes (block-out, the
// canvas, depth and detail, goods, ground and light) and returns the structure for the minecraft-builder skill's runner.
// Also writes furniture.json (empty: the stall has no seats, tables or beds) for tools/blueprints/rebuild.py.
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, BACK, PALETTE, MASSES, GOODS, LAMPS, OUTSIDE } from './plan.mjs'
import { Canvas, mix, plinth, post, railing, hangingLantern, trapdoor } from '../parts/index.mjs'
import { cornerDepth, lAwning, valance } from '../parts/market_awning.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const [W, , D] = SIZE
  const depth = (x, z) => cornerDepth(x, z, BACK.x, BACK.z)
  const S = MASSES.screen, M = MASSES.mast, K = MASSES.canvas

  // --- Pass 1: block-out --------------------------------------------------------------------------------------------
  // Paving over the whole plot, a spruce trading floor under the counters, cobble under the screens
  plinth(c, 0, 0, W - 1, D - 1, mix(P.paving, 11))
  for (let i = 0; i < W - 1; i++) { c.set(i, 0, 0, P.kerb); c.set(0, 0, i, P.kerb) } // the kerb on the open sides
  for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
    const d = depth(x, z)
    if (d === MASSES.counters.depth) c.set(x, 0, z, P.deck)
    else if (d === 0) c.set(x, 0, z, mix(P.footing, 5)) // the screens stand on their own footing
  }
  // The screens on the two street sides: boards between dark posts, the mast in their corner
  for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) if (depth(x, z) === 0)
    for (let y = S.y0; y <= S.y1; y++) {
      if (y === S.footing) c.set(x, y, z, mix(P.footing, 5))
      else if (y === S.plate) c.set(x, y, z, P.frame, { axis: x === BACK.x ? 'z' : 'x' })
      else c.set(x, y, z, P.boards)
    }
  for (const [x, z] of S.posts) post(c, x, z, S.y0, S.y1, P.frame)
  post(c, M.x, M.z, 1, M.top, P.frame)

  // --- Pass 2: the canvas --------------------------------------------------------------------------------------------
  const awning = { bx: BACK.x, bz: BACK.z, x0: 0, z0: 0, top: K.top, reach: K.reach }
  lAwning(c, { ...awning, skip: (x, y, z) => x === M.x && z === M.z }, P)
  for (const [x, z] of MASSES.poles) railing(c, [[x, 1, z], [x, 2, z]], P.wood) // the canvas's two outer front poles

  // --- Pass 3: depth and detail --------------------------------------------------------------------------------------
  // the frame rail along both arms under the canvas's middle row (the mast takes the corner's place in it)
  for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) if (depth(x, z) === MASSES.counters.depth)
    c.set(x, MASSES.rail.y, z, P.rail, { axis: x === BACK.x - 1 && z !== BACK.z - 1 ? 'z' : 'x' })
  valance(c, awning, P) // the scalloped hem: red and white banners on the canvas's front edge

  // --- Pass 4: the goods on the counters -----------------------------------------------------------------------------
  for (const [x, z, counter, goods] of GOODS) {
    c.set(x, 1, z, counter, counter.endsWith('_log') || counter === 'hay_block' ? { axis: 'y' } : {})
    if (!goods) continue
    const props = goods === 'hay_block' ? { axis: 'y' }
      : goods === 'pumpkin' || goods === 'melon' || goods.includes('carpet') || goods.startsWith('potted') ? {}
        : goods === 'cake' ? { bites: '0' }
          : goods === 'decorated_pot' ? { facing: x === 6 ? 'west' : 'north', cracked: 'false', waterlogged: 'false' } : {}
    c.set(x, 2, z, goods, props)
  }

  // --- Pass 5: ground and light --------------------------------------------------------------------------------------
  for (const [x, y, z, id] of OUTSIDE.heap) c.set(x, y, z, id, id === 'hay_block' ? { axis: 'y' } : {})
  for (const [x, y, z, id] of OUTSIDE.crates) {
    if (id === 'lid') trapdoor(c, x, y, z, `${P.wood}_trapdoor`, 'north')
    else c.set(x, y, z, id)
  }
  for (const l of LAMPS) {
    const [x, y, z] = l.at
    if (l.hung) hangingLantern(c, x, y, z, 'lantern', 0)
    else c.set(x, y, z, 'lantern', { hanging: 'false', waterlogged: 'false' })
  }
  return c
}

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `market_corner_cut${cut}` : 'market_corner' } }
}
