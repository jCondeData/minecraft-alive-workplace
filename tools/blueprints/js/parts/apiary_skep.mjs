// Apiaries (parts library, tools/blueprints/REBUILD.md; grown from the Apiary Garden, "the Skep Wall", 49): a straw
// skep dome built course by course from round discs, a bee bole (a hive in a niche of a thick wall, smoked by a
// campfire under it), a hive smoked where it stands, a one-cell stone bee font, and hiveCheck, which proves a drawing
// keeps the Beekeeper's rules (bee/BeekeeperWork: a hive's face open to air, flowers round it, a chest in reach).
// Every block comes from the caller's palette; none is picked here (the beehive and the campfire are the job's own
// blocks, passed in too).
import { OPP, DIRS, stair } from './canvas.mjs'
import { discCells } from './fires.mjs'
import { pool } from './water.mjs'

/** fairMix: a weighted, repeatable mix like canvas.mjs's mix(), with a 32-bit integer hash (Math.imul), so every
 *  weight shows up in its share (mix()'s float multiply loses the low bits and gives nearly all of the first block,
 *  most of all at y 0). `weighted`: [[id, weight], ...]. */
export function fairMix(weighted, seed = 1) {
  const total = weighted.reduce((a, [, w]) => a + w, 0)
  return (x, y, z) => {
    let h = Math.imul(x, 73856093) ^ Math.imul(y, 19349663) ^ Math.imul(z, 83492791) ^ Math.imul(seed, 0x9e3779b1)
    h = Math.imul(h ^ (h >>> 16), 0x85ebca6b); h = Math.imul(h ^ (h >>> 13), 0xc2b2ae35); h = (h ^ (h >>> 16)) >>> 0
    h %= total
    for (const [id, w] of weighted) { if (h < w) return id; h -= w }
    return weighted[0][0]
  }
}

/** skepDome: a domed skep about (cx, cz), one course per entry of `courses` from y0 up. Each course is
 *  { r2, hollow }: a disc of every cell with dx² + dz² <= r2 (5: a 5-wide round, 4: a 3-square with arms, 2: a
 *  3-square, 1: a plus, 0: one cell); a hollow course leaves its inside empty (the cells whose four neighbours are all
 *  in the disc), so the lower courses make a room and the first solid one its ceiling. `straw` is an id or a mix().
 *  `stand` (an id or mix()) and `standR2` lay the stone it sits on at y0 - 1. Returns the inside cells [x, y, z]. */
export function skepDome(c, cx, cz, y0, courses, straw, { stand = null, standR2 = 8 } = {}) {
  if (stand) for (const [x, z] of discCells(cx, cz, standR2)) c.set(x, y0 - 1, z, stand)
  const inside = []
  courses.forEach(({ r2, hollow }, i) => {
    const y = y0 + i
    const disc = discCells(cx, cz, r2)
    const has = new Set(disc.map(([x, z]) => `${x},${z}`))
    for (const [x, z] of disc) {
      const inner = [[1, 0], [-1, 0], [0, 1], [0, -1]].every(([dx, dz]) => has.has(`${x + dx},${z + dz}`))
      if (hollow && inner) { c.clear(x, y, z); inside.push([x, y, z]); continue }
      c.set(x, y, z, straw, { axis: 'y' })
    }
  })
  return inside
}

/** smokedHive: a beehive facing `facing` (its bees' way out, which must open onto air) with a lit campfire right
 *  under it, so the game's smoke rule keeps the bees in when it is harvested. ids: { hive, fire } (fire null: none). */
export function smokedHive(c, [x, y, z], facing, { hive, fire }) {
  c.set(x, y, z, hive, { facing, honey_level: '0' })
  if (fire) c.set(x, y - 1, z, fire, { facing, lit: 'true', signal_fire: 'false', waterlogged: 'false' })
}

/** beeBole: a hive set back in a thick wall: the hive at (x, y, z) in the wall's core facing `out`, the niche's mouth
 *  (the wall's face cell in front of it) opened at y and, with a fire, at y - 1 too (the campfire glows in the niche's
 *  foot), and its head an upside-down stair of `head` leaning back into the wall (a half-block hood over the mouth).
 *  ids: { hive, fire, head }. */
