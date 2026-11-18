// Kilns (parts library, tools/blueprints/REBUILD.md), grown from the Artisan's House (49): a brick bottle kiln whose
// neck is a chimney. Every block comes from the family's palette; none is picked here.
import { stair } from './canvas.mjs'

/** disc: is (dx, dz) inside a round course of radius r (r 3: rows of 3, 5, 7, 7, 7, 5, 3; r 2: a 5 x 5 with its
 *  corners cut; r 1: the full 3 x 3, a kiln's neck). */
export function inDisc(dx, dz, r) {
  if (r <= 1) return Math.abs(dx) <= r && Math.abs(dz) <= r
  return Math.abs(dx) <= r && Math.abs(dz) <= r && dx * dx + dz * dz <= r * r + r - 1
}

/** bottleKiln: a round bottle kiln centred on (cx, cz) that narrows in `stages` ([{r, top}], widest first, each a
 *  stack of round courses of radius r up to y = top; y0 is a `base` course, any y in `bands` a hoop of `band`). Between
 *  two stages a shoulder: `stairs` rising inward on the ring the next stage leaves, the next stage's disc solid. The
 *  last stage (r 1) is the neck, its corners `wall` so it reads round; over it a flared lip of upside-down stairs one
 *  out (on the r 2 ring), and a rim of `wall` round the mouth with a lit campfire in it (smoke), standing on a column
 *  of full `body` from y0: the kiln is the house's chimney. `keep(x, y, z)` returns false for a cell the recipe keeps
 *  (a fire mouth). Returns the campfire's y. */
export function bottleKiln(c, cx, cz, { y0 = 0, stages, bands = [], smoke = true }, { base, body, band = body, stairs, wall }, keep = () => true) {
  const put = (x, y, z, id, props) => { if (keep(x, y, z)) c.set(x, y, z, id, props) }
  const inward = (dx, dz) => (Math.abs(dx) >= Math.abs(dz) ? (dx < 0 ? 'east' : 'west') : (dz < 0 ? 'south' : 'north'))
  const R = Math.max(...stages.map(s => s.r))
  let y = y0
  stages.forEach((s, i) => {
    if (i > 0) { // the shoulder from the stage below
      const below = stages[i - 1].r
      for (let dx = -below; dx <= below; dx++) for (let dz = -below; dz <= below; dz++) {
        if (!inDisc(dx, dz, below)) continue
        if (inDisc(dx, dz, s.r)) put(cx + dx, y, cz + dz, body)
        else if (keep(cx + dx, y, cz + dz)) stair(c, cx + dx, y, cz + dz, stairs, inward(dx, dz))
      }
      y++
    }
    for (; y <= s.top; y++)
      for (let dx = -s.r; dx <= s.r; dx++) for (let dz = -s.r; dz <= s.r; dz++) {
        if (!inDisc(dx, dz, s.r)) continue
        const corner = s.r === 1 && Math.abs(dx) === 1 && Math.abs(dz) === 1
        put(cx + dx, y, cz + dz, corner ? wall : y === y0 ? base : bands.includes(y) ? band : body)
      }
  })
  const lip = y
  for (let dx = -2; dx <= 2; dx++) for (let dz = -2; dz <= 2; dz++) {
    if (inDisc(dx, dz, 1)) put(cx + dx, lip, cz + dz, body)
    else if (inDisc(dx, dz, 2) && keep(cx + dx, lip, cz + dz)) stair(c, cx + dx, lip, cz + dz, stairs, inward(dx, dz), 'top')
  }
  const rim = lip + 1
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) if (dx || dz) put(cx + dx, rim, cz + dz, wall)
  if (smoke) put(cx, rim, cz, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  return { rim, R }
}
