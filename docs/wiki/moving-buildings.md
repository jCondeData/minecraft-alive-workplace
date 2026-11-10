# Moving a finished building

The owner's Milestone 45: a building the Village Hall knows can be moved within its village by a new Mover job, who
takes it down top-down and puts it up again at the new spot with nothing lost. Being built in order: the snapshot (45.1), the Hall's Move page that plans a move (45.2) and the Mover who carries it
out (45.3).

Roadmap items: 45.1, 45.2, 45.3

## What a player sees

**The Hall's Move page (45.2).** A **Move** tab (a piston) in the Village Hall, under Buildings, lists the village's
finished buildings, each with where it is and, once planned, where it's going. A click opens the picker:

- the building, with its size and the way its front faces;
- a map of the ground round the new spot, one cell every 4 blocks, north at the top: the new spot (green when it fits,
  red when it's refused, yellow before a spot is picked), where it stands now, other buildings (bricks), roads, water,
  the hall and the land outside the village; a click on a cell puts the spot's middle there;
- Rotate (a quarter turn a click), the fit line, Confirm and Cancel, and Call off once a move is planned.

While choosing, a ghost outline of the new spot glows in the world for the player (green or red, its front edge gold).
The fit line says plainly why a spot is refused: it would overlap another building (named) or the hall, sit on a road,
be partly outside the village, the ground rises more than 3 blocks across it, or there's water on it. Confirm also needs
a hired Mover in the village (45.3); without one the page says "Needs a hired Mover" and nothing is saved.

![The Move page](https://jcondedata.github.io/minecraft-alive-workplace/move_plan/still-2.jpg)

**The Mover (45.3).** A villager without a job takes a free **Mover's Crate** by themselves, as a builder takes a
Blueprint Table. Once a move is confirmed, the village's Mover walks over and takes the building down from the top, one
block at a time, into his crates: chests and barrels packed with their items (each its own), frames, armor stands and
furniture too, nothing dropped. Then he puts it up at the new spot from the ground up (foundation, structure,
decoration), turned as picked, and the old spot is left cleared. Two Movers share a move. He packs into the crates
at his Mover's Crate: with his crate broken he can't take anything more down, so he waits, his line saying "Waiting for
his Mover's Crate: nowhere to pack the building", and carries on once he has one again (putting it up needs none). A
Mover's Crate inside the building being moved stays where it stands (it's where he packs), so the building goes up at
its new spot without it and the Mover keeps his workstation; move the crate yourself afterward if you like. Called off on the Move page,
even half way, what went up at the new spot comes down again and everything goes back up where it was. Whoever
confirmed the move is told when it's over.

![The Mover half way](https://jcondedata.github.io/minecraft-alive-workplace/mover/still-2.jpg)

## How it works

**A building's snapshot (45.1).** `move/MovePlan` captures a building as a moving plan:

- every block in its box with its full state, and each block entity's data (a chest's items in their slots, a sign's
  text, a banner's patterns, a modded block's data), all by id, never by another mod's classes;
- its entities: armor stands, item frames, paintings and any other non-living thing standing in it (modded furniture
  entities too), saved by id without their UUID;
- its front, its bed heads, and its residents: villagers whose home or job site lies in the box, with those beds and
  workstations.

A building built from a blueprint or grown by the village is captured from its Finished record (its blueprint's size,
outline and turn give the box and the front); `captureAt(hall, pos)` finds the building at a spot. Any box with a
front can be captured too, for a building without a blueprint. An empty box, a record whose blueprint is gone or a
spot no building covers gives no plan.

A plan can be turned a quarter, a half or three quarters: stairs, doors and beds (both halves), chests, furnaces and
other facing blocks, standing signs and hanging frames all turn with it, and the lowest corner stays the lowest corner.
It saves as NBT with a version (a plan from a newer version, or one with no version or size, loads as none; a block whose
state is missing from the plan's palette is left out), so a move can be saved mid-way.

**The fit check (45.2).** `move/MoveFit.check` takes the spot's middle and the turned size: the footprint's four corners
must be inside the village (`VillageHalls.contains`), its footprint clear of the hall and of every other finished
building or live build site (by their blueprint boxes); then each column's ground (the highest block that
stops a walker or holds water, leaves and barriers left out) is read: paving (`RoadStyles.isPaving`) refuses as a road,
water as under water, and a rise of more than `MAX_STEP` (3) from the lowest column to the highest as too steep. The
building's own old spot counts as flat ground at its floor, so it can shift a few blocks or turn where it stands. The new
lowest corner sits on the middle (median) column's ground.

**The Move page (45.2).** `hall/MovePage` is one of the hall's spare pages (`HallPages.SPARES` is now 6), placed under
Buildings. What a player is picking (the building, its plan, the spot and the turn) is kept in memory while the screen is
open and forgotten when it closes or on Cancel. The client draws the picker's map as a grid of framed cells
(`VillageHallMenuScreen`, by the cells' `MAP_MARK`). The ghost is `BlueprintOutline.glowMove`. A Mover is a villager whose
profession id is `aliveworkplace:mover` (`move/Movers`; 45.3 registers the job under that id).

**The Mover's site (45.3).** `move/MoveSites` (saved) holds a `move/MoveSite` per building on the move: the plan
captured when the Mover starts (`MoveSites.begin`, from the waiting `PendingMoves` entry and the hall's Finished record),
the old and new lowest corners, the turn, and where each block is now: standing at the old spot, packed (in the
Mover's crates: the site's record, with each block entity's data as it was taken down), standing at the new spot, or
lost (someone broke it before he got to it). The stages are the Builder's: DISMANTLE (top-down, what hangs on others
first), FOUNDATION (the plan's bottom layer), STRUCTURE (solid blocks), DECORATION (the rest, then the things) and DONE;
a canceled move runs RESTORE_DOWN and RESTORE_UP. Each step looks at the world before it acts, so a step is never done
twice after a restart; blocks come down and go up without neighbor updates (a door's halves, a bed and a wall torch
stay put) and a container is emptied into its record before it comes down, so nothing spills. `move/MoverWork` is the
Mover's WORK behavior: the site of the village round his crate (`MoveSites.work`; two Movers get the same site), a
step every 4 ticks from within 20 blocks of the box worked on. A step that packs (DISMANTLE, RESTORE_DOWN) needs his
job site to be a Mover's Crate standing now (`MoverWork.hasCrate`); without one the step isn't taken and the site's
`waiting` (not saved, set again each swing) puts `NO_CRATE` on his line. A Mover's Crate in the plan
(`MoveSite.stays`) is marked `STAYS` (4 in the saved `where` array) when DISMANTLE reaches it and is never packed, put
up or counted in `total()` (B1120: packing his own crate left him waiting forever). A site saved before that fix with
the crate already packed puts it back where it stood on the next swing (`MoveSite.crateToPutBack`, which also lets that swing
through without a crate). When a move ends, the hall's Finished record follows the
building (its placement's origin and turn), and the waiting move is cleared. The Move page's Call off (the
`PendingMoves` entry gone) cancels a started site.

## Items, blocks, jobs, commands

The Village Hall's Move tab (45.2). The Mover job and the **Mover's Crate** (`aliveworkplace:movers_crate`, its
workstation: planks round a barrel, a lead on top, two iron ingots under it; in the creative tab) (45.3).

## Switches

`movers` (on, 45.3): off, a jobless villager doesn't take a Mover's Crate (`WorkerLimits.hideFull`), Movers already
hired do nothing, and confirmed moves wait.

## Saved data

A move under way is saved per dimension in `aliveworkplace_move_sites` (`move/MoveSites`): each site's plan, corners,
turn, stage, where each block is, packed block entity data, and whether it was canceled. A world from before 45.3 loads
with none; a site that doesn't read is skipped.

A confirmed move is saved per dimension in `aliveworkplace_pending_moves` (`move/PendingMoves`): the hall, the building's
origin and blueprint, its new lowest corner, its turn and who confirmed it; one per building. A world from before 45.2
loads with none, and an entry that doesn't read is skipped, never the rest. A plan's NBT (`MovePlan.save`, `version` 1: a block-state palette, blocks, things, beds, workstations, residents) is what the Mover's site will keep mid-move.

## Decisions

- The Mover's crates are the site's saved record, not items in a chest: a block's full state (a door's halves, a bed's
  facing, a chest's items) can't be kept by an item. So the village Storehouse is never used and can't be full; the
  Done when's "full Storehouse" wait is a question for the owner (ROADMAP Notes). Under this decision "he can't store
  it, so he waits and says why" is a Mover whose Mover's Crate is gone.
- A block someone breaks before the Mover gets to it isn't put up again: whoever broke it has it.
- A Mover's Crate inside the building stays where it stands rather than moving last or refusing the move (B1120): moved,
  it would leave its Mover without a workstation for the whole rebuild (vanilla can take his job away), and a refusal
  would make the player break the crate, which loses the job the same way.
- Residents keep their own beds and workstations by vanilla's rules: a bed taken down is let go, and they claim the
  beds put up at the new spot like any free bed.

- The picker's map is a grid in the hall's screen, not the vanilla map item the hall draws: a map item can't be clicked.
- A building may turn in place or shift onto part of its own old spot: the Mover takes it down first.
- Until the Mover job exists (45.3), Confirm is always refused with "Needs a hired Mover".

- Plans only turn, never mirror: a move keeps the building's handedness.
- Entities are saved without their UUID, so a rebuilt frame or stand is a new entity, never a duplicate of one still standing.

## Proof

`MovePageGameTests` (10), all through the hall's clicks: a map click picks the spot (and the ghost follows, green); a
spot over another building, on a road, outside the village, too steep and under water each refused with its line;
Rotate a quarter a click; Cancel forgets the pick and the ghost; Confirm without a Mover refused, nothing saved; with a
Mover the pending move saved, through a save and reload, an old save and a broken entry, marked on the list, reopened on
its spot and called off.

`MovePlanGameTests` (4): a captured house rebuilt 14 blocks away matches block for block (`MaterialRules.matches`) with
its chest, sign, banner, frame and armor stand; the same at 90, 180 and 270 degrees; a building the hall knows
captured with its front and resident; empty and unknown buildings give no plan.

`MoverGameTests` (10): the job (its id, the crate as its workstation, the recipe, the text); a whole move (taken down
top-down, the chest and barrel packed apart, put up turned a quarter block for block with each container's own items,
the item frame, nothing dropped, the hall's record following it); saved and reloaded in every stage; a wall block broken
half way (whoever broke it has it, not put up again, nothing doubled); called off half way through putting it up
(everything back, the new spot empty); two Movers sharing one site; his crate broken half way down (he waits, says why,
and finishes once it's back); his own crate inside the house (it stays standing, the house moves without it and the move
ends), also on a save from before the fix with the crate already packed (put back, the move ends); config `movers` off (he does nothing, the house stands, the move waits, a jobless villager
doesn't see the crate).

`QaBuildingSnapshot1109bGameTests` (4, the QA lane): a 7 × 9 room written to disk and read back, then rebuilt unturned
and turned 90, 180 and 270 degrees, matches exactly, block for block: a double chest (still one, 54 slots), a named
chest, an empty chest (still empty), a barrel, a shulker box and a furnace with all three slots, slot for slot; a sign's
front and back text and wax, a wall sign, a wall banner's patterns in order, a lectern's book; an open right-hinged
door, a top trapdoor, upside-down stairs, a sideways log, a bed, a lever and a wall torch, all turned; an item frame
(its item and its turn), a two-wide painting and an armor stand in full gear, turned and looking the turned way, as
new entities beside the originals. Capturing twice changes nothing in the room (every block, its data and every
entity) and gives the same plan. Only frames, paintings and stands are its things (not a cow, a dropped item, a
minecart or a visitor), and its residents are the villagers whose bed or workstation is in it, wherever they stand.

## Known limits

- The pick (spot and turn) is not saved: closing the hall's screen forgets it. Only a confirmed move is.

- A player's own house has no record the hall knows (nothing registers one yet), so it is captured only as a box.
- `place()` puts everything up at once; the Mover's step-by-step rebuild (45.3) must empty the original containers so nothing is doubled.
