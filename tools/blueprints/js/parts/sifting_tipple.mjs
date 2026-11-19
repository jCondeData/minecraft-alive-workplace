// Tipples and screening works (parts library, tools/blueprints/REBUILD.md; grown from the Sifting Shed, "the Tipple",
// 49): a trestle of legs on stone footings with knee braces under the beam, a chute's funnel under a loft-floor hatch,
// a mounded heap of a gravity block, a covered incline gallery climbing into a loft, and sieves hung on a rail. Every
// block comes from the caller's palette; none is picked here.
import { stair, trapdoor } from './canvas.mjs'

/** trestle: a leg at each [x, z] of `legs`, a `footing` block (an id or mix()) at footY and `log` from footY + 1 to
 *  topY, with an upside-down `brace` stair under the beam (at topY) on each side of the leg named in `braces(x, z)`
 *  (a list of 'north' | 'south' | 'west' | 'east'), its full side against the leg. */
export function trestle(c, legs, footY, topY, { footing, log, brace, braces = () => [] }) {
  const step = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }
  const back = { north: 'south', south: 'north', west: 'east', east: 'west' }
  for (const [x, z] of legs) {
    c.set(x, footY, z, typeof footing === 'function' ? footing(x, footY, z) : footing)
    for (let y = footY + 1; y <= topY; y++) c.set(x, y, z, log, { axis: 'y' })
    for (const side of braces(x, z)) {
      const [dx, dz] = step[side]
      stair(c, x + dx, topY, z + dz, brace, back[side], 'top')
    }
  }
}

/** chuteFunnel: the hatch over a sieve and the funnel under it. At (cx, y, cz) a closed `hatch` trapdoor (top half, so
 *  the loft floor stays level); round it at y - 1 four upside-down `stairs` whose low side faces the chute and a
 *  `corner` block at each diagonal, the chute itself left open down to whatever stands under it. */
export function chuteFunnel(c, cx, y, cz, { hatch, stairs, corner }) {
  trapdoor(c, cx, y, cz, hatch, 'north', 'top', false)
  const fy = y - 1
  stair(c, cx - 1, fy, cz, stairs, 'east', 'top')
  stair(c, cx + 1, fy, cz, stairs, 'west', 'top')
  stair(c, cx, fy, cz - 1, stairs, 'south', 'top')
  stair(c, cx, fy, cz + 1, stairs, 'north', 'top')
  for (const [dx, dz] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) c.set(cx + dx, fy, cz + dz, corner)
  c.clear(cx, fy, cz)
}

/** heap: a mounded heap of a gravity `block` on y `y0` (base: [x, z] cells, each must stand on something solid at
 *  y0 - 1) with `crown` cells [x, y, z] above, each over a base cell. */
export function heap(c, y0, base, crown, block) {
  for (const [x, z] of base) c.set(x, y0, z, block)
  for (const [x, y, z] of crown) c.set(x, y, z, block)
}

/** inclineGallery: a covered incline whose deck drops one block a row from `deckTop` at zTop to the ground going
 *  toward zFoot (+z), across x0..x1 (the outer columns are the roof's overhang; the deck and rails are x0 + 1..x1 - 1).
 *  Deck: `deck` stairs rising toward zTop (north when zFoot > zTop), every cell under the deck with less than two
 *  clear under it (no walker fits) filled with `fill` (an id or mix()). Rails: `rail` at deck + 1 on the deck's
 *  edge columns, a `post` log up to the roof at every other row. Roof: `roof.stairs` at deck + 3 across x0..x1,
 *  rising the same way; `roofTop: false` leaves the top row's roof off (when it would run in under a higher roof's
 *  overhang, which the kit seals by carrying that roof's wall out); `eaves: false` keeps the roof to the deck's width
 *  (no x0 / x1 overhang: the space under the deck stays near open air, holes.py's `open` check). Returns { deckY(z) }. */
export function inclineGallery(c, { x0, x1, zTop, zFoot, deckTop }, { deck, deckFull, fill, rail, post, roof, roofTop = true, eaves = true }) {
  const deckY = z => deckTop - (z - zTop)
  const rise = zFoot > zTop ? 'north' : 'south'
  const dir = zFoot > zTop ? 1 : -1
  for (let z = zTop; dir > 0 ? z <= zFoot : z >= zFoot; z += dir) {
    const d = deckY(z)
    for (let x = x0 + 1; x <= x1 - 1; x++) {
      if (z === zTop) c.set(x, d, z, deckFull)
      else stair(c, x, d, z, deck, rise)
      if (d < 3) for (let y = Math.max(0, d - 2); y < d; y++) c.set(x, y, z, typeof fill === 'function' ? fill(x, y, z) : fill)
    }
    if (d < 0) continue
    const ry = d + 3
    if (roofTop || z !== zTop) for (let x = eaves ? x0 : x0 + 1; x <= (eaves ? x1 : x1 - 1); x++) stair(c, x, ry, z, roof.stairs, rise)
    for (const x of [x0 + 1, x1 - 1]) {
      if ((z - zTop) % 2 === 1) for (let y = d + 1; y < ry; y++) c.set(x, y, z, post, { axis: 'y' })
      else c.set(x, d + 1, z, rail)
    }
  }
  return { deckY }
}

/** sieveRack: sieves (open `sieve` trapdoors) hung to dry on a fence rail: `rail` posts at [x, z] of `posts` from y
 *  1 to railY, the rail between them at railY, and a trapdoor at each [x, z] of `sieves` at railY, open, its board
 *  against the rail (facing away from it). */
export function sieveRack(c, { x, zs: [z0, z1], railY }, sieves, { rail, sieve }) {
  for (const z of [z0, z1]) for (let y = 1; y <= railY; y++) c.set(x, y, z, rail)
  for (let z = z0 + 1; z < z1; z++) c.set(x, railY, z, rail)
  for (const [sx, sz] of sieves) trapdoor(c, sx, railY, sz, sieve, sx < x ? 'west' : 'east', 'bottom', true)
}
