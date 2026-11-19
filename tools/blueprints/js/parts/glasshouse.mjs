// Glasshouses (parts library, tools/blueprints/REBUILD.md; grown from the Flower Shop, 49): a 45-degree glazed gable
// roof with glazing bars, the staging a nursery shows its pots on, and a raised growing bed. Every block comes from the
// caller's palette; only the plants and pots it is handed are placed.
import { slab } from './canvas.mjs'

/** glassGableRoof: a 45-degree roof over `across` (eaves at both ends) running `along` (gables at both ends), glazed
 *  with full `glass` blocks stepping up the slope, a `bar` stair on every rib line (`ribs`: the along coordinates of the
 *  glazing bars, the posts below them) and on both ends, a `gutter` stair along each eave and a `ridge` block along the
 *  top. Stepped full blocks meet corner to corner, so the glazing is tight from inside. `keep(x,y,z)` false leaves a
 *  cell to another mass. Returns {ridgeY, under} like gableRoof (each column's roof height, for gables). */
export function glassGableRoof(c, { axis, across: [a0, a1], along: [l0, l1], eaveY }, { glass, bar, gutter, ridge }, ribs = [], keep = () => true) {
  const half = (a1 - a0) / 2
  const mid = a0 + half
  const put = (u, y, v, id, props) => {
    const [x, z] = axis === 'z' ? [u, v] : [v, u]
    if (keep(x, y, z)) c.set(x, y, z, id, props)
  }
  const ribbed = v => v === l0 || v === l1 || ribs.includes(v)
  for (let v = l0; v <= l1; v++) {
    for (let i = 0; i < half; i++) {
      for (const [u, rise] of [[a0 + i, axis === 'z' ? 'east' : 'south'], [a1 - i, axis === 'z' ? 'west' : 'north']]) {
        const st = { facing: rise, half: 'bottom', shape: 'straight', waterlogged: 'false' }
        if (i === 0) put(u, eaveY, v, gutter, st)
        else if (ribbed(v)) put(u, eaveY + i, v, bar, st)
        else put(u, eaveY + i, v, glass)
      }
    }
    put(mid, eaveY + half, v, ridge)
  }
  const under = {}
  for (let i = 0; i <= half; i++) { under[a0 + i] = eaveY + i; under[a1 - i] = eaveY + i }
  return { ridgeY: eaveY + half, under }
}

/** staging: a nursery's display shelf, top slabs (`wood`_slab) in a row of cells [[x, y, z]] with a pot on each from
 *  `pots` (potted plants or empty flower pots; null leaves the shelf bare). A fixture: no furniture spot. */
export function staging(c, cells, wood, pots = []) {
  cells.forEach(([x, y, z], i) => {
    c.free([x, y, z], [x, y + 1, z])
    slab(c, x, y, z, `${wood}_slab`, 'top')
    if (pots[i]) c.set(x, y + 1, z, pots[i])
  })
}

/** growingBed: a bed raised one block on `base` over cells [[x, z]] (base at y, `soil` at y + 1), and `plants`
 *  ([[x, z, id]]) standing in it (tall flowers get their upper half). Grass beds keep air above the soil where nothing
 *  is planted, so a gardener can bring flowers up there. Returns the soil cells [x, y + 1, z]. */
export function growingBed(c, cells, y, { base, soil, plants = [] }) {
  const TALL = new Set(['lilac', 'peony', 'rose_bush', 'sunflower', 'tall_grass', 'large_fern', 'pitcher_plant'])
  const out = []
  for (const [x, z] of cells) {
    c.set(x, y, z, base)
    c.set(x, y + 1, z, soil, soil.endsWith('grass_block') ? { snowy: 'false' } : {})
    out.push([x, y + 1, z])
  }
  for (const [x, z, id] of plants) {
    if (TALL.has(id)) { c.set(x, y + 2, z, id, { half: 'lower' }); c.set(x, y + 3, z, id, { half: 'upper' }) }
    else c.set(x, y + 2, z, id)
  }
  return out
}

