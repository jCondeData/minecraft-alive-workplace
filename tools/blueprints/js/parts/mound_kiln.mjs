// Mound kiln (parts library, tools/blueprints/REBUILD.md), grown from the Artisan's House's savanna look (49.10g): a
// potter's updraft kiln built of termite-mound clay in the shape of the mound it came from: a broad round foot that
// steps in, course by course, to a fluted body and one tall flue spire, with smaller turrets clinging to its flanks the
// way a mound's chimneys do. Every block comes from the caller's palette; none is picked here.
import { inDisc } from './kilns.mjs'

/** moundKiln: a solid stepped mound centred on (cx, cz) from y0. `courses` ([{r, top, plus}], widest first): each a
 *  stack of round courses of radius r (inDisc) up to y = top; plus: a cross (the r 1 square without its corners) so
 *  the spire reads round. `turrets` ([x, z, top]): a column of `body` from the first course above the foot up to top,
 *  capped with `cap` (a wall block: a rounded tip). The flue's top course carries a lit campfire when `smoke`.
 *  `foot` is the bottom course (soot-dark), `body` may be a mix(). `keep(x, y, z)` false leaves a cell to the caller
 *  (a fire mouth). Returns the campfire's y. */
export function moundKiln(c, cx, cz, { y0 = 0, courses, turrets = [], smoke = true }, { foot, body, cap }, keep = () => true) {
  const put = (x, y, z, id, props) => { if (keep(x, y, z)) c.set(x, y, z, id, props) }
  let y = y0
  for (const s of courses) {
    for (; y <= s.top; y++)
      for (let dx = -s.r; dx <= s.r; dx++) for (let dz = -s.r; dz <= s.r; dz++) {
        if (!inDisc(dx, dz, s.r)) continue
        if (s.plus && Math.abs(dx) + Math.abs(dz) > 1) continue
        put(cx + dx, y, cz + dz, y === y0 ? foot : body)
      }
  }
  for (const [x, z, top] of turrets) {
    for (let ty = y0 + 1; ty <= top; ty++) put(x, ty, z, body)
    put(x, top + 1, z, cap, { up: 'true', north: 'none', south: 'none', east: 'none', west: 'none', waterlogged: 'false' })
  }
  if (smoke) put(cx, y, cz, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  return y
}
