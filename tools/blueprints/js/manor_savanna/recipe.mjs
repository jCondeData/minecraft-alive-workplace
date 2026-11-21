// Noble's Manor, savanna look (49.10f), the recipe: assembles plan.mjs from the parts library (../parts) in passes
// (block-out and the great thatch, walls under it, openings, depth and detail, interior, ground) for LEVEL = 1..4, each
// level drawing the one below first and adding to it (the thatch is drawn once over the masses the level has: at III
// it grows into a T over the ballroom). Writes furniture.json (and, for IV, out/cellar.txt) for rebuild.py. Build all
// four: build.sh. CUT=<y>: a cutaway (above y).
//   Level I    the wing, the hall and the tower under the great thatch, the colonnade, the front verandas
//   Level II   + the walled yard round the family's acacia
//   Level III  + the ballroom under the thatch's T, and the east yard
//   Level IV   + the ribbed cellar under the kitchen and the hall's west end
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { IDS, SIZES, BASEMENT, SHIPPED, PALETTE, MASSES, WINDOWS, FIRES, OUTSIDE } from './plan.mjs'
import {
  Canvas, LiftedCanvas, mix, DIRS, stair, slab, window, chimney, hipRoof, hood, slabPent, acaciaTree, paintedBand,
  lampPost, quoins,
} from '../parts/index.mjs'
import { manorFloors, manorFittings, unionHip } from '../parts/noble_house.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const stone = mix(P.stone, 21), base = mix(P.plinth, 22), cellarWall = mix(P.cellarWall, 23)
const ROOMS = { ...P.rooms, cellarWall }
const paint = (x, y, z) => P.paint[(x + z + y) % 2]

const ringCells = (r, skip = () => false) => {
  const out = []
  for (let x = r.x0; x <= r.x1; x++) for (let z = r.z0; z <= r.z1; z++)
    if ((x === r.x0 || x === r.x1 || z === r.z0 || z === r.z1) && !skip(x, z)) out.push([x, z])
  return out
}
const inside = (r, x, z) => x >= r.x0 && x <= r.x1 && z >= r.z0 && z <= r.z1
/** a mass's walls up under the thatch: the plinth, red granite to y 4, the acacia beam ring at 5, packed mud from 6 to
 *  the roof; where the wall stands under the eave (an outside wall) its top course is painted */
function walls(c, r, under, skip = () => false, top = null) {
  for (const [x, z] of ringCells(r, skip)) {
    const y1 = top ?? under(x, z) - 1
    c.set(x, 0, z, base)
    for (let y = 1; y <= y1; y++) {
      if (y <= 4) c.set(x, y, z, stone)
      else if (y === 5) c.set(x, y, z, P.beam, { axis: x === r.x0 || x === r.x1 ? 'z' : 'x' })
      else c.set(x, y, z, y === y1 && y1 === MASSES.eave ? paint : P.mud)
    }
  }
}
function posts(c, list, y1) {
  for (const [x, z] of list) for (let y = 1; y <= y1; y++) c.set(x, y, z, P.post, { axis: 'y' })
}
function windows(c, list, level) {
  for (const w of list) {
    if ((w.until ?? 9) < level) continue
    for (const a of w.at) {
      const cells = []
      for (let y = w.y[0]; y <= w.y[1]; y++) cells.push(w.axis === 'x' ? [a, y, w.line] : [w.line, y, a])
      window(c, cells, w.out, w.dress, { glass: P.glass, wood: P.wood, pots: w.pots ?? [] })
      // the wall behind opened: a deep reveal under a lintel (the hall's blinds hang on it, as on the shipped hall's window)
      if (w.reveal) for (const [x, y, z] of cells) if (y < w.y[1]) c.clear(x, y, z + 1)
      if (w.reveal) c.set(a, w.y[0] - 1, w.line, stone) // the bay's stone base under the glass
    }
  }
}
const pave = (c, x0, z0, x1, z1, id = P.paving) => {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) if (c.inside(x, 0, z) && !c.get(x, 0, z)) c.set(x, 0, z, id)
}
/** a garden wall: mud brick, a painted course, a thatched coping */
function yardWall(c, cells, top = 3) {
  for (const [x, z] of cells) {
    c.set(x, 0, z, base)
    for (let y = 1; y < top; y++) c.set(x, y, z, P.mudBrick)
    c.set(x, top, z, paint)
    slab(c, x, top + 1, z, P.roof.slab)
  }
}

