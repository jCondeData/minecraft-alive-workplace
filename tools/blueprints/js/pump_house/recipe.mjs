// The Pump House, the recipe: assembles plan.mjs from the shared parts library (../parts) in passes (block-out and
// structure, the machine and its water, openings, depth and detail, interior, ground, light) and returns the structure
// for the minecraft-builder skill's runner. Also writes furniture.json (every furniture spot) for rebuild.py.
// Build: ./build.sh (a candidate in build/rebuild/pump_house/); CUT=<y> ./build.sh writes a cutaway .nbt only.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, MACHINE, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, stair, plinth, floor, post, wallPanel, window, door, gableRoof, gableWall, bargeTrim, hood, brackets,
  furnitureParts, pool, fallRace, cog, shaftRun, waterWheel, pipeRun, tallTank, corbelGallery, steepHollowCap, hatchLadder,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = mix(P.stone, 11)
  const wet = mix(P.wetStone, 12)
  const base = mix(P.plinth, 13)
  const T = MASSES.tower, K = MASSES.tannery, M = MACHINE
  const empty = (x, y, z) => !c.get(x, y, z)

  // --- Pass 1: block-out and structure ------------------------------------------------------------------------------
  // Tower: a stone shaft on a rubble plinth, wetter stone low down where the cistern and the race keep it damp
  plinth(c, T.x0, T.z0, T.x1, T.z1, base)
  for (let y = 1; y <= T.top; y++) for (let x = T.x0; x <= T.x1; x++) for (let z = T.z0; z <= T.z1; z++) {
    const ring = x === T.x0 || x === T.x1 || z === T.z0 || z === T.z1
    if (ring) c.set(x, y, z, y <= 2 ? wet : stone)
  }
  // the dressed corners: chiseled stone at the top course of each corner
  for (const [x, z] of [[T.x0, T.z0], [T.x1, T.z0], [T.x0, T.z1], [T.x1, T.z1]]) c.set(x, T.top, z, P.dressed)

  // Tannery: a rubble plinth, mud-brick walls between spruce posts, a beam ring tying them (never over the tower)
  const { posts: kp, panels: kn, room } = K
  plinth(c, kp.x0, kp.z0, kp.x1, kp.z1, base)
  floor(c, room.x0, room.z0, room.x1, room.z1, 0, P.floor)
  const keep = (x, y, z) => empty(x, y, z)
  for (const [a, b] of [[[kn.x0, kn.z0], [kn.x1, kn.z0]], [[kn.x0, kn.z1], [kn.x1, kn.z1]], [[kn.x0, kn.z0], [kn.x0, kn.z1]], [[kn.x1, kn.z0], [kn.x1, kn.z1]]])
    wallPanel(c, a, b, 1, K.wallTop, (x, y, z) => c.name(x, y, z) !== 'minecraft:air' ? c.get(x, y, z).id : P.infill)
  for (const x of K.frontPosts) if (empty(x, 1, kp.z0)) post(c, x, kp.z0, 1, K.wallTop, P.frame)
  for (const x of K.backPosts) post(c, x, kp.z1, 1, K.wallTop, P.frame)
  for (const z of K.westPosts) post(c, kp.x0, z, 1, K.wallTop, P.frame)
  for (const z of K.eastPosts) post(c, kp.x1, z, 1, K.wallTop, P.frame)
  for (let x = kp.x0; x <= kp.x1; x++) for (let z = kp.z0; z <= kp.z1; z++) {
    const ringCell = x === kp.x0 || x === kp.x1 || z === kp.z0 || z === kp.z1
    if (!ringCell || !empty(x, K.beamY, z)) continue
    const corner = (x === kp.x0 || x === kp.x1) && (z === kp.z0 || z === kp.z1)
    c.set(x, K.beamY, z, P.beam, { axis: corner ? 'y' : (z === kp.z0 || z === kp.z1) ? 'x' : 'z' })
  }
  // the tie beam across the room the lamps hang from
  for (let x = kn.x0 + 1; x < kn.x1; x++) c.set(x, K.beamY, K.tieBeamZ, P.beam, { axis: 'x' })

  // --- Pass 2: the machine and its water ------------------------------------------------------------------------------
  // the cistern: a curb round four poured sources at the tower's foot (it refills itself as the pump draws)
  for (const [x, y, z] of M.curb) c.set(x, y, z, wet)
  pool(c, M.pool.map(([x, , z]) => [x, z]), 1)
  // the pump over the cistern, its cog meshing beside it, the drive through the east wall to the wheel
  pipeRun(c, M.intake, { before: 'down', after: 'east' })
  c.set(...M.pump.at, 'create:mechanical_pump', { facing: M.pump.facing })
  cog(c, M.cog.at, M.cog.axis)
  shaftRun(c, M.shafts, 'x')
  waterWheel(c, M.wheel.at, M.wheel.facing)
  // the riser up the shaft and into the tank's foot; the tank stands in the belvedere
  pipeRun(c, M.outlet, { before: 'west', after: 'up' })
  tallTank(c, M.tank)
  // the race: the trough the fall drops into (and the wheel's bed), the cup on the tower's flank, the fall
  for (let x = 16; x <= 17; x++) for (let z = 2; z <= 4; z++) c.set(x, 0, z, base)
  fallRace(c, { ...M.race, corbels: [] }, { wall: wet, floorId: base, stairs: P.stoneStairs })
  // the cup's side stones carried on corbels with their backs to the tower; the outer stone hangs from them
  brackets(c, [[16, 4, 2], [16, 4, 4]], P.stoneStairs, 'west')

  // --- Pass 3: the tower's top ------------------------------------------------------------------------------------------
  const G = T.gallery
  // the tower's inside floor at the gallery's level, the riser passing through it
  corbelGallery(c, { ...G, skip: [[13, G.y, 3], ...T.corbelSkip] }, { deck: P.deck, corbel: P.stoneStairs, rail: `${P.wood}_fence` })
  // the belvedere's corner posts up to the cap
  for (const x of [T.x0, T.x1]) for (const z of [T.z0, T.z1]) post(c, x, z, G.y + 1, T.beltTop - 1, P.frame)
  // the belvedere glazed between its posts, so the tank stands in a lantern of glass
  for (let y = G.y + 1; y < T.beltTop; y++) for (let x = T.x0; x <= T.x1; x++) for (let z = T.z0; z <= T.z1; z++) {
    const ring = x === T.x0 || x === T.x1 || z === T.z0 || z === T.z1
    if (ring && !c.get(x, y, z)) c.set(x, y, z, P.glass)
  }
  const cap = T.cap
  steepHollowCap(c, cap.cx, cap.cz, cap.r, cap.y0, P.cap, 'lightning_rod')
  // a beam ring under the cap's first inner course, on the posts: the belvedere's head
  for (let x = T.x0; x <= T.x1; x++) for (let z = T.z0; z <= T.z1; z++) {
    const ring = x === T.x0 || x === T.x1 || z === T.z0 || z === T.z1
    if (!ring || c.get(x, T.beltTop, z)) continue
    const corner = (x === T.x0 || x === T.x1) && (z === T.z0 || z === T.z1)
    c.set(x, T.beltTop, z, P.beam, { axis: corner ? 'y' : (z === T.z0 || z === T.z1) ? 'x' : 'z' })
  }

  // --- Pass 4: the tannery's roof and gables ----------------------------------------------------------------------------
  const R = gableRoof(c, K.roof, P.roof, keep)
  const gablePick = (u, y) => {
    if (u === 7 && y !== 7) return [P.frame, { axis: 'y' }] // king post, the gable window in it
    if (u === 6 || u === 8) return [P.frame, { axis: 'y' }] // studs over the door and back posts, carried up into the gable
    return P.infill
  }
  gableWall(c, 'z', kp.z0, [kp.x0, kp.x1], K.beamY + 1, R.under, (u, y) => (empty(u, y, kp.z0) ? gablePick(u, y) : null))
  gableWall(c, 'z', kp.z1, [kp.x0, kp.x1], K.beamY + 1, R.under, gablePick)
  bargeTrim(c, 'z', K.roof.along[0], K.roof.across, R.under, `${P.wood}_stairs`)
  bargeTrim(c, 'z', K.roof.along[1], K.roof.across, R.under, `${P.wood}_stairs`)

  // --- Pass 5: openings -------------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step })
  hood(c, fd.at[0], K.beamY, kp.z0 - 1, P.roof)
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood })
  // the tower's door onto the dry cell by the cistern, and the ladder up to a hatch in the gallery deck
  door(c, T.door.at, 'south', 'north', { wood: P.wood, step: T.door.step, stepStair: P.stoneStairs })
  const L = T.ladder
  hatchLadder(c, L.x, L.z, L.y0, L.top, L.facing, P.wood)
  // and the belvedere's door out onto the gallery, in place of the front's middle panes
  door(c, T.galleryDoor, 'south', 'north', { wood: P.wood })

  // --- Pass 6: interior -------------------------------------------------------------------------------------------------
  for (const f of FURNITURE) {
    if (f.part === 'vat') { c.free(f.pos); c.set(...f.pos, 'cauldron'); continue }
    furnitureParts[f.part](c, f)
  }

  // --- Pass 7: ground and light -----------------------------------------------------------------------------------------
  for (const [x, y, z] of OUTSIDE.path) c.set(x, y, z, P.stoneSlab, { type: 'bottom', waterlogged: 'false' }) // flagstones
  for (const [x, y, z] of OUTSIDE.lamps) c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `pump_house_cut${cut}` : 'pump_house' } }
}
