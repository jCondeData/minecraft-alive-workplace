// Camp Kitchen (camp_kitchen, camp_kitchen_2), the recipe: assembles plan.mjs from the shared parts library (../parts)
// in passes (block-out and structure, roof, openings, depth and detail, interior, ground) and returns the structure for
// the minecraft-builder skill's runner. LEVEL=1|2 picks the level (default 1); Level II draws Level I first, untouched,
// and adds to it. CUT=<y> builds a cutaway with everything above y removed. Writes furniture.json (the level's spots).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { LEVELS, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE, PINS } from './plan.mjs'
import {
  Canvas, mix, stair, slab, plinth, floor, post, beamRing, wallPanel, window, door, gableRoof, gableWall, bargeTrim,
  chimney, flowerBed, lampPost, tentRoof, louvre, campPot, grainBed, furnitureParts,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

function cookhouse(c) {
  const P = PALETTE
  const stone = mix(P.stone, 11), soot = mix(P.soot, 12), flags = mix(P.floor, 13)
  const T = MASSES.terrace, H = MASSES.cookhouse
  const { x0, x1, z0, z1 } = H.posts
  const [cx, cz] = H.centre

  // --- Pass 1: block-out and structure ---------------------------------------------------------------------------
  plinth(c, T.x0, T.z0, T.x1, T.z1, stone) // the terrace the cookhouse stands on, a walk round under its eaves
  floor(c, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, flags)
  floor(c, cx - 1, cz - 1, cx + 1, cz + 1, 0, P.hearth) // the hearth: a square of brick under the pot
  // walls: a sooty rubble course, pale boards above, frame posts proud at the corners and the middle bay's sides
  for (const [a, b] of [[[x0, z0], [x1, z0]], [[x0, z1], [x1, z1]], [[x0, z0], [x0, z1]], [[x1, z0], [x1, z1]]]) {
    wallPanel(c, a, b, 1, 1, soot)
    wallPanel(c, a, b, 2, H.wallTop, P.infill)
  }
  for (const x of H.frontPosts) post(c, x, z0, 1, H.wallTop, P.frame)
  for (const x of H.backPosts) post(c, x, z1, 1, H.wallTop, P.frame)
  for (const z of H.westPosts) post(c, x0, z, 1, H.wallTop, P.frame)
  for (const z of H.eastPosts) post(c, x1, z, 1, H.wallTop, P.frame)
  beamRing(c, x0, z0, x1, z1, H.beamY, P.frame)

  // --- Pass 2: the roof: a bell-cast pyramid (spruce kick, deepslate tiles steepening upward) to the copper louvre ---
  tentRoof(c, H.roof, P.roof, { lining: P.lining })
  louvre(c, cx, cz, H.louvreY, { base: P.roof.full, post: `${P.wood}_fence`, roof: P.copper, finial: P.copper.finial })
  // the flue: the louvre's floor right over the pot is a damper (a spruce trapdoor, propped open) the smoke goes out by
  c.set(cx, H.louvreY, cz, `${P.wood}_trapdoor`, { facing: 'north', half: 'top', open: 'true', powered: 'false', waterlogged: 'false' })

  // --- Pass 3: openings --------------------------------------------------------------------------------------------
  const d = OPENINGS.door
  door(c, d.at, d.facing, 'north', { wood: P.wood, step: d.step, stepStair: 'stone_brick_stairs' })
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, 'shutters', { glass: P.glass, wood: P.wood })

  // --- Pass 4: depth and detail -------------------------------------------------------------------------------------
  // lanterns either side of the door, hung from the eave
  for (const x of [d.at[0] - 1, d.at[0] + 1]) c.set(x, H.wallTop, z0 - 1, 'lantern', { hanging: 'true', waterlogged: 'false' })
  // the bale lean-to on the west flank: a stone pad, two posts, a wall plate under the main eave, a pent roof
  const L = MASSES.leanTo
  plinth(c, L.x0, L.z0, L.x0, L.z1, stone)
  for (const z of [L.z0, L.z1]) post(c, L.x0, z, 1, L.roofY - 1, P.frame)
  for (let z = L.z0 - 1; z <= L.z1 + 1; z++) {
    stair(c, L.x0, L.roofY, z, P.roof.stairs, 'east')
    c.set(L.x1, L.roofY, z, P.frame, { axis: 'z' })
  }
  for (let z = L.z0 + 1; z <= L.z1 - 1; z++) {
    for (let y = 1; y <= 3; y++) c.set(L.x1, y, z, P.bale, { axis: y === 2 ? 'z' : 'y' })
    for (let y = 1; y <= 2; y++) c.set(L.x0, y, z, P.bale, { axis: y === 2 ? 'x' : 'y' })
  }
  c.set(...PINS.bale, P.bale, { axis: 'y' })

  // --- Pass 5: interior: the pot in the middle, then the plan's furniture --------------------------------------------
  campPot(c, PINS.pot, 'north')
  for (const f of FURNITURE) if (!f.level) furnitureParts[f.part](c, f)

  // --- Pass 6: ground: steps, flower beds, lamp posts, the woodpile ---------------------------------------------------
  for (const x of OUTSIDE.steps) stair(c, x, 0, 0, 'stone_brick_stairs', 'south')
  for (const b of OUTSIDE.flowerBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  for (const [x, y, z] of OUTSIDE.lampPosts) lampPost(c, x, y, z, `${P.wood}_fence`, 2)
  // the fuel: two stacks of split logs on the east terrace, either side of the east window
  for (const zs of OUTSIDE.woodpiles) for (const z of zs) for (const y of [1, 2]) c.set(T.x1, y, z, 'spruce_log', { axis: 'x' })
}

function smokehouse(c) {
  const P = PALETTE
  const stone = mix(P.stone, 21), soot = mix(P.soot, 22)
  const S = MASSES.smokehouse, W = S.walls
  plinth(c, S.plinth.x0, S.plinth.z0, S.plinth.x1, S.plinth.z1, stone)
  floor(c, S.room.x0, S.room.z0, S.room.x1, S.room.z1, 0, P.hearth)
  for (const [a, b] of [[[W.x0, W.z0], [W.x1, W.z0]], [[W.x0, W.z1], [W.x1, W.z1]], [[W.x0, W.z0], [W.x0, W.z1]], [[W.x1, W.z0], [W.x1, W.z1]]])
    wallPanel(c, a, b, 1, S.wallTop, soot)
  // dressed corners and a spruce wall plate: the same frame as the cookhouse, in stone
  for (const [x, z] of [[W.x0, W.z0], [W.x1, W.z0], [W.x0, W.z1], [W.x1, W.z1]]) post(c, x, z, 1, S.wallTop, P.frame)
  beamRing(c, W.x0, W.z0, W.x1, W.z1, S.beamY, P.frame)
  const r = gableRoof(c, S.roof, P.roof)
  for (const z of [W.z0, W.z1]) gableWall(c, 'z', z, [W.x0, W.x1], S.beamY + 1, r.under, () => soot)
  bargeTrim(c, 'z', S.roof.along[0], S.roof.across, r.under, `${P.wood}_stairs`)
  bargeTrim(c, 'z', S.roof.along[1], S.roof.across, r.under, `${P.wood}_stairs`)
  // the chimney up the back gable, smoking, its breast stepping in either side
  const [chx, chz] = S.chimney
  chimney(c, chx, chz, 0, r.ridgeY + 1, soot, { breast: { axis: 'x', top: 3, stair: 'cobblestone_stairs' } })
  const sd = OPENINGS.smokeDoor
  door(c, sd.at, sd.facing, 'north', { wood: P.wood, step: sd.step, stepStair: 'stone_brick_stairs' })
  for (const [x, y, z] of OPENINGS.vents) c.set(x, y, z, 'iron_bars')
  // a little hood over the door, a lantern under it
  stair(c, sd.at[0] - 1, 4, W.z0 - 1, P.roof.stairs, 'east')
  stair(c, sd.at[0] + 1, 4, W.z0 - 1, P.roof.stairs, 'west')
  c.set(sd.at[0], 4, W.z0 - 1, P.roof.full)
  c.set(sd.at[0], 3, W.z0 - 1, 'lantern', { hanging: 'true', waterlogged: 'false' })
  // inside: the smoker against the back wall; a tie beam across the room, wall to wall, is the smoking rack: the lamp
  // and the meat hooks (chains) hang from it over the counters
  for (let x = W.x0 + 1; x <= W.x1 - 1; x++) c.set(x, S.beamY, 6, P.frame, { axis: 'x' })
  c.set(...PINS.smoker, 'smoker', { facing: 'north', lit: 'false' })
  for (const f of FURNITURE) if (f.level === 2) furnitureParts[f.part](c, f)
  for (const x of [15, 17]) for (const y of [4]) c.set(x, y, 6, 'chain', { axis: 'y', waterlogged: 'false' })
  // its fuel: split logs stacked against the smokehouse's west wall, on its plinth, under the eave
  const sw = OUTSIDE.smokeWoodpile
  for (let z = sw.z0; z <= sw.z1; z++) for (let y = 1; y <= (z === sw.z0 || z === sw.z1 ? 1 : 2); y++) c.set(sw.x, y, z, 'spruce_log', { axis: 'x' })
}

function grainField(c) {
  const P = PALETTE
  const G = MASSES.grainBed
  grainBed(c, G, { rim: mix(P.stone, 31), cap: null, bed: mix(P.soot, 32), soil: 'dirt', grain: P.grain })
  // lamps on the bed's front corners, on posts standing on the rim
  for (const x of [G.x0, G.x1]) { c.set(x, 2, G.z0, `${P.wood}_fence`); c.set(x, 3, G.z0, 'lantern', { hanging: 'false', waterlogged: 'false' }) }
  // the path round the back of the cookhouse to the bed
  const p = OUTSIDE.path
  for (let x = p.x0; x <= p.x1; x++) c.set(x, 0, p.z, 'dirt_path')
  for (let z = 2; z < p.z; z++) c.set(p.x1, 0, z, 'dirt_path') // the alley between the two houses
}

export function draw(level) {
  const c = new Canvas(LEVELS[level].size)
  cookhouse(c)
  if (level >= 2) { smokehouse(c); grainField(c) }
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const name = level === 2 ? 'camp_kitchen_2' : 'camp_kitchen'
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${name}_cut${cut}` : name } }
}
