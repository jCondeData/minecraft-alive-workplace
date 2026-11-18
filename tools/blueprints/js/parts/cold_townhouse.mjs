// Cold townhouses: the pieces the snowy and taiga looks of the Burgher's Townhouse are drawn with (ROADMAP 49.10b).
// Every block comes from the palette passed in; nothing here is hard-coded but the lantern.
//   deepWindow  panes set back in a thick wall: the reveal open in front of them, a lintel over it, snow on its sill
//   oriel       a little glazed bay on corner posts over a porch's cheeks, under its own cap (snow on the cap's middle)
//   sideDormer  a gabled dormer in a long slope: two cheeks and an apex round two panes, a clean stair rake, no steps
//   wideStack   a broad chimney stack over several flues: a solid slab of stone, smoke from each flue, a cap between
import { stair, DIRS } from './canvas.mjs'

/** deepWindow: glass in `cells` (the inner face of a wall two thick), the cell before each (toward `out`) cleared as the
 *  reveal, a `lintel` log over the reveal along the wall, and, with `snow`, a drift of `layers` on the reveal's sill
 *  (the bottom row's reveal cells: the wall's course under them carries it). */
export function deepWindow(c, cells, out, { glass, lintel, snow = null, layers = '2' }) {
  const [ox, oz] = DIRS[out]
  const low = Math.min(...cells.map(p => p[1])), high = Math.max(...cells.map(p => p[1]))
  const axis = ox === 0 ? 'x' : 'z'
  for (const [x, y, z] of cells) {
    c.set(x, y, z, glass)
    c.clear(x + ox, y, z + oz)
    if (y === high) c.set(x + ox, y + 1, z + oz, lintel, { axis })
    if (y === low && snow) c.set(x + ox, y, z + oz, snow, { layers })
  }
}

/** oriel: a glazed bay x0..x1 on the row z in front of a wall, its `base` course at y0 (standing on what is under it:
 *  a porch's cheeks and lintel), corner `post`s from y0 + 1 to y1, `glass` between them, and a cap at y1 + 1: a
 *  `roof.stairs` at each end rising inward, `roof.full` between, `snow` on the full cells (none when null). */
export function oriel(c, { x0, x1, z, y0, y1 }, { base, post, glass, roof, snow = null }) {
  for (let x = x0; x <= x1; x++) {
    c.set(x, y0, z, base)
    for (let y = y0 + 1; y <= y1; y++) c.set(x, y, z, x === x0 || x === x1 ? post : glass, x === x0 || x === x1 ? { axis: 'y' } : {})
    if (x === x0) stair(c, x, y1 + 1, z, roof.stairs, 'east')
    else if (x === x1) stair(c, x, y1 + 1, z, roof.stairs, 'west')
    else { c.set(x, y1 + 1, z, roof.full); if (snow) c.set(x, y1 + 2, z, snow, { layers: '2' }) }
  }
}

/** sideDormer: a gabled dormer in a long slope whose ridge runs along z, centred on z: its face in the wall plane
 *  x = `face` (cheeks at z +- 1 and the apex over the window in `infill`, two panes of `glass` at y0..y0 + 1), under a
 *  clean little 45-degree gable roof of `roof.stairs` (eaves at z +- 2 on y0 + 1, z +- 1 on y0 + 2, the `roof.full`
 *  ridge on y0 + 3) running from one block outside the face (`run` 1: the slope rises east, the overhang at face - 1)
 *  `depth` columns back into the main slope (`infill` an id or [id, props]); the cell behind the upper pane is opened into the attic. No stepped
 *  blocks: the face is two cheeks and one apex block, the rest is stair rake. */
export function sideDormer(c, z, roof, { infill: fill, glass }, { face, y0, run = 1, depth = 3 }) {
  const [id, props] = Array.isArray(fill) ? fill : [fill, {}]
  const infill = id
  const st = (facing) => ({ facing, half: 'bottom', shape: 'straight', waterlogged: 'false' })
  for (let i = 0; i < depth; i++) {
    const x = face - run + i * run
    c.set(x, y0 + 1, z - 2, roof.stairs, st('south')); c.set(x, y0 + 1, z + 2, roof.stairs, st('north'))
    c.set(x, y0 + 2, z - 1, roof.stairs, st('south')); c.set(x, y0 + 2, z + 1, roof.stairs, st('north'))
    c.set(x, y0 + 3, z, roof.full)
    if (i >= 2) c.set(x, y0 + 2, z, infill, props) // the pocket under the ridge, behind the face, closed
  }
  for (const dz of [-1, 1]) for (let y = y0; y <= y0 + 1; y++) c.set(face, y, z + dz, infill, props)
  c.set(face, y0, z, glass); c.set(face, y0 + 1, z, glass)
  c.set(face, y0 + 2, z, infill, props)
  c.clear(face + run, y0 + 1, z)
}

/** wideStack: a chimney stack x0..x1 on the row z from y0 to y1 (solid `stone`, one column per flue, so each campfire
 *  stands on a whole column), a lit campfire on each flue in `flues` [x] at y1 + 1, and `cap` (a slab id, or null) on
 *  the columns between them. */
export function wideStack(c, { x0, x1, z, y0, y1 }, stone, { flues, cap = null }) {
  for (let x = x0; x <= x1; x++) for (let y = y0; y <= y1; y++) c.set(x, y, z, stone)
  for (let x = x0; x <= x1; x++) {
    if (flues.includes(x)) c.set(x, y1 + 1, z, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
    else if (cap) c.set(x, y1 + 1, z, cap, { type: 'bottom', waterlogged: 'false' })
  }
}
