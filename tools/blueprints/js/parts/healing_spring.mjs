// The Spring Infirmary's parts (the Healing Center, ROADMAP 49): roofs of several 45-degree gables joined into one
// (a T or an L of ridges, valleys where they meet), gables filled up to such a roof, a glazed roof lantern with a
// stepped copper cap, a cross-shaped gable window, a rimmed spring pool. Every block comes from the caller's palette.
import { stair } from './canvas.mjs'

/** unionGableRoof: several 45-degree gable roofs drawn as one surface. Each mass is {axis, across: [a0, a1], along:
 *  [l0, l1], eaveY} (axis 'z': the ridge runs along z over the middle of x a0..a1; 'x': along x over z a0..a1). Each
 *  column (x, z) takes the highest mass over it (the first listed on a tie): its ridge cell a full `roof.full`, any
 *  other a `roof.stairs` rising toward that mass's ridge, so a wing's ridge dies into the main slope in two valleys
 *  (kit.finish turns the valley stairs). `keep(x, y, z)` as in gableRoof. Returns h(x, z): the roof's y over a column
 *  (null where no mass is), for gables, lanterns and trim. */
export function unionGableRoof(c, masses, roof, keep = () => true) {
  const one = (m, x, z) => {
    const [u, v] = m.axis === 'z' ? [x, z] : [z, x]
    if (u < m.across[0] || u > m.across[1] || v < m.along[0] || v > m.along[1]) return null
    return m.eaveY + Math.min(u - m.across[0], m.across[1] - u)
  }
  const top = (x, z) => {
    let best = null, by = null
    for (const m of masses) {
      const y = one(m, x, z)
      if (y !== null && (best === null || y > best)) { best = y; by = m }
    }
    return [best, by]
  }
  const [w, , d] = c.size
  for (let x = 0; x < w; x++) for (let z = 0; z < d; z++) {
    const [y, m] = top(x, z)
    if (y === null || !keep(x, y, z)) continue
    const u = m.axis === 'z' ? x : z
    const mid = (m.across[0] + m.across[1]) / 2
    if (u === mid) { c.set(x, y, z, roof.full); continue }
    const rise = m.axis === 'z' ? (u < mid ? 'east' : 'west') : (u < mid ? 'south' : 'north')
    stair(c, x, y, z, roof.stairs, rise)
  }
  return (x, z) => top(x, z)[0]
}

/** gableFill: a gable (or any wall) in the cells [x, z] from y0 up to just under the roof h(x, z), `pick(x, y, z)`
 *  choosing each block (an id, [id, props], or null to leave it). */
export function gableFill(c, cells, y0, h, pick) {
  for (const [x, z] of cells) {
    const top = h(x, z)
    if (top === null) continue
    for (let y = y0; y < top; y++) {
      const got = pick(x, y, z)
      if (!got) continue
      const [id, props] = Array.isArray(got) ? got : [got, {}]
      c.set(x, y, z, id, props)
    }
  }
}

/** glazedLantern: a three-by-three glazed lantern astride a ridge, centred on (cx, cz), its glass from y0 (the ridge's
 *  layer) two high: `post` blocks at the corners, `glass` panes in the middle of each face, the middle cell open down
 *  into the room under it; a low pyramid cap of `cap` ({full, stairs}) on it, a ring of stairs round a full block, and
 *  a `finial` on top. (No brim wider than the body: the kit would seal the slot under it down to the roof.) Returns
 *  the cap's middle cell [cx, y0 + 2, cz] (hang the room's lamp from it). */
export function glazedLantern(c, cx, cz, y0, { post, glass, cap, finial = null }) {
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) for (let y = y0; y <= y0 + 1; y++) {
    const x = cx + dx, z = cz + dz
    if (dx && dz) c.set(x, y, z, post, post.endsWith('_log') ? { axis: 'y' } : {})
    else if (dx || dz) c.set(x, y, z, glass)
    else c.clear(x, y, z)
  }
  const inward = (dx, dz) => (Math.abs(dx) >= Math.abs(dz) ? (dx < 0 ? 'east' : 'west') : (dz < 0 ? 'south' : 'north'))
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) {
    if (dx || dz) stair(c, cx + dx, y0 + 2, cz + dz, cap.stairs, inward(dx, dz))
    else c.set(cx, y0 + 2, cz, cap.full)
  }
  if (finial) c.set(cx, y0 + 3, cz, finial, finial.endsWith('lightning_rod') ? { facing: 'up', powered: 'false', waterlogged: 'false' } : {})
  return [cx, y0 + 2, cz]
}

/** crossWindow: a cross of `glass` panes in a wall plane (axis 'z': the plane z = line, the cross centred on x = cu;
 *  'x': the plane x = line, centred on z = cu), centred at height cy, arms one long: the window every gable of the
 *  Spring Infirmary shows, lit from inside at night. */
export function crossWindow(c, axis, line, cu, cy, glass) {
  for (const [du, dy] of [[0, 0], [-1, 0], [1, 0], [0, -1], [0, 1]]) {
    const [x, z] = axis === 'z' ? [cu + du, line] : [line, cu + du]
    c.set(x, cy + dy, z, glass)
  }
}

/** springPool: a pool of still water at y over x0..x1, z0..z1: a ring of `rim` (a block, slab or mix()) round its
 *  edge and `water` sources inside, standing on whatever floor is under them. Returns the water's cells. */
export function springPool(c, x0, z0, x1, z1, y, rim, water = 'water') {
  const cells = []
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const edge = x === x0 || x === x1 || z === z0 || z === z1
    if (edge) {
      if (typeof rim === 'string' && rim.endsWith('_slab')) c.set(x, y, z, rim, { type: 'bottom', waterlogged: 'false' })
      else c.set(x, y, z, rim)
    } else { c.set(x, y, z, water, { level: '0' }); cells.push([x, y, z]) }
  }
  return cells
}

/** framedGableWindow: one window in a gable, the way a gable takes it whole: `glass` panes in a row (u0..u1) at y in a
 *  wall plane (axis 'z': the plane z = line, u is x; 'x': the plane x = line, u is z), a lintel of `frame` logs over
 *  it, a king post of `frame` from the lintel's middle up to the roof (`h(x, z)`: the roof's y over a column), and a
 *  ledge of `sill` (a slab id, laid top-half) one block out (`out`) under it. */
export function framedGableWindow(c, axis, line, u0, u1, y, out, { glass, frame, sill, h }) {
  const at = u => (axis === 'z' ? [u, line] : [line, u])
  const [ox, oz] = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }[out]
  for (let u = u0; u <= u1; u++) {
    const [x, z] = at(u)
    c.set(x, y, z, glass)
    c.set(x, y + 1, z, frame, { axis: axis === 'z' ? 'x' : 'z' })
    c.set(x + ox, y - 1, z + oz, sill, { type: 'top', waterlogged: 'false' })
  }
  const [mx, mz] = at((u0 + u1) / 2)
  for (let yy = y + 2; yy < h(mx, mz); yy++) c.set(mx, yy, mz, frame, { axis: 'y' })
}
