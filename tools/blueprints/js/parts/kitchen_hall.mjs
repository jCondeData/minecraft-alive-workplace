// Great-kitchen parts (grown from the Lantern Kitchen, ROADMAP 49): a square stone kitchen open to a hipped roof with a
// smoke louvre on its crown, the stone ledges that give its plain walls depth (a battered skirt at the foot, a corbel
// table under the eaves), the tie beams its lamps hang from, and the cold larder's produce. Every block comes from the
// caller's palette; nothing is picked here.
import { stair } from './canvas.mjs'
import { hipCap } from './tinker.mjs'
import { louvre } from './roofs.mjs'

const inward = (x, z, x0, z0, x1, z1) => (z === z0 ? 'south' : z === z1 ? 'north' : x === x0 ? 'east' : 'west')
const onRing = (x, z, x0, z0, x1, z1) => x === x0 || x === x1 || z === z0 || z === z1
const corner = (x, z, x0, z0, x1, z1) => (x === x0 || x === x1) && (z === z0 || z === z1)

/** ledgeRing: one course of `stairs` round the rectangle x0..x1, z0..z1 at y, each with its tall back to the wall
 *  inside it (corners left out: piers or the roof's corner take them). half 'bottom': a battered skirt at a wall's
 *  foot that sheds rain off the plinth; half 'top': a corbel table, the eave's overhang read as carried. Only where
 *  `keep(x, z)` and nothing is drawn yet (a porch, a wing, a pier keeps its cell). */
export function ledgeRing(c, x0, z0, x1, z1, y, stairs, half = 'bottom', keep = () => true) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (!onRing(x, z, x0, z0, x1, z1) || corner(x, z, x0, z0, x1, z1)) continue
    if (!keep(x, z) || c.get(x, y, z)) continue
    stair(c, x, y, z, stairs, inward(x, z, x0, z0, x1, z1), half)
  }
}

/** louvredHip: a 45-degree hip roof over a square centred on (cx, cz), its eave ring `half` out at y0 (a block proud
 *  of walls 2 * half - 1 wide), each ring a block in and a block up, crowned where the last ring would be by a louvre
 *  (roofs.mjs: a solid 3 x 3 sill, open posts, a cap, a lamp inside) that lets the kitchen's smoke out. Inside, the room
 *  is open up to the sill. `keep(x, y, z)` as in gableRoof (a porch or wing roof drawn first keeps its cells).
 *  `cap`: the louvre's own {full, stairs} (a trim apart from the roof's tiles). Returns {sillY, top} (top: the cap's y). */
export function louvredHip(c, cx, cz, half, y0, roof, { base, post, cap = roof, finial = null, lamp = true }, keep = () => true) {
  hipCap(c, cx, cz, half, y0, roof, { keep: (x, y, z) => Math.max(Math.abs(x - cx), Math.abs(z - cz)) >= 2 && keep(x, y, z) })
  const sillY = y0 + half - 1
  louvre(c, cx, cz, sillY, { base, post, roof: cap, finial, lamp })
  return { sillY, top: sillY + 2 }
}

/** tieBeams: two logs crossing a square room at y (the collar a hip roof's thrust is tied with), along x on line `cz`
 *  from x0 to x1 and along z on line `cx` from z0 to z1, the crossing upright: lamps and drying herbs hang from them. */
export function tieBeams(c, cx, cz, [x0, x1], [z0, z1], y, log) {
  for (let x = x0; x <= x1; x++) c.set(x, y, cz, log, { axis: 'x' })
  for (let z = z0; z <= z1; z++) if (z !== cz) c.set(cx, y, z, log, { axis: 'z' })
  c.set(cx, y, cz, log, { axis: 'y' })
}

/** pilasters: flat stone strips standing a block proud of a wall between its skirt and corbel courses, at each [x, z]
 *  from y0 to y1, so a long plain wall reads in bays. */
export function pilasters(c, cells, y0, y1, id) {
  for (const [x, z] of cells) for (let y = y0; y <= y1; y++) c.set(x, y, z, id)
}

/** produce: a larder's heap of whole produce on its floor ([x, y, z, id] cells: pumpkins, melons, hay), each cell on
 *  a drawn block or the floor under it. */
export function produce(c, cells) {
  for (const [x, y, z, id, props] of cells) c.set(x, y, z, id, props ?? (id.endsWith('hay_block') ? { axis: 'y' } : {}))
}

/** hipRolls: the hip lines of a hip roof centred on (cx, cz) laid in full `id` blocks, from ring `r0` (at y0 + half - r0)
 *  out to ring `half` (at y0), and on out to each ring of `more` ([r, y] pairs: a kicked eave), so each hip reads as one
 *  clean roll of dressed stone instead of the corner stairs' broken ends. */
export function hipRolls(c, cx, cz, half, y0, r0, id, more = []) {
  const rings = []
  for (let r = r0; r <= half; r++) rings.push([r, y0 + half - r])
  for (const [r, y] of [...rings, ...more]) for (const sx of [-1, 1]) for (const sz of [-1, 1]) c.set(cx + sx * r, y, cz + sz * r, id)
}
