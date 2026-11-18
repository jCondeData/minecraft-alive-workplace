// Ranch parts (the Ranch, ROADMAP 49): a ranch gate over a way in, a horse trough at ground level, box-stall fronts off
// an aisle, a cupola on a ridge, a hay hood with its hoist beam, and a paddock fence with a man-gap. Every block comes
// from the caller's palette (parts library, tools/blueprints/REBUILD.md).
import { slab, stair } from './canvas.mjs'
import { railingRun } from './railings.mjs'
import { lowPyramid } from './butcher.mjs'

/** ranchGate: two posts (`post`, a log) from y 0 to `top` at x of `posts` on line z, a crossbar (`post` along x) at
 *  `barY` from bar[0] to bar[1], a standing banner on the bar (`banner` id at [x, y, z], `rotation`: 8 faces the
 *  front), and lanterns hung under the bar at `lamps`. The way between the posts stays open: no gate on a way in. */
export function ranchGate(c, { posts: [a, b], z, top, barY, bar: [b0, b1], banner, lamps = [] }, { post, bannerId, rotation = 8 }) {
  for (const x of [a, b]) for (let y = 0; y <= top; y++) c.set(x, y, z, post, { axis: 'y' })
  for (let x = b0; x <= b1; x++) c.set(x, barY, z, post, { axis: 'x' })
  if (banner) c.set(banner[0], banner[1], banner[2], bannerId, { rotation: String(rotation) })
  for (const [x, y, zz] of lamps) c.set(x, y, zz, 'lantern', { hanging: 'true', waterlogged: 'false' })
}

/** horseTrough: water sources in `cells` [[x, z]] at y (standing on the ground or on what is below), every side that
 *  isn't water or already drawn (a fence holds water too) closed by a `rim` log lying along the trough. */
export function horseTrough(c, cells, y, rim) {
  const water = new Set(cells.map(([x, z]) => `${x},${z}`))
  const alongX = cells.every(([, z]) => z === cells[0][1])
  for (const [x, z] of cells) c.set(x, y, z, 'water', { level: '0' })
  for (const [x, z] of cells) for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
    const nx = x + dx, nz = z + dz
    if (water.has(`${nx},${nz}`) || c.get(nx, y, nz)) continue
    c.set(nx, y, nz, rim, { axis: dz !== 0 ? (alongX ? 'x' : 'z') : (alongX ? 'z' : 'x') })
  }
}

/** stallFront: the front of a row of box stalls along the line x (the aisle on one side), between posts already drawn:
 *  for each stall {z0, z1, gate}, a half wall of `board` at y with a `gate` (a fence gate opening along x) at z = gate,
 *  and `rail` (a fence) on it at y + 1, so the horse looks out over its door. `toward`: the aisle's side ('east' or
 *  'west'), the way the gates face. */
export function stallFront(c, x, stalls, y, toward, { board, gate, rail }) {
  for (const s of stalls) for (let z = s.z0; z <= s.z1; z++) {
    if (z === s.gate) c.set(x, y, z, gate, { facing: toward, in_wall: 'false', open: 'false', powered: 'false' })
    else c.set(x, y, z, board)
    c.set(x, y + 1, z, rail)
  }
}

/** cupola: a little vent or lantern-house on a ridge centred on (cx, cz): a 3 x 3 body from y0 up to roofY - 1,
 *  corner `post`s (logs), its middle solid `wall`, `glass` (or louvres: a fence) on the top course's sides and `wall`
 *  on any course under it, and a pyramid of `roof` from `roofY` (one block over the body every side). Returns the
 *  peak's y. */
export function cupola(c, { cx, cz, y0, roofY }, { post, wall, glass, roof }) {
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) for (let y = y0; y < roofY; y++) {
    const corner = dx !== 0 && dz !== 0
    const mid = dx === 0 && dz === 0
    if (corner) c.set(cx + dx, y, cz + dz, post, { axis: 'y' })
    else if (mid || (y === y0 && roofY - y0 > 1)) c.set(cx + dx, y, cz + dz, wall)
    else c.set(cx + dx, y, cz + dz, glass)
  }
  return lowPyramid(c, cx, cz, 2, roofY, roof)
}

/** hayHood: the ridge of a gable roof (ridge along z, the gable at line z0 + 1) carried out over z0 as a little hood:
 *  `roof` stairs either side of the ridge cell at x - 1 and x + 1 (y - 1), the full ridge block at (x, y, z0), barge
 *  slabs under its edges; a hoist beam (`beam`, a log along z) at hoistY from z0 back into the gable, a chain and a
 *  lantern hung from its end. */
export function hayHood(c, { x, y, z0, hoistY, hoistZ1 }, { roof, beam, barge }) {
  stair(c, x - 1, y - 1, z0, roof.stairs, 'east')
  stair(c, x + 1, y - 1, z0, roof.stairs, 'west')
  c.set(x, y, z0, roof.full)
  for (const dx of [-1, 1]) if (!c.get(x + dx, y - 2, z0)) slab(c, x + dx, y - 2, z0, barge, 'top')
  for (let z = z0; z <= hoistZ1; z++) c.set(x, hoistY, z, beam, { axis: 'z' })
  c.set(x, hoistY - 1, z0, 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(x, hoistY - 2, z0, 'lantern', { hanging: 'true', waterlogged: 'false' })
}

/** paddockFence: a fence round the corner points of `ring` (closed: the last point is the first), every corner a post,
 *  a one-block `manGap` left open (a villager walks through it, a horse is too wide), `gates` (fence gates facing
 *  `gateFacing`) on the run, and lamp posts at `lampPosts` [[x, z]]: the fence a block taller there with a lantern on
 *  it. */
export function paddockFence(c, { ring, manGap = null, gates = [], gateFacing = 'east', lampPosts = [] }, { fence, gate }) {
  railingRun(c, ring, null, fence)
  const y = ring[0][1]
  if (manGap) c.clear(manGap[0], manGap[1], manGap[2])
  for (const [x, , z] of gates) c.set(x, y, z, gate, { facing: gateFacing, in_wall: 'false', open: 'false', powered: 'false' })
  for (const [x, z] of lampPosts) {
    c.set(x, y + 1, z, fence)
    c.set(x, y + 2, z, 'lantern', { hanging: 'false', waterlogged: 'false' })
  }
}
