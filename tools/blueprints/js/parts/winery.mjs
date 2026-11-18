// Wineries (parts library, tools/blueprints/REBUILD.md; grown from the Perron Press House, the Winery, 49): a double
// stone perron up to a first-floor door, casks of logs lying on their sides, vineyard rows on a trellis, and fruiting
// vines hanging from a beam. Every block comes from the caller's palette; none is picked here.
import { stair } from './canvas.mjs'

/** perron: a double flight of steps up a facade to a landing (x0..x1 the whole run, the landing lx0..lx1 at y `top`,
 *  both flights `z0..z1` deep), each flight rising toward the landing from its outer end at y 0, solid `fill` under
 *  every step, the landing carried on two piers (its end columns) with a one-wide passage under it (lx0 + 1 .. lx1 -
 *  1) left open to a door in the wall behind. `step`: the stairs' id; `landing`: the landing's block. */
export function perron(c, { x0, x1, lx0, lx1, z0, z1, top }, { step, fill, landing }) {
  for (let z = z0; z <= z1; z++) {
    for (let i = 0; x0 + i < lx0; i++) { // west flight, rising east
      const x = x0 + i, y = top - (lx0 - 1 - x)
      if (y < 0) continue
      stair(c, x, y, z, step, 'east')
      for (let yy = 0; yy < y; yy++) c.set(x, yy, z, fill)
    }
    for (let i = 0; x1 - i > lx1; i++) { // east flight, rising west
      const x = x1 - i, y = top - (x - lx1 - 1)
      if (y < 0) continue
      stair(c, x, y, z, step, 'west')
      for (let yy = 0; yy < y; yy++) c.set(x, yy, z, fill)
    }
    for (let x = lx0; x <= lx1; x++) {
      c.set(x, top, z, landing)
      if (x === lx0 || x === lx1) for (let yy = 0; yy < top; yy++) c.set(x, yy, z, fill) // the piers
    }
  }
}

/** casks: wine casks of `log` lying on their sides (axis 'x' or 'z', never upright) in the given [x, y, z] cells, the
 *  round heads toward the aisle. A test may count them (a barrel would be a job block). */
export function casks(c, cells, log, axis) {
  for (const [x, y, z] of cells) c.set(x, y, z, log, { axis })
}

/** logCaskRack: a rack of casks against a wall, `x0..x1` by `z0..z1` (one of them a single line), `y0..y1` high, lying on
 *  `axis`; `taper` drops the end casks of each higher tier so the stack reads as a pyramid. Returns the cells. */
export function logCaskRack(c, { x0, x1, z0, z1, y0, y1, axis, taper = false }, log) {
  const cells = []
  const along = x0 === x1 ? 'z' : 'x'
  for (let y = y0; y <= y1; y++) {
    const cut = taper ? y - y0 : 0
    for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
      const u = along === 'x' ? x : z, lo = along === 'x' ? x0 : z0, hi = along === 'x' ? x1 : z1
      if (u < lo + cut || u > hi - cut) continue
      cells.push([x, y, z])
    }
  }
  casks(c, cells, log, axis)
  return cells
}

/** trellisRow: a vineyard row along z at x: a raised bed of `soil` at y 0 from z0 to z1, `plant` (a sweet berry bush,
 *  its props) on every cell between the end posts, a `post` (a fence id) two high at each end and a `wire` (null: none; the same
 *  fence, or a wall) along the top at y 2 between them. Returns the planted cells. */
export function trellisRow(c, x, z0, z1, { soil, plant, plantProps = {}, post, wire = post }) {
  const planted = []
  for (let z = z0; z <= z1; z++) {
    c.set(x, 0, z, soil)
    if (z === z0 || z === z1) { c.set(x, 1, z, post); c.set(x, 2, z, post); continue }
    c.set(x, 1, z, plant, plantProps)
    if (wire) c.set(x, 2, z, wire)
    planted.push([x, 1, z])
  }
  return planted
}

/** fruitingVines: vines hanging from the beam above each [x, y, z, length] (y: the lowest cell, the fruiting tip;
 *  length - 1 plain vine cells above it, up to the beam). `tip` and `stem` are the vine ids (cave_vines and
 *  cave_vines_plant: every cell bears berries). Throws when the cell over the top isn't drawn. */
export function fruitingVines(c, vines, { tip, stem }) {
  for (const [x, y, z, n] of vines) {
    if (!c.get(x, y + n, z)) throw new Error(`a vine at ${x},${y},${z} hangs from nothing`)
    c.set(x, y, z, tip, { age: '25', berries: 'true' })
    for (let i = 1; i < n; i++) c.set(x, y + i, z, stem, { berries: 'true' })
  }
}
