// Figures drawn in sections (parts library, tools/blueprints/REBUILD.md; grown for the Colossus B, 52.6): a statue
// laid layer by layer from rounded cross-sections, limbs swept along a line, and a caged lantern of glowing blocks.
// Every block comes from the caller (an id, or a function (x, y, z, info) returning one); nothing is picked here.

/** figureCells: the cells of one rounded cross-section at height y, a superellipse round (cx, cz) with half-widths rx
 *  (along x) and rz (along z); p = 2 is an ellipse, 4 a rounded box. Returns [[x, z, edge]] where edge is true on the
 *  section's rim (a cell with a horizontal neighbour outside it). */
export function figureCells({ cx, cz, rx, rz, p = 2.6 }) {
  const inside = (x, z) => Math.abs((x - cx) / rx) ** p + Math.abs((z - cz) / rz) ** p <= 1
  const out = []
  for (let x = Math.floor(cx - rx); x <= Math.ceil(cx + rx); x++) for (let z = Math.floor(cz - rz); z <= Math.ceil(cz + rz); z++) {
    if (!inside(x, z)) continue
    const edge = !inside(x + 1, z) || !inside(x - 1, z) || !inside(x, z + 1) || !inside(x, z - 1)
    out.push([x, z, edge])
  }
  return out
}

/** figureSection: draws figureCells at y with `pick(x, y, z, {edge})` (an id, [id, props], or null for nothing).
 *  Uses c.add when the canvas has it (a later stage resting on an earlier one). Returns the cells drawn. */
export function figureSection(c, y, shape, pick) {
  const put = c.add ? c.add.bind(c) : c.set.bind(c)
  const drawn = []
  for (const [x, z, edge] of figureCells(shape)) {
    let v = pick(x, y, z, { edge })
    if (!v) continue
    const [id, props] = Array.isArray(v) ? v : [v, {}]
    if (c.get(x, y, z)) continue
    put(x, y, z, id, props)
    drawn.push([x, y, z])
  }
  return drawn
}

/** figureLimb: sweeps a limb of radius r (cells within r of the line, a round arm or leg) from a to b ([x, y, z],
 *  halves allowed), skipping cells already drawn. `pick(x, y, z, t)` gives the block, t from 0 (a) to 1 (b). */
export function figureLimb(c, a, b, r, pick) {
  const put = c.add ? c.add.bind(c) : c.set.bind(c)
  const d = b.map((v, i) => v - a[i])
  const len2 = d.reduce((s, v) => s + v * v, 0) || 1
  const lo = a.map((v, i) => Math.floor(Math.min(v, b[i]) - r)), hi = a.map((v, i) => Math.ceil(Math.max(v, b[i]) + r))
  const drawn = []
  for (let x = lo[0]; x <= hi[0]; x++) for (let y = lo[1]; y <= hi[1]; y++) for (let z = lo[2]; z <= hi[2]; z++) {
    const p = [x, y, z]
    const t = Math.max(0, Math.min(1, p.reduce((s, v, i) => s + (v - a[i]) * d[i], 0) / len2))
    const dist2 = p.reduce((s, v, i) => s + (v - (a[i] + t * d[i])) ** 2, 0)
    if (dist2 > r * r || c.get(x, y, z) || !c.inside(x, y, z)) continue
    const v = pick(x, y, z, t)
    if (!v) continue
    const [id, props] = Array.isArray(v) ? v : [v, {}]
    put(x, y, z, id, props)
    drawn.push(p)
  }
  return drawn
}

/** cagedLantern: a square lantern hung at (x, z): a column of `glow` blocks at the heights in `ys`, each boxed on its
 *  four sides by `glass` (panes, which meet the corner posts at both ends), `post` at the four corners, a `cap` course
 *  over and under the column (slabs: top half under, bottom half over) and an optional `drip` finial pointing down
 *  under it. P = { glow, glass, post, capSlab, drip }. */
export function cagedLantern(c, { x, z, ys }, P) {
  const lo = Math.min(...ys), hi = Math.max(...ys)
  for (const y of ys) {
    c.set(x, y, z, P.glow)
    for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) c.set(x + dx, y, z + dz, P.glass, { waterlogged: 'false' })
    for (const [dx, dz] of [[1, 1], [1, -1], [-1, 1], [-1, -1]]) c.set(x + dx, y, z + dz, P.post)
  }
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) {
    c.set(x + dx, lo - 1, z + dz, P.capSlab, { type: 'top', waterlogged: 'false' })
    c.set(x + dx, hi + 1, z + dz, P.capSlab, { type: dx || dz ? 'bottom' : 'double', waterlogged: 'false' })
  }
  if (P.drip) c.set(x, lo - 2, z, P.drip, { facing: 'down', powered: 'false', waterlogged: 'false' })
}
