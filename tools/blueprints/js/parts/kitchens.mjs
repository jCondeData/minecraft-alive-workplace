// Kitchens: counters, the stove, the worktop, the pantry, wall shelves and the crockery on them (parts library).
// Every piece records its furniture spot. check.py's owner rule 3: crockery stands only in a kitchen or on a dining
// table, so plates and shelves of crockery belong here.
import { slab } from './canvas.mjs'

export const kitchenParts = {
  counter(c, f) { // a board base under a stone top (f.top: the top's slab, smooth stone by default)
    const [x, y, z] = f.pos
    c.free([x, y, z], [x, y + 1, z])
    c.set(x, y, z, f.base ?? `${f.wood}_planks`)
    slab(c, x, y + 1, z, f.top ?? 'smooth_stone_slab', f.topSlab ? 'top' : 'bottom') // topSlab: flush with plates on it
    c.spot('counter', f.pos, f.facing, [[0, 0, 0], [0, 1, 0]], f.wood)
  },
  stove(c, f) { const [x, y, z] = f.pos; c.free(f.pos); c.set(x, y, z, f.block ?? 'furnace', { facing: f.facing, lit: 'false' }) },
  worktop(c, f) { const [x, y, z] = f.pos; c.free(f.pos); c.set(x, y, z, 'crafting_table') },
  pantry(c, f) { const [x, y, z] = f.pos; c.free(f.pos); c.set(x, y, z, f.block === 'barrel' ? 'barrel' : 'chest', f.block === 'barrel' ? { facing: 'up', open: 'false' } : { facing: f.facing, type: 'single', waterlogged: 'false' }) },
  shelf(c, f) { // an upside-down slab on the wall, crockery on it when given
    const [x, y, z] = f.pos
    c.free([x, y, z], [x, y + 1, z])
    slab(c, x, y, z, `${f.wood}_slab`, 'top')
    c.spot('shelf', f.pos, f.facing, [[0, 0, 0]], f.wood)
    if (f.crockery) {
      c.set(x, y + 1, z, f.crockery)
      c.spot('crockery', [x, y + 1, z], f.facing, [[0, 0, 0]], f.wood, 'white')
    }
  },
  plates(c, f) { // crockery standing on a counter top or a dining table
    const [x, y, z] = f.pos
    c.free(f.pos)
    c.set(x, y, z, f.block)
    c.spot('crockery', f.pos, f.facing, [[0, 0, 0]], f.wood, 'white')
  },
  pot(c, f) { // a pot standing on a counter top: a fixture, no crockery spot (a one-block modded counter leaves the top
    // cell empty and the furnishing sets what stood on it down onto the counter, off the spot's own cell)
    const [x, y, z] = f.pos
    c.free(f.pos)
    c.set(x, y, z, f.block)
  },
  sink(c, f) { // a stone sink (a cauldron, f.block to change it) under the kitchen window: a fixture, no spot. Grown from the Terrace (49).
    const [x, y, z] = f.pos
    c.free(f.pos)
    c.set(x, y, z, f.block ?? 'cauldron')
  },
}
