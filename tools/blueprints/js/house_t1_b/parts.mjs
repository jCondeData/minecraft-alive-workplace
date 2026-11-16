// House T1 (Version B), the parts library: small named parts the recipe assembles from the plan. Each part draws
// onto a Canvas (a map of cells, so a later pass can override or clear an earlier one) and furniture parts record
// their furniture spot. Facing words are Minecraft's: north = toward z = 0 (the front).

export const DIRS = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }
export const OPP = { north: 'south', south: 'north', west: 'east', east: 'west' }

const ns = id => (id.includes(':') ? id : `minecraft:${id}`)

export class Canvas {
  constructor([w, h, d]) {
    this.size = [w, h, d]
    this.cells = new Map()
    this.spots = [] // furniture spots: {kind, pos, facing, cells (absolute), wood, colour}
  }
  key(x, y, z) { return `${x},${y},${z}` }
  inside(x, y, z) { const [w, h, d] = this.size; return x >= 0 && x < w && y >= 0 && y < h && z >= 0 && z < d }
  set(x, y, z, id, props = {}) {
    if (!this.inside(x, y, z)) throw new Error(`out of bounds ${x},${y},${z} ${id}`)
    if (typeof id === 'function') id = id(x, y, z)
    this.cells.set(this.key(x, y, z), { id: ns(id), props: { ...props } })
  }
  get(x, y, z) { return this.cells.get(this.key(x, y, z)) }
  name(x, y, z) { return this.get(x, y, z)?.id ?? 'minecraft:air' }
  clear(x, y, z) { this.cells.delete(this.key(x, y, z)) }
  fill(x0, y0, z0, x1, y1, z1, id, props = {}) {
    for (let x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
      for (let y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
        for (let z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) this.set(x, y, z, id, props)
  }
  clearBox(x0, y0, z0, x1, y1, z1) {
    for (let x = x0; x <= x1; x++) for (let y = y0; y <= y1; y++) for (let z = z0; z <= z1; z++) this.clear(x, y, z)
  }
  free(...cells) {
    for (const [x, y, z] of cells) {
      const got = this.get(x, y, z)
      if (got && got.id !== 'minecraft:air') throw new Error(`furniture over ${got.id} at ${x},${y},${z}`)
    }
  }
  spot(kind, pos, facing, rel = [[0, 0, 0]], wood = null, colour = null) {
    this.spots.push({ kind, pos, facing, cells: rel.map(([dx, dy, dz]) => [pos[0] + dx, pos[1] + dy, pos[2] + dz]), wood, colour })
  }
  // a cut copy: everything above y (a floor's cutaway)
  cutAbove(y) {
    const c = new Canvas(this.size)
    for (const [k, v] of this.cells) if (Number(k.split(',')[1]) <= y) c.cells.set(k, v)
    return c
  }
  toStructure(api) {
    const s = api.createStructure(this.size)
    for (const [k, { id, props }] of [...this.cells].sort()) {
      const [x, y, z] = k.split(',').map(Number)
      api.setBlock(s, [x, y, z], id, props)
    }
    return s
  }
}

// A weighted, repeatable mix (the same cell always gets the same block): stone that reads as gathered, not printed.
export function mix(weighted, seed = 1) {
  const total = weighted.reduce((a, [, w]) => a + w, 0)
  return (x, y, z) => {
    let h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ (seed * 2654435761)
    h = Math.abs((h ^ (h >>> 13)) * 1274126177) % total
    for (const [id, w] of weighted) { if (h < w) return id; h -= w }
    return weighted[0][0]
  }
}

const stair = (c, x, y, z, id, facing, half = 'bottom') =>
  c.set(x, y, z, id, { facing, half, shape: 'straight', waterlogged: 'false' })
const slab = (c, x, y, z, id, type = 'bottom') => c.set(x, y, z, id, { type, waterlogged: 'false' })
const trapdoor = (c, x, y, z, id, facing, half = 'bottom', open = false) =>
  c.set(x, y, z, id, { facing, half, open: String(open), powered: 'false', waterlogged: 'false' })

// --- Structure parts ---------------------------------------------------------------------------------------------

/** plinth: a stone ring (or slab of ground) at y, the house's darkest course. */
export function plinth(c, x0, z0, x1, z1, stone, y = 0) {
  c.fill(x0, y, z0, x1, y, z1, stone)
}

/** floor: boards (or flags) over a rectangle at y. */
export function floor(c, x0, z0, x1, z1, y, id) { c.fill(x0, y, z0, x1, y, z1, id) }

/** post: an upright frame log from y0 to y1. */
export function post(c, x, z, y0, y1, log) { for (let y = y0; y <= y1; y++) c.set(x, y, z, log, { axis: 'y' }) }

/** beamRing: horizontal logs round a rectangle at y, corners upright. */
export function beamRing(c, x0, z0, x1, z1, y, log) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (x !== x0 && x !== x1 && z !== z0 && z !== z1) continue
    const corner = (x === x0 || x === x1) && (z === z0 || z === z1)
    c.set(x, y, z, log, { axis: corner ? 'y' : (z === z0 || z === z1) ? 'x' : 'z' })
  }
}

