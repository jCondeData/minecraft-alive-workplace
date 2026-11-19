// Windmills: an octagonal smock tower on a brick drum, its reefing stage, a boat cap with Create's windmill bearing in
// its nose, common sails in a pinwheel and the drive down the tower to a millstone (parts library,
// tools/blueprints/REBUILD.md). Grown from the Windmill (49 / 50.10, a Create build). Create's ids and states are checked
// in libs/create-fabric-6.0.10.0+mc1.21.1.jar: a windmill bearing's `facing` is the side its sails are on and its drive
// leaves from its back; a sail's (`white_sail`, `sail_frame`) `facing` is the bearing's axis; a gearbox connects on the
// four faces off its own `axis`; a millstone takes its drive from below only. Every other block comes from the family's
// palette.

import { DIRS, OPP, stair } from './canvas.mjs'

const CREATE = id => `create:${id}`

/** octagon: an octagon about (cx, cz) `half` blocks from its centre to each flat side, its corners cut so each flat side
 *  is about half as long as the octagon is wide. inside(x, z), ring() (the cells with a side neighbour outside: a closed
 *  wall) and interior(x, z) (inside and not on the ring). */
export function octagon(cx, cz, half) {
  const cut = half + Math.floor(half / 2)
  const inside = (x, z) => {
    const dx = Math.abs(x - cx), dz = Math.abs(z - cz)
    return dx <= half && dz <= half && dx + dz <= cut
  }
  const onRing = (x, z) => inside(x, z) && [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([a, b]) => !inside(x + a, z + b))
  const cells = []
  for (let x = cx - half; x <= cx + half; x++) for (let z = cz - half; z <= cz + half; z++) if (inside(x, z)) cells.push([x, z])
  return {
    cx, cz, half, inside, onRing,
    interior: (x, z) => inside(x, z) && !onRing(x, z),
    cells: () => cells,
    ring: () => cells.filter(([x, z]) => onRing(x, z)),
  }
}

/** inward: the facing of a stair on an octagon's ring cell that rises toward the centre (its high side inward). */
export function inwardOf(oct, x, z) {
  const dx = x - oct.cx, dz = z - oct.cz
  if (Math.abs(dx) >= Math.abs(dz)) return dx < 0 ? 'east' : 'west'
  return dz < 0 ? 'south' : 'north'
}

/** octDrum: an octagon's ring walled from y0 to y1 in `wall` (an id or a (x, y, z) => id mix), skipping cells `keep`
 *  says no to (openings drawn after). */
export function octDrum(c, oct, y0, y1, wall, keep = () => true) {
  for (const [x, z] of oct.ring()) for (let y = y0; y <= y1; y++) if (keep(x, y, z)) c.set(x, y, z, wall)
}

/** octFloor: every cell inside an octagon at y in `floor`, except the `holes` ([x, z]). */
export function octFloor(c, oct, y, floor, holes = []) {
  const skip = new Set(holes.map(([x, z]) => `${x},${z}`))
  for (const [x, z] of oct.cells()) if (!skip.has(`${x},${z}`)) c.set(x, y, z, floor)
}

/** taperLedge: where a tower steps in from `outer` to `inner`, the ledge cells (in outer, not in inner) at y get
 *  `stairs` rising inward: the drip that makes a smock taper read as one sloped skin. */
export function taperLedge(c, outer, inner, y, stairs, keep = () => true) {
  for (const [x, z] of outer.cells()) {
    if (inner.inside(x, z) || !keep(x, y, z)) continue
    stair(c, x, y, z, stairs, inwardOf(outer, x, z))
  }
}

/** reefingStage: a mill's gallery round its tower at deck height y: a deck over every cell of `deck` (an octagon) the
 *  tower doesn't stand on, a `fence` rail round its rim one up, upside-down `bracket` stairs under the rim every
 *  `every` cells (the rim is the cantilever past the drum below), and the cells in `gaps` ([x, z]) left out of the rail. */
export function reefingStage(c, deck, y, { floor, fence, bracket }, { every = 2, gaps = [], skip = () => false } = {}) {
  const open = new Set(gaps.map(([x, z]) => `${x},${z}`))
  for (const [x, z] of deck.cells()) if (!skip(x, z)) c.set(x, y, z, floor)
  deck.ring().forEach(([x, z], i) => {
    if (skip(x, z)) return
    if (!open.has(`${x},${z}`)) c.set(x, y + 1, z, fence)
    if ((x + z) % every === 0) stair(c, x, y - 1, z, bracket, OPP[inwardOf(deck, x, z)], 'top')
  })
}

/** commonSails: four sails in a pinwheel on a hub at [x, y, z] in the plane across `facing` (north/south: the x-y plane;
 *  the bearing behind the hub faces `facing`). Each arm is a `stock` spine `arm` long from the hub, with a `width`-wide
 *  `cloth` blade on its trailing side starting `gap` cells out (a sail-frame lattice edge where `lattice` is set: the
 *  outer row of each blade). The hub is `hub` (any movable block). Returns every sail cell (for sailPlaneCheck). */
export function commonSails(c, [hx, hy, hz], facing, { arm = 6, width = 2, gap = 1, lattice = false }, { stock, cloth, hub, hubProps = {} }) {
  if (facing !== 'north' && facing !== 'south') throw new Error('commonSails: north or south bearings only')
  const props = { facing, waterlogged: 'false' }
  c.set(hx, hy, hz, hub, hubProps)
  const cells = []
  // the up arm in (u, v) = (x, y) offsets, then turned a quarter at a time: (u, v) -> (v, -u)
  const up = []
  for (let v = 1; v <= arm; v++) up.push([0, v, stock])
  for (let v = gap + 1; v <= arm; v++) for (let u = 1; u <= width; u++) up.push([u, v, lattice && u === width ? stock : cloth])
  let shape = up
  for (let q = 0; q < 4; q++) {
    for (const [u, v, id] of shape) { c.set(hx + u, hy + v, hz, id, props); cells.push([hx + u, hy + v, hz]) }
    shape = shape.map(([u, v, id]) => [v, -u, id])
  }
  return cells
}

