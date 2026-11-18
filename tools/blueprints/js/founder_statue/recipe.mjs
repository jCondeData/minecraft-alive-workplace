// The Founder's Statue, the recipe: assembles plan.mjs from the shared parts library (../parts, ROADMAP 49) in passes
// (block-out: the stepped plinth; structure: the founding stone and the figure's front; depth: toes, cloak and hem,
// the hat's brim, the staff; detail: the bronze plate; ground: moss and azaleas; light: the lantern on the staff and the corner posts)
// and returns the structure for the minecraft-builder skill's runner. No furniture: it writes an empty furniture.json
// for rebuild.py. CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, FRONT, DEPTH, STAFF, PLINTH, FOUNDING_STONE, CORNERS } from './plan.mjs'
import { Canvas, mix, stair, steppedPlinth, profileFigure, brimHat, lampPost, plantFlower } from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const paving = mix(P.paving, 3)

  // --- Pass 1: block-out (two steps of dressed stone) ------------------------------------------------------------
  const lo = PLINTH.lower, up = PLINTH.upper
  steppedPlinth(c, lo.x0, lo.z0, lo.x1, lo.z1, paving, () => P.steps, lo.y)
  steppedPlinth(c, up.x0, up.z0, up.x1, up.z1, P.die, () => P.steps, up.y)
  for (const x of [up.x0 + 1, up.x1 - 1]) for (const z of [up.z0 + 1, up.z1 - 1]) c.set(x, up.y, z, P.dieCorner)

  // --- Pass 2: structure (the founding stone, the figure's front plane) -------------------------------------------
  const fs0 = FOUNDING_STONE
  for (let y = fs0.y0; y <= fs0.y1; y++) c.set(fs0.x, y, fs0.z, P.rough)
  const st = (id, facing, half = 'bottom') => [id, { facing, half, shape: 'straight', waterlogged: 'false' }]
  profileFigure(c, { rows: FRONT.rows, x0: 0, y0: FRONT.y0, zs: [FRONT.z] }, {
    F: P.skin,
    h: null, b: null, // the hat: brimHat below
    H: P.skin,
    s: st(P.coatStairs, 'east'), // the shoulders slope down to the sleeves
    t: st(P.coatStairs, 'west'),
    C: P.coat,
    X: P.clasp,
    a: P.coat,
    K: P.coat,
    k: st(P.coatStairs, 'south', 'top'),
    l: P.coat,
    B: P.patina,
  })

  // --- Pass 3: depth (toes, cloak and hem, the hat) ----------------------------------------------------------------
  for (const [x, y, z] of DEPTH.toes) c.set(x, y, z, ...st(P.bright, 'south'))
  const [cx, cy, cz] = DEPTH.collar
  c.set(cx, cy, cz, P.cloak)
  c.set(cx - 1, cy, cz, ...st(P.cloakStairs, 'east'))
  c.set(cx + 1, cy, cz, ...st(P.cloakStairs, 'west'))
  const k = DEPTH.cloak
  for (let x = k.x0; x <= k.x1; x++) for (let y = k.y0; y <= k.y1; y++) c.set(x, y, k.z, P.cloak)
  const hm = DEPTH.hem
  for (let x = hm.x0; x <= hm.x1; x++) c.set(x, hm.y, hm.z, ...st(P.cloakStairs, 'north')) // flares out behind the knees
  const head = FRONT.rows.findIndex(r => r.includes('h'))
  brimHat(c, [FRONT.rows[head].indexOf('h'), FRONT.y0 + FRONT.rows.length - 1 - head, FRONT.z], P.patina, P.brim, true)

  // the staff: an iron rod in front of the right sleeve, his hand round it, the lantern on its top
  for (let y = STAFF.y0; y <= STAFF.y1; y++) c.set(STAFF.x, y, STAFF.z, P.staff, { axis: 'y', waterlogged: 'false' })
  c.set(STAFF.x, STAFF.hand, STAFF.z, P.skin)
  c.set(STAFF.x, STAFF.lantern, STAFF.z, 'lantern', { hanging: 'false', waterlogged: 'false' })

  // --- Pass 4: detail (the bronze plate in the front step) ---------------------------------------------------------
  c.set(...PLINTH.plate, P.plate)

  // --- Pass 5: ground and light (the corners) ----------------------------------------------------------------------
  for (const [x, y, z] of CORNERS.bushes) { c.set(x, y, z, P.soil); plantFlower(c, x, y + 1, z, P.bush) }
  for (const [x, y, z] of CORNERS.lamps) lampPost(c, x, y, z, P.post, 2)
  return c
}

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": []}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `founder_statue_cut${cut}` : 'founder_statue' } }
}
