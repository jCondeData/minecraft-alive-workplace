// Noble's Manor, desert look (49.10f), the recipe: assembles plan.mjs from the parts library (../parts) in passes
// (block-out and structure, roofs and the windcatchers, openings, depth and detail, interior, ground) for LEVEL = 1..4,
// each level drawing the one below first and adding to it. Writes furniture.json (and, for IV, out/cellar.txt) for
// rebuild.py. Build all four: build.sh. CUT=<y>: a cutaway (above y).
//   Level I    the wing, the hall and the windcatcher tower with its iwan
//   Level II   + the paradise garden behind the hall
//   Level III  + the ballroom wing with its lantern, and the east garden
//   Level IV   + the ribbed cellar under the kitchen and the hall's west end
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  IDS, SIZES, BASEMENT, SHIPPED, PALETTE, MASSES, WINDOWS, GREAT_WINDOW, RAWSHANS, ORIEL, BAY, CHAINS, FIRES, OUTSIDE,
} from './plan.mjs'
import {
  Canvas, LiftedCanvas, mix, DIRS, OPP, stair, slab, window, windcatcher, screenOriel, rawshan, chimney, badgirHead,
  qaaLantern, datePalm,
} from '../parts/index.mjs'
import { manorFloors, manorFittings, merlons } from '../parts/noble_house.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const ashlar = mix(P.ashlar, 11), mud = mix(P.mud, 12), cellarWall = mix(P.cellarWall, 13)
const ROOMS = { ...P.rooms, cellarWall }

