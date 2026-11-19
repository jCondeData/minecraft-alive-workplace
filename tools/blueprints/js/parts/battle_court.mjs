// Battle courts (parts library, tools/blueprints/REBUILD.md; grown from the Trainer's House, 49): a marked playing
// field with its two trainers' boxes, a covered bench stand along its side, and a sparring dummy. Every block comes
// from the caller's palette; none is picked here.
import { OPP, stair } from './canvas.mjs'

/** courtField: a field over x0..x1, z0..z1 at y (its long axis along z, odd width and depth so it has a true centre):
 *  `turf` everywhere, a `line` across the middle (along x at the centre z) and a ring round the centre spot `r` out
 *  (every cell whose rounded distance from the centre is r, so it reads round), and a box at each end, `boxHalf` out
 *  either side of the centre line and `boxDepth` deep: `near` at the z1 end (the home trainer's), `far` at the z0 end (the challenger's). Returns the
 *  centre [cx, cz]. */
export function courtField(c, { x0, x1, z0, z1, y = 0, r = 2, boxDepth = 2, boxHalf = 1 }, { turf, line, near, far }) {
  const cx = (x0 + x1) / 2, cz = (z0 + z1) / 2
  if (!Number.isInteger(cx) || !Number.isInteger(cz)) throw new Error('courtField needs an odd width and depth')
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const dx = Math.abs(x - cx), dz = Math.abs(z - cz)
    const ring = Math.round(Math.hypot(dx, dz)) === r
    let id = turf
    if (z === cz || ring) id = line
    else if (dx <= boxHalf && z > z1 - boxDepth) id = near
    else if (dx <= boxHalf && z < z0 + boxDepth) id = far
    c.set(x, y, z, id)
  }
  return [cx, cz]
}

/** benchRow: stair seats in the given cells [x, y, z], the sitters facing `facing` (each stair's backrest on the
 *  other side). An outdoor stand's seats: no furniture spots (a decoration that hands out no seat keeps none). */
export function benchRow(c, cells, seat, facing) {
  for (const [x, y, z] of cells) stair(c, x, y, z, seat, OPP[facing])
}

/** sparringDummy: a straw body (`body`, a hay bale) standing on the ground at [x, y, z] with a target (`head`) on
 *  it, for a trainer's Pokémon to practise moves on. Neither block is a job block. */
export function sparringDummy(c, [x, y, z], { body, head }) {
  c.set(x, y, z, body, { axis: 'y' })
  c.set(x, y + 1, z, head, head.endsWith('target') ? { power: '0' } : {})
}
