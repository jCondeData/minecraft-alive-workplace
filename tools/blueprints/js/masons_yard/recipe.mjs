// Mason's Yard, the recipe: assembles plan.mjs from the shared parts library (../parts) in passes (block-out and
// structure, openings, depth and detail, interior, ground, light). One drawing for both levels: Level II is Level I
// with the second banker, the second chest and the Tracing House grown on its east end, so Level I's cells stay put.
// LEVEL=1|2 picks the level (default 1); CUT=<y> builds a cutaway with everything above y removed. Writes
// furniture.json (the level's furniture spots) beside it for rebuild.py.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { LEVELS, PALETTE, MASSES, OPENINGS, FURNITURE, STOCK, BUTTRESSES, YARD } from './plan.mjs'
import {
  Canvas, mix, plinth, floor, post, beamRing, wallPanel, window, door, quoins, catslideRoof, catslideTrim, gableRoof,
  gableWall, bargeTrim, railingRun, materialStack, jettyBeam, furnitureParts, masonJibCrane, masonLampPier,
  masonPurlin, masonButtress,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const at = (lv, f) => (f.level ?? 1) <= lv && lv <= (f.until ?? 9)

export function draw(level = 1) {
  const c = new Canvas(LEVELS[level].size)
  const P = PALETTE
  const rubble = mix(P.rubble, 11)
  const ground = mix(P.ground, 5)
  const Y = MASSES.yard, S = MASSES.shed, L = MASSES.lodge, R = MASSES.catslide

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // the ground: a rubble plinth under every wall, stone flags in the shed and lodge, chippings in the yard
  plinth(c, S.x0, S.z0, L.x1, L.z1, rubble)
  floor(c, S.x0 + 1, S.z0, S.x1 - 1, L.z1 - 1, 0, P.floor)
  floor(c, Y.x0, Y.z0, Y.x1, Y.z0, 0, rubble) // under the front wall
  for (let z = Y.z0 + 1; z <= Y.z1; z++) { c.set(Y.x0, 0, z, rubble); c.set(Y.x1, 0, z, rubble) }
  floor(c, Y.x0 + 1, Y.z0 + 1, Y.x1 - 1, Y.z1, 0, ground)

  // the lodge's walls, its gable ends carried down as the shed's closed ends
  wallPanel(c, [L.x0, L.z0], [L.x1, L.z0], 1, L.wallTop, rubble) // front (the shed's back)
  wallPanel(c, [L.x0, L.z1], [L.x1, L.z1], 1, L.wallTop, rubble) // back
  const roof = catslideRoof(c, R, P.roof)
  for (const x of [L.x0, L.x1]) gableWall(c, 'x', x, [S.z0, L.z1], 1, roof.under, () => rubble)
  quoins(c, [[L.x0, S.z0], [L.x1, S.z0]], 1, roof.under[S.z0] - 1, P.dressed)
  quoins(c, [[L.x0, L.z1], [L.x1, L.z1]], 1, L.wallTop, P.dressed, rubble)
  // the shed's posts carry the slope; the purlin and soffit at its back keep it shallow and bright
  for (const x of S.posts) post(c, x, S.z0, 1, roof.under[S.z0] - 1, P.post)
  masonPurlin(c, 'x', S.z1, [S.x0 + 1, S.x1 - 1], 3, P.beam, P.boards)
  // tie beams across the lodge (lamps hang from them)
  for (const x of [3, 9]) for (let z = L.z0 + 1; z <= L.z1 - 1; z++) c.set(x, L.wallTop, z, P.beam, { axis: 'z' })

  // the yard wall: front and both sides, joined at the corners, two lamp piers at the gateway
  railingRun(c, [[Y.x0, 1, Y.z1], [Y.x0, 1, Y.z0], [Y.x1, 1, Y.z0], [Y.x1, 1, Y.z1]], null, P.fence)
  for (let x = Y.gate[0]; x <= Y.gate[1]; x++) c.clear(x, 1, Y.z0)
  for (const x of [Y.gate[0] - 1, Y.gate[1] + 1]) masonLampPier(c, [x, 1, Y.z0], { pier: P.dressed, post: P.dressedWall })

  // --- Pass 2: openings -------------------------------------------------------------------------------------------
  const ld = OPENINGS.lodgeDoor
  door(c, ld.at, ld.facing, 'north', { wood: P.wood })
  for (const w of OPENINGS.windows) if (at(level, w)) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood })

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  catslideTrim(c, 'x', R.along[0], R.across, R.ridge, roof.under, `${P.wood}_stairs`, `${P.wood}_slab`)
  catslideTrim(c, 'x', R.along[1], R.across, R.ridge, roof.under, `${P.wood}_stairs`, `${P.wood}_slab`)
  // the gateway path: dressed flags from the street to the shed
  for (let x = Y.gate[0]; x <= Y.gate[1]; x++) c.set(x, 0, Y.z0, P.dressed)
  for (let z = Y.z0 + 1; z <= Y.z1; z++) c.set(6, 0, z, P.dressed)
  // the jib crane in the yard's east corner, a finished block slung under its hook
  masonJibCrane(c, [10, 1, 2], 'west', { mast: P.beam, jib: P.beam, brace: `${P.wood}_stairs`, height: 5, reach: 2, drop: 1, load: P.ashlar })

  // the yard's story: a trestle with a stone on it, chippings round the rough pile, a chiseled sample in the pier
  for (const [x, y, z] of YARD.trestle.legs) { c.set(x, y, z, `${P.wood}_fence`); c.set(x, y + 1, z, YARD.trestle.top, { type: 'bottom', waterlogged: 'false' }) }
  for (const [x, y, z] of YARD.chippings) c.set(x, y, z, P.chips, { type: 'bottom', waterlogged: 'false' })
  c.set(...YARD.sample, P.sample)

  // --- Level II: the Tracing House on the east end --------------------------------------------------------------
  if (level >= 2) tracingHouse(c, P, rubble)

  // --- Pass 4: interior (the plan's furniture) --------------------------------------------------------------------
  for (const f of FURNITURE) {
    if (!at(level, f)) continue
    if (f.kind === 'stock') { c.set(...f.pos, f.block); continue }
    if (f.block === 'stonecutter') { c.set(...f.pos, 'stonecutter', { facing: f.facing }); continue }
    furnitureParts[f.part](c, f)
  }

  // buttresses on the back faces
  for (const b of BUTTRESSES) if (at(level, b)) for (const [x, z] of b.cells) masonButtress(c, x, z, 0, b.top, rubble, P.dressedStairs, b.toward)

  // --- Pass 5: ground: stock piles -----------------------------------------------------------------------------------
  for (const s of STOCK) {
    if (!at(level, s)) continue
    materialStack(c, s.cells, s.y, P[s.block], { cap: s.cap ? P[s.cap] : null })
  }
  return c
}

