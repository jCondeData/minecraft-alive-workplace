// Artisan's House I-III, the taiga look (ROADMAP 49.10g): assembles plan.mjs from the parts library (../parts) in passes
// (block-out and structure, roofs and gables, openings, depth and detail, interior, ground) for LEVEL = 1..3, each level
// drawing the one below first, so II keeps I's cells and III is II block for block above the ground. Writes
// furniture.json (every furniture spot) and, for III, out/cellar.txt (the cellar's inside, for rebuild.py --cellar).
// Build: build.sh. CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, LiftedCanvas, mix, stair, slab, plinth, floor, window, door, gableRoof, flight, stairwell, railingRun,
  flowerBed, cellar, hatchLadder, furnitureParts,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const stone = mix(P.stone, 11)
const footStone = mix(P.footStone, 5)
const H = MASSES.house, B = MASSES.barn, K = MASSES.kiln, W = MASSES.wing

const log = (c, x, y, z, axis, id = P.log) => c.set(x, y, z, id, { axis })

// a log gable under a roof: logs along the wall from y0 up to under each column, the end logs proud at the rakes
function logGable(c, z, [a0, a1], y0, under, skip = () => false) {
  for (let x = a0 + 1; x < a1; x++) for (let y = y0; y < under[x]; y++) if (!skip(x, y)) log(c, x, y, z, 'x')
}
// the solid verge: a slate stair upside down under each rake stair (manor round 2), so the rake is two deep
function verge(c, z, [a0, a1], under, roof) {
  const mid = (a0 + a1) / 2
  for (let x = a0; x <= a1; x++) {
    const y = under[x] - 1
    if (y < 1 || c.get(x, y, z) || x === mid || Math.abs(x - mid) === 0.5) continue
    stair(c, x, y, z, roof.stairs, x < mid ? 'west' : 'east', 'top')
  }
}