const ringCells = (r, skip = () => false) => {
  const out = []
  for (let x = r.x0; x <= r.x1; x++) for (let z = r.z0; z <= r.z1; z++)
    if ((x === r.x0 || x === r.x1 || z === r.z0 || z === r.z1) && !skip(x, z)) out.push([x, z])
  return out
}
/** a mass's walls: the plinth, coursed sandstone to y 4, the course at 5, mud brick from 6 up to `top` */
function walls(c, r, top, skip = () => false) {
  for (const [x, z] of ringCells(r, skip)) {
    c.set(x, 0, z, P.plinth)
    for (let y = 1; y <= top; y++) c.set(x, y, z, y <= 4 ? ashlar : y === 5 ? P.course : mud)
  }
}
/** the parapet over a deck: a mud-brick course at y, merlons and crenels at y + 1 */
function parapet(c, r, y, skip = () => false) {
  for (const [x, z] of ringCells(r, skip)) c.set(x, y, z, P.parapet)
  merlons(c, { ...r, y: y + 1 }, P.merlon, P.crenel, skip)
}
/** a cornice under a parapet: an upside-down stair a block out of every face at y, its back to the wall */
function cornice(c, r, y, skip = () => false) {
  const put = (x, z, f) => { if (c.inside(x, y, z) && !c.get(x, y, z) && !skip(x, z)) stair(c, x, y, z, P.cornice, f, 'top') }
  for (let x = r.x0; x <= r.x1; x++) { put(x, r.z0 - 1, 'south'); put(x, r.z1 + 1, 'north') }
  for (let z = r.z0; z <= r.z1; z++) { put(r.x0 - 1, z, 'east'); put(r.x1 + 1, z, 'west') }
}
function piers(c, list) {
  for (const [x, z, top] of list) {
    for (let y = 0; y <= top; y++) c.set(x, y, z, P.pier)
    slab(c, x, top + 1, z, P.cap)
  }
}
function windows(c, list, level) {
  for (const w of list) {
    if ((w.until ?? 9) < level) continue
    for (const a of w.at) {
      const cells = []
      for (let y = w.y[0]; y <= w.y[1]; y++) cells.push(w.axis === 'x' ? [a, y, w.line] : [w.line, y, a])
      if (w.dress === 'lattice') {
        window(c, cells, w.out, 'shutters', { glass: P.glass, wood: 'dark_oak' })
        const [x, y, z] = cells[0], [ox, oz] = DIRS[w.out]
        slab(c, x + ox, y - 1, z + oz, P.sill, 'top')
      } else if (w.dress === 'sill') window(c, cells, w.out, 'sill', { glass: P.glass, sill: P.sill })
      else window(c, cells, w.out, null, { glass: P.glass })
      if (w.arch) { const [x, , z] = cells[cells.length - 1]; c.set(x, w.y[1] + 1, z, P.carved) }
    }
  }
}
const pave = (c, x0, z0, x1, z1, id = P.paving) => {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) if (c.inside(x, 0, z) && !c.get(x, 0, z)) c.set(x, 0, z, id)
}
const pot = (c, x, y, z) => { if (!c.get(x, y, z)) c.set(x, y, z, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' }) }
const water = (c, x, y, z) => c.set(x, y, z, 'water', { level: '0' })

// --- Level I: the wing, the hall and the windcatcher tower ------------------------------------------------------------
function levelOne(c) {
  const Wg = MASSES.wing, H = MASSES.hall, T = MASSES.tower
  const hallWall = (x, z) => x === Wg.x1 && z >= H.z0 && z <= H.z1
  // Pass 1, block-out and structure: the wing, then the hall over the shared wall, then the tower; the decks
  walls(c, Wg, Wg.top)
  c.fill(Wg.x0, Wg.deck, Wg.z0, Wg.x1, Wg.deck, Wg.z1, P.deck)
  walls(c, H, H.top)
  c.fill(H.x0, H.deck, H.z0, H.x1, H.deck, H.z1, P.deck)
  walls(c, T, T.top)
  c.fill(T.x0 + 1, 10, T.z0 + 1, T.x1 - 1, T.top, T.z1 - 1, P.badgir.body) // the study's ceiling and the shaft over it
  for (const [x, z] of ringCells(T)) c.set(x, 11, z, P.tile) // a band of tile under the windcatcher's head

  // Pass 2, the roofs: parapets with merlons over every deck, cornices under them, the badgir head on the tower, the
  // small windcatcher over the great chamber, the two stacks
  parapet(c, Wg, Wg.deck + 1, hallWall)
  parapet(c, H, H.deck + 1, (x, z) => z === H.z0 && x >= T.x0 && x <= T.x1)
  // no cornice round the cap: an overhang over the hall's deck left slots the bake walled up in loose mud brick
  badgirHead(c, { x0: T.x0, z0: T.z0, x1: T.x1, z1: T.z1, y0: 13, y1: 16 }, { ...P.badgir, cornice: null })
  const K = MASSES.windcatcher
  windcatcher(c, K.x0, K.z0, K.y0, K.y1, { body: mix(P.catcher.body, 9), corner: P.catcher.corner, cap: P.catcher.cap, capTop: P.catcher.capTop })
  const S = MASSES.stacks
  chimney(c, S.kitchen.x, S.kitchen.z, 0, S.kitchen.top, P.stack, { breast: { axis: 'z', top: S.kitchen.breastTop, stair: P.stackStair } })
  chimney(c, S.hall.x, S.hall.z, 0, S.hall.top, P.stack, { breast: { axis: 'x', top: S.hall.breastTop, stair: P.stackStair } })
  piers(c, MASSES.piers[1])

  // Pass 3, openings: the windows (the doors come with the rooms, pass 5); the parlour's bay
  windows(c, WINDOWS[1], 1)
  const B = BAY
  for (let x = B.x0; x <= B.x1; x++) {
    c.set(x, 0, B.z, P.plinth)
    const end = x === B.x0 || x === B.x1
    for (let y = 1; y < B.lintel; y++) c.set(x, y, B.z, end ? P.pier : y < 2 ? P.course : P.glass)
    c.set(x, B.lintel, B.z, P.course)
  }
  for (let x = B.glass[0]; x <= B.glass[1]; x++) for (let y = B.y[0]; y <= B.y[1]; y++) c.clear(x, y, B.z + 1) // the bay opens into the parlour

  // Pass 4, depth and detail: the rawshans, the iwan (the study's oriel, its arch, the tile), the cornices
  for (const R of RAWSHANS) {
    rawshan(c, { x0: R.x0, x1: R.x1, z: R.z, y0: R.y0, y1: R.y1, posts: R.posts },
      { post: P.latticePost, lattice: P.lattice, sill: P.latticeSill, roof: P.latticeRoof, corbel: null })
    for (const x of R.corbels ?? []) stair(c, x, R.y0 - 1, R.z, P.corbel, 'south', 'top')
  }
  const O = ORIEL
  for (const x of [O.x0, O.x1]) c.set(x, O.y0 - 1, O.z, P.carved) // carved corbels, the door's lamps hung under them
  screenOriel(c, { x0: O.x0, x1: O.x1, z: O.z, y0: O.y0, y1: O.y1 },
    { post: P.latticePost, lattice: P.lattice, roof: P.latticeSill, sill: P.latticeSill, corbel: P.corbel })
  // the iwan's head closed into one solid frame with the piers, up to the tower's top: a band of tile between
  // sandstone courses (round 3: an open arch head left sky between the piers, the head and the shaft)
  for (let x = O.x0; x <= O.x1; x++) { c.set(x, 10, O.z, P.pier); c.set(x, 11, O.z, P.tile); c.set(x, 12, O.z, P.pier) }
  c.set(13, 10, 1, P.carved) // a carved rosette in the iwan's head
  cornice(c, Wg, Wg.deck, (x, z) => x === Wg.x1 + 1 && z >= H.z0 && z <= H.z1)
  cornice(c, H, H.deck)
  for (const [x, y, z] of OUTSIDE.doorLamps) c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })

  // Pass 5, interior: the shipped Manor's rooms, cell for cell (furniture comes last, after every level's walls)
  // Pass 6, ground: the front court and the back terrace paved, the kept rain butt, jars
  pave(c, 8, 0, 18, 4)
  pave(c, 20, 4, 20, 14)
  pave(c, 8, 14, 19, 16, P.paving2)
  c.set(...OUTSIDE.rainButt, 'barrel', { facing: 'up', open: 'false' })
  for (const [x, y, z] of OUTSIDE.pots[1]) pot(c, x, y, z)
}

