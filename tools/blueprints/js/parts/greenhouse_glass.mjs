// Glasshouses and leats: a ridge-and-furrow glass roof (parallel glazed gables between shared valley gutters, a raised
// nave among them), the glazed gable ends under it, an undershot water wheel turning in an open leat, and a delivery
// hatch where a belt hands its load to the street (parts library, tools/blueprints/REBUILD.md; grown from the
// Greenhouse, 49/50.9). Every block comes from the caller's palette; only the blocks the game reads as themselves are
// named here (water, Create's water wheel and depot).

/** ridgeSpans: for a ridge-and-furrow roof whose valley gutters sit at `gutterY` on the columns in `gutters` and whose
 *  other columns top out at tops[x], the lowest cell each column needs so no gap opens between it and a lower
 *  neighbour (a column steps down at most one cell at a time, face to face): a column next to a gutter comes down to
 *  the gutter's top (a clerestory). Returns {lo, hi} maps by column. */
export function ridgeSpans(gutters, tops, gutterY) {
  const hi = {}, lo = {}
  const xs = [...gutters, ...Object.keys(tops).map(Number)].sort((a, b) => a - b)
  for (const x of xs) hi[x] = gutters.includes(x) ? gutterY : tops[x]
  for (const x of xs) {
    if (gutters.includes(x)) { lo[x] = gutterY; continue }
    const near = [hi[x - 1], hi[x + 1]].filter(v => v !== undefined)
    lo[x] = Math.min(hi[x], Math.min(...near) + 1)
  }
  return { lo, hi }
}

/** glassRidges: a ridge-and-furrow glass roof running `along` [l0, l1] (axis z): a `gutter` log (axis z) on each column
 *  of `gutters` at `gutterY`, and between them glazed gables whose columns top out at tops[x] (ridgeSpans fills each
 *  column down to its neighbour). Roof cells are `pane` (a glass block: a roof must be whole blocks), or `frame` on the
 *  end rows (l0, l1: the gables' outline) and on every z of `ribs` (the rafters); with `ridgeBeam` every ridge's top
 *  cell (a column higher than both neighbours) is `frame` all along (a pale ridge line over the glass). With `edge` (a
 *  stairs id) the end rows' sloping top cells are stairs rising toward the ridge, so each gable reads as a clean
 *  triangle. A `cap` (a slab id) runs along every ridge between the end rows; `finial` (a block that stands by
 *  itself, e.g. a lightning rod) on each ridge's end row tops. `keep(x, y, z)` returns false for cells another part
 *  owns. Returns the spans ({lo, hi}) for the gable ends (glassGable). */
export function glassRidges(c, { along: [l0, l1], gutterY, gutters, tops },
  { pane, frame, gutter, ribs = [], cap = null, finial = null, ridgeBeam = false, edge = null }, keep = () => true) {
  const { lo, hi } = ridgeSpans(gutters, tops, gutterY)
  for (let z = l0; z <= l1; z++) {
    const end = z === l0 || z === l1
    for (const x of gutters) if (keep(x, gutterY, z)) c.set(x, gutterY, z, gutter, axisOf(gutter, 'z'))
    for (const x of Object.keys(tops).map(Number)) {
      const left = hi[x - 1] ?? -1, right = hi[x + 1] ?? -1
      const ridge = hi[x] > left && hi[x] > right
      for (let y = lo[x]; y <= hi[x]; y++) {
        if (!keep(x, y, z)) continue
        if (end && edge && y === hi[x] && !ridge && left !== right)
          c.set(x, y, z, edge, { facing: right > left ? 'east' : 'west', half: 'bottom', shape: 'straight', waterlogged: 'false' })
        else c.set(x, y, z, end || ribs.includes(z) || (ridgeBeam && ridge && y === hi[x]) ? frame : pane)
      }
      if (!ridge) continue
      if (end && finial) c.set(x, hi[x] + 1, z, finial)
      else if (!end && cap) c.set(x, hi[x] + 1, z, cap, { type: 'bottom', waterlogged: 'false' })
    }
  }
  return { lo, hi }
}

