// Garden: the living pieces round a pond, a yard or a park (parts library, tools/blueprints/REBUILD.md): an old tree
// grown by hand (logs and persistent leaves, nothing that grows or decays by itself), lily pads on still water, and
// a heap of field boulders. Every block comes from the family's palette; none is picked here.

/** tree: a hand-grown tree. `logs` are [x, y, z, axis] cells (the trunk and its branches, each touching the last);
 *  `leaves` are [x, y, z] cells, drawn only where nothing is drawn yet (a log, a lantern keeps its cell); `leaf` is a
 *  block id or a function of (x, y, z) (a mix(): leaves with and without flowers). Leaves are drawn persistent, as a
 *  builder's placed leaves are, so they never decay. */
export function tree(c, logs, leaves, log, leaf) {
  for (const [x, y, z, axis = 'y'] of logs) c.set(x, y, z, log, { axis })
  for (const [x, y, z] of leaves) {
    if (c.get(x, y, z)) continue
    const id = typeof leaf === 'function' ? leaf(x, y, z) : leaf
    c.set(x, y, z, id, { persistent: 'true', distance: '1', waterlogged: 'false' })
  }
}

/** canopy: the leaf cells of a layered crown, from rows of [y, z, x0, x1] (one row of leaves along x at that y and
 *  z): the shape stays readable in plan.mjs, a ragged outline row by row. */
export function canopy(rows) {
  const cells = []
  for (const [y, z, x0, x1] of rows) for (let x = x0; x <= x1; x++) cells.push([x, y, z])
  return cells
}

/** lilyPads: a pad on each given [x, z] at y, which must stand on a still water source at y - 1 (the only block a
 *  pad survives on); throws otherwise. */
export function lilyPads(c, cells, y, pad) {
  for (const [x, z] of cells) {
    const under = c.get(x, y - 1, z)
    if (!under || under.id !== 'minecraft:water' || (under.props.level ?? '0') !== '0') throw new Error(`lily pad at ${x},${y},${z} isn't on a water source`)
    c.set(x, y, z, pad)
  }
}

/** boulders: field stones heaped on the ground, from [x, y, z] cells (`stone`, a block or a mix()), each resting on a
 *  drawn block; `caps` are [x, y, z] cells that get a bottom slab (`capSlab`) to round a heap's top. */
export function boulders(c, cells, stone, caps = [], capSlab = null) {
  for (const [x, y, z] of cells) {
    if (y > 0 && !c.get(x, y - 1, z)) throw new Error(`boulder at ${x},${y},${z} has nothing under it`)
    c.set(x, y, z, stone)
  }
  for (const [x, y, z] of caps) c.set(x, y, z, capSlab, { type: 'bottom', waterlogged: 'false' })
}
