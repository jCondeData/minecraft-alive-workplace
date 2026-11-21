// Ranch, savanna look (49.10e), the recipe: assembles plan.mjs from the parts library (../parts) in passes (block-out
// and structure, roofs, openings, depth and detail, interior, ground) for LEVEL = 1 or 2, Level II all of Level I but
// the skirt's east eave, then the bunk house and the kraal. Writes furniture.json. Build both: build.sh. CUT=<y>: a
// cutaway.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  SIZE, PALETTE, STABLE, SKIRT, LOFT, CROWN, HOOD, STACK, STALLS, GATE, YARD, BUNK, WOODPILE, KRAAL, OPENINGS, FURNITURE,
} from './plan.mjs'
import {
  Canvas, mix, slab, stair, plinth, floor, post, beamRing, wallPanel, window, door, chimney, railingRun, furnitureParts,
  doubleChest, hipRoof, horseTrough, stallFront, paddockFence, acaciaTree,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const POT = { facing: 'north', cracked: 'false', waterlogged: 'false' }

const pieces = {
  ...furnitureParts,
  doubleChest: (c, f) => doubleChest(c, f.pos, f.facing, f.along),
  // the shelf by the smoker with a pot plant on it (a plant, not crockery): a young acacia in its pot
  shelf: (c, f) => {
    furnitureParts.shelf(c, f)
    const [x, y, z] = f.pos; c.free([x, y + 1, z]); c.set(x, y + 1, z, 'potted_acacia_sapling')
  },
}

// round 3, note 1: the skirt falls from the loft wall to the eave like a roof, half a block a ring (2.5 blocks over its
// four rings): a stair at the eave (y 4, facing in; rebuild.py's finish() turns the corners into hip corners), a bottom
// slab and then a full block at y 5, and a stair against the loft at y 6 (the stalls' lamps hang from it). Its hips run
// diagonally through the slab and block rings, with no stepped corner.
function skirt(c, { x0, z0, x1, z1, eaveY }, roof, skip) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (skip(x, z)) continue
    const d = Math.min(x - x0, x1 - x, z - z0, z1 - z)
    const face = d === x - x0 ? 'east' : d === x1 - x ? 'west' : d === z - z0 ? 'south' : 'north'
    if (d === 0) stair(c, x, eaveY, z, roof.stairs, face)
    else if (d === 1) slab(c, x, eaveY + 1, z, roof.slab, 'bottom')
    else if (d === 2) c.set(x, eaveY + 1, z, roof.full) // a full block, not a top slab (the same height, and it reads in every render)
    else stair(c, x, eaveY + 2, z, roof.stairs, face)
  }
}

// the painted band: red and white in turn along a wall line
function band(c, [ax, az], [bx, bz], y, P, skip = () => false) {
  for (let x = Math.min(ax, bx); x <= Math.max(ax, bx); x++) for (let z = Math.min(az, bz); z <= Math.max(az, bz); z++)
    if (!skip(x, z)) c.set(x, y, z, P.paint[(x + z) % 2])
}

