// Research Lab (research_lab, _2, _3), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes
// (block-out and structure, openings, depth and detail, interior, ground) and returns the structure for the
// minecraft-builder skill's runner. LEVEL=1..3 picks the level (default 1): each level draws the one below first and
// grows out of it, so II keeps I's cells and III is II block for block above the ground (with the hatch).
// Writes furniture.json (the level's furniture spots) and, for III, out/cellar.txt (the cellar's inside, dug out).
// Build: sh build.sh (all three, as candidates); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, BASEMENT, PALETTE as P, MASSES, OPENINGS, ANALYZER, FURNITURE, SKELETON, BURIED, OUTSIDE } from './plan.mjs'
import {
  Canvas, LiftedCanvas, mix, stair, plinth, floor, wallPanel, window, door, gableRoof, gableWall,
  buttress, bayTies, vaultRoof, vaultSpans, crossWindow, steppedPlinth, cellar, hatchLadder, lampPost, flowerBed,
  furnitureParts, copedGable, strataBands, pointedLight, boneFrame, specimenStand, digBed, rodFinials,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const bands = list => strataBands(list.map(([y, b], i) => [y, Array.isArray(b) ? mix(b, 11 + i) : b]))
const strata = bands(P.strata)
const ring = (c, { x0, x1, z0, z1 }, y0, y1, id) => {
  wallPanel(c, [x0, z0], [x1, z0], y0, y1, id); wallPanel(c, [x0, z1], [x1, z1], y0, y1, id)
  wallPanel(c, [x0, z0], [x0, z1], y0, y1, id); wallPanel(c, [x1, z0], [x1, z1], y0, y1, id)
}
const empty = c => (x, y, z) => !c.get(x, y, z)

function furnish(c, level) {
  for (const f of FURNITURE.filter(f => f.level === level)) {
    if (f.part === 'specimen') specimenStand(c, f.pos, P.stand, f.piece, f.props ?? {})
    else furnitureParts[f.part](c, f)
  }
}

// --- Level I: the Lab and the Prep Room ------------------------------------------------------------------------------
function levelOne(c) {
  const L = MASSES.lab, K = MASSES.prep
  const base = mix(P.plinth, 5)

  // Pass 1: block-out and structure. A projecting deepslate plinth, the banded walls on it, the roofs.
  plinth(c, L.plinth.x0, L.plinth.z0, L.plinth.x1, L.plinth.z1, base)
  plinth(c, K.plinth.x0, K.plinth.z0, K.plinth.x1, K.plinth.z1, base)
  floor(c, L.room.x0, L.room.z0, L.room.x1, L.room.z1, 0, P.floor)
  floor(c, K.room.x0, K.room.z0, K.room.x1, K.room.z1, 0, P.floor)
  ring(c, L.walls, 1, L.wallTop, strata)
  const kw = K.walls
  wallPanel(c, [kw.x0 + 1, kw.z0], [kw.x1, kw.z0], 1, K.wallTop, strata)
  wallPanel(c, [kw.x0 + 1, kw.z1], [kw.x1, kw.z1], 1, K.wallTop, strata)
  wallPanel(c, [kw.x1, kw.z0], [kw.x1, kw.z1], 1, K.wallTop, strata)
  // the buttresses before the roofs' trim, so the barge boards step round their caps
  for (const [x, z, top, toward] of L.buttresses) buttress(c, x, z, 1, top, strata, P.capStair, toward)

  const main = gableRoof(c, L.roof, P.roof)
  const wing = gableRoof(c, K.roof, P.roof, empty(c))
  // the skylight: the west slope's tiles over three bays turned to glass, stepping up the pitch (the eave stays tile)
  const sk = L.skylight
  for (let x = sk.x0; x <= sk.x1; x++) for (let z = sk.z0; z <= sk.z1; z++)
    for (let y = main.under[x]; y < main.under[x] + 2; y++) if (c.get(x, y, z)) c.set(x, y, z, P.vaultGlass)
  // the gables: parapets banded like the walls, coped in tuff along the roof's pitch with kneelers at the eaves; the
  // little gable where the Prep Room's roof meets the Lab's
  const cope = id => (id.endsWith('_stairs') ? P.coping.stairs : P.coping.full)
  copedGable(c, 'z', L.walls.z0, L.roof.along[0], L.roof.across, main.under, L.wallTop + 1, strata, cope)
  copedGable(c, 'z', L.walls.z1, L.roof.along[1], L.roof.across, main.under, L.wallTop + 1, strata, cope)
  for (const z of [L.walls.z0, L.walls.z1]) c.set(4, main.ridgeY, z, P.coping.apex)
  copedGable(c, 'x', kw.x1, K.roof.along[1], K.roof.across, wing.under, K.wallTop + 1, strata, cope)
  gableWall(c, 'x', kw.x0, [kw.z0, kw.z1], K.wallTop + 1, wing.under, (z, y) => (c.get(kw.x0, y, z) ? null : P.roof.full)) // the valley's backing, in tile
  // tie beams across the Lab at the wall top, their ends showing through the walls
  bayTies(c, L.ties.zs, L.walls.x0, L.walls.x1, L.ties.y, `${P.wood}_log`)

  // Pass 2: openings.
  const ld = OPENINGS.labDoor
  door(c, ld.at, ld.facing, ld.out, { wood: P.wood, step: ld.step, stepStair: P.stepStair })
  const gw = OPENINGS.greatWindow
  pointedLight(c, gw.axis, gw.line, gw.cu, gw.y0, gw.y1, P.glass, P.sill, P.key, P.vaultGlass)
  for (let y = 1; y <= 2; y++) c.clear(...[OPENINGS.prepDoorway.at[0], y, OPENINGS.prepDoorway.at[2]])
  for (const w of OPENINGS.windows.filter(w => w.level === 1)) window(c, w.cells, w.out, w.sill ? 'sill' : null, { glass: P.glass, wood: P.wood, sill: P.sill })

  // Pass 3: depth and detail. The door's hood with a lantern either side, the barge boards, the rods on the ridge.
  const h = OPENINGS.labHood
  stair(c, h.x - 1, h.y, h.z, P.roof.stairs, 'east'); stair(c, h.x + 1, h.y, h.z, P.roof.stairs, 'west'); c.set(h.x, h.y, h.z, P.roof.full)
  for (const [x, y, z] of h.lamps) c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
  rodFinials(c, L.rods, P.rod)

  // Pass 4: interior. The analyzer first (the job block), then the plan's furniture.
  c.free(ANALYZER.at)
  c.set(...ANALYZER.at, 'cobblemon:fossil_analyzer', { facing: ANALYZER.facing })
  furnish(c, 1)

  // Pass 5: ground. The path, ferns in their own soil, the boulder with a bone weathering out of it.
  for (const [x, y, z] of OUTSIDE.path) c.set(x, y, z, P.path)
  for (const b of OUTSIDE.fernBeds) flowerBed(c, b.x0, b.x1, b.z, ['fern'], P.ground)
  for (const [x, y, z] of OUTSIDE.boulder.stone) c.set(x, y, z, strata(x, 2, z))
  c.set(...OUTSIDE.boulder.bone, P.bone, { axis: 'x' })
}

// --- Level II: the Gallery -----------------------------------------------------------------------------------------
function levelTwo(c) {
  const G = MASSES.gallery, K = MASSES.prep
  const base = mix(P.plinth, 5)
  const gw = G.walls

  // the Prep Room's roof overhang and its barge board come down where the Gallery stands; its east window's sill
  for (let z = K.roof.across[0] - 1; z <= K.roof.across[1] + 1; z++) for (let y = 1; y <= 9; y++) c.clear(gw.x0 + 1, y, z)

  // Pass 1: block-out and structure. Plinth, banded walls, the glass vault on its copper ribs, the stone lunettes.
  plinth(c, G.plinth.x0 + 1, G.plinth.z0, G.plinth.x1, G.plinth.z1, base)
  for (let z = G.plinth.z0; z <= G.plinth.z1; z++) if (!c.get(gw.x0, 0, z)) c.set(gw.x0, 0, z, base)
  floor(c, G.room.x0, G.room.z0, G.room.x1, G.room.z1, 0, P.hallFloor)
  ring(c, gw, 1, G.wallTop, strata)
  const V = G.vault
  vaultRoof(c, V, { pane: P.vaultGlass, rib: P.copper, gutter: P.cornice, ribs: V.ribs })
  const { lo, hi } = vaultSpans(V.across, V.profile)
  for (const x of V.ends) for (let z = V.across[0]; z <= V.across[1]; z++) for (let y = lo[z]; y <= hi[z]; y++) c.set(x, y, z, P.cornice)
  for (const x of [gw.x0, gw.x1]) for (let z = gw.z0 + 1; z < gw.z1; z++)
    for (let y = G.wallTop + 1; y < lo[z]; y++) c.set(x, y, z, strata)
  for (const x of G.piersFront) buttress(c, x, gw.z0 - 1, 1, G.pierTop, strata, P.capStair, 'south')
  for (const x of G.piersBack) buttress(c, x, gw.z1 + 1, 1, G.pierTop, strata, P.capStair, 'north')
  buttress(c, gw.x1 + 1, G.pierEast, 1, G.pierTop, strata, P.capStair, 'west')

  // the frontispiece: two stone piers and the wall over them two deep before the door, a keystone over the arch,
  // a little gable coped like the Lab's standing against the vault's foot, a rod on its apex
  const po = G.portal
  plinth(c, po.x0, po.z0, po.x1, po.z1, base)
  for (let z = po.z0; z <= po.z1; z++) {
    for (const x of [po.x0, po.x1]) wallPanel(c, [x, z], [x, z], 1, po.top, strata)
    wallPanel(c, [po.x0 + 1, z], [po.x0 + 1, z], 3, po.top, strata)
    stair(c, po.x0, po.top + 1, z, P.coping.stairs, 'east'); stair(c, po.x1, po.top + 1, z, P.coping.stairs, 'west')
    c.set(po.x0 + 1, po.top + 1, z, P.coping.full); c.set(po.x0 + 1, po.top + 2, z, z === po.z0 ? P.coping.apex : P.coping.full)
  }
  c.set(po.x0 + 1, 3, po.z0, P.key)
  rodFinials(c, [[po.x0 + 1, po.top + 3, po.z0]], P.rod)

  // Pass 2: openings.
  const gd = OPENINGS.galleryDoor
  door(c, gd.at, gd.facing, gd.out, { wood: P.wood })
  stair(c, ...gd.step, P.stepStair, 'south')
  c.set(gd.at[0], gd.at[1] + 3, gd.at[2], P.key) // the carved panel over the door
  const dw = OPENINGS.galleryDoorway.at
  for (let y = 1; y <= 2; y++) c.clear(dw[0], y, dw[2])
  for (const w of OPENINGS.windows.filter(w => w.level === 2)) window(c, w.cells, w.out, w.sill ? 'sill' : null, { glass: P.glass, wood: P.wood, sill: P.sill })
  const r = OPENINGS.rose
  crossWindow(c, r.axis, r.line, r.cu, r.cy, P.glass)

  // Pass 3: depth and detail. Rods on the ribs' crowns, lamp posts either side of the frontispiece.
  rodFinials(c, G.rods, P.rod)
  for (const at of OUTSIDE.lampPosts.at) lampPost(c, ...at, `${P.wood}_fence`, 2)

  // Pass 4: interior. The stepped dais and the skeleton standing on it, then the furniture.
  const d = G.dais
  steppedPlinth(c, d.x0, d.z0, d.x1, d.z1, P.dais, () => P.daisStep, d.y)
  boneFrame(c, SKELETON, P.bone, P.thinBone)
  furnish(c, 2)
}

// --- Level III: the Dig -------------------------------------------------------------------------------------------
function levelThree(c) {
  const D = MASSES.dig
  const inside = cellar(c, D.walls.x0, D.walls.z0, D.walls.x1, D.walls.z1, D.depth, bands(P.cellarStrata), 'cobbled_deepslate')
  const [ix0, iz0, ix1, iz1] = inside
  const pit = D.pit
  // the boardwalk: everything inside the cellar at y -3 but the pit
  for (let x = ix0; x <= ix1; x++) for (let z = iz0; z <= iz1; z++) {
    const inPit = x >= pit.x0 && x <= pit.x1 && z >= pit.z0 && z <= pit.z1
    if (!inPit) c.set(x, -3, z, P.walk)
  }
  // the pit's bed of loose ground with the skeleton lying in it, a spoil heap in the far corner, a step down into it
  digBed(c, { x0: pit.x0, z0: pit.z0, x1: pit.x1, z1: pit.z1, y: -D.depth }, mix(P.digBed, 9), BURIED, P.bone)
  for (const [x, y, z] of [[20, -3, 12], [19, -3, 12], [20, -3, 11], [20, -2, 12]]) c.set(x, y, z, 'gravel')
  stair(c, 16, -3, pit.z0, `${P.wood}_stairs`, 'north')
  // a rail along the boardwalk's edge over the pit, open at the step
  for (let x = pit.x0; x <= pit.x1; x++) if (x !== 16) c.set(x, -2, pit.z0 - 1, `${P.wood}_fence`)
  const hch = OPENINGS.hatch
  hatchLadder(c, hch.x, hch.z, -2, 0, hch.facing, P.wood)
  furnish(c, 3)
  return inside
}

export function draw(level = 1) {
  const c = level === 3 ? new LiftedCanvas(SIZE[3], BASEMENT) : new Canvas(SIZE[level])
  levelOne(c)
  if (level >= 2) levelTwo(c)
  if (level >= 3) c.cellarInside = levelThree(c)
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const id = level === 1 ? 'research_lab' : `research_lab_${level}`
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  fs.mkdirSync(path.join(here, 'out'), { recursive: true })
  if (c.cellarInside) fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), c.cellarInside.join(','))
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
