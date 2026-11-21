// Farmstead, desert look (49.10e), the recipe: assembles plan.mjs from the parts library (../parts) in passes
// (block-out and structure, roofs, openings, depth and detail, interior, cellar, ground and field) for LEVEL = 1..3, each
// level the one before plus what it adds (II the passage, the granary, the second field; III the cool store under the
// hall and its hatch, nothing else above ground). Writes furniture.json and, for III, out/cellar.txt. Build: build.sh.
// CUT=<y> cuts everything above y.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  SIZE, BASEMENT, IDS, PALETTE, HOUSE, PORTAL, BENCHES, FLUE, DOVECOTE, GALLERY, SHADUF, PASSAGE, GRANARY, BARN, CELLAR,
  OPENINGS, FURNITURE, CROPS, FIELD, FIELD2, OUTSIDE, SCARECROW_Z,
} from './plan.mjs'
import {
  LiftedCanvas, mix, stair, slab, plinth, floor, wallPanel, window, door, chimney, railingRun, furnitureParts, cellar,
  hatchLadder, rowParapet,
} from '../parts/index.mjs'
import { fieldPlot, kerbFence, scarecrow, composter } from '../parts/farm_fields.mjs'
import { dovecote, shaduf, grainBin, shade, screen, palmCrown } from '../parts/farmstead_desert_farm.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const pot = (c, x, y, z) => c.set(x, y, z, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })
const lantern = (c, x, y, z, hanging) => c.set(x, y, z, 'lantern', { hanging: String(hanging), waterlogged: 'false' })

