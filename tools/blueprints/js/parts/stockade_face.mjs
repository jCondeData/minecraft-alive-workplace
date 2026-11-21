// A stockade's outer face and its yard gear (parts library, tools/blueprints/REBUILD.md; grown from the Raiders'
// Stockade, 49, round 2): a stake wall set one block back behind a line of proud posts with a stepped rubble bank
// between them (so the wall reads in three planes, not one), bare wagon hoops over a cart bed, and a cloth spread on
// the ground with a border. Every block comes from the caller's palette; none is picked here.
//   proudFace   on the outer ring of a square: posts (a log upright, own height, a tip on top) at the given cells and a
//               stair bank at y0 between them, each stair stepping up toward the wall behind it
//   bareHoops   a wagon's hoops with the cover pulled off: a fence arch (two uprights and a crown) across a cart bed
//   groundCloth a cloth (carpets) spread flat on the ground: a border colour round a field colour
import { ringFacing } from './palisade_kit.mjs'

const INWARD = { north: 'south', south: 'north', west: 'east', east: 'west' }

/** proudFace: along the outer ring of `box` ({x0, z0, x1, z1}) at y0, for each cell: a post when `post(x, z)` returns
 *  {h, tip, tipProps} (a log of `log`, axis y, from y0 up h blocks, then `tip` on top when given), nothing when
 *  `skip(x, z)` is true (a gateway, a tower's foot), otherwise a stair of `step(x, z)` facing the wall behind it (its
 *  high side against the wall: the bank steps up toward the stakes). Returns the post cells. */
export function proudFace(c, box, y0, { log, step }, post, skip = () => false) {
  const { x0, z0, x1, z1 } = box
  const out = []
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (!(x === x0 || x === x1 || z === z0 || z === z1) || skip(x, z)) continue
    const p = post(x, z)
    if (p) {
      for (let y = y0; y < y0 + p.h; y++) c.set(x, y, z, log, { axis: 'y' })
      if (p.tip) c.set(x, y0 + p.h, z, p.tip, p.tipProps ?? {})
      out.push([x, z])
    } else {
      c.set(x, y0, z, step(x, z), { facing: INWARD[ringFacing(box, x, z)], half: 'bottom', shape: 'straight', waterlogged: 'false' })
    }
  }
  return out
}

/** bareHoops: over a cart bed along x (its beams at z0 and z1, top at y), at each x in `xs`, a hoop of `fence`: an
 *  upright on each beam at y + 1 and y + 2 and a crown joining them at y + 2 (a fence arch, no corner left without
 *  its post). */
export function bareHoops(c, { xs, z0, z1, y }, fence) {
  for (const x of xs) {
    for (const z of [z0, z1]) for (const dy of [1, 2]) c.set(x, y + dy, z, fence)
    for (let z = z0 + 1; z < z1; z++) c.set(x, y + 2, z, fence)
  }
}

/** groundCloth: carpets over (x0, z0)..(x1, z1) at y: `border` round the edge, `field` inside; `skip(x, z)` leaves a
 *  cell (something already stands there). Returns the cells laid. */
export function groundCloth(c, { x0, z0, x1, z1, y }, { border, field }, skip = () => false) {
  const out = []
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (skip(x, z) || c.get(x, y, z)) continue
    const edge = x === x0 || x === x1 || z === z0 || z === z1
    c.set(x, y, z, edge ? border : field)
    out.push([x, y, z])
  }
  return out
}
