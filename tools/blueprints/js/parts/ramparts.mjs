// Ramparts (ROADMAP 52.3, the stone wall kit: stone_wall and wall_tower; any wall, gate or tower can use them).
// Every block comes from the palette the caller passes; a battle station (BattleStations.read) is any block at y >= 2
// with a sturdy top and two empty cells over it, so the parts below keep their tops unsturdy (bottom stairs and slabs)
// wherever a stray station would spoil a family's count.
import { stair, slab } from './canvas.mjs'

/** crenels: merlons along a rim. `cells` are [x, z] in order; `merlon(i, x, z)` says which carry one. Each merlon is
 *  `block` from y0 to y1, capped (optionally) by `cap` at y1 + 1: a finial (a wall post) or a bottom slab, an unsturdy top so
 *  no station stands on a merlon (a slab cap between open crenels reads to holes.py as a gap between roofs). Returns the crenel cells (the gaps). */
export function crenels(c, cells, y0, y1, block, merlon, cap = null) {
  const gaps = []
  cells.forEach(([x, z], i) => {
    if (!merlon(i, x, z)) { gaps.push([x, z]); return }
    for (let y = y0; y <= y1; y++) c.set(x, y, z, block)
    if (cap) cap.endsWith('_slab') ? slab(c, x, y1 + 1, z, cap) : c.set(x, y1 + 1, z, cap)
  })
  return gaps
}

/** wallFlight: a straight flight of steps up the face of a wall, along x in row z, from (x0, y0) rising one per block
 *  toward `rise` ('east' or 'west'), `steps` long, with its underside filled solid by `fill` down to y0 (a stepped
 *  buttress, not a ladder: a villager or a player walks it). Returns the top step [x, y, z]. */
export function wallFlight(c, x0, y0, z, steps, rise, stairs, fill) {
  const dx = rise === 'east' ? 1 : -1
  let top = null
  for (let i = 0; i < steps; i++) {
    const x = x0 + dx * i, y = y0 + i
    for (let yy = y0; yy < y; yy++) c.set(x, yy, z, fill)
    stair(c, x, y, z, stairs, rise)
    top = [x, y, z]
  }
  return top
}

/** newel: a pier at the foot of a wall's inside face with a wall-post on it and a lantern standing on the post (the
 *  owner's rule: a lantern on a post, never on a floor). Its top is a post, not a sturdy block: no station. */
export function newel(c, x, z, y0, y1, pier, postId, lamp = 'lantern') {
  for (let y = y0; y <= y1; y++) c.set(x, y, z, pier)
  c.set(x, y1 + 1, z, postId, { up: 'true', north: 'none', south: 'none', east: 'none', west: 'none', waterlogged: 'false' })
  if (lamp) c.set(x, y1 + 2, z, lamp, { hanging: 'false', waterlogged: 'false' })
}

/** hoarding: a timber fighting gallery hung on a wall's outer face (row `wallZ`), out over its foot (row `outZ`),
 *  along x0..x1. Bays alternate: a `bay(x)` cell gets a bracket (an upside-down stair under the board), a board at
 *  floorY and a post over it; the others are left open as murder holes, with the screen open over them so the
 *  defender in the crenel behind looks straight down through the hole. A roof of stairs runs from the outer eave (over
 *  the posts) up over the wall, one course per row, to a slab ridge over row `topZ` (`backEave`: a short slope down
 *  the other side); `joist` puts a beam over each bay's merlon so the roof sits on the stone.
 *  P: { bracket, board, post, roof, roofCap, beam }. Returns { holes, boards }. */
export function hoarding(c, { x0, x1, wallZ, outZ, floorY, topZ, bay, joist = true, backEave = false }, P) {
  const inward = outZ > wallZ ? 'north' : 'south'
  const step = outZ > wallZ ? -1 : 1
  const holes = [], boards = []
  for (let x = x0; x <= x1; x++) {
    if (bay(x)) {
      stair(c, x, floorY - 1, outZ, P.bracket, inward, 'top')
      c.set(x, floorY, outZ, P.board)
      c.set(x, floorY + 1, outZ, P.post, { axis: 'y' })
      if (joist) c.set(x, floorY + 2, wallZ, P.beam, { axis: 'z' })
      boards.push(x)
    } else holes.push(x)
    // the roof: the eave over the posts, one course up per row inward, to a ridge of slabs over topZ; with
    // `backEave` a short slope falls on the inside too (a lopsided gable: long outside, short over the walk)
    let y = floorY + 2, z = outZ
    for (; z !== topZ; z += step, y++) stair(c, x, y, z, P.roof, inward)
    slab(c, x, y, z, P.roofCap)
    if (backEave) stair(c, x, y - 1, z + step, P.roof, inward === 'north' ? 'south' : 'north')
  }
  return { holes, boards }
}

/** arrowLoop: a slit one wide and two tall in a wall at [x, z], from y; its sill (y - 1) is a stair sloping down to
 *  the outside (a plunging sill: the archer sees the foot of the wall, and no station stands in a loop). */
export function arrowLoop(c, x, y, z, inward, sill) {
  c.clear(x, y, z); c.clear(x, y + 1, z)
  stair(c, x, y - 1, z, sill, inward)
}

