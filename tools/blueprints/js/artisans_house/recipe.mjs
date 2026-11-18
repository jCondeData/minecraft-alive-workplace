// Artisan's House I-III (Milestone 49), the recipe: assembles plan.mjs from the shared parts library (../parts) in
// passes (block-out and structure, openings, depth and detail, interior, ground, light) and returns the structure for
// the minecraft-builder skill's runner. LEVEL=1..3 picks the level (default 1): each level draws the one below first and
// adds to it, so II keeps I's cells and III is II block for block above the ground. Also writes furniture.json (every
// furniture spot the parts recorded) and, for III, out/cellar.txt (the cellar's inside, for rebuild.py --cellar).
// Build: build.sh. CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, LiftedCanvas, mix, stair, slab, plinth, floor, post, beamRing, wallPanel, window, door, gableRoof, gableWall,
  bargeTrim, flight, stairwell, railingRun, quoins, flowerBed, woodpile, cellar, hatchLadder, furnitureParts, jetty,
  pentice, bottleKiln, dormer,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const stone = mix(P.stone, 11)
const plaster = mix(P.infill, 5)
const H = MASSES.house, K = MASSES.kiln, S = MASSES.shed, W = MASSES.wing

// --- Level I: the Kiln House ---------------------------------------------------------------------------------------
function levelOne(c) {
  const { x0, x1, z0, z1 } = H.walls
  const U = H.upper

  // Pass 1: block-out and structure
  plinth(c, x0 - 1, z0, x1 + 1, z1 + 1, stone) // the house's plinth and its skirt (sills stand on it)
  plinth(c, OUTSIDE.stoop.x0, OUTSIDE.stoop.z, OUTSIDE.stoop.x1, OUTSIDE.stoop.z, stone) // the stoop under the jetty
  floor(c, x0 + 1, z0 + 1, x1 - 1, 5, 0, P.workFloor) // workroom flags
  floor(c, x0 + 1, 6, x1 - 1, z1 - 1, 0, P.floor) // hall boards

  // ground floor: rubble walls with dressed quoins
  wallPanel(c, [x0, z0], [x1, z0], 1, H.groundTop, stone)
  wallPanel(c, [x0, z1], [x1, z1], 1, H.groundTop, stone)
  wallPanel(c, [x0, z0], [x0, z1], 1, H.groundTop, stone)
  wallPanel(c, [x1, z0], [x1, z1], 1, H.groundTop, stone)
  quoins(c, [[x0, z0], [x1, z0], [x0, z1], [x1, z1]], 1, H.groundTop, P.quoin, P.quoinAlt)

  // upper floor: the sill beam ring (jettied a block out over the street), the floor, posts, panels, the top beam
  beamRing(c, U.x0, U.z0, U.x1, U.z1, H.sillY, P.frame)
  floor(c, U.x0 + 1, U.z0 + 1, U.x1 - 1, U.z1 - 1, H.sillY, P.floor)
  wallPanel(c, [U.x0, U.z0], [U.x1, U.z0], H.sillY + 1, H.upperTop, plaster)
  wallPanel(c, [U.x0, U.z1], [U.x1, U.z1], H.sillY + 1, H.upperTop, plaster)
  wallPanel(c, [U.x0, U.z0], [U.x0, U.z1], H.sillY + 1, H.upperTop, plaster)
  wallPanel(c, [U.x1, U.z0], [U.x1, U.z1], H.sillY + 1, H.upperTop, plaster)
  for (const x of H.frontPosts) post(c, x, U.z0, H.sillY + 1, H.upperTop, P.frame)
  for (const x of H.backPosts) post(c, x, U.z1, H.sillY + 1, H.upperTop, P.frame)
  for (const z of H.sidePosts) { post(c, U.x0, z, H.sillY + 1, H.upperTop, P.frame); post(c, U.x1, z, H.sillY + 1, H.upperTop, P.frame) }
  beamRing(c, U.x0, U.z0, U.x1, U.z1, H.beamY, P.frame)
  for (const z of H.ties) for (let x = U.x0 + 1; x < U.x1; x++) c.set(x, H.beamY, z, P.frame, { axis: 'x' }) // tie beams
  // the jetty: carved brackets under the sill beam at each front post, a moulding a block out (the sills sit on it)
  jetty(c, { x0: U.x0, x1: U.x1, z: U.z0, y: H.sillY, out: 'north', at: H.frontPosts }, { trim: P.trim })

  // the roof: one steep tiled gable, ridge along z, eaves level with the top beam
  const main = gableRoof(c, H.roof, P.roof)
  const studs = (u, y) => (u === 3 || u === 5)
  for (const line of [U.z0, U.z1]) {
    gableWall(c, 'z', line, H.roof.across, H.beamY + 1, main.under, (u, y) => {
      if (studs(u, y) && y <= 11) return [P.frame, { axis: 'y' }]
      if (u === 4 && y >= 14) return [P.frame, { axis: 'y' }] // king post
      if (u === 4 && y === 10) return [P.frame, { axis: 'x' }] // collar under the gable window
      return plaster
    })
  }
  // a pentice board over the street windows, running on from the eaves under the front gable
  pentice(c, Array.from({ length: U.x1 - U.x0 + 1 }, (_, i) => [U.x0 + i, U.z0 - 1]), H.beamY, P.roof, 'south')
  bargeTrim(c, 'z', H.roof.along[0], H.roof.across, main.under, `${P.trim}_slab`)
  bargeTrim(c, 'z', H.roof.along[1], H.roof.across, main.under, `${P.trim}_slab`)
  // the upper room's ceiling: pale boards lining the steep tiles, the ridge left open above the tie beams
  for (let z = U.z0 + 1; z < U.z1; z++)
    for (const [x, y] of [[2, 11], [3, 12], [3, 13], [4, 14], [4, 15], [5, 12], [5, 13], [6, 11]]) {
      if (x === 2 && Math.abs(z - H.dormerZ) <= 1) continue // the dormer's bay stays open to its window
      c.set(x, y, z, P.lining)
    }
  // a gabled dormer on the long west slope, lighting the stair head between the bedroom and the sitting room
  dormer(c, H.dormerZ, P.roof, plaster, P.glass, P.frame, { face: U.x0, eave: U.x0 - 1, y0: H.beamY + 1 })

  // the kiln: a brick bottle on the east side, its neck the house's chimney; its fire mouth opens into the workroom
  bottleKiln(c, K.cx, K.cz, { stages: K.stages, bands: K.bands }, P.kiln, (x, y, z) => !c.get(x, y, z) || y < H.beamY)
  // the flue's breast: where the kiln's back corner and neck meet the house wall under the eave, bonded in brick
  for (let z = K.cz - 1; z <= K.cz + 1; z++) for (let y = K.stages[0].top + 1; y <= H.upperTop; y++) c.set(H.walls.x1 + 1, y, z, P.kiln.body)
  for (let y = 1; y <= H.upperTop; y++) c.set(H.walls.x1 + 1, y, K.cz + 2, P.kiln.body)
  const km = OPENINGS.kilnMouth, sh = OPENINGS.stokeHole
  c.set(...km.at, 'furnace', { facing: km.facing, lit: 'false' })
  c.set(...sh.at, 'furnace', { facing: sh.facing, lit: 'false' })
  stair(c, sh.at[0] + 1, sh.at[1] + 1, sh.at[2], P.kiln.stairs, 'west', 'top') // a brick hood over the stoke hole

  // the fuel shed: a lean-to off the house's east wall on two posts, leaning on the kiln's back
  for (let x = S.x0; x <= S.x1; x++) for (let z = S.z0; z <= S.z1 - 1; z++) c.set(x, 0, z, stone)
  for (const [x, z] of S.posts) post(c, x, z, 1, 3, P.frame)
  for (let z = S.z0 - 1; z <= S.z1; z++) { // the roof runs on to the kiln's back
    if (!c.get(S.x0, 4, z)) c.set(S.x0, 4, z, P.roof.full) // the lean-to's head on the wall
    for (const [x, y] of [[8, 5], [9, 4], [10, 3]]) if (!c.get(x, y, z)) stair(c, x, y, z, P.roof.stairs, 'west')
  }

  // Pass 2: openings
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood })
  stair(c, ...fd.step, `${P.wood}_stairs`, 'south')
  c.set(fd.at[0], fd.at[1] + 2, fd.at[2], P.frame, { axis: 'x' }) // the door's lintel
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, pots: w.pots ?? [] })
  const st = OPENINGS.stairs
  stairwell(c, st.well)
  flight(c, st.flight, st.rises, P.wood, stone)
  railingRun(c, OPENINGS.rail, P.wood)

  // Pass 3: depth and detail
  c.set(...OPENINGS.doorLamp, 'lantern', { hanging: 'true', waterlogged: 'false' })

  // Pass 4: interior
  for (const f of FURNITURE[1]) furnitureParts[f.part](c, f)
  c.set(6, 2, 3, 'decorated_pot', { facing: 'west', cracked: 'false', waterlogged: 'false' }) // wares on the kept barrel

  // Pass 5: ground
  for (const b of OUTSIDE.flowerBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  c.set(...OUTSIDE.wares, 'barrel', { facing: 'up', open: 'false' })
  for (const p of OUTSIDE.woodpiles) for (const [z, x0] of p.rows) for (let x = x0; x <= 15; x++) {
    for (let y = 0; y <= 1; y++) c.set(x, y, z, 'spruce_log', { axis: 'x' }) // end grain out
    slab(c, x, 2, z, `${P.wood}_slab`, 'bottom') // the lid
  }
  const sd = OUTSIDE.shed
  for (let z = sd.logs.z0; z <= sd.logs.z1; z++) for (let y = 1; y <= sd.logs.high; y++) c.set(sd.logs.x, y, z, 'spruce_log', { axis: 'x' })
  for (const p of sd.clay) c.set(...p, 'clay')
  c.set(...sd.cauldron, 'cauldron')
}

