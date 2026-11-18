// The rebuild kit's parts library (ROADMAP 49.1, tools/blueprints/REBUILD.md): import everything from here.
//   canvas    Canvas, LiftedCanvas (a basement under y 0), mix, DIRS, OPP, stair/slab/trapdoor
//   frames    plinth, steppedPlinth, floor, post, posts, beamRing, cellar
//   walls     wallPanel, window, door, doorway, quoins, stringCourse
//   roofs     gableRoof, flaredGableRoof, catslideRoof, pentice, gableWall, bargeTrim, dormer, crossGableRoof, louvre   (roofs and gables)
//   hearths   chimney, hearthFire
//   kitchens  kitchenParts (counter, stove, worktop, pantry, shelf, plates)
//   stairs    flight, stairwell, lining, hatchLadder                     (stairs and lofts)
//   porches   hood, porch, flowerBed, woodpile
//   railings  railing, railingRun
//   cellars   lampPost                                         (outdoor lights)
//   water     roundCurb, pool, wellShaft, aqueduct, octCells, ringCells, inward, bowl (wells, fountains, cisterns)
//   garden    tree, canopy, lilyPads, boulders                        (ponds, yards, parks)
//   bridges   humpback, lampGate, stream, banks                  (bridges and the water under them)
//   fires     discCells, roundDais, firePit, standingStone, trilithon, spit, logStack (fire pits, stone rings)
//   planters  plantFlower, tieredBed, boxBoards, beamRun, kneeBrace, hangingLantern   (planters, pergolas)
//   furniture furnitureParts (every room piece, kitchens included; couch and blinds)
//   sundials  cradleProfile, cradle, rod, pylon                     (sundials: a cylinder dial and its rod's pylons)
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
export * from './camps.mjs' // campPot, grainBed (the Camp Cook's pot and a raised, watered grain bed)
export * from './village_hall_b_moot.mjs' // stretchedLevel, stretchedTent, octRing, ringFace, wallBench, hangingPot, bellPorch (moot halls: stretched octagons)
export * from './village_hall_a_civic.mjs' // meetingBell, civicParts (hangingPot, bench), wallHearth, belfry, needleSpire, pewRows, civicDormer
export * from './inn_b_arcade.mjs' // arcade, moulding, skirt, bench, coachGate, stall, pitchDormer (the Arcade Inn, 49 B)
export * from './inn_a_coaching.mjs' // jetty, barRun, turningStair, gallery, stalls, caskRack, signPost, gatePiers (coaching inns)
export * from './storehouse_a_store.mjs' // chestRack, tallyDesk (storehouses: chest racks paired into doubles, the tally desk)
export * from './storehouse_b_warehouse.mjs' // chestStack, countingDesk, crowSteps, hoistArm (canal warehouses: paired chests, the desk, stepped gables, hoists)
export * from './schoolhouse_school.mjs' // schoolBellcote, blackboard, lectern (schoolhouses: the bell-cote, the board, the lectern)
export * from './school_dojo.mjs' // sparringRing, moonWindow, strawTarget (the Move Tutor's School, 49 A: a sparring ring in the yard's paving, a round gable window, practice targets)
export * from './library_nave.mjs' // buttress, shelfWall, enchantRing, pyramidRoof, lancet (the Lamplit Nave library, 49)