// --- Level II: the paradise garden -------------------------------------------------------------------------------------
function levelTwo(c) {
  const G = MASSES.garden
  // the walls: mud brick to y 3 under a coping, piers on the back wall with lamps on them
  const wallCells = []
  for (let z = 17; z <= G.z1; z++) wallCells.push([G.x0, z])
  for (let z = 15; z <= G.z1; z++) wallCells.push([G.x1, z])
  for (let x = G.x0 + 1; x < G.x1; x++) wallCells.push([x, G.z1])
  for (const [x, z] of wallCells) {
    c.set(x, 0, z, P.plinth)
    for (let y = 1; y <= G.wallTop; y++) c.set(x, y, z, mud)
    slab(c, x, G.wallTop + 1, z, P.cap)
  }
  for (const x of G.piers) {
    for (let y = 0; y <= G.wallTop + 1; y++) c.set(x, y, G.z1, P.pier)
    c.set(x, G.wallTop + 2, G.z1, P.lampPost)
    c.set(x, G.wallTop + 3, G.z1, 'lantern', { hanging: 'false', waterlogged: 'false' })
  }
  // the beds: soil, a palm in the middle of each, flowers round it
  OUTSIDE.beds.forEach(([x0, z0, x1, z1], i) => {
    for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
      c.set(x, 0, z, P.soil)
      if ((x + z) % 2 === 0) c.set(x, 1, z, OUTSIDE.flowers[i])
    }
  })
  // the fountain at the cross: a kerb round a pool, a pedestal with a lamp in its middle
  const F = OUTSIDE.pool
  for (let x = F.x0; x <= F.x1; x++) for (let z = F.z0; z <= F.z1; z++) {
    c.set(x, 0, z, P.paving)
    const edge = x === F.x0 || x === F.x1 || z === F.z0 || z === F.z1
    if (edge) c.set(x, 1, z, P.course)
    else if (x === F.cx && z === F.cz) { c.set(x, 1, z, P.carved); c.set(x, 2, z, P.lampPost); c.set(x, 3, z, 'lantern', { hanging: 'false', waterlogged: 'false' }) }
    else water(c, x, 1, z)
  }
  for (const [x, z] of OUTSIDE.palms) {
    c.clear(x, 1, z)
    datePalm(c, [x, 0, z], { soil: P.soil, trunk: P.palmTrunk, leaves: P.palmLeaves, height: 6, reach: 2 })
  }
  for (const [x, z] of OUTSIDE.shrubs) c.set(x, 1, z, 'flowering_azalea')
  // the bench at the end of the walk, its arms two blocks with a cactus pot on each
  const Bn = OUTSIDE.bench
  for (let x = Bn.x0; x <= Bn.x1; x++) stair(c, x, 1, Bn.z, P.step, 'south')
  for (const x of [Bn.x0 - 1, Bn.x1 + 1]) { c.set(x, 1, Bn.z, P.pier); c.set(x, 2, Bn.z, 'potted_cactus') }
  pave(c, G.x0 + 1, 16, G.x1 - 1, G.z1 - 1)
}

