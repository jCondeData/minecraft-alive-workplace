// Inn (Version A), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes (block-out and
// structure, openings, depth and detail, interior, ground) for LEVEL = 1..3, each level drawn as the level before plus
// what it adds (II: the coach yard, gallery and stable; III: the ale cellar). Writes furniture.json (the level's
// furniture spots) for tools/blueprints/rebuild.py. CUT=<y> cuts everything above y.
//   LEVEL=2 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955   (build.sh: all three)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { IDS, SIZES, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE, CELLAR } from './plan.mjs'
import {
  LiftedCanvas, mix, stair, slab, plinth, floor, post, beamRing, wallPanel, window, door, doorway, quoins, gableRoof,
  gableWall, dormer, chimney, flowerBed, flight, hatchLadder, cellar, lampPost, furnitureParts, railingRun,
  jetty, barRun, turningStair, gallery, stalls, caskRack, signPost, gatePiers,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const stone = mix(P.stone, 3)
P.infill = typeof P.infill === 'string' ? P.infill : mix(P.infill, 4)
const rubble = mix(P.plinth, 5)
const yardMix = mix(P.yard, 9)
const T = MASSES.taproom, C = MASSES.chambers, R = MASSES.roof, TW = MASSES.tower, S = MASSES.scullery
const log = (axis) => [P.frame, { axis }]

/** A timber wall line (infill) from a to b at y0..y1, frame posts at `posts` (along the line) carried through. */
function timberWall(c, axis, line, [u0, u1], y0, y1, posts) {
  for (let u = u0; u <= u1; u++) for (let y = y0; y <= y1; y++) {
    const [x, z] = axis === 'x' ? [u, line] : [line, u]
    if (posts.includes(u)) c.set(x, y, z, P.frame, { axis: 'y' })
    else c.set(x, y, z, P.infill)
  }
}

function drawLevel(level) {
  const c = new LiftedCanvas(SIZES[level], BASEMENT[level] ?? 0)
  const at = l => level >= l
  const tw = T.walls, cw = C.walls

  // --- Pass 1: block-out and structure ------------------------------------------------------------------------------
  // The Taproom: a rubble plinth, spruce boards, stone walls with dressed corners
  plinth(c, tw.x0, tw.z0, tw.x1, tw.z1, rubble)
  floor(c, T.room.x0, T.room.z0, T.room.x1, T.room.z1, 0, P.floor)
  for (let x = tw.x0; x <= tw.x1; x++) c.set(x, 0, 1, P.apron) // the paving under the jetty
  for (let z = tw.z0; z <= tw.z1; z++) { c.set(tw.x0 - 1, 0, z, P.apron); c.set(tw.x1 + 1, 0, z, P.apron) } // a skirt the side sills stand on
  wallPanel(c, [tw.x0, tw.z0], [tw.x1, tw.z0], 1, T.top, stone)
  wallPanel(c, [tw.x0, tw.z1], [tw.x1, tw.z1], 1, T.top, stone)
  wallPanel(c, [tw.x0, tw.z0], [tw.x0, tw.z1], 1, T.top, stone)
  wallPanel(c, [tw.x1, tw.z0], [tw.x1, tw.z1], 1, T.top, stone)
  quoins(c, [[tw.x0, tw.z0], [tw.x1, tw.z0], [tw.x0, tw.z1], [tw.x1, tw.z1]], 1, T.top, P.quoin, 'stone_bricks')

  // The Chambers: the beam ring at y 5 (the jetty's beam at the front), boards, timber walls, a boarded ceiling
  beamRing(c, cw.x0, cw.z0, cw.x1, cw.z1, C.floorY, P.frame)
  floor(c, C.room.x0, C.room.z0, C.room.x1, C.room.z1, C.floorY, P.floor)
  for (let x = C.room.x0; x <= C.room.x1; x++) c.set(x, C.floorY, 5, ...log('x')) // the summer beam over the taproom
  timberWall(c, 'x', cw.z0, [cw.x0, cw.x1], 6, C.top, C.frontPosts)
  timberWall(c, 'x', cw.z1, [cw.x0, cw.x1], 6, C.top, C.backPosts)
  timberWall(c, 'z', cw.x0, [cw.z0, cw.z1], 6, C.top - 1, C.sidePosts)
  timberWall(c, 'z', cw.x1, [cw.z0, cw.z1], 6, C.top - 1, C.sidePosts)
  for (let z = cw.z0; z <= cw.z1; z++) { c.set(cw.x0, C.top, z, ...log('z')); c.set(cw.x1, C.top, z, ...log('z')) } // wall plates
  // partitions: the chambers either side of x 6, the passage behind z 7
  timberWall(c, 'z', 6, [C.room.z0, 6], 6, C.top - 1, [C.room.z0])
  timberWall(c, 'x', 7, [C.room.x0, C.room.x1], 6, C.top - 1, [C.room.x0, 6, C.room.x1])
  for (let z = C.room.z0; z <= 6; z++) c.set(6, C.top, z, ...log('z')) // beams over the partitions (and the doors)
  for (let x = C.room.x0; x <= C.room.x1; x++) c.set(x, C.top, 7, ...log('x'))
  floor(c, C.room.x0, C.room.z0, C.room.x1, C.room.z1, C.ceilingY, P.ceiling)
  for (let x = cw.x0 + 1; x <= cw.x1 - 1; x++) { c.set(x, C.ceilingY, cw.z0, ...log('x')); c.set(x, C.ceilingY, cw.z1, ...log('x')) } // tie beams

  // The Roof: one slate roof, the street gable, a block of overhang at the front and the back
  const main = gableRoof(c, R, P.roof)
  // the gables: a frame of logs on the wall line (posts, a collar, a king post) round a two-high window between
  // plaster jambs; the corner panels under the rakes set a block back (`inward`), so the frame stands proud
  const gableFrame = (line, inward) => {
    for (let x = cw.x0 + 1; x < cw.x1; x++) for (let y = C.ceilingY + 1; y < main.under[x]; y++) {
      if (x === 6 && y <= 11) { c.set(x, y, line, P.glass); continue }
      if ((x === 5 || x === 7) && y <= 11) { c.set(x, y, line, P.infill); continue } // the window's plaster jambs
      if ((x === 4 || x === 8) || (x === 6) ) { c.set(x, y, line, ...log('y')); continue }
      if (y === 12) { c.set(x, y, line, ...log('x')); continue }
      c.set(x, y, line + inward, P.infill) // a recessed panel
    }
    stair(c, 6, C.ceilingY, line - inward, `${P.trim}_stairs`, inward > 0 ? 'south' : 'north', 'top') // the window's sill
  }
  gableFrame(cw.z0, 1)
  gableFrame(cw.z1, -1)

  // a gabled wall dormer on each slope over each chamber, its face on the wall line
  for (const d of MASSES.dormers) {
    dormer(c, d.z, P.roof, P.infill, P.glass, P.frame, { face: d.face, eave: d.eave, y0: 7 })
    c.clear(d.eave, 8, d.z)
  }
  if (at(2)) door(c, OPENINGS.galleryDoor.at, OPENINGS.galleryDoor.facing, 'east', { wood: P.wood }) // under the east dormer's glass

  // The rear outshot: one stone lean-to across the back under one catslide whose eave hangs a block out (z 12): the
  // stair (west) and the scullery (east) either side of a shared wall at x 6
  const t = TW.walls, sc = S.walls
  for (const w of [t, sc]) {
    plinth(c, w.x0, w.z0, w.x1, w.z1, rubble)
    wallPanel(c, [w.x0, w.z0 + 1], [w.x0, w.z1], 1, S.top, stone)
    wallPanel(c, [w.x1, w.z0 + 1], [w.x1, w.z1], 1, S.top, stone)
    wallPanel(c, [w.x0, w.z1], [w.x1, w.z1], 1, S.top, stone)
    for (const x of [w.x0, w.x1]) c.set(x, 6, 10, stone) // the lean-to's cheeks
  }
  quoins(c, [[t.x0, t.z1], [sc.x1, sc.z1]], 1, S.top, P.quoin, 'stone_bricks')
  floor(c, TW.room.x0, TW.room.z0, TW.room.x1, TW.room.z1, 0, P.flags)
  floor(c, S.room.x0, S.room.z0, S.room.x1, S.room.z1, 0, P.flags)
  for (let x = R.across[0]; x <= R.across[1]; x++) for (const [y, z] of [[5, 12], [6, 11], [7, 10]]) stair(c, x, y, z, P.roof.stairs, 'north')
  for (let x = R.across[0]; x <= R.across[1]; x++) c.set(x, 0, 12, P.apron) // the skirt the back sills stand on
  for (const [x, z] of OUTSIDE.skirt) c.set(x, 0, z, P.apron)
  // the jetty's moulding carried round both flanks at the chambers' floor, so the frame reads on every side
  for (let z = cw.z0; z <= cw.z1; z++) {
    stair(c, cw.x0 - 1, C.floorY, z, `${P.trim}_stairs`, 'east', 'top')
    stair(c, cw.x1 + 1, C.floorY, z, `${P.trim}_stairs`, 'west', 'top')
  }

  // --- Pass 2: openings -----------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: 'stone_brick_stairs' })
  doorway(c, OPENINGS.towerDoor.at, OPENINGS.towerDoor.height)
  doorway(c, OPENINGS.sculleryDoor.at, OPENINGS.sculleryDoor.height)
  const bd = OPENINGS.backDoor
  door(c, bd.at, bd.facing, 'east', { wood: P.wood, step: bd.step, stepStair: 'stone_brick_stairs' })
  for (const d of OPENINGS.chamberDoors) door(c, d.at, d.facing, 'south', { wood: P.wood })
  for (const w of OPENINGS.windows) {
    if (w.until && at(w.until)) continue
    window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, pots: w.pots ?? [] })
  }
  // the turning stair up the tower, through the back wall onto the passage
  doorway(c, OPENINGS.passageDoor.at, OPENINGS.passageDoor.height)
  turningStair(c, OPENINGS.towerStair, P.wood, P.flags)

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------------
  jetty(c, { x0: cw.x0, x1: cw.x1, z: cw.z0, y: C.floorY, out: 'north', at: C.frontPosts }, { trim: P.trim })
  // barge boards under the front overhang, and under the back overhang east of the tower (45 degrees: stairs)
  for (let x = R.across[0]; x <= R.across[1]; x++) {
    const up = x <= 6 ? 'east' : 'west'
    if (!c.get(x, main.under[x] - 1, R.along[0])) stair(c, x, main.under[x] - 1, R.along[0], `${P.trim}_stairs`, up, 'top')
    if (!c.get(x, main.under[x] - 1, R.along[1])) stair(c, x, main.under[x] - 1, R.along[1], `${P.trim}_stairs`, up, 'top')
  }
  // the chimney up the west flank, the hearth set in the wall, a stone mantel over it
  const ch = MASSES.chimney
  chimney(c, ch.x, ch.z, 0, ch.top, stone, { breast: { axis: 'z', top: 4, stair: 'stone_brick_stairs' } })
  c.set(ch.x, ch.top, ch.z, 'stone_bricks')
  for (let z = ch.z - 1; z <= ch.z + 1; z++) stair(c, 2, 4, z, 'stone_brick_stairs', 'west', 'top')
  // the front: lanterns under the jetty, the sign post at the street corner, a flower bed
  for (const [x, y, z] of OUTSIDE.doorLamps) c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
  const sp = OUTSIDE.signPost
  signPost(c, sp.at, sp.height, `${P.wood}_fence`, sp.banner, sp.rotation)
  for (const b of OUTSIDE.flowerBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)

  // --- Pass 4: interior (the plan's furniture) ------------------------------------------------------------------------
  barRun(c, { x: 9, y: 1, z0: 5, z1: 8, jobZ: 6, facing: 'west' }, { plank: P.bar, job: 'aliveworkplace:shop_counter' })
  c.set(9, 2, 8, 'barrel', { facing: 'west', open: 'false' }) // the tapped keg on the bar's end

  // --- Pass 5: the Coach Yard (II) ---------------------------------------------------------------------------------------
  if (at(2)) drawYard(c)

  // --- Pass 6: the Ale Cellar (III) ---------------------------------------------------------------------------------------
  let dug = null
  if (at(3)) {
    const w = MASSES.cellar.walls, d = MASSES.cellar.depth
    dug = cellar(c, w.x0, w.z0, w.x1, w.z1, d, stone, 'cobblestone')
    const L = CELLAR.ladder
    hatchLadder(c, L.x, L.z, -d + 1, 0, L.facing, P.wood)
    for (const r of CELLAR.racks) caskRack(c, { x: r.x, y: -d + 1, z0: r.z0, z1: r.z1, facing: r.facing, high: r.x === 2 ? 2 : 1 })
    for (const [x, z] of CELLAR.pillars) post(c, x, z, -d + 1, -1, P.frame)
    for (const x of CELLAR.backRack) c.set(x, -d + 1, 8, 'barrel', { facing: 'north', open: 'false' })
    const bc = CELLAR.bigCasks
    caskRack(c, { x: bc.x, y: -d + 1, z0: bc.z0, z1: bc.z1, facing: bc.facing, high: 2 })
    for (const [id, x, z] of CELLAR.workCorner) c.set(x, -d + 1, z, id)
    for (const [x, y, z, f] of CELLAR.chests) c.set(x, y, z, 'chest', { facing: f, type: 'single', waterlogged: 'false' })
    for (const [x, y, z] of CELLAR.lamps) c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
  }
  // the plan's furniture last, every level's (the loft's and the cellar's rooms stand by now)
  for (const f of FURNITURE) if (at(f.level ?? 1)) furnitureParts[f.part](c, f)
  return { c, dug }
}

