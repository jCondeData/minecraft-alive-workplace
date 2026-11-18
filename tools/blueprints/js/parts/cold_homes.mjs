// Cold homes: the pieces the snowy and taiga looks of a home are drawn with (ROADMAP 49.10b, the Stone House's snowy
// and taiga looks; the Starter Cottage's looks set the language). Every block comes from the palette passed in.
//   snowLoad     snow lying on a steep slate roof: on its ridges, its eaves and the treads the caller picks
//   snowSkirt    a drift of snow layers on the ground round a house, kept off its doorsteps and walks
//   stormPorch   a closed entry niche before a door: stone cheeks either side under the eave, a lintel, a lantern
//   frontDormer  a steep gabled dormer in a slope whose ridge runs along x, its face in the wall line (z)
//   pentPorch    a lodge's front pent roof on log posts, with brackets under its ends
//   beamEnds     log ends poking out of a wall line (a sill beam's ends at a log storey's corners)
import { stair } from './canvas.mjs'

const ns = id => (id.includes(':') ? id : `minecraft:${id}`)

/** snowLoad: turns every steep roof course into a step of snow: each `roof.stairs` (bottom half) standing on a
 *  `roof.full` in its own column becomes a full block with `snow` layers on top (when the cell over it is free and
 *  inside the canvas), and each bare full block topping a roof column (the ridges) takes snow too. With `eaves`, the
 *  eave stairs (nothing under them) become full blocks with snow as well: a thick, snow-laden lip. `tread(x, y, z)`
 *  picks which courses above the eave carry snow (default all; a few reads as snow lying, every one as stripes);
 *  `skip(x, y, z)` keeps a cell as drawn (a rake, a dormer). The cells stay where the roof's were, so the roof keeps
 *  its underside (rooms, chains). */
export function snowLoad(c, roof, snow, { layers = '2', ridge = '3', eaves = false, tread = () => true, skip = () => false } = {}) {
  const full = ns(roof.full), stairs = ns(roof.stairs)
  const [w, h, d] = c.size
  const base = c.base ?? 0
  const tops = []
  for (let x = 0; x < w; x++) for (let z = 0; z < d; z++) for (let y = -base; y < h - base; y++) {
    const got = c.get(x, y, z)
    if (!got || skip(x, y, z)) continue
    const below = c.name(x, y - 1, z)
    if (got.id === stairs && got.props.half === 'bottom' && ((below === full && tread(x, y, z)) || (eaves && below !== full && below !== stairs))) {
      c.set(x, y, z, roof.full)
      tops.push([x, y + 1, z, layers])
    } else if (got.id === full && c.name(x, y + 1, z) === 'minecraft:air') tops.push([x, y + 1, z, ridge])
  }
  for (const [x, y, z, n] of tops)
    if (y < h - base && c.name(x, y, z) === 'minecraft:air') c.set(x, y, z, snow, { layers: n })
}

/** snowSkirt: snow layers (`layers`) on the ground layer (y 0) in every free cell touching the house (a block at y 0
 *  or 1 beside it) with nothing over it from y 1 to 3; `keep` [[x, z]] stay bare (doorsteps, the walks to doors). */
export function snowSkirt(c, snow, keep = [], layers = '2') {
  const [w, , d] = c.size
  const solid = (x, z) => c.name(x, 0, z) !== 'minecraft:air' || c.name(x, 1, z) !== 'minecraft:air'
  const covered = (x, z) => [1, 2, 3].some(y => c.name(x, y, z) !== 'minecraft:air')
  const bare = new Set(keep.map(([x, z]) => `${x},${z}`))
  const add = []
  for (let x = 0; x < w; x++) for (let z = 0; z < d; z++) {
    if (solid(x, z) || covered(x, z) || bare.has(`${x},${z}`)) continue
    if ([[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dz]) => x + dx >= 0 && x + dx < w && z + dz >= 0 && z + dz < d && solid(x + dx, z + dz))) add.push([x, z])
  }
  for (const [x, z] of add) c.set(x, 0, z, snow, { layers })
}