// --- Level III: the ballroom and the east garden -------------------------------------------------------------------
function levelThree(c) {
  const R = MASSES.ballroom, H = MASSES.hall
  // take down what the ballroom stands on: the hall's east piers and cornice, the paving east of it
  c.clearBox(R.x0 + 1, 0, 0, R.x1 + 1, 17, R.z1 + 1)
  for (let y = 0; y <= 13; y++) c.clear(R.x0, y, 0)
  // Pass 1: walls (the hall's east wall stays below its deck), the deck, the parapet and merlons, the cornice
  walls(c, R, R.top, (x, z) => x === R.x0 && z >= H.z0 && z <= H.z1)
  for (let z = H.z0; z <= H.z1; z++) for (let y = H.deck; y <= R.top; y++) c.set(R.x0, y, z, mud)
  c.fill(R.x0, R.deck, R.z0, R.x1, R.deck, R.z1, P.deck)
  // one level parapet with a straight coping all round (no merlons: seen along its length a merlon row read as ragged)
  for (const [x, z] of ringCells(R)) { c.set(x, R.deck + 1, z, P.parapet); slab(c, x, R.deck + 2, z, P.cap) }
  cornice(c, R, R.deck, (x, z) => x === R.x0 - 1 || (x === R.x0 && z === R.z1 + 1)) // none over the hall's roof or the garden wall (a slot there)
  // the lantern over the dance floor: a ring of wall with glass on every face, its ceiling, a low cap
  const L = R.lantern
  for (let x = L.x0 + 1; x < L.x1; x++) for (let z = L.z0 + 1; z < L.z1; z++) c.clear(x, R.deck, z)
  const lw = [[23, L.z0], [23, L.z1], [L.x0, 7], [L.x0, 9], [L.x1, 7], [L.x1, 9]]
  qaaLantern(c, { x0: L.x0, z0: L.z0, x1: L.x1, z1: L.z1, y0: L.y0, y1: L.y1 },
    { wall: P.pier, coping: P.pier, ceiling: P.deck, glass: P.glass, windows: lw })
  for (const [x, z] of lw) c.set(x, L.y0, z, P.pier) // the glass starts above the deck's course
  // (no cap on it: a slab cap over the lantern's coping stepped up to the windcatcher in a wedge; its top is level)
  piers(c, MASSES.piers[3])
  // Pass 3: the great window (a mullion, a pointed head, a band of tile over it), the tall east windows, the rest
  const G = GREAT_WINDOW
  for (let x = G.x0; x <= G.x1; x++) for (let y = G.y0; y < G.spring; y++) c.set(x, y, G.line, x === G.mullion ? P.pier : P.glass)
  stair(c, G.x0, G.spring, G.line, P.cornice, 'west', 'top'); stair(c, G.x1, G.spring, G.line, P.cornice, 'east', 'top')
  for (let x = G.x0 + 1; x < G.x1; x++) c.set(x, G.spring, G.line, P.glass)
  stair(c, G.x0 + 1, G.spring + 1, G.line, P.cornice, 'west', 'top'); stair(c, G.x1 - 1, G.spring + 1, G.line, P.cornice, 'east', 'top')
  c.set(G.mullion, G.spring + 1, G.line, P.carved) // the keystone
  for (let x = R.x0 + 1; x < R.x1; x++) c.set(x, R.top, G.line, P.tile)
  for (let x = G.x0; x <= G.x1; x++) slab(c, x, 1, 0, P.sill, 'top')
  windows(c, WINDOWS[3], 3)
  // the east garden: walls, paving, a pool on the walk, two palm beds; the gate through the old garden's east wall
  const E = MASSES.gardenEast
  for (let z = 17; z <= E.z1; z++) { c.set(E.x1, 0, z, P.plinth); for (let y = 1; y <= 3; y++) c.set(E.x1, y, z, mud); slab(c, E.x1, 4, z, P.cap) }
  for (let x = E.x0 + 1; x < E.x1; x++) { c.set(x, 0, E.z1, P.plinth); for (let y = 1; y <= 3; y++) c.set(x, y, E.z1, mud); slab(c, x, 4, E.z1, P.cap) }
  for (const [x, z] of [[E.x1, 20], [23, E.z1]]) { for (let y = 0; y <= 4; y++) c.set(x, y, z, P.pier); slab(c, x, 5, z, P.cap) }
  for (let y = 1; y <= 3; y++) c.clear(OUTSIDE.gate.x, y, OUTSIDE.gate.z)
  c.set(OUTSIDE.gate.x, 4, OUTSIDE.gate.z, P.pier)
  for (const [x0, z0, x1, z1] of OUTSIDE.eastBeds) for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    c.set(x, 0, z, P.soil)
    if ((x + z) % 2 === 0) c.set(x, 1, z, 'orange_tulip')
  }
  const EP = OUTSIDE.eastPool
  for (let x = EP.x0 - 1; x <= EP.x1 + 1; x++) for (let z = EP.z0 - 1; z <= EP.z1 + 1; z++) {
    c.set(x, 0, z, P.paving)
    const edge = x === EP.x0 - 1 || x === EP.x1 + 1 || z === EP.z0 - 1 || z === EP.z1 + 1
    if (edge) c.set(x, 1, z, P.course)
    else water(c, x, 1, z)
  }
  for (const [x, z] of OUTSIDE.eastPalms) { c.clear(x, 1, z); datePalm(c, [x, 0, z], { soil: P.soil, trunk: P.palmTrunk, leaves: P.palmLeaves, height: 6, reach: 2 }) }
  pave(c, R.x0, 0, R.x1 + 1, 0)
  pave(c, R.x1 + 1, 0, R.x1 + 1, R.z1 + 1)
  pave(c, E.x0 + 1, R.z1 + 1, E.x1 - 1, E.z1 - 1)
}

