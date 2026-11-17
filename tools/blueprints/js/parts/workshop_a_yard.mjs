// Workshop yard parts (parts library, tools/blueprints/REBUILD.md; grown from the Builder's Workshop, designer A):
// a hoist beam out of a gable, a jetty's cantilevered beam on its brackets, and the builder's material stacks. Every
// block comes from the family's palette; none is picked here.
import { DIRS, OPP, slab, stair } from './canvas.mjs'

/** hoistBeam: a beam (`log`) run out of a wall at (x, y, z) `reach` blocks toward `out`, a bracket (an upside-down `bracket`
 *  stair) under its first outward cell, and from its tip a chain `drop` long ending in a hanging lantern (the hook).
 *  The wall cell itself becomes the beam's tail (it runs through the wall). */
export function hoistBeam(c, [x, y, z], out, { log, bracket, reach = 2, drop = 4 }) {
  const [dx, dz] = DIRS[out]
  const axis = dx ? 'x' : 'z'
  for (let i = 0; i <= reach; i++) c.set(x + dx * i, y, z + dz * i, log, { axis })
  stair(c, x + dx, y - 1, z + dz, bracket, OPP[out], 'top')
  const tx = x + dx * reach, tz = z + dz * reach
  for (let i = 1; i <= drop; i++) c.set(tx, y - i, tz, 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(tx, y - drop - 1, tz, 'lantern', { hanging: 'true', waterlogged: 'false' })
}

/** jettyBeam: the beam an upper storey stands on where it overhangs the wall below by one: logs along the overhang line
 *  (`axis` 'x': from a to b on line z = `line`; 'z': along z on line x) at y, with an upside-down stair bracket
 *  under it at each of `brackets` (facing back to the wall, `toward`). */
export function jettyBeam(c, axis, line, [a, b], y, log, bracket, brackets, toward) {
  for (let u = a; u <= b; u++) axis === 'x' ? c.set(u, y, line, log, { axis: 'x' }) : c.set(line, y, u, log, { axis: 'z' })
  for (const u of brackets) axis === 'x' ? stair(c, u, y - 1, line, bracket, toward, 'top') : stair(c, line, y - 1, u, bracket, toward, 'top')
}

/** materialStack: a builder's pile of stock on the ground at y: `cells` [[x, z, height], ...] each a column of
 *  `block` (props allowed) `height` high, topped by a bottom slab of `cap` when given, so a pile steps down to its
 *  edges instead of ending square. */
export function materialStack(c, cells, y, block, { props = {}, cap = null } = {}) {
  for (const [x, z, h] of cells) {
    for (let i = 0; i < h; i++) c.set(x, y + i, z, block, props)
    if (cap) slab(c, x, y + h, z, cap, 'bottom')
  }
}

/** steppedBreast: a chimney breast face of `stone` (may be a mix()) over cells [x, z] from y0 to `top`, each column
 *  capped at top + 1 by a `stair` rising toward `facing` (the stack), so a stack widens to the ground in a shoulder
 *  instead of standing on it like a pole. Draw it outward of an existing breast for a second, lower step. */
export function steppedBreast(c, cells, y0, top, stone, stairId, facing) {
  for (const [x, z] of cells) {
    for (let y = y0; y <= top; y++) c.set(x, y, z, stone)
    stair(c, x, top + 1, z, stairId, facing)
  }
}
