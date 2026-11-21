// The Lighthouse (Wonder), the recipe: assembles plan.mjs from the parts library (../parts) in passes (block-out and
// structure, openings, depth and detail, interior, ground, light), one stage at a time. draw(n) is the whole Wonder
// once stage n is built; each stage only adds to the one before (checked at the end). STAGE=<1..4> picks the stage the
// runner writes (default 4); CUT=<y> builds a cutaway with everything above y removed. stages.py saves all four.
import { SIZE, TOWER, LEVELS, PALETTE, MASSES, OPENINGS, FURNITURE } from './plan.mjs'
import {
  Canvas, mix, stair, slab, plinth, floor, post, beamRing, wallPanel, window, door, gableRoof, gableWall, bargeTrim,
  furnitureParts, roundCells, shellCells, railCells, towardCentre, corbelCourse, weathering, lanternRoom, causeway, bollard,
  dryingRack, wallRing, spiralStair, lampHood,
} from '../parts/index.mjs'

const { cx, cz } = TOWER
const key = (x, z) => `${x},${z}`
const inSet = cells => { const s = new Set(cells.map(([x, z]) => key(x, z))); return (x, z) => s.has(key(x, z)) }
const interior4 = (() => { const shell = inSet(shellCells(cx, cz, TOWER.shaft)); return roundCells(cx, cz, TOWER.shaft).filter(([x, z]) => !shell(x, z)) })()
const isInterior = inSet(interior4)
// The winding stair's ring: the 24 wall cells of the room, started by the door at (cx + 1, cz - 3) and walked east,
// so the doorway's own cell (cx, cz - 3) is the ring's last, passed overhead at y 27.
const RING = (() => {
  let r = wallRing(interior4, [cx + 1, cz - 3])
  const i = r.findIndex(([x, z]) => x === cx + 1 && z === cz - 3)
  r = [...r.slice(i), ...r.slice(0, i)]
  if (!(r[1][0] === cx + 1 && r[1][1] === cz - 2)) r = [r[0], ...r.slice(1).reverse()]
  return r
})()

// The tower's window columns: the pane sits in the outside face, the cells between it and the room are opened.
const WINDOW_AT = { north: [cx, cz - 1, 0, -1], south: [cx, cz + 1, 0, 1], west: [cx - 1, cz, -1, 0], east: [cx + 1, cz, 1, 0] }

const radiusAt = y => (y <= LEVELS.footTop ? TOWER.foot : y <= LEVELS.waistTop ? TOWER.waist : TOWER.shaft)

function tower(c, P, y0, y1) {
  const L = LEVELS
  const sea = mix(P.sea, 3), waist = mix(P.waist, 4), lime = mix(P.limewash, 5)
  const solid = r => r === TOWER.shaft ? shellCells(cx, cz, r) : roundCells(cx, cz, r).filter(([x, z]) => !isInterior(x, z))
  const winRows = new Set(L.windows.flat())
  const door = OPENINGS.towerDoor.at
  for (let y = Math.max(y0, L.deck + 1); y <= y1; y++) {
    const r = radiusAt(y)
    const body = r === TOWER.foot ? sea : r === TOWER.waist ? waist : lime
    const course = r !== TOWER.foot && (L.floors.includes(y) || L.bands.includes(y))
    for (const [x, z] of solid(r)) {
      if (x === door[0] && z < cz && (y === L.deck + 1 || y === L.deck + 2)) continue // the doorway through the wall
      c.set(x, y, z, course ? (r === TOWER.shaft ? P.band : P.course) : body)
    }
    // windows: a pane in the outer face, the reveal behind it open to the room
    if (winRows.has(y)) for (const side of OPENINGS.towerWindows) {
      const [, , dx, dz] = WINDOW_AT[side]
      c.set(cx + dx * r, y, cz + dz * r, P.glass)
      for (let k = r - 1; k >= 1; k--) { const ix = cx + dx * k, iz = cz + dz * k; if (isInterior(ix, iz)) break; c.clear(ix, y, iz) }
      // round 2 (the plain drum): each window framed proud of the wall, a dark sill under it and a drip hood over it,
      // one cell out, so every landing reads on the outside as a lit opening with a shadow line (skipped where a ledge,
      // a corbel or the doorway already holds the cell)
      const ox = cx + dx * (r + 1), oz = cz + dz * (r + 1), out = towardCentre(ox, oz, cx, cz)
      const low = !winRows.has(y - 1), high = !winRows.has(y + 1)
      if (low && y - 1 > L.deck + 2 && !c.get(ox, y - 1, oz)) slab(c, ox, y - 1, oz, `${P.band}_slab`, 'top')
      if (high && y + 1 < L.shaftTop - 1 && !c.get(ox, y + 1, oz)) stair(c, ox, y + 1, oz, `${P.band}_stairs`, out, 'top')
    }
  }
  // the floors and the stair winding up the inside wall through them, to the gallery floor
  spiralStair(c, RING, interior4, L.stairFrom, L.galleryY, L.floors, { stairs: P.stair, floorBlock: P.floor }, Math.max(y0, L.deck + 1), Math.min(y1, L.galleryY - 1))
}
// the ring cells the stair needs open through the gallery floor (the two steps under it)
const stairAt = y => RING[((y - LEVELS.stairFrom) % RING.length + RING.length) % RING.length]

