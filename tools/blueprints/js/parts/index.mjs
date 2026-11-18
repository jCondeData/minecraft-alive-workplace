// The rebuild kit's parts library (ROADMAP 49.1, tools/blueprints/REBUILD.md): import everything from here.
//   canvas    Canvas, LiftedCanvas (a basement under y 0), mix, DIRS, OPP, stair/slab/trapdoor
//   frames    plinth, steppedPlinth, floor, post, posts, beamRing, cellar, brackets
//   walls     wallPanel, window, door, doorway, quoins, stringCourse
//   roofs     gableRoof, flaredGableRoof, catslideRoof, pentice, pyramidRoof, gableWall, bargeTrim, dormer,
//             evenGableRoof (a narrow house's two-stair ridge, gutters over party walls), crowGable (stepped parapet),
//             crossGableRoof, louvre   (roofs and gables)
//   hearths   chimney, hearthFire
//   kitchens  kitchenParts (counter, stove, worktop, pantry, shelf, plates)
//   stairs    flight, stairwell, lining, hatchLadder                     (stairs and lofts)
//   porches   hood, porch, flowerBed, woodpile, doorCanopy
//   railings  railing, railingRun
//   cellars   lampPost                                         (outdoor lights)
//   water     roundCurb, pool, wellShaft, aqueduct, octCells, ringCells, inward, bowl (wells, fountains, cisterns)
//   garden    tree, canopy, lilyPads, boulders                        (ponds, yards, parks)
//   bridges   humpback, lampGate, stream, banks                  (bridges and the water under them)
//   fires     discCells, roundDais, firePit, standingStone, trilithon, spit, logStack (fire pits, stone rings)
//   planters  plantFlower, tieredBed, boxBoards, beamRun, kneeBrace, hangingLantern   (planters, pergolas)
//   furniture furnitureParts (every room piece, kitchens included; couch and blinds)
//   sundials  cradleProfile, cradle, rod, pylon                     (sundials: a cylinder dial and its rod's pylons)
//   brickwork quoinsLS, plinthCourse, courseOut, faceCells, mullionWindow, kneeler, bayWindow (brick with stone dressings)
//   camps     campPot, grainBed                                (the Camp Cook's pot, a raised watered grain bed)
// Grow it: a part a new family needs that another could use goes here, with its palette passed in, never hard-coded.
export * from './canvas.mjs'
export * from './frames.mjs'
export * from './walls.mjs'
export * from './roofs.mjs'
export * from './hearths.mjs'
export * from './kitchens.mjs'
export * from './stairs.mjs'
export * from './porches.mjs'
export * from './railings.mjs'
export * from './furniture.mjs'
export * from './water.mjs'
export * from './cellars.mjs'
export * from './bridges.mjs'
export * from './garden.mjs'
export * from './seats.mjs' // benchRun, raisedBed (outdoor seats and the beds behind them)
export * from './workshop_a_yard.mjs'
export * from './workshop_b.mjs'
export * from './fires.mjs'
export * from './planters.mjs'
export * from './sundials.mjs'
export * from './pavilions.mjs' // octLevel, tentRoof, faceRun, sideBench (gazebos and round pavilions)
export * from './statues.mjs' // profileFigure, legs, antlerPair (animal statues: a side profile swept, legs, antlers)
export * from './topiary.mjs' // clip, hedgeRun, hedgeArch, tieredTopiary, leafColumn (clipped leaves: hedges, arches, standards)
export * from './carpenter.mjs'
export * from './mason.mjs'
export * from './mill.mjs'
export * from './brickwork.mjs' // quoinsLS, plinthCourse, courseOut, faceCells, mullionWindow, kneeler, bayWindow (brick houses with stone dressings)
export * from './camps.mjs' // campPot, grainBed (the Camp Cook's pot and a raised, watered grain bed)
export * from './village_hall_b_moot.mjs' // stretchedLevel, stretchedTent, octRing, ringFace, wallBench, hangingPot, bellPorch (moot halls: stretched octagons)
export * from './village_hall_a_civic.mjs' // meetingBell, civicParts (hangingPot, bench), wallHearth, belfry, needleSpire, pewRows, civicDormer
export * from './inn_b_arcade.mjs' // arcade, moulding, skirt, bench, coachGate, stall, pitchDormer (the Arcade Inn, 49 B)
export * from './inn_a_coaching.mjs' // jetty, barRun, turningStair, gallery, stalls, caskRack, signPost, gatePiers (coaching inns)
export * from './storehouse_a_store.mjs' // chestRack, tallyDesk (storehouses: chest racks paired into doubles, the tally desk)
export * from './storehouse_b_warehouse.mjs' // chestStack, countingDesk, crowSteps, hoistArm (canal warehouses: paired chests, the desk, stepped gables, hoists)
export * from './schoolhouse_school.mjs' // schoolBellcote, blackboard, lectern (schoolhouses: the bell-cote, the board, the lectern)
export * from './school_dojo.mjs' // sparringRing, moonWindow, strawTarget (the Move Tutor's School, 49 A: a sparring ring in the yard's paving, a round gable window, practice targets)
export * from './library_nave.mjs' // buttress, shelfWall, enchantRing, boxPyramidRoof, lancet (the Lamplit Nave library, 49)
export * from './leaders_hall_arena.mjs' // piers, dais, standard (challenge halls: proud piers, the leader's dais, banner poles)
export * from './guildhall_arcade.mjs' // arcadeRun, jettyCorbels (guildhalls: an open arcade under a jettied hall)
export * from './map_room_tower.mjs' // corbelRing, compassRose, lookoutRail, ladderShaft, batter (towers: a jettied lookout on corbels, a floor compass rose, a ladder shaft, a splayed foot)
export * from './trade_hall_ball.mjs' // roundShaft, ballCap, steppedCap (the Ball Tower trade hall: a chamfered tower and the ball on it)
export * from './palisade_kit.mjs' // extrude, stakeBed, fenceGates, gateGable, stockade, watchHat, setBackPanels, ringFacing (wall kits: an overlapping segment's section, stake ditches, gateways, lookouts)
export * from './ramparts.mjs' // crenels, wallFlight, newel, hoarding, arrowLoop, corbelLine, hipCap, crossGableCrown, batteredFoot (the stone wall kit, 52.3: merlons, steps up a wall's face, timber hoardings with murder holes, loops, a pyramid cap)
export * from './tower_depth.mjs' // cornerPiers, stairPlinth, towerBrackets, mapWall (towers: proud corner piers, a stepped plinth, brackets under an overhang, a banner map wall)
export * from './trade_hall_frame.mjs' // proudFrame, shaftBand, footSkirt, seatCrossGable (a frame proud of its infill, a band round a shaft, a stone skirt, a cross-gable seated in a slope)
export * from './tinker.mjs' // hipCap, skirtRing, doubleChest, lampOn, railSpur (towers with hip caps, workshops)
export * from './smithy.mjs' // taperedStack, quenchTrough, furnaceMouth, clumped (furnace stacks, quench water, a furnace in a wall, patchy rubble)
export * from './foundry.mjs' // shaftRun, gearbox, cog, waterWheel, mixerStack, settledWater, roofLantern, bandedTank (Create foundries)
export * from './butcher.mjs' // smokeTower, lowPyramid, freezer, meatRail, choppingBlock, stockPen, shelter (the Butcher's Shop, 49: a smoke tower, an ice-lined freezer, hooks, a pen)
export * from './kitchen_hall.mjs' // ledgeRing, louvredHip, hipRolls, tieBeams, pilasters, produce (great kitchens: a hipped stone kitchen with a smoke louvre, skirt and corbel courses, tie beams, a larder's produce)
export * from './winery.mjs' // perron, casks, logCaskRack, trellisRow, fruitingVines (wineries: a double perron, log casks on their sides, vineyard rows, glow berries on a beam)
export * from './wayshrine.mjs' // squareRing, inwardFacing, hipCourse, armedBench (open-centred square roofs, benches with stone arms)
export * from './post_office_tower.mjs' // pigeonholes, tieBeams, pillarBox, spireTip (post offices: a sorting wall, a hall's tie beams, a street post box, a slender spire tip)
export * from './shrines.mjs' // roundArch, twinFlight (shrines: a round-headed arch in one plane, an altar's twin flights to a middle landing)
export * from './watermill_lucam.mjs' // raisedPond, overshotWheel, driveToMillstone, hurst, lucam, sluiceGate (watermills: a raised mill pond, a launder over an overshot wheel into a closed pit, the millstone on its hurst, a gable's sack-hoist hood)
