// Hot-country row houses (parts library, tools/blueprints/REBUILD.md): the pieces the Terrace's desert and savanna
// looks (49.10c) are known by, each usable by any row of homes in a hot climate. Every block comes from the caller's
// palette.
//   rowPilasters  proud piers on the wall lines of a row (one per party wall and end), each with a cap: the row read as
//                 several houses before a door is seen
//   rowParapet    the parapet round a row's flat roof with a coping on it, carried across the roof on every party wall
//                 (each household its own roof terrace)
//   paintedBand   a run of wall cells painted in alternating colours (the painted trims of a savanna village's houses)
//   stoop         a stone strip along a row's front at y 0, skipping cells already drawn (the doors' steps)
import { slab } from './canvas.mjs'

/** pilasters: a pier at each x of `xs` on line z, from y0 to y1 (`id`, may be a mix()), capped with a bottom `cap`
 *  slab at y1 + 1 when given. */
export function rowPilasters(c, xs, z, y0, y1, id, cap = null) {
  for (const x of xs) {
    for (let y = y0; y <= y1; y++) c.set(x, y, z, id)
    if (cap) slab(c, x, y1 + 1, z, cap, 'bottom')
  }
}

/** rowParapet: a one-high parapet of `wall` at y round x0..x1, z0..z1 and along every x of `cross` (party walls) from
 *  z0 to z1, with a `coping` slab on top at y + 1. `skip(x, z)` leaves a cell to the caller (a tower standing there). */
export function rowParapet(c, { x0, z0, x1, z1, y, cross = [] }, { wall, coping }, skip = () => false) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const edge = x === x0 || x === x1 || z === z0 || z === z1 || cross.includes(x)
    if (!edge || skip(x, z)) continue
    c.set(x, y, z, wall)
    slab(c, x, y + 1, z, coping, 'bottom')
  }
}

/** paintedBand: `cells` [[x, y, z]] painted in turn with each id of `paints` (two or more), so a band reads as a
 *  patterned trim rather than a stripe. */
export function paintedBand(c, cells, paints) {
  cells.forEach(([x, y, z], i) => c.set(x, y, z, paints[i % paints.length]))
}

/** stoop: `id` along the row's front, x0..x1 on line z at y, leaving any cell already drawn (a door's step). */
export function stoop(c, x0, x1, z, y, id) {
  for (let x = x0; x <= x1; x++) if (!c.get(x, y, z)) c.set(x, y, z, id)
}
