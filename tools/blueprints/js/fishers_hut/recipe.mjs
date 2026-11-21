// Fisher's Hut (the Upturned Boat and the Net Shop), the recipe: assembles plan.mjs from the shared parts library
// (../parts) in passes (block-out and structure, openings, depth and detail, interior, ground, light) and returns the
// structure for the minecraft-builder skill's runner. LEVEL=1|2 picks the level (default 1); Level II draws Level I
// first and adds to it. CUT=<y> builds a cutaway with everything above y removed. Writes furniture.json (the level's
// spots).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { LEVELS, PALETTE, MASSES, OPENINGS, FURNITURE } from './plan.mjs'
import {
  Canvas, mix, stair, slab, trapdoor, floor, door, doorway, window, post, beamRing, brackets, gableRoof, gableWall,
  woodpile, benchRun, upturnedHull, pier, dryingRack, furnitureParts,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const stone = mix(P.stone, 11)
const lantern = (c, x, y, z, hanging) => c.set(x, y, z, 'lantern', { hanging: String(hanging), waterlogged: 'false' })
const log = (c, x, y, z, axis = 'y') => c.set(x, y, z, P.post, { axis })
// a two-high window with a dark sill one out under it (a top slab)
const tallWindow = (c, [x, y, z, out, pot]) => window(c, [[x, y, z], [x, y + 1, z]], out, 'sill', { glass: P.glass, sill: P.sill, pots: pot ? [pot] : [] })
// the catch: a crate (a top slab) heaped with the morning's fish (sea pickles, dry)
const catchCrate = (c, x, y, z, n = 3) => { slab(c, x, y, z, P.sill, 'top'); c.set(x, y + 1, z, 'sea_pickle', { pickles: String(n), waterlogged: 'false' }) }

function cabin(c) { // the upturned boat (west)
  const H = MASSES.hull
  // footing: rubble under the walls (one in from the gunwale), boards inside
  for (let x = H.along[0]; x <= H.along[1]; x++) {
    const r = H.width(x) - 1
    for (let d = -r; d <= r; d++) c.set(x, 0, H.centre + d, Math.abs(d) === r ? stone : P.floor)
  }
  upturnedHull(c, H, { ...P.hull, side: stone })
  log(c, H.keelEnd, H.wall + 1, H.centre, 'x') // the keel run out past the bow
  for (const [x, z] of H.posts) post(c, x, z, 0, H.wall, P.post) // proud posts under the eave ring
}

function netShop(c) { // the tower (east)
  const S = MASSES.shop, R = S.roof
  // ground storey: rubble on a footing, dark corner posts, boards inside
  for (let x = S.x0; x <= S.x1; x++) for (let z = S.z0; z <= S.z1; z++) {
    const edge = x === S.x0 || x === S.x1 || z === S.z0 || z === S.z1
    c.set(x, 0, z, edge ? stone : P.floor)
    if (edge) for (let y = 1; y < S.floorY; y++) c.set(x, y, z, stone)
  }
  for (const x of [S.x0, S.x1]) for (const z of [S.z0, S.z1]) post(c, x, z, 0, S.floorY - 1, P.post)
  // the loft floor: a beam ring at y 4 (jettied a block out over the landing), boards inside
  beamRing(c, S.x0, S.loftZ0, S.x1, S.z1, S.floorY, P.post)
  floor(c, S.x0 + 1, S.loftZ0 + 1, S.x1 - 1, S.z1 - 1, S.floorY, P.floor)
  brackets(c, S.brackets.map(x => [x, S.floorY - 1, S.loftZ0]), P.bracket, 'south')
  // the loft: tarred boards, dark corner posts, a wall plate at y 8
  for (let x = S.x0; x <= S.x1; x++) for (let z = S.loftZ0; z <= S.z1; z++) {
    if (!(x === S.x0 || x === S.x1 || z === S.loftZ0 || z === S.z1)) continue
    for (let y = S.floorY + 1; y < S.plateY; y++) c.set(x, y, z, P.boards)
  }
  for (const x of [S.x0, S.x1]) for (const z of [S.loftZ0, S.z1]) post(c, x, z, S.floorY + 1, S.plateY - 1, P.post)
  for (const x of [S.x0, S.x1]) post(c, x, 9, S.floorY + 1, S.plateY - 1, P.post) // a stud between the side windows
  beamRing(c, S.x0, S.loftZ0, S.x1, S.z1, S.plateY, P.post)
  // the tarred roof over every side, the gables boarded
  const { under } = gableRoof(c, R, P.roof)
  for (const line of [S.loftZ0, S.z1]) gableWall(c, 'z', line, [S.x0, S.x1], S.plateY + 1, under, () => P.boards)
  // proud posts: a row up the east face (full height, the eave sits on them), two up the back under the overhang
  for (const z of S.eastPosts) post(c, S.x1 + 1, z, 0, S.floorY - 1, P.post)
  for (let z = S.z0; z <= S.z1; z++) log(c, S.x1 + 1, S.floorY, z, 'z')
  for (const x of S.backPosts) post(c, x, S.z1 + 1, 0, S.floorY - 1, P.post)
  for (let x = S.x0; x <= S.x1; x++) log(c, x, S.floorY, S.z1 + 1, 'x')
}

function shore(c) { // the jetty and the landing
  const J = MASSES.jetty, L = MASSES.landing
  pier(c, J, J.piles.map(([x, z, h]) => [x, z, h, h ? null : P.jetty.cap]), P.jetty)
  for (const [x, z, h] of J.piles) if (h) lantern(c, x, h + 1, z, false) // the mooring piles carry the lights
  for (let x = L.x0; x <= L.x1; x++) for (let z = L.z0; z <= L.z1; z++) if (!c.get(x, 0, z)) c.set(x, 0, z, P.landing)
}

function openings(c) {
  const O = OPENINGS
  door(c, O.jettyDoor.at, O.jettyDoor.facing, 'north', { wood: P.door })
  door(c, O.backDoor.at, O.backDoor.facing, 'south', { wood: P.door, step: [O.backDoor.at[0], 0, O.backDoor.at[2] + 1] })
  doorway(c, O.cabinDoor)
  door(c, O.loftDoor.at, O.loftDoor.facing, 'north', { wood: P.door }) // nets go in and out here, on the hoist
  for (const w of O.windows) tallWindow(c, w)
  for (const [x, y, z] of O.gable) c.set(x, y, z, P.glass)
  for (const y of [2, 3]) c.set(10, y, 7, P.glass) // the gutting table's window to the water (the bench is its ledge)
  // the girts under the loft's east and back walls are the loft windows' ledges: laid again over the sills
  const S = MASSES.shop
  for (let z = S.z0; z <= S.z1; z++) log(c, S.x1 + 1, S.floorY, z, 'z')
  for (let x = S.x0; x <= S.x1; x++) log(c, x, S.floorY, S.z1 + 1, 'x')
}

function detail(c) {
  const S = MASSES.shop, Hs = S.hoist
  // the hoist beam over the loft door, its tackle hanging (a chain, a lantern for the landing below)
  log(c, Hs.x, Hs.y, Hs.z, 'z')
  for (let i = 1; i <= Hs.chain; i++) c.set(Hs.x, Hs.y - i, Hs.z, 'chain', { axis: 'y', waterlogged: 'false' })
  lantern(c, Hs.x, Hs.y - Hs.chain - 1, Hs.z, true)
  // the landing: a lantern under the loft beam by the jetty door, the bench, the catch crate by the door
  lantern(c, 9, S.floorY - 1, S.loftZ0, true)
  benchRun(c, MASSES.bench, { seat: `${P.wood}_stairs`, spot: true, wood: P.wood })
  catchCrate(c, 8, 1, 6, 4)
  lantern(c, 10, S.floorY - 1, S.z1 + 1, true) // by the back door, under the girt
  // the work room: rods on hooks over the barrel, a second crate of catch by the gutting table, the ladder to the loft
  for (const z of [8, 10]) c.set(8, 3, z, 'tripwire_hook', { facing: 'east', attached: 'false', powered: 'false' })
  catchCrate(c, 10, 1, 9, 2)
  for (let y = 1; y <= S.floorY; y++) c.set(10, y, 10, 'ladder', { facing: 'north', waterlogged: 'false' })
  // the net loft: coils of net and line (dried kelp) along the walls
  for (const [x, z] of [[8, 7], [8, 10], [10, 7]]) c.set(x, S.floorY + 1, z, 'dried_kelp_block')
}

function smokehouse(c) { // Level II
  const S = MASSES.smokehouse, K = MASSES.stack, R = S.roof
  for (let x = S.x0; x <= S.x1; x++) for (let z = S.z0; z <= S.z1; z++) {
    const wall = x === S.x0 || x === S.x1 || z === S.z1
    c.set(x, 0, z, wall ? stone : P.smokeFloor)
    if (wall && !c.get(x, 1, z)) for (let y = 1; y < S.plateY; y++) c.set(x, y, z, stone)
  }
  for (const x of [S.x0, S.x1]) post(c, x, S.z1, 0, S.plateY - 1, P.post)
  for (const z of [13, 15]) post(c, S.x1 + 1, z, 0, S.plateY - 1, P.post) // proud on the east face
  // wall plate, its own gable roof (sooty boards), the back gable boarded
  for (let z = S.z0; z <= S.z1; z++) for (const x of [S.x0, S.x1]) if (z > S.z0) log(c, x, S.plateY, z, 'z')
  for (let x = S.x0; x <= S.x1; x++) log(c, x, S.plateY, S.z1, 'x')
  const free = (x, y, z) => !c.get(x, y, z)
  const { under } = gableRoof(c, R, P.smokeRoof, free)
  gableWall(c, 'z', S.z1, [S.x0, S.x1], S.plateY + 1, under, () => P.boards)
  // openings
  door(c, OPENINGS.smokeDoor.at, OPENINGS.smokeDoor.facing, 'west', { wood: P.door })
  for (const w of OPENINGS.smokeWindows) tallWindow(c, w)
  // the stack: rubble up the back gable from behind the smoker, past the ridge, smoking; a breast a block out
  for (let y = 1; y <= K.top; y++) c.set(K.x, y, K.z, stone)
  c.set(K.x, K.top + 1, K.z, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  c.clear(K.x, under[K.x], K.z + 1) // the ridge's overhang stops at the stack
  for (let y = 0; y <= 2; y++) c.set(K.x, y, K.z + 1, stone)
  stair(c, K.x, 3, K.z + 1, 'cobblestone_stairs', 'north')
  // fish hung in the smoke from the roof
  for (const x of [8, 10]) {
    c.set(x, 5, 14, 'chain', { axis: 'y', waterlogged: 'false' })
    c.set(x, 4, 14, 'dried_kelp_block')
  }
  // the yard: the hjell, fuel against the west wall, a trodden path from the land behind to the side door
  dryingRack(c, MASSES.rack, P.rack)
  woodpile(c, MASSES.woodpile.x, MASSES.woodpile.z0, MASSES.woodpile.z1, P.post)
  const [dx, , dz] = OPENINGS.smokeDoor.at
  stair(c, dx - 1, 0, dz, 'cobblestone_stairs', 'east') // the step up to the side door
  lantern(c, dx - 1, S.plateY - 1, dz, true) // hung under the eave by the door
}

export function draw(level) {
  const c = new Canvas(LEVELS[level].size)
  cabin(c)
  netShop(c)
  shore(c)
  openings(c)
  detail(c)
  if (level >= 2) smokehouse(c)
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
