// The Barracks, the recipe: assembles plan.mjs (the Gate Ward) from the shared parts library (../parts) in passes
// (block-out and structure, openings, depth and detail, interior, ground and light) for LEVEL = 1, 2 or 3:
//   I    the West range and the Gate tower, the walled Court behind the tower;
//   II   + the East range: the West range's own lines drawn again through mirrorX (x' = 20 - x), closing the Court;
//   III  + the armoury and lock-up 4 deep under the West range, down a ladder under a hatch (II above the ground, block
//        for block, but that hatch).
// Writes furniture.json (every furniture spot) and, for III, out/cellar.txt (the cellar's inside for rebuild.py
// --cellar). Build every level: build.sh. CUT=<y> builds a cutaway with everything above y removed; ABOVE=1 drops the
// cellar (renders only).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, BASEMENT, MIRROR, PALETTE, MASSES, OPENINGS, FURNITURE, COURT } from './plan.mjs'
import {
  Canvas, LiftedCanvas, mix, stair, plinth, floor, beamRing, wallPanel, window, door, gableRoof, gableWall,
  hood, chimney, quoins, cellar, hatchLadder, furnitureParts, doubleChest, leverDoor,
} from '../parts/index.mjs'
import { mirrorX, gateArch, wallButtress, footlocker, weaponRack } from '../parts/barracks_ward.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const plinthStone = mix(P.plinth, 3)
const upper = mix(P.wall, 5)
const lower = mix(P.wallLow, 9)
const wall = (x, y, z) => (y <= 1 ? lower(x, y, z) : upper(x, y, z)) // weathered only in the bottom course
const paving = mix(P.paving, 4)
const earth = mix(P.court, 6)
const cellarFloor = mix(P.cellarFloor, 8)
const plaster = mix(P.infill, 7)

const parts = {
  ...furnitureParts,
  grindstone(c, f) { c.free(f.pos); c.set(...f.pos, 'grindstone', { face: 'floor', facing: f.facing }) },
  footlocker(c, f) { footlocker(c, f.pos, f.facing) },
}

// --- The Gate tower (drawn first: the ranges' roofs die into its walls) ------------------------------------------------
function tower(c) {
  const T = MASSES.tower
  const { x0, x1, z0, z1 } = T
  const ring = (x, z) => x === x0 || x === x1 || z === z0 || z === z1
  // Pass 1: plinth, shaft, floors
  plinth(c, x0, z0, x1, z1, plinthStone)
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (!ring(x, z)) continue
    for (let y = 1; y <= 7; y++) c.set(x, y, z, wall)
    c.set(x, 4, z, P.quoin) // string courses: over the gate, under the lookout
    c.set(x, 8, z, P.quoin)
  }
  quoins(c, [[x0, z0], [x1, z0], [x0, z1], [x1, z1]], 1, 7, P.quoin, P.quoinAlt)
  for (const y of T.floors) floor(c, x0 + 1, z0 + 1, x1 - 1, z1 - 1, y, P.floor)
  // the gate passage: paved through, arched front and back
  for (let x = x0 + 1; x <= x1 - 1; x++) for (let z = z0; z <= z1; z++) c.set(x, 0, z, paving)
  // the street face round-headed (haunches in its upper corners); the court face left square, so the passage stays a
  // passage open at both ends and never a room the street reaches into
  gateArch(c, T.gate.cx, T.gate.half, 1, T.gate.top, z0, P.stoneStairs)
  gateArch(c, T.gate.cx, T.gate.half, 1, T.gate.top, z1, P.stoneStairs)
  for (const x of [T.gate.cx - T.gate.half, T.gate.cx + T.gate.half]) c.clear(x, T.gate.top, z1)
  // Pass 2: the ladder up the passage's west wall through both floors, the watch room's windows
  const L = OPENINGS.ladder
  for (let y = L.y0; y <= L.y1; y++) c.set(L.x, y, L.z, 'ladder', { facing: L.facing, waterlogged: 'false' })
  // a hatch in each floor over the ladder (shut, it closes the watch room off from the open passage)
  for (const y of T.floors) c.set(L.x, y, L.z, `${P.wood}_trapdoor`, { facing: L.facing, half: 'top', open: 'false', powered: 'false', waterlogged: 'false' })
  for (const w of OPENINGS.towerWindows) window(c, w.cells, w.out, w.bare ? null : 'sill', { glass: P.glass, sill: P.stoneStairs })
  // Pass 3: the lookout, a lantern of posts and glass, and its slate cap
  const [ly0, ly1] = T.lookout
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (!ring(x, z)) continue
    const corner = (x === x0 || x === x1) && (z === z0 || z === z1)
    const mid = x === T.gate.cx || z === (z0 + z1) / 2
    for (let y = ly0; y <= ly1; y++) {
      if (corner || mid) c.set(x, y, z, P.frame, { axis: 'y' })
      else c.set(x, y, z, P.glass)
    }
  }
  floor(c, x0 + 1, z0 + 1, x1 - 1, z1 - 1, T.cap, P.floor) // the lookout's ceiling
  pyramidCap(c, x0, z0, x1, z1, T.cap)
  // heraldry: the guard's banners either side of the watch window
  for (const x of [T.gate.cx - 1, T.gate.cx + 1]) c.set(x, 6, z0 - 1, `${P.cloth}_wall_banner`, { facing: 'north' })
  // light in the passage (hung from the watch room's floor)
  c.set(T.gate.cx, 3, (z0 + z1) / 2, 'lantern', { hanging: 'true', waterlogged: 'false' })
}

