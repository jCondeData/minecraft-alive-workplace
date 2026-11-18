// Tower depth (parts library, grown from the Map Room's third round, ROADMAP 49): what gives a plain shaft depth and
// a front: corner piers standing proud, a stepped stair plinth, timber brackets under a jetty or an eave, and a map
// wall of banners. Every block comes from the caller's palette; nothing is picked here.
import { DIRS, OPP, stair } from './canvas.mjs'

/** cornerPiers: a square pier `size` x `size` at each corner of the rectangle x0..x1, z0..z1 (the piers' outer
 *  corners), from y0 to y1, courses alternating `id` and `alt` so they read as dressed stone. Returns a test for
 *  "this [x, z] is a pier cell" (to keep belts and corbels off them). */
export function cornerPiers(c, x0, z0, x1, z1, y0, y1, id, alt = id, size = 2) {
  const cells = []
  for (const [cx, sx] of [[x0, 1], [x1, -1]]) for (const [cz, sz] of [[z0, 1], [z1, -1]])
    for (let i = 0; i < size; i++) for (let j = 0; j < size; j++) cells.push([cx + sx * i, cz + sz * j])
  for (const [x, z] of cells) for (let y = y0; y <= y1; y++) c.set(x, y, z, (y - y0) % 2 ? alt : id)
  return (x, z) => cells.some(([px, pz]) => px === x && pz === z)
}

/** stairPlinth: a flight of `steps` stair courses `width` wide climbing toward `facing` from (x0, y, z) (x0 the
 *  flight's left end looking up it, along x for north/south flights), each course filled under with `fill`, and a
 *  cheek block (`cheek`) each side of every course. */
export function stairPlinth(c, x0, y, z, width, steps, facing, stairs, fill, cheek = null) {
  const [dx, dz] = DIRS[facing]
  const across = dx === 0 ? [1, 0] : [0, 1]
  for (let s = 0; s < steps; s++) {
    const sx = x0 + dx * s, sz = z + dz * s
    for (let i = 0; i < width; i++) {
      const x = sx + across[0] * i, zz = sz + across[1] * i
      for (let yy = y; yy < y + s; yy++) c.set(x, yy, zz, fill)
      stair(c, x, y + s, zz, stairs, facing)
    }
    if (cheek) for (const side of [-1, width]) for (let yy = y; yy <= y + s; yy++)
      c.set(sx + across[0] * side, yy, sz + across[1] * side, cheek)
  }
}

/** towerBrackets: timber brackets (upside-down stairs, their full side toward `toward`) at each [x, y, z] of `cells`:
 *  knee braces under an eave beside its posts, or the outer tier of a corbelled jetty. Leaves a filled cell alone. */
export function towerBrackets(c, cells, toward, stairs) {
  for (const [x, y, z] of cells) if (!c.get(x, y, z)) stair(c, x, y, z, stairs, toward, 'top')
}

/** mapWall: a row of wall banners (`colours`, a sea-and-land map read as cloth) on a wall's inner face, from (x, y, z)
 *  along `along` (east or south), facing `facing` into the room; each is recorded as a picture spot. */
export function mapWall(c, [x, y, z], along, facing, colours, wood) {
  const [ax, az] = DIRS[along]
  colours.forEach((colour, i) => {
    const p = [x + ax * i, y, z + az * i]
    c.free(p)
    c.set(...p, `${colour}_wall_banner`, { facing })
    c.spot('picture', p, facing, [[0, 0, 0]], wood, colour)
  })
}

export { OPP }
