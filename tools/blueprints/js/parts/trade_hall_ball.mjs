// The Ball Tower trade hall's parts (ROADMAP 49, trade_hall): a chamfered round-ish tower shaft and the ball that caps
// it (a hollow sphere in three bands, low / band / high, with an "eye" boss on its front). Every block comes from the
// palette passed in.

/** roundShaft: a 5-wide (or wider) tower ring x0..x1, z0..z1 with its four corners cut off, y0..y1, `id` (a block or mix). */
export function roundShaft(c, x0, z0, x1, z1, y0, y1, id) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const edge = x === x0 || x === x1 || z === z0 || z === z1
    const corner = (x === x0 || x === x1) && (z === z0 || z === z1)
    if (!edge || corner) continue
    for (let y = y0; y <= y1; y++) c.set(x, y, z, id)
  }
}

/** ballCap: a hollow ball of radius r centred at (cx, cy, cz): below cy `low`, the equator row `band`, above it `high`;
 *  on the front (the smallest z of each column) an `eye` boss set in the band. keep(x, y, z) false skips a cell.
 *  `shell`: the wall's thickness (a large one fills it solid, leaving no wedged air). Returns the cells it set. */
export function ballCap(c, [cx, cy, cz], r, { low, band, high, eye }, keep = () => true, shell = 1.25) {
  const set = []
  const R = Math.ceil(r)
  for (let x = cx - R; x <= cx + R; x++) for (let y = cy - R; y <= cy + R; y++) for (let z = cz - R; z <= cz + R; z++) {
    const d = Math.hypot(x - cx, y - cy, z - cz)
    if (d > r || d <= r - shell || !c.inside(x, y, z) || !keep(x, y, z)) continue
    c.set(x, y, z, y < cy ? low : y === cy ? band : high)
    set.push([x, y, z])
  }
  // the eye: the front-most cell at the centre of the band
  const front = (x, y) => Math.min(...set.filter(p => p[0] === x && p[1] === y).map(p => p[2]))
  const fz = front(cx, cy)
  c.set(cx, cy, fz, eye)
  return set
}

/** steppedCap: a stepped tile cap over a tower, square x0..x1, z0..z1 at y0, each course one in and one up: stairs round
 *  the edge rising inward, full blocks inside (solid, no air wedged), `finial` on the top. roof = {full, stairs}. */
export function steppedCap(c, x0, z0, x1, z1, y0, roof, finial = null) {
  for (let k = 0; x0 + k <= x1 - k && z0 + k <= z1 - k; k++) {
    const a = x0 + k, b = x1 - k, p = z0 + k, q = z1 - k, y = y0 + k
    for (let x = a; x <= b; x++) for (let z = p; z <= q; z++) {
      const face = z === p ? 'south' : z === q ? 'north' : x === a ? 'east' : x === b ? 'west' : null
      if (face && a !== b) c.set(x, y, z, roof.stairs, { facing: face, half: 'bottom', shape: 'straight', waterlogged: 'false' })
      else c.set(x, y, z, roof.full)
    }
    if (a === b && finial) c.set(a, y + 1, p, finial)
  }
}
