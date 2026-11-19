// Noble houses (parts library, tools/blueprints/REBUILD.md): the pieces the Noble's Manor's biome looks (49.10f) share,
// grown from the desert and savanna looks. Every block comes from the caller's palette.
//   unionHip     one hipped roof over a union of rectangles (an L, a T or an H plan): eaves on every side, hips at the
//                outer corners, valleys at the inner ones, the ridges at one height where the masses are as wide
//   manorRooms   the shipped Manor's rooms drawn into any look: floors, partitions, the arch, the grand stair and its
//                railing, the doors, the hearth fires, the ballroom's floor and musicians' gallery, the cellar and its
//                hatch, and every furniture piece of the plan, cell for cell (a look is the same building)
//   merlons      a parapet's top: merlons and crenels in turn along a wall ring (a desert house's crenellation)
//   latticeBay   a lattice screen out from a wall on any side: corner posts, open trapdoors on its face, a sill on
//                corbels and a little roof (a mashrabiya), the window behind it the caller's
import { DIRS, OPP, stair, slab, trapdoor } from './canvas.mjs'
import { flight, hatchLadder } from './stairs.mjs'
import { railingRun } from './railings.mjs'
import { door, wallPanel } from './walls.mjs'
import { hearthFire } from './hearths.mjs'
import { furnitureParts } from './furniture.mjs'
import { cellar } from './frames.mjs'

/** unionHip: a 45-degree hipped roof over the union of `rects` [{x0, z0, x1, z1}] (their outermost cells the eave at
 *  eaveY): each cell one course above the eave for every ring it lies inside the footprint (the largest square round it
 *  that stays inside), a `roof.stairs` facing up the slope (away from the lower neighbour; in a valley toward the
 *  higher one), `roof.full` on the ridges and peaks. `keep(x, y, z)` false leaves a cell to the caller (a tower that
 *  runs up through the roof). Returns under(x, z): the roof's y over a column (null off the footprint). */
export function unionHip(c, rects, eaveY, roof, keep = () => true, cap = 99) {
  const inF = (x, z) => rects.some(r => x >= r.x0 && x <= r.x1 && z >= r.z0 && z <= r.z1)
  const D = new Map()
  const k = (x, z) => `${x},${z}`
  for (const r of rects) for (let x = r.x0; x <= r.x1; x++) for (let z = r.z0; z <= r.z1; z++) {
    if (D.has(k(x, z))) continue
    let d = 0
    for (;;) {
      const n = d + 1
      let ok = true
      for (let dx = -n; dx <= n && ok; dx++) for (let dz = -n; dz <= n && ok; dz++) if (!inF(x + dx, z + dz)) ok = false
      if (!ok || d >= cap) break
      d = n
    }
    D.set(k(x, z), d)
  }
  const dOf = (x, z) => D.get(k(x, z))
  for (const [key, d] of D) {
    const [x, z] = key.split(',').map(Number)
    const y = eaveY + d
    if (!keep(x, y, z)) continue
    const nb = ['west', 'east', 'north', 'south'].map(f => [f, DIRS[f]]).map(([f, [dx, dz]]) => [f, dOf(x + dx, z + dz)]) // ties to the x faces, as hipRoof
    const lower = nb.find(([, e]) => e === undefined || e < d)
    const higher = nb.find(([, e]) => e !== undefined && e > d)
    if (lower) stair(c, x, y, z, roof.stairs, OPP[lower[0]])
    else if (higher) stair(c, x, y, z, roof.stairs, higher[0])
    else c.set(x, y, z, roof.full)
  }
  return (x, z) => (D.has(k(x, z)) ? eaveY + dOf(x, z) : null)
}

/** merlons: the top of a parapet at y round x0..x1, z0..z1 (the ring only): `merlon` and `crenel` (a slab) in turn,
 *  the corners always merlons; `skip(x, z)` leaves a cell (a tower standing there). */
