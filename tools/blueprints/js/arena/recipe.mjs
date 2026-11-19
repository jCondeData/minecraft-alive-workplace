// The Arena (ROADMAP 49 rebuild), the recipe: draws plan.mjs's three levels from the shared parts library (../parts)
// in passes (block-out and structure, openings, depth and detail, interior, ground and light) for LEVEL = 1, 2 or 3.
// Each level is the last one with more built onto it: II redraws I's front wall as the colonnade, III builds the
// grandstand over I's back wall and the towers at its ends. Writes furniture.json (every furniture spot) beside it.
// Build: build.sh (all three levels, saved as candidates); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZES, PALETTE as P, SPOTS, SEATS, MASSES as M, OPENINGS, FURNITURE } from './plan.mjs'
import {
  Canvas, mix, stair, slab, trapdoor, post, beamRing, wallPanel, window, door, gableRoof, gableWall, bargeTrim, hood,
  flight, railingRun, flowerBed, lampPost, flagPole, hipRoof, benchRun, furnitureParts,
  battlePitch, arcadeFace, trainerBox, bannerMast,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(level) {
  const [W, , D] = SIZES[level]
  const c = new Canvas(SIZES[level])
  const ground = mix(P.ground, 11), masonry = mix(P.masonry, 5), plaster = mix(P.plaster, 3)
  const R = SPOTS.ring
  const seat = `${P.wood}_stairs`, fence = `${P.wood}_fence`

  // --- Pass 1: block-out --------------------------------------------------------------------------------------------
  // the ground: cobbled paving over the whole plot, the pitch marked out on it
  c.fill(0, 0, 0, W - 1, 0, D - 1, ground)
  battlePitch(c, R, P.pitch)

  // the two trainer's boxes, red (west) and blue (east), the step at the back of each
  const box = (spot, ring, colour) => trainerBox(c, spot, { ring, entry: 'south' }, {
    dais: P.dressed, mark: `${colour}_terracotta`, post: P.frame, beam: P.frame, ceiling: P.deck, rail: fence,
    step: P.springer, roof: P.roof,
  })
  box(SPOTS.boxes[0], 'east', P.red)
  box(SPOTS.boxes[1], 'west', P.blue)

  // the back stand (I): a coped wall along z 18 with piers, one row of seats before it, a podium in the middle
  const B = M.backWall
  wallPanel(c, [B.x0, B.z], [B.x1, B.z], 1, 2, masonry)
  for (let x = B.x0; x <= B.x1; x++) if (!B.piers.includes(x)) slab(c, x, 3, B.z, P.coping)
  for (const x of B.piers) { c.set(x, 3, B.z, masonry); if (x === 8 || x === 16) c.set(x, 4, B.z, P.dressed); else slab(c, x, 4, B.z, P.coping) } // lamp piers get a full cap
  for (const x of [B.x0, B.x1]) wallPanel(c, [x, 17], [x, 17], 1, 2, masonry) // the stand's ends
  for (const x of [B.x0, B.x1]) slab(c, x, 3, 17, P.coping)
  benchRun(c, { x0: 5, x1: 10, y: 1, z: 17, facing: 'north' }, { seat })
  benchRun(c, { x0: 14, x1: 19, y: 1, z: 17, facing: 'north' }, { seat })
  c.set(11, 1, 17, masonry); c.set(13, 1, 17, masonry); c.set(12, 1, 17, P.carved)

  // --- Pass 2: the front: I's wall and gate, or II's colonnade over the fair lane ------------------------------------
  const F = M.frontWall
  if (level === 1) {
    for (const x of F.piers) {
      const tall = x === 10 || x === 14 // the gate piers
      for (let y = 1; y <= (tall ? 3 : 2); y++) c.set(x, y, F.z, masonry)
      if (tall) c.set(x, 4, F.z, P.dressed); else slab(c, x, 3, F.z, P.coping)
    }
    for (let x = F.x0; x <= F.x1; x++) if (!F.piers.includes(x) && (x < F.gate[0] || x > F.gate[1])) c.set(x, 1, F.z, P.parapet)
    c.set(14, 5, F.z, 'lantern', { hanging: 'false', waterlogged: 'false' }) // a lamp on the east gate pier
  } else {
    const C = M.colonnade
    // both faces arcaded on the same piers, the gate bay taller (square-headed on the pitch side)
    arcadeFace(c, { line: C.z0, piers: C.piers }, { pier: masonry, spring: P.springer })
    arcadeFace(c, { line: C.z1, piers: C.piers }, { pier: masonry, spring: P.springer })
    for (const x of [C.x0, C.x1]) wallPanel(c, [x, C.z0], [x, C.z1], 1, 3, masonry) // closed ends
    // the deck: a beam along each face, boards between
    for (let x = C.x0; x <= C.x1; x++) {
      c.set(x, C.deckY, C.z0, P.frame, { axis: 'x' })
      c.set(x, C.deckY, C.z1, P.frame, { axis: 'x' })
      for (let z = C.z0 + 1; z < C.z1; z++) c.set(x, C.deckY, z, P.deck)
    }
    // a landing at each end, carried on a solid end bay, and a flight up to it from the pitch side
    for (const x of [C.x0 - 1, C.x1 + 1]) {
      wallPanel(c, [x, C.z0], [x, C.z1 - 1], 1, 3, masonry)
      c.set(x, C.deckY, C.z0, P.frame, { axis: 'x' })
      for (let z = C.z0 + 1; z < C.z1; z++) c.set(x, C.deckY, z, P.deck)
    }
    for (const f of OPENINGS.flights) flight(c, f.steps, f.rises, P.wood, masonry)
    // 18 seats along the deck's pitch edge, a parapet to the street
    benchRun(c, { x0: 2, x1: 10, y: 5, z: C.z1, facing: 'south' }, { seat })
    benchRun(c, { x0: 14, x1: 22, y: 5, z: C.z1, facing: 'south' }, { seat })
    for (let x = C.x0 - 1; x <= C.x1 + 1; x++) if (x < 10 || x > 14) c.set(x, 5, C.z0, P.parapet)

    // the judges' loggia over the gate: posts on the beams, a lintel to hang the gate banner from, a beam ring, a gable
    const L = M.loggia
    for (const x of [L.x0, L.x1]) for (const z of [L.z0, L.z1]) post(c, x, z, 5, L.beamY - 1, P.frame)
    beamRing(c, L.x0, L.z0, L.x1, L.z1, L.beamY, P.frame)
    for (let x = L.x0 + 1; x < L.x1; x++) { c.set(x, 5, L.z0, fence); c.set(x, 7, L.z0, P.frame, { axis: 'x' }) }
    const lr = gableRoof(c, L.roof, P.roof)
    const gable = (u, y) => (u === 12 ? [P.frame, { axis: 'y' }] : plaster) // a king post up the gable's middle
    gableWall(c, 'z', L.z0, L.roof.across, L.beamY + 1, lr.under, gable)
    gableWall(c, 'z', L.z1, L.roof.across, L.beamY + 1, lr.under, gable)
    for (const u of [L.x0, L.x1]) for (const z of [L.z0, L.z1]) c.set(u, L.beamY + 1, z, P.frame, { axis: 'y' }) // the rake's foot
    bargeTrim(c, 'z', L.roof.along[1], L.roof.across, lr.under, `${P.wood}_stairs`)
  }

  // --- Pass 3: the grandstand and the trainers' towers (III) ------------------------------------------------------------
  if (level >= 3) {
    const G = M.grandstand
    // tiers: rows two and three on solid masonry over I's back wall, a promenade, the back wall with windows
    for (let x = G.x0; x <= G.x1; x++) {
      c.clear(x, 3, 18); c.clear(x, 4, 18)
      const mid = x >= 11 && x <= 13
      if (!mid) {
        c.set(x, 1, 18, masonry); c.clear(x, 2, 18)
        wallPanel(c, [x, 19], [x, 19], 1, 2, masonry)
      }
      wallPanel(c, [x, 20], [x, 20], 1, 2, masonry); c.set(x, 3, 20, P.deck)
      wallPanel(c, [x, G.backWall], [x, G.backWall], 1, G.beamY, masonry)
    }
    benchRun(c, { x0: 5, x1: 10, y: 2, z: 18, facing: 'north' }, { seat })
    benchRun(c, { x0: 14, x1: 19, y: 2, z: 18, facing: 'north' }, { seat })
    benchRun(c, { x0: 5, x1: 10, y: 3, z: 19, facing: 'north' }, { seat })
    benchRun(c, { x0: 14, x1: 19, y: 3, z: 19, facing: 'north' }, { seat })
    // the Leader's box: a dressed front on I's podium, a floor at the promenade's level, posts, a rail, a throne
    for (let x = 11; x <= 13; x++) for (let z = 17; z <= 19; z++) {
      for (let y = 1; y <= 2; y++) c.set(x, y, z, z === 17 ? (x === 12 ? P.carved : P.dressed) : masonry)
      c.set(x, 3, z, z === 17 ? P.dressed : P.deck)
    }
    for (const x of [11, 13]) post(c, x, 17, 4, G.beamY - 1, P.frame)
    c.set(12, 4, 17, fence)
    for (const x of [11, 13]) c.set(x, 4, 18, fence)
    stair(c, 12, 4, 19, seat, 'south')
    for (const x of [11, 13]) c.set(x, 4, 19, `stripped_${P.wood}_log`, { axis: 'y' })
    // the front beam the roof's eave rests on, from tower to tower
    for (let x = G.x0; x <= G.x1; x++) if (x !== 11 && x !== 13) c.set(x, G.beamY, 17, P.frame, { axis: 'x' })
    for (const x of [11, 13]) c.set(x, G.beamY, 17, P.frame, { axis: 'y' })
    // piers proud of the back wall, carrying the eave
    for (const x of [8, 12, 16]) wallPanel(c, [x, G.eave], [x, G.eave], 1, G.beamY - 1, masonry)
    for (let x = G.x0; x <= G.x1; x++) c.set(x, 4, G.backWall, P.dressed) // a dressed course at the promenade's floor

    // the towers: stone to the beam, plaster and timber above, a hip roof; the room inside
    for (const T of M.towers) {
      for (let x = T.x0; x <= T.x1; x++) for (let z = T.z0; z <= T.z1; z++) {
        const edge = x === T.x0 || x === T.x1 || z === T.z0 || z === T.z1
        const corner = (x === T.x0 || x === T.x1) && (z === T.z0 || z === T.z1)
        c.set(x, 0, z, edge ? masonry : P.deck)
        for (let y = 1; y <= M.towerTop; y++) {
          if (!edge) { if (y === M.towerBeamY) c.set(x, y, z, P.deck); else c.clear(x, y, z); continue }
          if (y < M.towerBeamY) c.set(x, y, z, masonry)
          else if (y === M.towerBeamY || y === M.towerTop || corner) c.set(x, y, z, P.frame, corner ? { axis: 'y' } : { axis: x === T.x0 || x === T.x1 ? 'z' : 'x' })
          else c.set(x, y, z, plaster)
        }
      }
      for (const x of [T.x0, T.x1]) for (const z of [18, 20]) post(c, x, z, M.towerBeamY + 1, M.towerTop - 1, P.frame) // studs either side of the long faces' windows
      hipRoof(c, { x0: T.x0, z0: T.z0, x1: T.x1, z1: T.z1, eaveY: M.towerTop + 1 }, P.roof)
    }

    // the roofs: the Leader's hero gable first, then the grandstand's roof round it
    const hero = gableRoof(c, G.hero, P.roof)
    const heroFace = (u, y) => (u === 12 && y === 10 ? P.glass : u === 12 && y < 10 ? [P.frame, { axis: 'y' }] : plaster)
    gableWall(c, 'z', G.hero.along[0], G.hero.across, G.beamY + 1, hero.under, heroFace)
    gableWall(c, 'z', G.hero.along[1], G.hero.across, G.beamY + 1, hero.under, heroFace)
    for (let z = G.hero.along[0] + 1; z < G.hero.along[1]; z++) for (const x of [11, 13]) c.set(x, G.hero.eaveY, z, P.roof.full) // the steep slope's fill
    const main = gableRoof(c, G.roof, P.roof, (x, y, z) => !c.get(x, y, z))
    bargeTrim(c, 'z', G.hero.along[0], G.hero.across, hero.under, `${P.wood}_slab`)
  }

  // --- Pass 4: openings ----------------------------------------------------------------------------------------------
  // the notice board on the west gate pier: a dark board under a sign that faces the street
  const [nx, ny, nz] = SPOTS.noticeBoard
  c.set(nx, ny, nz, P.board)
  c.set(nx, ny, nz - 1, `${P.wood}_wall_sign`, { facing: 'north', waterlogged: 'false' })
  if (level >= 3) {
    for (const [i, T] of M.towers.entries()) {
      const d = OPENINGS.towerDoors[i].at, mid = (T.x0 + T.x1) / 2
      door(c, d, 'south', 'north', { wood: P.wood })
      hood(c, d[0], 4, T.z0 - 1, P.roof)
      const outX = i === 0 ? T.x0 : T.x1
      window(c, [[outX, 2, 19], [outX, 3, 19]], i === 0 ? 'west' : 'east', null, { glass: P.glass })
      window(c, [[mid, 2, T.z1], [mid, 3, T.z1]], 'south', null, { glass: P.glass })
      for (const [x, z] of [[mid, T.z0], [outX, 19], [mid, T.z1]]) window(c, [[x, 7, z], [x, 8, z]], 'north', null, { glass: P.glass })
    }
    for (const x of [6, 10, 14, 18]) window(c, [[x, 5, 21], [x, 6, 21]], 'south', 'shutters', { glass: P.glass, wood: P.wood })
  }

  // --- Pass 5: interior (the trainers' rooms) -------------------------------------------------------------------------
  if (level >= 3) for (const f of FURNITURE) {
    if (f.part === 'machine') { c.set(...f.pos, f.block, { facing: f.facing }); continue }
    furnitureParts[f.part](c, f)
  }

  // --- Pass 6: ground, flags and light ---------------------------------------------------------------------------------
  flagPole(c, [0, 14], 1, 7, fence, `${P.red}_banner`, 'north')
  flagPole(c, [24, 14], 1, 7, fence, `${P.blue}_banner`, 'north')
  flowerBed(c, 0, 1, 0, ['poppy', 'oxeye_daisy'])
  flowerBed(c, 23, 24, 0, ['cornflower', 'oxeye_daisy'])
  if (level < 3) { // the back corners: beds of flowers where III's towers will stand
    flowerBed(c, 0, 2, 17, ['poppy', 'allium', 'poppy'])
    flowerBed(c, 22, 24, 17, ['cornflower', 'allium', 'cornflower'])
  }
  if (level < 3) for (const x of [8, 16]) c.set(x, 5, M.backWall.z, 'lantern', { hanging: 'false', waterlogged: 'false' })
  if (level >= 2) {
    // lighting round the pitch: lamp posts at its front corners, lanterns under the deck and on the parapet's ends
    lampPost(c, 4, 1, 6, fence, 2)
    lampPost(c, 20, 1, 6, fence, 2)
    for (const x of [8, 16]) c.set(x, 3, 4, 'lantern', { hanging: 'true', waterlogged: 'false' })
    for (const x of [1, 23]) c.set(x, 6, 2, 'lantern', { hanging: 'false', waterlogged: 'false' })
    // the festival's cloth: the gate banner under the loggia's lintel, the corners' colours on the piers
    c.set(12, 7, 1, `${P.cup}_wall_banner`, { facing: 'north' })
    c.set(6, 3, 1, `${P.red}_wall_banner`, { facing: 'north' })
    c.set(18, 3, 1, `${P.blue}_wall_banner`, { facing: 'north' })
    for (const x of [6, 18]) c.set(x, 3, 6, `${x < 12 ? P.red : P.blue}_wall_banner`, { facing: 'south' })
  }
  if (level >= 3) {
    bannerMast(c, [12, SPOTS.championPole[2]], 1, SPOTS.championPole[1], { base: P.carved, pole: fence, head: P.frame, crown: 'lantern' })
    c.set(12, 2, 16, 'white_wall_banner', { facing: 'north' }) // the Leader's cloth on the box front
  }
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const id = level === 1 ? 'arena' : `arena_${level}`
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
