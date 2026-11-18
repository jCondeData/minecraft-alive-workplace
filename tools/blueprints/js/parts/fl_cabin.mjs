// Log cabins (parts library, tools/blueprints/REBUILD.md): a pen of round logs laid in courses, notched at the corners,
// and a little gable on posts over a porch's mouth. Grown from the Fletcher's Lodge (49), a dogtrot of two pens. Every
// block comes from the caller's palette.
import { stair } from './canvas.mjs'

/** logPen: the four walls of a log pen round x0..x1, z0..z1 from y0 to y1, each course of `log` lying along its wall
 *  (axis x on the front and back, z on the sides); the corner log alternates course by course between the two walls'
 *  axes, so the corners read as notched, the way a pen's logs cross. `skip(x, y, z)` leaves a cell (a door, a window,
 *  a hearth) for the caller. */
export function logPen(c, { x0, z0, x1, z1, y0, y1 }, log, skip = () => false) {
  for (let y = y0; y <= y1; y++)
    for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
      const side = x === x0 || x === x1, end = z === z0 || z === z1
      if (!side && !end) continue
      if (skip(x, y, z)) continue
      const axis = side && end ? ((y - y0) % 2 ? 'z' : 'x') : end ? 'x' : 'z'
      c.set(x, y, z, log, { axis })
    }
}

/** porchGable: a small 45-degree gable roof whose ridge runs from the front (z0) back into the main slope (z1), over a
 *  porch's mouth centred on cx, `half` columns either side, its eave at y. Its face at z0 stands on a `beam` (a log
 *  along x between the eave stairs) with `face` (an id, or [id, props]) in the pediment's cells over the beam. Cells
 *  the caller's walls own are kept (`keep(x, y, z)` false); a main roof's stairs under it are overdrawn. Returns the
 *  ridge's y. */
export function porchGable(c, { cx, half, y, z0, z1 }, roof, { beam, face }, keep = () => true) {
  for (let z = z0; z <= z1; z++) {
    for (let i = 0; i < half; i++) {
      for (const [x, rise] of [[cx - half + i, 'east'], [cx + half - i, 'west']])
        if (keep(x, y + i, z)) stair(c, x, y + i, z, roof.stairs, rise)
    }
    if (keep(cx, y + half, z)) c.set(cx, y + half, z, roof.full)
  }
  for (let i = 1; i < half; i++) for (let z = z0 + 1; z <= z1; z++) // close the pocket behind the pediment
    for (let x = cx - half + 1 + i; x <= cx + half - 1 - i; x++) if (!c.get(x, y + i, z)) c.set(x, y + i, z, roof.full)
  for (let x = cx - half + 1; x <= cx + half - 1; x++) c.set(x, y, z0, beam, { axis: 'x' })
  for (let i = 1; i < half; i++) for (let x = cx - half + 1 + i; x <= cx + half - 1 - i; x++) {
    const [id, props] = Array.isArray(face) ? face : [face, {}]
    c.set(x, y + i, z0, id, props)
  }
  return y + half
}