// draw(n): the Wonder as it stands after stage n. A later stage never replaces a block an earlier one laid (52.6: a
// builder would have to break finished work; the cottage's floor, the landings' boards and the quay's paving are laid
// over stage 1's ground), so every block of stage n is the block the finished Wonder has there: the footprints of the
// stage-2 floors are laid with stage 1's ground.
export function draw(stage = 4) {
  const c = drawn(stage)
  if (stage < 4) {
    const whole = drawn(4)
    for (const k of c.cells.keys()) if (whole.cells.has(k)) c.cells.set(k, whole.cells.get(k))
  }
  return c
}

function drawn(stage) {
  const c = new Canvas(SIZE)
  const P = PALETTE, L = LEVELS
  const sea = mix(P.sea, 3), quayMix = mix(P.quay, 4), deckMix = mix(P.deck, 6), plinthMix = mix(P.plinth, 8)
  const M = MASSES

  // === Stage I: the Quay, the Mole and the Bastion ===============================================================
  // --- Pass 1: block-out ---
  floor(c, M.quay.x0, M.quay.z0, M.green.x0 - 1, M.quay.z1, 0, quayMix)
  // the quay's east end: grass, a gravel path from the front to the ferry landing
  for (let x = M.green.x0; x <= M.green.x1; x++) for (let z = M.green.z0; z <= M.green.z1; z++)
    c.set(x, 0, z, x === M.green.path ? 'dirt_path' : (x * 5 + z * 3) % 7 === 0 ? 'coarse_dirt' : 'grass_block', x === M.green.path || (x * 5 + z * 3) % 7 === 0 ? {} : { snowy: 'false' })
  const mole = M.mole
  const gap = (x, z) => (x === mole.x1 && mole.gaps.east.includes(z)) || (x === mole.x0 && mole.gaps.west.includes(z))
  causeway(c, { x0: mole.x0, x1: mole.x1, z0: mole.z0 + L.deck, z1: mole.z1, deckY: mole.deckY }, { body: sea, deck: deckMix, wall: P.coping }, gap)
  // the bastion: solid to the deck, a parapet round its rim but where the mole comes in
  const bast = roundCells(cx, cz, TOWER.bastion)
  const rim = inSet(shellCells(cx, cz, TOWER.bastion))
  for (const [x, z] of bast) {
    for (let y = 0; y < L.deck; y++) c.set(x, y, z, sea)
    c.set(x, L.deck, z, rim(x, z) ? sea : deckMix)
    if (!rim(x, z) || (z < cz && x > mole.x0 && x < mole.x1)) continue
    // the wave wall: on the sea side (behind the tower) a solid course under the coping, so the swell breaks on it
    if (z > cz - 3) { c.set(x, L.deck + 1, z, sea); c.set(x, L.deck + 2, z, P.coping) } else c.set(x, L.deck + 1, z, P.coping)
  }
  for (const x of [mole.x0, mole.x1]) c.set(x, L.deck + 1, mole.z1 + 1, P.coping) // the parapets meet the bastion's
  // the mole's mouth: a flight of stairs up from the quay, cheeked by the parapet's ends
  for (let i = 0; i < L.deck; i++) {
    const z = mole.z0 + i
    for (let x = mole.x0; x <= mole.x1; x++) for (let y = 0; y <= i; y++) c.set(x, y, z, sea)
    for (let x = OPENINGS.moleStep.x0; x <= OPENINGS.moleStep.x1; x++) stair(c, x, i + 1, z, 'stone_brick_stairs', 'south')
    for (const x of [mole.x0, mole.x1]) { c.set(x, i + 1, z, sea); c.set(x, i + 2, z, P.coping) }
  }
  // rip-rap: tumbled boulders round the bastion's foot and along the mole's flanks, two high where the sea breaks
  const rip = mix(P.riprap, 9), bastIn = inSet(bast)
  const hash = (x, z) => ((x * 7349 + z * 9151) ^ (x * z * 31)) & 7
  for (const [x, z] of roundCells(cx, cz, TOWER.riprap)) {
    if (bastIn(x, z) || !c.inside(x, 0, z) || z < mole.z0 + 1) continue
    if (z <= mole.z1 && (x < mole.x0 - 2 || x > mole.x1 + 2)) continue // only a strip along the mole
    if (c.get(x, 0, z)) continue
    const d2 = (x - cx) ** 2 + (z - cz) ** 2
    const near = d2 <= (TOWER.bastion + 1) ** 2 + 4 || (z <= mole.z1 && Math.abs(x - 12) === 4)
    c.set(x, 0, z, rip)
    if (near || hash(x, z) < 3) c.set(x, 1, z, rip)
    if (near && hash(x, z) < 4) c.set(x, 2, z, rip) // the big stones heaped against the wall
  }
  // the tower's first courses on the bastion
  tower(c, P, 4, 6)
  if (stage === 1) return finish(c, stage)

  // === Stage II: the Keeper's Cottage, the landings and the tower's foot ===========================================
  tower(c, P, 7, 18)
  door(c, OPENINGS.towerDoor.at, OPENINGS.towerDoor.facing, OPENINGS.towerDoor.out, { wood: P.wood })
  weathering(c, cx, cz, TOWER.foot, TOWER.waist, L.ledges[0], P.ledge)

  // Cottage: plinth, floor, frame proud of limewash infill, beam ring, slate roof
  const K = M.cottage
  const lime = mix(P.limewash, 5)
  plinth(c, K.x0, K.z0, K.x1, K.z1, plinthMix)
  floor(c, K.x0 + 1, K.z0 + 1, K.x1 - 1, K.z1 - 1, 0, P.floor)
  for (const [ax, az, bx, bz] of [[K.x0, K.z0, K.x1, K.z0], [K.x0, K.z1, K.x1, K.z1], [K.x0, K.z0, K.x0, K.z1], [K.x1, K.z0, K.x1, K.z1]])
    wallPanel(c, [ax, az], [bx, bz], 1, K.topY - 1, lime)
  for (const [x, z] of [[K.x0, K.z0], [K.x1, K.z0], [K.x0, K.z1], [K.x1, K.z1]]) post(c, x, z, 1, K.topY - 1, P.frame)
  for (const y of [K.beamY, K.topY]) {
    beamRing(c, K.x0, K.z0, K.x1, K.z1, y, P.frame)
    floor(c, K.x0 + 1, K.z0 + 1, K.x1 - 1, K.z1 - 1, y, P.floor) // the loft's floor, and its ceiling
  }
  for (let y = 1; y <= K.beamY; y++) c.set(K.ladder[0], y, K.ladder[1], 'ladder', { facing: 'north', waterlogged: 'false' })
  const roof = gableRoof(c, K.roof, P.roof)
  for (const xl of [K.x0, K.x1]) gableWall(c, 'x', xl, K.roof.across, K.topY + 1, roof.under, (u, y) => {
    if (u === 5 && y === K.topY + 2) return P.glass // a little attic window in each gable
    if (u === 5) return [P.frame, { axis: 'y' }] // the king post
    return lime
  })
  bargeTrim(c, 'x', K.roof.along[0], K.roof.across, roof.under, `${P.wood}_stairs`)
  bargeTrim(c, 'x', K.roof.along[1], K.roof.across, roof.under, `${P.wood}_stairs`)
  door(c, OPENINGS.cottageDoor.at, OPENINGS.cottageDoor.facing, OPENINGS.cottageDoor.out, { wood: P.wood })
  for (const w of OPENINGS.cottageWindows) window(c, w.cells.map(([dx, y, dz]) => [K.x0 + dx, y, K.z0 + dz]), w.out, 'shutters', { glass: P.glass, wood: P.wood })
  // a chimney stack up the east gable, a campfire's smoke out of its pot
  for (let y = 1; y <= roof.ridgeY; y++) c.set(K.x0 - 1, y, 6, plinthMix)
  c.set(K.x0 - 1, roof.ridgeY + 1, 6, 'campfire', { lit: 'true', facing: 'north', signal_fire: 'false', waterlogged: 'false' })
  // a lantern on a post beside the cottage door
  c.set(OPENINGS.cottageDoor.at[0] + 1, 1, 1, `${P.wood}_fence`)
  c.set(OPENINGS.cottageDoor.at[0] + 1, 2, 1, 'lantern', { hanging: 'false', waterlogged: 'false' })

  // Ferry Landing: a stone stage a step below the mole, kerbed, the Travel Post at its sea corner, bollards
  const LD = M.landing
  for (let x = LD.x0; x <= LD.x1; x++) for (let z = LD.z0; z <= LD.z1; z++) { c.set(x, 0, z, sea); c.set(x, 1, z, (x === LD.x1 || z === LD.z0 || z === LD.z1) ? sea : deckMix) }
  for (const z of mole.gaps.east) stair(c, LD.x0, 2, z, 'stone_brick_stairs', 'west')
  for (const z of [LD.z0, LD.z1]) bollard(c, LD.x1, 2, z, { post: 'stone_brick_wall', cap: 'polished_andesite_slab' })
  c.set(LD.x1, 2, LD.z0 + 1, `${P.wood}_fence`)
  c.set(LD.x1, 3, LD.z0 + 1, 'lantern', { hanging: 'false', waterlogged: 'false' })
  stair(c, M.green.path, 1, LD.z0 - 1, 'stone_brick_stairs', 'south') // the path's step up onto the landing
  // the Ferry Jetty: spruce boards on log piles out to the plot's east edge, the Travel Post at its end, sea round it
  const J = M.jetty
  for (let x = J.x0; x <= J.x1; x++) for (let z = J.z0; z <= J.z1; z++) {
    if ((x === J.x0 + 1 || x === J.x1) && z !== J.z0 + 1) c.set(x, 0, z, P.frame, { axis: 'y' })
    c.set(x, 1, z, P.floor)
  }
  c.set(...J.post, 'aliveworkplace:travel_post', { facing: 'east' })
  for (const z of [J.z0, J.z1]) bollard(c, J.x1, 2, z, { post: `${P.wood}_fence`, cap: `${P.wood}_slab` })
  c.set(J.x0 + 1, 2, J.z0, `${P.wood}_fence`); c.set(J.x0 + 1, 3, J.z0, 'lantern', { hanging: 'false', waterlogged: 'false' })
  // the Net Shed on the green: four posts, a slab roof, a boarded back, the boat gear in an empty chest, hay for the floats
  const N = M.netShed
  for (const [x, z] of [[N.x0, N.z0], [N.x1, N.z0], [N.x0, N.z1], [N.x1, N.z1]]) post(c, x, z, 1, 3, P.frame)
  for (let x = N.x0; x <= N.x1; x++) for (let z = N.z0; z <= N.z1; z++) slab(c, x, 4, z, `${P.wood}_slab`, 'bottom')
  for (let z = N.z0 + 1; z < N.z1; z++) for (let y = 1; y <= 3; y++) c.set(N.x1, y, z, P.floor)
  furnitureParts.chest(c, { pos: [N.x1 - 1, 1, N.z0 + 1], facing: 'west', block: 'chest' })
  c.set(N.x1 - 1, 1, N.z1 - 1, 'hay_block', { axis: 'x' })
  c.set(N.x1 - 1, 2, N.z1 - 1, 'hay_block', { axis: 'z' })
  furnitureParts.hangingLamp(c, { pos: [N.x0 + 2, 3, N.z0 + 2], facing: 'north', wood: P.wood })

  // Fish Stage: spruce boards on the ground, a step down off the mole, the fisherman's barrel, a drying rack
  const F = M.fishStage
  for (let x = F.x0; x <= F.x1; x++) for (let z = F.z0; z <= F.z1; z++) { c.set(x, 0, z, sea); c.set(x, 1, z, x === F.x0 ? P.frame : P.floor, x === F.x0 ? { axis: 'z' } : {}) }
  for (const z of mole.gaps.west) stair(c, F.x1, 2, z, 'stone_brick_stairs', 'east')
  furnitureParts.pantry(c, { pos: [F.x1 - 1, 2, F.z0], block: 'barrel' }) // the fisherman's job site
  dryingRack(c, { axis: 'z', line: F.x0 + 1, span: [F.z0 + 2, F.z1], posts: [F.z0 + 2, F.z1], bundles: [F.z0 + 3, F.z1 - 1], y0: 2, top: 4 },
    { post: P.frame, beam: 'stripped_spruce_log', hang: 'dried_kelp_block' })
  if (stage === 2) return finish(c, stage)

  // === Stage III: the Shaft and the Gallery ======================================================================
  tower(c, P, 19, L.shaftTop)
  weathering(c, cx, cz, TOWER.waist, TOWER.shaft, L.ledges[1], P.ledge)
  // the gallery: two corbel courses carry its floor out two cells past the shaft
  corbelCourse(c, cx, cz, TOWER.gallery - 1, TOWER.shaft, L.shaftTop - 1, P.corbel)
  corbelCourse(c, cx, cz, TOWER.gallery, TOWER.shaft, L.shaftTop, P.corbel)
  const galleryHoles = inSet([stairAt(L.galleryY - 1), stairAt(L.galleryY - 2)])
  for (const [x, z] of roundCells(cx, cz, TOWER.gallery)) if (!galleryHoles(x, z)) c.set(x, L.galleryY, z, P.galleryFloor)
  spiralStair(c, RING, interior4, L.stairFrom, L.galleryY, [], { stairs: P.stair, floorBlock: P.floor }, L.galleryY, L.galleryY) // the last step, into the lantern
  const galleryIn = inSet(roundCells(cx, cz, TOWER.gallery))
  for (const [x, z] of railCells(cx, cz, TOWER.gallery)) {
    if (!galleryIn(x, z)) { c.set(x, L.galleryY, z, P.galleryFloor); stair(c, x, L.galleryY - 1, z, P.corbel, towardCentre(x, z, cx, cz), 'top') }
    c.set(x, L.galleryY + 1, z, P.rail)
  }
  // where the rail steps round the circle, a stone stanchion in the outer corner of each step: both runs join it
  const railIn = inSet(railCells(cx, cz, TOWER.gallery))
  const R = TOWER.gallery + 1
  for (let x = cx - R; x <= cx + R; x++) for (let z = cz - R; z <= cz + R; z++) {
    if (railIn(x, z)) continue
    if (!(railIn(x - 1, z) || railIn(x + 1, z)) || !(railIn(x, z - 1) || railIn(x, z + 1))) continue
    if (!galleryIn(x, z)) {
      c.set(x, L.galleryY, z, P.galleryFloor)
      stair(c, x, L.galleryY - 1, z, P.corbel, towardCentre(x, z, cx, cz), 'top')
    }
    c.set(x, L.galleryY + 1, z, P.course)
  }
  if (stage === 3) return finish(c, stage)

  // === Stage IV: the Lantern and its Hood ========================================================================
  lanternRoom(c, cx, cz, TOWER.lantern, L.lanternY, L.glassH, { sill: P.copper.cut, post: P.copper.post, glass: P.glass })
  door(c, OPENINGS.lanternDoor.at, OPENINGS.lanternDoor.facing, OPENINGS.lanternDoor.out, { wood: P.copper.door })
  c.set(cx, L.lanternY, cz, P.pedestal)
  c.set(cx, L.lanternY + 1, cz, 'sea_lantern')
  c.set(cx, L.lanternY + 2, cz, 'sea_lantern')
  // the hood: a cap over the glass, an eave of night lamps one cell out over the gallery walk, a stepped copper roof
  const lamps = lampHood(c, cx, cz, TOWER.lantern, L.crownY,
    { cap: P.copper.cut, corner: P.copper.post, stairs: P.copper.stairs, slab: 'waxed_oxidized_cut_copper_slab' },
    ['lightning_rod', { facing: 'up', powered: 'false', waterlogged: 'false' }])

  // lamp posts along the mole and round the bastion (a lantern on a parapet post)
  const up = L.deck + 2
  for (const z of [mole.z0 + 2, mole.z1 - 1]) for (const x of [mole.x0, mole.x1]) if (c.name(x, up - 1, z).endsWith('wall')) c.set(x, up, z, 'lantern', { hanging: 'false', waterlogged: 'false' })
  for (const [x, z] of [[4, 24], [20, 24], [12, 32]]) for (const y of [up, up + 1]) if (c.name(x, y - 1, z).endsWith('wall')) c.set(x, y, z, 'lantern', { hanging: 'false', waterlogged: 'false' })

  // --- Interior: the furniture of every room, the oil casks, and a lamp under every floor ---
  for (const f of FURNITURE) furnitureParts[f.part](c, f)
  for (const dz of [-1, 0, 1]) c.set(cx + 3, 13, cz + dz, 'stripped_spruce_log', { axis: 'x' }) // lamp-oil casks, racked
  for (const dz of [-1, 1]) slab(c, cx + 3, 14, cz + dz, `${P.wood}_slab`, 'bottom')
  for (const y of [...L.floors, L.galleryY]) furnitureParts.hangingLamp(c, { pos: [cx, y - 1, cz], facing: 'north', wood: P.wood })
  c.lamps = lamps
  return finish(c, stage)
}

