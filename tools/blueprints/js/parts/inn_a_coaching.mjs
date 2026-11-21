// Coaching-inn parts (parts library, tools/blueprints/REBUILD.md), grown from the Inn (Version A, ROADMAP 49): a jetty's
// brackets and moulding, a bar run with its job block, a turning stair, a gallery on posts, stable stalls, cask racks
// and a sign post. Every block comes from the family's palette; none is picked here.
import { DIRS, OPP, stair } from './canvas.mjs'

/** jetty: what holds an upper storey out over the wall below it along line z (x0..x1, the storey's beam at y): a carved
 *  bracket (an upside-down `trim` stair, its tall side against the wall at `wallZ`) under the beam at each x of `at`,
 *  and a moulding of upside-down stairs a block further out at the beam's height (`moulding`: the cell line in front,
 *  a run of 9 or more so sills and shutters above it are held). `out`: the side the jetty faces. */
export function jetty(c, { x0, x1, z, y, out = 'north', at = [] }, { trim, moulding = true }) {
  const [, dz] = DIRS[out]
  for (const x of at) stair(c, x, y - 1, z, `${trim}_stairs`, OPP[out], 'top')
  if (moulding) for (let x = x0; x <= x1; x++) stair(c, x, y, z + dz, `${trim}_stairs`, OPP[out], 'top')
}

/** barRun: the bar along line x from z0 to z1 at y: `plank` blocks with the job block (`job`, facing `facing`, its
 *  front into the room) at `jobZ`; `gap` z values are left open (the flap into the walk-in space). */
export function barRun(c, { x, y, z0, z1, jobZ, facing, gap = [] }, { plank, job }) {
  for (let z = z0; z <= z1; z++) {
    if (gap.includes(z)) continue
    if (z === jobZ) c.set(x, y, z, job, { facing })
    else c.set(x, y, z, plank)
  }
}

/** turningStair: a stair that turns in a small room: each step [x, y, z, rises] is a `wood` stair rising the way
 *  given, solid `under` beneath it down to y0 (a stone newel under the turns). */
export function turningStair(c, steps, wood, under, y0 = 1) {
  for (const [x, y, z, rises] of steps) {
    stair(c, x, y, z, `${wood}_stairs`, rises)
    for (let yy = y0; yy < y; yy++) c.set(x, yy, z, under)
  }
}

/** gallery: a timber deck along a wall (x0..x1 across, z0..z1 along, its floor at y): `deck` boards, posts (`post`, a
 *  log) at each z of `posts` on the outer line (x1) from the ground to the rail, a fence rail along the outer line
 *  (the posts' cells and `gap` z values left open), and a pentice of `roof` stairs over the outer line at `roofY`
 *  falling away from the wall (east, `out`). */
export function gallery(c, { x0, x1, z0, z1, y, posts = [], gap = [], roofY = null, out = 'east' }, { deck, post, rail, roof = null }) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) c.set(x, y, z, deck)
  for (const z of posts) for (let yy = 1; yy < y; yy++) c.set(x1, yy, z, post, { axis: 'y' })
  for (let z = z0; z <= z1; z++) {
    if (gap.includes(z)) continue
    if (posts.includes(z)) c.set(x1, y + 1, z, post, { axis: 'y' })
    else c.set(x1, y + 1, z, rail)
  }
  if (roof && roofY !== null) for (let z = z0; z <= z1; z++) if (!gap.includes(z)) stair(c, x1, roofY, z, roof.stairs, OPP[out])
}

/** stalls: open-fronted stable stalls along a wall line (the stalls' open side at x = `front`, their back wall at
 *  `back`), split at each z of `posts` by a fence partition from front to back; each stall gets a hay bale in its back
 *  corner and a trough (a `trough` block) beside it. y: the floor's top layer (stalls stand on y - 1). */
export function stalls(c, { front, back, posts, y = 1 }, { fence, hay, trough }) {
  const dx = back > front ? 1 : -1
  for (const z of posts) for (let x = front + dx; x !== back; x += dx) c.set(x, y, z, fence)
  for (let i = 0; i + 1 < posts.length; i++) {
    const za = posts[i] + 1
    c.set(back - dx, y, za, hay, { axis: 'y' })
    if (posts[i + 1] - 1 > za) c.set(back - dx, y, za + 1, trough)
  }
}

/** caskRack: barrels lying on their sides along line x from z0 to z1, two high, their ends facing `facing` (the
 *  aisle), on a `base` sill. */
export function caskRack(c, { x, y, z0, z1, facing, high = 2 }, { base = null } = {}) {
  for (let z = z0; z <= z1; z++) {
    if (base) c.set(x, y - 1, z, base)
    for (let h = 0; h < high; h++) c.set(x, y + h, z, 'barrel', { facing, open: 'false' })
  }
}

/** signPost: a fence post `height` tall from y with a standing banner on top turned to `rotation` (8: the street). */
export function signPost(c, [x, y, z], height, fence, banner, rotation) {
  for (let i = 0; i < height; i++) c.set(x, y + i, z, fence)
  c.set(x, y + height, z, banner, { rotation: String(rotation) })
}

/** gatePiers: two stone piers (x of `piers`, line z) from y 0 to `beamY` - 1, a beam (`beam`, a log along x) across
 *  their tops at beamY, a little roof of `roof` stairs over it from pier to pier falling to the front and back, a
 *  lantern under the beam's middle. */
export function gatePiers(c, { piers: [a, b], z, beamY }, { stone, beam, roof }) {
  for (const x of [a, b]) for (let y = 0; y < beamY; y++) c.set(x, y, z, stone)
  for (let x = a; x <= b; x++) c.set(x, beamY, z, beam, { axis: 'x' })
  for (let x = a; x <= b; x++) {
    stair(c, x, beamY + 1, z - 1, roof.stairs, 'south')
    stair(c, x, beamY + 1, z + 1, roof.stairs, 'north')
    c.set(x, beamY + 1, z, roof.full)
  }
  c.set(Math.floor((a + b) / 2), beamY - 1, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
}
