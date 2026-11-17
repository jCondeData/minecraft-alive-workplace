// The rebuild kit's parts library (ROADMAP 49.1, tools/blueprints/REBUILD.md): import everything from here.
//   canvas    Canvas, LiftedCanvas (a basement under y 0), mix, DIRS, OPP, stair/slab/trapdoor
//   frames    plinth, steppedPlinth, floor, post, posts, beamRing, cellar
//   walls     wallPanel, window, door, doorway, quoins, stringCourse
//   roofs     gableRoof, flaredGableRoof, catslideRoof, pentice, gableWall, bargeTrim, dormer,   (roofs and gables)
//             steppedGable (crow steps), pyramidRoof (a tower's spire)
//   hearths   chimney, hearthFire
//   kitchens  kitchenParts (counter, stove, worktop, pantry, shelf, plates)
//   stairs    flight, stairwell, lining, hatchLadder                     (stairs and lofts)
//   porches   hood, porch, flowerBed, woodpile
//   railings  railing, railingRun
//   cellars   lampPost                                         (outdoor lights)
//   water     roundCurb, pool                                  (wells, fountains, cisterns)
//   furniture furnitureParts (every room piece, kitchens included; couch and blinds)
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
