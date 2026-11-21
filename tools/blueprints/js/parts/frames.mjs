// Frames: the plinth a house stands on, its floors, upright posts and the beam ring that ties them (parts library,
// tools/blueprints/REBUILD.md). Blocks come from the family's palette.

/** plinth: a stone ring (or slab of ground) at y, the house's darkest course. `stone` may be a mix(). */
export function plinth(c, x0, z0, x1, z1, stone, y = 0) {
  c.fill(x0, y, z0, x1, y, z1, stone)
}

/** floor: boards (or flags) over a rectangle at y. */
export function floor(c, x0, z0, x1, z1, y, id) { c.fill(x0, y, z0, x1, y, z1, id) }

/** post: an upright frame log from y0 to y1. */
export function post(c, x, z, y0, y1, log) { for (let y = y0; y <= y1; y++) c.set(x, y, z, log, { axis: 'y' }) }

/** posts: a row of posts along one wall line (`axis` 'x': at each x of `at` on line z; 'z': at each z on line x). */
export function posts(c, axis, line, at, y0, y1, log) {
  for (const u of at) axis === 'x' ? post(c, u, line, y0, y1, log) : post(c, line, u, y0, y1, log)
}

/** beamRing: horizontal logs round a rectangle at y, corners upright. */
export function beamRing(c, x0, z0, x1, z1, y, log) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (x !== x0 && x !== x1 && z !== z0 && z !== z1) continue
    const corner = (x === x0 || x === x1) && (z === z0 || z === z1)
    c.set(x, y, z, log, { axis: corner ? 'y' : (z === z0 || z === z1) ? 'x' : 'z' })
  }
}

/** steppedPlinth: a raised stone floor over a rectangle at y whose edge is a ring of stairs rising inward (a step up
 *  from every side; kit.finish turns the corners), the inside full `stone`. `step(x, z)` picks the edge's stair id. */
export function steppedPlinth(c, x0, z0, x1, z1, stone, step, y = 0) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const edge = x === x0 || x === x1 || z === z0 || z === z1
    if (!edge) { c.set(x, y, z, stone); continue }
    const facing = z === z0 ? 'south' : z === z1 ? 'north' : x === x0 ? 'east' : 'west'
    c.set(x, y, z, step(x, z), { facing, half: 'bottom', shape: 'straight', waterlogged: 'false' })
  }
}

/** cellar: a room dug under the ground floor (a LiftedCanvas with base >= depth): a floor at y = -depth over the walls'
 *  rectangle x0..x1, z0..z1 and the wall ring up to y = -1 (the ground floor's floor is its ceiling). `wall` may be a
 *  mix(). Returns the inside box [x0, z0, x1, z1] (rebuild.py --cellar: those cells are dug out). */
export function cellar(c, x0, z0, x1, z1, depth, wall, floorId) {
  c.fill(x0, -depth, z0, x1, -depth, z1, floorId)
  for (let y = -depth + 1; y <= -1; y++)
    for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++)
      if (x === x0 || x === x1 || z === z0 || z === z1) c.set(x, y, z, wall)
  return [x0 + 1, z0 + 1, x1 - 1, z1 - 1]
}

/** brackets: upside-down stairs under an overhang (a jetty's beam, a bay), each with its back to the wall behind it
 *  (`facing`: the way the wall lies from the bracket), so the storey above reads as carried. Grown from the Burgher's
 *  Townhouse (49). */
export function brackets(c, cells, id, facing) {
  for (const [x, y, z] of cells) c.set(x, y, z, id, { facing, half: 'top', shape: 'straight', waterlogged: 'false' })
}
