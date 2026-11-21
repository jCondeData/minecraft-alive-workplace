// Raiders' Stockade, the recipe: assembles plan.mjs from the shared parts library (../parts) in passes (block-out and
// structure, openings, depth and detail, interior, yard, ground, light) and returns the structure for the
// minecraft-builder skill's runner. Also writes furniture.json (the furniture spots the parts recorded) for
// tools/blueprints/rebuild.py.
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
// The loot chest's LootTable is not carried by rebuild.py: loot.py writes it into the saved .nbt (see package.json).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, PINS, OPENINGS, FURNITURE, WALK, BRANCHES, OUTSIDE, GUARD_GROUND } from './plan.mjs'
import {
  Canvas, mix, slab, gableRoof, gableWall, gateGable, cartWheel, cartBed, ridgeTent, warStandard, logStack,
  furnitureParts, setBackPanels, serratedStakes, leverDoor, stiltLookout, innerWale, watchHat,
  proudFace, bareHoops, groundCloth,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const HANG = { hanging: 'true', waterlogged: 'false' }

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const footing = mix(P.footing, 11)
  const bank = mix(P.bank, 7)
  const ground = mix(P.ground, 5)
  const pal = MASSES.palisade, G = MASSES.gateTower, L = MASSES.lookout, T = MASSES.tent, H = MASSES.perch
  const O = pal.outer, S = pal.stakes
  const way = G.way
  const ringOf = ({ x0, z0, x1, z1 }) => { // wall order: front west -> east, east side, back east -> west, west side
    const r = []
    for (let x = x0; x <= x1; x++) r.push([x, z0])
    for (let z = z0 + 1; z <= z1; z++) r.push([x1, z])
    for (let x = x1 - 1; x >= x0; x--) r.push([x, z1])
    for (let z = z1 - 1; z > z0; z--) r.push([x0, z])
    return r
  }
  const inBox = (x, z, { x0, z0, x1, z1 }) => x >= x0 && x <= x1 && z >= z0 && z <= z1
  const gateFoot = (x, z) => x >= G.posts[0][0] && x <= G.posts[1][0] && z >= 0 && z <= G.posts[2][1] // the tower's footprint
  const inWay = (x, z) => x >= way.x0 && x <= way.x1 && z <= G.posts[2][1]
  const lookFoot = (x, z) => inBox(x, z, L)
  const perchFoot = (x, z) => inBox(x, z, H)

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // The palisade, in two lines. A rubble footing under both (the gateway stays open ground)
  for (const [x, z] of [...ringOf(O), ...ringOf(S)]) if (!inWay(x, z)) c.set(x, 0, z, footing)
  // The outer line: proud posts, spiked (skulls either side of the gate), a stepped bank of rubble between them
  const postAt = new Map(pal.posts.map(([x, z]) => [`${x},${z}`, true]))
  const corner = ({ x0, z0, x1, z1 }, x, z) => (x === x0 || x === x1) && (z === z0 || z === z1)
  proudFace(c, O, pal.y0, { log: P.frame, step: (x, z) => bank(x, 1, z) },
    (x, z) => {
      if (!postAt.has(`${x},${z}`)) return null
      if (pal.skulls.some(([a, b]) => a === x && b === z)) return { h: 6, tip: P.skull, tipProps: { rotation: '8' } } // facing out
      return { h: corner(O, x, z) ? 7 : 6, tip: P.stakeTip }
    },
    (x, z) => gateFoot(x, z) || (x === O.x0 && perchFoot(x, z) && (z === H.z0 || z === H.z1 || z === H.midPost[1])))
  // The stake line one block in: each stake one timber all the way up, mostly dark oak, a patched one here and there
  const patched = (x, z) => (x * 5 + z * 3) % 7 === 2 || (x * 3 + z * 5) % 11 === 4 // about one in four, unevenly
  const stakeOf = (x, y, z) => (patched(x, z) ? P.patch : P.stake)
  const stakeCells = ringOf(S).filter(([x, z]) => !gateFoot(x, z) && !(x === S.x1 && z >= L.z0 && z <= L.z1))
  serratedStakes(c, stakeCells, pal.y0, pal.rhythm, { log: stakeOf, tip: P.stakeTip },
    (i, x, z) => {
      if (corner(S, x, z)) return { h: 5, tip: true }
      if (perchFoot(x, z)) return { h: H.deckY - 1 } // under the perch's deck
      if (z === S.z0 && (x === G.posts[0][0] - 1 || x === G.posts[1][0] + 1)) return { h: 4 } // under the gate roof's eaves
      return null
    })
  // The stakes run on along the lookout's east side, the cage's outer wall (its front cell barred later)
  for (let z = L.z0 + 1; z < L.z1; z++) for (let y = 1; y <= MASSES.cage.y1; y++) c.set(S.x1, y, z, stakeOf(S.x1, y, z), { axis: 'y' })

  // Gate tower: four posts, boarded sides under a tie rail, lintels front and back, a fighting deck framed by tie
  // logs, its rail (the west side's back cell open: the ladder comes up there)
  for (const [x, z] of G.posts) for (let y = 1; y <= G.top; y++) c.set(x, y, z, P.frame, { axis: 'y' })
  const gz0 = G.posts[0][1], gz1 = G.posts[2][1], gx0 = G.posts[0][0], gx1 = G.posts[1][0]
  for (const x of [gx0, gx1]) for (let z = gz0 + 1; z < gz1; z++) {
    for (let y = 1; y < G.deckY - 1; y++) c.set(x, y, z, P.boards)
    c.set(x, G.deckY - 1, z, P.tie, { axis: 'z' })
  }
  for (let x = way.x0; x <= way.x1; x++) c.set(x, G.deckY - 1, gz0, P.frame, { axis: 'x' }) // the lintel over the gates
  for (let x = gx0; x <= gx1; x++) for (let z = gz0; z <= gz1; z++) {
    if (G.posts.some(([a, b]) => a === x && b === z)) continue
    const edge = z === gz0 || z === gz1 ? 'x' : x === gx0 || x === gx1 ? 'z' : null
    if (edge) c.set(x, G.deckY, z, P.tie, { axis: edge }); else c.set(x, G.deckY, z, P.deck)
  }
  // its breastwork: planks propped upright round the deck's edge (open trapdoors), boards up to the roof on the sides,
  // the ladder's way in through two panels swung back along the wall
  const gBox = { x0: gx0, z0: gz0, x1: gx1, z1: gz1 }
  const [ggx, ggz] = OPENINGS.gateLadder.gap
  const gEdge = []
  for (let x = gx0; x <= gx1; x++) for (let z = gz0; z <= gz1; z++) {
    const edge = x === gx0 || x === gx1 || z === gz0 || z === gz1
    if (edge && !G.posts.some(([a, b]) => a === x && b === z) && !(x === ggx && z === ggz)) gEdge.push([x, G.deckY + 1, z])
  }
  setBackPanels(c, gBox, gEdge, P.screen)
  for (const x of [gx0, gx1]) for (let z = gz0 + 1; z < gz1; z++) if (!(x === ggx && z === ggz)) c.set(x, G.deckY + 2, z, P.boards)
  for (const y of [G.deckY + 1, G.deckY + 2]) c.set(ggx, y, ggz, P.screen, { facing: 'north', half: 'bottom', open: 'true', powered: 'false', waterlogged: 'false' })

  // Lookout: four tall posts, the top plate, the deck (the cage's ceiling) edged with tie logs, a spruce breastwork
  // with the ladder's gap, shutters of trapdoors propped open above it
  const [lgx, lgz] = OPENINGS.lookoutLadder.gap
  stiltLookout(c, L, { y0: 1, deckY: L.deckY, topY: L.topY }, { post: P.frame, deck: P.deck, rail: P.boards }, [OPENINGS.lookoutLadder.gap])
  const edgeAxis = (b, x, z) => ((z === b.z0 || z === b.z1) ? 'x' : 'z')
  for (let x = L.x0; x <= L.x1; x++) for (let z = L.z0; z <= L.z1; z++) {
    const edge = x === L.x0 || x === L.x1 || z === L.z0 || z === L.z1
    if (edge && !corner(L, x, z)) c.set(x, L.deckY, z, P.tie, { axis: edgeAxis(L, x, z) })
  }
  const screen = []
  for (let x = L.x0; x <= L.x1; x++) for (let z = L.z0; z <= L.z1; z++) {
    const edge = x === L.x0 || x === L.x1 || z === L.z0 || z === L.z1
    if (edge && !corner(L, x, z) && !(x === lgx && z === lgz)) for (let y = L.deckY + 2; y < L.topY; y++) screen.push([x, y, z])
  }
  setBackPanels(c, L, screen, P.screen)
  for (let y = L.deckY + 1; y < L.topY; y++) c.set(lgx, y, lgz, P.screen, { facing: 'north', half: 'bottom', open: 'true', powered: 'false', waterlogged: 'false' })

  // The cage under it: barred toward the yard (front z 10, west x 13), the stake wall east, stakes at the back
  const cg = MASSES.cage
  for (let y = cg.y0; y <= cg.y1; y++) {
    for (const z of [L.z0 + 1, L.z0 + 2, L.z0 + 3]) c.set(L.x0, y, z, P.bars) // joined to the post in front, the boards behind
    c.set(L.x0, y, L.z1 - 1, P.boards) // the ladder's backing
    c.set(cg.inside.x1, y, L.z0 + 1, P.bars) // between the door's jamb and the stake wall
    for (let x = cg.inside.x0; x <= cg.inside.x1; x++) c.set(x, y, L.z1, stakeOf(x, y, L.z1), { axis: 'y' })
  }
  c.set(PINS.door[0], cg.y1, PINS.door[2], P.frame, { axis: 'x' }) // the lintel over the iron door
  for (let y = cg.y0; y <= cg.y1; y++) c.set(cg.inside.x1, y, L.z0, stakeOf(cg.inside.x1, y, L.z0), { axis: 'y' }) // the porch's east cheek

  // The perch over the west wall's middle: corner posts and the pinned log as its middle post, a deck hung out over
  // the bank, a rail with the ladder's gap, boarded north and south under the deck (the armoury), a hipped hat
  stiltLookout(c, H, { y0: 1, deckY: H.deckY, topY: H.topY }, { post: P.frame, deck: P.deck, rail: P.boards }, [OPENINGS.perchLadder.gap])
  for (let y = H.deckY + 1; y < H.topY; y++) c.set(OPENINGS.perchLadder.gap[0], y, OPENINGS.perchLadder.gap[1], P.screen, { facing: 'east', half: 'bottom', open: 'true', powered: 'false', waterlogged: 'false' })
  for (let z = H.z0 + 1; z < H.z1; z++) if (z !== H.midPost[1]) c.set(H.x0, H.deckY - 1, z, P.bracket, { facing: 'east', half: 'top', shape: 'straight', waterlogged: 'false' }) // corbels under the hoarding
  for (let y = 1; y <= H.topY; y++) c.set(H.midPost[0], y, H.midPost[1], P.frame, { axis: 'y' })
  for (let x = H.x0 + 2; x < H.x1; x++) for (const z of [H.z0, H.z1]) for (let y = 1; y < H.deckY; y++) c.set(x, y, z, P.boards)
  for (let x = H.x0; x <= H.x1; x++) for (let z = H.z0; z <= H.z1; z++) {
    const edge = x === H.x0 || x === H.x1 || z === H.z0 || z === H.z1
    if (edge && !corner(H, x, z) && !(x === H.midPost[0] && z === H.midPost[1])) c.set(x, H.deckY, z, P.tie, { axis: edgeAxis(H, x, z) })
  }
  const shutters = []
  for (let x = H.x0; x <= H.x1; x++) for (let z = H.z0; z <= H.z1; z++) {
    const edge = x === H.x0 || x === H.x1 || z === H.z0 || z === H.z1
    const [px, pz] = OPENINGS.perchLadder.gap
    if (edge && !corner(H, x, z) && !(x === H.midPost[0] && z === H.midPost[1]) && !(x === px && z === pz)) for (let y = H.deckY + 2; y < H.topY; y++) shutters.push([x, y, z])
  }
  setBackPanels(c, H, shutters, P.accentScreen)
  const [cap] = watchHat(c, { x0: H.x0, z0: H.z0, x1: H.x1, z1: H.z1, y: H.hatY }, P.roof, P.ridgeCap, H.hatY + 2)

  // Roofs: the gate tower's 45-degree roof with spruce gables front and back, the lookout's along z
  gateGable(c, { ...G.roof, faces: [gz0, gz1] }, P.roof, { infill: P.boards, king: P.frame, cap: P.ridgeCap })
  for (const [x, z] of G.posts) c.set(x, G.top, z, P.frame, { axis: 'y' }) // the posts carry the roof's foot
  for (const z of [gz0, gz1]) for (let x = gx0 + 1; x < gx1; x++) if (x !== (gx0 + gx1) / 2) c.set(x, G.roof.eaveY, z, P.tie, { axis: 'x' }) // the gable's tie beam
  const look = gableRoof(c, L.roof, P.roof)
  for (const z of [L.z0, L.z1]) // gables over the top plate, a king post in each
    gableWall(c, 'z', z, L.roof.across, L.topY + 1, look.under, (u, y) => (u === 15 ? [P.frame, { axis: 'y' }] : y === L.topY + 1 ? [P.tie, { axis: 'x' }] : P.boards))
  for (let z = L.roof.along[0]; z <= L.roof.along[1]; z++) slab(c, 15, look.ridgeY + 1, z, P.ridgeCap)

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  // The way in: three gates hung open (a villager can't open a gate, and the carter walks out through here)
  for (let x = way.x0; x <= way.x1; x++) c.set(x, 1, 0, P.gate, { facing: 'south', open: 'true', in_wall: 'false', powered: 'false' })
  // The cage's iron door with its jamb and the lever that really works it (the lever powers the jamb, which touches
  // the door's upper half)
  const d = OPENINGS.cageDoor
  const lever = leverDoor(c, d.at, { wall: d.wall, jambSide: d.jambSide, out: d.out }, { door: P.cageDoor, jamb: P.frame })
  if (lever.join() !== PINS.lever.join()) throw new Error(`lever at ${lever}, pinned at ${PINS.lever}`)
  // Ladders: up the lookout's west side, the gate tower's west side and the perch's north side
  for (const l of [OPENINGS.lookoutLadder, OPENINGS.gateLadder, OPENINGS.perchLadder])
    for (let y = l.y0; y <= l.y1; y++) c.set(l.x, y, l.z, 'ladder', { facing: l.facing, waterlogged: 'false' })

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  // The tent: the caravan's canvas over a dark ridge pole, the red stripe along its hems, the back shut, the mouth
  // to the fire; the ridge pole runs out a block over the mouth onto a forked pole
  const stripe = (x, y, z) => (y === T.y0 ? P.stripe : P.canvas)
  ridgeTent(c, { axis: T.axis, across: T.across, along: T.along, y0: T.y0 }, stripe, { back: 'l1', hem: P.hem })
  for (let z = T.ridge.z0; z <= T.ridge.z1; z++) c.set(T.ridge.x, T.ridge.y, z, P.tentRidge, { axis: 'z' })
  for (const [x, z] of T.forks) for (let y = 1; y < T.ridge.y; y++) c.set(x, y, z, P.rail)
  // The fire ring: the campfire in the middle, a curb of mossy rubble slabs, log benches on three sides
  const [fx, fy, fz] = PINS.fire
  c.set(fx, fy, fz, 'campfire', { facing: 'south', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  for (const [dx, dz] of [[-1, -1], [0, -1], [1, -1], [-1, 0], [1, 0], [-1, 1], [0, 1], [1, 1]]) slab(c, fx + dx, fy, fz + dz, P.fireCurb)
  logStack(c, [[6, 1, 7], [6, 1, 8], [6, 1, 9]], P.bench, 'z')
  logStack(c, [[12, 1, 8], [12, 1, 9], [12, 1, 10]], P.bench, 'z')
  logStack(c, [[8, 1, 11], [9, 1, 11], [10, 1, 11]], P.bench, 'x')
  // Spoils: the caravan's shipment piled against the back wall
  c.set(8, 1, 14, 'barrel', { facing: 'up', open: 'false' })
  c.set(8, 2, 14, 'barrel', { facing: 'north', open: 'false' })
  c.set(8, 1, 13, 'barrel', { facing: 'up', open: 'false' })
  c.set(9, 1, 14, 'white_wool'); c.set(9, 2, 14, 'red_wool'); c.set(9, 1, 13, 'light_gray_wool') // bolts of cloth
  logStack(c, [[10, 1, 13], [11, 1, 13], [10, 2, 13]], 'oak_log', 'x') // the timber they were carrying
  cartWheel(c, [11, 2, 14], 'z', { rim: P.wagon.rim, hubLog: P.wagon.hub })
  // The black standard over the lookout's ridge; the caravan's own white banner flown over the perch's hat
  warStandard(c, [15, look.ridgeY + 1, 11], { logHeight: 0, fenceHeight: 1, arm: null, rotation: 0 },
    { log: P.frame, fence: P.rail, banner: P.banner, lantern: 'lantern' })
  warStandard(c, [cap[0], cap[1] + 1, cap[2]], { logHeight: 0, fenceHeight: 1, arm: null, rotation: 4 },
    { log: P.frame, fence: P.rail, banner: P.captured, lantern: 'lantern' })

  // --- Pass 4: interior (the plan's furniture) --------------------------------------------------------------------
  for (const f of FURNITURE) {
    if (f.part) { furnitureParts[f.part](c, f); continue }
    const props = {
      chest: { facing: f.facing, type: 'single', waterlogged: 'false' }, barrel: { facing: 'up', open: 'false' },
      hay_block: { axis: 'y' }, lectern: { facing: f.facing, has_book: 'true', powered: 'false' },
      anvil: { facing: f.facing }, grindstone: { face: 'floor', facing: f.facing },
      spruce_stairs: { facing: f.facing, half: 'bottom', shape: 'straight', waterlogged: 'false' },
    }[f.block] ?? {}
    c.set(...f.pos, f.block, props)
  }
  for (const y of [2, 3]) c.set(16, y, 13, 'chain', { axis: 'y', waterlogged: 'false' }) // his shackle chain from the deck

  // --- Pass 5: yard -----------------------------------------------------------------------------------------------
  // The share-out: the caravan's cover spread on the ground between the gate and the fire, the goods sorted on it
  for (const [id, pos, props] of OUTSIDE.goods) c.set(...pos, id, props)
  groundCloth(c, MASSES.shareOut, P.cloth)
  // The caravan's cart, stripped: its bed down on the ground, the wheels off, the hoops bare (the cover is on the
  // ground), the load still aboard, the shafts dropped
  const W = MASSES.wagon
  cartBed(c, { ...W.bed, along: 'x' }, { beam: P.wagon.beam, deck: P.wagon.deck })
  cartWheel(c, W.wheel, 'z', { rim: P.wagon.rim, hubLog: P.wagon.hub }) // a wheel off, leaning on the front wall
  bareHoops(c, { xs: W.hoops, z0: W.bed.z0, z1: W.bed.z1, y: W.bed.y }, P.wagon.hoop)
  const wz = (W.bed.z0 + W.bed.z1) / 2 // the load left aboard: two barrels and a bolt of the caravan's cloth
  c.set(W.hoops[0], W.bed.y + 1, wz, 'barrel', { facing: 'east', open: 'false' })
  c.set(W.wheel[0], W.bed.y + 1, wz, 'barrel', { facing: 'up', open: 'false' })
  c.set(W.hoops[1], W.bed.y + 1, wz, P.canvas)
  for (const [x, z] of W.shafts) slab(c, x, 1, z, P.wagon.slab)
  // The woodpile against the front wall, a chopping stump, the horses' trough by the cage
  const wp = OUTSIDE.woodpile
  for (let y = 1; y <= wp.h; y++) for (let x = wp.x0; x <= wp.x1; x++) for (let z = wp.z0; z <= wp.z1; z++) c.set(x, y, z, 'spruce_log', { axis: 'z' })
  c.set(...OUTSIDE.stump, P.bench, { axis: 'y' })
  c.set(...OUTSIDE.trough, 'water_cauldron', { level: '3' })
  // The lashing rail along the stake line's inner face (it stops at anything already there: a tower, a pile)
  const skipWale = (x, z) => (z === S.z0 + 1 && x >= 5 && x <= 12) || (x === S.x0 + 1 && z >= 10) || (z === S.z1 - 1 && x <= 6)
    || (x === S.x1 - 1 && z >= L.z0) || (z === S.z1 - 1 && x >= L.x0)
  for (let z = S.z0 + 1; z < S.z1; z++) for (const x of [S.x0 + 1, S.x1 - 1]) if (!skipWale(x, z)) innerWale(c, [[x, z]], 3, P.wale, 'z')
  for (let x = S.x0 + 1; x < S.x1; x++) for (const z of [S.z0 + 1, S.z1 - 1]) if (!skipWale(x, z)) innerWale(c, [[x, z]], 3, P.wale, 'x')

  // --- Pass 6: ground (y 0 is the ground layer itself: never air) ---------------------------------------------------
  const lines = [...WALK, ...BRANCHES.flat()]
  const walk = new Set(lines.map(([x, , z]) => `${x},${z}`))
  for (const [x, , z] of lines) {
    const n = c.name(x, 1, z)
    const passable = n === 'minecraft:iron_door' || n.endsWith('_carpet') || (n.endsWith('fence_gate') && c.get(x, 1, z).props.open === 'true')
    if (c.get(x, 1, z) && !passable) throw new Error(`walking line blocked at ${x},1,${z}: ${n}`)
    if (c.get(x, 2, z) && c.name(x, 2, z) !== 'minecraft:iron_door') throw new Error(`walking line too low at ${x},2,${z}`)
  }
  for (let x = 0; x < SIZE[0]; x++) for (let z = 0; z < SIZE[2]; z++) {
    if (c.get(x, 0, z)) continue
    const over = c.get(x, 1, z)
    if (walk.has(`${x},${z}`) && (!over || over.id.endsWith('_carpet'))) c.set(x, 0, z, P.path)
    else c.set(x, 0, z, ground)
  }

  // --- Pass 7: light ----------------------------------------------------------------------------------------------
  c.set(14, 3, 9, 'lantern', HANG) // over the cage door, from the deck
  c.set(15, 3, 11, 'lantern', HANG) // in the cage, from the deck
  c.set(9, 3, 1, 'lantern', HANG) // in the gate's passage, from the deck
  c.set(9, G.roof.eaveY + 2, 2, 'lantern', HANG) // over the gate deck, from the ridge
  const hz = (H.z0 + H.z1) / 2, hx = (H.x0 + H.x1) / 2
  c.set(hx + 1, 2, hz, 'lantern', HANG) // in the armoury, from the perch's deck
  c.set(hx, H.hatY + 1, hz, 'lantern', HANG) // in the perch, from the hat's cap
  c.set(T.ridge.x, 2, 13, 'lantern', HANG) // in the tent, from the ridge pole
  for (const [x, y, z] of OUTSIDE.lamps) { c.set(x, y, z, P.rail); c.set(x, y + 1, z, P.rail); c.set(x, y + 2, z, 'lantern', { hanging: 'false', waterlogged: 'false' }) }
  for (const y of [9, 10]) c.set(15, y, 11, 'chain', { axis: 'y', waterlogged: 'false' }) // the lookout's lamp under the ridge
  c.set(15, 8, 11, 'lantern', HANG)

  // The guards' ground: nothing over y 1 in the 49 columns round the middle
  for (let x = GUARD_GROUND.x0; x <= GUARD_GROUND.x1; x++) for (let z = GUARD_GROUND.z0; z <= GUARD_GROUND.z1; z++)
    for (let y = 2; y < SIZE[1]; y++) if (c.get(x, y, z)) throw new Error(`guard ground covered at ${x},${y},${z}: ${c.name(x, y, z)}`)
  // The showcase's view into the cage: its camera stands at the door + (0.5, 1.4, -4.5), (15, 2.9, 6), looking at the
  // carter's cell; nothing over the ground layer's first block between it and the cage's front
  for (let x = 13; x <= 15; x++) for (let z = 6; z <= 8; z++) for (let y = 2; y <= 4; y++)
    if (c.get(x, y, z)) throw new Error(`the showcase's view into the cage is blocked at ${x},${y},${z}: ${c.name(x, y, z)}`)
  // The pins, by id
  const pinned = [['palisadeLog', 'dark_oak_log'], ['gate', 'dark_oak_fence_gate'], ['fire', 'campfire'], ['tentRidge', 'stripped_dark_oak_log'],
    ['bars', 'iron_bars'], ['lever', 'lever'], ['door', 'iron_door'], ['chest', 'chest'], ['hay', 'hay_block']]
  for (const [k, id] of pinned) if (c.name(...PINS[k]) !== `minecraft:${id}`) throw new Error(`pin ${k} at ${PINS[k]} is ${c.name(...PINS[k])}`)
  for (const y of [1, 2]) if (c.get(PINS.carter[0], y, PINS.carter[2])) throw new Error('the carter\'s cell is not clear')
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `raiders_stockade_cut${cut}` : 'raiders_stockade' } }
}
