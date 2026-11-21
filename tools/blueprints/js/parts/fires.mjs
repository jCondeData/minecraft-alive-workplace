// Fires: open-air fire places and the stones round them (parts library, tools/blueprints/REBUILD.md): a round raised
// dais with a ring of steps, a sunken fire pit with its curb, standing stones and a trilithon (two stones and a lintel),
// a spit over the fire, and a stacked woodpile. Every block comes from the family's palette; none is picked here
// (the campfire is the fire itself, as hearths.mjs's hearthFire).
import { stair, slab } from './canvas.mjs'

/** discCells: the [x, z] cells of a round disc about (cx, cz), every cell with dx² + dz² <= r2. */
export function discCells(cx, cz, r2) {
  const cells = []
  const r = Math.ceil(Math.sqrt(r2))
  for (let x = cx - r; x <= cx + r; x++) for (let z = cz - r; z <= cz + r; z++)
    if ((x - cx) ** 2 + (z - cz) ** 2 <= r2) cells.push([x, z])
  return cells
}

/** roundDais: a round floor of `floor` (a block or a mix()) one layer high at y, its edge (every cell with a side
 *  open to the outside) a ring of `step` stairs climbing inward, so it can be walked onto from any side. `skip` lists
 *  [x, z] cells left for the caller (a stone's foot, the fire). Returns the disc's cells. */
export function roundDais(c, cx, cz, r2, y, floor, step, skip = []) {
  const cells = discCells(cx, cz, r2)
  const inDisc = new Set(cells.map(([x, z]) => `${x},${z}`))
  const skipped = new Set(skip.map(([x, z]) => `${x},${z}`))
  for (const [x, z] of cells) {
    if (skipped.has(`${x},${z}`)) continue
    const open = [[0, -1, 'south'], [0, 1, 'north'], [-1, 0, 'east'], [1, 0, 'west']].filter(([dx, dz]) => !inDisc.has(`${x + dx},${z + dz}`))
    if (!open.length) { c.set(x, y, z, floor); continue }
    // the step climbs toward the center: face the axis the cell is furthest out along
    const dx = x - cx, dz = z - cz
    const facing = Math.abs(dz) >= Math.abs(dx) ? (dz < 0 ? 'south' : 'north') : (dx < 0 ? 'east' : 'west')
    stair(c, x, y, z, typeof step === 'function' ? step(x, y, z) : step, facing)
  }
  return cells
}

/** firePit: a lit campfire at (x, y, z), sunk in the floor's layer, on `bed` (the hearthstones under the ash ring),
 *  ringed at y + 1 by a curb: stairs on the four sides (their high back toward the fire, so the rim stands up round it
 *  and steps down outward) and slabs on the corners. `open` lists side directions ('north'...) left without a curb
 *  stair (a spit's upright stands there). */
export function firePit(c, x, y, z, { curbStair, curbSlab, fire = 'campfire', facing = 'south', open = [] }) {
  c.set(x, y, z, fire, { facing, lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  const sides = { north: [0, -1, 'south'], south: [0, 1, 'north'], west: [-1, 0, 'east'], east: [1, 0, 'west'] }
  for (const [dir, [dx, dz, f]] of Object.entries(sides)) if (!open.includes(dir)) stair(c, x + dx, y + 1, z + dz, curbStair, f)
  for (const [dx, dz] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) slab(c, x + dx, y + 1, z + dz, curbSlab)
}

/** standingStone: a rough stone from y0 up `height` blocks of `stone` (a block or a mix()), with an optional `cap`:
 *  {stair, facing} (a slanted top, the stone weathered to a point) or {slab} (a flat, rounded top). */
export function standingStone(c, x, z, y0, height, stone, cap = null) {
  for (let y = y0; y < y0 + height; y++) c.set(x, y, z, stone)
  const top = y0 + height
  if (cap?.stair) stair(c, x, top, z, cap.stair, cap.facing ?? 'north')
  else if (cap?.slab) slab(c, x, top, z, cap.slab)
}

/** trilithon: two standing stones (at [xa, z] and [xb, z], from y0 to topY - 1) and a lintel along x at topY, of
 *  `lintel.full` (a block or mix()) from xa - over to xb + over (a lantern can hang under those full proud ends), and
 *  with `tips` an upside-down `lintel.stairs` beyond each end, so the lintel's ends taper. */
export function trilithon(c, xa, xb, z, y0, topY, stone, lintel, over = 1, tips = true) {
  for (const x of [xa, xb]) for (let y = y0; y < topY; y++) c.set(x, y, z, stone)
  for (let x = xa - over; x <= xb + over; x++) c.set(x, topY, z, lintel.full)
  if (tips) {
    stair(c, xa - over - 1, topY, z, lintel.stairs, 'east', 'top')
    stair(c, xb + over + 1, topY, z, lintel.stairs, 'west', 'top')
  }
}

/** spit: a roasting spit across a fire along x: two fence uprights at xa and xb (y0 .. y0 + 1) and a fence bar
 *  between them at y0 + 1, the run straight (owner rule 1: no corner without its post). */
export function spit(c, xa, xb, z, y0, fence) {
  const f = (x, y, e, w) => c.set(x, y, z, fence, { east: String(e), west: String(w), north: 'false', south: 'false', waterlogged: 'false' })
  f(xa, y0, false, false); f(xb, y0, false, false)
  for (let x = xa; x <= xb; x++) f(x, y0 + 1, x < xb, x > xa)
}

/** logStack: a woodpile of `log` lying along `axis`, from [x, y, z] cells: the fuel for the fire. */
export function logStack(c, cells, log, axis = 'x') {
  for (const [x, y, z] of cells) c.set(x, y, z, log, { axis })
}
