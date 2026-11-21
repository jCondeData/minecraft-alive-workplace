// Artisan's House, savanna look (49.10g), the recipe: assembles plan.mjs from the parts library (../parts) in passes
// (block-out and structure, roof, openings, depth and detail, interior, ground) for LEVEL = 1..3; each level draws the
// one below first, so II keeps I's cells and III is II block for block above the ground. Writes furniture.json and,
// for III, out/cellar.txt (the cellar's inside, for rebuild.py --cellar). Build: build.sh. CUT=<y>: a cutaway.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, BASEMENT, PALETTE, HOUSE, HIP, KILN, SHELTER, WING, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, LiftedCanvas, mix, stair, slab, plinth, floor, post, beamRing, wallPanel, window, door, flight, stairwell,
  railingRun, cellar, hatchLadder, furnitureParts, hipRoof,
} from '../parts/index.mjs'
import { moundKiln } from '../parts/mound_kiln.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const granite = mix(P.granite, 5), mud = mix(P.mud, 6), terra = mix(P.plinth, 7), earth = (x, y, z) => (KILN.lips.includes(y) ? P.kiln.lip : y >= KILN.neckYs[0] && y <= KILN.neckYs[1] ? P.kiln.neck : P.kiln.body)
const pot = (c, [x, y, z], facing = 'north') => c.set(x, y, z, P.pot, { facing, cracked: 'false', waterlogged: 'false' })

