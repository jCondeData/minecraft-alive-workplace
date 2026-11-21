// Press works (parts library, tools/blueprints/REBUILD.md; grown from the Workshop, ROADMAP 49 / 50.7): Create's
// mechanical press standing over its depot, and a hooped head tank on a tower top whose spout box holds the ONE water
// source a builder pours (the fall below it is fallRace's, clock_tower_drive.mjs), and a weathered plinth skirt. Create ids and states checked in
// libs/create-fabric-6.0.10.0+mc1.21.1.jar: the press turns about its `facing` axis and takes a shaft (or a cogwheel's
// axle) only on the two faces along it; it works on a create:depot exactly two blocks below it, the cell between
// left empty for its head. Every other block comes from the family's palette.

const CREATE = id => `create:${id}`

/** pressOverDepot: a mechanical press at [x, y, z] turning about `facing`'s axis, the cell under it cleared for its
 *  head, a depot two below it. Drive it with a shaft or a cogwheel on that axis beside it. */
export function pressOverDepot(c, [x, y, z], facing) {
  c.set(x, y, z, CREATE('mechanical_press'), { facing })
  c.clear(x, y - 1, z)
  c.set(x, y - 2, z, CREATE('depot'))
}

/** spoutTank: a hooped stave tank on a tower top (x0..x1, z0..z1, y0..y1: upright `stave` logs, a `hoop` ring at each
 *  y of `hoops`), a low pyramid cap of `cap` stairs round a full block with a `finial`, and on one face at y1 a spout
 *  box of `spoutId` blocks (`box`: [x, z] cells) round the pour cell `pour` [x, z], which stays open above and is left
 *  for the caller's water. `corbels`: [{at: [x, z], facing}] upside-down `corbel` stairs under the box's free cells. */
export function spoutTank(c, { x0, z0, x1, z1, y0, y1, hoops = [], box, pour, corbels = [] }, { stave, hoop, cap, finial = null, spoutId, corbel }) {
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const ring = x === x0 || x === x1 || z === z0 || z === z1
    c.set(x, y, z, ring && hoops.includes(y) ? hoop : stave, ring && hoops.includes(y) ? {} : { axis: 'y' })
  }
  const mx = (x0 + x1) / 2, mz = (z0 + z1) / 2
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const dx = x - mx, dz = z - mz
    if (dx === 0 && dz === 0) { c.set(x, y1 + 1, z, cap.full); continue }
    const facing = Math.abs(dz) >= Math.abs(dx) ? (dz < 0 ? 'south' : 'north') : (dx < 0 ? 'east' : 'west')
    c.set(x, y1 + 1, z, cap.stairs, { facing, half: 'bottom', shape: 'straight', waterlogged: 'false' })
  }
  if (finial) c.set(Math.round(mx), y1 + 2, Math.round(mz), finial)
  for (const [x, z] of box) c.set(x, y1, z, spoutId)
  for (const { at: [x, z], facing } of corbels) c.set(x, y1 - 1, z, corbel, { facing, half: 'top', shape: 'straight', waterlogged: 'false' })
  c.clear(pour[0], y1 + 1, pour[1]) // open above: the bucket pours from there
}

/** weatheredSkirt: a plinth course one block out from a wall face, between its proud posts: a full `stone` block (a
 *  mix() welcome) at y and a `stairs` weathering on it at y + 1, rising toward the wall (`toward`), so the dado's foot
 *  reads thick and every window over it has a sill to stand on. cells: [x, z] outside the wall. */
export function weatheredSkirt(c, cells, y, stone, stairs, toward) {
  for (const [x, z] of cells) {
    c.set(x, y, z, stone)
    c.set(x, y + 1, z, stairs, { facing: toward, half: 'bottom', shape: 'straight', waterlogged: 'false' })
  }
}