// --- Level I: the wing, the hall and the tower under the great thatch -----------------------------------------------
function levelOne(c, level) {
  const Wg = MASSES.wing, H = MASSES.hall, T = MASSES.tower
  const rects = [...MASSES.roof[1], ...(level >= 3 ? MASSES.roof[3] : [])]
  const gone = (x, z) => level >= 3 && MASSES.gonePostsAt3.some(([gx, gz]) => gx === x && gz === z)
  // Pass 1, block-out and the great thatch: one hip over the masses (the tower stands up through it), then the walls
  // up under it, the beam ring, the floors and ceilings
  const under = unionHip(c, rects, MASSES.eave, P.roof, (x, y, z) => !inside(T, x, z))
  walls(c, Wg, under)
  walls(c, H, under, (x, z) => x === Wg.x1 || inside(T, x, z))
  walls(c, T, under, () => false, T.top)
  quoins(c, [[Wg.x0, Wg.z0], [Wg.x1, Wg.z0], [Wg.x0, Wg.z1], [Wg.x1, Wg.z1], [H.x1, H.z0], [H.x1, H.z1], [T.x0, T.z0], [T.x1, T.z0]], 1, 4, P.quoin, stone)
  for (let x = T.x0 + 1; x < T.x1; x++) c.set(x, T.top, T.z0, paint) // the painted band between the tower's front corners only (round 3: a painted corner showed under the eave from the side)
  // the ceilings: the chambers' at 10, the Long Gallery's at 11, the study's at 10 (the thatch over them an attic)
  const ceil = (x0, z0, x1, z1, y) => { for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) if (!c.get(x, y, z) && (under(x, z) ?? 99) > y) c.set(x, y, z, P.ceiling, { axis: 'y' }) }
  ceil(Wg.x0 + 1, Wg.z0 + 1, Wg.x1 - 1, Wg.z1 - 1, 10)
  ceil(H.x0 + 1, H.z0 + 1, H.x1 - 1, H.z1 - 1, 11)
  ceil(T.x0 + 1, T.z0 + 1, T.x1 - 1, T.z1 - 1, 10)
  // the tower's steep pyramid of thatch, the clay pot on its point
  // its own eave all round over the great thatch, but none over the front bays (it would roof them into rooms)
  hipRoof(c, T.hip, P.roof, (x, y, z) => !((x === T.hip.x0 || x === T.hip.x1) && z > T.hip.z0 && z < 4))
  c.set(...T.finial, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })
  // the stacks through the eaves
  const S = MASSES.stacks
  chimney(c, S.kitchen.x, S.kitchen.z, 0, S.kitchen.top, P.mudBrick, { breast: { axis: 'z', top: S.kitchen.breastTop, stair: P.mudStair } })
  chimney(c, S.hall.x, S.hall.z, 0, S.hall.top, P.mudBrick, { breast: { axis: 'x', top: S.hall.breastTop, stair: P.mudStair } })

  // Pass 2, openings: the windows (the doors come with the rooms); the parlour's bay under a thatch hood
  windows(c, WINDOWS[1], level)
  const B = MASSES.bay
  for (let x = B.x0; x <= B.x1; x++) {
    c.set(x, 0, B.z, base)
    const end = x === B.x0 || x === B.x1
    for (let y = 1; y < B.lintel; y++) {
      if (end) c.set(x, y, B.z, P.post, { axis: 'y' })
      else c.set(x, y, B.z, y < 2 ? stone : P.glass)
    }
    c.set(x, B.lintel, B.z, P.beam, { axis: 'x' })
    slab(c, x, B.lintel + 1, B.z, P.roof.slab)
  }
  for (let x = B.glass[0]; x <= B.glass[1]; x++) for (let y = B.y[0]; y <= B.y[1]; y++) c.clear(x, y, B.z + 1)

  // Pass 3, depth and detail: the colonnade under the eaves, the tower's posts, the front verandas, the door's hood and
  // its painted surround
  posts(c, MASSES.posts[1].filter(([x, z]) => !gone(x, z)), MASSES.eave - 1)
  posts(c, MASSES.posts.tower, T.hip.eaveY - 1)
  for (const V of MASSES.verandas) {
    for (let x = V.x0 - 1; x <= V.x1 + 1; x++) for (let z = 1; z <= 4; z++) if (x > Wg.x1 && x < T.x0 || x > T.x1 && x < H.x1) c.set(x, 0, z, P.deck)
    posts(c, [[V.post, 2]], 4)
    for (let x = V.x0; x <= V.x1; x++) for (let z = 2; z <= 3; z++) slab(c, x, 5, z, P.roof.slab)
    for (let x = V.x0; x <= V.x1; x++) stair(c, x, 1, 3, `${P.wood}_stairs`, 'south') // a bench under the windows
  }
  posts(c, [...MASSES.jambs.map(x => [x, 4]), ...(level < 3 ? [[H.x1, 4]] : [])], 4) // jambs for the glazed windows
  for (const [x, y, z] of OUTSIDE.verandaLamps) c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
  hood(c, ...OUTSIDE.doorHood, P.roof)
  paintedBand(c, [[12, 1, 1], [12, 2, 1], [12, 3, 1], [13, 3, 1], [14, 3, 1], [14, 2, 1], [14, 1, 1]], P.paint)

  // Pass 5, ground: the front and the sides paved, the back terrace, the kept rain butt, pots and a hay bale
  pave(c, 0, 0, 20, 4)
  pave(c, 0, 0, 0, 16)
  pave(c, 20, 0, 20, 16)
  pave(c, 0, 16, 8, 16)
  pave(c, 9, 14, 19, 15, P.paving2)
  c.set(...OUTSIDE.rainButt, 'barrel', { facing: 'up', open: 'false' })
  for (const [x, y, z, id] of OUTSIDE.pots[1]) if (!(level >= 3 && x >= 19) && !c.get(x, y, z)) c.set(x, y, z, id)
  if (level < 3) for (const p of OUTSIDE.hay) c.set(...p, 'hay_block', { axis: 'y' })
  return under
}

