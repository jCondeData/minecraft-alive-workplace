// Mason's parts (parts library, tools/blueprints/REBUILD.md; grown from the Mason's Yard, 49): a jib crane over a
// yard, a dressed gate pier with its lamp, and a banker shed's purlin (the deep beam under a lean-to's slope that
// keeps the shed shallow and shades the bankers). Every block comes from the family's palette; none is picked here.
import { DIRS, OPP, stair } from './canvas.mjs'

/** masonJibCrane: a yard crane: a `mast` (a log) on the ground at [x, y, z] `height` tall, a `jib` (a log) out of its
 *  top `reach` blocks toward `out`, a `brace` (an upside-down stair) under the jib's first cell, and from the jib's tip
 *  a chain `drop` links long ending in a hanging lantern (the hook). With `load`, a block of stock stands on the ground
 *  under the hook, slung and waiting to be lifted. */
export function masonJibCrane(c, [x, y, z], out, { mast, jib, brace, height = 5, reach = 2, drop = 1, load = null }) {
  const [dx, dz] = DIRS[out]
  const top = y + height - 1
  for (let yy = y; yy <= top; yy++) c.set(x, yy, z, mast, { axis: 'y' })
  const axis = dx ? 'x' : 'z'
  for (let i = 1; i <= reach; i++) c.set(x + dx * i, top, z + dz * i, jib, { axis })
  stair(c, x + dx, top - 1, z + dz, brace, OPP[out], 'top')
  const tx = x + dx * reach, tz = z + dz * reach
  for (let i = 1; i <= drop; i++) c.set(tx, top - i, tz, 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(tx, top - drop - 1, tz, 'lantern', { hanging: 'true', waterlogged: 'false' })
  if (load) c.set(tx, y, tz, load)
  return [tx, tz]
}

/** masonLampPier: a gate pier at [x, y, z]: `height` courses of dressed `pier` stone, a `post` (a wall or fence id) on
 *  top and a lantern standing on the post (a lamp on a post, owner rule 2). */
export function masonLampPier(c, [x, y, z], { pier, post, height = 1 }) {
  for (let i = 0; i < height; i++) c.set(x, y + i, z, pier)
  c.set(x, y + height, z, post)
  c.set(x, y + height + 1, z, 'lantern', { hanging: 'false', waterlogged: 'false' })
}

/** masonPurlin: the deep beam along a lean-to's back under its slope: a `log` beam on line (`axis` 'x': z = `line`,
 *  from a to b) at y, and `soffit` boards above it up to (not into) the roof at y + 1. It closes the high, dark back of
 *  an open shed, so the shed stays a shallow porch (holes.py's `open` check) and its bankers stand in light. */
export function masonPurlin(c, axis, line, [a, b], y, log, soffit) {
  for (let u = a; u <= b; u++) {
    const [x, z] = axis === 'x' ? [u, line] : [line, u]
    c.set(x, y, z, log, { axis })
    if (soffit) c.set(x, y + 1, z, soffit)
  }
}

/** masonButtress: a stepped buttress against a wall at [x, z]: `stone` (may be a mix()) from y0 to `top`, capped at
 *  top + 1 by a `capStair` rising toward the wall (`toward`), so the back of a long stone wall gets bays and shadow. */
export function masonButtress(c, x, z, y0, top, stone, capStair, toward) {
  for (let y = y0; y <= top; y++) c.set(x, y, z, stone)
  stair(c, x, top + 1, z, capStair, toward)
}
