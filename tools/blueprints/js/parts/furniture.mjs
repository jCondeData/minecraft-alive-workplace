// Furniture: the pieces of a room, each recording its furniture spot (furniture.py's kinds) as it draws (parts
// library, tools/blueprints/REBUILD.md). The plan lists every piece footprint first ({room, kind, part, pos, facing,
// cells, wood, colour}); the recipe draws them with furnitureParts[f.part](canvas, f). Owner rule 2: a short lamp never
// stands on a floor (tableLamp puts a side table under it; hangingLamp hangs it).
import { DIRS, OPP, slab, stair, trapdoor } from './canvas.mjs'
import { hearthFire } from './hearths.mjs'
import { kitchenParts } from './kitchens.mjs'

const roomParts = {
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
  hearth(c, f) { hearthFire(c, f.pos, f.facing) },
  chest(c, f) { kitchenParts.pantry(c, f) },
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

export const furnitureParts = { ...roomParts, ...kitchenParts }
