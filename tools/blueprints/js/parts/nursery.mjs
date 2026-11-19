// Nurseries and walled gardens: a barrel-vaulted glasshouse roof and the infill under its ends, a low garden wall
// with piers, a hoist beam out of a gable (parts library, tools/blueprints/REBUILD.md; grown from the Berry Nursery, 49).
// Every block comes from the caller's palette.

/** vaultSpans: for a vault whose surface over column u (a0..a1) tops out at profile[u - a0], the lowest cell each
 *  column needs so no gap opens between it and a lower neighbour (a column steps down at most one cell at a time,
 *  face to face). Returns {lo, hi} maps by column. */
export function vaultSpans([a0, a1], profile) {
  const hi = {}, lo = {}
  for (let u = a0; u <= a1; u++) hi[u] = profile[u - a0]
  for (let u = a0; u <= a1; u++) {
    const near = [hi[u - 1], hi[u + 1]].filter(v => v !== undefined)
    lo[u] = Math.min(hi[u], Math.min(...near) + 1)
  }
  return { lo, hi }
}

/** vaultRoof: a curved glasshouse roof over `across` [a0, a1] (its feet on the side walls), running `along` [l0, l1],
 *  the surface over column u from lo to hi (vaultSpans). Bays of `pane` (a glass block: a roof has to be whole
 *  blocks) between ribs of `rib` at every v in `ribs`; each foot (the a0 and a1 columns) a `gutter` beam along the
 *  run. `keep(x, y, z)` returns false for cells another part owns. Returns the spans ({lo, hi}). */
export function vaultRoof(c, { axis, across, along: [l0, l1], profile }, { pane, rib, gutter = rib, ribs = [] }, keep = () => true) {
  const { lo, hi } = vaultSpans(across, profile)
  const [a0, a1] = across
  for (let v = l0; v <= l1; v++) for (let u = a0; u <= a1; u++) for (let y = lo[u]; y <= hi[u]; y++) {
    const [x, z] = axis === 'z' ? [u, v] : [v, u]
    if (!keep(x, y, z)) continue
    if (u === a0 || u === a1) c.set(x, y, z, gutter, gutter.endsWith('_log') ? { axis: axis === 'z' ? 'z' : 'x' } : {})
    else if (ribs.includes(v)) c.set(x, y, z, rib)
    else c.set(x, y, z, pane)
  }
  return { lo, hi }
}

/** gardenWall: a low garden wall along a path of corner points [[x, z], ...] (each leg straight): `courses` rows of
 *  `course` (may be a mix()) from y and a `parapet` (a wall block, joined by finish()) on them, with a pier at each
 *  corner and at every point listed in `piers` ([x, z]): the course carried up to `pierHigh` above y and a `cap` on
 *  top. Returns the piers' top cells [x, y, z] (where a lantern or a pot can stand). */
export function gardenWall(c, points, y, { course, parapet, pier = course, cap }, { piers = [], pierHigh = 2, courses = 1 } = {}) {
  const tops = []
  const atPier = new Set([...points, ...piers].map(([x, z]) => `${x},${z}`))
  for (let i = 0; i + 1 < points.length; i++) {
    const [ax, az] = points[i], [bx, bz] = points[i + 1]
    if (ax !== bx && az !== bz) throw new Error(`wall leg ${points[i]} -> ${points[i + 1]} isn't straight`)
    for (let x = Math.min(ax, bx); x <= Math.max(ax, bx); x++)
      for (let z = Math.min(az, bz); z <= Math.max(az, bz); z++) {
        for (let k = 0; k < courses; k++) c.set(x, y + k, z, course)
        if (atPier.has(`${x},${z}`)) continue
        c.set(x, y + courses, z, parapet)
      }
  }
  for (const k of atPier) {
    const [x, z] = k.split(',').map(Number)
    if (!c.get(x, y, z)) continue // a listed pier off the wall's line
    for (let dy = 1; dy <= pierHigh; dy++) c.set(x, y + dy, z, pier)
    c.set(x, y + pierHigh + 1, z, cap.id, cap.props ?? {})
    tops.push([x, y + pierHigh + 2, z])
  }
  return tops
}

/** loftHoist: a beam (`log`) run out of a gable face at [x, y, z] toward `out` for `reach` blocks, a chain hanging
 *  `drop` cells from its end and a hook-lantern under it (the seed loft's hoist). */
export function loftHoist(c, [x, y, z], out, reach, drop, log) {
  const d = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }[out]
  const axis = d[0] ? 'x' : 'z'
  for (let i = 0; i < reach; i++) c.set(x + d[0] * i, y, z + d[1] * i, log, { axis })
  const ex = x + d[0] * (reach - 1), ez = z + d[1] * (reach - 1)
  for (let i = 1; i <= drop; i++) c.set(ex, y - i, ez, 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(ex, y - drop - 1, ez, 'lantern', { hanging: 'true', waterlogged: 'false' })
}
