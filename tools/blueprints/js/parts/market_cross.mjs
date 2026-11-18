// Market crosses (ROADMAP 49, the Market Cross): a mercat drum (an octagonal stone platform with pilasters and a
// parapet) and a cross shaft with a lamp-hung capital. Every block comes from the caller's palette.
import { stair } from './canvas.mjs'

/** edgeRing8: the cells of `cells` ([x, z] list) with an outside cell among their eight neighbours: an outline joined
 *  four ways round a diagonal (a wall or railing run round an octagon never meets itself only at a corner). */
export function edgeRing8(cells) {
  const has = new Set(cells.map(([x, z]) => `${x},${z}`))
  return cells.filter(([x, z]) => {
    for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) if (!has.has(`${x + dx},${z + dz}`)) return true
    return false
  })
}

/** vertexCells: the cells of an octagon's outline where a straight face meets a cut corner (an end of each face): a
 *  cell of `ring` whose four-way ring neighbours don't lie on one straight line. */
export function vertexCells(ring) {
  const has = new Set(ring.map(([x, z]) => `${x},${z}`))
  return ring.filter(([x, z]) => {
    const e = has.has(`${x + 1},${z}`), w = has.has(`${x - 1},${z}`), n = has.has(`${x},${z - 1}`), s = has.has(`${x},${z + 1}`)
    return !((e && w && !n && !s) || (n && s && !e && !w))
  })
}

/** mercatDrum: a solid drum over `cells` from y0 to y1. The top course (y1) is all `cap` (the platform floor and its
 *  edge in one); below it the outline is `face` with `pilaster` at `vertices` and `medallion` at `medallions`, the inside
 *  `core`. Blocks are ids or mix() functions. */
export function mercatDrum(c, { cells, y0, y1, vertices = [], medallions = [] }, { face, core, pilaster, medallion, cap }) {
  const ring = new Set(edgeRing8(cells).map(([x, z]) => `${x},${z}`))
  const vx = new Set(vertices.map(([x, z]) => `${x},${z}`)), md = new Set(medallions.map(([x, z]) => `${x},${z}`))
  for (const [x, z] of cells) {
    const k = `${x},${z}`
    for (let y = y0; y < y1; y++)
      c.set(x, y, z, md.has(k) ? medallion : vx.has(k) ? pilaster : ring.has(k) ? face : core)
    c.set(x, y1, z, cap)
  }
}

/** crossShaft: a market cross's shaft at (x, z): a `foot` at y0 with a flare of four bottom stairs round it (backs to
 *  the shaft), `pillar` up to pillarTo, a `capital` with four full `arm` blocks out to the sides (the cross's head),
 *  each capped with an `armCap` stair sloping up to the middle, a `lamp` hanging under each arm, and `flag` (a standing
 *  banner, `rotation`) on top. */
export function crossShaft(c, { x, z, y0, pillarTo, capital, flag, rotation = 8 }, P) {
  const sides = [['east', 1, 0, 'west'], ['west', -1, 0, 'east'], ['south', 0, 1, 'north'], ['north', 0, -1, 'south']]
  c.set(x, y0, z, P.foot)
  for (const [, dx, dz, back] of sides) stair(c, x + dx, y0, z + dz, P.flare, back)
  for (let y = y0 + 1; y <= pillarTo; y++) c.set(x, y, z, P.pillar, { axis: 'y' })
  c.set(x, capital, z, P.capital)
  for (const [, dx, dz, back] of sides) {
    c.set(x + dx, capital, z + dz, P.arm)
    if (P.armCap && capital + 1 < c.size[1]) stair(c, x + dx, capital + 1, z + dz, P.armCap, back)
    if (P.lamp) c.set(x + dx, capital - 1, z + dz, P.lamp, { hanging: 'true', waterlogged: 'false' })
  }
  if (P.flag) c.set(x, flag, z, P.flag, { rotation: String(rotation) })
}