// --- Level I: the log house and its kiln barn ---------------------------------------------------------------------
function levelOne(c) {
  const { x0, x1, z0, z1 } = H.walls
  const U = H.upper

  // Pass 1: block-out and structure
  plinth(c, x0 - 1, z0, x1 + 1, z1 + 1, footStone) // the footing and its skirt
  plinth(c, x0, 1, x1, 1, stone) // the stoop under the jetty
  floor(c, x0 + 1, z0 + 1, x1 - 1, 5, 0, P.workFloor)
  floor(c, x0 + 1, 6, x1 - 1, z1 - 1, 0, P.floor)
  // the workroom storey: fieldstone, mossier at its foot
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (x !== x0 && x !== x1 && z !== z0 && z !== z1) continue
    for (let y = 1; y <= H.groundTop; y++) c.set(x, y, z, y === 1 ? footStone : stone)
  }
  // the log storey: the pale sill beam (the jetty line) and its floor, notched log walls, the dark beam ring, ties
  for (let x = U.x0; x <= U.x1; x++) for (let z = U.z0; z <= U.z1; z++) {
    const edgeX = x === U.x0 || x === U.x1, edgeZ = z === U.z0 || z === U.z1
    if (!edgeX && !edgeZ) { c.set(x, H.sillY, z, P.floor); continue }
    log(c, x, H.sillY, z, edgeZ && !edgeX ? 'x' : edgeX && !edgeZ ? 'z' : 'x', P.pale)
    for (let y = H.sillY + 1; y <= H.upperTop; y++) {
      const axis = edgeX && edgeZ ? (y % 2 === 0 ? 'x' : 'z') : edgeZ ? 'x' : 'z'
      log(c, x, y, z, axis)
    }
    log(c, x, H.beamY, z, edgeZ && !edgeX ? 'x' : edgeX && !edgeZ ? 'z' : 'x')
  }
  for (const z of H.ties) for (let x = U.x0 + 1; x < U.x1; x++) log(c, x, H.beamY, z, 'x')
  // the notches' crossing logs proud of the corners on alternate courses
  for (const z of [U.z0, U.z1]) for (const y of [6, 8]) log(c, U.x0 - 1, y, z, 'x') // (east: over the barn's roof, none)
  for (const x of [U.x0, U.x1]) { log(c, x, 7, U.z0 - 1, 'z'); log(c, x, 7, U.z1 + 1, 'z') }
  // the jetty's corbels: proud log ends under the sill beam, a spruce bracket under each
  for (const x of H.corbels) { log(c, x, H.sillY - 1, U.z0, 'z', P.pale); stair(c, x, H.sillY - 2, U.z0, `${P.wood}_stairs`, 'south', 'top') }

  // Pass 2: roofs and gables
  const main = gableRoof(c, H.roof, P.roof)
  for (const z of [U.z0, U.z1]) logGable(c, z, H.roof.across, H.beamY + 1, main.under)
  for (const z of H.roof.along) verge(c, z, H.roof.across, main.under, P.roof)
  // the upper rooms' ceiling: spruce boards lining the slopes
  for (let z = U.z0 + 1; z < U.z1; z++) for (const [x, y] of [[2, 10], [3, 11], [4, 12], [5, 11], [6, 10]]) c.set(x, y, z, P.lining)
  // the konek: the neck a stair on the ridge, the horse's head over the front rake, a trapdoor its forelock and ears
  const KN = MASSES.konek, ry = main.ridgeY
  stair(c, KN.x, ry + 1, KN.z + 1, `${P.wood}_slab`.replace('_slab', '_stairs'), 'north') // round 2: a longer spruce neck
  stair(c, KN.x, ry + 1, KN.z, P.konek, 'north')
  stair(c, KN.x, ry + 1, KN.z - 1, P.konek, 'south', 'top')
  c.set(KN.x, ry + 2, KN.z - 1, P.konekEar, { facing: 'south', half: 'bottom', open: 'false', powered: 'false', waterlogged: 'false' })

  // the kiln barn: log posts on fieldstone pads, a spruce plate, a slate gable parallel to the house's
  for (let x = B.x0; x <= B.x1; x++) for (let z = B.z0; z <= B.z1; z++) c.set(x, 0, z, mix(P.barnFloor, x * 7 + z))
  // pale stripped posts and plate, so the barn is framed like the house's jetty line
  for (const [x, z] of B.posts) { c.set(x, 0, z, footStone); for (let y = 1; y < B.plate; y++) log(c, x, y, z, 'y', P.pale) }
  for (let x = B.x0; x <= B.x1; x++) for (const z of [B.z0, B.z1]) log(c, x, B.plate, z, 'x', P.pale)
  for (let z = B.z0 + 1; z < B.z1; z++) for (const x of [B.x0, B.x1]) log(c, x, B.plate, z, 'z', P.pale)
  const S = K.stack
  const isStack = (x, z) => x >= S.x0 && x <= S.x1 && z >= S.z0 && z <= S.z1
  // an even span (x 8..15): two stairs meet at the ridge (x 11, 12, y 8), the stack rising through them
  for (let z = B.roof.along[0]; z <= B.roof.along[1]; z++) for (let i = 0; i < 4; i++)
    for (const [x, rise] of [[B.x0 + i, 'east'], [B.x1 - i, 'west']])
      if (!isStack(x, z) && !c.get(x, B.roof.eaveY + i, z)) stair(c, x, B.roof.eaveY + i, z, P.roof.stairs, rise)
  const bu = { 8: 5, 9: 6, 10: 7, 11: 8, 12: 8, 13: 7, 14: 6, 15: 5 }
  for (const z of [B.z0, B.z1]) for (let x = B.x0 + 1; x < B.x1; x++) for (let y = B.plate + 1; y < bu[x]; y++) log(c, x, y, z, 'x')
  for (const z of B.roof.along) for (let x = B.x0; x <= B.x1; x++) {
    const y = bu[x] - 1
    if (y > B.plate && !c.get(x, y, z)) stair(c, x, y, z, P.roof.stairs, x <= 11 ? 'west' : 'east', 'top')
  }
  // the barn (round 2): the front bay open under the plate on a middle post, so the road sees the clay kiln; the east
  // side open from the front post to a post at z 7 for the stoker, firewood stacked lengthwise either side of the stoke
  // hole; behind the kiln a log partition shuts the fuel store; a plank ceiling at the plate over the kiln bay
  for (let x = B.x0 + 1; x < B.x1; x++) for (let y = 1; y < B.plate; y++) {
    log(c, x, y, B.z1, 'x')
    log(c, x, y, MASSES.partitionZ, 'x')
  }
  for (let y = 1; y < B.plate; y++) log(c, MASSES.bayPost, y, B.z0, 'y', P.pale)
  for (let z = MASSES.partitionZ; z < B.z1; z++) for (let y = 1; y < B.plate; y++)
    log(c, B.x1, y, z, z === MASSES.partitionZ ? 'y' : 'z', z === MASSES.partitionZ ? P.pale : P.log)
  for (const z of [K.z0, K.z1]) for (let y = 1; y <= 2; y++) log(c, B.x1, y, z, 'z') // firewood, bark out
  for (const z of [K.z0, K.z1]) slab(c, B.x1, 3, z, `${P.wood}_slab`, 'bottom')
  for (let x = B.x0 + 1; x < B.x1; x++) for (let z = K.z0; z < MASSES.partitionZ; z++) if (!c.get(x, B.plate, z)) c.set(x, B.plate, z, P.floor)
  for (let z = K.z0; z < MASSES.partitionZ; z++) for (let y = 1; y < B.plate; y++) c.set(B.x0, y, z, stone) // the kiln's back against the house
  for (let x = B.x0 + 1; x < B.x1; x++) log(c, x, B.plate, MASSES.partitionZ, 'x') // the partition's head
  c.set(13, 3, B.z0, 'lantern', { hanging: 'true', waterlogged: 'false' }) // in the open bay, under the plate
  door(c, MASSES.barnDoor, 'north', 'south', { wood: P.wood })

  // the kiln: a fieldstone tunnel under a clay vault, the stack through the barn's ridge
  for (let x = K.x0; x <= K.x1; x++) for (let z = K.z0; z <= K.z1; z++) {
    const corner = (x === K.x0 || x === K.x1) && (z === K.z0 || z === K.z1)
    if (!corner) { c.set(x, 1, z, footStone); c.set(x, 2, z, stone) }
  }
  for (let x = K.x0; x <= K.x1; x++) for (let z = K.z0; z <= K.z1; z++) { // the vault, y 3
    const endX = x === K.x0 || x === K.x1, edgeZ = z === K.z0 || z === K.z1
    if (endX && edgeZ) continue
    if (edgeZ) stair(c, x, 3, z, P.clayStairs, z === K.z0 ? 'south' : 'north')
    else if (endX) stair(c, x, 3, z, P.clayStairs, x === K.x0 ? 'east' : 'west')
    else c.set(x, 3, z, P.clay)
  }
  for (let x = K.x0 + 1; x < K.x1; x++) { // the crown, y 4
    c.set(x, 4, 4, P.clay)
    stair(c, x, 4, 3, P.clayStairs, 'south'); stair(c, x, 4, 5, P.clayStairs, 'north')
  }
  for (let y = 1; y <= 2; y++) for (let z = 3; z <= 5; z++) c.set(8, y, z, stone) // the throat to the loading mouth
  for (let x = S.x0; x <= S.x1; x++) for (let z = S.z0; z <= S.z1; z++) {
    for (let y = 1; y <= S.top; y++) c.set(x, y, z, stone)
    if (x === S.fire[0] && z === S.fire[1]) c.set(x, S.top + 1, z, 'campfire', { facing: 'south', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
    else slab(c, x, S.top + 1, z, P.stackCap, 'bottom')
  }
  const km = OPENINGS.kilnMouth, sh = OPENINGS.stokeHole
  c.set(...km.at, 'furnace', { facing: km.facing, lit: 'false' })
  c.set(...sh.at, 'furnace', { facing: sh.facing, lit: 'false' })

  // Pass 3: openings
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood })
  stair(c, ...fd.step, `${P.wood}_stairs`, 'south')
  for (const dx of [-1, 1]) for (let y = 1; y <= 2; y++) log(c, fd.at[0] + dx, y, fd.at[2], 'y', P.pale) // the door frame
  log(c, fd.at[0], 3, fd.at[2], 'x', P.pale)
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, pots: w.pots ?? [] })
  const st = OPENINGS.stairs
  stairwell(c, st.well)
  flight(c, st.flight, st.rises, P.wood, stone)
  railingRun(c, OPENINGS.rail, P.wood)

  // Pass 4: depth and detail
  c.set(...OPENINGS.doorLamp, 'lantern', { hanging: 'true', waterlogged: 'false' })

  // Pass 5: interior
  for (const f of FURNITURE[1]) furnitureParts[f.part](c, f)
  c.set(6, 2, 3, 'decorated_pot', { facing: 'west', cracked: 'false', waterlogged: 'false' }) // wares on the kept barrel

  // Pass 6: ground
  for (const b of OUTSIDE.flowerBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  c.set(...OUTSIDE.wares, 'barrel', { facing: 'up', open: 'false' })
  c.set(5, 2, 1, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })
  const F = MASSES.firewood
  for (let x = F.x0; x <= F.x1; x++) for (let y = 1; y <= F.high; y++) log(c, x, y, F.z, 'x')
  const sd = MASSES.store
  for (let z = sd.logs.z0; z <= sd.logs.z1; z++) for (let y = 1; y <= sd.logs.high; y++) log(c, sd.logs.x, y, z, 'z')
  for (const p of sd.clay) c.set(...p, 'clay')
  c.set(...sd.cauldron, 'cauldron')
  for (let x = 8; x <= 15; x++) if (!c.get(x, 0, 0)) c.set(x, 0, 0, 'coarse_dirt') // the barn's apron
}

