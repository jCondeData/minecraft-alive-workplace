// Trail marks and things left along a road (parts library, tools/blueprints/REBUILD.md; grown from the Trail Marks,
// story/trail_1..5, ROADMAP 49): a whole ground layer drawn cell by cell, wheel ruts, a post with cloth rags tied near
// its top, a burst barrel with its staves, arrows (hooks) stuck in a block's sides, and a canvas cover thrown over its
// hoop. Every block comes from the caller's palette; none is picked here.
import { DIRS, trapdoor } from './canvas.mjs'

/** trailGround: the whole y = `y` layer of a w x d drawing (a template whose y 0 replaces the world's top ground),
 *  every cell drawn: `pick(x, z, over)` returns the block id (or [id, props]) for that cell, `over` being what stands
 *  on it (the cell above, or undefined). Cells already drawn at y (a sunk wheel, a stand) are kept. */
export function trailGround(c, w, d, pick, y = 0) {
  for (let x = 0; x < w; x++) for (let z = 0; z < d; z++) {
    if (c.get(x, y, z)) continue
    const got = pick(x, z, c.get(x, y + 1, z))
    if (!got) continue
    const [id, props] = Array.isArray(got) ? got : [got, {}]
    c.set(x, y, z, id, props)
  }
}

/** wheelRuts: the cells of two wheel ruts `gauge` apart, along `axis` ('z': the ruts run in z at x = a and a + gauge,
 *  from l0 to l1; 'x': they run in x at z = a and a + gauge). `skip(x, z)` leaves a cell out (a break in the rut).
 *  Returns [[x, z], ...]: the caller lays its rut block there (a path block only under nothing solid). */
export function wheelRuts({ axis = 'z', a, gauge = 2, along: [l0, l1] }, skip = () => false) {
  const out = []
  for (let v = l0; v <= l1; v++) for (const u of [a, a + gauge]) {
    const [x, z] = axis === 'z' ? [u, v] : [v, u]
    if (!skip(x, z)) out.push([x, z])
  }
  return out
}

/** ragPost: a post at [x, y, z] (`post` upright, `height` blocks) with a `tip` (a fence splinter, or null) on top and
 *  cloth `rag`s (a wall banner id) tied on the faces named in `rags` (['north', 'east', ...]) at `ragY` (default its
 *  top block). A wall banner faces away from the block it hangs on, so each rag sits in the cell beside the post. */
export function ragPost(c, [x, y, z], { height, ragY = null, rags = [], tip = 1 }, { post, fence = null, rag }) {
  for (let i = 0; i < height; i++) c.set(x, y + i, z, post, { axis: 'y' })
  if (fence) for (let i = 0; i < tip; i++) c.set(x, y + height + i, z, fence)
  const ry = ragY ?? y + height - 1
  for (const f of rags) {
    const [dx, dz] = DIRS[f]
    c.set(x + dx, ry, z + dz, rag, { facing: f })
  }
}

/** burstBarrel: a `barrel` at [x, y, z] standing open to the sky, and its loose staves round it: `staves` lists
 *  [dx, dz, facing, open] for each (a `stave` trapdoor: open = standing on edge, shut = lying flat on the ground). */
export function burstBarrel(c, [x, y, z], staves, { barrel, stave }) {
  c.set(x, y, z, barrel, { facing: 'up', open: 'true' })
  for (const [dx, dz, facing, open] of staves) trapdoor(c, x + dx, y, z + dz, stave, facing, 'bottom', open)
}

/** arrowStuds: arrows stuck in the sides of the block at [x, y, z]: a `hook` (a tripwire hook, which reads as an
 *  arrow's shaft and fletching) on each face named in `faces`, each facing away from the block it is stuck in. */
export function arrowStuds(c, [x, y, z], faces, hook) {
  for (const f of faces) {
    const [dx, dz] = DIRS[f]
    c.set(x + dx, y, z + dz, hook, { facing: f, attached: 'false', powered: 'false' })
  }
}

/** coverHoop: a wagon cover's hoop standing across x from x0 to x1 at z: a `rib` (fence) up each side from y0 to
 *  top - 1, and the `cloth` over the top at y = top from x0 to x1 (the canvas laid on the hoop). Then the canvas
 *  slumps away toward `slump` ('south' or 'north'): each row a block lower and two deep, so every row hangs by a face
 *  from the one before (`steps` rows; the last runs down to `foot`, the canvas's hem on the ground, when given). The
 *  cells `torn(x, y, z)` says are missing are left out (the hoop's own top row is never torn). Returns the cloth cells. */
export function coverHoop(c, { x0, x1, z, y0, top, slump = 'south', steps = 2, foot = null }, { rib, cloth }, torn = () => false) {
  for (let y = y0; y < top; y++) { c.set(x0, y, z, rib); c.set(x1, y, z, rib) }
  const out = []
  const dz = slump === 'south' ? 1 : -1
  for (let i = 0; i <= steps; i++) for (let x = x0; x <= x1; x++) {
    const cz = z + i * dz
    const lo = i === steps && foot !== null ? foot : top - i, hi = i === 0 ? top : top - i + 1
    for (let cy = hi; cy >= lo; cy--) {
      if (i > 0 && torn(x, cy, cz)) continue
      c.set(x, cy, cz, cloth); out.push([x, cy, cz])
    }
  }
  return out
}
