// Strong rooms and shopfronts (parts library, tools/blueprints/REBUILD.md; grown from the Jeweler's Workshop, 49):
// a stone barrel vault from its cross-section, a stepped buttress, a button for an iron door (on a wall or the floor),
// and a glass case projecting from a shopfront with one piece on show in it. Blocks come from the family's palette.
import { slab, stair } from './canvas.mjs'

/** jwBarrelVault: a curved roof from one cross-section repeated along z from z0 to z1. `section` is a list of
 *  [x, y, shape] with shape 'full', 'slab' (a bottom slab), or 'stairs-<facing>' (a bottom stair whose high side
 *  faces that way: up the curve); `mat` is {full, stairs, slab}. Returns each x's underside (the lowest y of the
 *  section at that x) for the end walls. */
export function jwBarrelVault(c, section, z0, z1, mat) {
  for (let z = z0; z <= z1; z++) for (const [x, y, shape] of section) {
    if (shape === 'full') c.set(x, y, z, mat.full)
    else if (shape === 'slab') slab(c, x, y, z, mat.slab, 'bottom')
    else stair(c, x, y, z, mat.stairs, shape.split('-')[1])
  }
  const under = {}
  for (const [x, y] of section) under[x] = Math.min(under[x] ?? Infinity, y)
  return under
}

/** jwButtress: a stepped pier against a wall: `stone` from y0 to top - 1 at [x, z], a stair at `top` sloping away from
 *  the wall (`wall`: the side the wall is on). `stone` may be a mix(). */
export function jwButtress(c, x, z, y0, top, stone, stairs, wall) {
  for (let y = y0; y < top; y++) c.set(x, y, z, stone)
  stair(c, x, top, z, stairs, wall)
}

/** jwButton: a stone button on a wall's face (`facing`: the way it sticks out), or on the floor (facing null). */
export function jwButton(c, [x, y, z], facing, id = 'stone_button') {
  if (facing) c.set(x, y, z, id, { face: 'wall', facing, powered: 'false' })
  else c.set(x, y, z, id, { face: 'floor', facing: 'north', powered: 'false' })
}

/** jwVitrine: a glass case projecting a block from a wall: its posts at x0 and x1 on the line z (from y0 to y0 + 2), an
 *  apron between them at y0, glass in `front` (its outer face) and `sides` (cells beside the piece in the wall's
 *  opening), `open` cells cleared (the case's open back over the piece), and the piece (`gem`: [x, y, z], `block`,
 *  `props`) standing on its plinth (`plinth`, under the piece). */
export function jwVitrine(c, { x0, x1, z, y0 = 1 }, { front, sides, open, gem }, { post, apron, glass, plinth, block, props = {} }) {
  for (const x of [x0, x1]) for (let y = y0; y <= y0 + 2; y++) c.set(x, y, z, post, { axis: 'y' })
  for (let x = x0 + 1; x < x1; x++) c.set(x, y0, z, apron)
  for (const [x, y, zz] of [...front, ...sides]) c.set(x, y, zz, glass)
  for (const [x, y, zz] of open) c.clear(x, y, zz)
  const [gx, gy, gz] = gem
  c.set(gx, gy - 1, gz, plinth)
  c.set(gx, gy, gz, block, props)
}
