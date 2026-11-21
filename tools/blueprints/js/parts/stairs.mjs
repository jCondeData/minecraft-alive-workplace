// Stairs and lofts: a straight flight up to a loft, its stairwell, and a loft's ceiling lining (parts library).
import { stair } from './canvas.mjs'

/** flight: a straight stair flight, solid (`under`) beneath each step down to y = `base` (1: the ground floor; an
 *  upper flight passes the floor it starts from, e.g. 6 over a floor at 5, so the room below keeps its height). */
export function flight(c, steps, rises, wood, under, base = 1) {
  for (const [x, y, z] of steps) {
    stair(c, x, y, z, `${wood}_stairs`, rises)
    for (let yy = base; yy < y; yy++) c.set(x, yy, z, under)
  }
}

/** stairwell: clears the loft floor over a flight (the cells a person's head passes through). */
export function stairwell(c, cells) { for (const [x, y, z] of cells) c.clear(x, y, z) }

/** lining: boards under a roof's slope, the [x, y] cells of one cross-section repeated from z0 to z1 (`skip(x, z)`
 *  leaves a window or dormer clear). Pale boards make a loft under dark tiles a light room. */
export function lining(c, section, z0, z1, id, skip = () => false) {
  for (let z = z0; z <= z1; z++) for (const [x, y] of section) if (!skip(x, z)) c.set(x, y, z, id)
}

/** hatchLadder: a ladder from a cellar's floor (y0) up to just under the floor at `top` (the ground floor's layer),
 *  against the wall behind it (`facing`: the way it faces, away from that wall), under a closed hatch (a top-half
 *  trapdoor of `wood` flush with the floor): floor to a villager walking over it, a way down to a player. */
export function hatchLadder(c, x, z, y0, top, facing, wood) {
  for (let y = y0; y < top; y++) c.set(x, y, z, 'ladder', { facing, waterlogged: 'false' })
  c.set(x, top, z, `${wood}_trapdoor`, { facing, half: 'top', open: 'false', powered: 'false', waterlogged: 'false' })
}
