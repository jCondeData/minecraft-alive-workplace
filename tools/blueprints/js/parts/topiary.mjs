// Topiary: clipped leaves (parts library, tools/blueprints/REBUILD.md; grown from the Topiary Garden, 49). Hedges,
// a clipped archway through a hedge, tiered "pom-pom" standards and leaf columns. Every block comes from the caller
// (a leaf id, or a function of (x, y, z) such as a mix()); none is picked here. Leaves are drawn persistent, as a
// builder places them (MaterialRules), so a clipped shape needs no log near it to last.

const LEAF = { persistent: 'true', distance: '1', waterlogged: 'false' }

/** clip: persistent leaves on the given [x, y, z] cells. leaf: an id or a function of (x, y, z). over: false keeps
 *  cells already drawn (a log, a lantern's post). */
export function clip(c, cells, leaf, over = true) {
  for (const [x, y, z] of cells) {
    if (!over && c.get(x, y, z)) continue
    c.set(x, y, z, typeof leaf === 'function' ? leaf(x, y, z) : leaf, LEAF)
  }
}

/** hedgeRun: a clipped hedge along `axis` ('x' or 'z') on the line `at`, from a to b, standing on y0 and as tall as
 *  height(i) at each step i along it (0 leaves a gap): stepped, crenellated or swept tops come from height(). */
export function hedgeRun(c, axis, at, a, b, y0, height, leaf) {
  const cells = []
  for (let i = a; i <= b; i++) {
    const h = typeof height === 'function' ? height(i) : height
    for (let y = y0; y < y0 + h; y++) cells.push(axis === 'x' ? [i, y, at] : [at, y, i])
  }
  clip(c, cells, leaf)
}

/** hedgeArch: a clipped archway through a hedge running along x, on the rows z0..z1 (its depth): piers at xa and xb
 *  up to the springing y (y0..spring), a lintel across xa..xb at spring + 1, the arch's shoulders one under the lintel
 *  at xa + 1 and xb - 1 (so the opening reads round), and a crown of `crown` cells above the lintel's middle
 *  (a stepped peak: 1 is a single cell, 3 a row with one cell on top of it). Returns the opening's cells. */
export function hedgeArch(c, { xa, xb, z0, z1, y0 = 1, spring = 4, crown = 1 }, leaf) {
  const cells = [], open = []
  const mid = (xa + xb) >> 1
  for (let z = z0; z <= z1; z++) {
    for (let y = y0; y <= spring; y++) cells.push([xa, y, z], [xb, y, z])
    for (let x = xa; x <= xb; x++) cells.push([x, spring + 1, z])
    if (xb - xa > 2) cells.push([xa + 1, spring, z], [xb - 1, spring, z])
    if (crown >= 3) cells.push([mid - 1, spring + 2, z], [mid, spring + 2, z], [mid + 1, spring + 2, z], [mid, spring + 3, z])
    else if (crown >= 1) cells.push([mid, spring + 2, z])
    for (let x = xa + 1; x < xb; x++) for (let y = y0; y <= spring; y++) {
      if (y === spring && xb - xa > 2 && (x === xa + 1 || x === xb - 1)) continue
      open.push([x, y, z])
    }
  }
  clip(c, cells, leaf)
  return open
}

/** tieredTopiary: a standard clipped into tiers up a single stem at (cx, cz) from y0. tiers: one entry per layer,
 *  bottom first: 'square' (3 x 3), 'plus' (the center and its four sides), 'ball' (the center only), 'stem' (the
 *  stem shows: a log), or null (nothing). `leafAt(y)` picks the leaf for a layer (a crown of flowering leaves on top).
 *  The stem (a log, or a fence for a slender standard) shows in the 'stem' layers and runs hidden through the
 *  middle of every 'square' and 'plus' tier under the top; a 'ball' is leaf right through. */
export function tieredTopiary(c, cx, cz, y0, tiers, leafAt, stem) {
  const top = tiers.length - 1
  const stemProps = /_(log|wood|stem|hyphae)$/.test(stem) ? { axis: 'y' } : {}
  tiers.forEach((t, i) => {
    const y = y0 + i
    if (t === 'stem' || ((t === 'square' || t === 'plus') && i < top)) c.set(cx, y, cz, stem, stemProps)
    if (!t || t === 'stem') return
    const leaf = typeof leafAt === 'function' ? leafAt(y) : leafAt
    const cells = []
    if (t === 'square') for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) cells.push([cx + dx, y, cz + dz])
    else if (t === 'plus') cells.push([cx, y, cz], [cx - 1, y, cz], [cx + 1, y, cz], [cx, y, cz - 1], [cx, y, cz + 1])
    else cells.push([cx, y, cz])
    clip(c, cells, leaf, false) // the stem keeps its cell
  })
}

/** leafColumn: a narrow clipped column (a pencil yew) at (x, z), y0..y1, with an optional cap leaf on top. */
export function leafColumn(c, x, z, y0, y1, leaf, cap = null) {
  const cells = []
  for (let y = y0; y <= y1; y++) cells.push([x, y, z])
  clip(c, cells, leaf)
  if (cap) clip(c, [[x, y1 + 1, z]], cap)
}
