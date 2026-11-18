// The Castaway Camp (the Upturned Boat), the recipe: assembles plan.mjs from the shared parts library (../parts, ROADMAP
// 49; shore.mjs grown for it) in passes (block-out and structure, depth and detail, interior, ground, light) and
// returns the structure for the minecraft-builder skill's runner. Writes furniture.json (no spots: see plan.mjs) for
// rebuild.py. Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, FURNITURE, SHORE, CASTAWAY } from './plan.mjs'
import { Canvas, mix, stair, firePit, spit, logStack, hangingLantern, upturnedHull, mast, raggedCloth } from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const DRY = { waterlogged: 'false' }

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, M = MASSES
  const [w, , d] = SIZE

  // --- Pass 1: block-out and structure: the props, the boat resting on its stems and the props ---------------------
  for (const [x, z] of M.props) for (let y = 1; y < M.boat.g; y++) c.set(x, y, z, P.driftwood, { axis: 'y' })
  upturnedHull(c, M.boat, { ...P.hull, planks: mix(P.hull.planks, 5) })

  // the mast, stepped in the sand and wedged with stones, its yard across, the red rag on top
  const top = mast(c, M.mast.at, M.mast, { log: P.mast, yard: P.yard })
  for (const [x, y, z, f] of M.mastFoot) stair(c, x, y, z, P.curbStair, f)
  c.set(...top, P.rag, { rotation: '6' })

  // --- Pass 2: depth and detail: the sail, the windbreak, the fire and its spit ------------------------------------
  const sail = mix(P.sail, 9)
  raggedCloth(c, M.sail.axis, M.sail.at, M.sail.a, M.sail.y, M.sail.drops, sail)
  for (const [x, y, z] of M.sailBlown) c.set(x, y, z, sail)
  const wb = M.windbreak
  raggedCloth(c, 'z', wb.x, wb.z0, M.boat.g, wb.drops, sail)
  const F = M.fire
  firePit(c, ...F.at, { curbStair: P.curbStair, curbSlab: P.curbSlab, open: ['west', 'east'] })
  spit(c, F.spit.xa, F.spit.xb, F.spit.z, F.spit.y0, P.spit)
  logStack(c, M.fuel, P.fuel, 'z')
  logStack(c, M.driftLog, P.driftwood, 'x')

  // --- Pass 3: interior: the sea chest, the barrel, the bed of folded sail, the pillow, the rain cauldron ----------
  for (const f of FURNITURE) {
    if (f.block === 'chest') c.set(...f.pos, 'chest', { facing: f.facing, type: 'single', waterlogged: 'false' })
    else if (f.block === 'barrel') c.set(...f.pos, 'barrel', { facing: f.facing, open: 'false' })
    else if (f.block === 'water_cauldron') c.set(...f.pos, 'water_cauldron', { level: '3' })
    else if (f.kind === 'bedding') f.cellsAbs.forEach(([x, y, z], i) => c.set(x, y, z, P.bedding[i % 3 === 2 ? 1 : 0]))
    else if (f.kind === 'pillow') c.set(...f.pos, P.pillow, { axis: 'x' })
  }

  // --- Pass 4: ground: sand in a ragged round (the corners left to the beach's own ground), gravel and stones
  // trodden round the hearth, sand under everything that stands; shells and dead coral along the strandline --------
  const sand = mix(P.ground, 3), hearth = mix(P.hearthGround, 7)
  const G = M.ground
  for (const [x, , z] of SHORE.pickles) c.set(x, 1, z, P.shells.pickle, { pickles: String(SHORE.pickles.find(p => p[0] === x && p[2] === z)[3]), ...DRY })
  SHORE.fans.forEach(([x, y, z], i) => c.set(x, y, z, P.shells.fans[i % P.shells.fans.length], DRY))
  for (let x = 0; x < w; x++) for (let z = 0; z < d; z++) {
    if (c.get(x, 0, z)) continue // the campfire
    const inRound = (x - G.cx) ** 2 + (z - G.cz) ** 2 <= G.r2
    const stands = c.get(x, 1, z)
    if (!inRound && !stands) { c.set(x, 0, z, 'structure_void'); continue }
    c.set(x, 0, z, G.hearth.some(([a, b]) => a === x && b === z) ? hearth : sand)
  }

  // --- Pass 5: light: a lantern on the barrel under the hull, one from the yard's west end ---------------------------------------
  for (const f of FURNITURE) if (f.kind === 'lamp') {
    if (f.standing) c.set(...f.pos, P.lantern, { hanging: 'false', ...DRY })
    else hangingLantern(c, ...f.pos, P.lantern)
  }

  // the castaway's feet and head stay clear, the sand under them holds
  c.free(CASTAWAY, [CASTAWAY[0], CASTAWAY[1] + 1, CASTAWAY[2]])
  const under = c.get(CASTAWAY[0], CASTAWAY[1] - 1, CASTAWAY[2])
  if (!under || /void|air/.test(under.id)) throw new Error('no ground under the castaway')
  return c
}

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `castaway_camp_cut${cut}` : 'castaway_camp' } }
}
