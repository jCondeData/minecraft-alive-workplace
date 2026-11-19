// Strata and specimens (parts library, tools/blueprints/REBUILD.md; grown from the Research Lab, 49): walls laid in
// bands like a cliff's section, a pointed window in a gable, a fossil skeleton of bone, a specimen on its stand, a
// dig bed in a cellar floor, and lightning-rod finials. Every block comes from the caller's palette.
/** strataBands: a block picker by height: `bands` is [[fromY, id or mix()], ...] in rising order; a cell takes the
 *  last band whose fromY is at or under its y (a mix() is asked with the cell). Pass it anywhere a part takes an id. */
export function strataBands(bands) {
  return (x, y, z) => {
    let pick = bands[0][1]
    for (const [from, id] of bands) if (y >= from) pick = id
    return typeof pick === 'function' ? pick(x, y, z) : pick
  }
}

/** pointedLight: a three-light pointed window in a wall (`axis` 'x': the wall runs along x on line z = `line`; 'z':
 *  along z on line x), centred on u = `cu`: panes in the three lights from y0 to y1, the middle light's point one
 *  higher (`apex`: a whole glass block, since a pane between two stair heads joins nothing), the sides' heads closed
 *  by upside-down `head` stairs leaning in toward the middle, and a `key` block over the point (a keystone). */
export function pointedLight(c, axis, line, cu, y0, y1, glass, head, key, apex = glass) {
  const at = (u, y, id, props) => (axis === 'x' ? c.set(u, y, line, id, props) : c.set(line, y, u, id, props))
  for (const u of [cu - 1, cu, cu + 1]) for (let y = y0; y <= y1; y++) at(u, y, glass)
  at(cu, y1 + 1, apex)
  const out = axis === 'x' ? ['west', 'east'] : ['north', 'south']
  at(cu - 1, y1 + 1, head, { facing: out[0], half: 'top', shape: 'straight', waterlogged: 'false' })
  at(cu + 1, y1 + 1, head, { facing: out[1], half: 'top', shape: 'straight', waterlogged: 'false' })
  at(cu, y1 + 2, key)
}

/** boneFrame: a skeleton drawn bone by bone: each of `bones` is [x, y, z, axis]: a heavy bone (`bone`, a pillar block
 *  laid along its grain, axis 'x', 'y' or 'z') or, with axis 'thin', a slender one (`thin`, a wall or fence block that
 *  joins its neighbours: legs and ribs). Draw it face to face from the feet up, so the whole frame stands on its legs. */
export function boneFrame(c, bones, bone, thin = bone) {
  for (const [x, y, z, axis] of bones) {
    c.free([x, y, z])
    if (axis === 'thin') c.set(x, y, z, thin)
    else c.set(x, y, z, bone, { axis: axis ?? 'y' })
  }
}

/** specimenStand: a specimen on a stand: `stand` at [x, y, z] and the piece on it (`piece`, with its `props`). A
 *  fixture, no furniture spot (a museum's pedestal: a furniture mod has nothing to swap in). */
export function specimenStand(c, [x, y, z], stand, piece, props = {}) {
  c.free([x, y, z], [x, y + 1, z])
  c.set(x, y, z, stand)
  c.set(x, y + 1, z, piece, props)
}

/** digBed: an excavation in a floor at y over x0..x1, z0..z1: the bed (`bed`, a mix() of loose ground) with the finds
 *  (`finds`: [x, y, z, axis] bones lying in it, or standing a block proud of it) set as `bone`. */
export function digBed(c, { x0, z0, x1, z1, y }, bed, finds, bone) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) c.set(x, y, z, bed)
  for (const [x, yy, z, axis] of finds) c.set(x, yy, z, bone, { axis: axis ?? 'x' })
}

/** rodFinials: lightning rods standing up on a ridge or a rib's crown (`rod`: the rod's id), one per [x, y, z]. */
export function rodFinials(c, cells, rod) {
  for (const [x, y, z] of cells) c.set(x, y, z, rod, { facing: 'up', powered: 'false', waterlogged: 'false' })
}
