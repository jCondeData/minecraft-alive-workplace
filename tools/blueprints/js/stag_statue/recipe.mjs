// The Stag Statue, the recipe: assembles plan.mjs from the shared parts library (../parts, ROADMAP 49) in passes
// (block-out: the crag; structure: legs and body; depth and detail: neck, head, antlers, eyes; ground: curb, moss,
// plants; light: lamps) and returns the structure for the minecraft-builder skill's runner. No furniture: it writes an
// empty furniture.json for rebuild.py. CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, PROFILE, LEGS, ANTLERS, EYES, CRAG, CURB, SPINE, BOULDERS, BOULDER_SLABS, PLANTS, LAMPS } from './plan.mjs'
import { Canvas, mix, stair, slab, profileFigure, legs, antlerPair, plantFlower, lampPost } from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const rock = mix(P.rock, 11)
  const [W, , D] = SIZE
  const plantAt = new Map(PLANTS.map(([x, z, p]) => [`${x},${z}`, p]))
  const lampAt = new Set(LAMPS.map(l => `${l.x},${l.z}`))

  // --- Pass 1: block-out (the crag) ----------------------------------------------------------------------------
  const inOval = (x, z) => ((x - CRAG.cx) / CRAG.rx) ** 2 + ((z - CRAG.cz) / CRAG.rz) ** 2
  for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
    const d = inOval(x, z)
    if (d > 1) continue
    const curb = z === CURB.z && x >= CURB.x0 && x <= CURB.x1
    const spine = z >= SPINE.z0 && z <= SPINE.z1
    const k = `${x},${z}`
    if (curb) stair(c, x, 0, z, `${P.cut}_stairs`, 'south') // a step up toward the statue
    else if (plantAt.has(k) || (d > CRAG.rim && !spine && !lampAt.has(k))) c.set(x, 0, z, P.soil)
    else c.set(x, 0, z, rock)
  }
  for (const [x, y, z] of BOULDERS) c.set(x, y, z, rock)
  for (const [x, y, z] of BOULDER_SLABS) slab(c, x, y, z, P.rockSlab)

  // --- Pass 2: structure (legs, then the body's profile) --------------------------------------------------------
  legs(c, LEGS.feet, LEGS.y0, LEGS.y1, `${P.dark}_fence`)
  const st = (wood, facing, half = 'bottom') => [`${wood}_stairs`, { facing, half, shape: 'straight', waterlogged: 'false' }]
  profileFigure(c, PROFILE, {
    C: `${P.body}_planks`,
    P: `${P.pale}_planks`,
    W: `${P.body}_planks`,
    M: `${P.dark}_planks`,
    B: [`${P.body}_slab`, { type: 'bottom', waterlogged: 'false' }],
    R: st(P.body, 'east'), // the rump falls away west, to the tail
    N: st(P.dark, 'east'), // the nape rises east into the head
    T: st(P.dark, 'west', 'top'), // the throat under the jaw
    H: `${P.body}_planks`,
    S: st(P.dark, 'west'), // the muzzle tapers to the nose
  })

  // --- Pass 3: depth and detail (antlers, eyes) ----------------------------------------------------------------
  antlerPair(c, ANTLERS.burr, ANTLERS.beam, `${P.pale}_fence`, ANTLERS.gap)
  for (const e of EYES) c.set(...e.at, P.eye, { face: 'wall', facing: e.facing, powered: 'false' })

  // --- Pass 4: ground (plants on the moss) -----------------------------------------------------------------------
  for (const [x, z, p] of PLANTS) plantFlower(c, x, 1, z, p)

  // --- Pass 5: light ----------------------------------------------------------------------------------------------
  for (const l of LAMPS) lampPost(c, l.x, l.y, l.z, P.post, 1)
  return c
}

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": []}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `stag_statue_cut${cut}` : 'stag_statue' } }
}