/** the tower's cap: rings of slate stairs rising inward, a tile block at the top */
function pyramidCap(c, x0, z0, x1, z1, y) {
  while (x0 < x1) {
    for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
      if (x !== x0 && x !== x1 && z !== z0 && z !== z1) continue
      const facing = z === z0 ? 'south' : z === z1 ? 'north' : x === x0 ? 'east' : 'west'
      stair(c, x, y, z, P.roof.stairs, facing)
    }
    x0++; z0++; x1--; z1--; y++
  }
  c.set(x0, y - 1, z0, P.roof.full)
}

// --- The Court ------------------------------------------------------------------------------------------------------
function court(c, level) {
  const K = MASSES.court
  const x1 = K.x1[level >= 2 ? 2 : 1]
  floor(c, K.x0, K.z0, x1, K.z1, 0, earth)
  const walls = [] // [x, z] in order along the wall
  if (level === 1) for (let z = K.z0; z <= K.z1; z++) walls.push([K.wallX1, z])
  for (let x = K.wallX1; x >= K.x0; x--) walls.push([x, K.wallZ])
  for (const [x, z] of walls) {
    c.set(x, 0, z, plinthStone)
    for (let y = 1; y <= K.wallTop; y++) c.set(x, y, z, wall)
    if ((x + z) % 2 === 0 || (level === 1 && x === K.wallX1 && z === K.wallZ)) c.set(x, K.wallTop + 1, z, wall) // merlons
  }
  // the job piece and its dressing
  c.set(...COURT.dummy, 'aliveworkplace:training_dummy', { facing: 'north' })
  c.set(...COURT.butt.hay, 'hay_bale', { axis: 'y' })
  c.set(...COURT.butt.target, 'target', { power: '0' })
  weaponRack(c, COURT.rack, `${P.wood}_fence`, 'lightning_rod')
  if (level >= 2) weaponRack(mirrorX(c, MIRROR), COURT.rack, `${P.wood}_fence`, 'lightning_rod')
  // light: a lantern on the back wall's middle merlon, lanterns hung under the ranges' eaves over the court
  c.set(...COURT.lamps[0], 'lantern', { hanging: 'false', waterlogged: 'false' })
  if (level === 1) c.set(...COURT.lamps[1], 'lantern', { hanging: 'false', waterlogged: 'false' })
}

