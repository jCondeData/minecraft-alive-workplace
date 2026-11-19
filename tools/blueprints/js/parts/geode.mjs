// Geodes (parts library, tools/blueprints/REBUILD.md; grown from the Gem Grotto, "the Split Geode", 49): round shells
// made of egg-shaped bodies (ellipsoids, one or several fused), worked out in layers from the outside in the way a
// geode is (crust, then its middle band, then the crystal lining), a crystal spire breaking out of a crust, and a
// basin that holds a liquid in a stone kerb behind a window. Every block comes from the caller's palette.
import { DIRS } from './canvas.mjs'

/** eggCells: the cells [x, y, z] (y >= 0) of the union of `eggs`, each { c: [cx, cy, cz], r: [rx, ry, rz] }, inside
 *  the canvas. A cell is in an egg when its centre's normalised distance is at most 1. */
export function eggCells(c, eggs) {
  const [w, h, d] = c.size
  const out = []
  for (let x = 0; x < w; x++) for (let y = 0; y < h; y++) for (let z = 0; z < d; z++)
    if (eggs.some(({ c: [cx, cy, cz], r: [rx, ry, rz] }) => ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2 <= 1))
      out.push([x, y, z])
  return out
}

/** geodeLayers: for the solid body `cells` (from eggCells), each cell's layer counted from the outside (1 = the crust
 *  cell touching open air, 2 the one behind it, ...), by a six-way walk. Cells at y = 0 are the floor: they never
 *  count as open air and get layer 0. `sealed(x, y, z)`: cells that never count as open air either, solid or not (a
 *  slice taken off later: the cut face then shows the shell's layers in section). Returns a Map 'x,y,z' -> layer. */
export function geodeLayers(cells, sealed = () => false) {
  const key = (x, y, z) => `${x},${y},${z}`
  const solid = new Set(cells.map(p => key(...p)))
  const layer = new Map()
  let front = []
  for (const [x, y, z] of cells) {
    if (y === 0) { layer.set(key(x, y, z), 0); continue }
    for (const [dx, dy, dz] of [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]]) {
      const ny = y + dy
      if (ny < 0 || sealed(x + dx, ny, z + dz)) continue
      if (!solid.has(key(x + dx, ny, z + dz))) { layer.set(key(x, y, z), 1); front.push([x, y, z]); break }
    }
  }
  for (let n = 2; front.length; n++) {
    const next = []
    for (const [x, y, z] of front)
      for (const [dx, dy, dz] of [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]]) {
        const k = key(x + dx, y + dy, z + dz)
        if (solid.has(k) && !layer.has(k)) { layer.set(k, n); next.push([x + dx, y + dy, z + dz]) }
      }
    front = next
  }
  return layer
}

/** crystalSpire: a crystal breaking out of a crust at [x, y, z] and leaning `lean` (a facing word, or null for
 *  straight up): `height` blocks of `crystal`, stepping one block toward `lean` every `stepEvery` blocks, a `bud` (a
 *  cluster block, facing up) on its tip and, when `collar` is given, a ring of it round the foot where it broke the
 *  crust. Returns the tip's cell. */
export function crystalSpire(c, [x, y, z], height, { crystal, bud, collar = null, lean = null, stepEvery = 2 }) {
  if (collar) for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) if (!c.get(x + dx, y, z + dz)) c.set(x + dx, y, z + dz, collar)
  const [lx, lz] = lean ? DIRS[lean] : [0, 0]
  let cx = x, cz = z
  for (let i = 0; i < height; i++) {
    if (i > 0 && i % stepEvery === 0) { cx += lx; cz += lz; c.set(cx - lx, y + i - 1, cz - lz, crystal) }
    c.set(cx, y + i, cz, crystal)
  }
  c.set(cx, y + height, cz, bud, { facing: 'up', waterlogged: 'false' })
  return [cx, y + height, cz]
}

/** glazedBasin: a liquid held in a kerb: `cells` ([x, z]) of `liquid` sources at y, a kerb of `kerb` round them at y
 *  and y + 1 (a full block beside every cell above the liquid, for a bud to sit on), with `glass` in place of the kerb
 *  at y on the `glassSide` cells ([x, z]), and `corner` at the kerb's corners. The floor under it must already be
 *  drawn; throws if a source could run out. Returns the kerb's box [x0, z0, x1, z1]. */
export function glazedBasin(c, cells, y, { liquid, kerb, corner = kerb, glass, glassSide = [] }) {
  const xs = cells.map(p => p[0]), zs = cells.map(p => p[1])
  const x0 = Math.min(...xs) - 1, x1 = Math.max(...xs) + 1, z0 = Math.min(...zs) - 1, z1 = Math.max(...zs) + 1
  const pool = new Set(cells.map(([x, z]) => `${x},${z}`))
  const glazed = new Set(glassSide.map(([x, z]) => `${x},${z}`))
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (pool.has(`${x},${z}`)) continue
    const isCorner = (x === x0 || x === x1) && (z === z0 || z === z1)
    c.set(x, y, z, glazed.has(`${x},${z}`) ? glass : isCorner ? corner : kerb)
    c.set(x, y + 1, z, isCorner ? corner : kerb)
  }
  for (const [x, z] of cells) {
    c.set(x, y, z, liquid, { level: '0' })
    c.clear(x, y + 1, z)
    if (!c.get(x, y - 1, z)) throw new Error(`${liquid} at ${x},${y},${z} has no floor`)
  }
  return [x0, z0, x1, z1]
}

/** crustVein: a crack of `vein` running down a crust from [x, y, z] (where a crystal broke out): each course down it
 *  takes the crust cell (one of `isCrust(x, y, z)`) at that level nearest the last, stepping at most one block aside,
 *  for `length` courses, so the crust reads as split round the crystal. Returns the cells it set. */
export function crustVein(c, [x, y, z], length, vein, isCrust) {
  const set = []
  let px = x, pz = z
  for (let yy = y - 1; yy >= 1 && set.length < length; yy--) {
    let best = null
    for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) {
      const nx = px + dx, nz = pz + dz
      if (!isCrust(nx, yy, nz)) continue
      const score = Math.abs(dx) + Math.abs(dz) + ((nx * 5 + nz * 3 + yy) % 3 === 0 ? 0.5 : 0)
      if (!best || score < best[0]) best = [score, nx, nz]
    }
    if (!best) break
    ;[, px, pz] = best
    c.set(px, yy, pz, vein); set.push([px, yy, pz])
  }
  return set
}
