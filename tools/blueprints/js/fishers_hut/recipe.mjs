// Fisher's Hut (the Upturned Boat), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes
// (block-out and structure, openings, depth and detail, interior, ground, light) and returns the structure for the
// minecraft-builder skill's runner. LEVEL=1|2 picks the level (default 1); Level II draws Level I first, untouched, and
// adds to it. CUT=<y> builds a cutaway with everything above y removed. Writes furniture.json (the level's spots).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { LEVELS, PALETTE, MASSES, OPENINGS, FURNITURE } from './plan.mjs'
import {
  Canvas, mix, stair, floor, door, window, post, chimney, woodpile, benchRun, upturnedHull, transom, pier, dryingRack,
  furnitureParts,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const stone = mix(P.stone, 11)
const lantern = (c, x, y, z, hanging) => c.set(x, y, z, 'lantern', { hanging: String(hanging), waterlogged: 'false' })
// a two-high window in a wall with a dark sill one out under it (a top slab on the wall's foot course)
const tallWindow = (c, x, z, out) => window(c, [[x, 2, z], [x, 3, z]], out, 'sill', { glass: P.glass, sill: P.sill })

function hut(c) {
  const H = MASSES.hull, J = MASSES.jetty, Po = MASSES.porch

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // the footing: rubble under the walls (one in from the hull's gunwale), boards inside
  for (let x = H.along[0]; x <= H.along[1]; x++) {
    const r = H.width(x) - 1
    for (let d = -r; d <= r; d++) c.set(x, 0, H.centre + d, Math.abs(d) === r ? stone : P.floor)
  }
  for (const [x, z] of MASSES.posts) c.set(x, 0, z, stone)
  // the walls and the old boat resting on them, its eave ring overhanging
  upturnedHull(c, H, { ...P.hull, side: stone })
  for (const x of H.keelEnds) c.set(x, H.wall + 1, H.centre, P.hull.keel, { axis: 'x' }) // stem and sternpost run out
  // the jetty: a slab deck over the water on four piles, the end pair rising as mooring piles
  pier(c, J, J.piles.map(([x, z, h]) => [x, z, h, h ? null : P.jetty.cap]), P.jetty)

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  door(c, OPENINGS.frontDoor.at, OPENINGS.frontDoor.facing, 'north', { wood: P.door })
  door(c, OPENINGS.backDoor.at, OPENINGS.backDoor.facing, 'south', { wood: P.door })
  for (const [x, z, out] of OPENINGS.windows) tallWindow(c, x, z, out)

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  // proud dark posts at every step of the oval, up to the eave ring
  for (const [x, z] of MASSES.posts) post(c, x, z, 1, H.wall, P.post)
  // the porch: a plank stoop, a pent of dark stairs flaring out under the eave over the door, lanterns either side
  floor(c, Po.x0, Po.z0, Po.x1, Po.z1, 0, P.porch.floor)
  for (let x = Po.x0; x <= Po.x1; x++) stair(c, x, Po.pentY, Po.z0, P.porch.pent, 'south')
  for (const x of [Po.x0, Po.x1]) lantern(c, x, Po.pentY - 1, Po.z0, true)
  benchRun(c, MASSES.bench, { seat: `${P.wood}_stairs`, spot: true, wood: P.wood })
  // the mooring piles at the jetty's end carry the lights: a lantern standing on each
  for (const [x, z, h] of J.piles) if (h) lantern(c, x, h + 1, z, false)
  // the old mast stood up by the door: a spar, a yard across it, lanterns hung from the yard's ends and one on top
  const M = MASSES.mast
  post(c, M.x, M.z, 0, M.top, P.mast.spar)
  for (let x = M.yard[0]; x <= M.yard[1]; x++) if (x !== M.x) c.set(x, M.yardY, M.z, P.mast.yard)
  for (const x of M.yard) if (x !== M.x) lantern(c, x, M.yardY - 1, M.z, true)
  lantern(c, M.x, M.top + 1, M.z, false)
}

function smokehouse(c) {
  const S = MASSES.smokehouse, K = MASSES.stack
  const [front, back] = S.walls
  // footing and floor
  for (let z = front; z <= back; z++) for (let x = S.centre - 2; x <= S.centre + 2; x++) {
    const edge = Math.abs(x - S.centre) === 2 || z === back
    c.set(x, 0, z, edge ? stone : P.smokeFloor)
  }
  // the dinghy on its walls, its bow running into the hut's roof (it keeps every block already there)
  const free = (x, y, z) => !c.get(x, y, z)
  const hull = upturnedHull(c, { ...S, width: () => 3, keep: free }, { ...P.hull, side: stone })
  for (const x of [S.centre - 2, S.centre + 2]) for (let y = 1; y <= S.wall; y++) c.clear(x, y, S.along[1]) // the stern overhangs
  transom(c, S, back, hull, stone)
  c.set(S.centre, S.wall + 2, front, P.hull.full) // where the bow meets the hut's eave: closed under the keel
  door(c, OPENINGS.smokeDoor.at, OPENINGS.smokeDoor.facing, 'west', { wood: P.door })
  for (const [x, z, out] of OPENINGS.smokeWindows) tallWindow(c, x, z, out)
  // the stack: rubble from the ground past the keel, smoking
  chimney(c, K.x, K.z, 0, K.top, stone, { breast: { axis: 'x', top: 2, stair: 'cobblestone_stairs' } })
  // the hjell east of it, a woodpile of fuel against its wall
  dryingRack(c, MASSES.rack, P.rack)
  woodpile(c, MASSES.woodpile.x, MASSES.woodpile.z0, MASSES.woodpile.z1, 'spruce_log')
  // the yard: a trodden path round the west side from the land behind to the side door
  const [, , d] = LEVELS[2].size
  const [dx, , dz] = OPENINGS.smokeDoor.at
  for (let x = 1; x < dx; x++) c.set(x, 0, dz, P.path)
  for (let z = dz + 1; z < d; z++) c.set(1, 0, z, P.path)
}

export function draw(level) {
  const c = new Canvas(LEVELS[level].size)
  hut(c)
  if (level >= 2) smokehouse(c)
  // --- Pass 4: interior (the plan's furniture) --------------------------------------------------------------------
  for (const f of FURNITURE) if ((f.level ?? 1) <= level) furnitureParts[f.part](c, f)
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const name = level === 2 ? 'fishers_hut_2' : 'fishers_hut'
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${name}_cut${cut}` : name } }
}
