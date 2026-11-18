// Climate parts: the pieces a biome look draws its house with (ROADMAP 49.10a, the Starter Cottage's desert and snowy
// looks). Every block comes from the palette passed in; nothing here is hard-coded.
//   flatRoof   a flat roof deck over a box (desert)
//   parapet    the low wall round a flat roof, with piers at the corners (desert)
//   vigas      roof beams whose ends stick out of a wall under a flat roof (desert)
//   logCourse  a log wall course whose logs lie along the wall (snowy log cabin)
//   mudroom    a little closed entry porch before a door, its own steep gable roof (snowy)
import { stair, slab } from './canvas.mjs'

/** flatRoof: a deck of `id` over x0..x1, z0..z1 at y. */
export function flatRoof(c, x0, z0, x1, z1, y, id) { c.fill(x0, y, z0, x1, y, z1, id) }

/** parapet: a ring of `wall` (a wall block, which joins itself at the corners) round x0..x1, z0..z1 at y, with `pier`
 *  blocks at the four corners `pierH` tall; `gaps` [[x, z]] are left open (scuppers). */
export function parapet(c, x0, z0, x1, z1, y, wall, pier, pierH = 1, gaps = []) {
  const gap = (x, z) => gaps.some(([gx, gz]) => gx === x && gz === z)
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (x !== x0 && x !== x1 && z !== z0 && z !== z1) continue
    if (gap(x, z)) continue
    const corner = (x === x0 || x === x1) && (z === z0 || z === z1)
    if (corner) for (let i = 0; i < pierH; i++) c.set(x, y + i, z, pier)
    else c.set(x, y, z, wall)
  }
}

/** vigas: beam ends (`log`, lying across the wall) poking one block out of a wall line at y. axis: the wall's
 *  normal ('z' for a front or back wall, 'x' for a side), `at` the positions along the wall, `out` the cell outside. */
export function vigas(c, axis, out, at, y, log) {
  for (const a of at) {
    const [x, z] = axis === 'z' ? [a, out] : [out, a]
    c.set(x, y, z, log, { axis })
  }
}

/** logCourse: a wall of logs lying along it, from (ax, az) to (bx, bz), y0..y1 (log-cabin walls). */
export function logCourse(c, [ax, az], [bx, bz], y0, y1, log) {
  const axis = ax === bx ? 'z' : 'x'
  for (let x = Math.min(ax, bx); x <= Math.max(ax, bx); x++)
    for (let z = Math.min(az, bz); z <= Math.max(az, bz); z++)
      for (let y = y0; y <= y1; y++) c.set(x, y, z, log, { axis })
}

/** mudroom: a closed entry porch x0..x1 (walls on x0 and x1), z0..z1 (front wall at z0), its door `door` (an id
 *  prefix, e.g. 'oak') at (doorX, 1, z0), its own steep little gable roof (ridge along z) from eaveY. `wall` fills the
 *  walls, `roof` is {full, stairs, slab}. Returns the door's cell. */
export function mudroom(c, { x0, x1, z0, z1, eaveY }, { wall, roof, door, floor }) {
  const dx = (x0 + x1) / 2
  for (let z = z0; z <= z1; z++) {
    for (let y = 1; y < eaveY; y++) { c.set(x0, y, z, wall); c.set(x1, y, z, wall) }
    for (let x = x0; x <= x1; x++) c.set(x, 0, z, floor)
  }
  for (let y = 1; y < eaveY; y++) for (let x = x0 + 1; x < x1; x++) if (y > 2) c.set(x, y, z0, wall)
  for (const half of ['lower', 'upper'])
    c.set(dx, half === 'upper' ? 2 : 1, z0, `${door}_door`, { facing: 'south', half, hinge: 'left', open: 'false', powered: 'false' })
  // the roof: a stair each side over the walls' tops, a full block and a ridge slab over the middle
  for (let z = z0; z <= z1; z++) {
    stair(c, x0 - 1, eaveY - 1, z, roof.stairs, 'east')
    stair(c, x1 + 1, eaveY - 1, z, roof.stairs, 'west')
    stair(c, x0, eaveY, z, roof.stairs, 'east')
    stair(c, x1, eaveY, z, roof.stairs, 'west')
    c.set(dx, eaveY, z, roof.full)
    slab(c, dx, eaveY + 1, z, roof.slab)
  }
  return [dx, 1, z0]
}
