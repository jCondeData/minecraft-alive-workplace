// Cold homes: the pieces the snowy and taiga looks of a home are drawn with (ROADMAP 49.10b, the Stone House's snowy
// and taiga looks; the Starter Cottage's looks set the language). Every block comes from the palette passed in.
//   snowLoad     snow lying on a steep slate roof: on its ridges, its eaves and the treads the caller picks
//   snowSkirt    one continuous drift of snow layers on the ground round a house, kept off its doorsteps
//   stormPorch   a closed entry niche before a door: stone cheeks either side under the eave, a lintel, a lantern
//   frontDormer  a gabled dormer in a slope whose ridge runs along x: a proper gable face, its own verge and snow
//   pentPorch    a lodge's front pent roof on log posts, with brackets under its ends
//   beamEnds     log ends poking out of a wall line (a sill beam's ends at a log storey's corners)
import { stair } from './canvas.mjs'

const ns = id => (id.includes(':') ? id : `minecraft:${id}`)

/** snowLoad: snow lying on a steep slate roof. Each bare `roof.full` topping a roof column (a ridge) takes `ridge`
 *  layers. A `roof.stairs` (bottom half) standing on a `roof.full` in its own column (a tread) becomes a full block with
 *  `layers` of snow on it when `tread(x, y, z)` picks it, or, with `shoulders`, when it is the course right under a
 *  ridge (the ridge's full block beside it, one up): snow then caps the ridge three wide instead of one thin bar.
 *  With `eaves`, the eave stairs (no roof under them) become full blocks with snow too: a snow-laden lip. `skip(x, y,
 *  z)` keeps a tread or ridge as drawn (a rake, a dormer), `eaveSkip` an eave. The cells stay where the roof's were,
 *  so the roof keeps its underside (rooms, chains). */
export function snowLoad(c, roof, snow, { layers = '2', ridge = '3', eaves = false, shoulders = false, tread = () => true, skip = () => false, eaveSkip = () => false } = {}) {
  const full = ns(roof.full), stairs = ns(roof.stairs), air = 'minecraft:air'
  const [w, h, d] = c.size
  const base = c.base ?? 0
  const ridgeAt = (x, y, z) => c.name(x, y, z) === full && c.name(x, y + 1, z) === air && c.name(x, y - 1, z) !== full
  const tops = []
  for (let x = 0; x < w; x++) for (let z = 0; z < d; z++) for (let y = -base; y < h - base; y++) {
    const got = c.get(x, y, z)
    if (!got) continue
    const below = c.name(x, y - 1, z)
    const isStair = got.id === stairs && got.props.half === 'bottom'
    if (isStair && below !== full && below !== stairs) { // an eave
      if (eaves && !eaveSkip(x, y, z)) { c.set(x, y, z, roof.full); tops.push([x, y + 1, z, layers]) }
      continue
    }
    if (skip(x, y, z)) continue
    if (isStair && below === full) {
      const shoulder = shoulders && [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dz]) => ridgeAt(x + dx, y + 1, z + dz))
      if (shoulder || tread(x, y, z)) { c.set(x, y, z, roof.full); tops.push([x, y + 1, z, layers]) }
    } else if (got.id === full && c.name(x, y + 1, z) === air) tops.push([x, y + 1, z, ridge])
  }
  for (const [x, y, z, n] of tops)
    if (y < h - base && c.name(x, y, z) === air) c.set(x, y, z, snow, { layers: n })
}

/** snowSkirt: one continuous drift of snow layers (`layers`) on the ground layer (y 0) round the house: every free
 *  cell beside it or at its corners (a block at y 0 or 1 next to it, diagonals too), under eaves and sills alike;
 *  `keep` [[x, z]] stay bare (doorsteps, the walks to doors). */
export function snowSkirt(c, snow, keep = [], layers = '2') {
  const [w, , d] = c.size
  const solid = (x, z) => c.name(x, 0, z) !== 'minecraft:air' || c.name(x, 1, z) !== 'minecraft:air'
  const bare = new Set(keep.map(([x, z]) => `${x},${z}`))
  const add = []
  for (let x = 0; x < w; x++) for (let z = 0; z < d; z++) {
    if (solid(x, z) || bare.has(`${x},${z}`)) continue
    let near = false
    for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++)
      if ((dx || dz) && x + dx >= 0 && x + dx < w && z + dz >= 0 && z + dz < d && solid(x + dx, z + dz)) near = true
    if (near) add.push([x, z])
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

/** frontDormer: a three-wide gabled dormer centred on x = cx in a slope whose ridge runs along x and falls to z = 0:
 *  its face in the wall plane z = face (cheek posts at cx +- 1 from y0 to y0 + 1, two panes at cx), a proper little
 *  gable over them (`infill` three wide at y0 + 2 and one at the apex y0 + 3), under its own 45-degree roof (ridge
 *  along z at y0 + 4, eaves at cx +- 2 on y0 + 2) running from the overhang (z = face - 1) back into the main slope;
 *  cells the main roof holds are kept, a pocket under the dormer's ridge is closed with `infill`. The overhang's row
 *  is a `verge` (stairs of another material, e.g. a wood, and `vergeTop` its apex block) so the dormer's roof shows against the main slope; with
 *  `snow`, snow lies along its ridge. The face replaces the main roof's cells there. */
export function frontDormer(c, { cx, face, y0 }, roof, { cheek, infill, glass, verge = roof.stairs, vergeTop = roof.full, snow = null }) {
  for (let x = cx - 1; x <= cx + 1; x++) for (let y = y0; y <= y0 + 4; y++) c.clear(x, y, face)
  for (const dx of [-1, 1]) for (let y = y0; y <= y0 + 1; y++) c.set(cx + dx, y, face, cheek, { axis: 'y' })
  c.set(cx, y0, face, glass); c.set(cx, y0 + 1, face, glass)
  for (let x = cx - 1; x <= cx + 1; x++) c.set(x, y0 + 2, face, infill)
  c.set(cx, y0 + 3, face, infill)
  const st = facing => ({ facing, half: 'bottom', shape: 'straight', waterlogged: 'false' })
  const ridgeY = y0 + 4
  for (let z = face - 1; c.get(cx, ridgeY, z) === undefined; z++) {
    const put = (x, y, id, props = {}) => { if (!c.get(x, y, z)) c.set(x, y, z, id, props) }
    const sides = z === face - 1 ? verge : roof.stairs
    for (let i = 0; i < 2; i++) { put(cx - 2 + i, y0 + 2 + i, sides, st('east')); put(cx + 2 - i, y0 + 2 + i, sides, st('west')) }
    put(cx, ridgeY, z === face - 1 ? vergeTop : roof.full)
    if (snow && !c.get(cx, ridgeY + 1, z)) c.set(cx, ridgeY + 1, z, snow, { layers: '3' })
    if (z > face) for (let y = y0 + 2; y < ridgeY; y++) put(cx, y, infill)
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