/** wallPanel: a straight wall from a to b (same x or same z) between y0 and y1. */
export function wallPanel(c, [ax, az], [bx, bz], y0, y1, id) {
  for (let x = Math.min(ax, bx); x <= Math.max(ax, bx); x++)
    for (let z = Math.min(az, bz); z <= Math.max(az, bz); z++)
      for (let y = y0; y <= y1; y++) c.set(x, y, z, id)
}

/** window: glass panes in the given cells; dressing 'sill' (an upside-down stair one block out under the lowest
 *  pane, standing on the plinth, with a potted flower on it), or 'shutters' (open trapdoors flat on the wall either
 *  side, one block out). */
export function window(c, cells, out, dress, { glass = 'glass_pane', wood = 'spruce', pots = [] } = {}) {
  for (const [x, y, z] of cells) c.set(x, y, z, glass)
  if (!dress) return
  const [ox, oz] = DIRS[out]
  const low = Math.min(...cells.map(p => p[1]))
  const high = Math.max(...cells.map(p => p[1]))
  const bottoms = cells.filter(p => p[1] === low)
  if (dress === 'sill') {
    bottoms.forEach(([x, y, z], i) => {
      stair(c, x + ox, y - 1, z + oz, `${wood}_stairs`, OPP[out], 'top')
      if (pots[i]) c.set(x + ox, y, z + oz, pots[i])
    })
  } else if (dress === 'shutters') {
    const along = out === 'north' || out === 'south' ? [1, 0] : [0, 1]
    const xs = bottoms.map(p => p[0] * along[0] + p[2] * along[1])
    const lo = Math.min(...xs) - 1, hi = Math.max(...xs) + 1
    const [bx, , bz] = bottoms[0]
    for (const t of [lo, hi]) for (let y = low; y <= high; y++) {
      const x = along[0] ? t : bx, z = along[1] ? t : bz
      trapdoor(c, x + ox, y, z + oz, `${wood}_trapdoor`, out, 'bottom', true)
    }
  }
}

/** door: a two-high door with a stair step at y - 1 one block out. */
export function door(c, [x, y, z], facing, out, { wood = 'spruce', step = null } = {}) {
  for (const half of ['lower', 'upper'])
    c.set(x, y + (half === 'upper' ? 1 : 0), z, `${wood}_door`, { facing, half, hinge: 'left', open: 'false', powered: 'false' })
  if (step) stair(c, step[0], step[1], step[2], 'cobblestone_stairs', OPP[out])
}

/** gableRoof: a roof over `across` (the span, eaves at both ends) running `along` (gable ends at both ends,
 *  overhanging one block). steep: a full block under each stair (two up per one across). `keep(x,y,z)` returns false
 *  for cells another roof or wall owns. Returns each column's lowest roof y (for gables and trim). */
