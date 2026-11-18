// Tinker's parts (grown from the Tinker's Workshop, ROADMAP 50.3): a hip cap for a square tower, a double chest drawn
// whole, a lamp on a bench top, and a short spur of rail with its stop. Blocks come from the family's palette.
import { DIRS, stair } from './canvas.mjs'

/** hipCap: a square hip (pyramid) cap over a tower centred on (cx, cz): rings of `roof` stairs rising inward, the
 *  outermost `half` from the centre at y0 (an eave a block proud of a tower 2 * half - 1 wide), each next ring a block
 *  in and a block up, and the last ring (one from the centre) round a full block at its own level, so a 7-wide cap is
 *  three courses high. `keep(x, y, z)` as in gableRoof. `apex`: a block on top of the centre (a lightning rod, a
 *  finial), [id, props]. Returns the centre's y. */
export function hipCap(c, cx, cz, half, y0, roof, { keep = () => true, apex = null } = {}) {
  for (let s = half; s >= 1; s--) {
    const y = y0 + (half - s)
    for (let dx = -s; dx <= s; dx++) for (let dz = -s; dz <= s; dz++) {
      if (Math.max(Math.abs(dx), Math.abs(dz)) !== s) continue
      const x = cx + dx, z = cz + dz
      if (!keep(x, y, z)) continue
      const facing = Math.abs(dz) >= Math.abs(dx) ? (dz < 0 ? 'south' : 'north') : (dx < 0 ? 'east' : 'west')
      stair(c, x, y, z, roof.stairs, facing)
    }
  }
  const top = y0 + half - 1
  c.set(cx, top, cz, roof.full)
  if (apex) c.set(cx, top + 1, cz, apex[0], apex[1] ?? {})
  return top
}

/** skirtRing: one course of a hip cap's eave kept round a tower that has grown through it (a pent skirt at a floor
 *  line): the ring `half` from the centre at y, `roof` stairs rising inward. */
export function skirtRing(c, cx, cz, half, y, roof, keep = () => true) {
  for (let dx = -half; dx <= half; dx++) for (let dz = -half; dz <= half; dz++) {
    if (Math.max(Math.abs(dx), Math.abs(dz)) !== half || !keep(cx + dx, y, cz + dz)) continue
    const facing = Math.abs(dz) >= Math.abs(dx) ? (dz < 0 ? 'south' : 'north') : (dx < 0 ? 'east' : 'west')
    stair(c, cx + dx, y, cz + dz, roof.stairs, facing)
  }
}

/** doubleChest: a double chest against a wall, both halves joined: the first half at `pos`, the second one block on
 *  along the wall (`along`: the way to the second half). The game joins a 'left' half to the half clockwise of its
 *  facing, so the types follow from facing and along. Drawn empty (builders never fill containers). */
export function doubleChest(c, [x, y, z], facing, along) {
  const cw = { north: 'east', east: 'south', south: 'west', west: 'north' }[facing]
  const [ax, az] = DIRS[along]
  c.free([x, y, z], [x + ax, y, z + az])
  const first = along === cw ? 'left' : 'right'
  const second = first === 'left' ? 'right' : 'left'
  c.set(x, y, z, 'chest', { facing, type: first, waterlogged: 'false' })
  c.set(x + ax, y, z + az, 'chest', { facing, type: second, waterlogged: 'false' })
}

/** lampOn: a lantern standing on the bench or counter top under it (a top slab or a full block: a short lamp never
 *  stands on a floor), recorded as a lamp. */
export function lampOn(c, f) {
  const [x, y, z] = f.pos
  c.free(f.pos)
  c.set(x, y, z, 'lantern', { hanging: 'false', waterlogged: 'false' })
  c.spot('lamp', f.pos, f.facing, [[0, 0, 0]], f.wood)
}

/** railSpur: a straight spur of plain rail along `axis` ('x' or 'z') at y from a to b on the line `at`, on the
 *  `bed` it is laid on (y - 1, solid), the tinker's rails read from the street. */
export function railSpur(c, axis, at, a, b, y, bed) {
  for (let i = a; i <= b; i++) {
    const [x, z] = axis === 'x' ? [i, at] : [at, i]
    c.set(x, y - 1, z, bed)
    c.set(x, y, z, 'rail', { shape: axis === 'x' ? 'east_west' : 'north_south', waterlogged: 'false' })
  }
}
