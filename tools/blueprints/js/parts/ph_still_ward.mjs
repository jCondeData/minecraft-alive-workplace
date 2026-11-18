// Still-rooms and wards (parts library, tools/blueprints/REBUILD.md; grown from the Physician's House, 49): a hollow
// pyramid cap over a square tower, linen screens between ward beds, tie beams across a room, the brewing stand.
// Every block comes from the caller.
import { stair } from './canvas.mjs'

/** hollowPyramid: a 45-degree four-sided cap over a square tower centred on (cx, cz), hollow inside (cheap to build,
 *  open to the room under it): a ring of `roof.stairs` r out from the centre at y0 (the overhang when the walls are
 *  r - 1 out), each next ring one in and one up, and behind every ring of stairs a ring of `roof.full` one in at the
 *  same height so no gap shows over a stair. Stops at radius `top` (whose square, at y0 + r - top, the caller caps:
 *  a louvre, a finial, a full block). `keep(x, y, z)` returns false for a cell left out (e.g. an overhang that would
 *  hang over a neighbour's roof). Returns that cap's y. */
export function hollowPyramid(c, cx, cz, r, y0, roof, top = 0, keep = () => true) {
  const face = (dx, dz) => (Math.abs(dx) >= Math.abs(dz) ? (dx < 0 ? 'east' : 'west') : (dz < 0 ? 'south' : 'north'))
  const ring = (rr, fn) => {
    for (let dx = -rr; dx <= rr; dx++) for (let dz = -rr; dz <= rr; dz++)
      if (Math.max(Math.abs(dx), Math.abs(dz)) === rr) fn(dx, dz)
  }
  for (let k = 0; r - k > top; k++) {
    const rr = r - k, y = y0 + k
    ring(rr, (dx, dz) => { if (keep(cx + dx, y, cz + dz)) stair(c, cx + dx, y, cz + dz, roof.stairs, face(dx, dz)) })
    if (rr - 1 > 0) ring(rr - 1, (dx, dz) => { if (keep(cx + dx, y, cz + dz)) c.set(cx + dx, y, cz + dz, roof.full) })
    else if (keep(cx, y, cz)) c.set(cx, y, cz, roof.full)
  }
  return y0 + r - top
}

/** wardScreens: linen screens between ward beds: `cloth` (a wool id) from x0 to x1 on line z, y0..y1 high (each
 *  {x0, x1, z}); the beds stand in the bays between them, one screen north and one south of every bed. */
export function wardScreens(c, screens, cloth, y0 = 1, y1 = 2) {
  for (const { x0, x1, z } of screens)
    for (let x = x0; x <= x1; x++) for (let y = y0; y <= y1; y++) c.set(x, y, z, cloth)
}

/** bayTies: logs across a room from x0 to x1 at y on each line z of `zs` (axis x), the bay lines a lamp or a screen
 *  hangs from. */
export function bayTies(c, zs, x0, x1, y, log) {
  for (const z of zs) for (let x = x0; x <= x1; x++) c.set(x, y, z, log, { axis: 'x' })
}

/** brewingStand: a brewing stand with no bottles (a fixture: the Cleric's and the Physician's job site). */
export function brewingStand(c, [x, y, z]) {
  c.free([x, y, z])
  c.set(x, y, z, 'brewing_stand', { has_bottle_0: 'false', has_bottle_1: 'false', has_bottle_2: 'false' })
}

/** recessedWindow: a window set a block into a one-block wall: the wall's cells (`cells`) opened and the `glass` put
 *  in one block further in (away from `out`), so the reveal throws a shadow on a flat stone face. The cells behind must
 *  be free (an open shaft, not a room's floor cell). */
export function recessedWindow(c, cells, out, glass) {
  const [ox, oz] = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }[out]
  for (const [x, y, z] of cells) {
    c.free([x - ox, y, z - oz])
    c.clear(x, y, z)
    c.set(x - ox, y, z - oz, glass)
  }
}
