// Shore pieces (parts library, tools/blueprints/REBUILD.md; grown from the Castaway Camp, 49): a boat turned keel-up
// as a roof, a mast with its yard, and cloth hung ragged from a beam (a torn sail, an awning's fringe, a drying
// line's washing). Every block comes from the family's palette; none is picked here.
import { stair } from './canvas.mjs'

/** upturnedHull: a double-ended ship's boat turned keel-up on its gunwales, its keel along `axis` ('z' or 'x').
 *  across: [a0, a1] (an odd width, 5 or 7: the gunwales on the two edges, the keel in the middle); ends: [v0, v1], the
 *  two stems along the keel; g: the gunwales' height amidships; foot: the y the stems stand on (default 1).
 *  Amidships, in section: the gunwale logs at g, `sides` courses of topsides (planks) straight up from them, the
 *  bilge turning in (a stair each side, its high back inward) to a flat bottom of planks, and the keel log standing
 *  proud on top along the middle at g + sides + 2. Toward each
 *  end it narrows a block a side per cell and its gunwale and keel dip a block (keel-up, a boat's sheer, which rises at
 *  bow and stern, sweeps down), the ends rounded off in stairs that slope down toward the stem; each stem runs from the
 *  keel's dropped end down to `foot`, so the boat rests on its two stems with the gunwales propped amidships.
 *  hull: { gunwale (a log), planks (a block or mix()), stairs, keel (a log), stemHead (stairs: the keel's rounded end
 *  over each stem), and optionally thwart (a log): then the boat's inside is closed at g, seen from under it as its
 *  bottom boards (planks) crossed by thwarts (the rowers' seats, logs across) every other cell, so the shelter under it
 *  is a low, two-high space and not a deep cave }. Returns { keelY, inside: [x, z] cells under the full sections }. */
export function upturnedHull(c, { axis = 'z', across: [a0, a1], ends: [v0, v1], g, foot = 1, sides = 1 }, hull) {
  const at = (u, y, v) => (axis === 'z' ? [u, y, v] : [v, y, u])
  const along = axis === 'z' ? 'z' : 'x'
  const half = (a1 - a0) / 2, mid = a0 + half
  const keelY = g + sides + 2
  const [inward0, inward1] = axis === 'z' ? ['east', 'west'] : ['south', 'north']
  const [toV1, toV0] = axis === 'z' ? ['south', 'north'] : ['east', 'west'] // facing toward the larger / smaller v
  const mine = new Set()
  const put = (u, y, v, id, props) => { c.set(...at(u, y, v), id, props); mine.add(`${u},${y},${v}`) }
  const putStair = (u, y, v, facing) => { stair(c, ...at(u, y, v), hull.stairs, facing); mine.add(`${u},${y},${v}`) }
  const inside = []
  const section = (v, j) => { // j: how many blocks the section is narrowed by on each side (0 = full beam)
    const l = a0 + j, r = a1 - j, base = g - j, bilge = base + sides + 1
    put(l, base, v, hull.gunwale, { axis: along })
    put(r, base, v, hull.gunwale, { axis: along })
    if (j > 0) for (let u = l + 1; u < r; u++) put(u, base, v, hull.planks) // the end's skin is closed underneath
    for (let y = base + 1; y < bilge; y++) for (let u = l; u <= r; u++) put(u, y, v, hull.planks) // the topsides (and the hold, filled)
    putStair(l, bilge, v, inward0); putStair(r, bilge, v, inward1) // the bilge turns in
    for (let u = l + 1; u < r; u++) put(u, bilge, v, hull.planks) // the bottom, flat
    put(mid, bilge + 1, v, hull.keel, { axis: along }) // the keel, proud on top
  }
  const lo = Math.min(v0, v1), hi = Math.max(v0, v1)
  for (let v = lo + half; v <= hi - half; v++) {
    section(v, 0)
    for (let u = a0 + 1; u < a1; u++) inside.push(axis === 'z' ? [u, v] : [v, u])
  }
  for (let j = 1; j < half; j++) { section(lo + half - j, j); section(hi - half + j, j) }
  // the ends rounded: an empty cell over the hull's skin, against the next section in, gets a stair sloping out
  for (let j = 1; j < half; j++) for (const [v, inV, f] of [[lo + half - j, lo + half - j + 1, toV1], [hi - half + j, hi - half + j - 1, toV0]])
    for (let u = a0; u <= a1; u++) for (let y = g - half; y <= keelY; y++) {
      if (mine.has(`${u},${y},${v}`) || !mine.has(`${u},${y},${inV}`) || !mine.has(`${u},${y - 1},${v}`)) continue
      putStair(u, y, v, f)
    }
  // the stems, from the keel's dropped end down to the sand, rounded over in a stair
  const stemTop = keelY - half
  for (const [v, f] of [[lo, toV1], [hi, toV0]]) {
    for (let y = foot; y < stemTop; y++) put(mid, y, v, hull.keel, { axis: 'y' })
    stair(c, ...at(mid, stemTop, v), hull.stemHead, f)
  }
  if (hull.thwart) for (let v = lo + half; v <= hi - half; v++) {
    const seat = (v - lo - half) % 2 === 1
    for (let u = a0 + 1; u < a1; u++) c.set(...at(u, g, v), seat ? hull.thwart : hull.planks, seat ? { axis: axis === 'z' ? 'x' : 'z' } : {})
  }
  return { keelY, inside }
}

/** mast: a mast stepped at [x, y, z], `height` blocks of `log` (upright), its yard across at yardY along `yardAxis`
 *  from `from` to `to` (the other coordinate is the mast's), the yard's ends logs so a lantern can hang under them.
 *  Returns the top cell (the one above the mast's last log). */
export function mast(c, [x, y, z], { height, yardY, yardAxis = 'x', from, to }, { log, yard }) {
  for (let i = 0; i < height; i++) c.set(x, y + i, z, log, { axis: 'y' })
  for (let i = from; i <= to; i++) {
    const [px, pz] = yardAxis === 'x' ? [i, z] : [x, i]
    if (px === x && pz === z) continue
    c.set(px, yardY, pz, yard, { axis: yardAxis })
  }
  return [x, y + height, z]
}

/** raggedCloth: cloth hung under a beam along `axis` at line `at` (the other coordinate), from y down: drops[i] blocks
 *  under the beam's cell a + i (0 leaves a gap: the cloth is torn there). cloth: a block id or a mix() (weathered
 *  patches). Each column hangs from the block above it. */
export function raggedCloth(c, axis, at, a, y, drops, cloth) {
  drops.forEach((n, i) => {
    for (let k = 1; k <= n; k++) {
      const [x, z] = axis === 'x' ? [a + i, at] : [at, a + i]
      c.set(x, y - k, z, cloth)
    }
  })
}
