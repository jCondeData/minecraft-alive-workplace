// Stairs and lofts: a straight flight up to a loft, its stairwell, and a loft's ceiling lining (parts library).
import { stair } from './canvas.mjs'

/** flight: a straight stair flight, solid (`under`) beneath each step down to y = 1. */
export function flight(c, steps, rises, wood, under) {
  for (const [x, y, z] of steps) {
    stair(c, x, y, z, `${wood}_stairs`, rises)
    for (let yy = 1; yy < y; yy++) c.set(x, yy, z, under)
  }
}

/** stairwell: clears the loft floor over a flight (the cells a person's head passes through). */
export function stairwell(c, cells) { for (const [x, y, z] of cells) c.clear(x, y, z) }

/** lining: boards under a roof's slope, the [x, y] cells of one cross-section repeated from z0 to z1 (`skip(x, z)`
 *  leaves a window or dormer clear). Pale boards make a loft under dark tiles a light room. */
export function lining(c, section, z0, z1, id, skip = () => false) {
  for (let z = z0; z <= z1; z++) for (const [x, y] of section) if (!skip(x, z)) c.set(x, y, z, id)
}
