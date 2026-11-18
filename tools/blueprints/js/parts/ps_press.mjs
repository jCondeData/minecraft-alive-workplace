// Print-shop parts (parts library, tools/blueprints/REBUILD.md; grown from the Lantern Press, the Print Shop, 49):
// gable roofs merged as one roof surface (one roof cell per column, so no roof ever overhangs another: B1384), a
// glazed light lantern standing on a ridge, a round-headed inner archway, and the small furniture of a printer's
// room (a low slab counter, a desk with an ink candle, a wall shelf with one). Every block comes from the caller.
import { DIRS, OPP, slab, stair } from './canvas.mjs'

/** psRoofUnion: 45-degree gable roofs (`gables`: [{axis, across: [a0, a1], along: [l0, l1], eaveY}], odd spans) drawn
 *  as one surface: each column takes the highest roof over it, so a lower roof meets a higher one in a valley or
 *  under its slope and never runs on beneath its overhang. The winning roof's stair (rising toward its ridge) or its
 *  ridge block goes in that column (and, where two roofs meet a block apart, a full block under the higher one's
 *  stair); `keep(x, z)` false leaves a column out (a lantern's footprint). Returns
 *  {at(x, z): the roof's y or null, ridge: [ridgeY of each gable]}. */
export function psRoofUnion(c, gables, roof, keep = () => true) {
  const height = (g, x, z) => {
    const [u, v] = g.axis === 'z' ? [x, z] : [z, x]
    if (u < g.across[0] || u > g.across[1] || v < g.along[0] || v > g.along[1]) return null
    return g.eaveY + Math.min(u - g.across[0], g.across[1] - u)
  }
  const best = (x, z) => {
    let top = null, who = null
    for (const g of gables) { const h = height(g, x, z); if (h !== null && (top === null || h > top)) { top = h; who = g } }
    return [top, who]
  }
  const [w, , d] = c.size
  for (let x = 0; x < w; x++) for (let z = 0; z < d; z++) {
    const [y, g] = best(x, z)
    if (y === null || !keep(x, z)) continue
    const u = g.axis === 'z' ? x : z
    const mid = (g.across[0] + g.across[1]) / 2
    if (u === mid) { c.set(x, y, z, roof.full); continue }
    const rise = u < mid ? (g.axis === 'z' ? 'east' : 'south') : (g.axis === 'z' ? 'west' : 'north')
    stair(c, x, y, z, roof.stairs, rise)
  }
  // where two roofs meet in a valley one block apart, the higher one's stair gets a full block under it (no slot)
  for (let x = 0; x < w; x++) for (let z = 0; z < d; z++) {
    const [y, g] = best(x, z)
    if (y === null || !keep(x, z)) continue
    for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const [ny, ng] = best(x + dx, z + dz)
      if (ny !== null && ng !== g && ny < y && !c.get(x, y - 1, z)) c.set(x, y - 1, z, roof.full)
    }
  }
  return { at: (x, z) => best(x, z)[0], ridge: gables.map(g => g.eaveY + (g.across[1] - g.across[0]) / 2) }
}

/** psLightLantern: a square lantern standing in a roof (x0..x1, z0..z1, its ring the outermost cells): ring walls of
 *  `wall` from the roof surface (`base(x, z)`, the roof's y there) up to top - 1, glazed with `pane` between the
 *  corners from glazed[0] to glazed[1] (the brick below is its curb), `corner` up the four corners, and at `top` a cap ring of `cap` stairs rising inward round a
 *  glazed light (`glass` over every inner column, the only block over them: the shaft below stays open to the room).
 *  `hang`: inner cells [x, z] that get a lantern hung from the glass (never the press's own column). */
