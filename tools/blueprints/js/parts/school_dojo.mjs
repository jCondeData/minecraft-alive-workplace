// School parts (the Move Tutor's School, 49 A): a sparring ring inlaid in a yard's paving, a round "moon" window in a
// gable, and a straw practice target (parts library, tools/blueprints/REBUILD.md). Every block comes from the caller.
import { DIRS } from './canvas.mjs'

/** sparringRing: a round ring inlaid in a floor at y, centred on (cx, cz): `inner` inside radius r, a `border` band one
 *  block wide round it, `paving` (an id or a mix()) for the rest of the box x0..x1, z0..z1. Only floor blocks: a ring
 *  reads from above and from the cloisters without getting in anyone's way. */
export function sparringRing(c, { cx, cz, r, y = 0, x0, x1, z0, z1 }, { inner, border, paving }) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const d = (x - cx) ** 2 + (z - cz) ** 2
    const id = d <= r * r + 1 ? inner : d <= (r + 1) ** 2 + 1 ? border : paving
    c.set(x, y, z, id)
  }
}

/** moonWindow: a round window in a gable or wall face (axis 'z': the face runs along x at z = line; 'x': along z at
 *  x = line), centred on (cu, cy): a cross of five panes framed by `frame` blocks on its diagonals, so at this scale
 *  it reads as a circle. Draw it after the wall it sits in. */
export function moonWindow(c, axis, line, cu, cy, { glass, frame }) {
  const at = (u, y, id, props = {}) => {
    const [x, z] = axis === 'z' ? [u, line] : [line, u]
    c.set(x, y, z, id, props)
  }
  for (const [du, dy] of [[0, 0], [1, 0], [-1, 0], [0, 1], [0, -1]]) at(cu + du, cy + dy, glass)
  for (const [du, dy] of [[1, 1], [-1, 1], [1, -1], [-1, -1]]) at(cu + du, cy + dy, frame)
}

/** strawTarget: a practice target in a cloister or yard: `bale` (hay) stacked `height` high with a `face` block
 *  (a target) on top, and a `post` (a fence) beside it on the `side` it leans on. */
export function strawTarget(c, [x, y, z], { bale, face, post, height = 1, side = null }) {
  for (let i = 0; i < height; i++) c.set(x, y + i, z, bale, { axis: 'y' })
  c.set(x, y + height, z, face, { power: '0' })
  if (side) { const [dx, dz] = DIRS[side]; c.set(x + dx, y, z + dz, post) }
}