// --- Level II: the journeyman's room and the drying rack ----------------------------------------------------------
function levelTwo(c) {
  const { x0, x1, z0, z1 } = W.walls
  const hb = OPENINGS.windows.find(w => w.level1Only)
  for (const [x, y, z] of hb.cells) c.set(x, y, z, stone)
  c.clear(hb.cells[0][0], hb.cells[0][1] - 1, hb.cells[0][2] + 1); c.clear(hb.cells[0][0], hb.cells[0][1], hb.cells[0][2] + 1)

  for (const x of [H.upper.x0, H.upper.x1]) c.clear(x, 7, H.upper.z1 + 1) // the house's back log ends would sit on the wing's eave
  plinth(c, x0 - 1, z0, x1 + 1, z1 + 1, footStone)
  floor(c, x0 + 1, z0, x1 - 1, z1 - 1, 0, P.floor)
  for (let z = z0; z <= z1; z++) for (const x of [x0, x1]) for (let y = 1; y <= 4; y++) {
    const corner = z === z1
    log(c, x, y, z, corner ? (y % 2 ? 'x' : 'z') : 'z')
  }
  for (let x = x0 + 1; x < x1; x++) for (let y = 1; y <= 4; y++) log(c, x, y, z1, 'x')
  for (const y of [2, 4]) { log(c, x0, y, z1 + 1, 'z'); log(c, x1, y, z1 + 1, 'z') } // the notches' log ends
  for (let x = x0; x <= x1; x++) log(c, x, 5, z1, 'x', P.pale)
  for (let z = z0; z < z1; z++) for (const x of [x0, x1]) log(c, x, 5, z, 'z', P.pale)
  const wing = gableRoof(c, W.roof, P.roof, (x, y, z) => !c.get(x, y, z))
  logGable(c, z1, W.roof.across, 6, wing.under)
  verge(c, W.roof.along[1], W.roof.across, wing.under, P.roof)

  const wd = OPENINGS.wingDoorway
  for (let y = wd.at[1]; y < wd.at[1] + wd.height; y++) c.clear(wd.at[0], y, wd.at[2])
  c.set(wd.at[0], 0, wd.at[2], P.floor)
  for (const w of OPENINGS.wingWindows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, pots: w.pots ?? [] })

  for (const f of FURNITURE[2]) furnitureParts[f.part](c, f.part === 'hangingLamp' ? { ...f, chain: 3 } : f) // hung from the lower ridge

  // the drying rack of fired pots: the shipped barrel and quench cauldron cells, on stripped posts under spruce boards
  const r = OUTSIDE.rack
  for (let x = r.x0; x <= r.x1; x++) {
    if (x === r.x0) log(c, x, 1, r.z, 'y', P.pale)
    else if (x === r.x1) c.set(x, 1, r.z, 'cauldron')
    else c.set(x, 1, r.z, 'barrel', { facing: 'up', open: 'false' })
    slab(c, x, 2, r.z, `${P.wood}_slab`, 'bottom')
  }
  c.set(r.x0, 3, r.z, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })
  c.set(r.x1, 3, r.z, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })
  for (let x = r.x0 - 1; x <= r.x1 + 1; x++) for (let z = r.z - 1; z <= r.z + 1; z++) if (!c.get(x, 0, z)) c.set(x, 0, z, 'coarse_dirt')
}