export function psLightLantern(c, { x0, z0, x1, z1, top, glazed = null, base }, { wall, corner = wall, glass, pane = null, cap, hang = [] }) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const ring = x === x0 || x === x1 || z === z0 || z === z1
    if (!ring) { c.set(x, top, z, glass); continue }
    const isCorner = (x === x0 || x === x1) && (z === z0 || z === z1)
    for (let y = base(x, z); y < top; y++) {
      const lit = !isCorner && pane && glazed && y >= glazed[0] && y <= glazed[1] && y > base(x, z)
      c.set(x, y, z, lit ? pane : isCorner ? corner : wall)
    }
    const facing = z === z0 ? 'south' : z === z1 ? 'north' : x === x0 ? 'east' : 'west'
    stair(c, x, top, z, cap.stairs, facing)
  }
  for (const [x, z] of hang) c.set(x, top - 1, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
}

/** psArch: an opening through a wall (axis 'x': the wall z = line, spanning x u0..u1; 'z': the wall x = line) from y0
 *  to y1, its top corners eased by upside-down stairs of `id` (each backed into the jamb), so it reads as an arch. */
export function psArch(c, axis, line, [u0, u1], y0, y1, id) {
  const at = (u, y) => (axis === 'x' ? [u, y, line] : [line, y, u])
  for (let u = u0; u <= u1; u++) for (let y = y0; y <= y1; y++) c.clear(...at(u, y))
  if (u1 - u0 < 2) return
  const [lo, hi] = axis === 'x' ? ['west', 'east'] : ['north', 'south']
  stair(c, ...at(u0, y1), id, lo, 'top')
  stair(c, ...at(u1, y1), id, hi, 'top')
}

/** psParts: a printer's furniture, each recording its furniture spot like furnitureParts (f: {pos, facing, wood, ...}). */
export const psParts = {
  slabCounter(c, f) { // a low counter of top slabs (f.slab) on the floor, f.length long along `along`; f.on: blocks on it, one per cell
    const [x, y, z] = f.pos
    const along = { north: 'east', east: 'south', south: 'west', west: 'north' }[f.facing]
    const [ax, az] = DIRS[f.along ?? along]
    for (let i = 0; i < (f.length ?? 1); i++) {
      const p = [x + ax * i, y, z + az * i]
      c.free(p)
      slab(c, ...p, f.slab, 'top')
      c.spot('counter', p, f.facing, [[0, 0, 0]], f.wood)
      const on = f.on?.[i]
      if (on) { c.free([p[0], y + 1, p[2]]); c.set(p[0], y + 1, p[2], ...(Array.isArray(on) ? on : [on])) }
    }
  },
  inkDesk(c, f) { // a top slab against the wall with f.candle (an ink pot) on it
    const [x, y, z] = f.pos
    c.free([x, y, z], [x, y + 1, z])
    slab(c, x, y, z, `${f.wood}_slab`, 'top')
    c.set(x, y + 1, z, f.candle, { candles: String(f.candles ?? 1), lit: 'true', waterlogged: 'false' })
    c.spot('desk', f.pos, f.facing, [[0, 0, 0]], f.wood)
  },
  inkShelf(c, f) { // a top slab on the wall with f.candle on it (ink, not crockery)
    const [x, y, z] = f.pos
    c.free([x, y, z], [x, y + 1, z])
    slab(c, x, y, z, `${f.wood}_slab`, 'top')
    c.set(x, y + 1, z, f.candle, { candles: String(f.candles ?? 1), lit: 'true', waterlogged: 'false' })
    c.spot('shelf', f.pos, f.facing, [[0, 0, 0]], f.wood)
  },
}

/** psShopSign: a short bracket (`arm`, a fence) out from a wall at [x, y, z] toward `out`, a lantern hung under it. */
export function psBracketLamp(c, [x, y, z], out, arm) {
  const [ox, oz] = DIRS[out]
  c.set(x + ox, y, z + oz, arm)
  c.set(x + ox, y - 1, z + oz, 'lantern', { hanging: 'true', waterlogged: 'false' })
  return OPP[out]
}
