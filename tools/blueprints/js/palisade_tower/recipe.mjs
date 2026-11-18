// The Palisade Tower (`palisade_tower`), the recipe: assembles plan.mjs from the shared parts library (../parts,
// ROADMAP 52.3) in passes (block-out and structure, the roof, depth and detail, interior, light) and returns the
// structure for the minecraft-builder skill's runner. Writes furniture.json beside it for rebuild.py. CUT=<y> cuts
// everything above y (cutaways).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, LIGHTS } from './plan.mjs'
import { Canvas, post, beamRing, stockade, watchHat, ringFacing, trapdoor, stair } from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const has = (list, x, y, z) => list.some(([a, b, d]) => a === x && b === y && d === z)

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, M = MASSES, B = M.box
  const S = M.spine
  const special = (x, y, z) => has(M.door, x, y, z) || has(M.loopholes, x, y, z) || (x === S.x && z === S.z)

  // --- Pass 1: block-out and structure: the corner posts, the stone foot, the ladder's spine, the girt, the deck -----
  for (const x of [B.x0, B.x1]) for (const z of [B.z0, B.z1]) post(c, x, z, M.posts.y0, M.posts.y1, P.post)
  stockade(c, B, M.foot.y, M.foot.y, P.foot, (x, y, z) => has(M.door, x, y, z), {})
  post(c, S.x, S.z, S.y0, S.y1, P.stake)
  beamRing(c, B.x0, B.z0, B.x1, B.z1, M.girt.y, P.girt)
  for (const x of [B.x0, B.x1]) for (const z of [B.z0, B.z1]) c.set(x, M.girt.y, z, P.post, { axis: 'y' }) // the posts run through
  const D = M.deck
  c.fill(D.x0, D.y, D.z0, D.x1, D.y, D.z1, P.deck)
  // --- Pass 2: the breastwork round the deck (logs laid along each side) and the hat on the posts' heads -----------
  beamRing(c, B.x0, B.z0, B.x1, B.z1, M.breastwork.y, P.stake)
  for (const x of [B.x0, B.x1]) for (const z of [B.z0, B.z1]) c.set(x, M.breastwork.y, z, P.post, { axis: 'y' })
  c.set(S.x, M.breastwork.y, S.z, P.stake, { axis: 'y' })
  watchHat(c, { ...B, y: M.hat.y }, P.roof, P.cap, M.hat.top)
  // --- Pass 3: depth and detail: the panels (stakes, a stepped-back top course), the door, the loopholes, the eave -
  const inward = (x, z) => ({ north: 'south', south: 'north', west: 'east', east: 'west' })[ringFacing(B, x, z)]
  const ring = (y) => {
    const out = []
    for (let x = B.x0 + 1; x < B.x1; x++) for (const z of [B.z0, B.z1]) out.push([x, y, z])
    for (let z = B.z0 + 1; z < B.z1; z++) for (const x of [B.x0, B.x1]) out.push([x, y, z])
    return out
  }
  for (let y = M.panels.y0; y <= M.panels.y1; y++) for (const [x, , z] of ring(y)) if (!special(x, y, z)) c.set(x, y, z, P.stake, { axis: 'y' })
  for (const [x, y, z] of ring(M.panels.shadowY)) if (!special(x, y, z)) stair(c, x, y, z, P.trim, inward(x, z), 'bottom')
  c.set(...M.door[0], P.door, { facing: M.doorFacing, half: 'lower', hinge: 'left', open: 'false', powered: 'false' })
  c.set(...M.door[1], P.door, { facing: M.doorFacing, half: 'upper', hinge: 'left', open: 'false', powered: 'false' })
  for (const [x, y, z] of M.loopholes) stair(c, x, y, z, P.trim, inward(x, z), 'top')
  for (const [x, y, z] of ring(M.eave.y)) stair(c, x, y, z, P.trim, inward(x, z), 'top')
  c.set(2, M.hat.top, 2, P.finial, { waterlogged: 'false' }) // the finial over the hat's middle
  // --- Pass 4: interior: the ladder up the back wall, the hatch over it a trapdoor hung open -----------------------
  const L = M.ladder
  for (const y of L.ys) c.set(L.x, y, L.z, 'ladder', { facing: L.facing, waterlogged: 'false' })
  trapdoor(c, ...M.hatch, P.board, L.facing, 'bottom', true)
  // --- Pass 5: light ------------------------------------------------------------------------------------------------
  for (const p of LIGHTS) c.set(...p, 'lantern', { hanging: 'true', waterlogged: 'false' })
  return c
}

export default function build(api) {
  const id = 'palisade_tower'
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