// --- Level II: the journeyman's room and the drying yard -------------------------------------------------------------
function levelTwo(c) {
  const { x0, x1, z0, z1 } = W.walls
  const hb = OPENINGS.windows.find(w => w.level1Only)
  for (const [x, y, z] of hb.cells) c.set(x, y, z, stone) // the hall's back window walled up for the doorway
  c.clear(hb.cells[0][0], hb.cells[0][1] - 1, hb.cells[0][2] + 1); c.clear(hb.cells[0][0], hb.cells[0][1], hb.cells[0][2] + 1) // its sill and pot

  // Pass 1: the wing on its plinth, framed, a steep roof under the house's back gable
  plinth(c, x0 - 1, z0, x1 + 1, z1 + 1, stone)
  floor(c, x0 + 1, z0, x1 - 1, z1 - 1, 0, P.floor)
  wallPanel(c, [x0, z0], [x0, z1], 1, 4, plaster)
  wallPanel(c, [x1, z0], [x1, z1], 1, 4, plaster)
  wallPanel(c, [x0, z1], [x1, z1], 1, 4, plaster)
  for (const [x, z] of W.posts) post(c, x, z, 1, 4, P.frame)
  beamRing(c, x0, z0, x1, z1, 5, P.frame)
  const wing = gableRoof(c, W.roof, P.roof, (x, y, z) => !c.get(x, y, z))
  gableWall(c, 'z', z1, W.roof.across, 6, wing.under, (u, y) => {
    if (u === 3 || u === 5) return [P.frame, { axis: 'y' }]
    if (u === 4 && y >= 8) return [P.frame, { axis: 'y' }]
    return plaster
  })
  bargeTrim(c, 'z', W.roof.along[1], W.roof.across, wing.under, `${P.trim}_slab`)

  // Pass 2: openings
  const wd = OPENINGS.wingDoorway
  for (let y = wd.at[1]; y < wd.at[1] + wd.height; y++) c.clear(wd.at[0], y, wd.at[2])
  c.set(wd.at[0], 0, wd.at[2], P.floor) // the doorway's threshold boards
  for (const w of OPENINGS.wingWindows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, pots: w.pots ?? [] })

  // Pass 4: interior
  for (const f of FURNITURE[2]) furnitureParts[f.part](c, f)

  // Pass 5: ground: the drying yard behind the shed, a rack of fired pots on spruce boards
  const r = OUTSIDE.rack
  for (let x = r.x0; x <= r.x1; x++) {
    if (x === r.x0 || x === r.x1) c.set(x, 1, r.z, `${P.wood}_fence`)
    else c.set(x, 1, r.z, 'barrel', { facing: 'up', open: 'false' })
    slab(c, x, 2, r.z, `${P.wood}_slab`, 'bottom')
  }
  c.set(r.x0, 3, r.z, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })
  c.set(r.x1, 3, r.z, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })
  for (let x = r.x0 - 1; x <= r.x1 + 1; x++) for (let z = r.z - 1; z <= r.z + 1; z++) if (!c.get(x, 0, z)) c.set(x, 0, z, 'gravel')
  c.set(12, 1, 15, 'cauldron') // the quench tub
}

