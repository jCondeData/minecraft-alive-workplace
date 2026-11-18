// Raiders' Stockade, the recipe: assembles plan.mjs from the shared parts library (../parts) in passes (block-out and
// structure, openings, depth and detail, interior, ground, light) and returns the structure for the minecraft-builder
// skill's runner. Also writes furniture.json (the furniture spots the parts recorded) for tools/blueprints/rebuild.py.
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
// The loot chest's LootTable is not carried by rebuild.py: loot.py writes it into the saved .nbt (see package.json).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, PINS, OPENINGS, FURNITURE, WALK, BRANCHES, OUTSIDE, GUARD_GROUND } from './plan.mjs'
import {
  Canvas, mix, slab, stair, gableRoof, gableWall, gateGable, cartWheel, ridgeTent, warStandard, logStack,
  furnitureParts, setBackPanels, serratedStakes, leverDoor, stiltLookout, innerWale,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const footing = mix(P.footing, 11)
  const ground = mix(P.ground, 5)
  const pal = MASSES.palisade, G = MASSES.gateTower, L = MASSES.lookout, T = MASSES.tent
  const way = G.way

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // The palisade's ring, in wall order (front west -> east, east side, back east -> west, west side), corners once
  const ring = []
  for (let x = pal.x0; x <= pal.x1; x++) ring.push([x, pal.z0])
  for (let z = pal.z0 + 1; z <= pal.z1; z++) ring.push([pal.x1, z])
  for (let x = pal.x1 - 1; x >= pal.x0; x--) ring.push([x, pal.z1])
  for (let z = pal.z1 - 1; z > pal.z0; z--) ring.push([pal.x0, z])
  const corner = (x, z) => (x === pal.x0 || x === pal.x1) && (z === pal.z0 || z === pal.z1)
  const gatePost = (x, z) => z === pal.z0 && (x === 7 || x === 11)
  const inWay = (x, z) => z === pal.z0 && x >= way.x0 && x <= way.x1
  for (const [x, z] of ring) if (!inWay(x, z)) c.set(x, 0, z, footing) // a rubble footing the stakes are driven into
  // each stake one timber all the way up: mostly dark oak, a patched one here and there (the pinned log stays dark oak)
  const patched = (x, z) => (x * 5 + z * 3) % 7 === 2 || (x * 3 + z * 5) % 11 === 4 // about one in four, unevenly
  const stakeOf = (x, y, z) => (x === PINS.palisadeLog[0] && z === PINS.palisadeLog[2] ? P.stake : patched(x, z) ? P.patch : P.stake)
  serratedStakes(c, ring.filter(([x, z]) => !inWay(x, z) && !gatePost(x, z)), pal.y0, pal.rhythm,
    { log: stakeOf, tip: P.stakeTip },
    (i, x, z) => {
      if (corner(x, z)) return { h: 6, tip: true } // the corner posts stand a log proud, spiked
      if (z === pal.z0 && OUTSIDE.skulls.includes(x)) return { h: 4, tip: P.skull, props: { rotation: '8' } } // facing out
      if (z === pal.z0 && (x === 5 || x === 13)) return { h: 4 } // no spike against a skull
      return null
    })

  // Gate tower: four posts, boarded sides (the passage is a short tunnel), a fighting deck, its rail
  for (const [x, z] of G.posts) for (let y = 1; y <= G.top; y++) c.set(x, y, z, P.frame, { axis: 'y' })
  for (const x of [7, 11]) for (let y = 1; y < G.deckY; y++) c.set(x, y, 1, P.boards)
  for (let x = 7; x <= 11; x++) for (let z = 0; z <= 2; z++) if (!G.posts.some(([a, b]) => a === x && b === z)) c.set(x, G.deckY, z, P.deck)
  for (let x = 8; x <= 10; x++) { c.set(x, G.deckY + 1, 0, P.rail); c.set(x, G.deckY + 1, 2, P.rail) }
  c.set(11, G.deckY + 1, 1, P.rail) // the west side stays open: the ladder comes up there
  for (let x = 8; x <= 10; x++) c.set(x, G.deckY - 1, 0, P.frame, { axis: 'x' }) // the lintel over the way in, under the deck

  // Lookout: four tall posts, the top plate, the deck (the cage's ceiling), the rail with the ladder's gap
  stiltLookout(c, L, { y0: 1, deckY: L.deckY, topY: L.topY }, { post: P.frame, deck: P.deck, rail: P.boards }, [OPENINGS.lookoutLadder.gap])
  // above the board breastwork, shutters of trapdoors hung open on the inside (propped up for the watch);
  // the ladder's gap gets two panels swung back along the wall so a player can step in
  const screen = []
  for (let x = L.x0; x <= L.x1; x++) for (let z = L.z0; z <= L.z1; z++) {
    const edge = x === L.x0 || x === L.x1 || z === L.z0 || z === L.z1
    const post = (x === L.x0 || x === L.x1) && (z === L.z0 || z === L.z1)
    const [gx, gz] = OPENINGS.lookoutLadder.gap
    if (edge && !post && !(x === gx && z === gz)) for (let y = L.deckY + 2; y < L.topY; y++) screen.push([x, y, z])
  }
  setBackPanels(c, L, screen, P.screen)
  for (let y = L.deckY + 1; y < L.topY; y++) {
    const [gx, gz] = OPENINGS.lookoutLadder.gap
    c.set(gx, y, gz, P.screen, { facing: 'north', half: 'bottom', open: 'true', powered: 'false', waterlogged: 'false' })
  }

  // The cage under it: barred toward the yard (front z 10, west x 13), boarded at the back and east
  const cg = MASSES.cage
  for (let y = cg.y0; y <= cg.y1; y++) {
    c.set(13, y, 10, P.bars) // joined to the lookout's post in front of it and the bars behind: never ending in the air
    for (const z of [11, 12]) c.set(13, y, z, P.bars)
    c.set(13, y, 13, P.boards) // the ladder's backing
    c.set(16, y, 10, P.bars)
    c.set(17, y, 10, P.bars)
    for (const z of [11, 12, 13]) c.set(17, y, z, P.boards)
    for (const x of [14, 15, 16]) c.set(x, y, 14, x === 15 && y === 2 ? P.bars : P.boards) // one barred peep-hole
  }
  c.set(14, 3, 10, P.frame, { axis: 'x' }) // the lintel over the iron door

  // Roofs: the gate tower's 45-degree roof with a boarded gable front and back, the lookout's along z
  gateGable(c, { ...G.roof, faces: [0, 2] }, P.roof, { infill: P.boards, king: P.frame, cap: P.ridgeCap })
  for (const [x, z] of G.posts) c.set(x, G.top, z, P.frame, { axis: 'y' }) // the posts carry the roof's foot
  const look = gableRoof(c, L.roof, P.roof)
  for (const z of [L.z0, L.z1]) // gables over the top plate, a king post in each
    gableWall(c, 'z', z, L.roof.across, L.topY + 1, look.under, u => (u === 15 ? [P.frame, { axis: 'y' }] : P.boards))
  for (let z = L.roof.along[0]; z <= L.roof.along[1]; z++) slab(c, 15, look.ridgeY + 1, z, P.ridgeCap)

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  // The way in: three gates hung open (a villager can't open a gate, and the carter walks out through here)
  for (const x of [8, 9, 10]) c.set(x, 1, 0, P.gate, { facing: 'south', open: 'true', in_wall: 'false', powered: 'false' })
  // The cage's iron door with its jamb and the lever that really works it (the lever powers the jamb, which touches
  // the door's upper half)
  const d = OPENINGS.cageDoor
  const lever = leverDoor(c, d.at, { wall: d.wall, jambSide: d.jambSide, out: d.out }, { door: P.cageDoor, jamb: P.frame })
  if (lever.join() !== PINS.lever.join()) throw new Error(`lever at ${lever}, pinned at ${PINS.lever}`)
  // Ladders: up the lookout's west side, and up the gate tower's west side
  for (const l of [OPENINGS.lookoutLadder, OPENINGS.gateLadder])
    for (let y = l.y0; y <= l.y1; y++) c.set(l.x, y, l.z, 'ladder', { facing: l.facing, waterlogged: 'false' })

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  // The lashing rail along the palisade's inner face (it stops at anything already there)
  const wale = []
  for (let z = 1; z < pal.z1; z++) wale.push([[1, z], 'z'], [[17, z], 'z'])
  for (let x = 1; x < pal.x1; x++) wale.push([[x, 15], 'x'], [[x, 1], 'x'])
  const skipWale = (x, z) => (z === 1 && x >= 6 && x <= 12) || (x === 17 && z >= 8) || (z === 15 && x >= 13)
  for (const [[x, z], axis] of wale) if (!skipWale(x, z)) innerWale(c, [[x, z]], 3, P.wale, axis)
  // The tent: the caravan's canvas over a dark ridge pole, the red stripe along its hems, the back shut, the mouth
  // to the fire; the ridge pole runs out a block over the mouth onto a forked pole
  const stripe = (x, y, z) => (y === T.y0 ? P.stripe : P.canvas)
  ridgeTent(c, { axis: T.axis, across: T.across, along: T.along, y0: T.y0 }, stripe, { back: 'l1', hem: P.hem })
  for (let z = T.ridge.z0; z <= T.ridge.z1; z++) c.set(T.ridge.x, T.ridge.y, z, P.tentRidge, { axis: 'z' })
  for (let y = 1; y < T.ridge.y; y++) c.set(T.ridge.x, y, T.ridge.z0, P.rail)
  // The fire ring: the campfire in the middle, a curb of mossy rubble slabs, log benches on three sides
  const [fx, fy, fz] = PINS.fire
  c.set(fx, fy, fz, 'campfire', { facing: 'south', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  for (const [dx, dz] of [[-1, -1], [0, -1], [1, -1], [-1, 0], [1, 0], [-1, 1], [0, 1], [1, 1]]) slab(c, fx + dx, fy, fz + dz, P.fireCurb)
  logStack(c, [[6, 1, 7], [6, 1, 8], [6, 1, 9]], P.bench, 'z')
  logStack(c, [[12, 1, 7], [12, 1, 8], [12, 1, 9]], P.bench, 'z')
  logStack(c, [[8, 1, 11], [9, 1, 11], [10, 1, 11]], P.bench, 'x')
  // Spoils: the caravan's shipment piled against the back wall
  c.set(7, 1, 15, 'barrel', { facing: 'up', open: 'false' })
  c.set(8, 1, 15, 'barrel', { facing: 'north', open: 'false' })
  c.set(8, 2, 15, 'barrel', { facing: 'up', open: 'false' })
  c.set(8, 1, 14, 'barrel', { facing: 'up', open: 'false' })
  c.set(9, 1, 15, 'white_wool'); c.set(9, 2, 15, 'red_wool'); c.set(9, 1, 14, 'light_gray_wool') // bolts of cloth
  logStack(c, [[9, 1, 13], [10, 1, 13], [11, 1, 13], [10, 2, 13]], 'oak_log', 'x') // the timber they were carrying
  cartWheel(c, [11, 2, 15], 'z', { rim: { full: 'spruce_planks', stairs: 'spruce_stairs' }, hubLog: 'stripped_spruce_log' })
  // The black standard over the lookout's ridge, a lantern on its arm
  warStandard(c, [15, look.ridgeY + 1, 11], { logHeight: 0, fenceHeight: 1, arm: null, rotation: 0 },
    { log: P.frame, fence: P.rail, banner: P.banner, lantern: 'lantern' })

  // --- Pass 4: interior (the plan's furniture) --------------------------------------------------------------------
  for (const f of FURNITURE) {
    if (f.part) { furnitureParts[f.part](c, f); continue }
    const props = f.block === 'chest' ? { facing: f.facing, type: 'single', waterlogged: 'false' }
      : f.block === 'barrel' ? { facing: 'up', open: 'false' } : f.block === 'hay_block' ? { axis: 'y' } : {}
    c.set(...f.pos, f.block, props)
  }
  // His shackle chain hanging from the deck over the cage's back corner
  for (const y of [2, 3]) c.set(16, y, 13, 'chain', { axis: 'y', waterlogged: 'false' })

  // --- Pass 5: yard -----------------------------------------------------------------------------------------------
  const wp = OUTSIDE.woodpile
  logStack(c, Array.from({ length: wp.z1 - wp.z0 + 1 }, (_, i) => [wp.x, 1, wp.z0 + i]), 'spruce_log', 'x')
  logStack(c, Array.from({ length: wp.z1 - wp.z0 + 1 }, (_, i) => [wp.x, 2, wp.z0 + i]), 'spruce_log', 'x') // stacked up to the lashing rail
  c.set(...OUTSIDE.target.post, P.rail); c.set(OUTSIDE.target.post[0], 2, OUTSIDE.target.post[2], 'target') // the archers' butt
  c.set(3, 1, 4, P.bench, { axis: 'y' }) // a chopping block
  const hr = OUTSIDE.hitchRail
  for (let x = hr.x0; x <= hr.x1; x++) c.set(x, 1, hr.z, P.rail)
  c.set(...OUTSIDE.trough, 'water_cauldron', { level: '3' })

  // --- Pass 6: ground (y 0 is the ground layer itself: never air) ---------------------------------------------------
  const walk = new Set([...WALK, ...BRANCHES.flat()].map(([x, , z]) => `${x},${z}`))
  for (const [x, , z] of [...WALK, ...BRANCHES.flat()]) {
    const passable = n => n === 'minecraft:iron_door' || (n.endsWith('fence_gate') && c.get(x, 1, z).props.open === 'true')
    if (c.get(x, 1, z) && !passable(c.name(x, 1, z))) throw new Error(`walking line blocked at ${x},1,${z}: ${c.name(x, 1, z)}`)
    if (c.get(x, 2, z) && c.name(x, 2, z) !== 'minecraft:iron_door') throw new Error(`walking line too low at ${x},2,${z}`)
  }
  for (let x = 0; x < SIZE[0]; x++) for (let z = 0; z < SIZE[2]; z++) {
    if (c.get(x, 0, z)) continue
    const open = !c.get(x, 1, z)
    if (walk.has(`${x},${z}`) && open) c.set(x, 0, z, P.path)
    else c.set(x, 0, z, ground)
  }

  // --- Pass 7: light ----------------------------------------------------------------------------------------------
  c.set(14, 3, 9, 'lantern', { hanging: 'true', waterlogged: 'false' }) // over the cage door, from the deck
  c.set(9, 3, 1, 'lantern', { hanging: 'true', waterlogged: 'false' }) // in the gate's passage, from the deck
  for (const [x, y, z] of OUTSIDE.lamps) { c.set(x, y, z, P.rail); c.set(x, y + 1, z, P.rail); c.set(x, y + 2, z, 'lantern', { hanging: 'false', waterlogged: 'false' }) }
  for (const y of [9, 10]) c.set(15, y, 11, 'chain', { axis: 'y', waterlogged: 'false' }) // the lookout's lamp under the ridge
  c.set(15, 8, 11, 'lantern', { hanging: 'true', waterlogged: 'false' })

  // The guards' ground: nothing over y 1 in the 49 columns round the middle
  for (let x = GUARD_GROUND.x0; x <= GUARD_GROUND.x1; x++) for (let z = GUARD_GROUND.z0; z <= GUARD_GROUND.z1; z++)
    for (let y = 2; y < SIZE[1]; y++) if (c.get(x, y, z)) throw new Error(`guard ground covered at ${x},${y},${z}: ${c.name(x, y, z)}`)
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
