// Wagons, cages and camp gear (parts library, tools/blueprints/REBUILD.md; grown from the Prisoner Cage, 49): a disc
// cart wheel, a cart bed on two beams, a barred cage between corner posts, its shafts, a stepped canvas ridge tent and
// a war standard. Every block comes from the caller's palette; none is picked here.
import { slab, stair } from './canvas.mjs'

// the plane a wheel turns in: axle 'x' -> the wheel stands in y-z (u runs along z), axle 'z' -> in x-y (u along x)
const inPlane = (axle, [x, y, z], u, dy) => (axle === 'x' ? [x, y + dy, z + u] : [x + u, y + dy, z])
const lowSide = { x: ['south', 'north'], z: ['east', 'west'] } // the stair facing that rounds the -u and +u corners

/** cartWheel: a 3 x 3 disc wheel centred on `hub` [x, y, z], turning about an axle along `axle` ('x' or 'z'): the hub
 *  a log end on the axle (`hub` id), the rim's four arms full `rim.full` blocks, its four corners `rim.stairs` turned
 *  so the disc reads round.
 *  Its bottom row may sit in the ground layer (a wheel bogged to the hub). */
export function cartWheel(c, hub, axle, { rim, hubLog }) {
  const [x, y, z] = hub
  c.set(x, y, z, hubLog, { axis: axle })
  for (const [u, dy] of [[-1, 0], [1, 0], [0, -1], [0, 1]]) c.set(...inPlane(axle, hub, u, dy), rim.full)
  const [toPlus, toMinus] = lowSide[axle]
  for (const u of [-1, 1]) for (const dy of [-1, 1]) {
    const [cx, cy, cz] = inPlane(axle, hub, u, dy)
    stair(c, cx, cy, cz, rim.stairs, u < 0 ? toPlus : toMinus, dy > 0 ? 'bottom' : 'top')
  }
}

/** cartBed: a cart's floor at y from (x0, z0) to (x1, z1), running along `along`: a beam (`beam`, a log along the
 *  cart) down each long edge and `deck` planks between. Returns the deck's cells. */
export function cartBed(c, { x0, x1, z0, z1, y, along = 'z' }, { beam, deck }) {
  const cells = []
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const edge = along === 'z' ? x === x0 || x === x1 : z === z0 || z === z1
    if (edge) c.set(x, y, z, beam, { axis: along })
    else { c.set(x, y, z, deck); cells.push([x, y, z]) }
  }
  return cells
}

/** barCage: the walls of a cage round (x0, z0)..(x1, z1) from y0 to y1: a post (`post`, a log upright) at each corner
 *  and `bars` in every other wall cell, so each run of bars ends against a post (no bar sticks out into the air).
 *  `solid(x, y, z)` returns a block id for a wall cell that is boarded rather than barred. Returns the bar cells. */
export function barCage(c, { x0, x1, z0, z1, y0, y1 }, { post, bars }, solid = () => null) {
  const out = []
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const wall = x === x0 || x === x1 || z === z0 || z === z1
    if (!wall) continue
    const corner = (x === x0 || x === x1) && (z === z0 || z === z1)
    for (let y = y0; y <= y1; y++) {
      if (corner) { c.set(x, y, z, post, { axis: 'y' }); continue }
      const board = solid(x, y, z)
      if (board) { c.set(x, y, z, board); continue }
      c.set(x, y, z, bars); out.push([x, y, z])
    }
  }
  return out
}

/** cageLid: the cage's top at y over (x0, z0)..(x1, z1): a ring of `beam` logs on the walls (along each wall) and
 *  `deck` planks inside, the ceiling the prisoner looks up at. */
export function cageLid(c, { x0, x1, z0, z1, y }, { beam, deck }) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (x === x0 || x === x1) c.set(x, y, z, beam, { axis: 'z' })
    else if (z === z0 || z === z1) c.set(x, y, z, beam, { axis: 'x' })
    else c.set(x, y, z, deck)
  }
}

/** shafts: a cart's two shafts at x = each of `xs`, leaving the bed at z0 (y) and dropping to the ground at z1: a top
 *  slab where they leave the bed, bottom slabs where they lie on the ground, with a top-slab splinter bar joining
 *  them under the bed and a bottom-slab swingletree joining their ends. `slabId` is the wood's slab. */
export function shafts(c, { xs: [xa, xb], y, z0, z1 }, slabId) {
  for (const x of [xa, xb]) for (let z = z0; z <= z1; z++) slab(c, x, y, z, slabId, z === z0 ? 'top' : 'bottom')
  for (let x = xa + 1; x < xb; x++) { slab(c, x, y, z0, slabId, 'top'); slab(c, x, y, z1, slabId, 'bottom') }
}

/** ridgeTent: a stepped canvas tent across `across` [a0, a1] running `along` [l0, l1] (axis 'z': across is x; axis
 *  'x': across is z), from y0: each layer one in from the last on both sides until they meet (a ridge; an odd span
 *  peaks in one block). `back` ('l0', 'l1' or null) closes that end with canvas under the slopes; the other stays open
 *  (the mouth). `hem`: a carpet laid on each step's outer edge, softening the steps into a slope. Returns the inside
 *  floor cells. */
export function ridgeTent(c, { axis = 'z', across: [a0, a1], along: [l0, l1], y0 }, canvas, { back = 'l0', hem = null } = {}) {
  const at = (u, y, v) => (axis === 'z' ? [u, y, v] : [v, y, u])
  const inside = []
  const shut = back === 'l0' ? l0 : back === 'l1' ? l1 : null
  for (let i = 0; a0 + i <= a1 - i; i++) {
    const peak = a0 + i >= a1 - i - 1
    for (let v = l0; v <= l1; v++) {
      c.set(...at(a0 + i, y0 + i, v), canvas)
      c.set(...at(a1 - i, y0 + i, v), canvas)
      if (hem && !peak) { c.set(...at(a0 + i, y0 + i + 1, v), hem); c.set(...at(a1 - i, y0 + i + 1, v), hem) }
    }
    if (shut !== null) for (let u = a0 + i + 1; u < a1 - i; u++) c.set(...at(u, y0 + i, shut), canvas)
  }
  for (let u = a0 + 1; u < a1; u++) for (let v = l0; v <= l1; v++) if (v !== shut) inside.push(at(u, y0, v))
  return inside
}

/** warStandard: a camp's standard at [x, y, z]: a `log` upright `logHeight` tall, a `fence` post `fenceHeight` on it,
 *  a standing `banner` on top turned to `rotation`, and a fence arm out toward `arm` ([dx, dz]) with a lantern
 *  (`lantern`) hanging under it. */
export function warStandard(c, [x, y, z], { logHeight, fenceHeight, arm, rotation }, { log, fence, banner, lantern }) {
  for (let i = 0; i < logHeight; i++) c.set(x, y + i, z, log, { axis: 'y' })
  for (let i = 0; i < fenceHeight; i++) c.set(x, y + logHeight + i, z, fence)
  const top = y + logHeight + fenceHeight
  c.set(x, top, z, banner, { rotation: String(rotation) })
  if (arm) {
    const ay = top - 1
    c.set(x + arm[0], ay, z + arm[1], fence)
    c.set(x + arm[0], ay - 1, z + arm[1], lantern, { hanging: 'true', waterlogged: 'false' })
  }
}