export function draw(level = 1) {
  const c = new Canvas(SIZE[level])
  const P = PALETTE
  const granite = mix(P.granite, 5), mud = mix(P.mud, 6), terra = mix(P.plinth, 7)
  const S = STABLE, L = LOFT, A = L.aisle
  const inLoft = (x, z) => x >= L.x0 && x <= L.x1 && z >= L.z0 && z <= L.z1

  // --- Pass 1: block-out and structure -----------------------------------------------------------------------------
  // the plinth under the whole stable, the aisle paved, the stalls' earth floors
  plinth(c, S.x0, S.z0, S.x1, S.z1, terra)
  floor(c, A.x0, A.z0, A.x1, A.z1, 0, P.aisle)
  for (const [a, b] of [STALLS.west, STALLS.east]) floor(c, a, S.z0 + 1, b, S.z1 - 1, 0, P.stallFloor)
  // the stable's granite walls (y 1..3): the stalls' outer walls and the front and back walls right across
  for (const x of [S.x0, S.x1]) wallPanel(c, [x, S.z0], [x, S.z1], 1, S.wallTop, granite)
  for (const z of [S.z0, S.z1]) wallPanel(c, [S.x0, z], [S.x1, z], 1, S.wallTop, granite)
  for (const x of [S.x0, S.x1]) for (const z of S.posts) post(c, x, z, 1, S.postTop, P.frame)
  // the loft rising out of it: posts the full height, the beam at the loft floor, packed mud above, the beam ring
  for (const x of [L.x0, L.x1]) for (const z of L.posts) post(c, x, z, 1, L.paintY - 1, P.frame)
  for (const x of [L.x0, L.x1]) for (let z = L.z0 + 1; z < L.z1; z++) if (!L.posts.includes(z)) c.set(x, L.beamY, z, P.frame, { axis: 'z' })
  for (const z of [L.z0, L.z1]) for (let x = L.x0 + 1; x < L.x1; x++) c.set(x, L.beamY, z, P.frame, { axis: 'x' })
  const loftSides = [[[L.x0, L.z0], [L.x1, L.z0]], [[L.x0, L.z1], [L.x1, L.z1]], [[L.x0, L.z0], [L.x0, L.z1]], [[L.x1, L.z0], [L.x1, L.z1]]]
  const isPost = (x, z) => (x === L.x0 || x === L.x1) && L.posts.includes(z)
  for (const [a, b] of loftSides) {
    for (let x = Math.min(a[0], b[0]); x <= Math.max(a[0], b[0]); x++) for (let z = Math.min(a[1], b[1]); z <= Math.max(a[1], b[1]); z++) {
      if (isPost(x, z)) continue
      for (let y = L.mudY[0]; y < L.paintY; y++) c.set(x, y, z, mud)
    }
    band(c, a, b, L.paintY, P) // one unbroken course over the posts too (round 2, note 4)
  }
  beamRing(c, L.x0, L.z0, L.x1, L.z1, L.ringY, P.frame)
  floor(c, A.x0, A.z0, A.x1, A.z1, L.floorY, P.loftFloor)
  // the painted band across the stalls' front, under the skirt's front pent (between the corner posts)
  // the stalls' band right round the stable under the skirt, over the posts (round 2, note 4); the big doors break it
  for (const [a, b] of [[[S.x0, S.z0], [S.x1, S.z0]], [[S.x0, S.z1], [S.x1, S.z1]], [[S.x0, S.z0], [S.x0, S.z1]], [[S.x1, S.z0], [S.x1, S.z1]]])
    band(c, a, b, S.paintY, P, (x, z) => inLoft(x, z))

  // --- Pass 2: the roofs: the skirt (a hip round the loft), the crown (a hip over it), a clay pot on the ridge ----------
  skirt(c, SKIRT, P.roof, inLoft)
  hipRoof(c, CROWN, P.roof)
  c.set(...CROWN.finial, P.finial, POT)

  // --- Pass 3: openings ----------------------------------------------------------------------------------------------
  const bd = OPENINGS.barnDoor
  for (const [x, z] of bd.leaves) for (let y = 1; y <= 3; y++) c.set(x, y, z, P.leaf)
  c.set(bd.at[0], 3, bd.at[2], P.frame, { axis: 'x' }) // the wicket's lintel
  door(c, bd.at, bd.facing, bd.out, { wood: P.door, step: bd.step, stepStair: P.step })
  const hd = OPENINGS.hayDoor
  door(c, hd.at, hd.facing, hd.out, { wood: P.door })
  for (const x of [hd.at[0] - 1, hd.at[0] + 1]) for (const y of [hd.at[1], hd.at[1] + 1]) c.set(x, y, hd.at[2], P.jamb) // white jambs
  const cl = OPENINGS.clerestory
  for (const x of cl.xs) for (const z of cl.zs) for (const y of cl.ys) c.set(x, y, z, P.glass)
  const sw = OPENINGS.stallWindows
  for (const x of sw.xs) for (const z of sw.zs) c.set(x, sw.y, z, P.bars)
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.shutter })
  // the stall fronts off the aisle (boards, a gate, a rail over them), the partitions between each side's two stalls
  stallFront(c, L.x0, STALLS.list, 1, 'east', { board: P.board, gate: `${P.fence}_fence_gate`, rail: `${P.fence}_fence` })
  stallFront(c, L.x1, STALLS.list, 1, 'west', { board: P.board, gate: `${P.fence}_fence_gate`, rail: `${P.fence}_fence` })
  for (const [a, b] of [STALLS.west, STALLS.east]) for (let x = a; x <= b; x++) {
    c.set(x, 1, STALLS.partition, P.board); c.set(x, 2, STALLS.partition, `${P.fence}_fence`)
  }
  const ld = OPENINGS.ladder
  for (let y = ld.y0; y <= ld.top; y++) c.set(ld.x, y, ld.z, 'ladder', { facing: ld.facing, waterlogged: 'false' })

  // --- Pass 4: depth and detail --------------------------------------------------------------------------------------
  // the hay hood (a thatch hood over the hay door) and the hoist beam out over it, its chain and lantern
  for (const [x, y, z] of HOOD.stairs) stair(c, x, y, z, P.roof.stairs, 'south')
  const hb = HOOD.beam // round 3, note 2: through the hood, under the band, which runs on unbroken
  for (let z = hb.z0; z <= hb.z1; z++) c.set(hb.x, hb.y, z, P.frame, { axis: 'z' })
  c.set(...HOOD.chain, 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(...HOOD.lamp, 'lantern', { hanging: 'true', waterlogged: 'false' })
  // the smoker's stack up the back through both eaves
  chimney(c, STACK.x, STACK.z, 0, STACK.top, P.stack)
  // lanterns under the front pent either side of the wicket door
  for (const [x, y, z] of YARD.lamps) c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })

  // --- Pass 5: interior: the shipped Ranch's furniture, cell for cell; hay in the stalls and the loft ------------------
  for (const f of FURNITURE[1]) pieces[f.part](c, f)
  for (const [a, b] of [STALLS.west, STALLS.east]) {
    const outer = a === STALLS.west[0] ? a : b
    c.set(outer, 1, 4, P.hay, { axis: 'y' }); c.set(outer, 1, 10, P.hay, { axis: 'y' })
  }
  for (const x of [A.x0, A.x1]) for (let z = 6; z <= A.z1; z++) {
    c.set(x, L.floorY + 1, z, P.hay, { axis: 'y' })
    if (z >= 8) c.set(x, L.floorY + 2, z, P.hay, { axis: 'y' })
  }
  c.set(A.x0, L.floorY + 3, 9, P.hay, { axis: 'y' })

  // --- Pass 6: ground: the kraal gate, the yard's rails, trough and hay -------------------------------------------------
  const G = GATE
  // round 2, note 2: slender fence poles and a slab cap, so the wicket door reads through the gate
  for (const x of G.poles) for (let y = 0; y <= G.top; y++) c.set(x, y, G.z, `${P.fence}_fence`)
  for (let x = G.beam[0]; x <= G.beam[1]; x++) c.set(x, G.beamY, G.z, P.frame, { axis: 'x' })
  for (let x = G.beam[0]; x <= G.beam[1]; x++) if (x !== G.finial[0]) slab(c, x, G.beamY + 1, G.z, P.roof.slab, 'bottom')
  c.set(G.finial[0], G.beamY + 1, G.z, P.roof.full)
  c.set(...G.finial, P.finial, POT)
  for (const [x, y, z] of G.lamps) c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
  for (const run of YARD.rails) railingRun(c, run, P.fence)
  horseTrough(c, YARD.trough, 0, P.rim)
  for (const [x, y, z] of YARD.hay) c.set(x, y, z, P.hay, { axis: 'y' })
  c.set(...YARD.jar, P.finial, POT)

  if (level >= 2) drawLevel2(c, P, { granite, mud, terra })
  return c
}

