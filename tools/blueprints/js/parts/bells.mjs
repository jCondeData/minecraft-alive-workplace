// Bells and stone lanterns (grown from the Shift Bell, ROADMAP 49): a square dais with a step in each face, a head
// that flares out from a narrow waist on corbels, and an open chamber with a bell hung in its middle. Every block is
// passed in; nothing here picks one.
import { stair } from './canvas.mjs'

/** facedDais: a square dais at y over x0..x1, z0..z1, every face the same: `block` (a block or a mix) round the edge,
 *  `footing` in the middle cells, and a `step` stair in the middle of each face, rising inward (so the dais can be
 *  climbed from any side). Faces must be odd so each has a middle. */
export function facedDais(c, { x0, z0, x1, z1, y }, { block, footing = block, step }) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const edge = x === x0 || x === x1 || z === z0 || z === z1
    c.set(x, y, z, edge ? block : footing)
  }
  const mx = (x0 + x1) / 2, mz = (z0 + z1) / 2
  if (!Number.isInteger(mx) || !Number.isInteger(mz)) throw new Error('facedDais: faces must be odd')
  stair(c, mx, y, z0, step, 'south'); stair(c, mx, y, z1, step, 'north')
  stair(c, x0, y, mz, step, 'east'); stair(c, x1, y, mz, step, 'west')
}

/** flaredHead: a ring of upside-down `stairs` at y round x0..x1, z0..z1, each rising inward so the ring flares out from
 *  a narrower waist under it (corbels), with full `corner` blocks at the four corners (sturdy undersides: lanterns can
 *  hang from them) and `middle` filling the inside (the floor of whatever stands on the head). */
export function flaredHead(c, { x0, z0, x1, z1, y }, { stairs, corner, middle }) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const ex = x === x0 || x === x1, ez = z === z0 || z === z1
    if (ex && ez) c.set(x, y, z, corner)
    else if (ez) stair(c, x, y, z, stairs, z === z0 ? 'south' : 'north', 'top')
    else if (ex) stair(c, x, y, z, stairs, x === x0 ? 'east' : 'west', 'top')
    else c.set(x, y, z, middle)
  }
}

/** bellChamber: an open chamber at y: a `post` (a wall or fence, or [id, props]) at each corner of x0..x1, z0..z1, the faces between
 *  them left open, and a bell at `bell.at` (attachment and facing as given; a ceiling bell needs a full block above,
 *  a floor bell one below: the caller draws it). */
export function bellChamber(c, { x0, z0, x1, z1, y }, { post }, bell) {
  const [id, props] = Array.isArray(post) ? post : [post, {}]
  for (const [x, z] of [[x0, z0], [x1, z0], [x0, z1], [x1, z1]]) c.set(x, y, z, id, props)
  const [bx, by, bz] = bell.at
  c.set(bx, by, bz, 'bell', { attachment: bell.attachment ?? 'ceiling', facing: bell.facing ?? 'north', powered: 'false' })
}
