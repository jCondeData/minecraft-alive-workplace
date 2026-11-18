// Saw-tooth workshop parts (grown from the Crafter's Workshop, ROADMAP 50.7): a north-light ("saw-tooth") roof of
// teeth that each rise in a 45-degree slope and drop in a glazed face, the clerestory glazing under each face, upside-
// down stair trim under the teeth's overhanging rakes, a twin-flue stack, proud posts along a wall, and a wall shelf
// that holds a candle or a pot (not crockery). Every block comes from the family's palette; none is picked here.
import { stair, slab } from './canvas.mjs'

/** sawHeights: the roof's y over each column of a saw-tooth roof whose teeth run across `axis`: `teeth` teeth from
 *  column `from`, each `rise + 1` columns wide, rising one a column from eaveY to eaveY + rise and then dropping
 *  straight back to eaveY at the next tooth. Returns {u: y} and the glazed face columns (each tooth's top). */
export function sawHeights(from, teeth, rise, eaveY) {
  const h = {}, faces = []
  for (let t = 0; t < teeth; t++) for (let i = 0; i <= rise; i++) {
    const u = from + t * (rise + 1) + i
    h[u] = eaveY + i
    if (i === rise) faces.push(u)
  }
  return { h, faces }
}

/** sawtoothRoof: the teeth (sawHeights) laid in `roof.stairs` along `along` [l0, l1]: every column a stair rising
 *  toward the tooth's top (`rise`: 'east' for teeth whose slope climbs with x, axis 'x'), the lowest stair of each
 *  tooth after the first in `gutter` (a stair id: the valley's lead or copper) when given. `keep(x, y, z)` returns false
 *  for cells another mass owns (a chimney). Columns are x (axis 'x', the teeth seen in the front elevation) or z. */
export function sawtoothRoof(c, { axis = 'x', h, along: [l0, l1], rise }, roof, { gutter = null, eaveY = null, keep = () => true } = {}) {
  const us = Object.keys(h).map(Number).sort((a, b) => a - b)
  for (const u of us) for (let v = l0; v <= l1; v++) {
    const [x, z] = axis === 'x' ? [u, v] : [v, u]
    if (!keep(x, h[u], z)) continue
    const id = gutter && h[u] === eaveY && u !== us[0] ? gutter : roof.stairs
    stair(c, x, h[u], z, id, rise)
  }
}

/** clerestory: the glazed face under a saw tooth's top: `glass` in column `u` from y0 to y1 over v0..v1, a `frame`
 *  stud at each end (v0 - 1 and v1 + 1, the gable lines) and a `beam` log under the glass at y0 - 1 running the whole
 *  length (v0 - 1 .. v1 + 1), so the glazing stands on timber. axis 'x': u is x, the run is along z. */
export function clerestory(c, { axis = 'x', u, v0, v1, y0, y1 }, { glass, frame, beam = frame }) {
  const at = (v, y) => (axis === 'x' ? [u, y, v] : [v, y, u])
  for (let v = v0 - 1; v <= v1 + 1; v++) { const [x, y, z] = at(v, y0 - 1); c.set(x, y, z, beam, { axis: axis === 'x' ? 'z' : 'x' }) }
  for (let y = y0; y <= y1; y++) {
    for (let v = v0; v <= v1; v++) { const [x, yy, z] = at(v, y); c.set(x, yy, z, glass) }
    for (const v of [v0 - 1, v1 + 1]) { const [x, yy, z] = at(v, y); c.set(x, yy, z, frame, { axis: 'y' }) }
  }
}

/** sawTrim: upside-down stairs under the teeth's overhanging ends (the rake line `line`, z for axis 'x'), one under
 *  each column's roof stair where nothing is drawn yet, facing up the slope (`rise`), so the zig-zag edge reads apart
 *  from the gable under it. `skip(u)` leaves a column out. */
export function sawTrim(c, { axis = 'x', h, line, rise }, id, skip = () => false) {
  for (const u of Object.keys(h).map(Number)) {
    if (skip(u)) continue
    const [x, z] = axis === 'x' ? [u, line] : [line, u]
    const y = h[u] - 1
    if (!c.inside(x, y, z) || c.get(x, y, z)) continue
    stair(c, x, y, z, id, rise, 'top')
  }
}

/** twinFlue: a stack of two flues side by side outside a wall (x, z0..z1 along z), solid `stone` from y0 to y1, a
 *  band of `band` at `bandY`, each flue crowned by a lit campfire (the smoke of a forge and a stove). */
export function twinFlue(c, x, [z0, z1], y0, y1, stone, { band = null, bandY = null } = {}) {
  for (let z = z0; z <= z1; z++) {
    for (let y = y0; y <= y1; y++) c.set(x, y, z, band && y === bandY ? band : stone)
    c.set(x, y1 + 1, z, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  }
}

/** proudPosts: frame logs standing a block out from a wall face from y0 to y1, at each [x, z]. */
export function proudPosts(c, cells, y0, y1, log) {
  for (const [x, z] of cells) for (let y = y0; y <= y1; y++) c.set(x, y, z, log, { axis: 'y' })
}

/** wallShelf: a shelf on a wall (an upside-down `${wood}_slab`) with `f.on` standing on it (a pot, candles: [id,
 *  props]), recorded as a shelf; what stands on it is a fixture, not crockery (a workshop's shelf, owner rule 3). */
export function wallShelf(c, f) {
  const [x, y, z] = f.pos
  c.free([x, y, z], [x, y + 1, z])
  slab(c, x, y, z, `${f.wood}_slab`, 'top')
  c.spot('shelf', f.pos, f.facing, [[0, 0, 0]], f.wood)
  if (f.on) { const [id, props] = Array.isArray(f.on) ? f.on : [f.on, {}]; c.set(x, y + 1, z, id, props) }
}