// --- One barrack range (the West range; the East range is this through mirrorX) ---------------------------------------
function range(c) {
  const R = MASSES.range
  const { x0, x1, z0, z1 } = R
  // Pass 1: block-out and structure
  plinth(c, x0, z0, x1, z1, plinthStone)
  floor(c, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, P.floor)
  for (const z of [z0, z1]) wallPanel(c, [x0, z], [x1, z], 1, 3, wall)
  for (const x of [x0, x1]) wallPanel(c, [x, z0], [x, z1], 1, 3, wall)
  quoins(c, [[x0, z0], [x1, z0], [x0, z1], [x1, z1]], 1, 3, P.quoin, P.quoinAlt)
  beamRing(c, x0, z0, x1, z1, R.beamY, P.beam)
  for (const z of R.ties) for (let x = x0 + 1; x < x1; x++) c.set(x, R.beamY, z, P.frame, { axis: 'x' })
  const H = OPENINGS.hearth
  const tower = MASSES.tower
  const keep = (x, y, z) => !c.get(x, y, z) && !(x === tower.x0 && z <= tower.z1) && !(x === H.chimney[0] && z === H.chimney[1] && y <= H.top)
  const roof = gableRoof(c, R.roof, P.roof, keep)
  const studs = (u, y) => {
    if (u === x0 || u === x1 || u === 2 || u === 6) return [P.frame, { axis: 'y' }]
    if (u === 4 && y === 7) return [P.frame, { axis: 'y' }] // the king post under the ridge
    return plaster
  }
  for (const z of [z0, z1]) gableWall(c, 'z', z, R.roof.across, R.beamY + 1, roof.under, studs)
  // no barge boards: under a 45-degree rake their stairs read as teeth; the overhang's own stairs and the plaster a block
  // back carry the edge

  // Pass 2: openings
  const fd = OPENINGS.frontDoor
  door(c, fd.at, 'south', 'north', { wood: P.wood, step: fd.step, stepStair: P.stoneStairs })
  door(c, OPENINGS.courtDoor.at, 'west', 'east', { wood: P.wood })
  for (const w of OPENINGS.windows)
    window(c, w.cells, w.out, w.bare ? null : 'sill', { glass: P.glass, sill: P.stoneStairs })

  // Pass 3: depth and detail
  hood(c, fd.at[0], R.beamY, fd.at[2] - 1, P.roof)
  for (const [x, z] of OPENINGS.buttresses) wallButtress(c, x, z, 0, 2, wall, P.stoneStairs, 'east')
  // the hearth in the west wall, its stack up the outside past the eave
  chimney(c, H.chimney[0], H.chimney[1], 0, H.top, wall, { breast: { axis: 'z', top: 2, stair: P.stoneStairs } })
  c.set(H.chimney[0], H.top, H.chimney[1], P.quoin) // a dressed cap under the smoke
  // a lantern under the eave over the court door
  c.set(x1 + 1, R.beamY - 1, 7, 'lantern', { hanging: 'true', waterlogged: 'false' })
}

// --- Level III: the armoury and lock-up -------------------------------------------------------------------------------
function cellarLevel(c) {
  const C = MASSES.cellar
  const inside = cellar(c, C.x0, C.z0, C.x1, C.z1, C.depth, wall, cellarFloor)
  const h = OPENINGS.hatch
  hatchLadder(c, h.x, h.z, -3, 0, h.facing, P.wood)
  // Armoury: the arms chests (double) along the west wall, spear racks on the east wall
  doubleChest(c, [2, -3, 2], 'east', 'south')
  doubleChest(c, [2, -3, 5], 'east', 'south')
  weaponRack(c, [[6, -3, 2], [6, -3, 3]], `${P.wood}_fence`, 'lightning_rod')
  // Lock-up: iron bars across the room, an iron door worked by a lever on the armoury side, straw inside
  leverDoor(c, [4, -3, 7], { wall: 'x', jambSide: 1, out: 'north' }, { door: 'iron_door', jamb: P.beam })
  c.set(4, -1, 7, wall)
  for (const x of [2, 3, 6]) for (let y = -3; y <= -1; y++) c.set(x, y, 7, 'iron_bars')
  for (const [x, z] of [[2, 10], [3, 10], [2, 9]]) c.set(x, -3, z, 'hay_bale', { axis: 'x' })
  for (let y = -2; y <= -1; y++) c.set(6, y, 10, 'chain', { axis: 'y', waterlogged: 'false' })
  return inside
}

export function draw(level = 1) {
  const c = level === 3 ? new LiftedCanvas(SIZE[3], BASEMENT) : new Canvas(SIZE[level])
  tower(c)
  court(c, level)
  range(c)
  if (level >= 2) range(mirrorX(c, MIRROR))
  // Pass 4: interior (the plan's furniture; the East range's pieces are the West range's, mirrored)
  for (const f of FURNITURE) {
    if ((f.level ?? 1) > level) continue
    parts[f.part](c, f)
    if (f.mirror && level >= 2) parts[f.part](mirrorX(c, MIRROR), f.cellsAbs ? { ...f } : f)
  }
  if (level >= 3) c.cellarInside = cellarLevel(c)
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const id = level === 1 ? 'barracks' : `barracks_${level}`
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  fs.mkdirSync(path.join(here, 'out'), { recursive: true })
  if (c.cellarInside) fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), c.cellarInside.join(','))
  const cut = process.env.CUT
  let canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  if (process.env.ABOVE && level === 3) { // renders only: the build above the ground
    const a = new Canvas(SIZE[3])
    for (const [k, v] of canvas.cells) { const [x, y, z] = k.split(',').map(Number); if (y >= BASEMENT) a.cells.set(`${x},${y - BASEMENT},${z}`, v) }
    canvas = a
  }
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
