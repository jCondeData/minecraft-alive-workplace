// Camps: the Camp Cook's pieces (parts library, tools/blueprints/REBUILD.md; grown from the Camp Kitchen, 49): the
// Campfire Pot and a raised, watered grain bed. Every block comes from the caller's palette; none is picked here
// except the pot itself, which is a fixed job block (cobblemon:campfire, its pot fitted by the builder).

/** campPot: Cobblemon's Campfire Pot at pos (`cobblemon:campfire`, lid off): the Camp Cook's job block. The builder
 *  places a campfire and fits the pot on it; keep a standing spot within 3 blocks and the cook's chests within 8. */
export function campPot(c, [x, y, z], facing = 'north', id = 'cobblemon:campfire') {
  c.free([x, y, z])
  c.set(x, y, z, id, { facing, lid: 'false' })
}

/** grainBed: a raised stone bed x0..x1, z0..z1 with its soil one block up: at y 0 `bed` under the water channel and
 *  `soil` under the crop, the rim (`rim`) round the edge at y 0 and 1 (flush with the soil), coped with `cap` slabs at y 2 when given; at y 1 farmland
 *  (moist) on every inside cell but the channel (x = channelX, the full inside depth), which holds water sources shut
 *  in by farmland, the rim and the bed below; ripe `grain` (a two-high crop, age 6, lower and upper) on every
 *  farmland cell. Every farmland cell must lie within 4 of the channel across x (vanilla's hydration reach): it
 *  throws otherwise. Returns the farmland cells [x, z]. */
export function grainBed(c, { x0, x1, z0, z1, channelX }, { rim, cap, bed, soil, grain, age = 6 }) {
  const farm = []
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const edge = x === x0 || x === x1 || z === z0 || z === z1
    if (edge) {
      c.set(x, 0, z, rim); c.set(x, 1, z, rim)
      if (cap) c.set(x, 2, z, cap, { type: 'bottom', waterlogged: 'false' })
      continue
    }
    if (x === channelX) { c.set(x, 0, z, bed); c.set(x, 1, z, 'water', { level: '0' }); continue }
    if (Math.abs(x - channelX) > 4) throw new Error(`farmland at ${x},${z} is out of the channel's reach`)
    c.set(x, 0, z, soil)
    c.set(x, 1, z, 'farmland', { moisture: '7' })
    c.set(x, 2, z, grain, { age: String(age), half: 'lower' })
    c.set(x, 3, z, grain, { age: String(age), half: 'upper' })
    farm.push([x, z])
  }
  return farm
}
