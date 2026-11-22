// The Gatehouse (`gatehouse`, Level II `gatehouse_2`), the recipe: assembles plan.mjs from the shared parts library
// (../parts) in passes (block-out and structure, openings, depth and detail, interior, light) and returns the
// structure for the minecraft-builder skill's runner. LEVEL=1 (default) draws `gatehouse`; LEVEL=2 draws
// `gatehouse_2`: Level I drawn first and grown forward (every Level I block kept). Writes furniture.json beside it for
// rebuild.py. CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, GATES, OPENINGS, FURNITURE, FURNITURE_2, LIGHTS } from './plan.mjs'
import {
  Canvas, mix, stair, slab, quoins, corbelLine, crenels, hatchLadder, fenceGates, standard, furnitureParts,
  crenelTop, archHead, leafDoorway, loggia, murderDeck,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(level = 1) {
  const c = new Canvas(SIZE[level])
  const P = PALETTE, M = MASSES, T = M.tower, G = M.gateway, L = M.loggia
  const plinth = mix(P.plinth, 21), ashlar = mix(P.ashlar, 22)
  const post = { up: 'true', north: 'none', south: 'none', east: 'none', west: 'none', waterlogged: 'false' }

  // --- Pass 1: block-out and structure ----------------------------------------------------------------------------
  // the footing: the whole plot at y 0, the road paved through it, a pale sill under the portcullis
  for (let x = 0; x <= 10; x++) for (let z = T.z0; z <= T.z1; z++) c.set(x, 0, z, x >= G.x0 && x <= G.x1 ? P.road : plinth)
  for (const [x, , z] of GATES[1].bars) c.set(x, 0, z, P.course)

  // the Towers: a ring of wall y 1..9 round x0..x1, z 0..4, the Shoulder cut back at the outer front corner
  for (const t of M.towers) {
    const { x0, x1, outer } = t
    const cut = (x, y, z) => x === outer && z === T.shoulder.z && y >= T.shoulder.y0 && y <= T.shoulder.y1
    for (let y = 1; y <= T.wallTop; y++) for (let x = x0; x <= x1; x++) for (let z = T.z0; z <= T.z1; z++) {
      const ring = x === x0 || x === x1 || z === T.z0 || z === T.z1
      if (!ring || cut(x, y, z)) continue
      c.set(x, y, z, y <= T.plinthTop ? plinth : y === T.courseY ? P.course : ashlar)
    }
    // the shoulder's diagonal closed: the cells either side of the cut corner are wall already (x outer z 3, x outer±1 z 4)
    // inside: a stone floor, the upper floor at y 5 (spruce)
    for (let x = x0 + 1; x < x1; x++) for (let z = T.z0 + 1; z < T.z1; z++) { c.set(x, 0, z, P.road); c.set(x, 5, z, P.floor) }
    // dressed Quoins up the square corners (y 4..8: the course runs through at y 3), and the two faces of the cut corner
    quoins(c, [[t.outer, T.z0]], 4, T.wallTop - 1, P.quoin, P.quoinAlt)
    // the gateway's jambs, both faces: dressed from the road up (the course passes at y 3)
    quoins(c, [[t.inner, T.z0], [t.inner, T.z1]], 1, 2, P.quoin, P.quoin)
    quoins(c, [[t.inner, T.z0], [t.inner, T.z1]], 4, T.wallTop - 1, P.quoin, P.quoinAlt)
    quoins(c, [[outer, T.z1 - 1], [outer + t.dx, T.z1]], 4, T.wallTop - 1, P.quoinAlt, P.quoin)
    // the Corbel table at y 9 round the tower (the square corners stay full), the shoulder carried out square on a corbel
    corbelLine(c, x0, T.z0, x1, T.z1, T.wallTop, P.courseStairs) // pale: the parapet's line, as the course is the walk's
    stair(c, outer, T.wallTop, T.z1, P.courseStairs, 'north', 'top')
    c.clear(outer, 0, T.z1) // the shoulder cut down to the ground
    // the fighting Top: merlons on the corners and the long sides' middles, slab sills in the crenels, a low dark roof
    // inside the parapet (no deck: no battle station up here)
    crenelTop(c, { x0, z0: T.z0, x1, z1: T.z1, y: T.capY }, { merlon: P.merlon, cap: P.courseSlab, sill: P.courseSlab, roof: P.roof.stairs, roofFull: P.roof.full },
      (x, z) => (x === x0 || x === x1) && (z === T.z0 || z === T.z1 || z === 2))
  }

  // the Gateway's vault (y 4) and the loggia's walk over it (y 5), the portcullis' pale lintel at z 3
  for (let x = G.x0; x <= G.x1; x++) {
    for (let z = L.z0; z <= 2; z++) { c.set(x, G.vaultY, z, P.road); c.set(x, L.walkY, z, P.course) }
    c.set(x, G.vaultY, 3, P.course)
  }

  // --- Pass 2: openings: the gates and the portcullis (pinned), the arch, doorways, windows, slits ------------------
  fenceGates(c, GATES[1].gates.map(([x]) => x), 1, 2, P.gate, 'south')
  for (const [x, y, z] of GATES[1].bars) c.set(x, y, z, P.bars, { north: 'false', south: 'false', east: 'false', west: 'false', waterlogged: 'false' })
  // the Village Arch: haunches in the gateway's top corners at z 0, a keystone over the middle; another over the lintel
  archHead(c, G.x0, G.x1, G.archZ, G.vaultY, P.courseStairs, P.key) // four high at the face, the vault behind it at y 4
  c.clear(5, G.vaultY, G.archZ)
  c.set(5, G.vaultY, 3, P.key)
  for (const d of OPENINGS.guardDoors) {
    if (level < 2) leafDoorway(c, d.at, d.hinge, P.wood)
    else { c.clear(...d.at); c.clear(d.at[0], d.at[1] + 1, d.at[2]) } // Level II: no leaves, the way to the galleries
  }
  for (const d of OPENINGS.loggiaDoors) leafDoorway(c, d.at, d.hinge, P.wood)
  for (const l of OPENINGS.ladders) {
    hatchLadder(c, l.x, l.z, l.y0, l.top, l.facing, P.wood)
  }
  for (const w of OPENINGS.windows) for (const [x, y, z] of w.glass) {
    c.set(x, y, z, P.glass)
    if (w.reveal !== undefined) c.clear(x, y, w.reveal) // set back: the wall cell in front of the glass left open
  }
  for (const s of OPENINGS.slits) for (const y of s.ys) {
    c.set(s.x, y, s.z, P.glass); c.set(s.x, y + 1, s.z, P.glass)
    stair(c, s.x, y - 1, s.z, P.courseStairs, s.inward) // a plunging pale sill (its high side in)
  }

  // --- Pass 3: depth and detail: the Loggia's two fronts, its roof, the standard ----------------------------------
  // a timber gallery slung between the stone towers: a spruce post for the pier, spruce sills, a spruce rail and a
  // dark tie beam on the village side
  const lp = { stone: P.post, sill: `${P.wood}_slab`, corbel: P.stoneStairs, floor: P.course }
  loggia(c, { z: 3, pier: L.pier, lights: L.lights, sillY: L.sillY, headY: L.roofY, corbels: false }, lp) // the field front: tall lights up to the eave
  // the village back: open from tower to tower over a stone rail, the gable's end over it
  for (let x = L.x0; x <= L.x1; x++) {
    c.set(x, L.walkY + 1, L.z0, `${P.wood}_fence`)
    c.set(x, L.headY, L.z0, P.beam, { axis: 'x' })
  }
  // the Loggia's roof: a little gable between the towers, ridge along z, overhanging the ram's stand by one
  for (let z = L.roofZ[0]; z <= L.roofZ[1]; z++) {
    stair(c, 4, L.roofY, z, P.roof.stairs, 'east')
    c.set(5, L.roofY, z, P.roof.full)
    stair(c, 6, L.roofY, z, P.roof.stairs, 'west')
    slab(c, 5, L.roofY + 1, z, P.roof.slab)
  }
  const S = M.standard
  standard(c, S.x, S.y, S.z, { pole: P.pole, colour: P.standard, face: 'south', height: S.height })

  if (level >= 2) {
    // --- Level II, the Barbican: walls, the outer gate line, the deck with its murder holes, the parapet ----------
    const B = M.barbican
    for (let z = B.z0; z <= B.z1; z++) for (let x = B.deck.x0; x <= B.deck.x1; x++) c.set(x, 0, z, x >= 4 && x <= 6 ? P.road : plinth)
    for (const [x, , z] of GATES[2].bars) c.set(x, 0, z, P.course)
    for (const w of B.walls) for (let x = w.x0; x <= w.x1; x++) for (let z = B.z0; z <= B.z1; z++) for (let y = 1; y <= 3; y++)
      c.set(x, y, z, y <= T.plinthTop ? plinth : y === T.courseY ? P.course : ashlar)
    // the Galleries (B1427): a passage in each wall under the deck, in from the guardroom through the tower's front
    // wall, lit by a torch at its end and a glazed slit in the flank
    for (const g of B.galleries) {
      for (let z = B.galleryZ[0]; z <= B.galleryZ[1]; z++) for (let y = 1; y <= 2; y++) c.clear(g.x, y, z)
      c.clear(g.door[0], 1, g.door[2]); c.clear(g.door[0], 2, g.door[2])
      c.set(...g.slit, P.glass)
      c.set(...g.torch, 'wall_torch', { facing: 'north' })
    }
    fenceGates(c, GATES[2].gates.map(([x]) => x), 1, 8, P.gate, 'south')
    for (const [x, y, z] of GATES[2].bars) c.set(x, y, z, P.bars, { north: 'false', south: 'false', east: 'false', west: 'false', waterlogged: 'false' })
    // the Deck: stone, but spruce boards over the passage between the gate lines, the murder holes left open
    const D = B.deck
    murderDeck(c, { ...D, y: B.deckY }, (x, y, z) => (x >= B.timber.x0 && x <= B.timber.x1 && z >= B.timber.z0 && z <= B.timber.z1 ? P.deck : ashlar(x, y, z)), B.holes, (x, y, z) => !c.get(x, y, z))
    for (let x = 4; x <= 6; x++) c.set(x, B.deckY, B.z1, P.course) // the outer lintel over the outer stand
    c.set(5, B.deckY, B.z1, P.key)
    for (let x = 4; x <= 6; x++) c.set(x, B.deckY, 7, P.course) // the outer portcullis' lintel
    // the Parapet round the outer edges, merlons with finials on every other cell; the corner Turrets a course higher
    const rim = []
    for (let z = B.z0; z <= B.z1; z++) rim.push([D.x0, z])
    for (let x = D.x0 + 1; x < D.x1; x++) rim.push([x, B.z1])
    for (let z = B.z1; z >= B.z0; z--) rim.push([D.x1, z])
    for (const [x, z] of rim) c.set(x, B.parapetY, z, ashlar)
    crenels(c, rim, B.merlonY, B.merlonY, P.merlon, (i, x, z) => (x + z) % 2 === 0, null) // crenels in front of the towers' slits
    for (const [x, z] of rim) if (c.get(x, B.merlonY, z)) c.set(x, B.merlonY + 1, z, P.cap, post)
    // the Turrets: a 2 x 2 of stone at each front corner, up from the deck to y 7, dressed corners, a pale coping
    for (const t of B.turrets) {
      for (const [x, z] of t.cells) for (let y = B.deckY + 1; y <= B.turretTop; y++)
        c.set(x, y, z, x === t.corner[0] && z === t.corner[1] ? ((y % 2) ? P.quoin : P.quoinAlt) : ashlar)
      for (const [x, z] of t.cells) slab(c, x, B.turretTop + 1, z, P.courseSlab)
      c.set(...[t.corner[0], B.turretTop + 1, t.corner[1]], P.cap, post) // the corner's post, its lantern on it
    }
  }
  const S0 = M.standard
  if (S0.field) { // the town's colours over the portcullis, hung on the loggia's pier, facing the field
    const [x, y, z] = S0.field
    c.set(x, y, z, `${P.standard}_wall_banner`, { facing: 'south' })
  }

  // --- Pass 4: interior (the plan's furniture) ----------------------------------------------------------------------
  for (const f of level >= 2 ? FURNITURE_2 : FURNITURE) furnitureParts[f.part](c, f)

  // --- Pass 5: light ----------------------------------------------------------------------------------------------
  for (const [x, y, z, how] of LIGHTS[level]) c.set(x, y, z, 'lantern', { hanging: String(how === 'hanging'), waterlogged: 'false' })
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const id = level === 1 ? 'gatehouse' : `gatehouse_${level}`
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
