// Square rings and open-centred roofs (parts library, tools/blueprints/REBUILD.md; grown from the Waystone Plaza, 52):
// a square ring of cells round a centre, a ring of roof stairs (or slabs, for a flat roof) one course of a hipped roof,
// and a bench of stair seats along either axis with stone arms. Every block comes from the caller's palette.
import { OPP, slab, stair } from './canvas.mjs'

/** squareRing: the [x, z] cells r out from (cx, cz) (Chebyshev distance exactly r; r = 0 is the centre itself). */
export function squareRing(cx, cz, r) {
  if (r === 0) return [[cx, cz]]
  const out = []
  for (let x = cx - r; x <= cx + r; x++) for (let z = cz - r; z <= cz + r; z++)
    if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) === r) out.push([x, z])
  return out
}

/** inwardFacing: the stair facing whose high side points at the centre from (x, z) (sides; a corner takes its z side,
 *  and finish() turns corners into outer-corner shapes). */
export function inwardFacing(x, z, cx, cz) {
  const dx = x - cx, dz = z - cz
  if (Math.abs(dz) >= Math.abs(dx)) return dz < 0 ? 'south' : 'north'
  return dx < 0 ? 'east' : 'west'
}

/** hipCourse: one course of a square hipped roof r out from (cx, cz) at y: `roof.stairs` with the high side toward
 *  the centre (a step of the slope), or with `roof.flat` a `roof.slab` of `roof.type` ('top' a fascia under the course
 *  above, 'bottom' a flat lid). skip(x, z) leaves a cell for the caller. */
export function hipCourse(c, cx, cz, r, y, roof, skip = () => false) {
  for (const [x, z] of squareRing(cx, cz, r)) {
    if (skip(x, z)) continue
    if (roof.flat) slab(c, x, y, z, roof.slab, roof.type ?? 'bottom')
    else stair(c, x, y, z, typeof roof.stairs === 'function' ? roof.stairs(x, y, z) : roof.stairs, inwardFacing(x, z, cx, cz))
  }
}

/** armedBench: stair seats on `cells` ([x, z], in a straight run) at y, the sitters facing `facing`, with an `arm`
 *  block (capped by a bottom `cap` slab) on each of `arms` ([x, z]). Never a lone stair: a run of seats between arms. */
export function armedBench(c, cells, y, facing, { seat, arm, cap = null, arms = [] }) {
  for (const [x, z] of cells) { c.free([x, y, z]); stair(c, x, y, z, seat, OPP[facing]) }
  for (const [x, z] of arms) {
    c.set(x, y, z, arm)
    if (cap) slab(c, x, y + 1, z, cap)
  }
}
