// Brickwork: the dressings of a brick house with stone trim (parts library, tools/blueprints/REBUILD.md; grown from the
// Noble's Manor, 49). Long-and-short quoins, a projecting plinth course, a moulded string course, mullioned (and
// transomed) windows with stone sills, kneelers at a parapet gable's foot, and a shallow bay window. Every block comes
// from the caller; none is picked here.
import { DIRS, OPP, slab, stair } from './canvas.mjs'

/** quoinsLS: long-and-short quoins up a corner [x, z] from y0 to y1: the corner stone every course, and one more stone
 *  beside it on alternate faces (course by course along `a` then `b`, each [dx, dz] pointing along a face away from
 *  the corner), so the corner reads as dressed blocks toothed into the brick. `id` may be a mix(). */
export function quoinsLS(c, [x, z], a, b, y0, y1, id) {
  for (let y = y0; y <= y1; y++) {
    c.set(x, y, z, id)
    const [dx, dz] = (y - y0) % 2 ? b : a
    c.set(x + dx, y, z + dz, id)
  }
}

/** plinthCourse: a projecting plinth one block out from a wall face at y (full blocks of `stone`, a mix() welcome):
 *  the cells [x, z] given (outside the wall), skipping any `skip(x, z)` (a step, a bed of soil). */
export function plinthCourse(c, cells, y, stone, skip = () => false) {
  for (const [x, z] of cells) if (!skip(x, z)) c.set(x, y, z, stone)
}

/** stringCourse out: a moulding one block out from a wall face at y, upside-down stairs with their backs to the wall
 *  (`toward`: the way the wall lies), so the floor line reads outside and upper sills have something to stand on.
 *  cells: [x, z] outside the wall. */
export function moulding(c, cells, y, stairs, toward, skip = () => false) {
  for (const [x, z] of cells) if (!skip(x, z)) stair(c, x, y, z, stairs, toward, 'top')
}

/** faceCells: the cells one block out from a straight wall face, from a to b along it: axis 'x' (the face is the wall
 *  z = line, `out` 'north' or 'south') or 'z' (the face x = line, `out` 'west' or 'east'). */
export function faceCells(axis, line, a, b, out) {
  const [ox, oz] = DIRS[out]
  const cells = []
  for (let u = Math.min(a, b); u <= Math.max(a, b); u++) cells.push(axis === 'x' ? [u + ox, line + oz] : [line + ox, u + oz])
  return cells
}

/** mullionWindow: a stone-framed window in a wall (axis 'x': the wall z = line; 'z': the wall x = line) whose lights
 *  are the columns `lights` (u values) from y0 to y1, every column between them a stone mullion; `transom` (a y) a
 *  stone bar across the whole opening; `head` a stone lintel course over it (y1 + 1) from the first to the last
 *  column; `sill` a stone stair upside-down one block out (`out`) under every column, its back to the wall. */
export function mullionWindow(c, axis, line, lights, y0, y1, out, { glass, mullion, head = null, sill = null, transom = null }) {
  const lo = Math.min(...lights), hi = Math.max(...lights)
  const at = (u, y) => (axis === 'x' ? [u, y, line] : [line, y, u])
  for (let u = lo; u <= hi; u++) for (let y = y0; y <= y1; y++) {
    const pane = lights.includes(u) && y !== transom
    c.set(...at(u, y), pane ? glass : mullion)
  }
  if (head) for (let u = lo; u <= hi; u++) c.set(...at(u, y1 + 1), head)
  if (sill) {
    const [ox, oz] = DIRS[out]
    for (let u = lo; u <= hi; u++) {
      const [x, y, z] = at(u, y0 - 1)
      stair(c, x + ox, y, z + oz, sill, OPP[out], 'top')
    }
  }
}

/** kneeler: the stone at a parapet gable's foot, where the roof's overhang meets the gable: a dressed block at [x, y,
 *  z] on an upside-down stair corbel under it (its back to the wall, `toward`). */
export function kneeler(c, [x, y, z], stone, corbel, toward) {
  c.set(x, y, z, stone)
  stair(c, x, y - 1, z, corbel, toward, 'top')
}

/** bayWindow: a shallow rectangular bay one block out from a wall (axis 'x': the wall z = line, the bay on the `out`
 *  side, spanning a..b): stone piers at its two ends from y0 to top, an apron of `apron` under the glass, `glass` from
 *  g0 to g1 across a + 1 .. b - 1, a stone lintel at top, and a cap of `cap` slabs over it (top + 1). The wall behind
 *  the glass is opened from y0 to g1 so the room runs into the bay. */
export function bayWindow(c, axis, line, [a, b], out, { y0, g0, g1, top }, { pier, apron, glass, lintel, cap }) {
  const [ox, oz] = DIRS[out]
  const at = (u, y, d) => (axis === 'x' ? [u + ox * d, y, line + oz * d] : [line + ox * d, y, u + oz * d])
  for (let y = y0; y <= top; y++) { c.set(...at(a, y, 1), pier); c.set(...at(b, y, 1), pier) }
  for (let u = a + 1; u < b; u++) {
    for (let y = y0; y < g0; y++) c.set(...at(u, y, 1), apron)
    for (let y = g0; y <= g1; y++) c.set(...at(u, y, 1), glass)
    for (let y = g1 + 1; y <= top; y++) c.set(...at(u, y, 1), lintel)
    for (let y = y0 + 1; y <= g1; y++) c.clear(...at(u, y, 0))
  }
  for (let u = a; u <= b; u++) slab(c, ...at(u, top + 1, 1), cap, 'bottom')
}
