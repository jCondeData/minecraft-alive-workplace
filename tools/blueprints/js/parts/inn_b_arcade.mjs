// Arcades, jetties, mouldings and coach gates (parts library, grown from the Inn, Version B: the Arcade Inn, ROADMAP 49).
// A street arcade under a pentice, a moulding (a run of upside-down stairs) and a
// stone skirt along a wall's foot that shutters can stand on, a bench of stairs, a timber coach gate, stable stalls.
// Every block comes from the palette passed in.
import { stair, slab } from './canvas.mjs'

/** arcade: an open walk along a front wall (the wall on line z + 2*side), posts on line z from y0+1 to beamY-1, a
 *  beam along the posts at beamY, arch brackets (upside-down stairs) against each post at beamY-1 in every bay
 *  wider than one, a pentice (a row of roof stairs rising toward the house) over the beam at beamY+1, and the walk's
 *  floor at y0 from line z to the wall: a step row of `step` stairs on line z between the posts (full `paving` under
 *  each post) and `paving` behind. `away`: the side the arcade faces ('north' for a front arcade). */
export function arcade(c, { x0, x1, z, depth = 2, posts, y0 = 0, beamY = 4, away = 'north' }, { post, beam, bracket, roof, paving, step }) {
  const inward = away === 'north' ? 1 : -1
  const toward = away === 'north' ? 'south' : 'north'
  for (let x = x0; x <= x1; x++) {
    const isPost = posts.includes(x)
    for (let d = 0; d < depth; d++) {
      if (d === 0 && !isPost) stair(c, x, y0, z, step, toward)
      else c.set(x, y0, z + inward * d, paving)
    }
    c.set(x, beamY, z, beam, { axis: 'x' })
    stair(c, x, beamY + 1, z, roof.stairs, toward)
  }
  for (const x of posts) for (let y = y0 + 1; y < beamY; y++) c.set(x, y, z, post, { axis: 'y' })
  const sorted = [...posts].sort((a, b) => a - b)
  for (let i = 0; i + 1 < sorted.length; i++) {
    const a = sorted[i], b = sorted[i + 1]
    if (b - a < 4) continue // a one-wide bay stays a square opening
    stair(c, a + 1, beamY - 1, z, bracket, 'west', 'top')
    stair(c, b - 1, beamY - 1, z, bracket, 'east', 'top')
  }
}

/** moulding: a run of upside-down stairs along a wall's face at y (cells [x, z] outside the wall), the full side
 *  against the wall (`toward`). Nine or more in a run hold the shutters and pots that stand on them. */
export function moulding(c, cells, y, id, toward) {
  for (const [x, z] of cells) stair(c, x, y, z, id, toward, 'top')
}

/** skirt: a flared foot of stairs along a wall (cells [x, z] outside it) on a projecting plinth course at y - 1,
 *  rising toward the wall: the ground floor's shutters stand on it. `stone` and `steps` may be mix()es. */
export function skirt(c, cells, y, stone, steps, toward) {
  for (const [x, z] of cells) {
    c.set(x, y - 1, z, stone)
    stair(c, x, y, z, typeof steps === 'function' ? steps(x, y, z) : steps, toward)
  }
}

/** bench: a row of stair seats (cells [x, y, z]) whose sitters face `facing` (the backrest the other side). */
export function bench(c, cells, id, facing) {
  const back = { north: 'south', south: 'north', east: 'west', west: 'east' }[facing]
  for (const [x, y, z] of cells) stair(c, x, y, z, id, back)
}

/** coachGate: a timber gateway across a passage (x0..x1 the posts, on line z): posts from y0+1 to beamY-1, a beam at
 *  beamY, arch brackets in the inner corners, a slab cap on the beam (`capCells`: the x's that take one) and a lantern
 *  hanging under the beam's middle. */
export function coachGate(c, { x0, x1, z, y0 = 0, beamY = 4, capCells = null }, { post, beam, bracket, cap }) {
  for (const x of [x0, x1]) for (let y = y0 + 1; y < beamY; y++) c.set(x, y, z, post, { axis: 'y' })
  for (let x = x0; x <= x1; x++) c.set(x, beamY, z, beam, { axis: 'x' })
  stair(c, x0 + 1, beamY - 1, z, bracket, 'west', 'top')
  stair(c, x1 - 1, beamY - 1, z, bracket, 'east', 'top')
  for (const x of capCells ?? []) slab(c, x, beamY + 1, z, cap)
}

/** stall: a horse stall against a wall (x line `back`, the stall's cells z0..z1 in front of it at x = back + dir): a
 *  hay bale and a trough (a cauldron) at the wall, a fence divider on line `divider` (z) from the wall out `depth`. */
export function stall(c, { back, dir, z0, z1, y = 1, divider = null, depth = 2 }, { fence, hay = 'hay_block', trough = 'cauldron' }) {
  c.set(back + dir, y, z0, hay, { axis: 'y' })
  c.set(back + dir, y, z1, trough)
  if (divider !== null) for (let d = 1; d <= depth; d++) c.set(back + dir * d, y, divider, fence)
}

/** pitchDormer: a gabled dormer in a 45-degree slope whose ridge runs along z (the Arcade Inn's east slope): its face
 *  on line x = `face`, standing on the slope there (y0 = the main roof's y over `face`), centred on z = `zc`: two posts,
 *  a two-high window between them and infill in its apex, and its own little 45-degree roof (ridge along x) from one
 *  block outside the face (`dir`: +1 for an east slope, -1 for a west one) back into the main slope until it dies into
 *  it (`main(x)`: the main roof's y over column x), its valley stairs taking the main roof's cells where they meet. */
export function pitchDormer(c, { face, zc, dir, main }, { roof, frame, infill, glass }) {
  const y0 = main(face)
  for (const dz of [-1, 1]) for (let y = y0; y <= y0 + 1; y++) c.set(face, y, zc + dz, frame, { axis: 'y' })
  for (let y = y0; y <= y0 + 1; y++) c.set(face, y, zc, glass)
  c.set(face, y0 + 2, zc, infill)
  for (let x = face + dir; main(x) <= y0 + 3; x -= dir) for (let dz = -2; dz <= 2; dz++) {
    const y = y0 + 3 - Math.abs(dz)
    if (y < main(x)) continue
    if (dz === 0) c.set(x, y, zc, roof.full)
    else c.set(x, y, zc + dz, roof.stairs, { facing: dz < 0 ? 'south' : 'north', half: 'bottom', shape: 'straight', waterlogged: 'false' })
  }
  c.clear(face - dir, y0 + 1, zc) // the window looks into the attic, not at the back of a tile
}