/** stormPorch: a closed entry niche before a door at (x, 1, z + 1) in a wall at z + 1: stone `cheek` walls at x - 1
 *  and x + 1 on row z from y 1 to `top`, a `lintel` log along x over the opening at `top`, a lantern hung under it
 *  (`lamp` false: none). The cold reaches the door only straight on. */
export function stormPorch(c, x, z, top, { cheek, lintel, lamp = true }) {
  for (let y = 1; y <= top; y++) { c.set(x - 1, y, z, cheek); c.set(x + 1, y, z, cheek) }
  c.set(x, top, z, lintel, { axis: 'x' })
  if (lamp) c.set(x, top - 1, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
}

/** frontDormer: a three-wide steep gabled dormer centred on x = cx in a slope whose ridge runs along x and falls to
 *  z = 0: its face in the wall plane z = face (cheek posts at cx +- 1 from y0 to y0 + 2, two panes at cx, `infill`
 *  over them to the apex), its little roof (ridge along z) eaved at y0 + 2 on cx +- 2, running from the overhang
 *  (z = face - 1) back into the main slope (cells the main roof holds are kept; a pocket under the dormer's ridge is
 *  closed with `infill`). The face replaces the main roof's cells there. */
export function frontDormer(c, { cx, face, y0 }, roof, { cheek, infill, glass }) {
  for (let x = cx - 1; x <= cx + 1; x++) for (let y = y0; y <= y0 + 4; y++) c.clear(x, y, face)
  for (const dx of [-1, 1]) for (let y = y0; y <= y0 + 2; y++) c.set(cx + dx, y, face, cheek, { axis: 'y' })
  c.set(cx, y0, face, glass); c.set(cx, y0 + 1, face, glass)
  for (let y = y0 + 2; y <= y0 + 4; y++) c.set(cx, y, face, infill)
  const eave = y0 + 2
  const st = facing => ({ facing, half: 'bottom', shape: 'straight', waterlogged: 'false' })
  for (let z = face - 1; c.get(cx, eave + 3, z) === undefined; z++) {
    const put = (x, y, id, props = {}) => { if (!c.get(x, y, z)) c.set(x, y, z, id, props) }
    put(cx - 2, eave, roof.stairs, st('east')); put(cx + 2, eave, roof.stairs, st('west'))
    put(cx - 1, eave + 1, roof.full); put(cx + 1, eave + 1, roof.full)
    put(cx - 1, eave + 2, roof.stairs, st('east')); put(cx + 1, eave + 2, roof.stairs, st('west'))
    put(cx, eave + 3, roof.full)
    if (z > face) for (let y = eave; y < eave + 3; y++) put(cx, y, infill)
  }
}

/** pentPorch: a pent roof across a front at z (x0..x1, its stairs at y rising toward the wall behind, `toward`), on
 *  log posts (`post`) at `posts` [x] from y 1 to y - 1, an upside-down `bracket` stair under the roof beside each post
 *  (facing the post), and lanterns hung under it at `lamps` [x]. */
export function pentPorch(c, { x0, x1, z, y, toward = 'south', posts = [], lamps = [] }, { roof, post, bracket }) {
  for (let x = x0; x <= x1; x++) stair(c, x, y, z, roof.stairs, toward)
  for (const px of posts) {
    for (let yy = 1; yy < y; yy++) c.set(px, yy, z, post, { axis: 'y' })
    for (const dx of [-1, 1]) if (px + dx >= x0 && px + dx <= x1 && !c.get(px + dx, y - 1, z))
      stair(c, px + dx, y - 1, z, bracket, dx < 0 ? 'east' : 'west', 'top')
  }
  for (const lx of lamps) c.set(lx, y - 1, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
}

/** beamEnds: log ends (`log`) poking out of a wall: each [x, y, z, axis] cell set with the log lying along `axis`. */
export function beamEnds(c, cells, log) {
  for (const [x, y, z, axis] of cells) c.set(x, y, z, log, { axis })
}
