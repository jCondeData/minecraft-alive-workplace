// Porches: a hooded door, an open porch on posts, and the ground round the house (flower beds, woodpile) (parts library).
import { DIRS, OPP, slab, stair } from './canvas.mjs'
import { railingRun } from './railings.mjs'

/** hood: a little tiled hood over a door: two stairs rising to a full block, a lantern hanging under it. */
export function hood(c, x, y, z, roof) {
  stair(c, x - 1, y, z, roof.stairs, 'east')
  stair(c, x + 1, y, z, roof.stairs, 'west')
  c.set(x, y, z, roof.full)
  c.set(x, y - 1, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
}

/** porch: an open porch in front of a wall (the porch spans x0..x1, z0..z1, the house on its far side): a deck at y
 *  (`deck`), posts at the outer corners up to `topY`, a lean-to roof of `roof` stairs falling away from the house
 *  (`away`: the side that faces out, 'north' for a front porch), and a railing round the open sides with a gap at
 *  `gap` (the steps) when given. */
export function porch(c, { x0, x1, z0, z1, y = 0, topY = 3, away = 'north' }, { deck, post, roof, rail = null, gap = null }) {
  c.fill(x0, y, z0, x1, y, z1, deck)
  const outer = away === 'north' ? z0 : z1
  for (const x of [x0, x1]) for (let yy = y + 1; yy <= topY; yy++) c.set(x, yy, outer, post, { axis: 'y' })
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) stair(c, x, topY + 1, z, roof.stairs, away === 'north' ? 'south' : 'north')
  if (rail) {
    const inner = away === 'north' ? z1 : z0
    railingRun(c, [[x0, y + 1, inner], [x0, y + 1, outer], [x1, y + 1, outer], [x1, y + 1, inner]], null, rail)
    for (const x of [x0, x1]) c.set(x, y + 1, outer, post, { axis: 'y' })
    if (gap) c.clear(gap[0], y + 1, gap[1])
  }
}

/** flowerBed: a bed of its own soil with flowers in it (never plants on bare ground). */
export function flowerBed(c, x0, x1, z, plants, soil = 'grass_block') {
  for (let x = x0; x <= x1; x++) {
    c.set(x, 0, z, soil, soil === 'grass_block' ? { snowy: 'false' } : {})
    c.set(x, 1, z, plants[(x - x0) % plants.length])
  }
}

/** woodpile: split logs stacked against a wall, two high in the middle. */
export function woodpile(c, x, z0, z1, log) {
  for (let z = z0; z <= z1; z++) c.set(x, 0, z, log, { axis: 'x' })
  for (let z = z0 + 1; z <= z1 - 1; z++) c.set(x, 1, z, log, { axis: 'x' })
}

/** doorCanopy: a little pent canopy over a door in a wall (`door`: its lower half [x, y, z]; `out`: the side it opens
 *  to): a `roof.slab` one out from the wall `height` over the door's foot, a `roof.stairs` beside it on the `open`
 *  side falling away (the other side rests on a pier or a wall), and a lantern hung under the slab. Grown from the
 *  Terrace (49), whose paired front doors share one pier between their two canopies. */
export function doorCanopy(c, [x, y, z], out, open, roof, { lantern = true, height = 3 } = {}) {
  const [ox, oz] = DIRS[out], [sx, sz] = DIRS[open]
  const cx = x + ox, cz = z + oz, cy = y + height
  slab(c, cx, cy, cz, roof.slab, 'bottom')
  stair(c, cx + sx, cy, cz + sz, roof.stairs, OPP[open])
  if (lantern) c.set(cx, cy - 1, cz, 'lantern', { hanging: 'true', waterlogged: 'false' })
}

