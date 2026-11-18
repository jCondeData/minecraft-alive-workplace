// Butchers, smokehouses and ice houses (parts library, tools/blueprints/REBUILD.md). Grown from the Butcher's Shop
// (49): a smoke tower with a jettied pyramid cap and smoke at its peak, a freezer (an ice-lined cellar), a meat rail of
// hooks, a chopping block, and a stock pen against a wall. Every block comes from the palette passed in.
import { stair, slab } from './canvas.mjs'
import { pyramidRoof } from './roofs.mjs'
import { cellar } from './frames.mjs'
import { railingRun } from './railings.mjs'

/** smokeTower: a square tower (walls x0..x1, z0..z1, odd and square, from y 1 to `top`) of `body` (a mix() works)
 *  with its top course in `soot` (smoke-blackened), upside-down `bracket` stairs under the cap's overhang on the sides
 *  in `brackets` ('north', 'south', 'west', 'east'), and a pyramid cap of `roof` one block out, smoking at its peak
 *  (a lit campfire on the solid core): steep (pyramidRoof, two up per ring) or, with `steep: false`, a 45-degree
 *  cap (lowPyramid) that leaves the walls their height under a low limit. Returns the peak's y. */
export function smokeTower(c, { x0, z0, x1, z1, top }, { body, soot, bracket, roof, brackets = ['north', 'south', 'west', 'east'], steep = true }) {
  for (let y = 1; y <= top; y++)
    for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++)
      if (x === x0 || x === x1 || z === z0 || z === z1) c.set(x, y, z, y === top ? soot : body)
  const sides = {
    north: [[x0, x1], z0 - 1, 'south'], south: [[x0, x1], z1 + 1, 'north'],
    west: [[z0, z1], x0 - 1, 'east'], east: [[z0, z1], x1 + 1, 'west'],
  }
  for (const s of brackets) {
    const [[a, b], line, facing] = sides[s]
    for (let u = a; u <= b; u++) {
      const [x, z] = s === 'north' || s === 'south' ? [u, line] : [line, u]
      stair(c, x, top, z, bracket, facing, 'top')
    }
  }
  const cx = (x0 + x1) / 2, cz = (z0 + z1) / 2, r = (x1 - x0) / 2 + 1
  const peak = steep ? pyramidRoof(c, cx, cz, r, top + 1, roof) : lowPyramid(c, cx, cz, r, top + 1, roof)
  c.set(cx, peak, cz, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  return peak
}

/** freezer: an ice house dug under a floor (a LiftedCanvas): `cellar`'s floor and wall ring (x0..x1, z0..z1, `depth`
 *  deep) in `wall` (packed ice: it never melts), dressed `pier`s at the corners and the middle of each wall, a
 *  `floor` of cold flags with a panel of `ice` (blue ice) in its middle (`panel`: [x0, z0, x1, z1]). Returns the inside
 *  box (rebuild.py --cellar). Plain ice, frosted ice and snow layers melt by a lantern: never use them here. */
export function freezer(c, { x0, z0, x1, z1, depth, panel }, { wall, pier, floor, ice }) {
  const inside = cellar(c, x0, z0, x1, z1, depth, wall, floor)
  const mx = Math.round((x0 + x1) / 2), mz = Math.round((z0 + z1) / 2)
  for (const [x, z] of [[x0, z0], [x1, z0], [x0, z1], [x1, z1], [mx, z0], [mx, z1], [x0, mz], [x1, mz]])
    for (let y = -depth + 1; y <= -1; y++) c.set(x, y, z, pier)
  const [px0, pz0, px1, pz1] = panel
  for (let x = px0; x <= px1; x++) for (let z = pz0; z <= pz1; z++) c.set(x, -depth, z, ice)
  return inside
}

/** meatRail: hooks hanging from a beam: a chain `n` long under each cell [x, y, z] (y: the beam's underside). */
export function meatRail(c, cells, n = 1) {
  for (const [x, y, z] of cells) for (let i = 0; i < n; i++) c.set(x, y - i, z, 'chain', { axis: 'y', waterlogged: 'false' })
}

/** choppingBlock: a butcher's block: an upright stripped `log` with a `board` (a pressure plate) on it. */
export function choppingBlock(c, [x, y, z], log, board) {
  c.free([x, y, z], [x, y + 1, z])
  c.set(x, y, z, log, { axis: 'y' })
  c.set(x, y + 1, z, board, { powered: 'false' })
}

/** stockPen: a fenced pen against a building's wall: the ground (`ground`, a mix() works) under x0..x1, z0..z1 at
 *  y 0, a `fence` on its three open sides (the side at x1 + 1 is the wall it leans on, `wallSide` 'east'), a `gate`
 *  at [x, z] on the front fence opening `gateFacing`. */
export function stockPen(c, { x0, z0, x1, z1, gateAt, gateFacing = 'north' }, { ground, fence, gate }) {
  c.fill(x0, 0, z0, x1, 0, z1, ground)
  railingRun(c, [[x1, 1, z0], [x0, 1, z0], [x0, 1, z1], [x1, 1, z1]], null, fence)
  c.set(gateAt[0], 1, gateAt[1], gate, { facing: gateFacing, in_wall: 'false', open: 'false', powered: 'false' })
}

/** shelter: a flat lean-to of `slab`s at y over x0..x1, z0..z1 on `post`s (fence ids) standing at `posts` [[x, z]]
 *  from y 1 up to y - 1. */
export function shelter(c, { x0, z0, x1, z1, y, posts }, { slab: id, post }, skip = () => false) {
  for (const [x, z] of posts) for (let yy = 1; yy < y; yy++) c.set(x, yy, z, post)
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) if (!skip(x, z)) slab(c, x, y, z, id, 'bottom')
}

/** lowPyramid: a 45-degree four-sided cap centred on (cx, cz): a ring of `roof.stairs` `r` out at y0, each ring one in
 *  and one up, solid `roof.full` inside each ring, to a full block at the peak (y0 + r). Returns the peak's y. */
export function lowPyramid(c, cx, cz, r, y0, roof) {
  const face = (dx, dz) => (Math.abs(dx) >= Math.abs(dz) ? (dx < 0 ? 'east' : 'west') : (dz < 0 ? 'south' : 'north'))
  for (let k = 0; k <= r; k++) {
    const rr = r - k, y = y0 + k
    for (let dx = -rr; dx <= rr; dx++) for (let dz = -rr; dz <= rr; dz++) {
      if (Math.max(Math.abs(dx), Math.abs(dz)) === rr && rr > 0) stair(c, cx + dx, y, cz + dz, roof.stairs, face(dx, dz))
      else c.set(cx + dx, y, cz + dz, roof.full)
    }
  }
  return y0 + r
}
