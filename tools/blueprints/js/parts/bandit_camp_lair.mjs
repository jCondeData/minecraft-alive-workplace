// Outlaw camps (parts library, tools/blueprints/REBUILD.md; grown from the Bandit Camp, 49): a patchwork of stolen
// cloth (a base cloth with rectangles of others sewn in), a pitched tent with optional upright walls, closed back and a
// cut mouth in its front gable, a ridge pole lying on the peak, a shed marquee open along its front (a chief's tent),
// a crow's nest on four legs with its hatch and ladder, a ring of stones round an open fire, a trophy stake, a hitching
// rail. Every block comes from the caller's palette; none is picked here.
import { stair, slab } from './canvas.mjs'

/** patchwork: a cloth function (x, y, z) -> block: `base` everywhere but inside each patch
 *  {x: [x0, x1], y: [y0, y1], z: [z0, z1], cloth}, where that patch's cloth is sewn in (stolen cloth mended in
 *  rectangles, not confetti). Missing ranges mean "any". */
export function patchwork(base, patches = []) {
  const inR = (v, r) => !r || (v >= r[0] && v <= r[1])
  return (x, y, z) => {
    for (const p of patches) if (inR(x, p.x) && inR(y, p.y) && inR(z, p.z)) return p.cloth
    return base
  }
}

/** pitchedTent: a tent across `across` [a0, a1] (the span; odd spans peak in one cell) running `along` [l0, l1]
 *  (axis 'z': across is x, along z; axis 'x': the other way), from y0. Its sides stand upright for `wall` layers,
 *  then step in a cell a layer to the peak. The `back` end ('l0' or 'l1') is closed with cloth; the other end (the
 *  front) is closed too except the `mouth` {width, height} cut in its middle from y0. `hem` (optional; a block, or
 *  (x, y, z) -> a block or null) lays a carpet on every ledge a step leaves, so the steps read as a slope (return null
 *  for a ledge too high to reach: check.py counts a carpet up there as a stand nobody can climb to). Returns {peakY, mid, front, back, inside: the
 *  floor cells [x, y0, z] within the walls and the gables}. */
export function pitchedTent(c, { axis = 'z', across: [a0, a1], along: [l0, l1], y0, wall = 1, back = 'l0', mouth = null }, { cloth, hem = null }) {
  const at = (u, y, v) => (axis === 'z' ? [u, y, v] : [v, y, u])
  const shut = back === 'l0' ? l0 : l1
  const front = back === 'l0' ? l1 : l0
  const mid = (a0 + a1) / 2
  const steps = []
  for (let k = 0; k < wall - 1; k++) steps.push(0)
  for (let s = 0; a0 + s <= a1 - s; s++) steps.push(s)
  const put = (u, y, v, id) => { const p = at(u, y, v); const b = typeof id === 'function' ? id(...p) : id; if (b) c.set(...p, b) }
  steps.forEach((s, k) => {
    const y = y0 + k
    const L = a0 + s, R = a1 - s
    for (let v = l0; v <= l1; v++) { put(L, y, v, cloth); put(R, y, v, cloth) }
    for (let u = L + 1; u < R; u++) {
      put(u, y, shut, cloth)
      const open = mouth && Math.abs(u - mid) <= (mouth.width - 1) / 2 && y < y0 + mouth.height
      if (!open) put(u, y, front, cloth)
    }
    const next = steps[k + 1]
    if (hem && next !== undefined && next > s) for (let v = l0; v <= l1; v++) { put(L, y + 1, v, hem); put(R, y + 1, v, hem) }
  })
  const inside = []
  const lo = Math.min(shut, front), hi = Math.max(shut, front)
  for (let u = a0 + 1; u < a1; u++) for (let v = lo + 1; v < hi; v++) inside.push(at(u, y0, v))
  return { peakY: y0 + steps.length - 1, mid, front, back: shut, inside }
}

/** ridgePole: a log lying along `axis` at height y over the ridge's line `u` (x for axis 'z', z for axis 'x'), from
 *  `from` to `to` (it may run a cell out past a tent's mouth, where a lantern can hang from it). */
export function ridgePole(c, { axis = 'z', u, y, from, to }, log) {
  for (let v = Math.min(from, to); v <= Math.max(from, to); v++) c.set(...(axis === 'z' ? [u, y, v] : [v, y, u]), log, { axis })
}

/** marquee: a chief's shed tent over x0..x1, open along its front at z = zFront and closed at the back (zBack), from
 *  y0. The roof is one cell of cloth thick and climbs a cell a row from the front eave (`eaveY`) to the back; the
 *  front corners stand on `post` logs; the sides and back are cloth walls up to the roof. `cloth(x, y, z)` paints
 *  walls and roof (stripes come from the caller), `valance` (optional) a carpet hem along the front eave's top.
 *  Works for zBack > zFront (front facing north). Returns {roofY(z), inside: [x, y0, z] cells}. */
