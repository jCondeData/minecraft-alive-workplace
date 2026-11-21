// Sculpture parts for big figures (parts library, tools/blueprints/REBUILD.md; grown from the Colossus, 52.6 version A):
// a figure modelled in round sections instead of a swept profile. Ovals per layer for a body or a skirt, a limb as a
// capsule between joints, a drape (a cloak or banner) hanging as a sheet that leans back as it falls. Every part takes
// the caller's `pick(x, y, z, info)` for the block, so nothing here chooses a material.

/** sculptOval: the cells of one layer's oval (centre cx, cz may be halves; radii rx, rz) as [x, z] pairs, with `edge`
 *  true for a cell on the outline (a neighbour across a face falls outside). */
export function sculptOval(cx, cz, rx, rz) {
  const inside = (x, z) => ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2 <= 1
  const out = []
  for (let x = Math.floor(cx - rx); x <= Math.ceil(cx + rx); x++) for (let z = Math.floor(cz - rz); z <= Math.ceil(cz + rz); z++) {
    if (!inside(x, z)) continue
    out.push({ x, z, edge: !inside(x + 1, z) || !inside(x - 1, z) || !inside(x, z + 1) || !inside(x, z - 1) })
  }
  return out
}

/** sculptBody: a solid of stacked ovals. `layers(y)` gives {cx, cz, rx, rz} or null for each y in y0..y1; `pick(x, y, z,
 *  {edge, front})` the block (or null to leave the cell). `front` is true on the outline's z-low (front) half. Uses
 *  c.add when the canvas has it (a later stage resting on an earlier one). Returns the cells drawn. */
export function sculptBody(c, y0, y1, layers, pick) {
  const drawn = []
  for (let y = y0; y <= y1; y++) {
    const L = layers(y)
    if (!L) continue
    for (const { x, z, edge } of sculptOval(L.cx, L.cz, L.rx, L.rz)) {
      const id = pick(x, y, z, { edge, front: z <= L.cz, L })
      if (!id) continue
      const [b, props] = Array.isArray(id) ? id : [id, {}]
      put(c, x, y, z, b, props)
      drawn.push([x, y, z])
    }
  }
  return drawn
}

/** sculptLimb: a capsule of radius r along the joints [[x, y, z], ...] (a shoulder, an elbow, a wrist). `pick(x, y, z, t)`
 *  gives the block, t the segment's index (0 the upper arm, 1 the forearm...), or null. Returns the cells drawn. */
export function sculptLimb(c, joints, r, pick) {
  const cells = new Map()
  for (let i = 0; i < joints.length - 1; i++) {
    const [a, b] = [joints[i], joints[i + 1]]
    const lo = a.map((v, k) => Math.floor(Math.min(v, b[k]) - r)), hi = a.map((v, k) => Math.ceil(Math.max(v, b[k]) + r))
    const ab = b.map((v, k) => v - a[k]), len2 = ab.reduce((s, v) => s + v * v, 0)
    for (let x = lo[0]; x <= hi[0]; x++) for (let y = lo[1]; y <= hi[1]; y++) for (let z = lo[2]; z <= hi[2]; z++) {
      const p = [x - a[0], y - a[1], z - a[2]]
      const t = Math.max(0, Math.min(1, (p[0] * ab[0] + p[1] * ab[1] + p[2] * ab[2]) / len2))
      const d2 = p.reduce((s, v, k) => s + (v - t * ab[k]) ** 2, 0)
      const key = `${x},${y},${z}`
      if (d2 <= r * r && !cells.has(key)) cells.set(key, i)
    }
  }
  const drawn = []
  for (const [key, i] of cells) {
    const [x, y, z] = key.split(',').map(Number)
    const id = pick(x, y, z, i)
    if (!id) continue
    const [b, props] = Array.isArray(id) ? id : [id, {}]
    put(c, x, y, z, b, props)
    drawn.push([x, y, z])
  }
  return drawn
}

/** sculptDrape: a sheet hanging from y1 down to y0, its back face at z = back(y, x) (a pleat: some columns a block
 *  further back) and `thick(x, y)` cells deep toward the
 *  front, across x from left(y) to right(y). `pick(x, y, z, {outer, x0, x1})` gives the block (outer: the back face).
 *  For a cloak falling behind a figure: back(y) grows as y falls, so the sheet leans back toward its hem. */
export function sculptDrape(c, { y0, y1, back, left, right, thick }, pick) {
  const drawn = []
  for (let y = y1; y >= y0; y--) {
    const x0 = left(y), x1 = right(y)
    for (let x = x0; x <= x1; x++) for (let k = 0; k < thick(x, y); k++) {
      const zb = back(y, x)
      const z = zb - k
      const id = pick(x, y, z, { outer: k === 0, x0, x1 })
      if (!id) continue
      const [b, props] = Array.isArray(id) ? id : [id, {}]
      put(c, x, y, z, b, props)
      drawn.push([x, y, z])
    }
  }
  return drawn
}

const put = (c, x, y, z, id, props) => (c.add ? c.add(x, y, z, id, props) : c.set(x, y, z, id, props))