// --- Level IV: the ribbed cellar ---------------------------------------------------------------------------------------
function vault(c, [x0, z0, x1, z1]) {
  // haunches along the long walls, a rib across the middle, casks along the west wall
  for (let x = x0; x <= x1; x++) for (const [z, f] of [[z0, 'north'], [z1, 'south']])
    if (!c.get(x, -1, z)) stair(c, x, -1, z, P.cornice, f, 'top')
  for (let z = z0 + 1; z < z1; z++) if (!c.get(7, -1, z)) c.set(7, -1, z, P.course)
  // the casks (barrels on their sides) along both end walls, two high in the middle
  for (const [x, f] of [[x0, 'east'], [x1, 'west']]) {
    for (let z = z0 + 1; z < z1; z++) c.set(x, -3, z, 'barrel', { facing: f, open: 'false' })
    for (let z = z0 + 2; z < z1 - 1; z++) c.set(x, -2, z, 'barrel', { facing: f, open: 'false' })
  }
}

export function draw(level = 1) {
  const c = level >= 4 ? new LiftedCanvas(SIZES[4], BASEMENT) : new Canvas(SIZES[level])
  levelOne(c)
  if (level >= 2) levelTwo(c)
  if (level >= 3) levelThree(c)
  manorFloors(c, Math.min(level, 3), SHIPPED, ROOMS)
  // the rooms' fittings and every furniture piece, last (they stand in cells the walls leave free)
  const dug = manorFittings(c, level, SHIPPED, ROOMS, { fires: FIRES, chains: CHAINS[3] && level >= 3 ? CHAINS[3] : {}, cellarVault: vault })
  return { c, dug }
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const { c, dug } = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  if (dug) { fs.mkdirSync(path.join(here, 'out'), { recursive: true }); fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), dug.join(',')) }
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: `${IDS[level]}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
