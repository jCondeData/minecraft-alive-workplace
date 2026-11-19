// The Village Train, the plan (ROADMAP 49/50.13; brief: tools/blueprints/briefs/village_train.md). Drawn before any
// block: the car's frame, its masses, the driver's place, the cargo, the lamp. recipe.mjs assembles it from the parts
// library (../parts, rail_car.mjs) in passes.
//
// The box we draw in: 4 along the track (x 0..3, x = 0 nearest the station), 1 wide (z = 0, on the track line), and
// 4 tall (y 0..3: the Train Station keeps 1 to 4 blocks above its track clear, so we stay inside its 4 even though the
// train's brief allows 5). y = 0 stands on the track. Everything we draw is glued and carried off; every empty cell of
// the box takes whatever stands there with it, so the box is drawn nearly full.
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Handcar. One low deck: the bogey, the driver between his two controls, the chest over the bogey; a flat
//      slab canopy on spruce posts over the lot. Story: the plainest thing a village can put on rails, a covered
//      platform with a man on it. Massing: one flat slab of a car, 3 tall, the same from both ends.
//   B. The Steam Tram. Two masses: a stubby "boiler" over the bogey (the chest as its firebox, a dark casing smokebox,
//      a funnel standing proud of everything) and a three-long open cab behind it, its slab roof on two posts over the
//      driver; a lamp on the cab roof's far end. Story: the village dressed its Create car as the little steam trams
//      that ran down country roads, which were driven from either end, just as Create drives this one. Massing: a
//      tall narrow front, a lower roofed cab, a lamp at the tail: funnel, roof, lamp, the outline everyone reads as
//      "train" from across a valley.
//   C. The Goods Van. A closed box of casings round the chest, the driver up on top in an open lookout seat under a
//      canopy. Story: freight first. Massing: one tall box; the seat on the roof needs the station's 5th layer and the
//      sides show nothing but casing.
// Rounds 1 and 2 built B. Both critics read its three-high stack over the bogey (frame, chest, funnel) as cargo piled
// on the wheels, and its stepped top as ragged; a 4-tall box leaves no room for a funnel that stands proud of a cab
// tall enough to sit in. Round 3 (the last) builds the strongest of what is left, A grown into a proper car:
//   A'. The Railcar. One long low mass: a dark iron underframe the whole length (bogey, two casings, the goods chest
//      slung under the rear platform), the driver up on that deck between his controls fore and aft, and one flat
//      spruce canopy over all four cells on a post at each end, the lamp hung inside by the driver's shoulder. Story:
//      the open railcars that ran mail and parcels between country halts: a roof, a driver, a box of goods under the
//      floor, driven from either end. It reads as a carriage, not a bench on the rails, and its outline is one clean
//      line: underframe, open bay, canopy.
//
// Named parts (for review): Underframe (Bogey, Frame casings, the Goods chest at the rear), Deck (Fore controls, the
// conductor's Seat, Aft controls), Posts (Fore post on the fore controls, Aft post on the chest), Canopy (four slabs),
// Cab lamp (hanging under the canopy beside the driver's head).
//
// Side elevation (x along, y up; z = 0):
//   y3   r   r   r   r        r canopy (bottom spruce slabs, one line over the whole car)
//   y2   |   .   L   |        | posts (spruce fence)   . the driver's head (open)   L cab lamp (hanging)
//   y1   >   S   <   |        > < controls facing the seat   S the conductor's seat (on the deck)
//   y0   B   K   K   C        B bogey   K frame (railway casing)   C the goods chest (only a fence over it: the lid opens)
// The glue box has one empty cell: the driver's head (1,2). Everything touches the bogey through the frame.
// The walking line: none (the brief: nobody walks on; the conductor is sat down by code). A player steps up from the
// platform onto the seat from the side, under the canopy between the fore post and the lamp.

export const SIZE = [4, 4, 1] // along, up, across

// Palette 60/30/10: spruce (posts, canopy) / Create's dark railway iron (bogey, frame, controls, seat) / the warm
// lantern and the chest's brass. Why: spruce is the village's own timber, the dark iron is what Create trains are made
// of, and one warm lamp lights the driver at night. Every block is in the test barrels' stock, all of it used.
export const PALETTE = {
  bogey: 'create:small_bogey',
  casing: 'create:railway_casing',
  controls: 'create:controls',
  seat: 'create:brown_seat',
  chest: 'chest',
  post: 'spruce_fence',
  roofSlab: 'spruce_slab',
  lamp: 'lantern',
}

export const MASSES = {
  underframe: { bogey: [0, 0, 0], frame: [[1, 0, 0], [2, 0, 0]], chest: [3, 0, 0] },
  deck: { seat: [1, 1, 0] }, // controls at (0,1,0) facing east and (2,1,0) facing west
  posts: { fore: { x: 0, y0: 2, y1: 2 }, aft: { x: 3, y0: 1, y1: 2 } },
  canopy: { x0: 0, x1: 3, y: 3, type: 'bottom' },
  lamp: [2, 2, 0],
}

export const ROOMS = [{ name: 'Cab', use: 'the conductor drives from here, both ways', box: [0, 1, 0, 3, 2, 0] }]

export const OPENINGS = {
  head: [1, 2, 0], // open over the seat for the whole ride
  sides: 'the cab is open on both sides (z - 1 and z + 1) between the fore post and the lamp: a player sits in from either platform',
}

export const FURNITURE = [] // none (the brief); the seat is Create's, placed in the Cab pass

// What the test barrels hold (the brief's stock) and what this car uses of it.
export const STOCK = {
  'create:railway_casing': 3, 'create:controls': 2, 'create:brown_seat': 1, 'minecraft:chest': 1,
  'minecraft:spruce_fence': 3, 'minecraft:spruce_slab': 4, 'minecraft:lantern': 1,
}
// The bill (recipe.mjs checks it on every build): exactly the stock, so the three barrels (CreateTrainCompatTests.builder,
// QaTrainSiteRaceCompatTests.builder, JobScenes village_train) stay as they are.
export const BILL = { ...STOCK }