// Level II: the bunk house east of the stable, the woodpile between, the kraal behind; nothing of Level I moves but the
// skirt's east eave (x 12) beside the bunk house, which gives way to the two roofs' valley.
function drawLevel2(c, P, { mud, terra }) {
  const B = BUNK, W = B.walls, R = B.room
  for (let z = B.roof.z0; z <= B.roof.z1; z++) c.clear(SKIRT.x1, SKIRT.eaveY, z)
  plinth(c, W.x0, W.z0, W.x1, W.z1, terra)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.bunkFloor)
  const sides = [[[W.x0, W.z0], [W.x1, W.z0]], [[W.x0, W.z1], [W.x1, W.z1]], [[W.x0, W.z0], [W.x0, W.z1]], [[W.x1, W.z0], [W.x1, W.z1]]]
  const isPost = (x, z) => B.posts.some(([a, b]) => a === x && b === z)
  for (const [a, b] of sides) {
    for (let x = Math.min(a[0], b[0]); x <= Math.max(a[0], b[0]); x++) for (let z = Math.min(a[1], b[1]); z <= Math.max(a[1], b[1]); z++)
      if (!isPost(x, z)) for (let y = 1; y < B.paintY; y++) c.set(x, y, z, mud)
    band(c, a, b, B.paintY, P) // unbroken (round 2, note 4)
  }
  for (const [x, z] of B.posts) post(c, x, z, 1, B.wallTop - 1, P.frame)
  hipRoof(c, B.roof, P.roof)
  c.set(...B.finial, P.finial, POT)

  const o = OPENINGS.bunk
  door(c, o.door.at, o.door.facing, o.door.out, { wood: P.door, step: o.door.step, stepStair: P.step })
  c.set(...B.lamp, 'lantern', { hanging: 'true', waterlogged: 'false' })
  for (const w of o.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.shutter })

  for (const f of FURNITURE[2]) pieces[f.part](c, f)
  for (const [x, y, z] of B.hay) c.set(x, y, z, P.hay, { axis: 'y' })
  // the woodpile in the slot between the stalls and the bunk house, up under the valley
  const Wp = WOODPILE
  for (let z = Wp.z0; z <= Wp.z1; z++) for (let y = 0; y <= Wp.y1; y++) c.set(Wp.x, y, z, P.firewood, { axis: 'x' })

  // the kraal: acacia rails on a kerb, the man-gap, the herd's double gate, lamp posts at its corners
  const K = KRAAL
  paddockFence(c, { ring: K.ring, manGap: K.manGap, gates: K.gates, gateFacing: 'east', lampPosts: K.lampPosts },
    { fence: `${P.fence}_fence`, gate: `${P.fence}_fence_gate` })
  acaciaTree(c, K.tree, P.tree)
  // the shelter: four poles under a thatch pyramid, boarded under its point, a pot on it, hay under it
  const S = K.shelter
  for (const [x, z] of S.posts) post(c, x, z, 0, S.eaveY, P.frame)
  hipRoof(c, { x0: S.x0, z0: S.z0, x1: S.x1, z1: S.z1, eaveY: S.eaveY }, P.roof)
  for (let x = S.x0 + 1; x <= S.x1 - 1; x++) for (let z = S.z0 + 1; z <= S.z1 - 1; z++)
    if (!c.get(x, S.eaveY, z)) slab(c, x, S.eaveY, z, `${P.fence}_slab`, 'top')
  c.set(S.finial[0], S.eaveY + 1, S.finial[2], P.roof.full) // the point filled under its top
  c.set(...S.finial, P.finial, POT)
  for (const [x, y, z] of S.hay) c.set(x, y, z, P.hay, { axis: 'y' })
  horseTrough(c, K.trough, 0, P.rim)
  for (let y = 0; y <= 2; y++) c.set(K.snubbingPost[0], y, K.snubbingPost[1], P.frame, { axis: 'y' })
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const id = level > 1 ? `ranch_${level}` : 'ranch'
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