export function drawLevel(level) {
  const c = new LiftedCanvas(SIZE[level], BASEMENT[level] ?? 0)
  const P = PALETTE, H = HOUSE, G = GRANARY, B = BARN
  const two = level >= 2
  const has = o => (o.level ?? 1) <= level && level <= (o.until ?? 9)
  const mud = mix(P.mud, 6), ashlar = mix(P.ashlar, 5)
  const bz = PASSAGE.z
  const galleryEnd = GALLERY.z1[two ? 2 : 1]

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // The house: a smooth plinth, sandstone flags inside, mud-brick walls up to the ceiling course, the hearthstone
  plinth(c, H.x0, H.z0, H.x1, H.z1, P.plinth)
  floor(c, H.x0 + 1, H.z0 + 1, H.x1 - 1, H.z1 - 1, 0, P.floor)
  for (let z = 4; z <= 6; z++) c.set(2, 0, z, P.course) // the hearthstone
  const ring = (x0, z0, x1, z1) => [[[x0, z0], [x1, z0]], [[x0, z1], [x1, z1]], [[x0, z0], [x0, z1]], [[x1, z0], [x1, z1]]]
  for (const [a, b] of ring(H.x0, H.z0, H.x1, H.z1)) wallPanel(c, a, b, 1, H.deckY, mud)
  // the ceiling: spruce vigas across x with cane matting between, the roof deck over it
  for (let z = H.z0 + 1; z < H.z1; z++) for (let x = H.x0 + 1; x < H.x1; x++)
    c.set(x, H.ceilingY, z, ...(H.vigaZ.includes(z) ? [P.viga, { axis: 'x' }] : [P.matting]))
  c.fill(H.x0 + 1, H.deckY, H.z0 + 1, H.x1 - 1, H.deckY, H.z1 - 1, P.deck)

  // The gallery: a beaten-earth walk along the field side, a step up at the front
  for (let z = GALLERY.z0; z <= galleryEnd; z++) c.set(GALLERY.x, 0, z, P.gallery)
  stair(c, ...OPENINGS.galleryStep, P.step, 'south')
  // The back flags (I) / the passage's floor (II), a step from the west
  for (let x = H.x0; x <= H.x1; x++) c.set(x, 0, bz, P.plinth)
  stair(c, 0, 0, bz, P.step, 'east')

  // The granary (II): coursed sandstone to the loft floor, a cut course at it, mud brick above, its deck
  if (two) {
    plinth(c, G.x0, G.z0, G.x1, G.z1, P.plinth)
    floor(c, G.x0 + 1, G.z0 + 1, G.x1 - 1, G.z1 - 1, 0, P.barnFloor)
    for (const [a, b] of ring(G.x0, G.z0, G.x1, G.z1)) {
      wallPanel(c, a, b, 1, G.course - 1, ashlar)
      wallPanel(c, a, b, G.course, G.course, P.course)
      wallPanel(c, a, b, G.course + 1, G.deckY, mud)
    }
    // the loft floor through the whole barn (the ladder's hole at (2, 5, 15)), the upper ceiling's vigas, the deck
    for (let x = G.x0 + 1; x < G.x1; x++) for (let z = G.z0 + 1; z < G.z1; z++) {
      if (!(x === 2 && z === 15)) c.set(x, G.course, z, P.loftFloor)
      c.set(x, G.ceilingY, z, ...(G.vigaZ.includes(z) ? [P.viga, { axis: 'x' }] : [P.matting]))
      c.set(x, G.deckY, z, P.deck)
    }
    for (let y = 1; y <= G.course; y++) c.set(2, y, 15, 'ladder', { facing: 'east', waterlogged: 'false' })
    // the passage: mud end walls with a door in each, a low deck over it under a coping, a lantern hung in it
    for (const x of [H.x0, H.x1]) for (let y = 1; y < PASSAGE.roofY; y++) c.set(x, y, bz, mud)
    for (let x = H.x0; x <= H.x1; x++) {
      c.set(x, PASSAGE.roofY, bz, x === H.x0 || x === H.x1 ? mud : P.deck)
      if (x === H.x0 || x === H.x1) slab(c, x, PASSAGE.roofY + 1, bz, P.coping, 'bottom')
    }
    // the second Farmer's chamber under the loft: reed (cane) partitions, a door from the barn floor
    const K = B.chamber
    for (let x = G.x0 + 1; x <= K.doorX; x++) for (let y = 1; y < G.course; y++) c.set(x, y, K.wallZ, P.partition)
    for (let z = K.z0; z <= K.z1; z++) for (let y = 1; y < G.course; y++) c.set(K.wallX, y, z, P.partition)
    // two stalls down the east wall (the donkey's and the goats'): a fence front with a gate to each, dividers, a sunken
    // trough and hay in each
    const S = B.stalls
    for (let z = S.z0; z <= S.z1; z++) {
      if (S.gates.includes(z)) c.set(S.front, 1, z, `${P.wood}_fence_gate`, { facing: 'east', in_wall: 'false', open: 'false', powered: 'false' })
      else c.set(S.front, 1, z, `${P.wood}_fence`)
    }
    for (const z of S.dividers) c.set(G.x1 - 1, 1, z, `${P.wood}_fence`)
    for (const z of S.troughs) c.set(G.x1 - 1, 0, z, 'water', { level: '0' })
    for (const z of S.hay) c.set(G.x1 - 1, 1, z, 'hay_block', { axis: 'y' })
    // the tool wall: hooks on the west wall over a board bench
    for (const z of B.tools.z) c.set(B.tools.x, 2, z, 'tripwire_hook', { facing: 'east', attached: 'false', powered: 'false' })
    for (const z of B.tools.z.slice(0, 2)) slab(c, B.tools.x, 1, z, `${P.wood}_slab`, 'top')
  }

  // --- Pass 2: the roofs: decks, coped parapets, the crown over the door, the dovecote, the bins ------------------
  const inDove = (x, z) => x >= DOVECOTE.x0 && x <= DOVECOTE.x0 + 2 && z >= DOVECOTE.z0 && z <= DOVECOTE.z0 + 2
  const inCrown = (x, z) => z === H.z0 && x >= PORTAL.x0 && x <= PORTAL.x1
  rowParapet(c, { x0: H.x0, z0: H.z0, x1: H.x1, z1: H.z1, y: H.parapetY }, { wall: P.parapet, coping: P.coping },
    (x, z) => inDove(x, z) || inCrown(x, z))
  // the stepped crown over the door: the parapet up two courses at x 3..5, a smooth step at x 4
  for (let x = PORTAL.x0; x <= PORTAL.x1; x++) {
    c.set(x, H.parapetY, H.z0, mud); c.set(x, H.parapetY + 1, H.z0, mud)
    if (x !== 4) slab(c, x, H.parapetY + 2, H.z0, P.coping, 'bottom')
  }
  c.set(4, H.parapetY + 2, H.z0, P.horn); slab(c, 4, H.parapetY + 3, H.z0, P.coping, 'bottom')
  dovecote(c, DOVECOTE.x0, DOVECOTE.z0, DOVECOTE.y0, {
    body: mud, perch: P.vigaEnd, pot: P.pot, band: P.band, dome: P.domeStairs,
    faces: two ? ['north', 'west', 'east'] : ['north', 'south', 'west', 'east'],
  })
  // two low spurs tie the dovecote's back corners to the side parapets (no pit between tower and parapet)
  for (const x of [DOVECOTE.x0 - 1, DOVECOTE.x0 + 3]) {
    c.set(x, H.parapetY, DOVECOTE.z0 + 2, mud); slab(c, x, H.parapetY + 1, DOVECOTE.z0 + 2, P.coping, 'bottom')
  }
  // the dovecote's battered foot: a ring of stairs on the deck round its front and sides, high sides to the tower
  for (let z = DOVECOTE.z0 - 1; z <= DOVECOTE.z0 + 1; z++) {
    stair(c, DOVECOTE.x0 - 1, H.parapetY, z, P.batter, 'east'); stair(c, DOVECOTE.x0 + 3, H.parapetY, z, P.batter, 'west')
  }
  for (let x = DOVECOTE.x0; x <= DOVECOTE.x0 + 2; x++) stair(c, x, H.parapetY, DOVECOTE.z0 - 1, P.batter, 'south')
  if (two) {
    rowParapet(c, { x0: G.x0, z0: G.z0, x1: G.x1, z1: G.z1, y: G.parapetY }, { wall: P.parapet, coping: P.coping })
    for (const [x, z] of G.bins) grainBin(c, x, z, G.parapetY, { body: mud, corner: P.binCorner, stairs: P.domeStairs, core: P.band, top: P.band, h: G.binDrum })
  }

  // --- Pass 3: openings -----------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, fd.out, { wood: P.wood, step: fd.step, stepStair: P.step })
  const bd = OPENINGS.backDoor
  door(c, bd.at, bd.facing, bd.out, { wood: P.wood })
  if (two) {
    for (const o of [OPENINGS.barnDoor, OPENINGS.barnSideDoor]) door(c, o.at, o.facing, o.out, { wood: P.wood })
    const wd = OPENINGS.wagonDoors
    wd.at.forEach((at, i) => door(c, at, wd.facing, wd.out, { wood: P.wood, hinge: i ? 'right' : 'left' }))
    for (let x = 4; x <= 7; x++) c.set(x, 3, G.z1, P.viga, { axis: 'x' }) // the wagon doors' lintel
    for (const [x, , z] of wd.at) stair(c, x, 0, z + 1, P.step, 'north')
    door(c, [H.x0, 1, bz], 'east', 'west', { wood: P.wood })
    door(c, [H.x1, 1, bz], 'west', 'east', { wood: P.wood })
    lantern(c, 4, PASSAGE.roofY - 1, bz, true)
    const K = B.chamber
    door(c, [K.doorX, 1, K.wallZ], 'south', 'north', { wood: P.wood })
    // the hay door high in the back wall, the hoist beam out over it with its chain
    const hd = OPENINGS.hayDoor.at
    door(c, hd, 'north', 'south', { wood: P.wood })
    c.set(hd[0], G.deckY, G.z1, P.viga, { axis: 'z' }); c.set(hd[0], G.deckY, G.z1 + 1, P.viga, { axis: 'z' })
    c.set(hd[0], G.deckY - 1, G.z1 + 1, 'chain', { axis: 'y', waterlogged: 'false' })
    lantern(c, hd[0], G.deckY - 2, G.z1 + 1, true) // the hoist's lantern on its chain, before the hay door's head
    for (let x = hd[0] - 1; x <= hd[0] + 1; x++) c.set(x, G.ceilingY, G.z1, P.course) // a cut lintel over the hay door
  }
  for (const w of OPENINGS.windows) {
    if (!has(w)) continue
    window(c, w.cells, w.out, null, { glass: P.glass })
    if (w.screen) screen(c, w.cells, w.out, { lattice: P.lattice, sill: P.sill, hood: P.sill })
  }

  // --- Pass 4: depth and detail ---------------------------------------------------------------------------------
  // the portal: cut jambs proud of the door, a chiseled lintel, a hood of upside-down stairs over it
  for (const x of [PORTAL.x0, PORTAL.x1]) { c.set(x, 0, PORTAL.z, P.plinth); for (let y = 1; y <= PORTAL.top; y++) c.set(x, y, PORTAL.z, P.jamb) }
  c.set(fd.at[0], PORTAL.top, PORTAL.z, P.carved)
  for (let x = H.x0; x <= H.x1; x++) stair(c, x, PORTAL.hoodY, PORTAL.z, P.hood, 'south', 'top')
  // the mastabas: mud benches along the street front on a paved apron, lanterns on their outer ends
  for (let x = H.x0; x <= H.x1; x++) if (!c.get(x, 0, 0)) c.set(x, 0, 0, P.plinth)
  for (const x of BENCHES) if (!c.get(x, 1, 0)) slab(c, x, 1, 0, P.bench, 'top')
  lantern(c, 1, 2, 0, false); lantern(c, 7, 2, 0, false)
  // spruce beam ends a block out of both side walls at the ceiling (the vigas run across x); II's at the granary's ceiling
  for (const z of H.vigaZ) { if (Math.abs(z - FLUE.z) > 1) c.set(H.x0 - 1, H.ceilingY, z, P.vigaEnd); c.set(H.x1 + 1, H.ceilingY, z, P.vigaEnd) }
  if (two) for (const z of G.vigaZ) { c.set(G.x0 - 1, G.ceilingY, z, P.vigaEnd); c.set(G.x1 + 1, G.ceilingY, z, P.vigaEnd) }
  // and through the granary's back face at the same course, either side of the hoist (the face's shadow line)
  if (two) for (const x of G.backVigas) c.set(x, G.ceilingY, G.z1 + 1, P.vigaEnd)
  // the batter: a sandstone skirt along the house's west wall (the flue's breast takes the middle)
  for (let z = H.z0; z <= H.z1; z++) if (z < FLUE.z - 1 || z > FLUE.z + 1) stair(c, H.x0 - 1, 0, z, P.batter, 'east')
  if (two) for (let z = G.z0; z <= G.z1; z++) c.set(G.x0 - 1, 0, z, P.plinth) // the granary's foot course
  // the granary's drip moulding: the loft floor's cut course carried a block out along the west side and the back,
  // turned round the back west corner (finish() shapes it), the sill line of the hay door
  if (two) {
    for (let z = G.z0 + 1; z <= G.z1 + 1; z++) stair(c, G.x0 - 1, G.course, z, P.hood, 'east', 'top')
    for (let x = G.x0; x <= G.x1; x++) stair(c, x, G.course, G.z1 + 1, P.hood, 'north', 'top')
  }
  // the flue: the hearth's mud stack up the west wall with its oven breast, smoking over the coping
  chimney(c, FLUE.x, FLUE.z, 0, FLUE.top, mud, { breast: { axis: 'z', top: FLUE.breastTop, stair: P.batter } })
  c.set(FLUE.x, FLUE.top, FLUE.z, P.horn)
  // the palm-rib shade along the field side, on palm-trunk posts off the walking line
  shade(c, { x: GALLERY.x, z0: 0, z1: galleryEnd, y: GALLERY.shadeY, posts: GALLERY.posts[two ? 2 : 1],
    rafters: Array.from({ length: galleryEnd + 1 }, (_, i) => i).filter(z => z % 3 === 1) },
  { beam: P.palmBeam, post: P.palmPost, rafter: P.rafter, matting: P.mattingSlab })

  // --- Pass 5: interior (the shipped furniture, cell for cell) ------------------------------------------------------
  for (const f of FURNITURE) {
    if ((f.level ?? 1) > level) continue
    if (f.part === 'composter') composter(c, f)
    else furnitureParts[f.part](c, f)
  }
    if (two) {
    // hay up on the loft (clear of the hay door and the ladder's hole), grain jars along its west wall, a pumpkin by
    // the ladder, hay by the wagon doors
    for (const [x, y, z] of [[6, 1, 18], [6, 2, 18], [5, 6, 17], [5, 6, 18], [5, 6, 19], [6, 6, 18], [6, 6, 19], [6, 7, 19],
      [6, 6, 11], [6, 6, 12], [5, 6, 11], [6, 7, 11]]) c.set(x, y, z, 'hay_block', { axis: 'y' })
    for (const z of [11, 12, 17, 18, 19]) pot(c, 2, 6, z)
    c.set(2, 1, 14, 'pumpkin')
  }

  // --- Pass 6: the cool store (III) --------------------------------------------------------------------------------
  let dug = null
  if (level >= 3) {
    const C = CELLAR
    dug = cellar(c, C.x0, C.z0, C.x1, C.z1, C.depth, mix(P.cellarWall, 3), P.cellarFloor)
    hatchLadder(c, C.ladder.x, C.ladder.z, -C.depth + 1, 0, C.ladder.facing, P.wood)
    // jars of oil and dates and melons down the east wall, under and on a board shelf; sacks (hay) at the back
    ;[['melon', 3], ['jar', 4], ['melon', 5], ['jar', 6]].forEach(([id, z]) => {
      if (id === 'jar') pot(c, 6, -3, z); else c.set(6, -3, z, id)
      slab(c, 6, -2, z, `${P.wood}_slab`, 'top')
    })
    for (const z of [3, 5]) pot(c, 6, -1, z)
    for (const [x, y, z] of [[3, -3, 7], [4, -3, 7], [6, -3, 7], [3, -2, 7]]) c.set(x, y, z, 'hay_block', { axis: 'y' })
    pot(c, 5, -3, 7)
  }

  // --- Pass 7: ground and field ---------------------------------------------------------------------------------
  const cropAt = rows => (x, z) => { const r = rows[x]; return r ? [r[0], r[1][z % r[1].length]] : null }
  const F = FIELD, F2 = FIELD2
  fieldPlot(c, F, {
    water: (x, z) => x === F.channelX && z !== SCARECROW_Z,
    footing: (x, z) => (x === F.channelX && z === SCARECROW_Z ? P.course : null),
    crop: cropAt(CROPS[1]),
  })
  kerbFence(c, [[F.x0, F.z0 - 1], [14, F.z0 - 1], [14, F.z1 + 1], [F.x0, F.z1 + 1]], 0, P.kerb, P.kerbWall)
  scarecrow(c, [F.channelX, 1, SCARECROW_Z], 'west', { leg: P.rafter, body: 'white_wool', arms: P.rafter, hat: 'yellow_carpet' })
  if (two) {
    fieldPlot(c, F2, {
      water: (x, z) => x === F2.channelX && z >= F2.water[0] && z <= F2.water[1],
      crop: (x, z) => (x === F2.channelX ? ['carrots', 4] : cropAt(CROPS[2])(x, z)),
    })
    kerbFence(c, [[14, F2.z0 - 1], [14, F2.z1 + 1], [F2.x0, F2.z1 + 1]], 0, P.kerb, P.kerbWall)
  }
  // the shaduf on the kerb between the fields, its chain into the first field's channel
  const sw = SHADUF.sweep[two ? 2 : 1]
  shaduf(c, SHADUF.x, SHADUF.z, SHADUF.y0, { upright: P.rafter, bar: P.rafter, pole: `${P.wood}_fence`, cells: sw.cells, drop: 1,
    bucket: 'decorated_pot', weight: P.kerbWall, weightAt: sw.weight })
  for (const [x, z] of [...OUTSIDE.fieldLamps[1], ...(two ? OUTSIDE.fieldLamps[2] : [])]) lantern(c, x, 2, z, false)
  // the date palm at the field's front corner
  const pm = OUTSIDE.palm
  c.set(...pm.soil, P.palm.soil)
  for (const [x, y, z] of pm.trunk) c.set(x, y, z, P.palm.trunk, { axis: 'y' })
  palmCrown(c, pm.crown, P.palm.leaves)
  for (const [x, y, z] of OUTSIDE.jars[two ? 2 : 1]) pot(c, x, y, z)
  return { c, dug }
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const { c, dug } = drawLevel(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  fs.mkdirSync(path.join(here, 'out'), { recursive: true })
  if (dug) fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), dug.join(','))
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: `${IDS[level]}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
