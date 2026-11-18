// Nether Gate (nether_gate, nether_gate_2), the recipe: assembles plan.mjs from the shared parts library (../parts) in
// passes (block-out and structure, openings, depth and detail, interior, ground, light). One drawing for both levels:
// Level II is Level I with the Haul House grown behind the lodge and the Wart Terrace behind the gate, so Level I's
// cells stay put. LEVEL=1|2 picks the level (default 1); CUT=<y> builds a cutaway with everything above y removed.
// Writes furniture.json (the level's furniture spots) beside it for rebuild.py.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { LEVELS, PALETTE, MASSES, OPENINGS, FURNITURE, TERRACE } from './plan.mjs'
import {
  Canvas, mix, stair, slab, plinth, floor, post, beamRing, wallPanel, window, door, gableRoof, flaredGableRoof,
  gableWall, bargeTrim, brackets, furnitureParts, doubleChest,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const at = (lv, f) => (f.level ?? 1) <= lv

export function draw(level = 1) {
  const c = new Canvas(LEVELS[level].size)
  const P = PALETTE
  const base = mix(P.plinth, 3)
  const pier = mix(P.pier, 5)
  const court = mix(P.court, 9)
  const D = MASSES.dais, F = MASSES.frame, L = MASSES.lodge
  const keepFree = (x, y, z) => !c.get(x, y, z)

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // the Dais: a stone plinth, a broad flight of two steps across the front, two down at the back, a step along the
  // west edge, the court floored in polished stone (basalt in the lodge)
  plinth(c, D.x0, D.z0, D.x1, D.z1, base)
  for (let x = D.frontSteps[0]; x <= D.frontSteps[1]; x++) {
    stair(c, x, 0, 0, P.step, 'south')
    stair(c, x, 1, 1, P.step, 'south')
  }
  for (let x = D.backSteps[0]; x <= D.backSteps[1]; x++) {
    stair(c, x, 1, 7, P.step, 'north')
    if (level === 1) stair(c, x, 0, 8, P.step, 'north')
  }
  floor(c, 1, 2, 8, 6, D.floorY, court)
  floor(c, L.x0, L.z0, L.x1, L.z1, D.floorY, P.floor)
  for (let z = 2; z <= 6; z++) stair(c, 0, 1, z, P.step, 'east')
  // the soul-fire pedestals at the front corners: two dressed blocks, a soul campfire burning on top
  for (const x of [0, 12]) {
    c.set(x, 0, 0, P.dressed); c.set(x, 1, 0, P.cap); c.set(x, 1, 1, P.dressed)
    c.set(x, 2, 0, 'soul_campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  }

  // the Piers: 2 x 3 blackstone, basalt at the outer corners, a dressed foot, a gilded band, chiseled caps
  const [pz0, pz1] = MASSES.pierZ
  for (const { x0, x1 } of MASSES.piers) {
    const outer = x0 === 1 ? x0 : x1
    for (let x = x0; x <= x1; x++) for (let z = pz0; z <= pz1; z++) for (let y = 1; y <= MASSES.pierTop; y++) {
      const corner = x === outer && (z === pz0 || z === pz1)
      if (y === MASSES.pierTop) c.set(x, y, z, P.cap)
      else if (corner) c.set(x, y, z, P.pierCorner, { axis: 'y' })
      else if (y === 1) c.set(x, y, z, P.dressed)
      else if (y === 5) c.set(x, y, z, P.band)
      else c.set(x, y, z, pier)
    }
  }

  // the Frame: obsidian round a 2 x 3 inside (x 4-5, y 2-4, z 3), gilded corners (PortalShape needs no corners, and
  // no other obsidian is drawn anywhere: exactly one frame for Builders.lightPortals)
  for (let x = F.x0; x <= F.x1; x++) for (let y = F.y0; y <= F.y1; y++) {
    const edgeX = x === F.x0 || x === F.x1, edgeY = y === F.y0 || y === F.y1
    if (edgeX && edgeY) c.set(x, y, F.z, P.frameCorner)
    else if (edgeX || edgeY) c.set(x, y, F.z, 'obsidian')
    else c.clear(x, y, F.z)
  }

  // the Head: the solid lintel over the frame between the piers (y 6-7), shouldered either side of the opening
  for (let x = F.x0; x <= F.x1; x++) for (let z = pz0; z <= pz1; z++) for (let y = F.y1 + 1; y <= MASSES.pierTop; y++) {
    const face = z === pz0 || z === pz1
    c.set(x, y, z, !face ? pier : y === MASSES.pierTop ? P.cap : P.dressed)
  }
  for (const z of [pz0, pz1]) {
    brackets(c, [[F.x0, F.y1, z]], P.corbel, 'west')
    brackets(c, [[F.x1, F.y1, z]], P.corbel, 'east')
  }

  // the Eave beams: crimson stems a block proud of the piers, front and back, carrying the lower eave
  const B = MASSES.beams
  for (const z of B.z) for (let x = B.x[0]; x <= B.x[1]; x++) c.set(x, B.y, z, P.stem, { axis: 'x' })

  // the Lower Eave: the gate's first roof, a flared skirt front and back (a dark nether-brick kick, a crimson course
  // above it on the beam), running the gate's width
  const LE = MASSES.lowerEave
  for (let x = LE.x[0]; x <= LE.x[1]; x++) {
    stair(c, x, LE.y, 0, P.kick, 'south'); stair(c, x, LE.y, 6, P.kick, 'north')
    stair(c, x, LE.y + 1, 1, P.roof.stairs, 'south'); stair(c, x, LE.y + 1, 5, P.roof.stairs, 'north')
  }

  // the Frieze: the band between the two roofs (y 8-10) over both piers, basalt at its corners, a gilded plaque
  // in the middle of every face
  const FR = MASSES.frieze
  for (let x = FR.x0; x <= FR.x1; x++) for (let z = pz0; z <= pz1; z++) for (let y = FR.y0; y <= FR.y1; y++) {
    const fz = z === pz0 || z === pz1, fx = x === FR.x0 || x === FR.x1
    if (fz && fx) c.set(x, y, z, P.pierCorner, { axis: 'y' })
    else if (y === FR.y0 + 1 && ((fz && (x === 4 || x === 5)) || (fx && z === 3))) c.set(x, y, z, P.band)
    else if (y === FR.y0 + 1 && fz && (x === 3 || x === 6)) c.set(x, y, z, P.cap)
    else if (y === FR.y1) c.set(x, y, z, P.dressed)
    else c.set(x, y, z, pier)
  }

  // the Gate Roof: steep crimson over the frieze, flared at the eaves in the same dark kick, its gables planked over
  // the frieze's ends with a stripped king post, the attic filled solid; warped barge boards under the overhangs
  const gr = flaredGableRoof(c, MASSES.gateRoof, P.roof, P.kick)
  for (let x = FR.x0; x <= FR.x1; x++) for (let z = pz0; z <= pz1; z++) for (let y = FR.y1 + 1; y < gr.under[z]; y++) {
    const end = x === FR.x0 || x === FR.x1
    c.set(x, y, z, end && z === 3 ? P.stripped : P.gable, end && z === 3 ? { axis: 'y' } : {})
  }
  for (const x of MASSES.gateRoof.along) bargeTrim(c, 'x', x, MASSES.gateRoof.across, gr.under, `${P.trim}_slab`)
  // horned ridge ends: a crimson post at each end of the ridge, a soul lantern on it
  for (const x of MASSES.gateRoof.along) {
    c.set(x, gr.ridgeY + 1, 3, `${P.roof.full.replace('_planks', '')}_fence`)
    c.set(x, gr.ridgeY + 2, 3, 'soul_lantern', { hanging: 'false', waterlogged: 'false' })
  }

  // the Lodge: a crimson frame on the dais, quartz infill, its front to the street beside the east pier; a beam ring
  // at y 5 under a 45-degree gable roof tucked under the gate's eave
  for (const [x, z] of [[L.x1, L.z0], [L.x1, L.z1], [L.x0, L.z1]]) post(c, x, z, 2, L.wallTop, P.stem)
  wallPanel(c, [L.x0 + 1, L.z1], [L.x1 - 1, L.z1], 2, L.wallTop, P.infill) // back
  wallPanel(c, [L.x1, L.z0 + 1], [L.x1, L.z1 - 1], 2, L.wallTop, P.infill) // east
  wallPanel(c, [L.x0, L.z1 - 1], [L.x0, L.z1 - 1], 2, L.wallTop, P.infill) // west, behind the pier
  wallPanel(c, [L.x0, L.z0], [L.x1 - 1, L.z0], 2, L.wallTop, P.infill) // front
  beamRing(c, L.x0, L.z0, L.x1, L.z1, L.beamY, P.stem)
  const lr = gableRoof(c, MASSES.lodgeRoof, P.lodgeRoof, keepFree)
  for (const x of [L.x0, L.x1]) gableWall(c, 'x', x, MASSES.lodgeRoof.across, L.beamY + 1, lr.under, (z, y) =>
    c.get(x, y, z) ? null : (z === 4 && y === 7 ? [P.stripped, { axis: 'y' }] : P.infill))

  // --- Pass 2: openings ----------------------------------------------------------------------------------------
  for (const w of OPENINGS.windows) if (at(level, w)) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.trim })
  const ld = OPENINGS.lodgeDoor
  door(c, ld.at, ld.facing, ld.out, { wood: P.door })

  // --- Level II: the Haul House and the Wart Terrace ------------------------------------------------------------
  if (level >= 2) { haulHouse(c, P, base); terrace(c, P, base) }

  // --- Pass 3: depth and light ---------------------------------------------------------------------------------
  // soul lanterns hanging from the eave beams over the frame's sides, front and back
  for (const [x, z] of [[F.x0, 1], [F.x1, 1], [F.x0, 5], [F.x1, 5]]) c.set(x, B.y - 1, z, 'soul_lantern', { hanging: 'true', waterlogged: 'false' })
  // a lantern under the lodge's front eave, by its open front
  c.set(L.x1, L.beamY - 1, L.z0 - 1, 'lantern', { hanging: 'true', waterlogged: 'false' })

  // --- Pass 4: interior (the plan's furniture) -------------------------------------------------------------------
  for (const f of FURNITURE) {
    if (!at(level, f)) continue
    if (f.part === 'brazier') { c.free(f.pos); c.set(...f.pos, 'aliveworkplace:nether_brazier', { facing: f.facing }); continue }
    if (f.part === 'doubleChest') { doubleChest(c, f.pos, f.facing, f.along); continue }
    if (f.part === 'caskRack') { caskRack(c, f.pos); continue }
    furnitureParts[f.part](c, f)
  }
  return c
}

// the Haul House (Level II): a taller timbered store behind the lodge, its front gable rising over the lodge roof,
// a dressed stone course at its foot, a door down to the terrace
function haulHouse(c, P, base) {
  const H = MASSES.haul
  const { x0, x1, z0, z1, wallTop, beamY } = H
  plinth(c, x0, z0, x1, z1, base)
  floor(c, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, P.floor)
  for (let x = x0; x <= x1 - 1; x++) { c.clear(x, 5, 7); c.clear(x, 6, 6) } // the lodge's back eave, now inside
  // walls: a dressed course at y 1, infill to the wall top; the lodge's back wall is the party wall
  const ring = []
  for (let x = x0; x <= x1; x++) ring.push([x, z1])
  for (let z = z0; z <= z1; z++) { ring.push([x0, z]); ring.push([x1, z]) }
  for (const [x, z] of ring) {
    c.set(x, 1, z, P.dressed)
    for (let y = 2; y <= wallTop; y++) if (!(z === z0 && x < x1 && y <= MASSES.lodge.wallTop)) c.set(x, y, z, P.infill)
  }
  for (let x = x0; x < x1; x++) for (let y = MASSES.lodge.wallTop + 1; y <= wallTop; y++) c.set(x, y, z0, P.infill)
  for (const [x, z] of [[x1, z0], [x1, z1], [x0, z1]]) post(c, x, z, 2, wallTop, P.stem) // the corners (the lodge's post is the fourth)
  // a girt round the outer walls over the windows and the door: no quartz panel taller than three blocks
  for (let z = z0 + 1; z < z1; z++) { c.set(x0, 5, z, P.stripped, { axis: 'z' }); c.set(x1, 5, z, P.stripped, { axis: 'z' }) }
  for (let x = x0 + 1; x < x1; x++) c.set(x, 5, z1, P.stripped, { axis: 'x' })
  beamRing(c, x0, z0, x1, z1, beamY, P.stem)
  for (let x = x0 + 1; x <= x1 - 1; x++) c.set(x, beamY, 9, P.stem, { axis: 'x' }) // a tie beam (the lamp hangs from it)
  const hr = gableRoof(c, MASSES.haulRoof, P.lodgeRoof, (x, y, z) => !c.get(x, y, z))
  for (const z of [z0, z1]) gableWall(c, 'z', z, MASSES.haulRoof.across, beamY + 1, hr.under, (x, y) =>
    c.get(x, y, z) ? null : x === 11 && !(z === z0 && y === beamY + 1) ? [P.stem, { axis: 'y' }] : P.infill)
  for (let z = z0; z <= z1; z++) c.set(x0, beamY, z, P.stem, { axis: 'z' })
  bargeTrim(c, 'z', MASSES.haulRoof.along[1], MASSES.haulRoof.across, hr.under, P.kick)
  // openings: the doorway from the lodge, the door to the terrace with its step
  const hd = OPENINGS.haulDoorway.at
  for (let y = hd[1]; y <= hd[1] + 1; y++) c.clear(hd[0], y, hd[2])
  const td = OPENINGS.terraceDoor
  door(c, td.at, td.facing, td.out, { wood: P.trim, step: td.step, stepStair: P.step }) // warped: the store's door
  // a soul lantern on a chain from the eave over the door's step
  for (let y = 5; y < MASSES.haulRoof.eaveY; y++) c.set(td.at[0] - 1, y, td.at[2], 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(td.at[0] - 1, 4, td.at[2], 'soul_lantern', { hanging: 'true', waterlogged: 'false' })
}

// the Wart Terrace (Level II): a paved yard behind the gate, two raised beds of soul sand with ripe nether wart, a
// basalt path with shroomlights set in it, a low blackstone wall with lantern pillars, a soul-fire pillar at the end
function terrace(c, P, base) {
  const T = MASSES.terrace
  for (let x = T.x0; x <= T.x1; x++) for (let z = 8; z <= T.z1; z++) c.set(x, 0, z, mix(P.court, 13))
  for (let z = T.z0 + 1; z <= T.z1; z++) c.set(TERRACE.path.x, 0, z, P.path, { axis: 'y' })
  for (const b of TERRACE.beds) {
    for (let x = b.x0; x <= b.x1; x++) for (let z = b.z0; z <= b.z1; z++) {
      const corner = (x === b.x0 || x === b.x1) && (z === b.z0 || z === b.z1)
      c.set(x, 0, z, P.bed)
      if (corner) { c.set(x, 1, z, P.bed); c.set(x, 2, z, 'nether_brick_fence'); continue }
      c.set(x, 1, z, P.soil)
      c.set(x, 2, z, 'nether_wart', { age: '3' })
    }
  }
  for (const [x, y, z] of TERRACE.lights) c.set(x, y, z, 'shroomlight')
  // the wall: west side and back, lantern pillars at its ends and corner, the soul-fire pillar at the path's end
  const pillar = (x, z, top) => {
    c.set(x, 1, z, P.dressed); c.set(x, 2, z, P.wall)
    c.set(x, 3, z, top, top === 'soul_campfire' ? { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' } : { hanging: 'false', waterlogged: 'false' })
  }
  for (let z = 8; z <= T.z1 - 1; z++) c.set(T.x0, 1, z, P.wall)
  for (let x = T.x0 + 1; x <= T.x1 - 1; x++) c.set(x, 1, T.z1, P.wall)
  pillar(T.x0, T.z0, 'soul_lantern'); pillar(T.x0, T.z1, 'soul_lantern'); pillar(T.x1, T.z1, 'soul_lantern')
  const [px, pz] = TERRACE.pillar
  c.set(px, 1, pz, P.pierCorner, { axis: 'y' }); c.set(px, 2, pz, P.cap)
  c.set(px, 3, pz, 'soul_campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
}

// barrels on their sides on the Haul House's back wall, a second row over the ends (the window between them clear)
function caskRack(c, [x0, y, z]) {
  for (let x = x0; x <= x0 + 2; x++) c.set(x, y, z, 'barrel', { facing: 'north', open: 'false' })
  for (const x of [x0, x0 + 2]) c.set(x, y + 1, z, 'barrel', { facing: 'north', open: 'false' })
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const name = level === 2 ? 'nether_gate_2' : 'nether_gate'
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${name}_cut${cut}` : name } }
}
