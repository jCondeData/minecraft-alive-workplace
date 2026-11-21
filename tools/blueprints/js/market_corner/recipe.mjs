// Market Corner, the recipe: assembles plan.mjs from the shared parts library (../parts) in passes (block-out, the
// canvases, depth and detail, goods, light) and returns the structure for the minecraft-builder skill's runner.
// Also writes furniture.json (empty: the stall has no seats, tables or beds) for tools/blueprints/rebuild.py.
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, BACK, PALETTE, MASSES, GOODS, LAMPS } from './plan.mjs'
import { Canvas, mix, plinth, post, railing, hangingLantern, trapdoor, stair } from '../parts/index.mjs'
import { cornerDepth, lAwning, valance } from '../parts/market_awning.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const [W, , D] = SIZE
  const depth = (x, z) => cornerDepth(x, z, BACK.x, BACK.z)
  const S = MASSES.screen, M = MASSES.mast, K = MASSES.canvas, B = MASSES.booth
  const proud = new Set(MASSES.posts.at.map(([x, z]) => `${x},${z}`))
  // which way a street face looks: the back row (z = 8) south, the east row (x = 8) east; the corner is the mast's
  const out = (x, z) => (z === BACK.z && x !== BACK.x ? 'south' : 'east')

  // --- Pass 1: block-out --------------------------------------------------------------------------------------------
  // One paving over the whole plot; the screens and the splash course stand on it
  plinth(c, 0, 0, W - 1, D - 1, mix(P.paving, 11))
  // The screens on the two street sides (d 1): a cobble footing, boards, a dark oak plate; a post in the inside corner
  for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) if (depth(x, z) === 1)
    for (let y = S.y0; y <= S.y1; y++) {
      if (y === S.footing) c.set(x, y, z, P.base)
      else if (y === S.plate) c.set(x, y, z, P.frame, { axis: x === BACK.x - 1 && z !== BACK.z - 1 ? 'z' : 'x' })
      else c.set(x, y, z, P.boards)
    }
  for (const [x, z] of S.posts) post(c, x, z, S.y0, S.y1, P.frame)
  // The proud posts on the plot's edge (d 0), the splash course between them, and the mast in the corner
  for (const [x, z] of MASSES.posts.at) post(c, x, z, 1, MASSES.posts.y1, P.frame)
  for (let x = 0; x < W; x++) for (let z = 0; z < D; z++)
    if (depth(x, z) === 0 && !proud.has(`${x},${z}`) && !(x === M.x && z === M.z))
      stair(c, x, 1, z, P.splash, out(x, z) === 'south' ? 'north' : 'west') // its high side against the screen
  post(c, M.x, M.z, 1, M.top, P.frame)

  // --- Pass 2: the canvases ------------------------------------------------------------------------------------------
  const awning = { bx: BACK.x, bz: BACK.z, x0: 0, z0: 0, rows: K.rows }
  lAwning(c, { ...awning, skip: (x, y, z) => x === M.x && z === M.z }, P)
  for (const [x, z] of MASSES.poles) railing(c, [[x, 1, z], [x, 2, z], [x, 3, z]], P.wood) // the outer front poles
  // The booth: the same canvas mirrored on the front-west corner, lower, on fence posts
  const booth = { bx: 0, bz: 0, x0: B.x1, z0: B.z1, rows: B.rows }
  lAwning(c, booth, P)
  for (const [x, z, top] of B.posts) railing(c, Array.from({ length: top }, (_, i) => [x, i + 1, z]), P.wood)

  // --- Pass 3: depth and detail --------------------------------------------------------------------------------------
  valance(c, awning, P) // the scalloped hem: red and white banners on the main canvas's front edge
  // Serving hatches into the back of a counter, their shutters folded back flat against the boards (open trapdoors at
  // d 0 either side, the panel on the screen's side)
  for (const { at: [hx, hz], face } of MASSES.hatches) {
    const along = face === 'south' ? [1, 0] : [0, 1]
    for (const y of [2, 3]) {
      c.clear(hx, y, hz)
      for (const s of [-1, 1]) trapdoor(c, hx + 1 * (face === 'east') + s * along[0], y, hz + 1 * (face === 'south') + s * along[1], `${P.wood}_trapdoor`, face, y === 2 ? 'bottom' : 'top', true)
    }
  }
  for (const [x, y, z, facing] of MASSES.streetBanners) c.set(x, y, z, P.flag, { facing })
  for (const [x, y, z, facing] of M.flags) c.set(x, y, z, P.flag, { facing }) // the mast's flags

  // --- Pass 4: the goods on the counters -----------------------------------------------------------------------------
  for (const [x, z, counter, goods, stacked] of GOODS) {
    if (stacked) c.set(x, 3, z, stacked, stacked === 'hay_block' ? { axis: 'y' } : {})
    c.set(x, 1, z, counter, counter === 'hay_block' ? { axis: 'y' } : {})
    if (!goods) continue
    const props = goods === 'hay_block' ? { axis: 'y' }
      : goods === 'cake' ? { bites: '0' }
        : goods === 'decorated_pot' ? { facing: 'north', cracked: 'false', waterlogged: 'false' } : {}
    c.set(x, 2, z, goods, props)
  }

  // --- Pass 5: light -------------------------------------------------------------------------------------------------
  for (const l of LAMPS) { const [x, y, z] = l.at; hangingLantern(c, x, y, z, 'lantern', 0) }
  return c
}

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `market_corner_cut${cut}` : 'market_corner' } }
}