// --- Level III: the clay cellar --------------------------------------------------------------------------------------
function levelThree(c) {
  const Cl = MASSES.cellar
  const inside = cellar(c, Cl.x0, Cl.z0, Cl.x1, Cl.z1, Cl.depth, stone, P.cellarFloor)
  const L = OPENINGS.hatch
  hatchLadder(c, L.x, L.z, -3, 0, L.facing, P.wood)
  // the family's chests along the front wall (the kept cells), casks of slip on their sides along the east wall
  for (let x = 2; x <= 6; x++) {
    c.set(x, -3, 3, 'chest', { facing: 'south', type: 'single', waterlogged: 'false' })
    c.set(x, -2, 3, 'barrel', { facing: 'south', open: 'false' })
  }
  for (let z = 6; z <= 10; z++) c.set(6, -3, z, 'barrel', { facing: 'west', open: 'false' })
  for (const z of [7, 9]) c.set(6, -2, z, 'barrel', { facing: 'west', open: 'false' })
  // clay stock in the back corner, a sorting table
  for (const [x, y, z] of [[2, -3, 11], [3, -3, 11], [2, -2, 11], [2, -3, 10]]) c.set(x, y, z, 'clay')
  c.set(4, -3, 8, `${P.wood}_fence`); c.set(4, -2, 8, 'brown_carpet')
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
