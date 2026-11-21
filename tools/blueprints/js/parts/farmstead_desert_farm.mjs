// Desert farm parts (parts library, tools/blueprints/REBUILD.md): the pieces a Nile or oasis farm is known by, grown from
// the Farmstead's desert look (49.10e). Every block comes from the caller's palette.
//   dovecote   a pigeon tower on a flat roof (a burj hamam): a square mud shaft with perches out on every face, two
//              bands of clay pots set in the faces for nest holes, a whitewashed band and a dome narrowing to its crown
//   domeCap    a little dome over a 3 x 3 top: a ring of stairs round a core, one block on top
//   shaduf     the well sweep over a channel: thin posts, a thin bar, a tilted pole with a lashed counterweight at
//              one end and a chain and clay bucket at the other
//   grainBin   a little domed mud granary (a sawami' bin) on a flat roof: a round drum and a domeCap
//   palmCrown  a date palm's crown of long drooping fronds over its trunk's top
//   shade      a palm-rib shade along a wall: a beam on posts, rafters out over the edge, a matting of slabs on them
import { slab, stair, trapdoor, OPP, DIRS } from './canvas.mjs'

/** dovecote: a tower x0..x0+2, z0..z0+2 from y0 up to y0 + 5: y0 body; y0 + 1..y0 + 2 the faces' middle cells clay
 *  `pot`s (nest holes) round a solid core, a `perch` (a fence) a block out before each face's lower pot (`faces`); y0 + 3
 *  a `band` course; y0 + 4 a ring of `dome` stairs (high sides in; finish() turns the corners) round a `band` core; y0 + 5
 *  a single `band` block on top (a dome narrowing a step to its crown). `body` may be a mix(). Faces are 'north',
 *  'south', 'west', 'east'. */
export function dovecote(c, x0, z0, y0, { body, perch, pot, band, dome, faces = ['north', 'south', 'west', 'east'] }) {
  const mid = (dx, dz) => (dx === 1) !== (dz === 1) // the middle cell of a face
  for (let dx = 0; dx <= 2; dx++) for (let dz = 0; dz <= 2; dz++) {
    const x = x0 + dx, z = z0 + dz
    c.set(x, y0, z, body)
    for (let y = y0 + 1; y <= y0 + 2; y++) c.set(x, y, z, mid(dx, dz) ? pot : body)
    c.set(x, y0 + 3, z, band)
  }
  domeCap(c, x0, z0, y0 + 4, { stairs: dome, core: band, top: band })
  for (const f of faces) {
    const [ox, oz] = DIRS[f]
    const x = x0 + 1 + ox * 2, z = z0 + 1 + oz * 2
    if (c.inside(x, y0 + 1, z) && !c.get(x, y0 + 1, z)) c.set(x, y0 + 1, z, perch)
  }
}

/** domeCap: a little dome over a 3 x 3 top at y: a ring of `stairs` (each its high side to the middle; finish() turns
 *  the corners) round a `core` block, and one `top` block over the core (the dome narrowing a step to its crown). */
export function domeCap(c, x0, z0, y, { stairs, core, top }) {
  for (let dx = 0; dx <= 2; dx++) for (let dz = 0; dz <= 2; dz++) {
    const x = x0 + dx, z = z0 + dz
    if (dx === 1 && dz === 1) { c.set(x, y, z, core); c.set(x, y + 1, z, top); continue }
    stair(c, x, y, z, stairs, dz === 0 ? 'south' : dz === 2 ? 'north' : dx === 0 ? 'east' : 'west')
  }
}

/** shaduf: a well sweep: thin `upright`s (fences) at x and x + 2 on line z from y0 up to y0 + 2, a thin `bar` (a fence
 *  run) across them at y0 + 3, and the sweep pole of `pole` fences tilted over the bar: `cells` [[dz, dy]] relative to
 *  (x + 1, y0 + 4, z), the first its front (low) end, with a `drop`-long chain under it ending in a clay `bucket`, and
 *  the `weight` (a wall block, a lashed stone) at `weightAt` [dz, dy] when given. */
