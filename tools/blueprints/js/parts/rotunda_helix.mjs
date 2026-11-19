// Rotunda parts (the Great Library Wonder, ROADMAP 52.6; any round hall, tower or stair drum can use them): a helix
// that climbs the inside of a round wall (a walkway of half-block steps, and the band of cells that follows it a set
// height above, for a spiral of windows seen from outside), a classical pediment on a gable end, a row of columns, and a
// staged check that a later stage keeps every earlier block exactly. Every block comes from the palette passed in.
import { slab } from './canvas.mjs'

const d2 = (x, z, cx, cz) => (x - cx) ** 2 + (z - cz) ** 2

/** helixAngle: the angle (0..360) of cell (x, z) round (cx, cz), measured from `start` degrees (0 = toward +z, 90 =
 *  toward +x) in the turning direction `dir` (+1 or -1). */
export function helixAngle(x, z, { cx, cz, start = 0, dir = 1 }) {
  const a = ((Math.atan2(x + 0.5 - (cx + 0.5), z + 0.5 - (cz + 0.5)) * 180) / Math.PI + 360) % 360
  return (((a - start) * dir) % 360 + 360) % 360
}

/** helixHeights: the walking surfaces (in blocks, half steps) of a helix over cell (x, z): y0 at the start angle,
 *  climbing `rise` blocks a turn, up to `top`. */
export function helixHeights(x, z, h) {
  const phi = helixAngle(x, z, h)
  const out = []
  for (let k = 0; ; k++) {
    const s = h.y0 + (h.rise * (phi + 360 * k)) / 360
    if (s > h.top + 1e-9) break
    out.push(Math.round(s * 2) / 2)
  }
  return out
}

/** helixWalk: a walkway of half-block steps on every cell with inner2 < r2 <= outer2 round (cx, cz), climbing the
 *  helix `h` ({cx, cz, start, dir, y0, rise, top}): a top slab where the surface is a whole block, a bottom slab where it
 *  is a half. Surfaces below y0 + 0.5 are the floor itself and stay open. `id`: the slab block. Cells already drawn are
 *  left (add on a staged canvas). Returns the cells drawn. */
export function helixWalk(c, h, { inner2, outer2 }, id) {
  const R = Math.ceil(Math.sqrt(outer2)) + 1
  const cells = []
  for (let x = h.cx - R; x <= h.cx + R; x++) for (let z = h.cz - R; z <= h.cz + R; z++) {
    const r = d2(x, z, h.cx, h.cz)
    if (r <= inner2 || r > outer2) continue
    for (const s of helixHeights(x, z, h)) {
      if (s < h.y0 + 0.5) continue
      const whole = Number.isInteger(s)
      const y = whole ? s - 1 : s - 0.5
      if (c.get(x, y, z) || !c.inside(x, y, z)) continue
      slab(c, x, y, z, id, whole ? 'top' : 'bottom')
      cells.push([x, y, z])
    }
  }
  return cells
}

/** helixBand: a test for the cells a band `from`..`to` blocks above the helix's surface over (x, z) (a spiral of
 *  windows that rides above the walkway inside). */
export function helixBand(h, from, to) {
  return (x, y, z) => helixHeights(x, z, h).some(s => y >= Math.floor(s) + from && y <= Math.floor(s) + to)
}

/** pediment: a classical temple front on a gable end in the plane `line` (axis 'z': the face is at z = line, spanning
 *  x a0..a1; axis 'x': at x = line, spanning z). The raking cornice (`rake` stairs, facing out of the face's slope) climbs
 *  one block a column from y0 at both ends to the apex, the field under it filled with `field`, an entablature row
 *  `beam` along y0 - 1. `face`: the direction the front faces (for the rake's back). Returns the apex y. */
export function pediment(c, { axis, line, a0, a1, y0 }, { rake, field, beam, apex }) {
  const half = Math.floor((a1 - a0) / 2)
  const at = (u, y, id, props = {}) => (axis === 'z' ? c.set(u, y, line, id, props) : c.set(line, y, u, id, props))
  for (let u = a0; u <= a1; u++) at(u, y0 - 1, beam)
  for (let i = 0; i < half; i++) {
    const y = y0 + i
    const up0 = axis === 'z' ? 'east' : 'south', up1 = axis === 'z' ? 'west' : 'north'
    at(a0 + i, y, rake, { facing: up0, half: 'bottom', shape: 'straight', waterlogged: 'false' })
    at(a1 - i, y, rake, { facing: up1, half: 'bottom', shape: 'straight', waterlogged: 'false' })
    for (let u = a0 + i + 1; u < a1 - i; u++) at(u, y, field)
  }
  at(a0 + half, y0 + half, apex)
  return y0 + half
}

/** columnRow: round-looking columns (`shaft`, an axis-y block such as a pillar) at each [x, z] from y0 to y1, each on a
 *  `base` and under a `capital` block. */
export function columnRow(c, cells, y0, y1, { base, shaft, capital }) {
  for (const [x, z] of cells) {
    c.set(x, y0, z, base)
    for (let y = y0 + 1; y < y1; y++) c.set(x, y, z, shaft, { axis: 'y' })
    c.set(x, y1, z, capital)
  }
}

/** stagesOnlyAdd: given snapshots (canvases, stage 1 first), the first block a later stage changes or takes away, as a
 *  message, or null when every stage keeps every earlier block exactly. */
export function stagesOnlyAdd(snapshots) {
  for (let n = 1; n < snapshots.length; n++) {
    const before = snapshots[n - 1], after = snapshots[n]
    for (const [k, v] of before.cells) {
      const w = after.cells.get(k)
      if (!w) return `stage ${n + 1} takes away stage ${n}'s ${v.id} at ${k}`
      if (w.id !== v.id || JSON.stringify(w.props) !== JSON.stringify(v.props))
        return `stage ${n + 1} changes ${v.id} to ${w.id} at ${k}`
    }
  }
  return null
}

