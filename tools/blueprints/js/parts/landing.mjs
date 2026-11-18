// Landings (parts library, tools/blueprints/REBUILD.md): the pieces of a ferry or station platform: a lean-to canopy
// on posts with brackets and a fringe of boards along its eave (a station's valance), and a signal mast with a yard,
// a masthead light and a pennant. Grown from the Ferry House (49, the Ferry Station). Every block comes from the
// family's palette, passed in.
import { stair } from './canvas.mjs'
import { brackets } from './frames.mjs'

/** platformCanopy: a lean-to canopy along a wall that faces north (the wall's line z = wallZ, the canopy over the
 *  rows wallZ - depth .. wallZ - 1, x0..x1), its roof `roof.stairs` rising toward the wall a block a row from `eaveY` at
 *  the front row; posts (`post`, a log) under the front row at each x of `posts` from y = 1 to eaveY - 1, an
 *  upside-down stair bracket (`bracket`, null for none) either side of each post under the eave, and a valance: an open trapdoor of
 *  `valance` (a wood) hanging flush with the eave's outer edge in every other front cell (`every`: 1 for a solid fringe, 2 for a dagger
 *  rhythm). Returns the front row's z. */
export function platformCanopy(c, { x0, x1, wallZ, depth = 3, eaveY = 4, posts = [] }, { roof, post, bracket, valance, every = 1 }) {
  const front = wallZ - depth
  for (let i = 0; i < depth; i++)
    for (let x = x0; x <= x1; x++) stair(c, x, eaveY + i, front + i, roof.stairs, 'south')
  for (const x of posts) for (let y = 1; y < eaveY; y++) c.set(x, y, front, post, { axis: 'y' })
  const taken = new Set(posts)
  if (bracket) for (const x of posts) for (const d of [-1, 1]) {
    if (x + d < x0 || x + d > x1 || taken.has(x + d)) continue
    brackets(c, [[x + d, eaveY - 1, front]], bracket, d < 0 ? 'east' : 'west')
    taken.add(x + d)
  }
  if (valance) for (let x = x0; x <= x1; x++) {
    if (taken.has(x) || (x - x0) % every) continue
    c.set(x, eaveY - 1, front, `${valance}_trapdoor`, { facing: 'south', half: 'top', open: 'true', powered: 'false', waterlogged: 'false' })
  }
  return front
}

/** mast: a signal mast standing at (x, y0, z) up to y1 (`log`), a yard (`yard`, a fence id) at `yardY` running
 *  `yardTo` cells along [dx, dz] with a lantern hung from its far end on `chain` links, a lantern standing on the
 *  masthead (a light seen across the water), and a pennant (a wall banner of colour `flag`) on the `flagFacing` side
 *  just under the top. */
export function mast(c, [x, y0, z], y1, { log, yard, yardY, yardTo = 2, yardDir = [1, 0], chain = 0, flag = null, flagFacing = 'east' }) {
  for (let y = y0; y <= y1; y++) c.set(x, y, z, log, { axis: 'y' })
  const [dx, dz] = yardDir
  for (let i = 1; i <= yardTo; i++) c.set(x + dx * i, yardY, z + dz * i, yard)
  const ex = x + dx * yardTo, ez = z + dz * yardTo
  for (let i = 1; i <= chain; i++) c.set(ex, yardY - i, ez, 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(ex, yardY - chain - 1, ez, 'lantern', { hanging: 'true', waterlogged: 'false' })
  c.set(x, y1 + 1, z, 'lantern', { hanging: 'false', waterlogged: 'false' })
  if (flag) {
    const off = { east: [1, 0], west: [-1, 0], north: [0, -1], south: [0, 1] }[flagFacing]
    c.set(x + off[0], y1 - 1, z + off[1], `${flag}_wall_banner`, { facing: flagFacing })
  }
}
