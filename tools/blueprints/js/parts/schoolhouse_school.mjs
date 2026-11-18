// School parts (parts library, tools/blueprints/REBUILD.md; grown from the Schoolhouse, the Lantern Schoolroom, 49):
// a bell-cote on a gable's peak, a blackboard set in a wall, and the teacher's lectern. Every block comes from the
// caller's palette; none is picked here.
import { louvre } from './roofs.mjs'

/** schoolBellcote: an open bell-cote on a ridge (centre cx, cz; its stone base at y, over the ridge at y - 1): the
 *  3 x 3 base (`base`), the roof's full blocks filling under its side columns (`under`, down to the slope's stairs at
 *  y - 2), four fence posts (`post`), a cap of `cap` stairs round a full block, a `crown block and a `finial` on top (it rises to y + 4), and a working bell
 *  hanging from the cap inside. A bell is not a job block and records no furniture spot. */
export function schoolBellcote(c, { cx, cz, y }, { base, under, post, cap, finial = null, bellFacing = 'north' }) {
  for (const dx of [-1, 1]) for (let dz = -1; dz <= 1; dz++) c.set(cx + dx, y - 1, cz + dz, under)
  louvre(c, cx, cz, y, { base, post, roof: cap, finial: null, lamp: false })
  c.set(cx, y + 3, cz, cap.full) // the cap steps up to a point: the stair ring, a crown block, then the finial
  if (finial) c.set(cx, y + 4, cz, finial)
  c.set(cx, y + 1, cz, 'bell', { attachment: 'ceiling', facing: bellFacing, powered: 'false' })
}

/** blackboard: a board (`board`) set flush in a wall line from x0 to x1 (along x, at z) between y0 and y1, framed by
 *  a `ledge` (a bottom slab, the chalk ledge) along its foot on the room side (`into`: +1 or -1 in z), when given. */
export function blackboard(c, { x0, x1, z, y0, y1 }, board, ledge = null, into = -1) {
  for (let x = x0; x <= x1; x++) for (let y = y0; y <= y1; y++) c.set(x, y, z, board)
  if (ledge) for (let x = x0; x <= x1; x++) c.set(x, y0 - 1, z + into, ledge, { type: 'top', waterlogged: 'false' })
}

/** lectern: the Teacher's job block (one only, the brief says), facing the class. Records no furniture spot: no
 *  spot may cover a job block. */
export function lectern(c, [x, y, z], facing) {
  c.free([x, y, z])
  c.set(x, y, z, 'lectern', { facing, has_book: 'false', powered: 'false' })
}