// --- Level III: the clay cellar -----------------------------------------------------------------------------------
function levelThree(c) {
  const Cl = MASSES.cellar
  const inside = cellar(c, Cl.x0, Cl.z0, Cl.x1, Cl.z1, Cl.depth, mix(P.cellarWall, 3), P.cellarFloor)
  const L = OPENINGS.hatch
  hatchLadder(c, L.x, L.z, -3, 0, L.facing, P.wood)
  for (let x = 2; x <= 6; x++) {
    c.set(x, -3, 3, 'chest', { facing: 'south', type: 'single', waterlogged: 'false' })
    c.set(x, -2, 3, 'barrel', { facing: 'south', open: 'false' })
  }
  for (let z = 6; z <= 10; z++) c.set(6, -3, z, 'barrel', { facing: 'west', open: 'false' })
  for (const z of [7, 9]) c.set(6, -2, z, 'barrel', { facing: 'west', open: 'false' })
  for (const [x, y, z] of [[2, -3, 11], [3, -3, 11], [2, -2, 11], [2, -3, 10]]) c.set(x, y, z, 'clay')
  c.set(4, -3, 8, `${P.wood}_fence`); c.set(4, -2, 8, 'brown_carpet')
  for (let x = 2; x <= 6; x++) if (!c.get(x, -1, 7)) log(c, x, -1, 7, 'x') // a spruce beam across the cellar
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