// --- Level I: the potter's house, the mound kiln and the fuel shelter ------------------------------------------------
function levelOne(c) {
  const H = HOUSE, { x0, x1, z0, z1 } = H.walls, U = H.upper

  // Pass 1: block-out and structure
  plinth(c, x0 - 1, z0, x1 + 1, z1 + 1, terra) // the plinth and its skirt
  plinth(c, OUTSIDE.stoep.x0, OUTSIDE.stoep.z, OUTSIDE.stoep.x1, OUTSIDE.stoep.z, P.stoep) // the stoep's floor
  floor(c, x0 + 1, z0 + 1, x1 - 1, 5, 0, P.workFloor) // the workroom's mud floor
  floor(c, x0 + 1, 6, x1 - 1, z1 - 1, 0, P.floor) // the hall's boards
  for (const [a, b] of [[[x0, z0], [x1, z0]], [[x0, z1], [x1, z1]], [[x0, z0], [x0, z1]], [[x1, z0], [x1, z1]]])
    wallPanel(c, a, b, 1, H.groundTop, granite)
  for (const [x, z] of [[x0, z1], [x1, z1]]) post(c, x, z, 1, H.groundTop, P.frame) // the back corners
  for (const x of [x0, x1]) post(c, x, z0, 1, H.groundTop, P.frame) // the front corners, behind the stoep posts
  for (const x of H.stoepPosts) post(c, x, U.z0, 1, H.groundTop, P.frame) // the stoep posts carry the upper storey

  // the upper storey: the grey sill beam ring (a block out over the stoep), its floor, the painted band, packed mud,
  // acacia posts at the corners, the top beam ring and the tie beams the lamps hang from
  beamRing(c, U.x0, U.z0, U.x1, U.z1, H.sillY, P.frame)
  floor(c, U.x0 + 1, U.z0 + 1, U.x1 - 1, U.z1 - 1, H.sillY, P.floor)
  for (const [a, b] of [[[U.x0, U.z0], [U.x1, U.z0]], [[U.x0, U.z1], [U.x1, U.z1]], [[U.x0, U.z0], [U.x0, U.z1]], [[U.x1, U.z0], [U.x1, U.z1]]]) {
    wallPanel(c, a, b, H.paintY, H.paintY, (x, y, z) => P.paint[(x + z) % 2])
    wallPanel(c, a, b, H.paintY + 1, H.upperTop, mud)
  }
  for (const [x, z] of [[U.x0, U.z0], [U.x1, U.z0], [U.x0, U.z1], [U.x1, U.z1]]) post(c, x, z, H.paintY, H.upperTop, P.frame)
  for (const [x, y] of H.jambs) for (const yy of [y, y + 1]) c.set(x, yy, U.z0, P.jamb)
  beamRing(c, U.x0, U.z0, U.x1, U.z1, H.beamY, P.frame)
  for (const z of H.ties) for (let x = U.x0 + 1; x < U.x1; x++) c.set(x, H.beamY, z, P.frame, { axis: 'x' })

  // Pass 2: the roof: one golden hip, eaves a block out on every side, the ridge along the house
  hipRoof(c, HIP, P.roof)

  // the mound kiln: red earth stepped up to a smoking flue spire; its fire mouth opens into the workroom
  const K = KILN, km = OPENINGS.kilnMouth, sh = OPENINGS.stokeHole
  const isMouth = (x, y, z) => (x === km.at[0] && y === km.at[1] && z === km.at[2]) || (x === sh.at[0] && y === sh.at[1] && z === sh.at[2])
  moundKiln(c, K.cx, K.cz, { courses: K.courses, turrets: K.turrets }, { foot: P.kiln.foot, body: earth, cap: P.kiln.cap },
    (x, y, z) => !isMouth(x, y, z) && (x > x1 || !c.get(x, y, z)))
  for (let z = K.breast.z0; z <= K.breast.z1; z++) for (let y = 1; y <= K.breast.y1; y++) c.set(K.breast.x, y, z, mud)
  c.set(...km.at, 'furnace', { facing: km.facing, lit: 'false' })
  c.set(...sh.at, 'furnace', { facing: sh.facing, lit: 'false' })
  stair(c, ...K.hood, P.kiln.hood, 'west', 'top') // a mud hood over the stoke hole
  for (const { z, x0: fx } of K.fuel) for (let x = fx; x <= 15; x++) {
    const high = x === 15 ? 1 : 2 // stepped down at the outer end
    for (let y = 1; y <= high; y++) c.set(x, y, z, 'acacia_log', { axis: 'z' }) // bark to the yard
    if (x < 15) slab(c, x, 3, z, P.roof.slab, 'bottom') // a thatch lid over the inner two
    c.set(x, 0, z, 'coarse_dirt')
  }

  // the fuel shelter: two acacia posts and a thatch hip leaning on the house's east wall behind the kiln
  const S = SHELTER, R = S.roof
  for (let x = R.x0 - 1; x <= R.x1 - 1; x++) for (let z = R.z0; z <= R.z1 - 1; z++) if (!c.get(x, 0, z)) c.set(x, 0, z, 'coarse_dirt')
  for (const [x, z] of S.posts) post(c, x, z, 1, R.eaveY - 1, P.frame)
  hipRoof(c, R, P.roof, (x, y, z) => !c.get(x, y, z))
  for (let z = S.logs.z0; z <= S.logs.z1; z++) for (let y = 1; y <= S.logs.high; y++) c.set(S.logs.x, y, z, 'acacia_log', { axis: 'x' })
  for (const [x, z] of S.nook) for (let y = 1; y <= S.logs.high; y++) c.set(x, y, z, 'acacia_log', { axis: 'z' }) // stacked in the kiln's lee
  for (const p of S.clay) c.set(...p, 'clay')
  c.set(...S.cauldron, 'cauldron')

  // Pass 3: openings
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood })
  stair(c, ...fd.step, 'mud_brick_stairs', 'south')
  c.set(fd.at[0], fd.at[1] + 2, fd.at[2], P.frame, { axis: 'x' }) // the door's lintel
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.shutter })
  const st = OPENINGS.stairs
  stairwell(c, st.well)
  flight(c, st.flight, st.rises, P.wood, granite)
  railingRun(c, OPENINGS.rail, P.wood)

  // Pass 4: depth and detail: the stoep, her wares, the lamp over the door
  c.set(...OPENINGS.doorLamp, 'lantern', { hanging: 'true', waterlogged: 'false' })
  c.set(...OUTSIDE.wares, 'barrel', { facing: 'up', open: 'false' })
  for (const p of OUTSIDE.pots) pot(c, p)
  const F = OUTSIDE.forecourt
  for (let x = F.x0; x <= F.x1; x++) if (!c.get(x, 0, F.z)) c.set(x, 0, F.z, (x + F.z) % 3 ? 'coarse_dirt' : 'packed_mud')

  // Pass 5: interior: the shipped house's furniture, cell for cell
  for (const f of FURNITURE[1]) furnitureParts[f.part](c, f)
  pot(c, [6, 2, 3], 'west') // wares on the kept barrel
}

