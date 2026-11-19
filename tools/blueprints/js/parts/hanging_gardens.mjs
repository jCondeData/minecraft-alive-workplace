// Terraced gardens (ROADMAP 52.6, grown from the Hanging Gardens Wonder; any roof garden, terrace or oasis can use them):
// a date palm with dates on its trunk, a cypress spire, green spilling over a terrace's edge down its face, and a
// stair flight that climbs along a wall's face. Every block comes from the palette the caller passes; only blocks the
// game reads as themselves (vine, cocoa) are named here.

const FACE_OUT = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }
const OPPOSITE = { north: 'south', south: 'north', west: 'east', east: 'west' }
const leafProps = { persistent: 'true', distance: '1', waterlogged: 'false' }

/** datePalm: a trunk of `P.log` h tall from (x, y, z) (the cell over its soil), a crown of `P.fronds` leaves: a cap, four
 *  long fronds drooping at their tips and four short diagonal ones, and `dates` cocoa pods (P.dates, on a jungle log
 *  only) hung under the crown. Returns the trunk's top y. */
export function datePalm(c, [x, y, z], h, P, { dates = 2 } = {}) {
  const top = y + h - 1
  for (let yy = y; yy <= top; yy++) c.set(x, yy, z, P.log, { axis: 'y' })
  // each frond cell grows from the one before it (face or edge to face), so a frond cut short by a wall never leaves
  // a leaf hanging on its own
  const has = (dx, dy, dz) => !!c.get(x + dx, top + dy, z + dz)
  const leaf = (dx, dy, dz, from = [0, 0, 0]) => {
    if (!has(...from) || has(dx, dy, dz)) return
    c.set(x + dx, top + dy, z + dz, P.fronds, leafProps)
  }
  leaf(0, 1, 0)
  for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
    leaf(dx, 0, dz); leaf(2 * dx, 0, 2 * dz, [dx, 0, dz]); leaf(3 * dx, -1, 3 * dz, [2 * dx, 0, 2 * dz]); leaf(dx, 1, dz, [0, 1, 0])
  }
  for (const [dx, dz] of [[1, 1], [1, -1], [-1, 1], [-1, -1]]) { leaf(dx, 0, dz); leaf(2 * dx, 0, 2 * dz, [dx, 0, dz]); leaf(2 * dx, -1, 2 * dz, [2 * dx, 0, 2 * dz]) }
  if (P.dates) {
    // a pod hangs on the trunk's side, facing the log it grows from (young: nothing to pick for free)
    const sides = [['west', 1, 0], ['east', -1, 0], ['north', 0, 1], ['south', 0, -1]].slice(0, dates)
    for (const [facing, px, pz] of sides) if (!c.get(x + px, top - 1, z + pz)) c.set(x + px, top - 1, z + pz, P.dates, { facing, age: '1' })
  }
  return top
}

/** cypress: a slender spire of `P.leaves` round a `P.log` core h tall from (x, y, z): a full 3x3 skirt low down, a cross
 *  above, the core's tip a single leaf. */
export function cypress(c, [x, y, z], h, P) {
  for (let yy = y; yy < y + h - 1; yy++) c.set(x, yy, z, P.log, { axis: 'y' })
  for (let i = 1; i < h; i++) {
    const yy = y + i
    const ring = i <= Math.floor(h / 3) ? [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [1, -1], [-1, 1], [-1, -1]]
      : i < h - 1 ? [[1, 0], [-1, 0], [0, 1], [0, -1]] : []
    for (const [dx, dz] of ring) if (!c.get(x + dx, yy, z + dz)) c.set(x + dx, yy, z + dz, P.leaves, leafProps)
  }
  c.set(x, y + h - 1, z, P.leaves, leafProps)
  if (!c.get(x, y + h, z)) c.set(x, y + h, z, P.leaves, leafProps)
}

/** drape: green spilling over a terrace's edge and down its face. The face is the wall's plane; `out` the way it looks
 *  (north for a front face); cells `u` (x for a north/south face, z for east/west) along it; `top` the terrace's top
 *  layer. Leaves (a block id or a function (x, y, z)) fill the cells just outside the face from top down `depth(u)`
 *  blocks (2..3 reads as a hanging fringe), then vines (P.vine) hang on, attached to the face, `vines(u)` more, never
 *  down to `bottom` (the foot's layer) or into a drawn block. */
export function drape(c, { out, line, us, top, bottom = 0 }, leaves, depth, vines, P) {
  const [ox, oz] = FACE_OUT[out]
  for (const u of us) {
    const [x, z] = ox === 0 ? [u, line + oz] : [line + ox, u]
    const d = depth(u)
    for (let i = 0; i < d; i++) {
      const id = typeof leaves === 'function' ? leaves(x, top - i, z) : leaves
      c.set(x, top - i, z, id, leafProps)
    }
    const back = OPPOSITE[out]
    const v = vines(u)
    for (let i = 0; i < v; i++) {
      const yy = top - d - i
      if (yy <= bottom || c.get(x, yy, z)) break
      c.set(x, yy, z, P.vine, { north: 'false', south: 'false', east: 'false', west: 'false', up: 'false', [back]: 'true' })
    }
  }
}

/** faceFlight: a stair `width` wide climbing along a wall's face, from the floor at y0 + 1 (the first stair stands on the
 *  floor) one block a step to `steps` stairs, the last level with the terrace above (its top). `along` is the way it
 *  rises ('east' or 'west'), from column x0, on rows `zs`; every stair stands on a solid fill (`fill`, may be a mix())
 *  down to the floor. Returns the cells it takes [[x, y, z]]. */
export function faceFlight(c, { x0, along, zs, y0, steps }, stairs, fill) {
  const dx = along === 'east' ? 1 : -1
  const cells = []
  for (let i = 0; i < steps; i++) {
    const x = x0 + dx * i, y = y0 + 1 + i
    for (const z of zs) {
      for (let yy = y0 + 1; yy < y; yy++) { c.set(x, yy, z, fill); cells.push([x, yy, z]) }
      c.set(x, y, z, stairs, { facing: along, half: 'bottom', shape: 'straight', waterlogged: 'false' })
      cells.push([x, y, z])
    }
  }
  return cells
}