export function merlons(c, { x0, z0, x1, z1, y }, merlon, crenel, skip = () => false) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (x !== x0 && x !== x1 && z !== z0 && z !== z1) continue
    if (skip(x, z)) continue
    const corner = (x === x0 || x === x1) && (z === z0 || z === z1)
    const along = z === z0 || z === z1 ? x - x0 : z - z0
    if (corner || along % 2 === 0) c.set(x, y, z, merlon)
    else slab(c, x, y, z, crenel, 'bottom')
  }
}

/** latticeBay: a lattice screen one block out from a wall on side `out` ('north', 'south', 'west' or 'east'), its
 *  face the cells a0..a1 along the wall (x for a north or south wall, z for a west or east one) on the line `line` (the
 *  outside cell), from y0 (a sill of `sill`, a top slab, on `corbel` stairs under its ends) to y1 (a roof of `roof`
 *  stairs, their backs to the wall): `post` logs at both ends, open `lattice` trapdoors flat on its outer face between. */
export function latticeBay(c, { out, line, a0, a1, y0, y1, corbels = true }, { post, lattice, sill, roof, corbel }) {
  const at = (a, y) => (out === 'north' || out === 'south' ? [a, y, line] : [line, y, a])
  const wall = OPP[out]
  for (let a = a0; a <= a1; a++) {
    slab(c, ...at(a, y0), sill, 'top')
    for (let y = y0 + 1; y < y1; y++) {
      if (a === a0 || a === a1) c.set(...at(a, y), post, { axis: 'y' })
      else trapdoor(c, ...at(a, y), lattice, wall, 'bottom', true)
    }
    stair(c, ...at(a, y1), roof, wall)
  }
  if (corbels && corbel) for (const a of [a0, a1]) if (!c.get(...at(a, y0 - 1))) stair(c, ...at(a, y0 - 1), corbel, wall, 'top')
}

// --- The Manor's rooms --------------------------------------------------------------------------------------------
// M: the shipped plan ({ MASSES, OPENINGS, FURNITURE }); P: the look's interior palette ({ floor, hallFloor: [a, b],
// ballFloor: [a, b], upperFloor, partition, wood, doorWood, frontDoorWood, stairUnder, beam, cellarWall, cellarFloor,
// bracket }). Draw the look's walls first: this cuts the doors into them and furnishes the rooms.

/** manorFloors: the ground and upper floors and the partitions of the rooms a level has (before the furniture). */
export function manorFloors(c, level, M, P) {
  const W = M.MASSES.westWing.walls, H = M.MASSES.hall.room, T = M.MASSES.tower.room
  const chequer = ([a, b]) => (x, y, z) => ((x + z) % 2 ? a : b)
  // ground floors: the wing's rooms, the hall's chequer, the lobby
  c.fill(W.x0 + 1, 0, W.z0 + 1, W.x1 - 1, 0, W.z1 - 1, P.floor)
  c.fill(H.x0, 0, H.z0, H.x1, 0, H.z1, chequer(P.hallFloor))
  c.fill(T.x0, 0, T.z0, T.x1, 0, T.z1, chequer(P.hallFloor))
  for (let x = M.OPENINGS.arch.x0; x <= M.OPENINGS.arch.x1; x++) c.set(x, 0, M.OPENINGS.arch.z, chequer(P.hallFloor))
  // the upper floors (y 5): the chambers, the Long Gallery, the study
  const U = M.MASSES.westWing.walls, up = 5
  c.fill(U.x0 + 1, up, U.z0 + 1, U.x1 - 1, up, U.z1 - 1, P.upperFloor)
  c.fill(H.x0, up, H.z0, H.x1, up, H.z1, P.upperFloor)
  c.fill(T.x0, up, T.z0, T.x1, up, T.z1, P.upperFloor)
  // partitions: the parlour from the kitchen (z 8) and the front chamber from the great chamber (z 7)
  const pw = M.MASSES.westWing
  wallPanel(c, [W.x0 + 1, pw.partitionGround], [W.x1 - 1, pw.partitionGround], 1, 4, P.partition)
  wallPanel(c, [W.x0 + 1, pw.partitionUpper], [W.x1 - 1, pw.partitionUpper], 6, 9, P.partition)
  // the arch from the porch into the hall, its beam over it
  const A = M.OPENINGS.arch
  for (let x = A.x0; x <= A.x1; x++) {
    for (let y = A.y0; y <= A.y1; y++) c.clear(x, y, A.z)
    c.set(x, A.y1 + 1, A.z, P.beam, { axis: 'x' })
  }
  if (level >= 3) {
    const B = M.MASSES.ballroom.room, G = M.MASSES.ballroom.gallery
    c.fill(B.x0, 0, B.z0, B.x1, 0, B.z1, chequer(P.ballFloor))
    c.fill(G.x0, G.y, G.z0, G.x1, G.y, G.z1, P.upperFloor)
  }
}

