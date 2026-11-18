// The Ferry House, the recipe: assembles plan.mjs (the Ferry Station) from the shared parts library (../parts) in
// passes (block-out and structure, openings, depth and detail, interior, ground, light) and returns the structure for
// the minecraft-builder skill's runner. Also writes furniture.json (every furniture spot the parts recorded).
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, FURNITURE, BENCHES } from './plan.mjs'
import {
  Canvas, mix, stair, plinth, floor, post, beamRing, wallPanel, window, door, quoins, gableRoof, gableWall, bargeTrim,
  chimney, louvre, flight, stairwell, railingRun, benchRun, platformCanopy, mast, furnitureParts,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = mix(P.stone, 13)
  const wall = mix(P.wall, 5)
  const H = MASSES.house, W = H.walls, R = H.room, ST = MASSES.store
  const ring = (x0, z0, x1, z1, y0, y1, id) => {
    for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++)
      if (x === x0 || x === x1 || z === z0 || z === z1) for (let y = y0; y <= y1; y++) c.set(x, y, z, id)
  }

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // the platform's boards along the whole waterfront
  const pf = MASSES.platform
  floor(c, pf.x0, pf.z0, pf.x1, pf.z1, 0, P.deck)
  const ls = MASSES.landingSteps
  for (let x = ls.x0; x <= ls.x1; x++) stair(c, x, 0, ls.z, `${P.wood}_stairs`, 'south')
  const ap = MASSES.storeApron
  floor(c, ap.x0, ap.z, ap.x1, ap.z, 0, P.deck)
  // House: a rubble footing and ground floor, boards inside, quoins at the corners
  plinth(c, W.x0, W.z0, W.x1, W.z1, stone)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  ring(W.x0, W.z0, W.x1, W.z1, 1, H.groundTop, stone)
  quoins(c, [[W.x0, W.z0], [W.x1, W.z0], [W.x0, W.z1], [W.x1, W.z1]], 1, H.groundTop, P.quoin, P.quoinAlt)
  // the floor beam ring and the bedroom floor; the plastered upper floor between spruce posts; the eave beam ring
  beamRing(c, W.x0, W.z0, W.x1, W.z1, H.floorY, P.frame)
  floor(c, R.x0, R.z0, R.x1, R.z1, H.floorY, P.floor)
  ring(W.x0, W.z0, W.x1, W.z1, H.floorY + 1, H.upperTop, wall)
  for (const x of H.frontPosts) post(c, x, W.z0, H.floorY + 1, H.upperTop, P.frame)
  for (const x of H.backPosts) post(c, x, W.z1, H.floorY + 1, H.upperTop, P.frame)
  for (const z of H.sidePosts) { post(c, W.x0, z, H.floorY + 1, H.upperTop, P.frame); post(c, W.x1, z, H.floorY + 1, H.upperTop, P.frame) }
  beamRing(c, W.x0, W.z0, W.x1, W.z1, H.eaveBeamY, P.frame)

  // Store: a low rubble outhouse on the east gable, its own little slate gable facing the water
  plinth(c, ST.x0, ST.z0, ST.x1, ST.z1, stone)
  floor(c, ST.x0, ST.z0 + 1, ST.x1 - 1, ST.z1 - 1, 0, P.floor)
  for (let x = ST.x0; x <= ST.x1; x++) for (let z = ST.z0; z <= ST.z1; z++)
    if (x === ST.x1 || z === ST.z0 || z === ST.z1) for (let y = 1; y <= 3; y++) c.set(x, y, z, stone)
  const store = gableRoof(c, ST.roof, P.roof)
  for (const z of [ST.z0, ST.z1]) gableWall(c, 'z', z, [ST.x0, ST.x1], 4, store.under, () => wall)

  // Roof: slate, ridge along x, overhanging a block all round; plastered gables
  const roof = gableRoof(c, H.roof, P.roof)
  for (const x of [W.x0, W.x1]) gableWall(c, 'x', x, [W.z0, W.z1], H.eaveBeamY + 1, roof.under, () => wall)

  // the platform canopy along the front, on posts on the house's bay lines, a teal valance under its eave
  const cn = MASSES.canopy
  platformCanopy(c, cn, { roof: P.roof, post: P.canopyPost, bracket: null, valance: P.paint, every: 2 }) // a dagger-board rhythm
  // its wall plate against the house: a beam on a moulded course of upside-down stairs (the canopy's back is closed,
  // so the space under it is a porch, not a room)
  for (let x = cn.x0; x <= cn.x1; x++) {
    c.set(x, cn.eaveY + 1, cn.wallZ - 1, P.frame, { axis: 'x' })
    stair(c, x, cn.eaveY, cn.wallZ - 1, `${P.wood}_stairs`, 'south', 'top')
  }

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood })
  c.set(...fd.transom, P.glass)
  for (let x = fd.at[0] - 1; x <= fd.at[0] + 1; x++) c.set(x, H.groundTop, W.z0, P.quoin) // a dressed lintel course
  const sd = OPENINGS.storeDoor
  door(c, sd.at, sd.facing, 'north', { wood: P.paint })
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.paint, sill: 'stone_brick_stairs' })
  const st = OPENINGS.stairs
  stairwell(c, st.well)
  flight(c, st.flight, st.rises, P.wood, P.floor)
  railingRun(c, st.rail, P.wood)

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  // (no barge boards under the rakes: the ship step left them out, with the back and side windows' shutters and the
  // bollard, so the Ferryman's house is built within its builder's working day; 50.5)
  // the stove's stack up the east gable, past the ridge
  const ch = MASSES.chimney
  chimney(c, ch.x, ch.z, 1, ch.top, stone)
  c.set(ch.x, ch.top, ch.z, P.quoin)
  // the lookout cupola on the ridge, the ferry light standing on its cap
  const cu = MASSES.cupola
  louvre(c, cu.x, cu.z, cu.y, { base: P.roof.full, post: `${P.wood}_fence`, roof: P.roof, finial: P.finial })
  // the signal mast at the platform's west end
  const m = MASSES.mast
  mast(c, m.at, m.top, { log: P.frame, yard: `${P.wood}_fence`, yardY: m.yardY, yardTo: 2, chain: 1, flag: P.flag, flagFacing: 'east' })
  // the company's cloth over the door, between the bedroom's windows
  c.set(...MASSES.sign, `${P.flag}_wall_banner`, { facing: 'north' })

  // --- Pass 4: interior (the plan's furniture) and the platform's benches ----------------------------------------
  const parts = {
    ...furnitureParts,
    travelPost(c, f) { c.free(f.pos); c.set(...f.pos, 'aliveworkplace:travel_post', { facing: f.facing }) },
  }
  for (const f of FURNITURE) parts[f.part](c, f)
  for (const b of BENCHES) benchRun(c, b, { seat: `${P.wood}_stairs` })
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `ferry_house_cut${cut}` : 'ferry_house' } }
}
