// Gatehouses (ROADMAP 49, the Gatehouse of the stone wall kit; any gate, tower or lodge can use them). Every block
// comes from the palette the caller passes; nothing here picks one. Battle stations (BattleStations.read) are any
// block at y >= 2 with a sturdy top and two empty cells over it, so the parts keep their exposed tops unsturdy
// (bottom stairs and slabs) unless the caller wants a station there.
//   steepHip      a steep hipped cap over any rectangle, even widths included: the eave course of stairs, then each
//                 ring a course of full blocks under a course of stairs (two up per one in), the last ring a short
//                 ridge of stairs back to back across the short span. Every top a bottom stair: no station on it.
//   archHead      the head of a gateway three or more wide in a wall plane: upside-down stairs in its top corners
//                 (the haunches) and a keystone over the middle
//   leafDoorway   a doorway with its plank leaves swung open (open trapdoors against one jamb): a way in that is no
//                 door block (a siege counts every door as a gate block) yet shuts the room to the weather
//   loggia        an open fighting front: a central pier between two lights, each light on a low sill slab (an
//                 unsturdy top: no station in the opening) and under a corbel springing from the pier
//   murderDeck    a deck laid over a passage with chosen cells left open (murder holes)
import { stair, slab, trapdoor } from './canvas.mjs'

/** steepHip: a steep hipped cap over x0..x1, z0..z1 from y (the eave course). P: { stairs, full }. `keep(x, y, z)`
 *  false leaves a cell to something else. Returns the top course's y. */
export function steepHip(c, { x0, z0, x1, z1, y }, P, keep = () => true) {
  let k = 0, top = y
  for (; x0 + k <= x1 - k && z0 + k <= z1 - k; k++) {
    const a = x0 + k, b = x1 - k, f = z0 + k, g = z1 - k
    const yy = y + 2 * k
    const narrowX = b - a <= 1, narrowZ = g - f <= 1 // the last ring: a short ridge across the narrow span
    for (let x = a; x <= b; x++) for (let z = f; z <= g; z++) {
      if (x !== a && x !== b && z !== f && z !== g) continue
      let facing
      if (narrowX && !narrowZ) facing = x === a ? 'east' : 'west'
      else if (narrowZ && !narrowX) facing = z === f ? 'south' : 'north'
      else if (z === f) facing = 'south'
      else if (z === g) facing = 'north'
      else facing = x === a ? 'east' : 'west'
      if (k > 0 && keep(x, yy - 1, z)) c.set(x, yy - 1, z, P.full)
      if (keep(x, yy, z)) stair(c, x, yy, z, P.stairs, facing)
    }
    top = yy
  }
  return top
}

/** archHead: the head of a gateway x0..x1 in row z, its top course at y: upside-down stairs in the two top corners
 *  (their full side against the jambs) and `key` over the middle at y + 1 (when given). */
export function archHead(c, x0, x1, z, y, stairs, key = null) {
  stair(c, x0, y, z, stairs, 'west', 'top')
  stair(c, x1, y, z, stairs, 'east', 'top')
  if (key) c.set((x0 + x1) / 2, y + 1, z, key)
}

/** leafDoorway: a doorway at (x, y, z), `height` high, cleared and hung with open trapdoor leaves of `wood` lying
 *  against the jamb on the `hinge` side (north, south, east or west). */
export function leafDoorway(c, [x, y, z], hinge, wood, height = 2) {
  const facing = { north: 'south', south: 'north', west: 'east', east: 'west' }[hinge] // an open trapdoor lies on the side it faces away from
  for (let i = 0; i < height; i++) trapdoor(c, x, y + i, z, `${wood}_trapdoor`, facing, 'bottom', true)
}

/** loggia: an open front along x in row z: lights at `lights` (x values) on sill slabs at sillY, open from sillY + 1
 *  to headY - 1, under corbels (upside-down stairs) at headY leaning on the pier at `pier`; the pier is `stone` from
 *  sillY to headY. `rail` (optional): a wall-post block standing on each sill instead of leaving it open. `corbels`
 *  false: the lights open right up to headY (an eave over them is their head) and the pier stops under it. */
export function loggia(c, { z, pier, lights, sillY, headY, corbels = true }, P, rail = null) {
  for (let y = sillY; y <= (corbels ? headY : headY - 1); y++) c.set(pier, y, z, P.stone)
  for (const x of lights) {
    if (rail) c.set(x, sillY, z, P.floor)
    else slab(c, x, sillY, z, P.sill)
    if (rail) c.set(x, sillY + 1, z, rail)
    for (let y = sillY + (rail ? 2 : 1); y < headY; y++) c.clear(x, y, z)
    if (corbels) stair(c, x, headY, z, P.corbel, x < pier ? 'east' : 'west', 'top')
  }
}

/** murderDeck: a deck at y over x0..x1, z0..z1 of `id` (a function of the cell is fine), leaving `holes` ([x, z])
 *  open; `keep(x, y, z)` false leaves a cell to what is already drawn there (a tower's wall). Returns the cells laid. */
export function murderDeck(c, { x0, x1, z0, z1, y }, id, holes = [], keep = () => true) {
  const laid = []
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (!keep(x, y, z)) continue
    if (holes.some(([hx, hz]) => hx === x && hz === z)) { c.clear(x, y, z); continue }
    c.set(x, y, z, id); laid.push([x, z])
  }
  return laid
}

/** crenelTop: a tower's fighting top with no fighting deck (a battle station nobody may take): a parapet ring round
 *  x0..x1, z0..z1 at y, merlons where `merlon(x, z)` (P.merlon from y to y + 1, capped by P.cap: a slab is laid as a
 *  bottom slab, a coping; anything else as a wall-post finial), a
 *  bottom slab sill (P.sill, an unsturdy top) in every crenel, and inside the ring a low roof of P.roof stairs at y
 *  rising from both long sides to a ridge (hidden behind the merlons, an unsturdy top everywhere). */
export function crenelTop(c, { x0, z0, x1, z1, y }, P, merlon) {
  const post = { up: 'true', north: 'none', south: 'none', east: 'none', west: 'none', waterlogged: 'false' }
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (x !== x0 && x !== x1 && z !== z0 && z !== z1) continue
    if (merlon(x, z)) {
      c.set(x, y, z, P.merlon); c.set(x, y + 1, z, P.merlon)
      if (P.cap.endsWith('_slab')) slab(c, x, y + 2, z, P.cap)
      else c.set(x, y + 2, z, P.cap, post)
    }
    else slab(c, x, y, z, P.sill)
  }
  const alongZ = z1 - z0 >= x1 - x0
  steepHip(c, { x0: x0 + 1, z0: z0 + 1, x1: x1 - 1, z1: z1 - 1, y }, { stairs: P.roof, full: P.roofFull ?? P.merlon })
  return alongZ
}
