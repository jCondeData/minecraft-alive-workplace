// Wards and pavilion roofs (parts library, tools/blueprints/REBUILD.md): grown from the Clinic (49, the pavilion
// ward). A 45-degree gable whose ridge is closed under a slab cap and may run on past one eave as a catslide, beds in a
// row heads to a wall (checked against the wall as it is drawn), and a tie beam across a room with a lamp hung from it.
// Every block comes from the family's palette.
import { DIRS, stair } from './canvas.mjs'
import { furnitureParts } from './furniture.mjs'

/** cappedGableRoof: a 45-degree gable roof over an odd span `across` [a0, a1] running `along` [l0, l1]: a stair per
 *  column rising inward from eaveY at both eaves, the middle column a full block level with the top stairs (the ridge
 *  cell, never left open) under a `roof.slab` cap. `extend` {a0, a1}: columns the slope runs on past that eave, one
 *  lower each (a catslide over a veranda or an outshot). `keep(x, y, z)` as in gableRoof. Returns {ridgeY (the ridge
 *  cell's y), capY, under} (under[u]: the roof's y over column u, extensions included). */
export function cappedGableRoof(c, { axis, across: [a0, a1], along: [l0, l1], eaveY, extend = {} }, roof, keep = () => true) {
  const half = (a1 - a0) / 2
  if (!Number.isInteger(half)) throw new Error(`cappedGableRoof: span ${a0}..${a1} is even`)
  const mid = a0 + half
  const put = (u, y, v, id, props) => {
    const [x, z] = axis === 'z' ? [u, v] : [v, u]
    if (c.inside(x, y, z) && keep(x, y, z)) c.set(x, y, z, id, props)
  }
  const st = facing => ({ facing, half: 'bottom', shape: 'straight', waterlogged: 'false' })
  const riseA0 = axis === 'z' ? 'east' : 'south', riseA1 = axis === 'z' ? 'west' : 'north'
  const under = {}
  const ridgeY = eaveY + half - 1
  for (let i = 0; i < half; i++) { under[a0 + i] = eaveY + i; under[a1 - i] = eaveY + i }
  for (let k = 1; k <= (extend.a0 ?? 0); k++) under[a0 - k] = eaveY - k
  for (let k = 1; k <= (extend.a1 ?? 0); k++) under[a1 + k] = eaveY - k
  under[mid] = ridgeY
  for (let v = l0; v <= l1; v++) {
    for (const [u, y] of Object.entries(under)) {
      const uu = Number(u)
      if (uu === mid) continue
      put(uu, y, v, roof.stairs, st(uu < mid ? riseA0 : riseA1))
    }
    put(mid, ridgeY, v, roof.full)
    put(mid, ridgeY + 1, v, roof.slab, { type: 'bottom', waterlogged: 'false' })
  }
  return { ridgeY, capY: ridgeY + 1, under }
}

/** wardBeds: a row of beds heads to a wall (`heads`: each bed's head cell [x, y, z]; `facing`: the way the heads point,
 *  toward the wall), each recorded as a bed spot, with a nightstand (a slab and a candle) at each of `stands`. Refuses a
 *  head that does not lie against a full block already drawn (a bed's head needs a sturdy face: glass or bars will not
 *  do once built), so draw the wall first. */
export function wardBeds(c, heads, facing, { colour = 'white', wood = 'spruce', stands = [], solid = id => !/(pane|bars|glass|door|trapdoor|stairs|slab|fence)/.test(id) } = {}) {
  const [dx, dz] = DIRS[facing]
  for (const [x, y, z] of heads) {
    const wall = c.get(x + dx, y, z + dz)
    if (!wall || !solid(wall.id)) throw new Error(`wardBeds: the bed head at ${x},${y},${z} has ${wall?.id ?? 'air'} behind it, not a wall`)
    furnitureParts.bed(c, { pos: [x - dx, y, z - dz], facing, colour })
  }
  for (const pos of stands) furnitureParts.nightstand(c, { pos, facing, wood })
}

/** lampBeam: a tie beam across a room (axis 'x': from a0 to a1 at y on line z; 'z': along z on line x) resting on the
 *  walls at its ends, with a lantern hung on `drop` - 1 chains under it at `at` (a lamp spot). */
export function lampBeam(c, axis, line, [a0, a1], y, log, { at = null, drop = 2, wood = 'spruce' } = {}) {
  for (let u = a0; u <= a1; u++) {
    const [x, z] = axis === 'x' ? [u, line] : [line, u]
    c.set(x, y, z, log, { axis })
  }
  if (at === null) return
  const [x, z] = axis === 'x' ? [at, line] : [line, at]
  furnitureParts.hangingLamp(c, { pos: [x, y - drop, z], facing: 'north', chain: drop - 1, wood })
}

/** benchRecess: a stair seat set into a wall's recess between two proud posts (on whatever stands under it: a plinth),
 *  the sitter facing `facing` away from the wall. A fixture: no furniture spot (an outdoor seat). */
export function benchRecess(c, [x, y, z], facing, seat) {
  const back = { north: 'south', south: 'north', east: 'west', west: 'east' }[facing]
  stair(c, x, y, z, seat, back)
}