export function beeBole(c, [x, y, z], out, { hive, fire, head }) {
  const [dx, dz] = DIRS[out]
  const mx = x + dx, mz = z + dz
  smokedHive(c, [x, y, z], out, { hive, fire })
  c.clear(mx, y, mz)
  if (fire) c.clear(mx, y - 1, mz)
  stair(c, mx, y + 1, mz, head, OPP[out], 'top')
}

/** beeFont: a one-cell water source at (cx, y, cz) for the bees to drink, held by four stairs (`stairs`, their tall
 *  backs to the water, their steps outward) and four corner blocks (`corner`), with a lily pad on it when `pad`. The
 *  cells under it (y - 1) must be drawn. */
export function beeFont(c, cx, y, cz, { stairs, corner, pad = null }) {
  for (const [dx, dz] of [[1, 1], [1, -1], [-1, 1], [-1, -1]]) c.set(cx + dx, y, cz + dz, corner)
  stair(c, cx, y, cz - 1, stairs, 'south')
  stair(c, cx, y, cz + 1, stairs, 'north')
  stair(c, cx - 1, y, cz, stairs, 'east')
  stair(c, cx + 1, y, cz, stairs, 'west')
  pool(c, [[cx, cz]], y)
  if (pad) c.set(cx, y + 1, cz, pad)
}

/** hiveCheck: throws unless every hive keeps the Beekeeper's rules (tools/blueprints/briefs/apiary_garden.md):
 *  its face (facing side) on air; at least `minFlowers` blocks in `flowers` (a Set of ids) within 4 across, 3 below
 *  and 2 above it; a chest within 8 across and 4 up or down; a bare soil cell (`soil` ids) with air over it within 3
 *  across, its plant's cell 3 below to 1 above the hive, so she can plant more. Returns one line per hive. */
export function hiveCheck(c, { hive, chest, flowers, soil, minFlowers = 4 }) {
  const all = [...c.cells].map(([k, v]) => [k.split(',').map(Number), v])
  const hives = all.filter(([, v]) => v.id === `minecraft:${hive}`)
  const chests = all.filter(([, v]) => v.id === `minecraft:${chest}`).map(([p]) => p)
  const lines = [], bad = []
  for (const [[x, y, z], v] of hives) {
    const [dx, dz] = DIRS[v.props.facing]
    const face = c.get(x + dx, y, z + dz)
    let n = 0
    for (const [[fx, fy, fz], f] of all)
      if (flowers.has(f.id.replace('minecraft:', '')) && Math.abs(fx - x) <= 4 && Math.abs(fz - z) <= 4 && fy - y >= -3 && fy - y <= 2) n++
    const near = chests.filter(([cx, cy, cz]) => Math.abs(cx - x) <= 8 && Math.abs(cz - z) <= 8 && Math.abs(cy - y) <= 4).length
    let plant = 0
    for (let sx = x - 3; sx <= x + 3; sx++) for (let sz = z - 3; sz <= z + 3; sz++) for (let sy = y - 4; sy <= y; sy++) {
      const s = c.get(sx, sy, sz)
      if (s && soil.has(s.id.replace('minecraft:', '')) && !c.get(sx, sy + 1, sz)) plant++
    }
    lines.push(`hive ${x},${y},${z} ${v.props.facing}: ${n} flowers, ${near} chests in reach, ${plant} bare soil`)
    if (face) bad.push(`hive ${x},${y},${z} faces ${face.id}`)
    if (n < minFlowers) bad.push(`hive ${x},${y},${z} has ${n} flowers near`)
    if (!near) bad.push(`hive ${x},${y},${z} has no chest in reach`)
    if (!plant) bad.push(`hive ${x},${y},${z} has no bare soil to plant`)
  }
  if (bad.length) throw new Error('hiveCheck: ' + bad.join('; '))
  return lines
}
