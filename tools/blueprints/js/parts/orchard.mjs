// Orchards and berry gardens (parts library, tools/blueprints/REBUILD.md; grown from the Berry Farm, 49): raised
// berry beds with a log kerb, a pergola of posts, beams and tapered rafters, and a hedge cap. Every block comes from the
// caller's palette; only the plants the game reads as themselves (the sweet berry bush it is given) are set here, and
// even those are passed in.
import { stair } from './canvas.mjs'

/** berryBed: a raised bed: `soil` at y on each [x, z] cell (a block or a mix()), a `bush` on it at y + 1 with
 *  `props` (a sweet berry bush: { age: '3' }, ripe the day it is built). A bush needs grass, dirt, podzol, coarse dirt or
 *  farmland under it: pass one of those. Returns the bush cells [x, y + 1, z]. */
export function berryBed(c, cells, y, { soil, bush, props = {} }) {
  const out = []
  for (const [x, z] of cells) {
    const s = typeof soil === 'function' ? soil(x, y, z) : soil
    c.set(x, y, z, s, s.endsWith('grass_block') || s.endsWith('podzol') ? { snowy: 'false' } : {})
    if (bush) { c.set(x, y + 1, z, bush, props); out.push([x, y + 1, z]) }
  }
  return out
}

/** kerb: a log lying along `axis` ('x' or 'z') on the line `at`, from a to b at y: the low timber edge of a raised bed.
 *  `skip(i)` leaves a cell (a post stands there). */
export function kerb(c, axis, at, a, b, y, log, skip = () => false) {
  for (let i = Math.min(a, b); i <= Math.max(a, b); i++) {
    if (skip(i)) continue
    const [x, z] = axis === 'x' ? [i, at] : [at, i]
    c.set(x, y, z, log, { axis })
  }
}

/** pergola: an open timber walk. `posts`: [[x, z]] standing y0..beamY - 1 (logs); `beams`: [{ axis, at, a, b }] logs
 *  at beamY running along `axis` on the line `at` from a to b, an end given in `taper` (a or b) cut back as an
 *  upside-down stair so it reads as a beam's end, not a block; `rafters`: [{ axis, at, a, b, taper }] the same one up
 *  (beamY + 1), both ends tapered unless `taper` says otherwise ('' keeps logs: rafters every other row with tapered
 *  ends read to holes.py as gaps between roofs). `log` (beams and rafters), `post` (default: log) and `stairs` from
 *  the palette. Returns the rafter cells (what vines can hang from). */
export function pergola(c, { posts = [], y0 = 1, beamY, beams = [], rafters = [] }, { log, stairs, post = log }) {
  for (const [x, z] of posts) for (let y = y0; y < beamY; y++) c.set(x, y, z, post, { axis: 'y' })
  const run = (r, y, ends) => {
    const cells = []
    for (let i = r.a; i <= r.b; i++) {
      const [x, z] = r.axis === 'x' ? [i, r.at] : [r.at, i]
      const end = (i === r.a && ends.includes('a')) || (i === r.b && ends.includes('b'))
      if (end) {
        const facing = r.axis === 'x' ? (i === r.a ? 'east' : 'west') : (i === r.a ? 'south' : 'north')
        stair(c, x, y, z, stairs, facing, 'top')
      } else { c.set(x, y, z, log, { axis: r.axis }); cells.push([x, y, z]) }
    }
    return cells
  }
  for (const b of beams) run(b, beamY, b.taper ?? '')
  const held = []
  for (const r of rafters) held.push(...run(r, beamY + 1, r.taper ?? 'ab'))
  return held
}
