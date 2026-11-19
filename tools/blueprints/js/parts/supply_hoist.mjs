// Shop fronts and goods hoists (parts library, tools/blueprints/REBUILD.md; grown from the Supply Shop, 49): a wall
// trellis, a taking-in hoist at a gable's apex, a sign band across a jetty, a bay of glass under a jetty, a cask stack.
// Every block comes from the caller's palette; none is picked here.
import { DIRS, trapdoor } from './canvas.mjs'

/** wallTrellis: a lattice for climbers flat on a wall: open trapdoors `height` high at f.pos, f.facing away from the
 *  wall (the wall is behind it), a 'trellis' furniture spot. f.wood: the trapdoors' wood. */
export function wallTrellis(c, f) {
  const [x, y, z] = f.pos
  const n = f.height ?? 2
  const cells = Array.from({ length: n }, (_, i) => [x, y + i, z])
  c.free(...cells)
  for (const [cx, cy, cz] of cells) trapdoor(c, cx, cy, cz, `${f.wood}_trapdoor`, f.facing, 'bottom', true)
  c.spot('trellis', f.pos, f.facing, cells.map((_, i) => [0, i, 0]), f.wood)
}

/** gableHoist: a taking-in hoist: a beam (`beam`, a log) out of a gable at [x, y, z] one block toward `out`, a rope of
 *  `drop` chains under its tip and a hanging lantern on the end (the shop's sign-lamp over its door). */
export function gableHoist(c, [x, y, z], out, drop, { beam, lamp = 'lantern' }) {
  const [ox, oz] = DIRS[out]
  const tx = x + ox, tz = z + oz
  c.set(tx, y, tz, beam, { axis: ox ? 'x' : 'z' })
  for (let i = 1; i <= drop; i++) c.set(tx, y - i, tz, 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(tx, y - drop - 1, tz, lamp, { hanging: 'true', waterlogged: 'false' })
}

/** signBand: a band of `id` (a shop's metal or painted fascia) along x from x0 to x1 on line z at y, the course a jetty
 *  carries; `skip(x)` leaves a cell (a stud) alone. */
export function signBand(c, x0, x1, y, z, id, skip = () => false) {
  for (let x = x0; x <= x1; x++) if (!skip(x)) c.set(x, y, z, id)
}

/** jettyBay: a shop bay under a jetty: from x0 to x1 on line z, a stall board (`board`) at y, glass (`glass`) above it
 *  up to `top`, and the wall behind it (line z + back, toward the room) opened up so the bay is part of the room. */
export function jettyBay(c, x0, x1, y, top, z, back, { board, glass }) {
  for (let x = x0; x <= x1; x++) {
    c.set(x, y, z, board)
    for (let yy = y + 1; yy <= top; yy++) c.set(x, yy, z, glass)
    for (let yy = y; yy <= top; yy++) c.clear(x, yy, z + back)
  }
}

/** caskStack: storage casks (barrels lying with their lids toward `facing`) in a column `height` high at [x, y, z]. */
export function caskStack(c, [x, y, z], facing, height, cask) {
  for (let i = 0; i < height; i++) { c.free([x, y + i, z]); c.set(x, y + i, z, cask, { facing, open: 'false' }) }
}

