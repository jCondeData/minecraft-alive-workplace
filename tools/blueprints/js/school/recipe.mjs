// The School (the Move Tutor's), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes
// (block-out and structure, roofs, openings, depth and detail, interior, ground, light) and returns the structure for
// the minecraft-builder skill's runner. Also writes furniture.json (every furniture spot the parts recorded) for
// tools/blueprints/rebuild.py. Build: see package.json; CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE, JOB } from './plan.mjs'
import {
  Canvas, mix, plinth, floor, post, beamRing, wallPanel, window, door, doorway, gableRoof, gableWall, catslideRoof,
  bargeTrim, flowerBed, lampPost, furnitureParts, stair, slab, chimney, sparringRing, moonWindow, strawTarget,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = mix(P.stone, 11)
  const paving = mix(P.paving, 5)
  const G = MASSES.gate, CT = MASSES.court, K = MASSES.cloister, Y = MASSES.yardWall, H = MASSES.hall
  const hw = H.walls, hr = H.room
  const empty = (x, y, z) => !c.get(x, y, z)

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // Hall: a stone plinth, white panels between dark posts, a beam ring on top of the walls
  plinth(c, hw.x0, hw.z0, hw.x1, hw.z1, stone)
  floor(c, hr.x0, hr.z0, hr.x1, hr.z1, 0, P.floor)
  for (const [a, b] of [[[hw.x0, hw.z0], [hw.x1, hw.z0]], [[hw.x0, hw.z1], [hw.x1, hw.z1]], [[hw.x0, hw.z0], [hw.x0, hw.z1]], [[hw.x1, hw.z0], [hw.x1, hw.z1]]])
    wallPanel(c, a, b, 1, H.wallTop, P.infill)
  for (const x of H.frontPosts) post(c, x, hw.z0, 1, H.wallTop, P.frame)
  for (const x of H.backPosts) post(c, x, hw.z1, 1, H.wallTop, P.frame)
  for (const x of [hw.x0, hw.x1]) for (const z of H.sidePosts) post(c, x, z, 1, H.wallTop, P.frame)
  beamRing(c, hw.x0, hw.z0, hw.x1, hw.z1, H.beamY, P.frame)

  // Gate: two stone piers, a dark lintel and a board ceiling over the passage, a timber storey on a beam ring
  for (const [a, b] of G.piers) c.fill(a, 0, G.z0, b, G.pierTop, G.z1, stone)
  floor(c, G.passage[0], G.z0, G.passage[1], G.z1, 0, paving)
  for (const z of [G.z0, G.z1]) for (let x = G.passage[0]; x <= G.passage[1]; x++) c.set(x, G.pierTop, z, P.frame, { axis: 'x' })
  floor(c, G.passage[0], G.z0 + 1, G.passage[1], G.z1 - 1, G.pierTop, P.ceiling)
  const gx0 = G.piers[0][0], gx1 = G.piers[1][1]
  beamRing(c, gx0, G.z0, gx1, G.z1, G.pierTop + 1, P.frame) // the loft inside stays shut on the passage's ceiling

  // Court: paving, the sparring ring inlaid round the post
  floor(c, CT.x0, CT.z0, CT.x1, CT.z0, 0, paving)
  sparringRing(c, { ...CT.ring, y: 0, x0: CT.x0, x1: CT.x1, z0: CT.ring.z0, z1: CT.z1 }, { inner: P.ring.inner, border: P.ring.border, paving })

  // Cloister: a framed white outer wall on a plinth, the front end wall, a colonnade of posts on the court side
  const kr = K.roof
  const kUnder = {}; for (let u = kr.across[0]; u <= kr.across[1]; u++) kUnder[u] = kr.ridgeY - Math.abs(u - kr.ridge)
  plinth(c, K.wall, K.z0, K.wall, K.z1, stone)
  plinth(c, K.wall, K.z0, K.colonnade, K.z0, stone)
  floor(c, K.walk[0], K.z0 + 1, K.walk[1], K.z1, 0, P.floor)
  floor(c, K.colonnade, K.z0 + 1, K.colonnade, K.z1, 0, stone)
  wallPanel(c, [K.wall, K.z0], [K.wall, K.z1], 1, kUnder[K.wall] - 1, P.infill)
  for (const z of K.wallPosts) post(c, K.wall, z, 1, kUnder[K.wall] - 1, P.frame)
  for (let x = K.walk[0]; x <= K.walk[1]; x++) for (let y = 1; y < kUnder[x]; y++) c.set(x, y, K.z0, P.infill)
  for (const z of K.posts) post(c, K.colonnade, z, 1, kUnder[K.colonnade] - 1, P.frame)

  // Yard wall: a two-high stone wall with a slab coping, lantern piers at its two front corners
  const yw = []
  for (let x = Y.front[0]; x <= Y.front[1]; x++) yw.push([x, Y.z0])
  for (let z = Y.z0 + 1; z <= Y.z1; z++) yw.push([Y.x, z])
  for (const [x, z] of yw) {
    for (let y = 0; y <= Y.top; y++) c.set(x, y, z, stone)
    slab(c, x, Y.top + 1, z, P.coping, 'bottom')
  }
  for (const [x, z] of Y.piers) { c.set(x, Y.top + 1, z, stone); lampPost(c, x, Y.top + 2, z, `${P.wood}_fence`, 1) }
  // the paved practice strip inside it
  floor(c, Y.front[0], Y.z0 + 1, Y.x - 1, Y.z1, 0, paving)

  // --- Pass 2: roofs ------------------------------------------------------------------------------------------------
  // the gate's hero roof first, then the cloister's lean-to, then the hall's broad roof where nothing else stands (its
  // front eave stops at the cloister: the lean-to runs to the hall's wall, never under its eave)
  const gate = gableRoof(c, G.roof, P.roof)
  catslideRoof(c, kr, P.roof, empty)
  const hallKeep = (x, y, z) => empty(x, y, z) && !(z === hw.z0 - 1 && x <= K.colonnade)
  const hall = gableRoof(c, H.roof, P.roof, hallKeep)

  // the gate's gables: studs either side of the moon window, white panels; the side walls of its timber storey
  for (const z of [G.z0, G.z1]) {
    gableWall(c, 'z', z, [gx0, gx1], G.pierTop + 2, gate.under, (u) => (u === 6 || u === 10 ? [P.frame, { axis: 'y' }] : P.infill))
    moonWindow(c, 'z', z, G.moon[0], G.moon[1], { glass: P.glass, frame: P.frame })
  }
  for (const x of [gx0, gx1]) for (let z = G.z0 + 1; z < G.z1; z++) for (let y = G.pierTop + 2; y < gate.under[x]; y++) c.set(x, y, z, P.infill)

  // the hall's gables: a stud each side of the gable window
  for (const x of [hw.x0, hw.x1]) {
    gableWall(c, 'x', x, [hw.z0, hw.z1], H.beamY + 1, hall.under, (u) => (H.gableStuds.includes(u) ? [P.frame, { axis: 'y' }] : P.infill))
  }
  // the cloister's back end, above the hall's beam: close the triangle under the lean-to against the hall's slope
  for (let x = K.wall + 1; x < K.colonnade; x++) for (let y = H.beamY + 1; y < kUnder[x]; y++) if (empty(x, y, K.z1)) c.set(x, y, K.z1, P.infill)

  // --- Pass 3: openings ---------------------------------------------------------------------------------------------
  for (const [x, y, z] of OPENINGS.gateway.steps) stair(c, x, y, z, 'stone_brick_stairs', 'south')
  const hd = OPENINGS.hallDoor
  door(c, hd.at, hd.facing, hd.out, { wood: P.wood })
  c.set(hd.at[0], 0, hd.at[2], 'polished_andesite') // a threshold
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, pots: w.pots ? ['potted_red_tulip', 'potted_oxeye_daisy'] : [] })

  // --- Pass 4: depth and detail -------------------------------------------------------------------------------------
  // the bookshelf screen between the lecture room and the quarters, a tie beam over it
  const sc = H.screen
  for (let z = sc.z0; z <= sc.z1; z++) { c.set(sc.x, 1, z, 'bookshelf'); c.set(sc.x, 2, z, 'bookshelf'); c.set(sc.x, 3, z, `${P.wood}_planks`) }
  for (let z = hw.z0 + 1; z < hw.z1; z++) c.set(sc.x, H.beamY, z, P.frame, { axis: 'z' })
  doorway(c, OPENINGS.screenDoorway.at, OPENINGS.screenDoorway.height)
  // barge boards under the overhanging rakes: the gate front and back, the hall's two gables (its front corner over the
  // cloister left out: the lean-to owns that cell)
  bargeTrim(c, 'z', G.roof.along[0], G.roof.across, gate.under, `${P.wood}_stairs`)
  bargeTrim(c, 'z', G.roof.along[1], G.roof.across, gate.under, `${P.wood}_stairs`)
  bargeTrim(c, 'x', H.roof.along[0], H.roof.across, hall.under, `${P.wood}_stairs`)
  bargeTrim(c, 'x', H.roof.along[1], H.roof.across, hall.under, `${P.wood}_stairs`)
  c.clear(H.roof.along[0], hall.under[hw.z0 - 1] - 1, hw.z0 - 1)
  // the hearth's stone back in the wall and the chimney stack outside it, up past the ridge
  const ch = H.chimney
  for (let y = 1; y <= H.wallTop; y++) c.set(ch.x, y, hw.z1, stone)
  chimney(c, ch.x, ch.z, 0, hall.ridgeY + 1, stone)
  // red cloth hung on the gate piers either side of the passage, facing the road
  c.set(6, 3, 0, `${P.accent}_wall_banner`, { facing: 'north' })
  c.set(10, 3, 0, `${P.accent}_wall_banner`, { facing: 'north' })

  // --- Pass 5: interior (the plan's furniture and the post) -----------------------------------------------------------
  const extra = { target: (cv, f) => strawTarget(cv, f.pos, { bale: 'hay_block', face: 'target' }) }
  for (const f of FURNITURE) (extra[f.part] ?? furnitureParts[f.part])(c, f)
  c.set(...JOB.at, JOB.block, { facing: JOB.facing })

  // --- Pass 6: ground and light ---------------------------------------------------------------------------------------
  for (const b of OUTSIDE.flowerBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  const [px, pz] = OUTSIDE.corner
  for (let y = 0; y <= Y.top + 1; y++) c.set(px, y, pz, stone)
  lampPost(c, px, Y.top + 2, pz, `${P.wood}_fence`, 1)
  c.set(8, 3, 2, 'lantern', { hanging: 'true', waterlogged: 'false' }) // in the gate passage, under its ceiling
  c.set(8, 3, 11, 'lantern', { hanging: 'true', waterlogged: 'false' }) // over the hall door, under the eave
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `school_cut${cut}` : 'school' } }
}
