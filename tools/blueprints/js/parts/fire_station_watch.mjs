// Fire-watch parts (parts library, tools/blueprints/REBUILD.md; grown from the Fire Station, 49): a two-tier pail rack,
// a stone trough with its spout, a downpipe, a segmental arch over a wide doorway, a hose reel on its wheels, and a
// corbelled lookout gallery under a hip cap for any watchtower. Every block comes from the caller's palette.
import { DIRS, slab, stair, trapdoor } from './canvas.mjs'
import { corbelRing } from './map_room_tower.mjs'
import { hipCap } from './tinker.mjs'

/** pailRack: a rack of pails against a wall: at each cell [x, z] along it, a top-half `shelf` slab at every y of
 *  `tiers` with a `pail` (an empty flower pot: a bucket has no block) standing on it, and an upright `post` (a log) at
 *  each end cell of `ends` from the lowest tier to `postTop`. Returns the pail cells. */
export function pailRack(c, cells, tiers, { shelf, pail = 'flower_pot', post = null, ends = [], postTop = null }) {
  const pails = []
  for (const [x, z] of cells) for (const y of tiers) {
    slab(c, x, y, z, shelf, 'top')
    c.set(x, y + 1, z, pail)
    pails.push([x, y + 1, z])
  }
  if (post) for (const [x, z] of ends) for (let y = Math.min(...tiers); y <= postTop; y++) c.set(x, y, z, post, { axis: 'y' })
  return pails
}

/** trough: still water (sources, each a bucket on the builder's list) in `cells` [x, z] at y, every side and the floor
 *  under it closed: a `rim` block on every open side at y (a block or a mix()), the floor at y - 1 filled with `rim`
 *  where nothing is drawn. Returns the rim cells. Throws if the water could still run out. */
export function trough(c, cells, y, rim) {
  const water = new Set(cells.map(([x, z]) => `${x},${z}`))
  const ring = []
  for (const [x, z] of cells) {
    if (!c.get(x, y - 1, z)) c.set(x, y - 1, z, rim)
    for (const [dx, dz] of Object.values(DIRS)) {
      const nx = x + dx, nz = z + dz
      if (water.has(`${nx},${nz}`) || c.get(nx, y, nz)) continue
      c.set(nx, y, nz, rim); ring.push([nx, nz])
    }
  }
  for (const [x, z] of cells) c.set(x, y, z, 'water', { level: '0' })
  return ring
}

/** downpipe: a pipe (`pipe`, e.g. a lightning rod standing up) from y0 to y1 at (x, z) against a wall, under an eave,
 *  and its spout (`spout`, the same block turned `out`) through the wall at [sx, sy, sz] over the water it feeds. */
export function downpipe(c, x, z, y0, y1, pipe, spoutAt = null, out = 'east') {
  for (let y = y0; y <= y1; y++) c.set(x, y, z, pipe, { facing: 'up', powered: 'false', waterlogged: 'false' })
  if (spoutAt) c.set(spoutAt[0], spoutAt[1], spoutAt[2], pipe, { facing: out, powered: 'false', waterlogged: 'false' })
}

/** segmentalArch: a low arch over a three-wide opening in a wall plane (axis 'x': the opening runs x0..x0+2 on line z),
 *  its crown at y: upside-down `stairs` at the two outer cells (each stepping down onto its jamb), a top-half `slabId`
 *  in the middle (the soffit half a block higher), and a `key` stone over it at y + 1. */
export function segmentalArch(c, axis, line, a0, y, { stairs, slabId, key }) {
  const at = (u, yy) => (axis === 'x' ? [u, yy, line] : [line, yy, u])
  const [lo, hi] = axis === 'x' ? ['west', 'east'] : ['north', 'south']
  stair(c, ...at(a0, y), stairs, lo, 'top')
  stair(c, ...at(a0 + 2, y), stairs, hi, 'top')
  slab(c, ...at(a0 + 1, y), slabId, 'top')
  c.set(...at(a0 + 1, y + 1), key)
}

/** hoseReel: a hose reel on two wheels standing on a floor at [x, y, z], its axle along `axle` ('x' or 'z'): the coiled
 *  hose (`coil`, a block) in the middle, a wheel (an open `wheel` trapdoor flat against the coil) either side, and the
 *  handle (`handle`, a fence) one block toward `pull`. */
export function hoseReel(c, [x, y, z], axle, pull, { coil, wheel, handle }) {
  const [ax, az] = axle === 'x' ? [1, 0] : [0, 1]
  c.set(x, y, z, coil)
  // an open trapdoor lies against the side opposite its facing: the west wheel faces west to lie on the coil's side
  const sides = axle === 'x' ? [[-1, 'west'], [1, 'east']] : [[-1, 'north'], [1, 'south']]
  for (const [s, facing] of sides) trapdoor(c, x + ax * s, y, z + az * s, wheel, facing, 'bottom', true)
  const [px, pz] = DIRS[pull]
  c.set(x + px, y, z + pz, handle)
}

/** lookoutGallery: a watchtower's top (a square shaft centred on (cx, cz), `half` its walls' half-width): a corbel
 *  ring of upside-down stairs at y0 one block out, the deck over shaft and corbels at y0 + 1 (`hole`: [x, z] left open
 *  for a ladder), corner posts on the shaft's corners up `height` blocks, a rail round the deck's edge, a beam ring and
 *  boarded ceiling over the shaft, and a hip cap whose eave stands over the rail. Returns {deckY, ceilingY, capTop}. */
export function lookoutGallery(c, cx, cz, half, y0, { corbel, deck, post, rail, beam, ceiling, roof, apex = null }, { height = 2, hole = null } = {}) {
  const o = half + 1
  corbelRing(c, cx - o, cz - o, cx + o, cz + o, y0, corbel)
  const deckY = y0 + 1
  for (let x = cx - o; x <= cx + o; x++) for (let z = cz - o; z <= cz + o; z++) {
    if (hole && hole[0] === x && hole[1] === z) continue
    c.set(x, deckY, z, deck)
  }
  for (let x = cx - o; x <= cx + o; x++) for (let z = cz - o; z <= cz + o; z++)
    if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) === o) c.set(x, deckY + 1, z, rail)
  for (const dx of [-half, half]) for (const dz of [-half, half])
    for (let y = deckY + 1; y <= deckY + height; y++) c.set(cx + dx, y, cz + dz, post, { axis: 'y' })
  const ceilingY = deckY + height + 1
  for (let x = cx - half; x <= cx + half; x++) for (let z = cz - half; z <= cz + half; z++) {
    const edgeX = Math.abs(x - cx) === half, edgeZ = Math.abs(z - cz) === half
    if (edgeX || edgeZ) c.set(x, ceilingY, z, beam, { axis: edgeX && edgeZ ? 'y' : edgeZ ? 'x' : 'z' })
    else c.set(x, ceilingY, z, ceiling)
  }
  const capTop = hipCap(c, cx, cz, o, ceilingY, roof, { apex })
  return { deckY, ceilingY, capTop }
}