export function marquee(c, { x0, x1, zFront, zBack, y0, eaveY }, { cloth, post, valance = null }) {
  const roofY = z => eaveY + (z - zFront)
  const put = (x, y, z, id) => c.set(x, y, z, typeof id === 'function' ? id(x, y, z) : id)
  for (const x of [x0, x1]) for (let y = y0; y < eaveY; y++) c.set(x, y, zFront, post, { axis: 'y' })
  for (let z = zFront; z <= zBack; z++) {
    for (let x = x0; x <= x1; x++) put(x, roofY(z), z, cloth)
    if (z === zFront) continue
    for (let y = y0; y < roofY(z); y++) { put(x0, y, z, cloth); put(x1, y, z, cloth) }
  }
  for (let x = x0 + 1; x < x1; x++) for (let y = y0; y < roofY(zBack); y++) put(x, y, zBack, cloth)
  if (valance) for (let x = x0; x <= x1; x++) put(x, eaveY + 1, zFront, valance)
  const inside = []
  for (let x = x0 + 1; x < x1; x++) for (let z = zFront; z < zBack; z++) inside.push([x, y0, z])
  return { roofY, inside }
}

/** crowsNest: a lookout deck at deckY over x0..x1, z0..z1 on four corner legs (from y0, running up through the deck
 *  as the rail's corner posts), a fence rail round the deck's edge, a hatch at `hatch` [x, z] (on the deck's edge, by
 *  a leg) with a ladder up to it (`ladderFacing`: the side of its wall it hangs on), a ring of braces at braceY on
 *  every side but `openSide` ('north' ... : the ladder's side), and top-stair brackets under the deck by each leg.
 *  `tall` [{x, z, top}] runs a leg on up to y = top (a flag's mast). Above the deck the legs are `railPost` (a fence:
 *  a thin mast and rail posts) when given, else the leg's log; the braces are `brace` (a lighter log) or the leg's. */
export function crowsNest(c, { x0, x1, z0, z1, y0, deckY, braceY, hatch, ladderFacing, openSide = null, tall = [] }, { leg, deck, rail, ladder, bracket, railPost = null, brace = null }) {
  const corner = (x, z) => (x === x0 || x === x1) && (z === z0 || z === z1)
  for (const x of [x0, x1]) for (const z of [z0, z1]) {
    const t = tall.find(l => l.x === x && l.z === z)
    for (let y = y0; y <= deckY; y++) c.set(x, y, z, leg, { axis: 'y' })
    for (let y = deckY + 1; y <= (t ? t.top : deckY + 1); y++) c.set(x, y, z, railPost ?? leg, railPost ? {} : { axis: 'y' })
  }
  const [hx, hz] = hatch
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (corner(x, z)) continue
    if (x === hx && z === hz) continue
    c.set(x, deckY, z, deck)
    const edge = x === x0 || x === x1 || z === z0 || z === z1
    if (edge) c.set(x, deckY + 1, z, rail)
  }
  for (let y = y0; y <= deckY; y++) c.set(hx, y, hz, ladder, { facing: ladderFacing, waterlogged: 'false' })
  const sides = {
    north: { cells: x => [x, z0], range: [x0, x1], axis: 'x', toward: ['west', 'east'] },
    south: { cells: x => [x, z1], range: [x0, x1], axis: 'x', toward: ['west', 'east'] },
    west: { cells: z => [x0, z], range: [z0, z1], axis: 'z', toward: ['north', 'south'] },
    east: { cells: z => [x1, z], range: [z0, z1], axis: 'z', toward: ['north', 'south'] },
  }
  for (const [name, s] of Object.entries(sides)) {
    if (name === openSide) continue
    const [r0, r1] = s.range
    for (let u = r0 + 1; u < r1; u++) {
      const [x, z] = s.cells(u)
      c.set(x, braceY, z, brace ?? leg, { axis: s.axis })
      if (u === r0 + 1) stair(c, x, deckY - 1, z, bracket, s.toward[0], 'top')
      if (u === r1 - 1) stair(c, x, deckY - 1, z, bracket, s.toward[1], 'top')
    }
  }
}

/** fireRing: a ring of stones round an open fire at (x, y, z) (the fire itself is the caller's): stairs on the four
 *  sides with their high backs to the fire, slabs on the corners, all at the fire's own level. */
export function fireRing(c, x, y, z, { stairs, slabs }) {
  for (const [dx, dz, f] of [[0, -1, 'south'], [0, 1, 'north'], [-1, 0, 'east'], [1, 0, 'west']]) stair(c, x + dx, y, z + dz, stairs(x + dx, y, z + dz), f)
  for (const [dx, dz] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) slab(c, x + dx, y, z + dz, slabs(x + dx, y, z + dz))
}

/** trophyStake: a stake at [x, y, z]: `log` `height` high, a `fence` point on it and `top` (a skull, a lantern) on
 *  the point, with `topProps`. */
export function trophyStake(c, [x, y, z], { height = 2, log, fence, top = null, topProps = {} }) {
  for (let k = 0; k < height; k++) c.set(x, y + k, z, log, { axis: 'y' })
  c.set(x, y + height, z, fence)
  if (top) c.set(x, y + height + 1, z, top, topProps)
}

/** hitchRail: a hitching rail along z at x from z0 to z1: a fence post at each end from y and a fence bar between
 *  them a block up (one straight run, so no corner wants a post). */
export function hitchRail(c, { x, z0, z1, y }, fence) {
  c.set(x, y, z0, fence); c.set(x, y, z1, fence)
  for (let z = z0; z <= z1; z++) c.set(x, y + 1, z, fence)
}
