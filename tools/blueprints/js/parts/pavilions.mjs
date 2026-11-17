// Pavilions: the round-ish open shelters of a park (parts library, tools/blueprints/REBUILD.md; grown from the Gazebo,
// 49): an octagon's ring number for any cell, a tent roof whose rings step in to a point (bell-cast when its profile
// is shallow at the eaves and steep at the top), and the benches and railings that run round its faces. Every block
// comes from the caller's palette; none is picked here.
import { stair } from './canvas.mjs'
import { inward } from './water.mjs'

/** octLevel: the ring an [x, z] cell lies on in an octagon round (cx, cz): max(|dx|, |dz|, |dx| + |dz| - cut). Ring r
 *  is the outline of a (2r + 1)-wide square with its corners cut back by r - cut cells (rings up to `cut` stay square,
 *  bigger ones come out octagonal; cut 2 on ring 4 gives eight posts' worth of corners). */
export const octLevel = (cx, cz, cut) => (x, z) => {
  const dx = Math.abs(x - cx), dz = Math.abs(z - cz)
  return Math.max(dx, dz, dx + dz - cut)
}

/** tentRoof: a roof over every cell of an octagon (octLevel) up to ring heights.length - 1, each ring a course of
 *  stairs rising toward the center at heights[ring] (ring 0, the apex, a full block). Where a ring stands two or more
 *  above the next ring out, full blocks fill under its stair (a steep course); `roof.kick` (optional) is the outermost
 *  ring's stair (a flared eave). `lining` (optional): a board under each shallow course's stair, inside, so the
 *  ceiling reads light and closed. keep(x, y, z) as in gableRoof. Returns {level, at(x, z)}: the ring and its height. */
export function tentRoof(c, { cx, cz, cut, heights }, roof, { lining = null, keep = () => true } = {}) {
  const level = octLevel(cx, cz, cut)
  const R = heights.length - 1
  for (let x = cx - R; x <= cx + R; x++) for (let z = cz - R; z <= cz + R; z++) {
    const L = level(x, z)
    if (L > R || !c.inside(x, heights[L], z)) continue
    const y = heights[L]
    if (L === 0) { if (keep(x, y, z)) c.set(x, y, z, roof.full); continue }
    const id = L === R && roof.kick ? roof.kick : roof.stairs
    if (keep(x, y, z)) stair(c, x, y, z, id, inward(x, z, cx, cz))
    if (L === R) continue
    const below = heights[L + 1]
    for (let yy = below + 1; yy < y; yy++) if (keep(x, yy, z)) c.set(x, yy, z, roof.full)
    if (lining && y - below === 1 && !c.get(x, y - 1, z) && keep(x, y - 1, z)) c.set(x, y - 1, z, lining)
  }
  return { level, at: (x, z) => heights[level(x, z)] }
}

/** faceRun: the cells of one straight face of a ring between two posts (exclusive), as [x, z]: from post a to post b
 *  (both [x, z], on one line along x or z). */
export function faceRun([ax, az], [bx, bz]) {
  if (ax !== bx && az !== bz) throw new Error(`face ${ax},${az} -> ${bx},${bz} isn't straight`)
  const out = []
  if (ax === bx) for (let z = Math.min(az, bz) + 1; z < Math.max(az, bz); z++) out.push([ax, z])
  else for (let x = Math.min(ax, bx) + 1; x < Math.max(ax, bx); x++) out.push([x, az])
  return out
}

/** sideBench: a bench of stair seats on the given [x, z] cells at y, the sitters facing `facing` (any of the four
 *  ways; the stair's backrest is behind them). The cells must be free. */
export function sideBench(c, cells, y, facing, seat) {
  const back = { north: 'south', south: 'north', east: 'west', west: 'east' }[facing]
  for (const [x, z] of cells) { c.free([x, y, z]); stair(c, x, y, z, seat, back) }
}
