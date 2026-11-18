// Guildhall parts (ROADMAP 49): an open stone arcade (piers with sprung arches under a beam course) and a jetty's
// corbels (upside-down stairs under an overhanging floor). Palette passed in; no block picked here.
import { stair } from './canvas.mjs'

/** arcadeRun: piers along a line (axis 'x': z fixed = line, piers at x in `piers`; axis 'z' likewise) from y = 1 to
 *  top - 1, a beam course at `top`, and in each opening an arch: upside-down stairs at top - 1 beside each pier (only
 *  where the opening is 3 or wider, so a 1.95-tall villager still walks under it). `pier` and `course` may be mix()es;
 *  `arch` is a stairs id. */
export function arcadeRun(c, axis, line, piers, top, { pier, course, arch, cap = null }) {
  const at = (u, y) => (axis === 'x' ? [u, y, line] : [line, y, u])
  for (const u of piers) {
    for (let y = 1; y < top; y++) c.set(...at(u, y), pier)
    if (cap) c.set(...at(u, top - 1), cap)
  }
  const lo = Math.min(...piers), hi = Math.max(...piers)
  for (let u = lo; u <= hi; u++) c.set(...at(u, top), course)
  const sorted = [...piers].sort((a, b) => a - b)
  for (let i = 0; i + 1 < sorted.length; i++) {
    const a = sorted[i], b = sorted[i + 1]
    if (b - a - 1 < 3) continue
    const [fa, fb] = axis === 'x' ? ['west', 'east'] : ['north', 'south']
    stair(c, ...at(a + 1, top - 1), arch, fa, 'top')
    stair(c, ...at(b - 1, top - 1), arch, fb, 'top')
  }
}

/** jettyCorbels: upside-down stairs under an overhanging floor edge, at each of `at` (the post line), facing back
 *  into the building (`toward`), so the jetty reads as carried. */
export function jettyCorbels(c, axis, line, at, y, id, toward) {
  for (const u of at) {
    const [x, z] = axis === 'x' ? [u, line] : [line, u]
    stair(c, x, y, z, id, toward, 'top')
  }
}
