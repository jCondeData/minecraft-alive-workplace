// Weavers' parts (parts library, tools/blueprints/REBUILD.md; grown from the Weaver's Cottage, 49): the long mullioned
// window weavers' houses are known by, a moulded course under it, a taking-in door with its hoist, a tenter frame with
// dyed cloth hung on it, and rows of dye flowers. Every block comes from the caller's palette; none is picked here.
import { DIRS, OPP, stair } from './canvas.mjs'
import { plantFlower } from './planters.mjs'
import { hoistBeam } from './workshop_a_yard.mjs'

/** lightBand: a long window in a wall line from u0 to u1 (`axis` 'x': along x on z = `line`; 'z': along z on x =
 *  `line`), y0..y1 high: glass in each of `lights`, a `mullion` (a wall id reads as a slim stone upright) in every other
 *  cell, and a `lintel` course over the whole band at y1 + 1 (null keeps the wall's own block). */
export function lightBand(c, { axis, line, u0, u1, y0, y1, lights }, { glass, mullion, lintel = null }) {
  const at = (u, y) => (axis === 'x' ? [u, y, line] : [line, y, u])
  for (let u = u0; u <= u1; u++) {
    for (let y = y0; y <= y1; y++) { const [x, yy, z] = at(u, y); c.set(x, yy, z, lights.includes(u) ? glass : mullion) }
    if (lintel) { const [x, yy, z] = at(u, y1 + 1); c.set(x, yy, z, lintel) }
  }
}

/** mouldedCourse: a run of upside-down stairs one block out from a wall face (`out`: the side it stands out to) at y,
 *  from u0 to u1 along the wall line (`axis`, `line`: the wall's own line); a drip over the windows under it and a
 *  sill for those over it. */
export function mouldedCourse(c, { axis, line, u0, u1, y, out }, id) {
  const [ox, oz] = DIRS[out]
  for (let u = u0; u <= u1; u++) {
    const [x, z] = axis === 'x' ? [u, line] : [line, u]
    stair(c, x + ox, y, z + oz, id, OPP[out], 'top')
  }
}

/** takingInDoor: an upper door in a gable (`at`: its lower half; `out`: the side it opens to, `facing` into the house)
 *  with a hoist beam (workshop_a_yard's hoistBeam) over it at `hoist` [x, y, z] whose hook hangs over the yard below:
 *  the way fleeces, sacks and bales are lifted straight into a loft. */
export function takingInDoor(c, at, facing, out, { wood, hoist = null, log, bracket, reach = 2, drop = 4 }) {
  const [x, y, z] = at
  for (const half of ['lower', 'upper'])
    c.set(x, y + (half === 'upper' ? 1 : 0), z, `${wood}_door`, { facing, half, hinge: 'left', open: 'false', powered: 'false' })
  if (hoist) hoistBeam(c, hoist, out, { log, bracket, reach, drop })
}

/** tenterFrame: a rail (`rail`, a log) at y along `axis` on its posts (`post`, fence ids, y 1..y-1 at each of
 *  `posts`), with cloth (wall banners of the colours in `cloth`, cycled) hung from the rail's sides on `sides` (the way
 *  each side's cloth faces) in every bay between posts: dyed cloth stretched to dry. */
export function tenterFrame(c, { axis, line, u0, u1, posts, y, sides }, { post, rail, cloth }) {
  const at = (u, yy) => (axis === 'x' ? [u, yy, line] : [line, yy, u])
  for (const u of posts) for (let yy = 1; yy < y; yy++) { const [x, py, z] = at(u, yy); c.set(x, py, z, post) }
  for (let u = u0; u <= u1; u++) { const [x, py, z] = at(u, y); c.set(x, py, z, rail, { axis }) }
  let i = 0
  for (const side of sides) {
    const [ox, oz] = DIRS[side]
    for (let u = u0; u <= u1; u++) {
      if (posts.includes(u)) continue
      const [x, py, z] = at(u, y)
      c.set(x + ox, py, z + oz, `${cloth[i++ % cloth.length]}_wall_banner`, { facing: side })
    }
  }
}

/** dyeRows: a bed of rows of flowers, one dye colour a row: soil (`soil`) at y 0 over x0..x1 on each row z, the
 *  row's plants (a list, cycled along the row; a two-block flower takes its upper half too) at y 1. rows: {z: [ids]}. */
export function dyeRows(c, x0, x1, rows, soil) {
  for (const [zs, plants] of Object.entries(rows)) {
    const z = Number(zs)
    for (let x = x0; x <= x1; x++) {
      c.set(x, 0, z, soil, soil.endsWith('grass_block') ? { snowy: 'false' } : {})
      plantFlower(c, x, 1, z, plants[(x - x0) % plants.length])
    }
  }
}

/** leanTo: an open-fronted lean-to against a wall (a fold's shed, a cart shelter): roof stairs over the cells from the
 *  wall out (`axis` 'x': the shed runs along z from v0 to v1 and steps out along x from u0, the column against the wall,
 *  to u1; 'z' the other way round), each column a block lower than the last, starting at `topY` against the wall, the
 *  stairs rising toward the wall (`toward`), with posts (`post`, fence ids) under the outer column at each of `posts`
 *  (v values), from y 1 up to the stair. */
export function leanTo(c, { axis, u0, u1, v0, v1, topY, toward, posts }, { roof, post }) {
  const step = u1 >= u0 ? 1 : -1
  for (let u = u0, y = topY; step > 0 ? u <= u1 : u >= u1; u += step, y--)
    for (let v = v0; v <= v1; v++) {
      const [x, z] = axis === 'x' ? [u, v] : [v, u]
      stair(c, x, y, z, roof.stairs, toward)
    }
  const outY = topY - Math.abs(u1 - u0)
  for (const v of posts) for (let y = 1; y < outY; y++) {
    const [x, z] = axis === 'x' ? [u1, v] : [v, u1]
    c.set(x, y, z, post)
  }
}
