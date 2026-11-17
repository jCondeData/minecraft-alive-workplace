// Carpenter parts (parts library, tools/blueprints/REBUILD.md; grown from the Carpenter's Workshop, 49): a cruck truss
// (the paired curved blades that carry a steep roof, seen on a gable or standing inside a loft), its stone feet, and a
// sawhorse. Every block comes from the family's palette; none is picked here.
import { stair } from './canvas.mjs'

/** cruckTruss: a cruck truss in the plane z = `line` (the roof's ridge runs along z) under a gableRoof's `under`, across
 *  the span a0..a1 (the eave columns; the blades rise from the wall posts on a0 + 1 and a1 - 1): each blade is the
 *  two cells just under the roof in each column from the wall post to the ridge, so the pair meets at the apex like two
 *  curved timbers; a collar beam ties them at `collarY` when given. Cells below `y0` are left (the wall and its posts).
 *  Returns isCruck(u, y), for a gableWall's pick to leave the blades' cells alone. Draw it before the gable's infill. */
export function cruckTruss(c, line, [a0, a1], under, y0, log, { collarY = null } = {}) {
  const cells = new Set()
  for (let u = a0 + 2; u <= a1 - 2; u++) {
    const top = under[u] - 1
    for (let y = Math.max(y0, top - 1); y <= top; y++) {
      c.set(u, y, line, log, { axis: 'y' })
      cells.add(`${u},${y}`)
    }
  }
  if (collarY !== null) {
    for (let u = a0 + 2; u <= a1 - 2; u++) {
      if (cells.has(`${u},${collarY}`) || under[u] - 1 < collarY) continue
      c.set(u, collarY, line, log, { axis: 'x' })
      cells.add(`${u},${collarY}`)
    }
  }
  return (u, y) => cells.has(`${u},${y}`)
}

/** cruckFoot: where a cruck blade meets the ground outside the wall, a stone step (`stairs`) leaning on the post's
 *  foot (`toward` the post), so the blade reads as rising out of a pad rather than standing in a box. */
export function cruckFoot(c, [x, y, z], stairs, toward) { stair(c, x, y, z, stairs, toward) }

/** sawhorse: two legs (`leg`, a fence id) at a and b (same y, along x or z) with a beam (`beam`, a log) laid across
 *  their tops from a to b: the trestle a carpenter saws boards on. */
export function sawhorse(c, [ax, y, az], [bx, , bz], leg, beam) {
  const axis = ax !== bx ? 'x' : 'z'
  c.set(ax, y, az, leg); c.set(bx, y, bz, leg)
  for (let x = Math.min(ax, bx); x <= Math.max(ax, bx); x++)
    for (let z = Math.min(az, bz); z <= Math.max(az, bz); z++) c.set(x, y + 1, z, beam, { axis })
}
