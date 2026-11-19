// Berry Farm (the Lantern Arbor), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes
// (block-out and structure, openings, depth and detail, interior, ground and planting, light) and returns the
// structure for the minecraft-builder skill's runner. LEVEL=1 (berry_farm) or 2 (berry_farm_2): Level II is Level I
// drawn first and then grown (the same calls, then its own), so its kept cells are I's by construction.
// Writes furniture.json (no spots: the keeper's pieces are fixtures) for tools/blueprints/rebuild.py.
// Build: ./build.sh (every level as a candidate); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZES, PALETTE as P, LODGE, ARBOR, BEDS, KERBS, GROUND, HEDGE, PIECES, VINES } from './plan.mjs'
import {
  Canvas, mix, plinth, floor, post, beamRing, wallPanel, window, door, flaredGableRoof, gableWall, dormer, bargeTrim, pentice,
  hangingLantern, hedgeRun, berryBed, kerb, pergola, fruitingVines,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(level) {
  const [w, , d] = SIZES[level]
  const c = new Canvas(SIZES[level])
  const stone = mix(P.plinth, 5), porchStone = mix(P.porch, 9), leaf = mix(P.hedge, 3)
  const W = LODGE.walls, R = LODGE.room
  const levels = level >= 2 ? [1, 2] : [1]

  // --- Pass 1: block-out -- the ground, the lodge's footing, the hedge's line --------------------------------------
  for (let x = 0; x < w; x++) for (let z = 0; z < d; z++) c.set(x, 0, z, P.ground, { snowy: 'false' })
  for (const cells of GROUND.walk) for (const [x, z] of cells) c.set(x, 0, z, P.walk)
  for (const [x, z] of GROUND.porch) c.set(x, 0, z, porchStone)
  for (const L of levels) for (const cells of GROUND.paths[L]) for (const [x, z] of cells) c.set(x, 0, z, P.walk)
  plinth(c, W.x0, W.z0, W.x1, W.z1, stone)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)

  // the hedge: one high, two at the corners and the gateway's cheeks; at II the back row behind the lodge gives way
  // to the cloister and the hedge runs on round it
  const tall = (x, z) => HEDGE.tall.some(([tx, tz]) => tx === x && tz === z)
  for (const L of levels) for (const [axis, at, a, b] of HEDGE[L]) {
    const back = L === 1 && axis === 'x' && at === 12
    if (back && level >= 2) continue
    hedgeRun(c, axis, at, a, b, 1, i => {
      const [x, z] = axis === 'x' ? [i, at] : [at, i]
      return tall(x, z) && !(level >= 2 && z === 12) ? 2 : 1
    }, leaf)
  }

  // --- Pass 2: structure -- the lodge's frame, boards, beam ring, roof and gables ----------------------------------
  for (const [x, z] of LODGE.posts) post(c, x, z, 1, LODGE.wallTop, P.frame)
  const boards = (x, z) => !c.get(x, 1, z)
  for (const [a, b] of [[[W.x0, W.z0], [W.x1, W.z0]], [[W.x0, W.z1], [W.x1, W.z1]], [[W.x0, W.z0], [W.x0, W.z1]], [[W.x1, W.z0], [W.x1, W.z1]]]) {
    for (let x = Math.min(a[0], b[0]); x <= Math.max(a[0], b[0]); x++)
      for (let z = Math.min(a[1], b[1]); z <= Math.max(a[1], b[1]); z++)
        if (boards(x, z)) wallPanel(c, [x, z], [x, z], 1, LODGE.wallTop, P.infill)
  }
  beamRing(c, W.x0, W.z0, W.x1, W.z1, LODGE.beamY, P.frame)
  const roof = flaredGableRoof(c, LODGE.roof, P.roof, `${P.wood}_stairs`)
  const [g0, g1] = LODGE.roof.across
  for (const z of [W.z0, W.z1]) gableWall(c, 'z', z, [g0, g1], LODGE.beamY + 1, roof.under, (u, y) => {
    if (u === 4 || u === 8) return [P.frame, { axis: 'y' }] // studs
    if (u === 6) return LODGE.gableGlass.some(([gu, gy]) => gu === u && gy === y) ? P.glass : [P.frame, { axis: 'y' }] // the king post round its slit
    return P.infill
  })
  // a dormer on each slope over the store's middle (the east one over its door): it breaks the steep roof's plane and
  // lights the space under the ridge
  for (const dm of LODGE.dormers) dormer(c, dm.z, P.roof, P.infill, P.glass, P.frame, dm)
  // the barge boards under both rakes (upside-down slabs: the roof's edge reads apart from the gable)
  // (not under the kick: its stair is the trim, and a slab under it would hang at head height over the paths)
  const [k0, k1] = LODGE.roof.across
  for (const z of LODGE.roof.along) bargeTrim(c, 'z', z, [k0 + 1, k1 - 1], roof.under, `${P.wood}_slab`)

  // --- Pass 3: openings ---------------------------------------------------------------------------------------------
  for (const win of LODGE.windows) window(c, win.cells, win.out, null, { glass: P.glass })
  for (const L of levels) for (const dr of PIECES.doors[L]) door(c, dr.at, dr.facing, dr.out, { wood: P.wood })

  // --- Pass 4: depth and detail -- the arbor, the porch's pent roof on its posts --------------------------------------
  // beds and kerbs first: the arbor's posts stand in the kerb line and replace a kerb log where they do
  const bushes = []
  for (const L of levels) {
    for (const b of BEDS[L]) {
      for (const [x, z] of b.cells) { c.set(x, 0, z, P.under); c.clear(x, 2, z); c.clear(x, 3, z) } // II: the old back hedge goes
      bushes.push(...berryBed(c, b.cells, 1, { soil: P.soil, bush: P.bush, props: { age: '3' } }))
    }
    for (const k of KERBS[L]) {
      kerb(c, k.axis, k.at, k.a, k.b, 1, P.kerb)
      for (let i = k.a; i <= k.b; i++) { const [x, z] = k.axis === 'x' ? [i, k.at] : [k.at, i]; c.set(x, 0, z, P.under); c.clear(x, 2, z) }
    }
  }
  if (level >= 2) { // the old back hedge's cells on the cloister's paths
    for (const cells of GROUND.paths[2]) for (const [x, z] of cells) { c.clear(x, 1, z); c.clear(x, 2, z) }
  }
  const held = []
  held.push(...pergola(c, ARBOR[1], { post: P.frame, log: P.poles, stairs: `${P.wood}_stairs` }))
  if (level >= 2) {
    held.push(...pergola(c, ARBOR[2], { post: P.frame, log: P.poles, stairs: `${P.wood}_stairs` }))
    held.push(...pergola(c, ARBOR.cloister, { post: P.frame, log: P.poles, stairs: `${P.wood}_stairs` }))
  }
  // the picking porch: a pent roof along the lodge's front wall
  for (const [z, y] of PIECES.pentice.rows) {
    const cells = []
    for (let x = PIECES.pentice.x0; x <= PIECES.pentice.x1; x++) cells.push([x, z])
    pentice(c, cells, y, P.roof, 'south')
  }

  // --- Pass 5: interior and the keeper's pieces ------------------------------------------------------------------------
  c.set(...PIECES.composter, 'composter', { level: '0' })
  c.set(...PIECES.chest.at, 'chest', { facing: PIECES.chest.facing, type: 'single', waterlogged: 'false' })
  for (const [x, y, z] of PIECES.hay) c.set(x, y, z, 'hay_block', { axis: 'y' })
  const tb = PIECES.tieBeam
  for (let x = tb.x0; x <= tb.x1; x++) c.set(x, tb.y, tb.z, P.frame, { axis: 'x' })

  // --- Pass 6: planting (II: glow berries on the lattice) and light ---------------------------------------------------
  if (level >= 2) fruitingVines(c, VINES, P.vine)
  for (const [x, y, z] of PIECES.lanterns) hangingLantern(c, x, y, z)
  return { c, bushes, vines: level >= 2 ? VINES.length : 0 }
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const { c } = draw(level)
  const id = level > 1 ? `berry_farm_${level}` : 'berry_farm'
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": []}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