export function gableRoof(c, { axis, across: [a0, a1], along: [l0, l1], eaveY, steep }, roof, keep = () => true) {
  const half = (a1 - a0) / 2
  const mid = a0 + half
  const put = (u, y, v, id, props) => {
    const [x, z] = axis === 'z' ? [u, v] : [v, u]
    if (keep(x, y, z)) c.set(x, y, z, id, props)
  }
  const ridgeY = steep ? eaveY + 2 * half - 1 : eaveY + half
  for (let v = l0; v <= l1; v++) {
    for (let i = 0; i < half; i++) {
      for (const [u, rise] of [[a0 + i, axis === 'z' ? 'east' : 'south'], [a1 - i, axis === 'z' ? 'west' : 'north']]) {
        if (steep) {
          if (i > 0) put(u, eaveY + 2 * i - 1, v, roof.full)
          put(u, eaveY + 2 * i, v, roof.stairs, { facing: rise, half: 'bottom', shape: 'straight', waterlogged: 'false' })
        } else {
          put(u, eaveY + i, v, roof.stairs, { facing: rise, half: 'bottom', shape: 'straight', waterlogged: 'false' })
        }
      }
    }
    put(mid, ridgeY, v, roof.full)
  }
  // the underside height of each column across: a gable wall fills up to (but not into) it
  const under = {}
  for (let i = 0; i <= half; i++) {
    const y = i === 0 ? eaveY : steep ? (i === half ? ridgeY : eaveY + 2 * i - 1) : eaveY + i
    under[a0 + i] = y; under[a1 - i] = y
  }
  return { ridgeY, under }
}

/** gableWall: fills a gable end under a roof from y0 up to each column's underside, with `pick(u, y)` choosing the
 *  block (daub, stud, glass...). */
export function gableWall(c, axis, line, [a0, a1], y0, under, pick) {
  for (let u = a0; u <= a1; u++) for (let y = y0; y < under[u]; y++) {
    const id = pick(u, y)
    if (!id) continue
    const [x, z] = axis === 'z' ? [u, line] : [line, u]
    const [name, props] = Array.isArray(id) ? id : [id, {}]
    c.set(x, y, z, name, props)
  }
}

/** bargeTrim: a dark board under a gable's overhanging rake, stepping up with it (an upside-down slab under each
 *  column's lowest roof cell on the overhang line), so the roof's edge reads apart from the gable. */
export function bargeTrim(c, axis, line, [a0, a1], under, id) {
  for (let u = a0; u <= a1; u++) {
    const [x, z] = axis === 'z' ? [u, line] : [line, u]
    if (!c.get(x, under[u] - 1, z)) slab(c, x, under[u] - 1, z, id, 'top')
  }
}