// --- Level II: the walled yard round the family's acacia ----------------------------------------------------------
function levelTwo(c) {
  const G = MASSES.garden
  const cells = []
  for (let z = 16; z <= G.z1; z++) cells.push([G.x0, z])
  for (let z = 15; z <= G.z1; z++) cells.push([G.x1, z])
  for (let x = G.x0 + 1; x < G.x1; x++) cells.push([x, G.z1])
  yardWall(c, cells, G.wallTop)
  // the walks (terracotta, the cross at x 13 and z 19), the beds, grass for the rest
  for (let z = 16; z < G.z1; z++) c.set(13, 0, z, P.paving)
  for (let x = G.x0 + 1; x < G.x1; x++) c.set(x, 0, 19, P.paving)
  OUTSIDE.beds.forEach(([x0, z0, x1, z1], i) => {
    for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
      c.set(x, 0, z, P.soil)
      if ((x + z) % 2 === 0) c.set(x, 1, z, OUTSIDE.flowers[i])
    }
  })
  acaciaTree(c, OUTSIDE.tree, { soil: P.soil, log: P.treeLog, leaves: P.treeLeaves })
  for (const [x, y, z, f] of OUTSIDE.seat) stair(c, x, y, z, `${P.wood}_stairs`, f)
  for (const [x, z] of OUTSIDE.lamps) { c.set(x, 0, z, P.paving); lampPost(c, x, 1, z, P.fence, 1) }
  const Bn = OUTSIDE.bench
  for (let x = Bn.x0; x <= Bn.x1; x++) { c.set(x, 0, Bn.z, P.paving); stair(c, x, 1, Bn.z, `${P.wood}_stairs`, 'south') }
  for (const x of [Bn.x0 - 1, Bn.x1 + 1]) { c.set(x, 0, Bn.z, P.paving); c.set(x, 1, Bn.z, P.mudBrick); c.set(x, 2, Bn.z, 'potted_dead_bush') }
  pave(c, G.x0 + 1, 16, G.x1 - 1, G.z1 - 1, P.soil)
}