function tracingHouse(c, P, rubble) {
  const T = MASSES.tracing
  const { x0, x1, z0, z1, jettyZ, floorY, upperTop } = T
  // ground storey: rubble, dressed corners, flags inside
  plinth(c, x0, z0, x1, z1, rubble)
  floor(c, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, P.floor)
  for (let x = x0 + 1; x <= x1 - 1; x++) c.set(x, 0, jettyZ, P.dressed) // the apron under the jetty
  wallPanel(c, [x0, z0], [x1, z0], 1, floorY - 1, rubble)
  wallPanel(c, [x0, z1], [x1, z1], 1, floorY - 1, rubble)
  const party = (y, z) => z > z0 + 1 && z < z1 && c.get(x0 - 1, y, z) && !c.name(x0 - 1, y, z).endsWith('_stairs') // the lodge's east gable: the tracing house shares it
  for (let z = z0; z <= z1; z++) for (let y = 1; y <= floorY - 1; y++) if (!party(y, z)) c.set(x0, y, z, rubble); else c.clear(x0, y, z)
  wallPanel(c, [x1, z0], [x1, z1], 1, floorY - 1, rubble)
  quoins(c, [[x0, z0], [x1, z0], [x0, z1], [x1, z1]], 1, floorY - 1, P.dressed, rubble)
  // the tracing floor: a beam ring jettied a block out over the front, on brackets, boards inside
  beamRing(c, x0, jettyZ, x1, z1, floorY, P.beam)
  floor(c, x0 + 1, jettyZ + 1, x1 - 1, z1 - 1, floorY, P.boards)
  jettyBeam(c, 'x', jettyZ, [x0, x1], floorY, P.beam, `${P.wood}_stairs`, [x0, x0 + 2, x1], 'south')
  // upper storey: posts at the corners and the middle of each long side, board infill between
  wallPanel(c, [x0, jettyZ], [x1, jettyZ], floorY + 1, upperTop, P.boards)
  wallPanel(c, [x0, z1], [x1, z1], floorY + 1, upperTop, P.boards)
  for (let z = jettyZ; z <= z1; z++) for (let y = floorY + 1; y <= upperTop; y++) if (!party(y, z)) c.set(x0, y, z, P.boards); else c.clear(x0, y, z)
  wallPanel(c, [x1, jettyZ], [x1, z1], floorY + 1, upperTop, P.boards)
  for (const [x, z] of [[x0, jettyZ], [x1, jettyZ], [x0, z1], [x1, z1], [x0, 5], [x1, 5]]) post(c, x, z, floorY + 1, upperTop, P.post)
  for (const z of [jettyZ, z1]) for (let x = x0 + 1; x <= x1 - 1; x++) c.set(x, upperTop, z, P.beam, { axis: 'x' })
  // the steep tiled roof, ridge to the street, its gables boarded with a king post and a little window
  const r = gableRoof(c, T.roof, P.roof)
  const mid = (T.roof.across[0] + T.roof.across[1]) / 2
  for (const z of [jettyZ, z1]) gableWall(c, 'z', z, T.roof.across, upperTop + 1, r.under, u =>
    u === mid ? [P.post, { axis: 'y' }] : P.boards)
  bargeTrim(c, 'z', T.roof.along[0], T.roof.across, r.under, `${P.wood}_slab`)
  bargeTrim(c, 'z', T.roof.along[1], T.roof.across, r.under, `${P.wood}_slab`)
  // a collar across the roof (the tracing floor's lamp hangs from it)
  for (let x = x0 + 1; x <= x1 - 1; x++) c.set(x, upperTop + 1, 5, P.beam, { axis: 'x' })
  // the lodge's east gable window now looks into the tracing house: walled up
  for (const y of [5, 6]) c.set(11, y, 9, rubble)
  // openings: the street door under the jetty (a lantern hung from the beam either side), windows, the ladder up
  const O = OPENINGS.tracing
  door(c, O.door.at, O.door.facing, 'north', { wood: P.wood, step: O.door.step, stepStair: 'stone_brick_stairs' })
  for (const x of [O.door.at[0] - 1, O.door.at[0] + 1]) c.set(x, floorY - 1, jettyZ, 'lantern', { hanging: 'true', waterlogged: 'false' })
  for (const w of O.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood })
  const ld = O.ladder
  for (let y = ld.y0; y <= ld.top; y++) c.set(ld.x, y, ld.z, 'ladder', { facing: ld.facing, waterlogged: 'false' })
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const name = level === 2 ? 'masons_yard_2' : 'masons_yard'
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${name}_cut${cut}` : name } }
}
