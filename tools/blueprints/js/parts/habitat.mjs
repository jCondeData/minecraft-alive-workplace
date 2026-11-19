// Habitat pieces (parts library, tools/blueprints/REBUILD.md; grown from the Habitat Garden, 49): a natural pond
// with a grass bank worked round its water, an open-fronted lean-to whose roof rises to its back wall, and a raised
// cabin on stilts (a keeper's hide, a lookout) with braces, a ladder through a floor hatch and a hipped cap. Every
// block comes from the caller's palette.
import { stair } from './canvas.mjs'
import { pool } from './water.mjs'
import { beamRing, post } from './frames.mjs'
import { hipCap } from './tinker.mjs'
import { hatchLadder } from './stairs.mjs'

const pick = (id, x, y, z) => (typeof id === 'function' ? id(x, y, z) : id)

/** bankedPond: still water sources in `water` ([x, z]) at y on a bed (`bed`, at y - 1), held in by a bank: every
 *  cell next to the water (eight ways) not drawn yet at y gets `soil` at y - 1 and `bank` at y (grass), or
 *  `boulder` where it is listed in `boulders`. A cell already drawn at y (a stilt, a standing stone) is part of the
 *  bank as it is. pool() checks nothing runs out. Returns the bank cells [x, z] it drew. */
export function bankedPond(c, { water, y = 1, boulders = [] }, { bed, soil, bank, boulder }) {
  const wet = new Set(water.map(([x, z]) => `${x},${z}`))
  const rocks = new Set(boulders.map(([x, z]) => `${x},${z}`))
  for (const [x, z] of water) c.set(x, y - 1, z, pick(bed, x, y - 1, z))
  const ring = []
  const seen = new Set()
  for (const [x, z] of water) for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) {
    const k = `${x + dx},${z + dz}`
    if (wet.has(k) || seen.has(k)) continue
    seen.add(k)
    const [bx, bz] = [x + dx, z + dz]
    if (c.get(bx, y, bz)) continue
    c.set(bx, y - 1, bz, pick(soil, bx, y - 1, bz))
    c.set(bx, y, bz, rocks.has(k) ? pick(boulder, bx, y, bz) : pick(bank, bx, y, bz), rocks.has(k) ? {} : { snowy: 'false' })
    ring.push([bx, bz])
  }
  pool(c, water, y)
  return ring
}

/** openShed: an open-fronted shed x0..x1, z0..z1 on the ground (y 0), its back wall on x1 and a low roof rising
 *  half a block a column to the east, from `roofFrom` (a bottom slab at eaveY, the eave proud of the front; x0 must
 *  be one column in, on a full step) to x1, over z0 - 1 .. z1 + 1: bottom slabs and full blocks by turns. The front
 *  column's full step is a beam of `frame` along z (out past the posts at both ends) on posts at z0 and z1, so the
 *  front opening is two blocks high under it. The back wall (`wall`, with `frame` posts at `backPosts`) and the side
 *  walls on z0 and z1 fill up to the roof; `sideWindows` [[x, y]] put `glass` in both side walls. Returns roofY(x),
 *  the y of the roof's block over column x. */
export function openShed(c, { x0, x1, z0, z1, roofFrom, eaveY, backPosts, sideWindows = [] }, { frame, wall, roof, glass = null }) {
  if ((x0 - roofFrom) % 2 !== 1) throw new Error('openShed: the front posts must stand under a full step')
  const roofY = x => eaveY + Math.floor((x - roofFrom) / 2)
  const full = x => (x - roofFrom) % 2 === 1
  for (let x = roofFrom; x <= x1; x++) for (let z = z0 - 1; z <= z1 + 1; z++) {
    if (x === x0) c.set(x, roofY(x), z, frame, { axis: 'z' })
    else if (full(x)) c.set(x, roofY(x), z, roof.full)
    else c.set(x, roofY(x), z, roof.slab, { type: 'bottom', waterlogged: 'false' })
  }
  for (const z of [z0, z1]) post(c, x0, z, 1, roofY(x0) - 1, frame)
  for (let z = z0; z <= z1; z++)
    for (let y = 1; y < roofY(x1); y++) c.set(x1, y, z, backPosts.includes(z) ? frame : wall, backPosts.includes(z) ? { axis: 'y' } : {})
  for (const z of [z0, z1]) for (let x = x0 + 1; x < x1; x++) for (let y = 1; y < roofY(x); y++) c.set(x, y, z, wall)
  for (const z of [z0, z1]) for (const [x, y] of sideWindows) c.set(x, y, z, glass, { waterlogged: 'false' })
  return roofY
}

/** stiltCabin: a raised cabin x0..x1, z0..z1 whose floor is at floorY: `stilts` [x, z] logs from a stone pad at
 *  y 0 up to the floor, knee `braces` [x, y, z, toward] under its beam ring, a beam ring at floorY round boards
 *  (`floor`), walls of `wall` from floorY + 1 to wallTop with corner posts, glass in `windows` (lists of cells, each
 *  run ending on a post or a wall), a `doorway` two high, a beam ring at beamY, a frieze of wall one above it under a
 *  hipped cap whose eave is one out from the walls, and a `ladder` {x, z, facing} from the ground to a hatch in the
 *  floor. Returns the roof's peak y. */
export function stiltCabin(c, h, { frame, wall, floor, pad, glass, roof, wood, apex = null }) {
  const { x0, x1, z0, z1, floorY, wallTop, beamY } = h
  for (const [x, z] of h.stilts) { c.set(x, 0, z, pad); post(c, x, z, 1, floorY - 1, frame) }
  for (const [x, y, z, toward] of h.braces) stair(c, x, y, z, `${wood}_stairs`, toward, 'top')
  beamRing(c, x0, z0, x1, z1, floorY, frame)
  for (let x = x0 + 1; x < x1; x++) for (let z = z0 + 1; z < z1; z++) c.set(x, floorY, z, floor)
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (x !== x0 && x !== x1 && z !== z0 && z !== z1) continue
    const corner = (x === x0 || x === x1) && (z === z0 || z === z1)
    for (let y = floorY + 1; y <= wallTop; y++) corner ? c.set(x, y, z, frame, { axis: 'y' }) : c.set(x, y, z, wall)
    c.set(x, beamY + 1, z, wall) // the frieze under the eave
  }
  for (const run of h.windows) for (const [x, y, z] of run) c.set(x, y, z, glass, { waterlogged: 'false' })
  const [dx, dy, dz] = h.doorway
  c.clear(dx, dy, dz); c.clear(dx, dy + 1, dz)
  beamRing(c, x0, z0, x1, z1, beamY, frame)
  const cx = (x0 + x1) >> 1, cz = (z0 + z1) >> 1
  const peak = hipCap(c, cx, cz, ((x1 - x0) >> 1) + 1, beamY + 1, roof, { apex })
  hatchLadder(c, h.ladder.x, h.ladder.z, 1, floorY, h.ladder.facing, wood)
  return peak
}

/** potsOn: a pot on each support cell's top (cells [x, y, z] of the supports; pots[i] the potted plant). */
export function potsOn(c, cells, pots) { cells.forEach(([x, y, z], i) => c.set(x, y + 1, z, pots[i % pots.length])) }

