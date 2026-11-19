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
//   tents     discEdge, coneTent, waymark                     (a stepped hide tipi with its poles; a cairn with a cap)
//   climate_desert windcatcher, screenOriel, archBay                 (desert stone houses)
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
export * from './climate.mjs' // flatDeck, parapet, vigas, logCourse, mudroom (biome looks: desert and snowy homes)
export * from './bridges.mjs'
export * from './garden.mjs'
export * from './seats.mjs' // benchRun, raisedBed (outdoor seats and the beds behind them)
export * from './workshop_a_yard.mjs'
export * from './workshop_b.mjs'
export * from './fires.mjs'
export * from './planters.mjs'
export * from './sundials.mjs'
export * from './pavilions.mjs' // octLevel, tentRoof, faceRun, sideBench (gazebos and round pavilions)
export * from './statues.mjs' // profileFigure, legs, antlerPair, brimHat (statues: a profile swept, legs, antlers, a figure's broad-brimmed hat)
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
export * from './post_office_tower.mjs' // pigeonholes, hallBeams, pillarBox, spireTip (post offices: a sorting wall, a hall's tie beams, a street post box, a slender spire tip)
export * from './shrines.mjs' // roundArch, twinFlight (shrines: a round-headed arch in one plane, an altar's twin flights to a middle landing)
export * from './nursery.mjs' // vaultSpans, vaultRoof, gardenWall, loftHoist (walled gardens and glasshouses: a barrel vault of glass on ribs, a low wall with piers, a loft's hoist)
export * from './shore.mjs' // upturnedHull, transom, pier, dryingRack, beachedBoat, boatMast, raggedCloth (shore buildings: an upturned boat for a roof, a pier on piles, a fish-drying rack; the Castaway Camp's boat on its stems, a mast and yard, a torn sail)
export * from './landing.mjs' // platformCanopy, mast (ferry and station platforms: a lean-to canopy with brackets and a valance, a signal mast; the Ferry House, 49)
export * from './watermill_lucam.mjs' // raisedPond, overshotWheel, driveToMillstone, hurst, lucam, sluiceGate (watermills: a raised mill pond, a launder over an overshot wheel into a closed pit, the millstone on its hurst, a gable's sack-hoist hood)
export * from './weavers.mjs' // lightBand, mouldedCourse, takingInDoor, tenterFrame, dyeRows, leanTo (weavers' houses: the long mullioned window, a moulded course, a taking-in door with its hoist, a tenter frame of dyed cloth, rows of dye flowers, a fold's open lean-to)
export * from './kilns.mjs' // inDisc, bottleKiln (a round course test; a potter's brick bottle kiln whose neck is the house's chimney, 49)
export * from './chapel_court.mjs' // copedGable, courtWall, louvres, pew, choirStall (chapels: a coped parapet gable on a roof's profile, a coped wall with piers, belfry louvres, pews with an aisle arm, choir stalls)
export * from './graveyard_lychgate.mjs' // yardWall, lychgate, parapetGable, lanternTower, pewRun, columnarYew (churchyards: a coped wall with piers, a roofed lychgate, a stone parapet gable, a lantern of the dead, pews, an Irish yew)
export * from './clock_tower_drive.mjs' // fallRace, dialFace, bellcastSpire, kineticTrain (clock towers: a spout race down a tower's corner past a wheel, a clock face in its dial surround, a bell-cast spire, a Create drive from a list)
export * from './bells.mjs' // facedDais, flaredHead, bellChamber (bells and stone lanterns: a dais stepped on every face, a head flared out on corbels, an open bell chamber; the Shift Bell, 49)
export * from './wagons.mjs' // cartWheel, cartBed, barCage, cageLid, shafts, ridgeTent, warStandard (cage wagons and raiders' camps: a disc wheel, a barred cage between posts, a stepped canvas tent, a standard)
export * from './tents.mjs' // discEdge, coneTent, waymark (the Traveler's Camp, 49: a stepped hide tipi, its poles out of the smoke hole; a wayfarer's cairn)
export * from './trail_marks.mjs' // trailGround, wheelRuts, ragPost, burstBarrel, arrowStuds, coverHoop (trail marks: a drawn ground layer, wheel ruts, a post with rags tied on, a burst barrel, arrows stuck in a crate, a cover thrown over its hoop)
export * from './bandit_fort_stronghold.mjs' // stakeWall, poleTent, legTower, stepLeanTo, jettyBand (the Bandit Fort, 49: a ragged stake wall with its fire-step, an A-frame tent on a ridge pole, a watchtower on legs, a stepped lean-to, a keep's jettied timber band)
export * from './stockade_lockup.mjs' // serratedStakes, leverDoor, stiltLookout, innerWale (raiders' stockades: a serrated stake ring, a lever-worked iron door that really opens, a lookout on four posts, a lashing rail)
export * from './farm_fields.mjs' // fieldPlot, kerbFence, scarecrow, composter, eaveBrackets (farms: watered farmland with its crops, a fenced kerb, a scarecrow, the Farmer's composter, brackets under a long eave; the Farmstead, 49)
export * from './ranch.mjs' // ranchGate, horseTrough, stallFront, cupola, hayHood, paddockFence (the Ranch, 49: a ranch gate over the way in, a ground-level trough, box-stall fronts, a ridge cupola, a hay hood with its hoist, a paddock fence with a man-gap)
export * from './fl_cabin.mjs' // logPen, porchGable (log cabins: a pen of notched round logs, a little gable on posts over a porch's mouth; the Fletcher's Lodge, 49)
export * from './ps_press.mjs' // psRoofUnion, psLightLantern, psArch, psParts, psBracketLamp (print shops: merged gable roofs, a glazed ridge lantern, an inner arch, slab counters and ink candles)
export * from './ts_cloth.mjs' // clothBolts, boltRack, dressForm, clothBay, postBanner, steepCap (tailors: bolts of cloth on stands and racks, a dress form, a drying bay of hung cloths, a banner on a post, a steep hollow tower cap with no overhang; the Tailor's Shop, 49)
export * from './glass_cone.mjs' // coneStage, glassCone, bullseye, kilnSiege, eyebrow (the Glassblower's Workshop, 50.7: a corbelled brick glass cone over the kiln, a crown-glass bullseye, a kiln's brick siege, a drip mould)
export * from './jw_vault.mjs' // jwBarrelVault, jwButtress, jwButton, jwVitrine (the Jeweler's Workshop, 50.7: a stone barrel vault from its section, stepped buttresses, a stone button, a glass case out in the street)
export * from './crafters_sawtooth.mjs' // sawHeights, sawtoothRoof, clerestory, sawTrim, twinFlue, proudPosts, wallShelf (the Crafter's Workshop, 50.7: a north-light saw-tooth roof, its glazed faces, a twin-flue stack, a wall shelf)
export * from './workshop_press.mjs' // pressOverDepot, spoutTank, weatheredSkirt (the Workshop, 50.7: Create's press over its depot, a head tank with its spout box, a weathered plinth skirt)
export * from './climate_desert.mjs' // windcatcher, screenOriel, archBay (desert stone houses: a slotted windcatcher tower, a lattice oriel, a round-headed bay)
export * from './fort_walls.mjs' // wallWalk, fightingStage, bretesche, pennant, bedroll (fort walls: a raised walk on posts and braces, a roofed fighting stage, a timber box over a door, a pennant on an apex, a bedroll; the Bandit Fort, 49 round 2)
export * from './stockade_face.mjs' // proudFace, bareHoops, groundCloth (stockades: proud posts and a stepped bank before a stake wall, a wagon's bare hoops, a cloth spread on the ground; the Raiders' Stockade, 49 round 2)
export * from './wrecks.mjs' // sideWagon, underGear, sideBonnet, spokeWheel, flatWheel, scatter (wrecks: a wagon on its side with its gear upright on the underside, a covered wagon's bonnet on its side sagging between its hoops, a wheel with its felloes smashed off, a wheel lying flat, a list of props; the Caravan Wreck, 49)
export * from './bandit_camp_lair.mjs' // patchwork, pitchedTent, ridgePole, marquee, crowsNest, fireRing, trophyStake, hitchRail (outlaw camps: mended stolen cloth, a pitched tent with a cut mouth, a ridge pole, a chief's open-fronted marquee, a crow's nest on legs with its ladder, stones round a fire, a trophy stake, a hitching rail; the Bandit Camp, 49)
export * from './lighthouse_light.mjs' // roundCells, shellCells, railCells, towardCentre, lightDrum, corbelCourse, weathering, lanternRoom, lampCrown, causeway, bollard (lighthouses: a round drum, a gallery's corbels, a weathering ledge, a glazed lantern, a crown of night lamps under inverted daylight detectors, a stone mole, bollards; the Lighthouse, 49)
export * from './observatory_instruments.mjs' // StagedCanvas, roundWall, roundCourse, skyDome, lineTube, quadrantBowl, gnomonStair (staged Wonders and sky instruments: a stage-by-stage canvas, round drums, a voxel dome, a telescope tube along any line, a sundial's quadrant and gnomon stair)
export * from './lighthouse_stair.mjs' // wallRing, spiralStair, lampHood (a stair winding up a round tower's inside wall through its floors, a lantern's hooded eave of night lamps under a stepped cap; the Lighthouse, 49, round 2)
export * from './climate_roofs.mjs' // hipRoof, flatRoof (hot climates: a low hipped roof with deep eaves; a flat mud roof with parapet and toron)
export * from './hot_townhouse.mjs' // rawshan, qaaLantern, badgirHead, datePalm, acaciaTree, slabPent, verandaEave (hot-country merchant houses, 49.10b: a lattice front over the street, a clerestory on a flat roof, a big windcatcher's slotted head, a date palm, a flat-topped acacia, a low slab veranda roof, a veranda's thatch eave with returns)
export * from './market_cross.mjs' // edgeRing8, vertexCells, mercatDrum, crossShaft (market crosses: an outline joined round an octagon, its vertex cells, an octagonal mercat drum with pilasters and a parapet, a cross shaft with a lamp-hung capital and a flag; the Market Cross, 49)
export * from './cold_homes.mjs' // snowLoad, snowSkirt, stormPorch, frontDormer, pentPorch, beamEnds (cold homes, 49.10b: a stepped slate roof with snow on every tread, a drift skirt, a closed storm porch, a steep front dormer, a lodge's front pent on posts, log ends)
export * from './cold_townhouse.mjs' // deepWindow, oriel, sideDormer, wideStack (cold townhouses, 49.10b: panes set back in a thick wall under a lintel with snow on the reveal, a glazed bay over a porch, a broad stack of several flues)
export * from './ph_still_ward.mjs' // hollowPyramid, wardScreens, bayTies, brewingStand, recessedWindow (the Physician's House, 49: a hollow pyramid cap to a louvre, linen screens between ward beds, tie beams across a room, the brewing stand, a window set into a stone wall)
export * from './clinic_ward.mjs' // cappedGableRoof, wardBeds, lampBeam, benchRecess (the Clinic, 49: a 45-degree gable with a closed slab-capped ridge and an optional catslide, beds heads to a wall, a tie beam with its lamp, a seat in a wall's recess)
export * from './healing_spring.mjs' // unionGableRoof, gableFill, glazedLantern, crossWindow, springPool (the Spring Infirmary healing center, 49: gables joined into one roof with valleys, a glazed copper lantern on a ridge, a glass-cross gable window, a rimmed pool)
export * from './guard_house_watch.mjs' // insetStorey, archFace, alarmBell (the Gate Watch guard house, 49: a storey framed on the outer line with its infill a block back, an arch's stone face with its keystone, a bell under an overhang)
export * from './market_awning.mjs' // cornerDepth, lAwning, valance (market stalls: a cell's depth in from a back corner, an L of striped canvas stepping down from a corner, its banner valance; the Market Corner, 49)
export * from './lookout_gallery.mjs' // rimCells, watchGallery, shedRoof, fenceLamp (lookout towers: a rim's cells in order, an open watch gallery on a jettied deck with braces and a beam ring, a lean-to roof off a wall, a lantern on a fence bracket)
export * from './gatehouse_gate.mjs' // steepHip, archHead, leafDoorway, loggia, murderDeck (gatehouses: a steep hipped cap on any rectangle, a gateway's arched head, open plank leaves that are no door block, an open fighting loggia, a deck with murder holes; the Gatehouse, 49)
export * from './fire_station_watch.mjs' // pailRack, trough, downpipe, segmentalArch, hoseReel, lookoutGallery (fire stations and watchtowers: a two-tier pail rack, a rimmed trough, a copper downpipe and spout, a low arch over a three-wide doorway, a hose reel on wheels, a corbelled lookout gallery under a hip cap; the Fire Station, 49)
export * from './barracks_ward.mjs' // mirrorX, gateArch, wallButtress, footlocker, weaponRack (the Barracks, 49: a wing drawn again as its mirror image, a round-headed gate arch, a stone buttress, a footlocker at a bed's foot, a spear rack)
export * from './orchard.mjs' // berryBed, kerb, pergola (berry gardens: a raised bed of ripe bushes, a log kerb, a pergola of posts, beams and tapered rafters that cave vines hang from; the Berry Farm, 49)
export * from './apiary_skep.mjs' // fairMix, skepDome, smokedHive, beeBole, beeFont, hiveCheck (a mix with every weight in its share; apiaries: a straw skep dome course by course, a smoked hive, a hive in a wall niche over its campfire, a bee font, the Beekeeper's rules checked; the Apiary Garden, 49)
export * from './compost_thatch.mjs' // steepUnionRoof, overhangTrim, compostBin (thatched barns: steep gables joined in a T with a straw ridge, trim under an overhang, a compost bin's graded heap behind its board; the Compost Yard, 49)
export * from './greenhouse_glass.mjs' // ridgeSpans, glassRidges, axisOf, glassGable, undershotLeat, deliveryHatch (glasshouses: a ridge-and-furrow glass roof with a raised nave, its glazed gable ends, an undershot wheel in an open leat that starts itself, a depot hatch in a wall; the Greenhouse, 49/50.9)
export * from './great_wall_gate.mjs' // staged, snapshot, wallSection, ridgedHip, pavilionSkirt, archway, portcullis, horseStair, doorIn (long walls and gate pavilions: stage-tagged drawing, a battered section, a flared hip, a skirt roof, an arched passage, portcullis teeth, a broad stair up a wall's face)
export * from './hanging_gardens.mjs' // datePalm, cypress, drape, faceFlight (terraced gardens: a date palm with its dates, a cypress spire, green spilling over a terrace's edge down its face, a stair climbing along a wall's face; the Hanging Gardens, 52.6)
export * from './supply_hoist.mjs' // wallTrellis, gableHoist, signBand, jettyBay, caskStack (shop fronts: a wall trellis, a gable's taking-in hoist with its sign-lamp, a copper sign band on a jetty, glass bays under a jetty, a stack of casks; the Supply Shop, 49)
export * from './shop_board.mjs' // shopBoard, flagPole (shopfronts that open on hinged boards: a fold-down counter ledge, a propped canopy board with a striped valance on poles; a pennant pole; the Market Stall, 49)
export * from './glasshouse.mjs' // glassGableRoof, staging, growingBed (glasshouses: a glazed 45-degree gable with glazing bars and gutters, a nursery's pot staging, a raised growing bed; the Flower Shop, 49)
export * from './geode.mjs' // eggCells, geodeLayers, crystalSpire, glazedBasin, crustVein (geodes: fused egg shells worked in layers from the crust in, a crystal breaking out, a liquid behind glass in a kerb)
export * from './sifting_tipple.mjs' // trestle, chuteFunnel, heap, inclineGallery, sieveRack (screening works: a tower on legs, a chute's funnel over a sieve, heaps, an incline gallery, sieves on a rail)
export * from './quarry_headframe.mjs' // headframe, sheave, rakedStrut, kerbRing, spoilHeap (a timber headframe over a pit with its sheave wheel and raked back-struts, a kerb ring with a gap, a spoil heap; the Quarry, 49)
export * from './crusher_pit.mjs' // crusherPair, twinDrop (crushing mills: Create's crushing wheels over an open catch pit, a cross line dropped by gearboxes into two axles turning into each other; the Crushing Mill, 49)
export * from './windmill_smock.mjs' // octagon, inwardOf, octDrum, octFloor, taperLedge, reefingStage, commonSails, sailPlaneCheck, windshaftDrive, boatCap, tailPole (windmills: an octagonal smock tapering on a drum, a reefing stage on brackets, Create sails in a pinwheel with a check that nothing else rides on them, the windshaft and upright shaft to a millstone, a boat cap, a tail pole; the Windmill, 49)
export * from './hot_terrace.mjs' // rowPilasters, rowParapet, paintedBand, stoop (hot-country row houses, the Terrace's desert and savanna looks, 49.10c: proud piers on the party walls, a flat roof's parapet carried over every party wall, a painted trim, a front stoop)
export * from './cold_terrace.mjs' // rakeVerge, evenRoof45, ridgeStack, skinWall, logEnds (cold terraces, 49.10c: a clean steep rake edge, a 45-degree even-span roof, a stack through the ridge, a thick ground storey's outer skin with deep reveals, notched log ends)
