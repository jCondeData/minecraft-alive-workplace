// Climate roofs (parts library, tools/blueprints/REBUILD.md): a hipped roof (45 degrees or low) with deep eaves on all four sides (hot,
// dry climates: shade all round, a veranda under the front eave), and a flat mud roof with a parapet and the beam ends
// (toron) that stick out of a Sahel wall. Grown from the Starter Cottage's savanna look (49.10a). Every block comes
// from the caller's palette.
import { stair } from './canvas.mjs'

/** hipRoof: a hipped roof over x0..x1, z0..z1 (the eave ring at eaveY, its outermost cells), with eaves on all four
 *  sides and no gables. 45 degrees (default): each ring inward one course higher, a `roof.stairs` rising toward the
 *  middle, the top ring (the ridge, or the peak) in `roof.full`; rebuild.py's finish() turns the corner stairs into hip
 *  corners. low: half that pitch, each ring half a block higher in `roof.slab` (bottom, top, bottom, ...), the low
 *  spreading roof of a hot country. `keep(x, y, z)` false leaves a cell to the caller (a tower wall the roof runs into).
 *  Returns under(x, z): the y of the roof's cell over each column, and under.top(x, z): true where that cell is a top
 *  slab (a wall standing under it should take that cell too, or a half-block gap shows over the wall). */
export function hipRoof(c, { x0, z0, x1, z1, eaveY, low = false }, roof, keep = () => true) {
  const ring = (x, z) => Math.min(x - x0, x1 - x, z - z0, z1 - z)
  const top = Math.min(Math.floor((x1 - x0) / 2), Math.floor((z1 - z0) / 2))
  const at = (x, z) => { const d = Math.min(ring(x, z), top); return low ? eaveY + Math.floor(d / 2) : eaveY + d }
  const half = (x, z) => low && Math.min(ring(x, z), top) % 2 === 1
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const d = Math.min(ring(x, z), top), y = at(x, z)
    if (!keep(x, y, z)) continue
    if (low) { c.set(x, y, z, roof.slab, { type: half(x, z) ? 'top' : 'bottom', waterlogged: 'false' }); continue }
    if (d >= top) { c.set(x, y, z, roof.full); continue }
    const face = d === x - x0 ? 'east' : d === x1 - x ? 'west' : d === z - z0 ? 'south' : 'north'
    stair(c, x, y, z, roof.stairs, face)
  }
  const under = (x, z) => at(x, z)
  under.top = half
  return under
}

/** flatRoof: a flat roof over x0..x1, z0..z1 at y (`deck`), a parapet one high round its edge (`wall`) with a gap
 *  every `gap` cells (the gaps let the rain off), and beam ends (`beam`, a fence that joins the wall behind it) one
 *  block out of the walls at y - 1, every `every` cells on the `sides` named: the toron of a mud wall. `skip(x, z)` leaves a parapet cell. */
export function flatRoof(c, { x0, z0, x1, z1, y }, { deck, wall, beam }, { gap = 0, every = 2, skip = () => false, sides = ['north', 'south', 'west', 'east'] } = {}) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    c.set(x, y, z, deck)
    const edge = x === x0 || x === x1 || z === z0 || z === z1
    if (!edge || skip(x, z)) continue
    const corner = (x === x0 || x === x1) && (z === z0 || z === z1)
    const along = z === z0 || z === z1 ? x - x0 : z - z0
    if (!corner && gap && along % gap === 0) continue
    c.set(x, y + 1, z, wall)
  }
  if (!beam) return
  const on = s => sides.includes(s)
  for (let x = x0 + 1; x < x1; x += every) { if (on('north')) c.set(x, y - 1, z0 - 1, beam); if (on('south')) c.set(x, y - 1, z1 + 1, beam) }
  for (let z = z0 + 1; z < z1; z += every) { if (on('west')) c.set(x0 - 1, y - 1, z, beam); if (on('east')) c.set(x1 + 1, y - 1, z, beam) }
}
