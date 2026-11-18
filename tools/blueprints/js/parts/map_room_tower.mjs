// Towers (parts library, grown from the Map Room, ROADMAP 49): a corbel ring under a jettied deck, a compass rose
// inlaid in a floor, an open lookout's posts and rail, a ladder shaft up through the floors, a battered plinth.
// Every block comes from the caller's palette.
import { stair } from './canvas.mjs'

/** corbelRing: upside-down stairs round a rectangle's outer ring at y (x0..x1, z0..z1), their backs to the wall
 *  inside, carrying a deck that juts out over them. `skip(x, z)` leaves a cell. */
export function corbelRing(c, x0, z0, x1, z1, y, stairs, skip = () => false) {
  const put = (x, z, facing, shape = 'straight') => {
    if (!skip(x, z)) c.set(x, y, z, stairs, { facing, half: 'top', shape, waterlogged: 'false' })
  }
  for (let x = x0 + 1; x < x1; x++) { put(x, z0, 'south'); put(x, z1, 'north') }
  for (let z = z0 + 1; z < z1; z++) { put(x0, z, 'east'); put(x1, z, 'west') }
  put(x0, z0, 'south', 'outer_right'); put(x1, z0, 'south', 'outer_left')
  put(x0, z1, 'north', 'outer_left'); put(x1, z1, 'north', 'outer_right')
}

/** compassRose: a rose inlaid flush in a floor at (cx, y, cz): `heart` in the middle, `arm` out `reach` blocks along
 *  the four quarters (the north arm toward z = 0), `diag` on the four diagonal cells round the heart. */
export function compassRose(c, cx, y, cz, { heart, arm, diag, reach = 2 }) {
  c.set(cx, y, cz, heart)
  for (let r = 1; r <= reach; r++) for (const [dx, dz] of [[0, -1], [0, 1], [-1, 0], [1, 0]]) c.set(cx + dx * r, y, cz + dz * r, arm)
  if (diag) for (const [dx, dz] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) c.set(cx + dx, y, cz + dz, diag)
}

/** lookoutRail: an open lookout on a deck: corner posts (`post`, a log) from y0 to y1, and a rail of `fence` at y0
 *  round the edge between them. `gap` lists [x, z] edge cells left open. */
export function lookoutRail(c, x0, z0, x1, z1, y0, y1, { post, fence }, gap = []) {
  const open = (x, z) => gap.some(([gx, gz]) => gx === x && gz === z)
  for (const [x, z] of [[x0, z0], [x1, z0], [x0, z1], [x1, z1]]) for (let y = y0; y <= y1; y++) c.set(x, y, z, post, { axis: 'y' })
  for (let x = x0 + 1; x < x1; x++) for (const z of [z0, z1]) if (!open(x, z)) c.set(x, y0, z, fence)
  for (let z = z0 + 1; z < z1; z++) for (const x of [x0, x1]) if (!open(x, z)) c.set(x, y0, z, fence)
}

/** ladderShaft: a ladder at (x, z) from y0 to y1 (facing away from the wall it hangs on), every floor it passes
 *  through cut open over it. */
export function ladderShaft(c, x, z, y0, y1, facing) {
  for (let y = y0; y <= y1; y++) c.set(x, y, z, 'ladder', { facing, waterlogged: 'false' })
}

/** batter: a splayed foot round a tower's base: the outer ring x0..x1, z0..z1 at y gets stairs leaning on the wall
 *  (their high side inward), a sloped skirt that roots the shaft. `skip(x, z)` leaves the way in. */
export function batter(c, x0, z0, x1, z1, y, stairs, skip = () => false) {
  const put = (x, z, facing, shape = 'straight') => {
    if (!skip(x, z)) c.set(x, y, z, stairs, { facing, half: 'bottom', shape, waterlogged: 'false' })
  }
  for (let x = x0 + 1; x < x1; x++) { put(x, z0, 'south'); put(x, z1, 'north') }
  for (let z = z0 + 1; z < z1; z++) { put(x0, z, 'east'); put(x1, z, 'west') }
  put(x0, z0, 'south', 'outer_right'); put(x1, z0, 'south', 'outer_left')
  put(x0, z1, 'north', 'outer_left'); put(x1, z1, 'north', 'outer_right')
}
