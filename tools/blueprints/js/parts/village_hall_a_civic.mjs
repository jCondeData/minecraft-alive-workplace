// Civic parts (parts library, tools/blueprints/REBUILD.md; grown from the Village Hall, Version A, 49): a meeting bell
// hung from a beam, a pot hung on a chain, pews, a hearth set in a wall with its stack outside, a timber belfry with
// arched openings, and a needle spire. Every block comes from the caller's palette; none is picked here.
import { DIRS, OPP, stair } from './canvas.mjs'
import { tentRoof } from './pavilions.mjs'
import { benchRun } from './seats.mjs'

/** meetingBell: a working bell at [x, y, z] hanging from the block above it (attachment 'ceiling'), or standing on the
 *  block below ('floor'). A bell is a point of interest: it records no furniture spot. */
export function meetingBell(c, [x, y, z], { facing = 'north', attachment = 'ceiling' } = {}) {
  c.set(x, y, z, 'bell', { attachment, facing, powered: 'false' })
}

/** civicParts: furniture pieces for furnitureParts-style plans ({kind, part, pos | at, facing, wood, ...}). */
export const civicParts = {
  hangingPot(c, f) { // a potted plant on a chain from the beam above f.pos; the spot is the chain's cell
    const [x, y, z] = f.pos
    c.free([x, y, z], [x, y - 1, z])
    c.set(x, y, z, 'chain', { axis: 'y', waterlogged: 'false' })
    c.set(x, y - 1, z, f.plant)
    c.spot('hanging_pot', f.pos, 'north', [[0, 0, 0], [0, -1, 0]], f.wood)
  },
  bench(c, f) { // a pew or porch bench: a row of stair seats along x (f.at: {x0, x1, y, z}), each a bench spot
    benchRun(c, { ...f.at, facing: f.facing }, { seat: `${f.wood}_stairs`, spot: true, wood: f.wood })
  },
}

/** wallHearth: a fire set into a wall: a campfire in the wall cell [x, y, z] facing into the room (`into`), dressed
 *  jambs either side and a lintel over it, and the stack built outside against the wall (one block beyond, away from
 *  the room) from the ground up to `top`, smoking from a campfire on its top. */
export function wallHearth(c, [x, y, z], into, top, { stone, dressed }) {
  const [ox, oz] = DIRS[OPP[into]] // outward
  const [ax, az] = [Math.abs(oz), Math.abs(ox)] // along the wall
  c.set(x, y, z, 'campfire', { facing: into, lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  for (const d of [-1, 1]) for (let yy = y; yy <= y + 1; yy++) c.set(x + ax * d, yy, z + az * d, dressed)
  c.set(x, y + 1, z, dressed)
  for (let yy = 0; yy <= top; yy++) c.set(x + ox, yy, z + oz, stone)
  c.set(x + ox, top + 1, z + oz, 'campfire', { facing: into, lit: 'true', signal_fire: 'false', waterlogged: 'false' })
}

/** belfry: an open timber stage over x0..x1, z0..z1 from y0 to top: corner posts (`post`, a log), a beam ring and a
 *  ceiling of `ceiling` at top, and on every face an arched opening between the corners: a `rail` (fence) balustrade
 *  at y0, open above it, the arch's shoulders upside-down `brace` stairs under the beam against each post. */
export function belfry(c, { x0, x1, z0, z1, y0, top }, { post, rail, brace, ceiling }) {
  for (const [x, z] of [[x0, z0], [x1, z0], [x0, z1], [x1, z1]]) for (let y = y0; y < top; y++) c.set(x, y, z, post, { axis: 'y' })
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const edge = x === x0 || x === x1 || z === z0 || z === z1
    if (!edge) { c.set(x, top, z, ceiling); continue }
    const corner = (x === x0 || x === x1) && (z === z0 || z === z1)
    c.set(x, top, z, post, { axis: corner ? 'y' : (z === z0 || z === z1) ? 'x' : 'z' })
    if (corner) continue
    c.set(x, y0, z, rail)
  }
  for (const z of [z0, z1]) { stair(c, x0 + 1, top - 1, z, brace, 'west', 'top'); stair(c, x1 - 1, top - 1, z, brace, 'east', 'top') }
  for (const x of [x0, x1]) { stair(c, x, top - 1, z0 + 1, brace, 'north', 'top'); stair(c, x, top - 1, z1 - 1, brace, 'south', 'top') }
}

/** needleSpire: a broach spire over a square tower top (tentRoof with cut >= its radius, so its rings stay square):
 *  heights[ring] from the needle (ring 0) to the eave ring, the needle's shaft filled solid under its top, and a
 *  `finial` above it. Returns tentRoof's {level, at}. */
export function needleSpire(c, { cx, cz, heights }, roof, finial = null) {
  const R = heights.length - 1
  const r = tentRoof(c, { cx, cz, cut: R, heights }, roof)
  for (let y = heights[1] + 1; y < heights[0]; y++) c.set(cx, y, cz, roof.full)
  if (finial) c.set(cx, heights[0] + 1, cz, finial)
  return r
}

/** pewRows: rows of bench seats (civicParts.bench), the sitters facing `facing`; rows: [{x0, x1, y, z}]. */
export function pewRows(c, rows, facing, wood) { for (const at of rows) civicParts.bench(c, { at, facing, wood }) }

/** civicDormer: a gabled dormer on a 45-degree slope whose ridge runs along z (grown from the Village Hall A): its face
 *  on the wall line x = `face` (posts either side of a two-high window at z, a wall apex over it, from y0), its little
 *  roof (ridge along x, at y0 + 3) from the eave line x = `eave` inward until it meets the main slope (`roofY(x)`: the
 *  main roof's stair height over column x), and the main roof cut away behind the window so it lights the room. */
export function civicDormer(c, { face, eave, z, y0, roofY }, { roof, wall, post, glass }) {
  const dir = eave > face ? 1 : -1 // outward
  const rise = { [-2]: 'south', [-1]: 'south', 2: 'north', 1: 'north' }
  for (let x = face - dir; Math.abs(x - face) < 6; x -= dir) if (roofY(x) <= y0 + 1) c.clear(x, roofY(x), z); else break
  for (const dz of [-1, 1]) for (let y = y0; y <= y0 + 1; y++) c.set(face, y, z + dz, post, { axis: 'y' })
  c.set(face, y0, z, glass); c.set(face, y0 + 1, z, glass)
  c.set(face, y0 + 2, z, wall)
  for (const dz of [-2, -1, 0, 1, 2]) {
    const y = y0 + 3 - Math.abs(dz)
    for (let x = eave; roofY(x) < y; x -= dir) {
      if (dz === 0) c.set(x, y, z, roof.full)
      else stair(c, x, y, z + dz, roof.stairs, rise[dz])
    }
  }
}
