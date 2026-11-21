// Desert ranch yard parts (the Ranch's desert look, ROADMAP 49.10e): an adobe-pier corral and a stone stock trough.
// Every block comes from the caller's palette (parts library, tools/blueprints/REBUILD.md). Not in index.mjs (other
// designers edit it in parallel): import this file directly.
import { slab } from './canvas.mjs'

/** pierRails: a corral round the ring x0..x1, z0..z1 at y: a pier (`pier`, may be a mix()) two high with a `cap` slab
 *  on it at each [x, z] of `piers` (the corners should be among them: a rail never turns a corner without its post),
 *  a `rail` (a fence) on every other ring cell, `gates` [[x, y, z]] (fence gates facing `gateFacing`) on the run, and
 *  the one cell `gap` [x, y, z] left open (a man-gap: a villager walks through it, a horse is too wide). `courses`: the
 *  rails' height (two: a rail as tall as the piers). */
export function pierRails(c, { x0, z0, x1, z1, y = 0, piers, gap = null, gates = [], gateFacing = 'east' }, { pier, cap, rail, gate, courses = 1 }) {
  const isPier = (x, z) => piers.some(([a, b]) => a === x && b === z)
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (x !== x0 && x !== x1 && z !== z0 && z !== z1) continue
    if (isPier(x, z)) {
      c.set(x, y, z, pier); c.set(x, y + 1, z, pier); slab(c, x, y + 2, z, cap, 'bottom')
    } else for (let k = 0; k < courses; k++) c.set(x, y + k, z, rail)
  }
  if (gap) for (let k = 0; k < courses; k++) c.clear(gap[0], gap[1] + k, gap[2])
  for (const [x, yy, z] of gates) {
    c.set(x, yy, z, gate, { facing: gateFacing, in_wall: 'false', open: 'false', powered: 'false' })
    for (let k = 1; k < courses; k++) c.clear(x, yy + k, z) // the gateway open over the gate
  }
}

/** stoneTrough: water sources in `water` [[x, z]] at y, held by `id` (a dressed stone) on the cells of `rim` [[x, z]],
 *  or, without a rim list, on every side of the water not already drawn. */
export function stoneTrough(c, water, rim, y, id) {
  const wet = new Set(water.map(([x, z]) => `${x},${z}`))
  for (const [x, z] of water) c.set(x, y, z, 'water', { level: '0' })
  const cells = rim ?? water.flatMap(([x, z]) => [[x + 1, z], [x - 1, z], [x, z + 1], [x, z - 1]])
  for (const [x, z] of cells) if (!wet.has(`${x},${z}`) && !c.get(x, y, z)) c.set(x, y, z, id)
}

/** cornice: a proud course of upside-down `id` stairs one block out round the walls x0..x1, z0..z1 at y (under a flat
 *  roof's parapet), the corners included (the game turns them), each stair's back to the wall; cells already drawn and
 *  those `skip(x, z)` names (a stack, a lower roof's parapet) are left. */
export function cornice(c, { x0, z0, x1, z1, y }, id, skip = () => false) {
  const put = (x, z, facing) => {
    if (!c.inside(x, y, z) || c.get(x, y, z) || skip(x, z)) return
    c.set(x, y, z, id, { facing, half: 'top', shape: 'straight', waterlogged: 'false' })
  }
  for (let x = x0 - 1; x <= x1 + 1; x++) { put(x, z0 - 1, 'south'); put(x, z1 + 1, 'north') }
  for (let z = z0; z <= z1; z++) { put(x0 - 1, z, 'east'); put(x1 + 1, z, 'west') }
}

/** slottedCatcher: a big windcatcher (a badgir) x0..x1, z0..z1 from y0 to capY: solid `body` (may be a mix()) up to
 *  slotY0, then courses slotY0..slotY1 whose faces alternate `pier` and open slots (every odd cell along a face, so a
 *  5-wide face has two slots between three piers), a solid core behind them; at capY a `cap` ring round a `capTop`
 *  slab (`grille`, a dark fence, fills the slots so they read dark from afar) and, when given, a projecting `cornice` of upside-down stairs a block out round it. `skip(x, y, z)` leaves a
 *  cell open (a throat down into the room below). */
export function slottedCatcher(c, { x0, z0, x1, z1, y0, slotY0, slotY1, capY }, { body, pier, cap, capTop, cornice: cor = null, grille = null }, skip = () => false) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const edgeX = x === x0 || x === x1, edgeZ = z === z0 || z === z1
    const ring = edgeX || edgeZ
    const along = edgeZ && !edgeX ? x - x0 : edgeX && !edgeZ ? z - z0 : 0
    for (let y = y0; y < capY; y++) {
      if (skip(x, y, z)) { c.clear(x, y, z); continue }
      if (ring && y >= slotY0 && y <= slotY1) { if (along % 2 === 0) c.set(x, y, z, pier); else if (grille) c.set(x, y, z, grille); else c.clear(x, y, z); continue }
      c.set(x, y, z, body)
    }
    if (ring) c.set(x, capY, z, cap)
    else slab(c, x, capY, z, capTop, 'bottom')
  }
  if (cor) cornice(c, { x0, z0, x1, z1, y: capY }, cor)
}