/** corbelLine: a shadow line under a parapet or a deck: the wall's course at y round a rectangle (corners left to the
 *  quoins) laid as upside-down stairs facing in, so their open lower quarter shows outside as a notch, the way a corbel
 *  table reads, while the inner half keeps the wall shut. */
export function corbelLine(c, x0, z0, x1, z1, y, stairs, skip = () => false) {
  for (let x = x0 + 1; x < x1; x++) {
    if (!skip(x, z0)) stair(c, x, y, z0, stairs, 'south', 'top')
    if (!skip(x, z1)) stair(c, x, y, z1, stairs, 'north', 'top')
  }
  for (let z = z0 + 1; z < z1; z++) {
    if (!skip(x0, z)) stair(c, x0, y, z, stairs, 'east', 'top')
    if (!skip(x1, z)) stair(c, x1, y, z, stairs, 'west', 'top')
  }
}

/** hipCap: a pyramid roof of stairs on a square ring from (x0, z0)-(x1, z1) at y, one course in per block up, closed
 *  at the top by a bottom slab `cap` (so the apex is no station) when the square doesn't come to a point. */
export function hipCap(c, x0, z0, x1, z1, y, stairs, cap) {
  let k = 0
  for (; x0 + k < x1 - k && z0 + k < z1 - k; k++) {
    const a = x0 + k, b = x1 - k, f = z0 + k, g = z1 - k, yy = y + k
    const put = (x, z, facing, shape = 'straight') =>
      c.set(x, yy, z, stairs, { facing, half: 'bottom', shape, waterlogged: 'false' })
    for (let x = a + 1; x < b; x++) { put(x, f, 'south'); put(x, g, 'north') }
    for (let z = f + 1; z < g; z++) { put(a, z, 'east'); put(b, z, 'west') }
    put(a, f, 'south', 'outer_right'); put(b, f, 'south', 'outer_left')
    put(a, g, 'north', 'outer_left'); put(b, g, 'north', 'outer_right')
  }
  if (x0 + k === x1 - k) slab(c, x0 + k, y + k - 1, z0 + k, cap) // fills the centre of the last ring
  return y + k - 1
}

/** crossGableCrown: a square top (odd width, at least 5) roofed by four little gables, one over the middle of each
 *  side, in two courses: at y the corners are eave stairs and each side's middle cells the gable's base (`gable`, full
 *  blocks: a lantern may hang under one); at y + 1 each side's gable rises in stairs to a slab peak and the middle is
 *  a slab flat where the four ridges meet. Every top is a bottom stair or slab: no station on the roof.
 *  P: { stairs, gable, slab }. */
export function crossGableCrown(c, x0, z0, x1, z1, y, P) {
  const mx = (x0 + x1) / 2, mz = (z0 + z1) / 2
  // course 1: eave stairs at the corners, the gables' bases between
  c.set(x0, y, z0, P.stairs, { facing: 'south', half: 'bottom', shape: 'outer_right', waterlogged: 'false' })
  c.set(x1, y, z0, P.stairs, { facing: 'south', half: 'bottom', shape: 'outer_left', waterlogged: 'false' })
  c.set(x0, y, z1, P.stairs, { facing: 'north', half: 'bottom', shape: 'outer_left', waterlogged: 'false' })
  c.set(x1, y, z1, P.stairs, { facing: 'north', half: 'bottom', shape: 'outer_right', waterlogged: 'false' })
  for (let x = x0 + 1; x < x1; x++) { c.set(x, y, z0, P.gable); c.set(x, y, z1, P.gable) }
  for (let z = z0 + 1; z < z1; z++) { c.set(x0, y, z, P.gable); c.set(x1, y, z, P.gable) }
  // course 2: each gable's rakes up to its peak over the side's middle; slabs where the ridges meet
  for (let x = x0 + 1; x < x1; x++) for (const z of [z0, z1]) {
    if (x === mx) slab(c, x, y + 1, z, P.slab)
    else stair(c, x, y + 1, z, P.stairs, x < mx ? 'east' : 'west')
  }
  for (let z = z0 + 1; z < z1; z++) for (const x of [x0, x1]) {
    if (z === mz) slab(c, x, y + 1, z, P.slab)
    else stair(c, x, y + 1, z, P.stairs, z < mz ? 'south' : 'north')
  }
  for (let x = x0 + 1; x < x1; x++) for (let z = z0 + 1; z < z1; z++) slab(c, x, y + 1, z, P.slab)
}

/** batteredFoot: the foot of a tower's faces splayed: a course of stairs at y facing in (their low step outward) on
 *  every face cell but the corners and `skip(x, z)` (a door's sill). */
export function batteredFoot(c, x0, z0, x1, z1, y, stairs, skip = () => false) {
  for (let x = x0 + 1; x < x1; x++) {
    if (!skip(x, z0)) stair(c, x, y, z0, stairs, 'south')
    if (!skip(x, z1)) stair(c, x, y, z1, stairs, 'north')
  }
  for (let z = z0 + 1; z < z1; z++) {
    if (!skip(x0, z)) stair(c, x0, y, z, stairs, 'east')
    if (!skip(x1, z)) stair(c, x1, y, z, stairs, 'west')
  }
}