/** manorFittings: the grand stair, its well and railing, the doors, the hearths, the ballroom gallery's railing and (IV)
 *  the cellar with its hatch; then every furniture piece of every level up to `level`, as the plan lists it. `fires`:
 *  [[x, y, z, facing]] hearth fires. `chains`: {'x,y,z': n} a lamp's chain length where this look's ceiling differs.
 *  Returns the cellar's dug-out box at IV (rebuild.py --cellar), else null. */
export function manorFittings(c, level, M, P, { fires = [], chains = {}, cellarVault = null } = {}) {
  // the wall lamps (BRACKET_LAMPS): a lantern hung under a block out of the wall, facing into the room
  for (const [l, list] of Object.entries(M.BRACKET_LAMPS ?? {})) if (Number(l) <= level) for (const [x, y, z, side] of list) {
    c.set(x, y + 1, z, P.bracket)
    c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
    c.spot('lamp', [x, y, z], OPP[side], [[0, 0, 0]], P.wood)
  }
  const O = M.OPENINGS
  const st = O.grandStair
  for (const [x, y, z] of st.well) c.clear(x, y, z)
  flight(c, st.flight, st.rises, P.wood, P.stairUnder)
  railingRun(c, O.railing, P.wood)
  const d = (o, out, wood = P.doorWood, extra = {}) => door(c, o.at, o.facing, out, { wood, ...extra })
  d(O.frontDoor, 'north', P.frontDoorWood ?? P.doorWood, { step: O.frontDoor.step, stepStair: P.step })
  for (const o of [O.parlourDoor, O.kitchenDoor, O.frontChamberDoor, O.greatChamberDoor]) d(o, 'west')
  d(O.gardenDoor, 'south', P.doorWood, { step: [O.gardenDoor.at[0], 0, O.gardenDoor.at[2] + 1], stepStair: P.step })
  d(O.studyDoor, 'south')
  for (const [x, y, z, f] of fires) hearthFire(c, [x, y, z], f)
  if (level >= 3) {
    for (const o of O.ballroomDoors) door(c, o.at, o.facing, 'west', { wood: P.doorWood, hinge: o.hinge })
    d(O.galleryDoor, 'west')
    d(O.ballroomGardenDoor, 'south', P.doorWood, { step: [O.ballroomGardenDoor.at[0], 0, O.ballroomGardenDoor.at[2] + 1], stepStair: P.step })
    const G = M.MASSES.ballroom.gallery
    railingRun(c, [[G.x0, G.y + 1, G.z0], [G.x1, G.y + 1, G.z0]], P.wood)
  }
  let dug = null
  if (level >= 4) {
    const C = M.MASSES.cellar, h = O.hatch
    dug = cellar(c, C.x0, C.z0, C.x1, C.z1, C.depth, P.cellarWall, P.cellarFloor)
    hatchLadder(c, h.x, h.z, -C.depth + 1, 0, h.facing, P.wood)
    if (cellarVault) cellarVault(c, dug)
  }
  for (let l = 1; l <= level; l++) for (const f of M.FURNITURE[l] ?? []) {
    const key = f.pos ? f.pos.join() : null
    const g = key && key in chains ? { ...f, chain: chains[key] } : f
    if (furnitureParts[g.part]) furnitureParts[g.part](c, g)
    else { c.free(g.pos); c.set(...g.pos, g.block ?? g.part) } // a fixture with no part of its own (the jukebox)
  }
  return dug
}
