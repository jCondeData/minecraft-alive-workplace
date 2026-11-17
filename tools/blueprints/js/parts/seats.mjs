// Seats out of doors (parts library, tools/blueprints/REBUILD.md): a bench of stair seats with a back and ends, and the
// raised stone bed a tree or flowers grow from (a bed's front rim can be a bench's back). Every block comes from the
// family's palette; none is picked here. Indoor chairs and couches are furniture.mjs's.
import { DIRS, OPP, slab, stair, trapdoor } from './canvas.mjs'

/** benchRun: a row of stair seats from x0 to x1 (along x) at y, z, the sitters facing `facing` ('north' or 'south').
 *  `back`: a wood id whose open trapdoors stand behind the seats one block up, as a slatted backrest (null: none, the
 *  seat backs onto whatever is behind it). `ends`: a block for an arm at each end (x0 - 1 and x1 + 1), capped with
 *  `endCap` (a bottom slab) when given. `spot`: record each seat as a 'bench' furniture spot (off by default: a
 *  decoration that hands out no seat keeps no spots). */
export function benchRun(c, { x0, x1, y, z, facing = 'north' }, { seat, back = null, ends = null, endCap = null, spot = false, wood = null }) {
  const [, dz] = DIRS[OPP[facing]] // the side the backrest is on
  for (let x = x0; x <= x1; x++) {
    c.free([x, y, z])
    stair(c, x, y, z, seat, OPP[facing])
    if (back) trapdoor(c, x, y + 1, z + dz, `${back}_trapdoor`, facing === 'north' ? 'south' : 'north', 'bottom', true)
    if (spot) c.spot('bench', [x, y, z], facing, [[0, 0, 0]], wood)
  }
  if (ends) for (const x of [x0 - 1, x1 + 1]) {
    c.set(x, y, z, ends)
    if (endCap) slab(c, x, y + 1, z, endCap)
  }
}

/** raisedBed: a stone-rimmed bed x0..x1, z0..z1 at y: the rim (`rim`, a block or a mix()) round the edge, coped with
 *  `cap` bottom slabs one up, and `soil` inside (a block, or a function of (x, z) for a bed of mixed soils: grass
 *  where flowers stand, rooted dirt under a trunk, which never turns to dirt under a log). `skipCap(x, z)` leaves a
 *  rim cell's coping off (a bench back stands there instead). Returns the inside cells as [x, z]. */
export function raisedBed(c, { x0, x1, z0, z1, y = 0 }, { rim, cap, soil, skipCap = () => false }) {
  const inside = []
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const edge = x === x0 || x === x1 || z === z0 || z === z1
    if (edge) {
      c.set(x, y, z, rim)
      if (!skipCap(x, z)) slab(c, x, y + 1, z, cap)
    } else {
      const id = typeof soil === 'function' ? soil(x, z) : soil
      c.set(x, y, z, id, id.endsWith('grass_block') ? { snowy: 'false' } : {})
      inside.push([x, z])
    }
  }
  return inside
}
