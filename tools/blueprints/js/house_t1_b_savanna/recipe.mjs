// Daub House, savanna look (49.10d), the recipe: assembles plan.mjs in passes (block-out and structure, roofs,
// openings, depth and detail, interior, ground) on the parts library (../parts), the kept insides from
// ../parts/daub_looks.mjs. Writes furniture.json. Build: build.sh. CUT=<y>: a cutaway.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, WINDOWS, OUTSIDE } from './plan.mjs'
import { Canvas, mix, stair, window, hipRoof } from '../parts/index.mjs'
import { daubInterior, daubSpots } from '../parts/daub_looks.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, H = MASSES.house, W = MASSES.wing, V = MASSES.veranda
  const plinth = mix(P.plinth, 2)
  const post = (x, z, y0, y1) => { for (let y = y0; y <= y1; y++) c.set(x, y, z, P.frame, { axis: 'y' }) }
  const isPost = (x, z) => ((z === H.z0 || z === H.z1) && H.posts.front.includes(x)) || ((x === H.x0 || x === H.x1) && H.posts.sides.includes(z))

  // --- Pass 1: block-out and structure: mud walls, the frame, the beam ring, the upper storey, the wing --------------
  const ground = (x, z) => x === H.x0 || x === H.x1 || z === H.front || z === H.back || z === H.z1 // the back recess filled
  for (let x = H.x0; x <= H.x1; x++) for (let z = H.z0; z <= H.z1; z++) {
    if (ground(x, z) && !(z < H.front && x > H.x0 && x < H.x1)) {
      c.set(x, 0, z, plinth)
      for (let y = 1; y <= H.groundTop; y++) c.set(x, y, z, P.mud)
    }
    const ring = x === H.x0 || x === H.x1 || z === H.z0 || z === H.z1
    if (!ring) continue
    c.set(x, H.beamY, z, P.frame, { axis: z === H.z0 || z === H.z1 ? 'x' : 'z' })
    for (let y = H.beamY + 1; y <= H.upperTop; y++) c.set(x, y, z, isPost(x, z) ? P.frame : P.mud, isPost(x, z) ? { axis: 'y' } : {})
  }
  // the ground storey's frame: corner posts, the door's posts, the side walls' bay posts
  for (const [x, z] of [[H.x0, H.z0], [H.x1, H.z0], [H.x0, H.z1], [H.x1, H.z1], [H.x0, H.front], [H.x1, H.front]]) post(x, z, 1, H.groundTop)
  for (const x of H.posts.door) post(x, H.front, 1, H.groundTop)
  for (const z of [4, 8]) for (const x of [H.x0, H.x1]) post(x, z, 1, H.groundTop)
  // the painted band under the beam: one continuous course across the front under the veranda, white and orange in
  // turn, over the posts too (round 2: on the side walls it read as lone squares between the posts)
  for (let x = H.x0; x <= H.x1; x++) c.set(x, H.paintY, H.front, P.paint[x % 2])
  // the kitchen wing: mud walls on the plinth, corner posts, an acacia beam on top, a ceiling
  for (let x = W.x0; x <= W.x1; x++) for (let z = W.z0; z <= W.z1; z++) {
    if (!(x === W.x1 || z === W.z0 || z === W.z1)) continue
    const corner = x === W.x1 && (z === W.z0 || z === W.z1)
    c.set(x, 0, z, plinth)
    for (let y = 1; y <= W.wallTop; y++) c.set(x, y, z, corner ? P.frame : P.mud, corner ? { axis: 'y' } : {})
    c.set(x, W.beamY, z, P.frame, { axis: z === W.z0 || z === W.z1 ? 'x' : 'z' })
  }
  c.fill(W.x0, W.beamY, W.z0 + 1, W.x1 - 1, W.beamY, W.z1 - 1, P.ceiling)

  // --- Pass 2: roofs: the house's hip, the wing's half-hip rising to meet its eave, the pot on the ridge ---------------
  const hall = hipRoof(c, H.hip, P.roof)
  const wingTop = hipRoof(c, W.hip, P.roof, (x, y, z) => x >= W.x0 && !c.get(x, y, z))
  // under the wing's thatch where it rides over the house's eave, close the pocket solid
  for (let z = W.hip.z0; z <= W.hip.z1; z++) for (let y = H.hip.eaveY + 1; y < wingTop(W.x0, z); y++)
    if (!c.get(W.x0, y, z)) c.set(W.x0, y, z, P.roof.full)
  c.set(4, hall(4, 6) + 1, 6, P.pot, { facing: 'north', cracked: 'false', waterlogged: 'false' })

  // --- Pass 3: openings ------------------------------------------------------------------------------------------------
  for (const w of WINDOWS) {
    window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, sill: P.sill }) // shutters or a sill, never both
  }

  // --- Pass 4: depth and detail: the veranda, the stack --------------------------------------------------------------
  for (let x = V.x0; x <= V.x1; x++) stair(c, x, V.y, V.z, P.roof.stairs, 'south')
  // (round 2: the pent stops on its corner posts; a return round the corner had nothing under it)
  for (const x of V.posts) post(x, V.z, 1, V.y - 1)
  for (let x = H.x0 + 1; x < H.x1; x++) c.set(x, H.beamY, H.z0, P.frame, { axis: 'x' }) // the beam over the recess
  for (let x = V.x0; x <= V.x1; x++) for (const z of [0, 1]) if (!c.get(x, 0, z)) c.set(x, 0, z, P.deck)
  c.set(V.lamp[0], V.lamp[1], V.lamp[2], 'lantern', { hanging: 'true', waterlogged: 'false' })
  const S = MASSES.stack
  for (let x = S.breast[0]; x <= S.breast[1]; x++) for (let y = 0; y <= S.breastTop; y++) c.set(x, y, S.z, y === 0 ? plinth : P.stack)
  stair(c, S.breast[0], S.breastTop + 1, S.z, P.shoulder, 'east')
  stair(c, S.breast[1], S.breastTop + 1, S.z, P.shoulder, 'west')
  for (let y = S.breastTop + 1; y <= S.top; y++) c.set(S.x, y, S.z, P.stack)
  c.set(S.x, S.top + 1, S.z, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })

  // --- Pass 5: interior: the shipped Daub House's insides, cell for cell ----------------------------------------------
  daubInterior(c, P, { step: OUTSIDE.step })

  // --- Pass 6: ground: clay pots on the veranda, hay by the kitchen ---------------------------------------------------
  for (const [x, y, z] of OUTSIDE.pots) c.set(x, y, z, P.pot, { facing: 'north', cracked: 'false', waterlogged: 'false' })
  for (const [x, y, z] of OUTSIDE.hay) c.set(x, y, z, 'hay_block', { axis: 'y' })
  return c
}

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + daubSpots(c).map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `house_t1_b_cut${cut}` : 'house_t1_b' } }
}
