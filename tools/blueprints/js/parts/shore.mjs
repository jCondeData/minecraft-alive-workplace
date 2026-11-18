// Shore parts: an upturned boat for a roof, a pier on piles over water, and a fish-drying rack (a hjell). Grown from
// the Fisher's Hut (49, the Upturned Boat). Every block comes from the palette passed in.
import { stair, slab } from './canvas.mjs'

// The hull's cross-section, half-width w (cells from the keel to the gunwale): the gunwale strake stands `wall` high
// from y0 + 1, the turn of the bilge is a stair on it, then each cell in toward the keel steps one up (a full block
// with a stair over it, the cell beside the keel a bottom slab), the keel on top. A boat turned over: steep sides,
// flattening over the top.
function section(w, wall) {
  const cells = [] // [d, dy, role]: d out from the keel, dy over y0
  for (let y = 1; y <= wall; y++) cells.push([w, y, 'side'])
  cells.push([w, wall + 1, 'turn'])
  for (let d = w - 1; d >= 1; d--) {
    const k = w - d
    cells.push([d, wall + k, 'full'])
    cells.push([d, wall + k + 1, d === 1 ? 'slab' : 'stairs'])
  }
  return cells
}

/** upturnedHull: an old boat turned keel-up as a roof and walls, its keel running along `axis` ('x' or 'z') over
 *  `along` [l0, l1] on the line `centre` (the other coordinate), standing on y0. `width(v)` gives the half-width at each
 *  place along (3 midships, then 2 and 1 where the hull draws in; 0 or less: nothing; 'stem': the stem post where the
 *  keel comes down to the ground, its head a stair curling in; `stemTop` raises the posts to that y, standing proud
 *  of the ends like a boat's stem and sternpost). `inset`: the walls (pal.side) stand one block in from the gunwale, so
 *  the eave ring (pal.turn) overhangs them, a hull resting on walls. `keep(x, y, z)` false leaves a cell to what is there. The section is `wall` high at its gunwale and the
 *  keel sits w over it, so a hull that draws in also comes down toward its ends. `ends` 'flat' leaves the section's ends open for a transom.
 *  pal: { side (the topsides: an id or a mix(); sideAlong: a log laid along the hull, like strakes), turn (the boot-top: a stairs id), full, stairs, slab (the old
 *  bottom), keel (a log), stem (the stem head's stairs, default turn) }. Returns { keelY(v), interior(v) } (interior: the [d, y] cells inside the section). */
export function upturnedHull(c, { axis, along: [l0, l1], centre, y0 = 0, wall = 2, width, stemTop = null, inset = false, keep = () => true }, pal) {
  const put = (x, y, z, id, props = {}) => { if (keep(x, y, z)) c.set(x, y, z, id, props) }
  const at = (v, d, y, id, props = {}) => {
    const [x, z] = axis === 'x' ? [v, centre + d] : [centre + d, v]
    put(x, y, z, id, props)
  }
  const inward = (sign) => axis === 'x' ? (sign > 0 ? 'north' : 'south') : (sign > 0 ? 'west' : 'east')
  const keelY = v => y0 + wall + (width(v) === 'stem' ? 0 : width(v))
  for (let v = l0; v <= l1; v++) {
    const w = width(v)
    if (w !== 'stem' && !(w > 0)) continue
    if (w === 'stem') { // the stem post where the keel comes down to the ground, its head curling in toward the body
      const body = width(v - 1) === 'stem' || !(width(v - 1) > 0) ? v + 1 : v - 1
      const top = stemTop ?? keelY(body) - 1
      for (let y = y0 + 1; y <= top; y++) at(v, 0, y, pal.keel, { axis: 'y' })
      const [x, z] = axis === 'x' ? [v, centre] : [centre, v]
      if (keep(x, top + 1, z)) stair(c, x, top + 1, z, pal.stem ?? pal.turn, axis === 'x' ? (body > v ? 'east' : 'west') : (body > v ? 'south' : 'north'))
      continue
    }
    for (const s of [-1, 1]) for (const [d0, dy, role] of section(w, wall)) {
      const d = role === 'side' && inset ? d0 - 1 : d0 // inset: the walls stand a block in, the eave ring overhangs them
      const y = y0 + dy, [x, z] = axis === 'x' ? [v, centre + s * d] : [centre + s * d, v]
      if (!keep(x, y, z)) continue
      if (role === 'side') c.set(x, y, z, pal.side, pal.sideAlong ? { axis } : {})
      else if (role === 'turn') stair(c, x, y, z, pal.turn, inward(s))
      else if (role === 'full') c.set(x, y, z, pal.full)
      else if (role === 'stairs') stair(c, x, y, z, pal.stairs, inward(s))
      else slab(c, x, y, z, pal.slab, 'bottom')
    }
    at(v, 0, keelY(v), pal.keel, { axis })
  }
  const interior = v => {
    const w = width(v), cells = []
    const r = inset ? w - 2 : w - 1
    for (let d = -r; d <= r; d++) for (let y = y0 + 1; y <= y0 + wall + (w - 1 - Math.abs(d)); y++) cells.push([d, y])
    return cells
  }
  return { keelY, interior }
}

