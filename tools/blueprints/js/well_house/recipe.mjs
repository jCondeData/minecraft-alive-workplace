// Well House, the recipe: assembles plan.mjs from the shared parts library (../parts, ROADMAP 49) in passes (block-out
// and structure, the water, openings, depth and detail, the hall's fittings, ground and light) and returns the
// structure for the minecraft-builder skill's runner. Writes furniture.json (no spots: the brief needs none).
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, stair, slab, plinth, floor, post, beamRing, wallPanel, railing, railingRun, crossGableRoof, louvre,
  wellShaft, aqueduct, flowerBed, lampPost,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const rubble = mix(P.rubble, 3), stone = mix(P.stone, 5), kerb = mix(P.kerb, 9)
  const Po = MASSES.podium, Pa = MASSES.pavilion, R = MASSES.roof, W = MASSES.well, A = MASSES.aqueduct
  const { x0, x1, z0, z1 } = Pa.posts

  // --- Pass 1: block-out and structure ---------------------------------------------------------------------------
  // The podium: a hollow cistern base, rubble at the bottom, stone bricks above, a coping ring round the deck
  plinth(c, Po.x0, Po.z0, Po.x1, Po.z1, rubble, 0)
  for (let x = Po.x0 + 1; x < Po.x1; x++) for (let z = Po.z0 + 1; z < Po.z1; z++) c.clear(x, 0, z)
  wallPanel(c, [Po.x0, Po.z0], [Po.x1, Po.z0], 1, 1, stone)
  wallPanel(c, [Po.x0, Po.z1], [Po.x1, Po.z1], 1, 1, stone)
  wallPanel(c, [Po.x0, Po.z0], [Po.x0, Po.z1], 1, 1, stone)
  wallPanel(c, [Po.x1, Po.z0], [Po.x1, Po.z1], 1, 1, stone)
  floor(c, Po.x0, Po.z0, Po.x1, Po.z1, Po.deckY, P.coping)
  floor(c, x0, z0, x1, z1, Po.deckY, P.deck)
  // The frame: posts, a beam ring tying them at y 6
  for (const x of Pa.frontPosts) post(c, x, z0, Po.deckY + 1, Pa.postTop, P.frame)
  for (const x of Pa.backPosts) post(c, x, z1, Po.deckY + 1, Pa.postTop, P.frame)
  for (const z of Pa.westPosts) post(c, x0, z, Po.deckY + 1, Pa.postTop, P.frame)
  for (const z of Pa.eastPosts) post(c, x1, z, Po.deckY + 1, Pa.postTop, P.frame)
  beamRing(c, x0, z0, x1, z1, Pa.beamY, P.frame)

  // The roof: a cross gable (a gable to every side), then the gables under it on the post lines
  const roof = crossGableRoof(c, R, P.roof)
  // pale birch panels framed by a spruce king post (u 5) and two studs (u 3, 7): three bays like the hall's below
  const stud = u => u === 3 || u === 5 || u === 7
  const gableCell = (x, y, z, u) => c.set(x, y, z, stud(u) ? P.frame : P.gable, stud(u) ? { axis: 'y' } : {})
  for (let u = x0 + 1; u < x1; u++) for (let y = Pa.beamY + 1; y < roof.at(u, z0); y++) {
    gableCell(u, y, z0, u); gableCell(u, y, z1, u)
  }
  for (let u = z0 + 1; u < z1; u++) for (let y = Pa.beamY + 1; y < roof.at(x0, u); y++) {
    gableCell(x0, y, u, u - 2); gableCell(x1, y, u, u - 2) // the king post at z 7 (u - 2 = 5)
  }
  // barge boards: an upside-down spruce stair under each rake's overhang, outlining the four gables against the dark
  // roof (a top slab here read as a table beside the lower roof stair, a "chair": QaChairFacingGameTests). Its lower
  // quarter tucks against the higher side of the rake, so the edge still reads as a thin board from below.
  for (let u = R.x0 + 1; u < R.x1; u++) for (const [x, z, alongX] of [[u, R.z0, true], [u, R.z1, true], [R.x0, u + 2, false], [R.x1, u + 2, false]]) {
    const y = roof.at(x, z) - 1
    if (y > Pa.beamY && !c.get(x, y, z)) {
      const up = alongX ? roof.at(x + 1, z) >= roof.at(x - 1, z) : roof.at(x, z + 1) >= roof.at(x, z - 1)
      stair(c, x, y, z, `${P.wood}_stairs`, alongX ? (up ? 'east' : 'west') : (up ? 'south' : 'north'), 'top')
    }
  }
  louvre(c, MASSES.louvre.x, MASSES.louvre.z, MASSES.louvre.y, { base: P.roof.full, post: P.louvrePost, roof: P.roof, finial: P.louvrePost })

  // --- Pass 2: the water -----------------------------------------------------------------------------------------
  // The shaft: lined through the podium, water at y 0, open to the mouth; the kerb ring at y 3
  wellShaft(c, W.x, W.z, W.waterY, W.mouthY, stone)
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) if (dx || dz) c.set(W.x + dx, W.mouthY, W.z + dz, kerb)
  // The channel's head inside the hall: andesite walls from the kerb to the deck's edge, the cauldron on its north side
  for (let x = W.x + 2; x <= Po.x1; x++) for (const z of [A.cz - 1, A.cz + 1]) if (x !== x1) c.set(x, A.y, z, P.channel)
  c.set(8, 3, 6, 'cauldron')
  // The aqueduct arm on its arches, the water running from the kerb to its far end
  aqueduct(c, A, { stone, coping: P.channel, arch: P.steps })

  // --- Pass 3: openings ------------------------------------------------------------------------------------------
  // The front steps: three stairs up to the deck (the last cut into the coping), cheeks either side with a lamp on
  // a wall post
  const S = OPENINGS.steps
  for (let x = S.x0; x <= S.x1; x++) {
    stair(c, x, 0, 0, P.steps, 'south')
    c.set(x, 0, 1, stone)
    stair(c, x, 1, 1, P.steps, 'south')
    stair(c, x, 2, 2, P.steps, 'south')
  }
  for (const [x, z] of OUTSIDE.cheekLamps) {
    c.set(x, 0, 0, rubble); c.set(x, 0, 1, rubble); c.set(x, 1, 1, stone)
    stair(c, x, 1, 0, P.steps, 'south')
    c.set(x, 2, z, P.cheekWall)
    c.set(x, 3, z, 'lantern', { hanging: 'false', waterlogged: 'false' })
  }
  // Railings in the bays that aren't a way in, the channel's gate or a bench's back
  for (const run of OPENINGS.railings) run.length === 1 ? railing(c, run, P.wood) : railingRun(c, run, P.wood)

  // --- Pass 4: depth and detail ----------------------------------------------------------------------------------
  for (const [x, y, z, f] of OPENINGS.corbels) stair(c, x, y, z, `${P.wood}_stairs`, f, 'top')
  // the aqueduct's middle pier gets cutwaters either side (stepped buttresses), so the arm reads as a bridge
  for (const z of [A.cz - 2, A.cz + 2]) {
    c.set(13, 0, z, stone)
    stair(c, 13, 1, z, P.steps, z < A.cz ? 'south' : 'north')
  }
  // podium faces: a pier of dressed stone under every corner post, standing proud of nothing but reading as a pilaster
  for (const [x, z] of [[Po.x0, Po.z0], [Po.x1, Po.z0], [Po.x0, Po.z1], [Po.x1, Po.z1]]) {
    c.set(x, 0, z, 'chiseled_stone_bricks'); c.set(x, 1, z, 'chiseled_stone_bricks')
  }
  // and under every post on the long west and back faces, so no face is a flat run of more than five blocks
  for (const [x, z] of [[Po.x0, 7], [3, Po.z1], [7, Po.z1]]) { c.set(x, 0, z, 'chiseled_stone_bricks'); c.set(x, 1, z, 'chiseled_stone_bricks') }

  // --- Pass 5: the hall's fittings --------------------------------------------------------------------------------
  for (const f of FURNITURE) {
    if (f.kind === 'bench') stair(c, ...f.pos, `${P.wood}_stairs`, f.facing)
    if (f.kind === 'windlass') {
      for (const [x, y, z] of f.posts) { c.set(x, y, z, `${P.wood}_fence`); c.set(x, y + 1, z, `${P.wood}_fence`) }
      for (const [x, y, z] of f.axle) c.set(x, y, z, P.frame, { axis: 'x' })
      for (const [x, y, z] of f.chain) c.set(x, y, z, 'chain', { axis: 'y', waterlogged: 'false' })
    }
    if (f.kind === 'lamp') for (const [x, y, z] of f.hanging) c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
  }

  // --- Pass 6: ground and light ----------------------------------------------------------------------------------
  for (const b of OUTSIDE.flowerBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  lampPost(c, ...OUTSIDE.endLamp, `${P.wood}_fence`)
  return c
}

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": []}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `well_house_cut${cut}` : 'well_house' } }
}