/** axisOf: {axis} for a block that has one (logs, wood, stems, pillars, chains), {} for any other (concrete, stone). */
export function axisOf(id, axis) {
  return /(_log|_wood|_stem|_hyphae|basalt|pillar|hay_block|bone_block|chain)$/.test(id) ? { axis } : {}
}

/** glassGable: fills a glazed gable end at row z under a glassRidges roof (its spans `lo`), from y0 up to each column's
 *  underside: `pick(x, y)` gives the block (or null to leave the cell). Gutter columns are left to the wall. */
export function glassGable(c, z, { gutters, lo }, y0, pick) {
  for (const [k, under] of Object.entries(lo)) {
    const x = Number(k)
    if (gutters.includes(x)) continue
    for (let y = y0; y < under; y++) { const id = pick(x, y); if (id) c.set(x, y, z, id) }
  }
}

/** undershotLeat: an open leat along z at column x, level y (the ground layer): `kerb` (may be a mix()) on both sides
 *  and across both ends (z0 - 1 and z1 + 1), still water poured at each z of `sources` (the dipping tank the builder
 *  fills from a bucket) and running water down the rest of the channel, levelled as it settles (1 beside a source, 7
 *  at most, it stops at the far kerb). A Create water wheel stands over the channel at wheelZ (y + 1), its axle across
 *  the leat (`facing` east or west): the running water under it turns it, and with the wheel gone (no Create) the water
 *  runs exactly as before, so nothing drawn is ever wetted. `raised` (a block, may be a mix()) a second kerb course at
 *  y + 1 along `raisedZ` [za, zb] (default the whole run: e.g. only round the dipping tank, so the wheel stands clear
 *  above a flush channel), except the cells in `gaps` ([x, z]: where a shaft leaves the wheel). Returns the water
 *  cells [{at, level}]. */
export function undershotLeat(c, { x, y = 0, z0, z1, sources, wheelZ, facing = 'east', gaps = [], raisedZ = null }, { kerb, raised = null }) {
  const cells = []
  const [ra, rb] = raisedZ ?? [z0 - 1, z1 + 1]
  const lift = (kx, z) => raised && z >= ra && z <= rb && !gaps.some(([gx, gz]) => gx === kx && gz === z)
  for (let z = z0 - 1; z <= z1 + 1; z++) {
    for (const kx of [x - 1, x + 1]) {
      c.set(kx, y, z, kerb)
      if (lift(kx, z)) c.set(kx, y + 1, z, raised)
    }
  }
  for (const z of [z0 - 1, z1 + 1]) {
    c.set(x, y, z, kerb)
    if (lift(x, z)) c.set(x, y + 1, z, raised)
  }
  for (let z = z0; z <= z1; z++) {
    const d = Math.min(...sources.map(s => Math.abs(s - z)))
    if (d > 7) throw new Error(`leat cell ${x},${y},${z} is beyond the water's reach`)
    c.set(x, y, z, 'water', { level: String(d) })
    cells.push({ at: [x, y, z], level: d })
  }
  const under = cells.find(w => w.at[2] === wheelZ)
  if (!under || under.level === 0) throw new Error('the wheel must stand over running water, not a source')
  c.set(x, y + 1, wheelZ, 'create:water_wheel', { facing })
  return cells
}

/** deliveryHatch: a Create depot set in a wall at [x, y, z] (a belt's end hands its load onto it), a shutter over it
 *  (a `wood` trapdoor, folded up: top half, shut) and a hood of `slab` (top slabs) one block out over the opening,
 *  `span` cells either side. `out`: the side the street is on. Nothing rests on the depot. `shutter: false` leaves
 *  the opening over the depot clear (the Greenhouse: a trapdoor there read as sitting on the depot). */
export function deliveryHatch(c, [x, y, z], out, { wood, slab, span = 1, shutter = true }) {
  c.set(x, y, z, 'create:depot')
  if (shutter) c.set(x, y + 1, z, `${wood}_trapdoor`, { facing: out, half: 'top', open: 'false', powered: 'false', waterlogged: 'false' })
  const [dx, dz] = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }[out]
  for (let u = -span; u <= span; u++) {
    const [hx, hz] = dx === 0 ? [x + u, z + dz] : [x + dx, z + u]
    c.set(hx, y + 2, hz, slab, { type: 'top', waterlogged: 'false' })
  }
}
