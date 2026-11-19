// Lookout Tower (lookout_tower .. lookout_tower_4), the recipe: assembles plan.mjs from the shared parts library
// (../parts) in passes (block-out and structure, openings, depth and detail, interior, ground and basement) for
// LEVEL = 1..4, each level drawn as the level before plus what it adds (the gear shed; the guardhouse; the armory).
// Writes furniture.json (the level's furniture spots) for tools/blueprints/rebuild.py. CUT=<y> cuts everything above y.
// Build all four as candidates: build.sh.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { IDS, SIZES, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, POSTS, ARMORY, OUTSIDE } from './plan.mjs'
import {
  LiftedCanvas, mix, stair, slab, floor, wallPanel, window, door, doorway, quoins, stringCourse, cellar, hatchLadder,
  furnitureParts, corbelRing, batter, ladderShaft, crenels, arrowLoop, corbelLine, doubleChest,
  rimCells, watchGallery, shedRoof, fenceLamp,
} from '../parts/index.mjs'
import { hipCap } from '../parts/ramparts.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

function drawLevel(level) {
  const c = new LiftedCanvas(SIZES[level], BASEMENT[level] ?? 0)
  const at = l => level >= l
  const P = PALETTE
  const upper = mix(P.stone, 11), lower = mix(P.stoneLow, 12)
  const rubble = (x, y, z) => (y <= 1 ? lower(x, y, z) : upper(x, y, z)) // moss only in the bottom courses
  const fence = `${P.wood}_fence`
  const S = MASSES.shaft, R = MASSES.room, G = MASSES.gallery, H = MASSES.hat, D = MASSES.shed, K = MASSES.guardhouse

  // --- Pass 1: block-out and structure ----------------------------------------------------------------------------
  // Shaft: a rubble plinth course, walls to the corbel course, dressed quoins, a band at the mid floor
  c.fill(S.x0, 0, S.z0, S.x1, 0, S.z1, rubble)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.groundFloor)
  for (const [a, b] of [[[S.x0, S.z0], [S.x1, S.z0]], [[S.x0, S.z1], [S.x1, S.z1]], [[S.x0, S.z0], [S.x0, S.z1]], [[S.x1, S.z0], [S.x1, S.z1]]])
    wallPanel(c, a, b, 1, S.top, rubble)
  const corner = (x, z) => (x === S.x0 || x === S.x1) && (z === S.z0 || z === S.z1)
  // dressed quoins up the corners only (a 5-wide face toothed with long-and-short stones read as all brick)
  quoins(c, [[S.x0, S.z0], [S.x1, S.z0], [S.x0, S.z1], [S.x1, S.z1]], 0, S.top, P.dressed)
  stringCourse(c, S.x0, S.z0, S.x1, S.z1, S.midY, P.dressed, corner)
  // the mid floor and the gallery's ceiling in bottom slabs: no sturdy top indoors, so no archer is ever set on an
  // inside floor (BattleStations counts any sturdy top with two clear cells over it)
  const slabs = (x0, z0, x1, z1, y) => { for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) slab(c, x, y, z, `${P.wood}_slab`) }
  slabs(R.x0, R.z0, R.x1, R.z1, S.midY)
  // Corbels and the gallery: the jetty's corbel ring under the deck, the open gallery on it
  const ladder = OPENINGS.ladders[0]
  corbelRing(c, G.x0, G.z0, G.x1, G.z1, S.top, P.dressedStairs)
  watchGallery(c, G, { deck: P.floor, post: P.frame, fence, beam: P.frame, brace: `${P.wood}_stairs` },
    (x, z) => x === ladder.x && z === ladder.z)
  // the gallery's ceiling inside the beam ring (the hat's attic shut off) and the king post under its apex
  slabs(G.x0 + 1, G.z0 + 1, G.x1 - 1, G.z1 - 1, G.beamY)
  const kx = (G.x0 + G.x1) / 2, kz = (G.z0 + G.z1) / 2
  for (let y = G.deckY + 1; y < G.beamY; y++) {
    c.set(kx, y, kz, P.frame, { axis: 'y' })
    // boards on the post's west and east faces (open trapdoors flat on it): the watch's notice boards
    c.set(kx - 1, y, kz, `${P.wood}_trapdoor`, { facing: 'west', half: 'bottom', open: 'true', powered: 'false', waterlogged: 'false' })
    c.set(kx + 1, y, kz, `${P.wood}_trapdoor`, { facing: 'east', half: 'bottom', open: 'true', powered: 'false', waterlogged: 'false' })
  }
  // Hat: a hipped slate roof from the eave ring, a tiled cap and the rod
  const capY = hipCap(c, H.x0, H.z0, H.x1, H.z1, H.y, P.roof.stairs, P.roof.slab)
  const mx = (H.x0 + H.x1) / 2, mz = (H.z0 + H.z1) / 2
  c.set(mx, capY, mz, P.roof.full)
  c.set(mx, capY + 1, mz, P.rod, { facing: 'up', powered: 'false', waterlogged: 'false' })

  // Gear Shed (II): a plinth and plank floor, spruce posts at its corners, boards to the roof's slope, the lean-to
  const roofY = x => D.wallTop + 1 + (D.eaveX - 1 - x) // x 8 -> 7 ... x 12 -> 3
  if (at(2)) {
    c.fill(D.x0, 0, D.z0, D.x1, 0, D.z1, rubble)
    floor(c, D.x0, D.z0 + 1, D.x1 - 1, D.z1 - 1, 0, P.floor)
    for (const z of [D.z0, D.z1]) for (let x = D.x0; x <= D.x1; x++) {
      const top = Math.min(roofY(x) - 1, roofY(D.x0 + 1) - 1)
      for (let y = 1; y <= top; y++) {
        if (y === D.wallTop && x > D.x0 && x < D.x1) c.set(x, y, z, P.frame, { axis: 'x' }) // the wall plate
        else c.set(x, y, z, P.boards)
      }
    }
    for (const z of [D.z0, D.z1]) c.set(D.x0 + 1, D.wallTop + 2, z, P.glass) // a little gable light over the wall plate
    if (!at(3)) {
      for (let z = D.z0 + 1; z < D.z1; z++) { for (let y = 1; y < D.wallTop; y++) c.set(D.x1, y, z, P.boards); c.set(D.x1, D.wallTop, z, P.frame, { axis: 'z' }) }
      for (const z of [D.z0, D.z1]) for (let y = 1; y <= D.wallTop; y++) c.set(D.x1, y, z, P.frame, { axis: 'y' })
    }
    for (const z of [D.z0, D.z1]) for (let y = 1; y <= roofY(D.x0 + 1) - 1; y++) c.set(D.x0, y, z, P.frame, { axis: 'y' })
    // the shaft's east corbels become full stones where the shed's roof runs in under them (no quarter gap)
    for (let z = G.z0 + 1; z < G.z1; z++) c.set(G.x1, S.top, z, P.dressed)
    shedRoof(c, { x0: D.x0 + 1, y0: roofY(D.x0 + 1), x1: at(3) ? K.x0 - 1 : D.eaveX, z0: D.z0 - 1, z1: D.z1 + 1, falls: 'east' }, P.roof.stairs)
    // its top course, tucked under the gallery's jetty, laid flat in full tiles: a flashing, not a slot under the hat
    for (let z = D.z0 - 1; z <= D.z1 + 1; z++) c.set(D.x0 + 1, roofY(D.x0 + 1), z, P.roof.full)
  }

  // Guardhouse (III): rubble walls with quoins, a corbel course, the stone deck and its crenellated parapet
  if (at(3)) {
    c.fill(K.x0, 0, K.z0, K.x1, 0, K.z1, rubble)
    floor(c, K.x0 + 1, K.z0 + 1, K.x1 - 1, K.z1 - 1, 0, P.floor)
    for (const [a, b] of [[[K.x0, K.z0], [K.x1, K.z0]], [[K.x0, K.z1], [K.x1, K.z1]], [[K.x0, K.z0], [K.x0, K.z1]], [[K.x1, K.z0], [K.x1, K.z1]]])
      wallPanel(c, a, b, 1, K.deckY - 1, rubble)
    quoins(c, [[K.x0, K.z0], [K.x1, K.z0], [K.x0, K.z1], [K.x1, K.z1]], 0, K.deckY, P.dressed, P.dressedAlt)
    corbelLine(c, K.x0, K.z0, K.x1, K.z1, K.deckY - 1, P.dressedStairs)
    c.fill(K.x0, K.deckY, K.z0, K.x1, K.deckY, K.z1, P.dressed)
    for (const [x, z] of [[K.x0, K.z0], [K.x1, K.z0], [K.x0, K.z1], [K.x1, K.z1]]) c.set(x, K.deckY, z, P.dressedAlt)
    crenels(c, rimCells(K.x0, K.z0, K.x1, K.z1), K.deckY + 1, K.deckY + 1, rubble, (i, x, z) => (x + z) % 2 === 0) // no cap: a merlon beside a crenel's station is no station
  }

  // --- Pass 2: openings ---------------------------------------------------------------------------------------------
  const td = OPENINGS.towerDoor
  door(c, td.at, td.facing, 'north', { wood: P.wood, step: td.step, stepStair: P.dressedStairs })
  for (const l of OPENINGS.loops) { // slit windows: a loop's shape and plunging sill, glazed against the weather
    arrowLoop(c, l.at[0], l.at[1], l.at[2], l.inward, P.dressedStairs)
    for (const dy of [0, 1]) c.set(l.at[0], l.at[1] + dy, l.at[2], P.glass)
  }
  for (const l of OPENINGS.ladders) if (at(l.level)) {
    if (l.hatch) hatchLadder(c, l.x, l.z, l.y0, l.y1, l.facing, P.wood)
    else ladderShaft(c, l.x, l.z, l.y0, l.y1, l.facing)
  }
  if (at(2)) {
    const sd = OPENINGS.shedDoor
    door(c, sd.at, sd.facing, 'north', { wood: P.wood, step: sd.step, stepStair: 'cobblestone_stairs' })
    doorway(c, OPENINGS.towerToShed.at)
  }
  if (at(3)) {
    const gd = OPENINGS.guardDoor
    door(c, gd.at, gd.facing, 'north', { wood: P.wood, step: gd.step, stepStair: P.dressedStairs })
    doorway(c, OPENINGS.shedToGuard.at)
  }
  for (const w of OPENINGS.windows) if (at(w.level) && !(w.until && level > w.until))
    window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, sill: w.sill ?? null })

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------------
  // the battered foot round the shaft (the shed takes its east side), the door's step left clear
  batter(c, S.x0 - 1, S.z0 - 1, S.x1 + 1, S.z1 + 1, 0, 'cobblestone_stairs',
    (x, z) => (x === td.step[0] && z === td.step[2]) || (at(2) && x === S.x1 + 1))
  for (const p of OUTSIDE.doorLamps) fenceLamp(c, p, fence)
  if (at(2)) c.set(...OUTSIDE.bell.at, 'bell', { attachment: 'ceiling', facing: 'east', powered: 'false' })
  if (at(3)) {
    for (const p of OUTSIDE.guardLamps.cells) fenceLamp(c, p, fence)
    const gd = OPENINGS.guardDoor // the guardhouse's battered foot on its three free sides, under the window sills
    batter(c, K.x0 - 1, K.z0 - 1, K.x1 + 1, K.z1 + 1, 0, 'cobblestone_stairs', (x, z) => x === K.x0 - 1 || (x === gd.step[0] && z === gd.step[2]))
  }

  // --- Pass 4: interior (the plan's furniture, level by level) ----------------------------------------------------------
  for (const f of FURNITURE) if (at(f.level)) furnitureParts[f.part](c, f)
  for (const p of POSTS) if (at(p.level)) { c.free(p.at); c.set(...p.at, 'aliveworkplace:guard_post', { facing: p.facing }) }

  // --- Pass 5: the armory (IV) --------------------------------------------------------------------------------------------
  let dug = null
  if (at(4)) {
    const A = MASSES.armory
    dug = cellar(c, A.x0, A.z0, A.x1, A.z1, A.depth, rubble, P.cellarFloor)
    const h = OPENINGS.hatch
    hatchLadder(c, h.x, h.z, -A.depth + 1, 0, h.facing, P.wood)
    doubleChest(c, ARMORY.chest.at, ARMORY.chest.facing, ARMORY.chest.along)
    c.set(...ARMORY.anvil, 'anvil', { facing: 'east' })
    for (const [x, y, z] of ARMORY.cellBars) for (let yy = y; yy < y + 3; yy++) c.set(x, yy, z, P.bars)
    const [dx, dy, dz] = ARMORY.cellDoor
    door(c, [dx, dy, dz], 'east', 'west', { wood: P.wood })
    c.set(dx, dy + 2, dz, P.bars)
    c.set(...ARMORY.cellStraw, 'hay_block', { axis: 'y' })
  }
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