/** chimney: a stone stack from y0 to y1 with a lit campfire on top for smoke, and a stepped breast at its foot. */
export function chimney(c, x, z, y0, y1, stone, { breast = null } = {}) {
  for (let y = y0; y <= y1; y++) c.set(x, y, z, stone)
  c.set(x, y1 + 1, z, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  if (breast) {
    const { axis, top } = breast
    for (const d of [-1, 1]) {
      const [bx, bz] = axis === 'x' ? [x + d, z] : [x, z + d]
      for (let y = y0; y < top; y++) c.set(bx, y, bz, stone)
      const f = axis === 'x' ? (d < 0 ? 'east' : 'west') : (d < 0 ? 'south' : 'north')
      stair(c, bx, top, bz, 'cobblestone_stairs', f)
    }
  }
}

/** dormer: a three-wide gabled dormer in the west slope of the steep roof, centred on z: its face in the knee line
 *  (x 1, y 6..9) of daub round a two-high window, its little roof (ridge along x) overhanging to the eave line and
 *  running its ridge into the main slope. */
export function dormer(c, z, roof, infill, glass, frame) {
  for (let dz = -1; dz <= 1; dz++) for (let y = 6; y <= 8; y++) c.set(1, y, z + dz, infill)
  c.set(1, 9, z, infill)
  c.set(1, 6, z, glass); c.set(1, 7, z, glass)
  for (const dz of [-1, 1]) for (let y = 6; y <= 8; y++) c.set(1, y, z + dz, frame, { axis: 'y' }) // corner posts
  for (const x of [0, 1]) {
    stair(c, x, 8, z - 2, roof.stairs, 'south')
    stair(c, x, 8, z + 2, roof.stairs, 'north')
    stair(c, x, 9, z - 1, roof.stairs, 'south')
    stair(c, x, 9, z + 1, roof.stairs, 'north')
  }
  for (const x of [0, 1, 2]) c.set(x, 10, z, roof.full)
}

/** hood: a little tiled hood over a door: two stairs rising to a full block, a lantern hanging under it. */
export function hood(c, x, y, z, roof) {
  stair(c, x - 1, y, z, roof.stairs, 'east')
  stair(c, x + 1, y, z, roof.stairs, 'west')
  c.set(x, y, z, roof.full)
  c.set(x, y - 1, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
}

/** flowerBed: a bed of its own soil with flowers in it (never plants on bare ground). */
export function flowerBed(c, x0, x1, z, plants) {
  for (let x = x0; x <= x1; x++) {
    c.set(x, 0, z, 'grass_block', { snowy: 'false' })
    c.set(x, 1, z, plants[(x - x0) % plants.length])
  }
}

/** woodpile: split logs stacked against a wall, two high in the middle. */
export function woodpile(c, x, z0, z1, log) {
  for (let z = z0; z <= z1; z++) c.set(x, 0, z, log, { axis: 'x' })
  for (let z = z0 + 1; z <= z1 - 1; z++) c.set(x, 1, z, log, { axis: 'x' })
}

/** railing: fence posts along a stairwell's open sides (finish() joins them). */
export function railing(c, cells, wood) { for (const [x, y, z] of cells) c.set(x, y, z, `${wood}_fence`) }

/** flight: a straight stair flight, solid under each step. */
export function flight(c, steps, rises, wood, under) {
  for (const [x, y, z] of steps) {
    stair(c, x, y, z, `${wood}_stairs`, rises)
    for (let yy = 1; yy < y; yy++) c.set(x, yy, z, under)
  }
}

// --- Furniture parts (each records its spot) -------------------------------------------------------------------

export const furnitureParts = {
  table(c, f) { // a fence leg with a carpet top
    const [x, y, z] = f.pos
    c.free([x, y, z], [x, y + 1, z])
    c.set(x, y, z, `${f.wood}_fence`)
    c.set(x, y + 1, z, `${f.colour}_carpet`)
    c.spot('table', f.pos, f.facing, [[0, 0, 0], [0, 1, 0]], f.wood, f.colour)
  },
  chair(c, f) { // a stair, the sitter facing f.facing (the stair's backrest the other side)
    const [x, y, z] = f.pos
    c.free(f.pos)
    stair(c, x, y, z, `${f.wood}_stairs`, OPP[f.facing])
    c.spot('chair', f.pos, f.facing, [[0, 0, 0]], f.wood)
  },
  armchair(c, f) { // a dark stair seat on a rug-coloured cushion: the stair alone, coloured by the rug beside it
    const [x, y, z] = f.pos
    c.free(f.pos)
    stair(c, x, y, z, `${f.wood}_stairs`, OPP[f.facing])
    c.spot('chair', f.pos, f.facing, [[0, 0, 0]], f.wood, f.colour)
  },
  hangingLamp(c, f) { // a lantern on a chain from the beam or ridge above
    const [x, y, z] = f.pos
    const n = f.chain ?? 0
    c.free(f.pos)
    for (let i = 1; i <= n; i++) c.set(x, y + i, z, 'chain', { axis: 'y', waterlogged: 'false' })
    c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
    c.spot('lamp', f.pos, f.facing, Array.from({ length: n + 1 }, (_, i) => [0, i, 0]), f.wood) // lantern and chain
  },
  tableLamp(c, f) { // a short lantern never stands on the floor: a little side table (a top slab) under it
    const [x, y, z] = f.pos
    c.free([x, y, z], [x, y + 1, z])
    c.set(x, y, z, `${f.wood}_slab`, { type: 'top', waterlogged: 'false' })
    c.set(x, y + 1, z, 'lantern', { hanging: 'false', waterlogged: 'false' })
    c.spot('side_table', f.pos, f.facing, [[0, 0, 0]], f.wood, 'white')
    c.spot('lamp', [x, y + 1, z], f.facing, [[0, 0, 0]], f.wood)
  },
  clock(c, f) { // a tall case: a stripped log under a trapdoor hood
    const [x, y, z] = f.pos
    c.free([x, y, z], [x, y + 1, z])
    c.set(x, y, z, `stripped_${f.wood}_log`, { axis: 'y' })
    trapdoor(c, x, y + 1, z, `${f.wood}_trapdoor`, f.facing)
    c.spot('clock', f.pos, f.facing, [[0, 0, 0], [0, 1, 0]], f.wood)
  },
  shelf(c, f) { // an upside-down slab on the wall with crockery on it
    const [x, y, z] = f.pos
    c.free([x, y, z], [x, y + 1, z])
    slab(c, x, y, z, `${f.wood}_slab`, 'top')
    c.spot('shelf', f.pos, f.facing, [[0, 0, 0]], f.wood)
    if (f.crockery) {
      c.set(x, y + 1, z, f.crockery)
      c.spot('crockery', [x, y + 1, z], f.facing, [[0, 0, 0]], f.wood, 'white')
    }
  },
  plates(c, f) { // crockery standing on a counter top
    const [x, y, z] = f.pos
    c.free(f.pos)
    c.set(x, y, z, f.block)
    c.spot('crockery', f.pos, f.facing, [[0, 0, 0]], f.wood, 'white')
  },
  cupboard(c, f) { // a two-high cupboard: shelves of books under a closed top
    const [x, y, z] = f.pos
    c.free([x, y, z], [x, y + 1, z])
    c.set(x, y, z, 'bookshelf')
    slab(c, x, y + 1, z, `${f.wood}_slab`, 'bottom')
    c.spot('cupboard', f.pos, f.facing, [[0, 0, 0], [0, 1, 0]], f.wood)
  },
  picture(c, f) { // a plain cloth on the wall (a wall banner)
    const [x, y, z] = f.pos
    c.free(f.pos)
    c.set(x, y, z, `${f.colour}_wall_banner`, { facing: f.facing })
    c.spot('picture', f.pos, f.facing, [[0, 0, 0]], f.wood, f.colour)
  },
  rug(c, f) { for (const [x, y, z] of f.cellsAbs) { c.free([x, y, z]); c.set(x, y, z, `${f.colour}_carpet`) } },
  hearth(c, f) { const [x, y, z] = f.pos; c.set(x, y, z, 'campfire', { facing: f.facing, lit: 'true', signal_fire: 'false', waterlogged: 'false' }) },
  counter(c, f) { // a board base under a stone top
    const [x, y, z] = f.pos
    c.free([x, y, z], [x, y + 1, z])
    c.set(x, y, z, `${f.wood}_planks`)
    slab(c, x, y + 1, z, 'smooth_stone_slab', f.topSlab ? 'top' : 'bottom') // topSlab: flush with the plates on it
    c.spot('counter', f.pos, f.facing, [[0, 0, 0], [0, 1, 0]], f.wood)
  },
  stove(c, f) { const [x, y, z] = f.pos; c.free(f.pos); c.set(x, y, z, 'furnace', { facing: f.facing, lit: 'false' }) },
  worktop(c, f) { const [x, y, z] = f.pos; c.free(f.pos); c.set(x, y, z, 'crafting_table') },
  pantry(c, f) { const [x, y, z] = f.pos; c.free(f.pos); c.set(x, y, z, 'chest', { facing: f.facing, type: 'single', waterlogged: 'false' }) },
  chest(c, f) { furnitureParts.pantry(c, f) },
  bed(c, f) { // foot at pos, head one block toward facing
    const [x, y, z] = f.pos
    const [dx, dz] = DIRS[f.facing]
    c.free([x, y, z], [x + dx, y, z + dz])
    c.set(x, y, z, `${f.colour}_bed`, { facing: f.facing, part: 'foot', occupied: 'false' })
    c.set(x + dx, y, z + dz, `${f.colour}_bed`, { facing: f.facing, part: 'head', occupied: 'false' })
    c.spot('bed', f.pos, f.facing, [[0, 0, 0], [dx, 0, dz]], null, f.colour)
  },
  nightstand(c, f) { // an upside-down slab with a lit candle on it
    const [x, y, z] = f.pos
    c.free([x, y, z], [x, y + 1, z])
    slab(c, x, y, z, `${f.wood}_slab`, 'top')
    c.set(x, y + 1, z, 'candle', { candles: '1', lit: 'true', waterlogged: 'false' })
    c.spot('nightstand', f.pos, f.facing, [[0, 0, 0]], f.wood)
  },
  desk(c, f) { // an upside-down slab against the wall, a candle on it
    const [x, y, z] = f.pos
    c.free([x, y, z], [x, y + 1, z])
    slab(c, x, y, z, `${f.wood}_slab`, 'top')
    c.set(x, y + 1, z, 'candle', { candles: '2', lit: 'true', waterlogged: 'false' })
    c.spot('desk', f.pos, f.facing, [[0, 0, 0]], f.wood)
  },
}
