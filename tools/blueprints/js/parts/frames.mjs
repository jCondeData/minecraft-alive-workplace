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
