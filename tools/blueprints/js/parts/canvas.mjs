// The rebuild kit's parts library (ROADMAP 49.1, tools/blueprints/REBUILD.md): the canvas every part draws on and the
// small helpers they share. Grown from the Daub House (36.23 Version B). Facing words are Minecraft's: north = toward
// z = 0 (the front). Each family keeps its own palette and massing (its plan.mjs) and passes them in; a part never
// picks a block itself unless it is told a default.

export const DIRS = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }
export const OPP = { north: 'south', south: 'north', west: 'east', east: 'west' }

export const ns = id => (id.includes(':') ? id : `minecraft:${id}`)

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

export const stair = (c, x, y, z, id, facing, half = 'bottom') =>
  c.set(x, y, z, id, { facing, half, shape: 'straight', waterlogged: 'false' })
export const slab = (c, x, y, z, id, type = 'bottom') => c.set(x, y, z, id, { type, waterlogged: 'false' })
export const trapdoor = (c, x, y, z, id, facing, half = 'bottom', open = false) =>
  c.set(x, y, z, id, { facing, half, open: String(open), powered: 'false', waterlogged: 'false' })

// A canvas with a basement (ROADMAP 26.15): the drawing's y = 0 is the ground floor's layer and the cells below it, down
// to y = -base, are the cellar. The file holds the basement at the bottom (every cell `base` higher), as the kit's
// Build saves it; furniture spots keep the drawing's coordinates. CUT (cutAbove) takes the drawing's y.
export class LiftedCanvas extends Canvas {
  constructor([w, h, d], base = 0) { super([w, h + base, d]); this.base = base }
  set(x, y, z, id, props = {}) { super.set(x, y + this.base, z, typeof id === "function" ? id(x, y, z) : id, props) } // a mix() by the drawing's y
  get(x, y, z) { return super.get(x, y + this.base, z) }
  clear(x, y, z) { super.clear(x, y + this.base, z) }
  cutAbove(y) { return super.cutAbove(y + this.base) }
}
