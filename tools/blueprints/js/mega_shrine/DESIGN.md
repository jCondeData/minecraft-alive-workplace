# Mega Shrine: design notes (blind rebuild, Milestone 52)

Brief: `tools/blueprints/briefs/mega_shrine.md`. Plot exactly 7 x 6 x 7; the pedestal at (3, 3, 3) on the chiseled
meteorite block at (3, 2, 3); the trainer's stand at (3, 3, 2), with (3, 2, 2) solid and a walkable way up from the
front; the crowd 4 blocks out in front, at (3, 1, -4).

## Three rough concepts

**A. The Keystone Arch.** A stepped dais of deepslate bricks with a round-headed arch standing across its middle, the
whole width of the plot and the whole height (y 0-5); the arch's crown is a chiseled meteorite *keystone*, and the
pedestal stands under it, framed by it, on a terrace of meteorite bricks. A straight, 3-wide flight climbs the dais's front
from the ground to the trainer's stand; twin flights climb the back to a landing behind the pedestal.
Two soul lanterns hang from the crown either side of the pedestal over two amethyst clusters. Roof: none; the arch is
the hero. Plan: layers front to back (forecourt, stair, terrace, arch and pedestal, terrace, stair, back court).
Story: the Key Stone is what wakes Mega Evolution, so the village set the meteorite's own chiseled stone as the
keystone of an arch and holds the show under it; the trainer climbs the steps and stands framed in the arch for the
whole square to see.

**B. The Fallen Star.** A meteorite half sunk in a crater of cracked deepslate, its top split open like a geode
(calcite, amethyst, clusters), the pedestal in the split, steps cut into the rock's face. Roof: none. Plan: a round
crater rim, a lumpy boulder in the middle. Story: the shrine *is* where the star fell. Characterful, but in 7 x 7 a
lumpy boulder reads as a pile from 20 blocks, the renderer can't draw the meteorite blocks the mass would be made of,
and the trainer would stand on a rock with no frame round him.

**C. The Canopy Altar.** A square stepped dais, four corner columns of deepslate brick wall, a flat canopy at y 5
(a baldachin) with soul lanterns hanging at its corners and amethyst on its cornice. Roof: a flat canopy. Plan: a
square in a square. Story: a covered altar. Safe and tidy, but it is every shrine, the canopy sits one block over the
trainer's head and squashes the show, and from the crowd the columns cut the stage into slots.

## Strongest: A

The arch is a shape you'd know in outline from across the square (a round head on two piers over a stepped triangle),
it frames exactly the two cells the show happens in (the trainer's stand and the pedestal), and it uses the full
6-block height without putting a roof over the Mega Evolution's light. It gives every side something: the front a
straight flight up the middle between lit cheeks, the back a stepped gable of twin flights (the shrine is climbed from
both sides, so it has no blind back), the sides the two-deep arch's piers braced by stepped buttresses. The keystone is the story in one block.

## Named parts

| Part | Where | What |
|---|---|---|
| Forecourt | z 0, y 0 | the Altar Stair's foot, the cheeks' plinth steps (stairs) at x 1 and x 5, tile slabs at the corners |
| Back Court | z 6, y 0 | tile slabs; two bollards (a wall post, a soul lantern on it) at the back flights' feet, x 1 and x 5 |
| Altar Stair | x 2-4, z 0-2 | a straight flight, one stair a step (y 0, 1, 2); its top step (3, 2, 2) is the stand's footing |
| Cheeks | x 1 and x 5, z 1 | two-high cheek blocks with a wall post and a soul lantern on top, a plinth step proud of each, a low stair at the corners (x 0, x 6): the front steps down in shadow lines |
| Back Stair | z 5 | twin flights from x 0 and x 6 at y 0 up to the landing (3, 2, 5); its face a stepped gable with a chiseled deepslate panel in the middle |
| Terrace | x 2-4, z 1-4, y 2 | meteorite brick; the stand (3, 2, 2) in front of the pedestal; the ring's floor (x 1 and x 5 at z 3) too |
| Plinth | the dais under it all, x 1-5, z 1-5, y 0-1 | deepslate bricks with cracked ones and tiles mixed in |
| Keystone Arch | z 3 and z 4, x 0-6, y 0-5 | two rings deep: piers at x 0 and x 6, a stair-rounded head, the chiseled meteorite keystone at (3, 5, 3) in front, a chiseled deepslate one behind |
| Pedestal | (3, 3, 3) on the chiseled meteorite (3, 2, 3) | Mega Showdown's pedestal |
| Crystals | (2, 3, 3), (4, 3, 3); the four geode beds on the plinth's shoulders | amethyst clusters on amethyst blocks |
| Lights | hung from the crown at (2, 4, 3) and (4, 4, 3); on the cheek posts and the two back bollards | soul lanterns, none standing on a floor |
| Buttresses | x 0 and x 6 at z 2 | a block and a stair leaning on each pier's front |

Palette (60/30/10): deepslate bricks, tiles and their stairs and walls (plinth, arch, stair) / Mega Showdown's
meteorite bricks and chiseled block (terrace, ring floor, keystone, under the pedestal) / amethyst, soul
lanterns (the crystals and the cold blue light the show happens in).

Walking line: crowd (3, 0, -4) -> straight up the Altar Stair (stairs (3, 0, 0), (3, 1, 1), (3, 2, 2)) -> the stand
(3, 3, 2), facing the pedestal.

## Round 1 (critic: BACK) and what changed
1. The front had no way up its middle (a 1.5 riser): the Altar Stair is now a straight 3-wide flight to the stand. Its
   top stair is the stand's footing, as the critic proposed (a stair is solid underfoot; nothing in `MegaShrines`
   reads that block).
2. The arch was a one-block hoop from the sides: a second ring at z 4 makes it two deep (z 2 stays free for the trainer).
3. The front of the dais was a flat two-high wall: cheeks with lantern posts, plinth steps proud of them and low corner
   stairs step it down.