export function shaduf(c, x, z, y0, { upright, bar, pole, cells, drop = 1, bucket, weight = null, weightAt = null }) {
  for (const px of [x, x + 2]) for (let y = y0; y <= y0 + 2; y++) c.set(px, y, z, upright)
  for (let px = x; px <= x + 2; px++) c.set(px, y0 + 3, z, bar)
  for (const [dz, dy] of cells) c.set(x + 1, y0 + 4 + dy, z + dz, pole)
  const [fz, fy] = cells[0]
  for (let i = 1; i <= drop; i++) c.set(x + 1, y0 + 4 + fy - i, z + fz, 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(x + 1, y0 + 4 + fy - drop - 1, z + fz, bucket, { facing: 'north', cracked: 'false', waterlogged: 'false' })
  if (weight) c.set(x + 1, y0 + 4 + weightAt[1], z + weightAt[0], weight)
}

/** grainBin: a domed bin x0..x0+2, z0..z0+2 on a roof: a round drum y0..y0 + h - 1 (`body` on the faces, may be a
 *  mix(); `corner` wall blocks at the corners, so it reads round), then a domeCap (`stairs` round a `core`, a `top`). */
export function grainBin(c, x0, z0, y0, { body, corner, stairs, core, top, h = 3 }) {
  for (let dx = 0; dx <= 2; dx++) for (let dz = 0; dz <= 2; dz++)
    for (let y = y0; y < y0 + h; y++) c.set(x0 + dx, y, z0 + dz, dx !== 1 && dz !== 1 ? corner : body)
  domeCap(c, x0, z0, y0 + h, { stairs, core, top })
}

/** palmCrown: a date palm's crown round [x, y, z] (the trunk's top under it): a tuft over it, and a long frond out each
 *  way, the four straight ones three blocks (level for two, then dropping a block and its tip a block more), the four
 *  diagonal ones two (level, the tip dropping a block). Cells outside the canvas or already drawn are left. */
export function palmCrown(c, [x, y, z], leaves) {
  const put = (a, b, d) => { if (c.inside(a, b, d) && !c.get(a, b, d)) c.set(a, b, d, leaves, { persistent: 'true', distance: '1', waterlogged: 'false' }) }
  put(x, y, z); put(x, y + 1, z)
  for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
    put(x + dx, y, z + dz); put(x + 2 * dx, y, z + 2 * dz); put(x + 3 * dx, y - 1, z + 3 * dz); put(x + 3 * dx, y - 2, z + 3 * dz)
  }
  for (const [dx, dz] of [[1, 1], [1, -1], [-1, 1], [-1, -1]]) { put(x + dx, y, z + dz); put(x + 2 * dx, y, z + 2 * dz); put(x + 2 * dx, y - 1, z + 2 * dz) }
}

/** shade: a palm-rib shade along a wall on line x (the wall at x - 1, the shade out east), z0..z1: a `beam` log along
 *  z at y - 1 (head height over a walk at y - 3), `post`s under it at each z of `posts` down to y0, `rafter` fences out
 *  a block past it at every z of `rafters`, and a `matting` of bottom slabs at y over the beam's line and the rafters'. */
export function shade(c, { x, z0, z1, y, y0 = 1, posts = [], rafters = [] }, { beam, post, rafter, matting }) {
  for (let z = z0; z <= z1; z++) {
    c.set(x, y - 1, z, beam, { axis: 'z' })
    slab(c, x, y, z, matting, 'bottom')
    if (rafters.includes(z)) c.set(x + 1, y - 1, z, rafter)
    if (rafters.includes(z) || rafters.includes(z - 1) || rafters.includes(z + 1)) slab(c, x + 1, y, z, matting, 'bottom')
  }
  for (const z of posts) for (let yy = y0; yy < y - 1; yy++) c.set(x, yy, z, post, { axis: 'y' })
}

/** screen: a lattice screen before a window (a desert farmhouse's shuttered light): open `lattice` trapdoors one block
 *  out from each window cell (their panels on the outer face), a `sill` top slab under them, a `hood` bottom slab over
 *  them. Cells already drawn are left (a bench under it serves as its sill). */
export function screen(c, cells, out, { lattice, sill, hood }) {
  const [ox, oz] = DIRS[out]
  const low = Math.min(...cells.map(p => p[1])), high = Math.max(...cells.map(p => p[1]))
  for (const [x, y, z] of cells) {
    trapdoor(c, x + ox, y, z + oz, lattice, OPP[out], 'bottom', true)
    if (y === low && !c.get(x + ox, y - 1, z + oz)) slab(c, x + ox, y - 1, z + oz, sill, 'top')
    if (y === high && !c.get(x + ox, y + 1, z + oz)) slab(c, x + ox, y + 1, z + oz, hood, 'bottom')
  }
}
