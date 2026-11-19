// Granary parts (the Terminal Nook, 49): a store lifted on staddle stones, a masonry flight along a front, stores that
// never pair into double chests, and one drawing that comes out twice (a vanilla build and a modded copy of it, cell
// for cell). Every block comes from the caller's palette; nothing here picks one itself.
import { stair } from './canvas.mjs'

/** staddle: a staddle stone at [x, z] from y: a stone stem (`stem`, a wall id reads as a tapered post) under a wide
 *  flat cap (`cap`, a slab id, laid top-half so the floor beam sits on it), the mushroom that keeps rats from a store. */
export function staddle(c, x, z, { stem, cap }, y = 0) {
  c.set(x, y, z, stem)
  c.set(x, y + 1, z, cap, { type: 'top', waterlogged: 'false' })
}

/** sideFlight: a masonry flight climbing along a wall's face (not into it): `n` steps of `steps` (a stairs id) from
 *  [x, y, z], each one up and one along `rises`, solid `under` beneath each down to y. Returns the cell after the
 *  last step (where its landing goes). */
export function sideFlight(c, [x, y, z], n, rises, { steps, under }) {
  const d = { east: [1, 0], west: [-1, 0], south: [0, 1], north: [0, -1] }[rises]
  for (let i = 0; i < n; i++) {
    const sx = x + d[0] * i, sy = y + i, sz = z + d[1] * i
    stair(c, sx, sy, sz, steps, rises)
    for (let yy = y; yy < sy; yy++) c.set(sx, yy, sz, under)
  }
  return [x + d[0] * n, y + n, z + d[1] * n]
}

/** storeStack: stores stacked at [x, z] on each of `ys`, facing `facing` (into the room). A chest is always a single
 *  chest (a stack never pairs, so each one is its own store, and a modded network counts it once); a barrel lies with
 *  its lid toward `facing`. Returns the cells. */
export function storeStack(c, [x, z], ys, facing, store) {
  const barrel = store.endsWith('barrel')
  for (const y of ys) c.set(x, y, z, store, barrel ? { facing, open: 'false' } : { facing, type: 'single', waterlogged: 'false' })
  return ys.map(y => [x, y, z])
}

/** twin: one cell drawn two ways from one plan: the `modded` block (id, props) when `on`, else the vanilla one. A
 *  modded copy keeps the vanilla build's shape (a block only where the vanilla build has one), so both are given. */
export function twin(c, [x, y, z], on, [vid, vprops = {}], [mid, mprops = {}]) {
  if (on) c.set(x, y, z, mid, mprops)
  else c.set(x, y, z, vid, vprops)
}
