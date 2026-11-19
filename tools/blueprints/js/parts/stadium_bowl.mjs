// Stadium bowls (the Stadium Wonder, 52.6): the "stadium" shape itself (a rounded rectangle: every cell's distance from
// a straight spine, so rings of equal width run round the straights and the round ends alike), a raked seating row on
// that ring, and a floodlight mast whose head of lamps is wired to one inverted daylight detector by a ring of dust.
// Every block comes from the caller's palette; nothing is picked here.

/** spineDistance: the distance of cell (x, z) from the spine, the segment from (cx - half, cz) to (cx + half, cz).
 *  ring(x, z) = Math.floor of it: ring r is the band of cells r..r+1 from the spine, the same width all the way round. */
export function spineDistance(x, z, { cx, cz, half }) {
  const dx = Math.max(0, Math.abs(x - cx) - half)
  return Math.hypot(dx, z - cz)
}
export const ringOf = (x, z, bowl) => Math.floor(spineDistance(x, z, bowl))

/** floodMast: a floodlight mast standing on [x, y0, z]: a shaft of `shaft` up to y = top - 1, a 3 x 3 head at `top`
 *  of eight `lamp`s round a `core`, an inverted `detector` on the core (it must see the sky) and a ring of `wire` on the
 *  eight lamps, so the detector lights every lamp of its head and nothing else. `collar`: a block for a 3 x 3 collar
 *  two under the head (a mast's capital), with `bracket` stairs (upside-down, facing out) under the head's edge. */
export function floodMast(c, [x, y0, z], top, { shaft, core, lamp, detector, wire, collar, bracket }) {
  for (let y = y0; y < top; y++) c.set(x, y, z, shaft)
  if (collar) for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) if (dx || dz) c.set(x + dx, top - 2, z + dz, collar)
  if (bracket) {
    for (const [dx, dz, f] of [[0, -1, 'north'], [0, 1, 'south'], [-1, 0, 'west'], [1, 0, 'east']])
      c.set(x + dx, top - 1, z + dz, bracket, { facing: f === 'north' ? 'south' : f === 'south' ? 'north' : f === 'west' ? 'east' : 'west', half: 'top', shape: 'straight', waterlogged: 'false' })
  }
  c.set(x, top, z, core)
  c.set(x, top + 1, z, detector, { inverted: 'true', power: '0' })
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) {
    if (!dx && !dz) continue
    c.set(x + dx, top, z + dz, lamp, { lit: 'false' })
    const side = (ox, oz) => (Math.abs(dx + ox) <= 1 && Math.abs(dz + oz) <= 1 ? 'side' : 'none')
    c.set(x + dx, top + 1, z + dz, wire, {
      north: side(0, -1), south: side(0, 1), west: side(-1, 0), east: side(1, 0), power: '0',
    })
  }
}

/** rakedRow: one ring of a raked stand. Every cell of `cells` (a list of [x, z]) gets a column of `fill` from y0 to
 *  top - 1 and a `tread` at top; `seat` (a carpet id, or a function of (x, z) giving one, or null) goes on the tread.
 *  `hollowBelow`: leave y0..hollowBelow empty (a concourse under the rake) and lay `soffit` at hollowBelow + 1. */
export function rakedRow(c, cells, { y0 = 1, top, hollowBelow = null }, { fill, tread, seat = null, soffit = null }) {
  for (const [x, z] of cells) {
    for (let y = y0; y < top; y++) {
      if (hollowBelow !== null && y <= hollowBelow) continue
      c.set(x, y, z, hollowBelow !== null && y === hollowBelow + 1 && soffit ? soffit : fill)
    }
    c.set(x, top, z, tread)
    const s = typeof seat === 'function' ? seat(x, z) : seat
    if (s) c.set(x, top + 1, z, s)
  }
}