/** Level II: the coach yard east of the inn: paving, the gallery and its stair, the stable, the gate, the yard's wall. */
function drawYard(c) {
  const Y = MASSES.yard, G = MASSES.gallery, St = MASSES.stable
  // the gallery along the inn's east wall at the chambers' floor, a pentice over its rail, a stair down into the yard
  gallery(c, { x0: G.x0, x1: G.x1, z0: G.z0, z1: G.z1, y: G.y, posts: G.posts, gap: [8], out: 'east' },
    { deck: P.floor, post: P.frame, rail: `${P.wood}_fence` })
  c.set(G.x0, G.y + 1, G.z0, `${P.wood}_fence`); c.set(G.x0, G.y + 1, G.z1, `${P.wood}_fence`) // the deck's ends
  const gs = G.stair
  flight(c, gs.steps, 'south', P.wood, 'stone_bricks')
  // the landing between the stair's head and the deck, and one joined railing round the whole deck
  // and landing: fences on every open edge (each corner a fence post), open only where the stair comes up
  const [lx, ly, lz] = gs.landing
  c.set(lx, ly, lz, P.floor)
  const rail = `${P.wood}_fence`
  for (let z = G.z0; z <= G.z1; z++) if (z !== lz) c.set(G.x1, G.y + 1, z, rail)
  for (let x = G.x0; x <= G.x1; x++) { c.set(x, G.y + 1, G.z0, rail); c.set(x, G.y + 1, G.z1, rail) }
  c.clear(G.x1, G.y + 1, lz)
  c.set(gs.steps[0][0], 0, gs.steps[0][2] - 1, P.apron) // the stair's foot

  // the stable: open stalls to the yard, stone back and gable walls, a hay loft under a slate roof
  const w = St.walls
  plinth(c, w.x0, w.z0, w.x1, w.z1, rubble)
  floor(c, St.room.x0, St.room.z0, St.room.x1, St.room.z1, 0, 'packed_mud')
  stalls(c, { front: w.x0, back: w.x1, posts: St.stallPosts, y: 1 }, { fence: `${P.wood}_fence`, hay: P.hay, trough: 'cauldron' })
  wallPanel(c, [w.x1, w.z0], [w.x1, w.z1], 1, 3, stone)
  wallPanel(c, [w.x0 + 1, w.z0], [w.x1, w.z0], 1, 3, stone)
  wallPanel(c, [w.x0 + 1, w.z1], [w.x1, w.z1], 1, 3, stone)
  for (const z of St.stallPosts) post(c, w.x0, z, 1, 3, P.frame)
  // the stable's yard face: stone between the posts, a door and a window to each stall
  for (let i = 0; i + 1 < St.stallPosts.length; i++) {
    const za = St.stallPosts[i] + 1
    for (let z = za; z < St.stallPosts[i + 1]; z++) for (let y = 1; y <= 3; y++) c.set(w.x0, y, z, stone)
    door(c, [w.x0, 1, za], 'east', 'west', { wood: P.wood })
    c.set(w.x0, 2, za + 1, P.glass)
    window(c, [[w.x1, 2, za], [w.x1, 3, za]], 'east', 'sill', { glass: P.glass, wood: P.wood, pots: ['potted_oxeye_daisy'] })
  }
  beamRing(c, w.x0, w.z0, w.x1, w.z1, St.loftY, P.frame)
  floor(c, St.room.x0, St.room.z0, St.room.x1, St.room.z1, St.loftY, P.floor)
  for (let z = w.z0; z <= w.z1; z++) { c.set(w.x0, 5, z, P.infill); c.set(w.x1, 5, z, P.infill) } // knee walls
  for (let z = w.z0; z <= w.z1; z++) c.set(w.x1 + 1, 0, z, P.apron) // the skirt the back sills stand on
  for (const x of [20, 24]) { c.set(x, 0, w.z0 - 1, P.apron); window(c, [[x, 2, w.z0], [x, 3, w.z0]], 'north', 'sill', { glass: P.glass, wood: P.wood, pots: ['potted_red_tulip'] }) }
  const sr = gableRoof(c, St.roof, P.roof)
  const pick = door => (u, y) => {
    if (u === 22) return door && (y === 5 || y === 6) ? null : log('y')
    if (u === 20 || u === 24) return log('y')
    return P.infill
  }
  gableWall(c, 'z', w.z0, [w.x0, w.x1], 5, sr.under, pick(true))
  gableWall(c, 'z', w.z1, [w.x0, w.x1], 5, sr.under, pick(false))
  door(c, [22, 5, w.z0], 'south', 'north', { wood: P.wood }) // the hay door over the street
  c.set(22, 8, w.z0 - 1, ...log('z')) // the hoist beam
  // the ladder to the loft and hay up there; lanterns under the loft
  for (let y = 1; y <= St.loftY; y++) c.set(20, y, 11, 'ladder', { facing: 'north', waterlogged: 'false' })
  for (const [x, z] of [[23, 10], [24, 10], [24, 11], [23, 11], [22, 11], [24, 9]]) c.set(x, 5, z, P.hay, { axis: 'y' })
  for (const z of [5, 8, 11]) c.set(21, 3, z, 'lantern', { hanging: 'true', waterlogged: 'false' })

  // the gate to the street, the yard's paving and back wall, a lamp post, a trough and barrels
  gatePiers(c, { piers: MASSES.gate.piers, z: MASSES.gate.z, beamY: MASSES.gate.beamY }, { stone, beam: P.frame, roof: P.roof })
  for (let x = Y.x0; x <= Y.x1; x++) for (let z = Y.z0; z <= Y.z1; z++) if (!c.get(x, 0, z)) c.set(x, 0, z, yardMix)
  railingRun(c, [[13, 1, 12], [18, 1, 12]], null, 'cobblestone_wall')
  lampPost(c, 17, 1, 5, `${P.wood}_fence`, 2)
  c.set(17, 1, 9, 'water_cauldron', { level: '3' }); c.set(17, 1, 10, 'water_cauldron', { level: '3' })
  c.set(15, 1, 11, 'barrel', { facing: 'up', open: 'false' }); c.set(16, 1, 11, 'barrel', { facing: 'up', open: 'false' })
  c.set(18, 1, 11, P.hay, { axis: 'y' })
  stair(c, 13, 1, 11, 'stone_brick_stairs', 'east') // a mounting block
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
  const name = `${IDS[level]}${cut !== undefined ? `_cut${cut}` : ''}`
  return { structure: canvas.toStructure(api), metadata: { name } }
}