/** transom: closes an upturned hull's open end at `v` with boards (`id`, a mix() works) across its inside section,
 *  `skip(d, y)` leaving a door or a window. */
export function transom(c, { axis, centre }, v, hull, id, skip = () => false) {
  for (const [d, y] of hull.interior(v)) {
    if (skip(d, y)) continue
    const [x, z] = axis === 'x' ? [v, centre + d] : [centre + d, v]
    c.set(x, y, z, id)
  }
}

/** pier: a deck of top slabs at y (one over the water's surface) over x0..x1, z0..z1, on log piles at `piles`
 *  [[x, z, h]]: each pile a log in the deck's layer (the builder takes it down to the bed) rising h more above the
 *  deck, a cap on its head (`cap`: a fence for a bollard, or null). A pile's bare top at deck height would be a bank a
 *  fisher could stand on, so every pile carries its cap or rises. pal: { deck (a slab id), pile (a log), cap }. */
export function pier(c, { x0, x1, z0, z1, y = 0 }, piles, pal) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) slab(c, x, y, z, pal.deck, 'top')
  for (const [x, z, h = 0, cap = pal.cap] of piles) {
    for (let yy = y; yy <= y + h; yy++) c.set(x, yy, z, pal.pile, { axis: 'y' })
    if (cap) c.set(x, y + h + 1, z, cap)
  }
}

/** dryingRack: a hjell, the open rack fish dry on: log posts at `posts` (positions along the line) standing from y0 to
 *  top - 1, a log beam along `axis` over `span` [a0, a1] at top, and bundles (`hang`, e.g. dried kelp) hanging under the
 *  beam at the positions in `bundles` (on `pal.links` links of `pal.link`, a chain, when given). */
export function dryingRack(c, { axis, line, span: [a0, a1], posts, bundles = [], y0 = 0, top }, pal) {
  const xz = u => (axis === 'x' ? [u, line] : [line, u])
  for (const u of posts) { const [x, z] = xz(u); for (let y = y0; y < top; y++) c.set(x, y, z, pal.post, /_(log|wood|stem|hyphae)$/.test(pal.post) ? { axis: 'y' } : {}) }
  for (let u = a0; u <= a1; u++) { const [x, z] = xz(u); c.set(x, top, z, pal.beam, { axis }) }
  const drop = pal.link ? (pal.links ?? 1) : 0 // links of chain (pal.link) between the beam and each bundle
  for (const u of bundles) {
    const [x, z] = xz(u)
    for (let i = 1; i <= drop; i++) c.set(x, top - i, z, pal.link, { axis: 'y', waterlogged: 'false' })
    c.set(x, top - 1 - drop, z, pal.hang, pal.hangProps ?? {})
  }
}
