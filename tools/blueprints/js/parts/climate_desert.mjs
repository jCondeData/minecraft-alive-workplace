// Desert parts (parts library, tools/blueprints/REBUILD.md): the pieces a hot, dry town's stone house is known by.
// Grown from the Stone House's desert look (49.10b). Every block comes from the caller's palette.
//   windcatcher  a square tower on a flat roof whose top is cut by tall slots on every face (a badgir: it catches the
//                wind and sends it down into the rooms), a flat cap over it
//   screenOriel  a wooden lattice oriel (a mashrabiya) out over a door or a window: framed posts, a screen of open
//                trapdoors on its face, a little roof, carried on corbels
//   archBay      a round-headed opening between two piers: the two cells under the lintel turned into haunches
import { stair, slab, trapdoor } from './canvas.mjs'

/** windcatcher: a tower x0..x0+2, z0..z0+2 from y0 to y1 (its cap), solid below its slots, then `slot` courses (two or
 *  more) with the middle cell of each face left open (four tall slots) round a solid core, the corners standing; the cap
 *  at y1 a ring of `cap` with `capTop` (a slab) in the middle. `body` may be a mix(). */
export function windcatcher(c, x0, z0, y0, y1, { body, corner = body, cap, capTop = cap, slot = 2 }) {
  const slotLo = y1 - slot, slotHi = y1 - 1
  for (let y = y0; y <= y1; y++) for (let dx = 0; dx <= 2; dx++) for (let dz = 0; dz <= 2; dz++) {
    const x = x0 + dx, z = z0 + dz
    const isCorner = dx !== 1 && dz !== 1, isCore = dx === 1 && dz === 1
    if (y === y1) { isCore ? slab(c, x, y, z, capTop, 'bottom') : c.set(x, y, z, cap); continue }
    if (y >= slotLo && y <= slotHi && !isCorner && !isCore) { c.clear(x, y, z); continue }
    c.set(x, y, z, isCorner ? corner : body)
  }
}

/** screenOriel: a lattice oriel out from a wall, its face the cells x0..x1 on line z (the wall at z + 1 behind it, the
 *  oriel facing north), from y0 (its sill course, on corbels) to y1 (its roof): corner posts of `post`, the middle cells
 *  a screen of open `lattice` trapdoors on the outer face, a roof of `roof` slabs, corbel stairs (`corbel`) under the
 *  sill's ends with their backs to the wall. */
export function screenOriel(c, { x0, x1, z, y0, y1 }, { post, lattice, roof, sill, corbel }) {
  for (let x = x0; x <= x1; x++) {
    c.set(x, y0, z, sill)
    for (let y = y0 + 1; y < y1; y++) {
      if (x === x0 || x === x1) c.set(x, y, z, post, { axis: 'y' })
      else trapdoor(c, x, y, z, lattice, 'south', 'bottom', true) // open, its panel on the cell's outer (north) face
    }
    slab(c, x, y1, z, roof, 'bottom')
  }
  if (corbel) for (const x of [x0, x1]) if (!c.get(x, y0 - 1, z)) stair(c, x, y0 - 1, z, corbel, 'south', 'top')
}

/** archBay: a round-headed arch in an opening x0..x1 on line z (or along z on line x when `axis` is 'z') whose lintel
 *  course is at y: the two end cells of the opening at y become upside-down stairs, their full sides to the piers. */
export function archBay(c, { axis = 'x', line, a0, a1, y }, id) {
  const at = a => (axis === 'x' ? [a, y, line] : [line, y, a])
  const [lo, hi] = axis === 'x' ? ['west', 'east'] : ['north', 'south']
  stair(c, ...at(a0), id, lo, 'top')
  stair(c, ...at(a1), id, hi, 'top')
  for (let a = a0 + 1; a < a1; a++) c.set(...at(a), id.replace(/_stairs$/, ''))
}
