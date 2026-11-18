// Challenge halls (the Leader's Hall, 49 rebuild): proud piers down a long wall, a raised dais with a full-width
// step, and a standard (a banner on a pole). Every block comes from the caller's palette.
import { stair } from './canvas.mjs'

/** piers: a row of piers a block proud of a wall (axis 'x': the wall runs along z at x = line; 'z': along x at
 *  z = line), at each of `at`, from y0 to y1, each capped by `cap` (a stair id facing out, or null). `out` is the
 *  side they stand out on. */
export function piers(c, axis, line, at, y0, y1, id, { cap = null, out = 'north' } = {}) {
  for (const u of at) {
    const [x, z] = axis === 'x' ? [line, u] : [u, line]
    for (let y = y0; y <= y1; y++) c.set(x, y, z, id)
    if (cap) stair(c, x, y1 + 1, z, cap, ({ north: 'south', south: 'north', west: 'east', east: 'west' })[out])
  }
}

/** dais: a raised platform x0..x1, z0..z1 one block high at y, its front (the `front` z edge, facing north) one
 *  row of `step` stairs across x0..x1 rising toward the dais, so anyone walks straight up from the floor. */
export function dais(c, { x0, x1, z0, z1, y = 1, stepZ }, { top, step }) {
  c.fill(x0, y, z0, x1, y, z1, top)
  for (let x = x0; x <= x1; x++) stair(c, x, y, stepZ, step, 'south')
}

/** standard: a banner on a pole: a `pole` (fence) `height` tall on the floor at (x, y, z), a wall banner of
 *  `colour` hung on its `face` side at the top. */
export function standard(c, x, y, z, { pole, colour, face, height = 3 }) {
  for (let i = 0; i < height; i++) c.set(x, y + i, z, pole)
  const d = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }[face]
  c.set(x + d[0], y + height - 1, z + d[1], `${colour}_wall_banner`, { facing: face })
}