/** sailPlaneCheck: Create carries every block touching a sail at its sides in the sail's own plane; throw if any block
 *  other than a sail, the hub or another carried cell (`carried`, [x, y, z]) touches one that way. `plane` is the
 *  bearing's axis ('z' for a north or south bearing). */
export function sailPlaneCheck(c, sails, plane, carried = []) {
  const ok = new Set([...sails, ...carried].map(p => p.join(',')))
  const steps = plane === 'z' ? [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0]] : [[0, 0, 1], [0, 0, -1], [0, 1, 0], [0, -1, 0]]
  for (const [x, y, z] of sails) for (const [a, b, d] of steps) {
    const n = [x + a, y + b, z + d]
    if (ok.has(n.join(','))) continue
    const got = c.get(...n)
    if (got && got.id !== 'minecraft:air') throw new Error(`a sail at ${x},${y},${z} would carry ${got.id} at ${n}`)
  }
}

/** windshaftDrive: a windmill bearing at `bearing` facing `facing` (its sails' side), a windshaft of shafts from its back
 *  to a gearbox at `head` (over the tower's centre), the upright shaft down from under the head to a foot gearbox at
 *  `footY`, and a run of shafts along `out` (a direction) to a last gearbox that turns a millstone up over it. Every
 *  part is in one line: every one turns. Returns the millstone's cell. */
export function windshaftDrive(c, { bearing, facing, head, footY, out, run }) {
  const [bx, by, bz] = bearing
  const [hx, hy, hz] = head
  c.set(bx, by, bz, CREATE('windmill_bearing'), { facing })
  const shaftAxis = facing === 'north' || facing === 'south' ? 'z' : 'x'
  const [dx, dz] = DIRS[OPP[facing]]
  for (let x = bx + dx, z = bz + dz; x !== hx || z !== hz; x += dx, z += dz)
    c.set(x, by, z, CREATE('shaft'), { axis: shaftAxis, waterlogged: 'false' })
  c.set(hx, hy, hz, CREATE('gearbox'), { axis: shaftAxis === 'z' ? 'x' : 'z' })
  for (let y = footY + 1; y < hy; y++) c.set(hx, y, hz, CREATE('shaft'), { axis: 'y', waterlogged: 'false' })
  const outAxis = out === 'east' || out === 'west' ? 'x' : 'z'
  const across = outAxis === 'x' ? 'z' : 'x'
  c.set(hx, footY, hz, CREATE('gearbox'), { axis: across })
  const [ox, oz] = DIRS[out]
  for (let i = 1; i < run; i++) c.set(hx + ox * i, footY, hz + oz * i, CREATE('shaft'), { axis: outAxis, waterlogged: 'false' })
  const end = [hx + ox * run, footY, hz + oz * run]
  c.set(...end, CREATE('gearbox'), { axis: across })
  const stone = [end[0], footY + 1, end[2]]
  c.set(...stone, CREATE('millstone'))
  return stone
}

/** boatCap: a mill's turning cap, a gable roof with its ridge along z over x0..x1 from z0 to z1 (eave at y0, 45
 *  degrees, a `ridge` slab on top where the width is odd), its gable ends filled with `board` at zg0 and zg1 under the
 *  roof, side boards along x0 + 1 and x1 - 1 at the eave, and the cells `keepOut` ([x, y, z]) left for the windshaft.
 *  Returns the ridge y. */
export function boatCap(c, { x0, x1, z0, z1, y0, zg0, zg1, keepOut = [] }, { stairs, ridge, board }) {
  const skip = new Set(keepOut.map(p => p.join(',')))
  const half = Math.floor((x1 - x0) / 2), mid = x0 + half
  for (let z = z0; z <= z1; z++) for (let i = 0; i < half; i++) {
    stair(c, x0 + i, y0 + i, z, stairs, 'east')
    stair(c, x1 - i, y0 + i, z, stairs, 'west')
  }
  for (let z = z0; z <= z1; z++) c.set(mid, y0 + half, z, ridge, { type: 'bottom', waterlogged: 'false' })
  for (const zg of [zg0, zg1]) for (let i = 1; i < half; i++) for (let x = x0 + i; x <= x1 - i; x++) {
    const y = y0 + i - 1
    if (!skip.has(`${x},${y},${zg}`)) c.set(x, y, zg, board)
  }
  for (const zg of [zg0, zg1]) if (!skip.has(`${mid},${y0 + half - 1},${zg}`)) c.set(mid, y0 + half - 1, zg, board)
  for (let z = zg0; z <= zg1; z++) for (const x of [x0 + 1, x1 - 1]) if (!skip.has(`${x},${y0},${z}`)) c.set(x, y0, z, board)
  return y0 + half
}

/** tailPole: the long pole a miller winds the cap round with, from under the cap's back down to the stage: `cells`
 *  ([x, y, z, axis]) in order, logs of `pole`, and a `wheel` (e.g. a trapdoor or a grindstone-free block) at its foot. */
export function tailPole(c, cells, pole) {
  for (const [x, y, z, axis] of cells) c.set(x, y, z, pole, { axis })
}