// --- Level II: the journeyman's wing, the drying bench and the firing pit --------------------------------------------
function levelTwo(c) {
  const W = WING, { x0, x1, z0, z1 } = W.walls
  const hb = OPENINGS.windows.find(w => w.level1Only)
  for (const [x, y, z] of hb.cells) c.set(x, y, z, granite) // the hall's back window walled up for the doorway
  for (const [x, , z] of hb.cells) for (const dx of [-1, 1]) c.clear(x + dx, hb.cells[0][1], z + 1), c.clear(x + dx, hb.cells[0][1] + 1, z + 1) // its shutters

  // Pass 1: the wing on its plinth, packed mud between acacia corners, a beam ring, its half-hip
  plinth(c, x0 - 1, z0, x1 + 1, z1 + 1, terra)
  floor(c, x0 + 1, z0, x1 - 1, z1 - 1, 0, P.floor)
  wallPanel(c, [x0, z0], [x0, z1], 1, 4, mud)
  wallPanel(c, [x1, z0], [x1, z1], 1, 4, mud)
  wallPanel(c, [x0, z1], [x1, z1], 1, 4, mud)
  for (const [x, z] of W.posts) post(c, x, z, 1, 4, P.frame)
  for (let z = z0; z <= z1; z++) for (const x of [x0, x1]) c.set(x, 5, z, P.frame, { axis: 'z' })
  for (let x = x0 + 1; x < x1; x++) c.set(x, 5, z1, P.frame, { axis: 'x' })
  c.set(x0, 5, z1, P.frame, { axis: 'y' }); c.set(x1, 5, z1, P.frame, { axis: 'y' })
  for (let x = x0 + 1; x < x1; x++) c.set(x, 4, z1, (x + z1) % 2 ? P.paint[0] : P.paint[1]) // the painted band under its eave
  hipRoof(c, W.roof, P.roof, (x, y, z) => z >= W.from && !c.get(x, y, z))

  // Pass 2: openings
  const wd = OPENINGS.wingDoorway
  for (let y = wd.at[1]; y < wd.at[1] + wd.height; y++) c.clear(wd.at[0], y, wd.at[2])
  c.set(wd.at[0], 0, wd.at[2], P.floor)
  for (const w of OPENINGS.wingWindows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.shutter })

  // Pass 4: interior
  for (const f of FURNITURE[2]) furnitureParts[f.part](c, f)

  // Pass 5: ground: the drying bench (a rail post, the kept barrel, the quench tub) with fired pots on its top
  const b = OUTSIDE.bench
  for (let x = b.x0 - 1; x <= b.x1 + 3; x++) for (let z = b.z - 1; z <= b.z + 2; z++) if (!c.get(x, 0, z)) c.set(x, 0, z, (x * 3 + z) % 4 ? 'coarse_dirt' : 'packed_mud')
  c.set(b.x0, 1, b.z, `${P.fence}_fence`)
  c.set(b.x0 + 1, 1, b.z, 'barrel', { facing: 'up', open: 'false' })
  c.set(b.x1, 1, b.z, 'cauldron') // the quench tub
  slab(c, b.x0, 2, b.z, 'acacia_slab', 'bottom')
  slab(c, b.x0 + 1, 2, b.z, 'acacia_slab', 'bottom')
  pot(c, [b.x0, 3, b.z]); pot(c, [b.x0 + 1, 3, b.z])
  // the open firing pit: a ring of mud bricks round last night's ashes, two big jars set out to cool
  const { cx, cz } = OUTSIDE.pit
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) {
    if (!dx && !dz) { c.set(cx, 0, cz, 'coarse_dirt'); c.set(cx, 1, cz, 'campfire', { facing: 'north', lit: 'false', signal_fire: 'false', waterlogged: 'false' }); continue }
    slab(c, cx + dx, 1, cz + dz, 'mud_brick_slab', 'bottom')
  }
  for (const [x, z] of [[cx - 2, cz + 1], [cx, cz + 2]]) { // each jar stands on the yard's ground (not on the plot's edge air)
    if (!c.get(x, 0, z)) c.set(x, 0, z, 'coarse_dirt')
    pot(c, [x, 1, z])
  }
}

// --- Level III: the clay cellar ---------------------------------------------------------------------------------------
function levelThree(c) {
  const inside = cellar(c, 1, 2, 7, 12, 4, mix(P.cellarWall, 3), P.cellarFloor)
  const L = OPENINGS.hatch
  hatchLadder(c, L.x, L.z, -3, 0, L.facing, P.wood)
  // the family's chests along the front wall (the kept cells), jars of slip in casks along the east wall
  for (let x = 2; x <= 6; x++) {
    c.set(x, -3, 3, 'chest', { facing: 'south', type: 'single', waterlogged: 'false' })
    c.set(x, -2, 3, 'barrel', { facing: 'south', open: 'false' })
  }
  for (let z = 6; z <= 10; z++) c.set(6, -3, z, 'barrel', { facing: 'west', open: 'false' })
  for (const z of [7, 9]) c.set(6, -2, z, 'barrel', { facing: 'west', open: 'false' })
  // clay stock in the back corner, a sorting table, unfired pots on the floor by it
  for (const [x, y, z] of [[2, -3, 11], [3, -3, 11], [2, -2, 11], [2, -3, 10]]) c.set(x, y, z, 'clay')
  c.set(4, -3, 8, `${P.fence}_fence`); c.set(4, -2, 8, 'brown_carpet')
  pot(c, [4, -3, 10])
  for (const z of [5, 9]) c.set(4, -1, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
  return inside
}

export function draw(level = 1) {
  const c = level === 3 ? new LiftedCanvas(SIZE[3], BASEMENT) : new Canvas(SIZE[level])
  levelOne(c)
  if (level >= 2) levelTwo(c)
  if (level >= 3) c.cellarInside = levelThree(c)
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const id = level === 1 ? 'artisans_house' : `artisans_house_${level}`
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  fs.mkdirSync(path.join(here, 'out'), { recursive: true })
  if (c.cellarInside) fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), c.cellarInside.join(','))
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