function finish(c, stage) { c.stage = stage; return c }

// The brief's checks, run on every stage: each only adds, everything joins the ground, the counts and the night light.
export function verify() {
  const seen = new Map()
  const counts = []
  for (let n = 1; n <= 4; n++) {
    const c = draw(n)
    for (const [k, v] of seen) if (!c.cells.has(k)) throw new Error(`stage ${n} takes away ${v.id} at ${k}`)
    for (const [k, v] of seen) if (c.cells.get(k).id !== v.id) throw new Error(`stage ${n} replaces ${v.id} at ${k} with ${c.cells.get(k).id}`)
    // grounded: flood from y 0 through face, edge and corner neighbours
    const reach = new Set([...c.cells.keys()].filter(k => k.split(',')[1] === '0'))
    const todo = [...reach]
    while (todo.length) {
      const [x, y, z] = todo.pop().split(',').map(Number)
      for (let dx = -1; dx <= 1; dx++) for (let dy = -1; dy <= 1; dy++) for (let dz = -1; dz <= 1; dz++) {
        const k = `${x + dx},${y + dy},${z + dz}`
        if (c.cells.has(k) && !reach.has(k)) { reach.add(k); todo.push(k) }
      }
    }
    const loose = [...c.cells.keys()].filter(k => !reach.has(k))
    if (loose.length) throw new Error(`stage ${n}: ${loose.length} blocks not joined to the ground, e.g. ${loose.slice(0, 3)}`)
    for (const [k, v] of c.cells) seen.set(k, v)
    counts.push(c.cells.size)
  }
  const c = draw(4)
  const ids = [...c.cells.values()].map(v => v.id)
  const n = id => ids.filter(i => i === `minecraft:${id}` || i === id).length
  const total = c.cells.size
  console.error(`whole: ${total}`)
  if (total < 4500 || total > 5500) throw new Error(`block count ${total}, not 4,500 to 5,500`)
  if (n('aliveworkplace:travel_post') !== 1) throw new Error('not exactly one Travel Post')
  if (n('barrel') < 1 || n('sea_lantern') < 1 || n('redstone_lamp') < 8 || n('daylight_detector') < 1) throw new Error('missing a must-have')
  if (ids.filter(i => i.endsWith('_bed')).length !== 2) throw new Error('not one whole bed')
  if (ids.some(i => i.includes('calcite'))) throw new Error('calcite')
  for (const [k, v] of c.cells) if (v.id === 'minecraft:daylight_detector' && v.props.inverted !== 'true') throw new Error(`detector at ${k} not inverted`)
  // every lamp is powered from a detector beside it and from nothing else that gives a signal
  const sources = /(redstone_torch|redstone_block|lever|_button|_pressure_plate|redstone_wire|repeater|comparator|observer|target|lightning_rod|daylight_detector)$/
  for (const [k, v] of c.cells) {
    if (v.id !== 'minecraft:redstone_lamp') continue
    const [x, y, z] = k.split(',').map(Number)
    const near = [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]].map(([a, b, d]) => c.get(x + a, y + b, z + d)).filter(Boolean)
    if (!near.some(b => b.id === 'minecraft:daylight_detector')) throw new Error(`lamp at ${k} has no detector`)
    if (near.some(b => sources.test(b.id) && b.id !== 'minecraft:daylight_detector')) throw new Error(`lamp at ${k} has another power source`)
    // the detector must see the sky: nothing above it
    for (let yy = y + 2; yy < c.size[1]; yy++) if (c.get(x, yy, z)) throw new Error(`detector over ${k} is roofed`)
  }
  return counts
}

export default function build(api) {
  const counts = verify()
  console.error(`stage totals: ${counts.join(', ')}`)
  const stage = Number(process.env.STAGE ?? 4)
  const c = draw(stage)
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const name = `lighthouse_s${stage}` + (cut !== undefined ? `_cut${cut}` : '')
  return { structure: canvas.toStructure(api), metadata: { name } }
}
