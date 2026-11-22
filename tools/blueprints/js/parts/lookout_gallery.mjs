// Lookout galleries (parts library, grown from the Lookout Tower, ROADMAP 49): an open timber watch gallery on a
// jettied deck, a lean-to roof falling away from a wall, a lantern hung from a fence bracket on a wall, a ring of
// cells round a rectangle. Every block comes from the caller's palette.
import { stair } from './canvas.mjs'

/** rimCells: the [x, z] cells round the edge of x0..x1, z0..z1, in order (north row west to east, then down the
 *  east side, back along the south row, up the west side): the rim a parapet or a rail runs on. */
export function rimCells(x0, z0, x1, z1) {
  const out = []
  for (let x = x0; x <= x1; x++) out.push([x, z0])
  for (let z = z0 + 1; z <= z1; z++) out.push([x1, z])
  for (let x = x1 - 1; x >= x0; x--) out.push([x, z1])
  for (let z = z1 - 1; z > z0; z--) out.push([x0, z])
  return out
}

/** watchGallery: an open gallery on a deck: the deck (`deck`) over x0..x1, z0..z1 at deckY, a `post` log at each
 *  corner from deckY + 1 to beamY - 1, a `fence` rail round the rim at deckY + 1 between them with every other cell
 *  left open (a shooting cell: the cells two, four, ... from a corner), a knee brace (an upside-down `brace` stair,
 *  its full side to the post) beside each post just under the beam, and a ring of `beam` logs at beamY. The deck
 *  keeps two clear cells over it inside the rail and in each open cell: those cells are an archer's stations. A fence
 *  stands 1.5 high and a villager's eyes 1.62, so behind an unbroken rail no station sees the ground below (B1469):
 *  from the open cells at the deck's edge one does. Keep the sides an odd number of cells long (a fence beside each
 *  post). `skip(x, z)` leaves a deck cell open (a ladder's hole). */
export function watchGallery(c, { x0, z0, x1, z1, deckY, beamY }, { deck, post, fence, beam, brace }, skip = () => false) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) if (!skip(x, z)) c.set(x, deckY, z, deck)
  const corners = [[x0, z0], [x1, z0], [x0, z1], [x1, z1]]
  for (const [x, z] of corners) for (let y = deckY + 1; y < beamY; y++) c.set(x, y, z, post, { axis: 'y' })
  // B1469: the rail is broken by an open cell every other cell, so an archer at the deck's edge sees down to the ground
  const open = (x, z) => (x - x0 + z - z0) % 2 === 0
  for (const [x, z] of rimCells(x0, z0, x1, z1)) if (!c.get(x, deckY + 1, z) && !open(x, z)) c.set(x, deckY + 1, z, fence)
  const by = beamY - 1
  for (const z of [z0, z1]) { stair(c, x0 + 1, by, z, brace, 'west', 'top'); stair(c, x1 - 1, by, z, brace, 'east', 'top') }
  for (const x of [x0, x1]) { stair(c, x, by, z0 + 1, brace, 'north', 'top'); stair(c, x, by, z1 - 1, brace, 'south', 'top') }
  for (const [x, z] of rimCells(x0, z0, x1, z1)) {
    const corner = (x === x0 || x === x1) && (z === z0 || z === z1)
    c.set(x, beamY, z, beam, { axis: corner ? 'y' : (z === z0 || z === z1) ? 'x' : 'z' })
  }
}

/** shedRoof: a lean-to (pent) roof of `stairs` along z0..z1, high against a wall and falling one block per block
 *  toward `falls` ('east' or 'west'): from (x0, y0) out to x1. Returns the [x, y] of each course (to fill gables
 *  under). `keep(x, y, z)` leaves a cell to whatever is drawn there. */
export function shedRoof(c, { x0, y0, x1, z0, z1, falls }, stairs, keep = () => true) {
  const dx = falls === 'east' ? 1 : -1
  const rise = falls === 'east' ? 'west' : 'east'
  const courses = []
  for (let x = x0, y = y0; dx > 0 ? x <= x1 : x >= x1; x += dx, y--) {
    courses.push([x, y])
    for (let z = z0; z <= z1; z++) if (keep(x, y, z)) stair(c, x, y, z, stairs, rise)
  }
  return courses
}

/** fenceLamp: a lantern hung from a `fence` bracket sticking out of a wall (the fence at [x, y, z], the lantern under
 *  it): light by a door that leaves no ledge for an archer and no lantern on a floor. */
export function fenceLamp(c, [x, y, z], fence) {
  c.set(x, y, z, fence)
  c.set(x, y - 1, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
}