// --- Level III: the ballroom under the thatch's T, the east yard ----------------------------------------------------
function levelThree(c, under) {
  const R = MASSES.ballroom, H = MASSES.hall
  walls(c, R, under, (x, z) => x === R.x0 && z >= H.z0 && z <= H.z1)
  // the hall's east wall now stands inside the thatch: carried up to it (walls() did, with the T's under())
  posts(c, MASSES.posts[3], MASSES.eave - 1)
  windows(c, WINDOWS[3], 3)
  c.fill(23, 2, 1, 23, 7, 1, P.post, { axis: 'y' }) // the great window's mullion, an acacia post
  for (let x = R.x0 + 1; x < R.x1; x++) c.set(x, 8, 1, paint)
  // the east yard
  const E = MASSES.gardenEast
  const cells = []
  for (let z = 17; z <= E.z1; z++) cells.push([E.x1, z])
  for (let x = E.x0 + 1; x < E.x1; x++) cells.push([x, E.z1])
  yardWall(c, cells, 3)
  for (let y = 1; y <= 3; y++) c.clear(OUTSIDE.gate.x, y, OUTSIDE.gate.z)
  for (const [x0, z0, x1, z1] of OUTSIDE.eastBeds) for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    c.set(x, 0, z, P.soil)
    if ((x + z) % 2 === 0) c.set(x, 1, z, 'orange_tulip')
  }
  const Pd = OUTSIDE.eastPond
  for (let x = Pd.x0 - 1; x <= Pd.x1 + 1; x++) for (let z = Pd.z0 - 1; z <= Pd.z1 + 1; z++) {
    c.set(x, 0, z, P.paving2)
    const edge = x === Pd.x0 - 1 || x === Pd.x1 + 1 || z === Pd.z0 - 1 || z === Pd.z1 + 1
    if (edge) c.set(x, 1, z, P.mudBrick)
    else c.set(x, 1, z, 'water', { level: '0' })
  }
  for (const [x, z] of [[22, 20], [24, 21]]) c.set(x, 2, z, 'lily_pad')
  for (const [x, z] of OUTSIDE.eastLamps) { c.set(x, 0, z, P.paving); lampPost(c, x, 1, z, P.fence, 1) }
  for (let z = R.z1 + 1; z < E.z1; z++) if (!c.get(23, 0, z)) c.set(23, 0, z, P.paving)
  for (let z = OUTSIDE.gate.z; z <= OUTSIDE.gate.z; z++) for (let x = E.x0 + 1; x < Pd.x0 - 1; x++) if (!c.get(x, 0, z)) c.set(x, 0, z, P.paving)
  pave(c, E.x0 + 1, R.z1 + 1, E.x1 - 1, E.z1 - 1, P.soil)
  pave(c, R.x0 - 1, 0, R.x1 + 1, 0)
  pave(c, R.x1 + 1, 0, R.x1 + 1, R.z1 + 1)
  pave(c, R.x0, R.z1 + 1, R.x1 + 1, R.z1 + 1)
}

// --- Level IV: the ribbed cellar -----------------------------------------------------------------------------------
function vault(c, [x0, z0, x1, z1]) {
  for (let x = x0; x <= x1; x++) for (const [z, f] of [[z0, 'north'], [z1, 'south']])
    if (!c.get(x, -1, z)) stair(c, x, -1, z, P.mudStair, f, 'top')
  for (let z = z0 + 1; z < z1; z++) if (!c.get(7, -1, z)) c.set(7, -1, z, P.mudBrick)
  for (const [x, f] of [[x0, 'east'], [x1, 'west']]) {
    for (let z = z0 + 1; z < z1; z++) c.set(x, -3, z, 'barrel', { facing: f, open: 'false' })
    for (let z = z0 + 2; z < z1 - 1; z++) c.set(x, -2, z, 'barrel', { facing: f, open: 'false' })
  }
}

export function draw(level = 1) {
  const c = level >= 4 ? new LiftedCanvas(SIZES[4], BASEMENT) : new Canvas(SIZES[level])
  const under = levelOne(c, level)
  if (level >= 2) levelTwo(c)
  if (level >= 3) levelThree(c, under)
  manorFloors(c, Math.min(level, 3), SHIPPED, ROOMS)
  // the ballroom is open to the thatch: its chandeliers hang from it on the chain it gives them
  const chains = {}
  if (level >= 3) for (const [x, y, z] of [[23, 6, 5], [23, 6, 9], [23, 9, 13]]) chains[[x, y, z].join()] = under(x, z) - y - 1
  const dug = manorFittings(c, level, SHIPPED, ROOMS, { fires: FIRES, chains, cellarVault: vault })
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
