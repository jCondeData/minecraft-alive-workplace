# Moving a finished building

The owner's Milestone 45: a building the Village Hall knows can be moved within its village by a new Mover job, who
takes it down top-down and puts it up again at the new spot with nothing lost. Being built in order: the snapshot (45.1), the Hall's Move page that plans a move (45.2), the Mover who carries it
out (45.3), its residents moving with it (45.4), its belongings arriving as they were (45.5) and the rules that keep
it safe on a live server (45.6). Player guide: `docs/guide/moving-buildings.md`.

Roadmap items: 45.1, 45.2, 45.2a, 45.3, 45.4, 45.5, 45.6, B1154, B1161, B1163, B1172, B1174, B1182

## What a player sees

**The Hall's Move page (45.2).** A **Move** tab (a piston) in the Village Hall, under Buildings, lists the village's
finished buildings, each with where it stands from the Hall ("Now: 17 blocks north-west of the Hall") and, once
planned, where it's going. Since B1163/45.2a the page is a three-step flow a first-time player can follow without this
wiki: three steps across the top (**1. Pick a building**, **2. Pick a new spot**, **3. Confirm**), the current one in
gold, and the step's instruction under them ("Step 1 of 3: pick the building to move...", "Step 2 of 3: click the map
where the Starter Cottage should go", "Step 3 of 3: the spot fits. Press Confirm and a Mover moves the Starter
Cottage"). A click on a building opens the picker:

- left, the village map: the land drawn like a vanilla map round the Hall (its bell), roads in light tan, the land
  outside the village darker, every other building as a named brick box, where the building stands now in yellow
  ("Now") and the new spot in green when it fits or red when it's refused ("New spot"), its front edge in gold; a
  legend and "North is up" under it. It starts fitted to what the village has (the Hall, its buildings, now and the new
  spot) so the buildings read; the mouse wheel zooms out to the whole village and back. Names sit inside their box when
  they fit, else beside it on a dark backing, never on another name, box or the Hall's bell ("Hall"). A click anywhere on the map puts the new spot's middle on that
  block; the mouse over it says what is there (a building's name, a road, water, the Hall, outside the village);
- right: "Moving: Starter Cottage", where it is now and where it would go (both from the Hall), the reason in red
  when the spot is refused, labeled buttons (**Rotate (front faces north)**, **Confirm the move**, gray until it can be
  done, **Cancel**, and **Call off the planned move** once one is planned), then "What happens": what the Mover does,
  about how long ("About 3 minutes of a Mover's work"), and whether a Mover works here or how to hire one.

While choosing, a ghost outline of the new spot glows in the world for the player (green or red, its front edge gold).
The fit line says plainly why a spot is refused: it would overlap another building (named) or the hall, sit on a road,
be partly outside the village, the ground rises more than 3 blocks across it, there's water on it, or a Mover's Crate
stands in it (B1129: a crate never moves with a building, so going up there would break it). Another building's new
spot counts as a building once its move is planned or under way (B1174, `MoveFit.moving`), so two moves never target
the same ground. Confirm also needs
a hired Mover in the village (45.3); without one the page says "Needs a hired Mover" and nothing is saved.

**What can't be moved (45.6).** Clicking a building's Move button (and Confirm, asked again) goes through
`MoveRules.refusal` first; a refused building opens no picker, says why on the action bar in red and the list shows
again. In order: config `moveBuildings` off ("Moving buildings is switched off on this server"); not one of this hall's
finished buildings ("that building isn't part of this village"); its footprint reaches into a protected village that
keeps the player out (the mod's own claims, `VillageProtection.keeper`: operators and the owner's friends are let in;
the line is the protection's own "X is Y's village..."); its footprint holds the Village Hall, this village's or any
other's (B1161: two villages side by side; `MoveRules.holdsHall` reads the loaded chunks' block entities and, for a
chunk not loaded, the halls' points of interest) ("the Village Hall stays where it stands"); a Wonder or a Wonder's part ("a Wonder is built to stay where it stands"); a build site not done at
its origin or overlapping it, such as an upgrade or repair ("builders are still working on it; move it once they're
done"). A move confirmed before the builders started on it waits (B1182): `MoveSites.begin` asks the same
`MoveRules.underBuilders` and starts nothing while such a site is live; the move stays saved, the building's row on the
Move page says "Waiting: builders are working on it; the Mover starts once they're done", and the Mover starts it once
the site is done. With `moveBuildings` off a move already confirmed still goes ahead (stopping one half way would leave a
building in pieces); `movers` off is the switch that stops the Movers.

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
even half way, what went up at the new spot comes down again and everything goes back up where it was: the building's
row opens on the planned spot at every stage of the move, with Call off, even once nothing is left at the old spot. Once
the Mover has started, Confirm is gray and a new spot is refused ("The Mover is already moving it: call the move off
first to pick another spot"); call it off, then pick again. Whoever confirmed the move is told when it's over.

![The Mover half way](https://jcondedata.github.io/minecraft-alive-workplace/mover/still-2.jpg)

**The residents move too (45.4).** Whoever sleeps or works in the building keeps it. While it's away, those with a bed
in it sleep in a free bed at the village's inn (round an Innkeeper's Shop Counter or an old Inn Counter), or else the free bed nearest the Village Hall;
with none free they have no bed until theirs is back. A worker keeps their job while their workstation is packed. The
moment the Mover puts a bed or a workstation up at the new spot it's theirs again: nobody else can take it, the bed at
the inn is given back, and the Hall's People page, the census, their home (the moved building), the job-status cards
and couples all show it straight away. A Builder's bench inside a moved house stays his, and his builds keep taking
from its chests. A move called off gives everyone their old bed and block back the same way. Someone who lives there
but is far off when the move starts (out trading, or in a part of the world nobody is near) isn't forgotten: their bed
and workstation are held for them too, and they get the moved ones when they're back, or the inn meanwhile if they
come back before the house is up. A bed nobody comes back for within five days is let go for someone else, and so is
one held for a resident who dies (B1201). A bed or job you give a resident at the Village Hall's People page while
their building is away stands: the Mover doesn't pull them back to the moved bed or workstation, and that one is free
for someone else (B1194).

![Tomas at his barrel in the moved cottage](https://jcondedata.github.io/minecraft-alive-workplace/mover/still-3.jpg)

**Its belongings arrive as they were (45.5).** Every chest, double chest, barrel, shulker box and modded store
(Sophisticated Storage chests and barrels, CobbleFurnies' cabinets, Handcrafted's cupboards and shelves) comes out at
the new spot with exactly the items it held, slot for slot, nothing dropped and nothing doubled; signs keep their words
on both sides, banners their patterns; item frames (one hanging on the building's outer wall too), paintings and armor
stands go up with what they held when the Mover packed them, so an item a player took off after confirming the move
isn't put back and one put in a chest meanwhile moves too. Furniture from CobbleFurnies, Handcrafted and Lucky's Cozy
Home moves like any block. A piece of a furniture mod removed from the game mid-move is left out; the rest goes up.
In the showcase, Tomas's barrel arrives with his 16 cod and 5 salmon and the cottage's sign still reads "Dara & Tomas".
With Create installed, a Mechanical Crafter, Weighted Ejector, vault or depot arrives with its items once (so does a
Deployer's hand, with its filter, and the item on a Placard), and a
belt of any length comes across whole, with the items on it, and runs at the new spot. A Fluid Tank or Item Vault
built from several blocks arrives as one tank or vault with all its fluid or items, and a canceled move puts it back
up as one, reachable through any of its blocks (B1135, B1136). A Mechanical Arm turned with the building still takes
from and puts on the same depots and belts: its targets turn with it, as on Create's own contraptions, and turn back
when a canceled move puts it back, also across a restart. A Display Link keeps pointing at the block it shows on. A
builder building a blueprint turned, mirrored, or both turns them the same way.

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
open and forgotten when it closes or on Cancel. The picker's buttons are still the menu's slots (Back, the header, Call
off, Rotate, the fit line, Confirm, Cancel and the 9x4 grid of map cells, which tests press); since B1163 the client
draws its own screen from them (`VillageHallMenuScreen`, spotted by the cells' `MAP_MARK`): the guide (`MovePage.GUIDE`:
the step's instruction and its lines, the step in its `STEP_MARK`) and the village map (`MovePage.VIEW`: the land's map
colors from `VillageMaps.colors`, a road/water mask, the Hall, the village radius, the other buildings' boxes with their
names as its lines, now, the new spot and its front, in its `VIEW_MARK`). The map is taken once when the picker opens
(`MovePage.MapView`, 128 pixels a side, a block a pixel up to a 64-block village radius) and a click on it is a menu
button, `MovePage.MAP_CLICK` plus the pixel, handled through `HallSections`. The list's rows carry `LIST_MARK`, which
shows the step 1 note. The time is `MovePage.minutes`: each block down and up again at the Mover's pace plus half a
minute's walking, rounded up. The ghost is `BlueprintOutline.glowMove`. A Mover is a villager whose
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
`PendingMoves` entry gone) cancels a started site. While a site is under way (`MovePage.underWay`: started, not
canceled, not done), the picker takes the site's own plan (the building as the Mover captured it) instead of capturing the
old spot, which may be empty by then (B1181), and Confirm is refused, so the saved move and the site never part (B1192).

**The residents (45.4).** `move/MoveResidents`. As a site starts (`MoveSites.begin`), every loaded villager within 128
blocks whose home (`MemoryModuleType.HOME`, a bed's head) or job site (`JOB_SITE`) is in the building's box is noted on
the site as a `Resident`: their bed and workstation relative to the box's lowest corner. A worker with no experience
gets 1 (as `Jobs.employ` does), so vanilla's profession reset doesn't take the job while the block is packed. One with a
bed moves into a stay bed (`MoveResidents.stayBed`: the nearest free bed round each inn counter in the village (`Innkeepers.counters`: an Inn Counter, or a Shop Counter an Innkeeper works, never a shop's; B1177), nearest the hall
first, else the free bed nearest the hall, never a wagon bed or one in the old or new box) through `HallBeds.moveIn`,
which lets their own bed go. When `MoveSite.put` puts a block up (at the new spot, or back at the old one on a cancel),
`MoveResidents.placed` runs: a Builder's build whose bench was that block's other position follows it
(`BuildSite.setBench`); a resident's bed or workstation is reserved (its point of interest filed at once if the level
hasn't yet, as `Builders.employ` does, B132, and its one ticket taken) and, if the resident is loaded, settled: home
memory set (the stay bed released with vanilla's `Villager.releasePoi`), job site set (any other site they took meanwhile released
with `Stations.releaseOld`), `JobSiteTickets.hold`. `MoveSite.take` clears a resident's reservation when their block
comes down again. At the end (`MoveSites.finish`), `arrive` reserves anything still standing where the building is
now that the Mover didn't put up (a move called off before it came down) and settles everyone loaded. Everything else
reads those memories: `VillageNeeds.bed` (the People page and census), `Homes` (by the Finished record that follows
the building), `Builders.benchPos` (the bench, the job-status cards); couples and households are kept by UUID.

A resident not loaded when their block goes up is settled by the villager tick (`mixin/VillagerMixin` →
`MoveResidents.tick`, once a second; a stay bed is looked for again every 10 seconds while they have none). One whose
move is over is kept in `MoveSites`' homecomings for `KEEP_TICKS` (5 days); homecomings are swept with the forwards,
and one not back by then has what was held for them released (`MoveResidents.giveUp`, B1201). A resident who dies or is
turned into something else (`BuilderEvents.workLeft` → `MoveSites.onGone`) is dropped from the site and the homecomings
at once, their held blocks released. A bed picked at the Hall (`HallBeds.click`, a move-in or a swap) or a job changed
or taken away (`Retraining.retrain`, `Retraining.unemploy`) calls `MoveSites.chosen` (B1194): the resident's moved bed
or workstation is no longer theirs (its relative spot cleared, so `placed` doesn't reserve it and `settle` doesn't set
it), and one held already is released.

Residents too far off to be noted (over 128 blocks, or not loaded; B1154): as the site starts, every occupied bed
(`PoiTypes.HOME`) or workstation (`VillagerProfession.ALL_ACQUIRABLE_JOBS`) point of interest in the box that no noted
resident has becomes a `MoveResidents.Claim` on the site (saved as `claims`). `placed` reserves a claim's block as it goes
up, `taken` clears it. A villager seen during the move whose home or job site memory is in the old box is noted then
(`MoveResidents.late`): the claim becomes theirs, and they get a stay bed. When the move is over, the claims standing
again wait in `MoveSites`' `forwards` (saved) with their old spot; a villager whose memory is that old spot (and no bed
or workstation stands there again) gets the moved block (`MoveResidents.forward`). Forwards are swept once a minute:
one whose block was broken is dropped, one older than `KEEP_TICKS` has its ticket released, and at most
`MoveSites.MAX_FORWARDS` (256) are kept, oldest let go first. A world from before the fix has neither list.

**Belongings (45.5).** `MoveSite.take` saves the block entity as it is now (`BlockEntity.saveWithoutMetadata`, by id: a modded
store's own data), empties it with `Clearable.tryClear`, and after the block is set to air discards any item that
appeared in and round its cell during that call: a store that isn't a vanilla `Container` (Sophisticated Storage) spills
its contents when it's removed even though they're in its saved data, which doubled them (the compat test fails
without this). `put` loads the saved data into the new block entity. The things are packed when DISMANTLE starts
(`MoveSite.packThings`): `MovePlan.thingsIn` finds every thing in the box plus any hanging entity whose supporting block is in
it (a frame on an outer wall, whose position is just outside the box), each saved as it is then (`MovePlan.thing`) in
the site's packed things and discarded; `MoveSite.placeThings` puts those up at the end of DECORATION (or back on a cancel),
not the plan's older picture. A plan block whose state reads as air (its mod's id is unknown since the save) is marked
lost by `take` or `put` and counted in `skipped`. A block laid whole with its neighbors (`MaterialRules.laidWhole`, Create's belt)
moves as one piece (B1128): taking one belt segment down makes Create remove the whole belt, so `take` packs every segment
of that block still standing (the belt's items are in its controller's data) before any comes down, and `put` sets
every packed segment before loading any data, moving each segment's `Controller` (a world position) to where that
block now stands (`MoveSite.withController`), as `MechanicWork.beltData` does for builds. Create's crafter and ejector
aren't `Clearable` and spill as they come down; the discard above takes those items back (B1127). A container Create joins from
touching blocks of one kind (`MaterialRules.joined`: Fluid Tank, Creative Fluid Tank, Item Vault) is packed whole too
(B1135, B1136): `MoveSite.whole` gathers every touching block of that kind still standing and `take` packs each one's
data before any comes down, since taking one makes Create split the rest (a tank's other block re-shaped and its share
of the fluid lost, a vault's other block packed as a vault of its own). Its blocks still go up one by one with that
data: put back where it stood (a cancel), each block's `LastKnownPos` matches and it is the old container again; at the
new spot Create sees the position changed, frees each block and joins them again, gathering the contents on the new
controller. `take` counts a joined block as still there whatever shape properties its neighbors gave it. Some block
entities keep positions relative to themselves in their data (a Create arm's `InteractionPoints`): after `put` loads the
data, `build/TurnedBlockEntities` turns it with the building (B1137), and back again for data a canceled move packed at
the new spot (the packed entry's `turned` flag); `MovePlan.place` and the builder's turned blueprints use it too.
`compat/create` fills it with Create's `StructureTransform.apply`, which runs each block entity's own transform; without
it the data goes in as saved, which is right for vanilla blocks. Capture uses `MovePlan.thingsIn` too, so the plan includes outer-wall frames.

## Items, blocks, jobs, commands

The Village Hall's Move tab (45.2). The Mover job and the **Mover's Crate** (`aliveworkplace:movers_crate`, its
workstation: planks round a barrel, a lead on top, two iron ingots under it; in the creative tab) (45.3).

## Switches

`movers` (on, 45.3): off, a jobless villager doesn't take a Mover's Crate (`WorkerLimits.hideFull`), Movers already
hired do nothing, and confirmed moves wait.

`moveBuildings` (on, 45.6, `MoveRules.ENABLED`): off, the Move page refuses every building, at its Move button and at
Confirm (a building picked before the switch went off too); a move already confirmed still goes ahead. Back on, Confirm
saves the same pick.

## Saved data

A move under way is saved per dimension in `aliveworkplace_move_sites` (`move/MoveSites`): each site's plan, corners,
turn, stage, where each block is, packed block entity data, and whether it was canceled. A world from before 45.3 loads
with none; a site that doesn't read is skipped. Since 45.4 each site also saves its `residents` (each villager's UUID,
bed and workstation relative to the building, their stay bed, where their bed and workstation are held for them and
whether they're settled), and `MoveSites` saves its `homecomings` (residents of moves already over, with the game time
they're given up after). A site or world saved before 45.4 loads with no residents and no homecomings. Since 45.5 a site
saves `packed_things` once its things are packed (each thing's relative position and data); a site saved before 45.5
has none and puts up the plan's things as before.

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
- Since it stays, a new spot over a Mover's Crate is refused on the Move page (B1129). A move confirmed before that check
  that still meets one never breaks it: the one block that would stand there is skipped (counted in `skipped`). So is a
  block whose spot gets a crate set down after the move is confirmed. A skipped block that holds items (a chest, a
  barrel) is dropped on top of the crate with everything packed in it, so nothing packed is ever deleted (B1172); a
  waiting step was ruled out because the crate is the Mover's workstation and would never move by itself.
- Residents keep their own beds and workstations (45.4): each is held for them the moment it's put up, never left
  free for a neighbor to claim first. During the move they sleep at the inn, else by the hall (the spec's "the Hall or
  the inn": the Village Hall has no beds of its own, so "the Hall" is the free bed nearest it).
- A resident whose bed was broken by someone before the Mover got to it keeps the bed at the inn.
- Belongings move as they are when packed, not as the plan saw them when the move started (45.5): the plan's picture
  would bring back an item a player has taken off a frame since, doubling it.
- Items a block spills as it's taken down are taken back, since its saved data already holds them (45.5). Contents
  aren't drained through `ItemStores` first: a store whose items live outside its block data would lose them.
- A block or chest a player places inside the box after the move started isn't in the plan, so it isn't moved.

- The picker's map is drawn by the hall's screen from the land's map colors, not the vanilla map item the hall draws: a
  map item can't be clicked. It shows the land as it was when the picker opened.
- A building may turn in place or shift onto part of its own old spot: the Mover takes it down first.
- Until the Mover job exists (45.3), Confirm is always refused with "Needs a hired Mover".

- Plans only turn, never mirror: a move keeps the building's handedness.
- Entities are saved without their UUID, so a rebuilt frame or stand is a new entity, never a duplicate of one still standing.

## Proof

`MovePageGuideGameTests` (13, B1163/45.2a): the list is step 1 and says where each building stands from the Hall;
the picker's guide (step 2, its instruction and every line) and the village map's data (land, road, Hall, where it
stands now); a map click picks the spot on the block clicked (step 3, the new spot from the Hall, the ghost); a map click
on another building, a road, outside the village, steep ground and water each keeps step 2 with its reason; Rotate turns
the front on the map; Cancel forgets the map; Confirm without a Mover refused (with how to hire one), with one saved and
back on step 1; the time grows with the building.

`MovePageGameTests` (15), all through the hall's clicks: a map click picks the spot (and the ghost follows, green); a
spot over another building, on a road, outside the village, too steep, under water and over a Mover's Crate each
refused with its line;
Rotate a quarter a click; Cancel forgets the pick and the ghost; Confirm without a Mover refused, nothing saved; with a
Mover the pending move saved, through a save and reload, an old save and a broken entry, marked on the list, reopened on
its spot and called off. The rules (45.6): with `moveBuildings` off, a live build site on the hut and the hut in
another player's protected village, each a click on its Move button refused (no picker, nothing saved, the reason's
words) and the picker opening once the cause is gone; the Hall's own building, a Wonder and a building outside the
village each refused with its reason, the village's own hut not.

`QaMoveRefusals1110cGameTests` (9, the QA lane, 45.6), through the Move page's buttons: the hut picked and its spot
chosen, then a live site started on it, another player's protection put on its village, `moveBuildings` switched off,
or its record gone: each Confirm refused with its line, nothing saved, every block of the hut and its chest's 7 diamonds
untouched; the site removed or the switch back on, the same Confirm saves. A DONE site on the hut doesn't hold it; a live
build whose footprint shares its wall does, one a block clear doesn't. A building whose box reaches the hall block at its
far corner is refused with the Hall's line, one a block short isn't. The player's own protected village lets them move;
a neighbor's protected village refuses a building whose footprint is 16 from its hall (its radius) and not one at 17.
The village's edge: a building whose middle is 16 from the hall is listed and movable, one at 17 isn't listed and is
refused as not this village's. A Wonder's finished part is refused at its Move button and the list shows again.

`QaMoveRefusals1110cBugTests` (1, B1161): a second village's Village Hall placed inside the hut: its Move button opens no
picker, nothing is saved, the hut and its chest untouched, and the refusal is the Hall's line.

`QaMovePage1111cBugTests` (2, B1174): with one hut's move planned, then with it started, the other hut's spot over
that new spot is refused naming it (step 2, Confirm gray, nothing saved), and a spot clear of it fits.

`QaHallMove1111c2GameTests` (5, the QA lane, B1163): a village with no finished buildings shows one note ("No
buildings to move yet", "Buildings your builders finish in the village are listed here") that opens nothing, the tab
"0 moves planned", the title "Move a building: <village>" and the no-Mover line (with a Mover, "A Mover works here");
exactly 36 buildings fill the list's four rows, 37 give 35 rows and "And 2 more" in the last slot, which opens nothing;
two moves confirmed (Confirm's own action-bar sentence with the spot) count "2 moves planned", Call off on one says
"The Test Hut stays where it is: the move is called off", the list shows again with that row unplanned, the other move
untouched, "1 move planned" and no block changed; a quarter-turned move saved and reloaded opens on its spot still
turned (front east, Rotate's next turn south, step 3, Call off), and Confirm there again keeps the one move unchanged.

`MovePlanGameTests` (4): a captured house rebuilt 14 blocks away matches block for block (`MaterialRules.matches`) with
its chest, sign, banner, frame and armor stand; the same at 90, 180 and 270 degrees; a building the hall knows
captured with its front and resident; empty and unknown buildings give no plan.

`MoverGameTests` (11): the job (its id, the crate as its workstation, the recipe, the text); a whole move (taken down
top-down, the chest and barrel packed apart, put up turned a quarter block for block with each container's own items,
the item frame, nothing dropped, the hall's record following it); saved and reloaded in every stage; a wall block broken
half way (whoever broke it has it, not put up again, nothing doubled); called off half way through putting it up
(everything back, the new spot empty); two Movers sharing one site; his crate broken half way down (he waits, says why,
and finishes once it's back); his own crate inside the house (it stays standing, the house moves without it and the move
ends), also on a save from before the fix with the crate already packed (put back, the move ends); a move confirmed onto
the spot where his crate stands (B1129: the crate isn't broken, only the one plank there is skipped, nothing dropped); `QaMoverCrate1111bGameTests` adds a chest whose spot is his crate (B1172: the chest and its 12 bread and 64 emeralds dropped there, none lost), a turned spot over the crate, a spot one block off it, reloads in every stage, the crate broken going down and going up, the Move page's crate refusals at the spot's edge, and B1154's 128-block line; config `movers` off (he does nothing, the house stands, the move waits, a jobless villager
doesn't see the crate).

`MoverBelongingsGameTests` (4, 45.5): a house with a chest (a named sword in it), a double chest, a barrel, a shulker
box, a two-sided sign, a banner, a frame inside and one on the outer wall, and an armor stand in a helmet, moved a
quarter turn by a Mover: every store slot for slot, the sign's text, both frames (the outer one turned to face east
with its clock), the stand's helmet, nothing loose and every item in the area counted the same before and after; the
frame's diamond and the stand's helmet taken and an apple put in the chest after the move started (the changes move,
not the plan's picture); saved and reloaded half way down (the packed things in the save) and half way up; a saved
move whose fence reads as an unknown Cozy Home chair (the piece left out, `skipped` 1, everything else arrives).
`MoverFurnitureCompatTests` (1, compat suite): CobbleFurnies' chair, table and cabinet, Handcrafted's cupboard, shelf
and side table, and a Sophisticated Storage chest and barrel (100 iron, 70 emeralds), filled through `ItemStores`,
moved through a reload half way down: block for block, each store with exactly its items, nothing loose.
`QaCreateMoverArm1110eCompatTests` (1, compat suite, B1137): a Mechanical Arm taking from a Depot to its west and
putting on one to its north, moved a quarter turn, still aims at both depots.
`QaCreateMoverHeld1111eCompatTests` (4, compat suite, the QA lane): a Deployer holding 16 iron ingots, a Deployer
filtered to an iron pickaxe and holding one, and a Placard showing a diamond on the floor and one hung on the inside of
a wall, each moved a quarter turn: the hand, the filter and the placard's item arrive once, the hung placard on its
turned wall, nothing dropped while the house comes down or loose after.
`QaCreateMoverLinks1111eCompatTests` (1, compat suite, the QA lane): a Redstone Link powered by a redstone block beside
it keeps its two frequency items through the move, sends nothing while packed, and at the new spot breaking and putting
back its redstone block turns its receiver off and on (no transmitter left at the old spot). Right after the move, before
anything next to it changes, it stays dark: B1216 (its failing test is on the tests/create-mover-link-1111e branch).
`QaMoverHomecomingSaves1111eGameTests` (4, the QA lane): a resident unloaded when his bed goes up is still waited for
after a restart, his bed held half the wait later, and loaded back he sleeps in it; one who never comes back has the bed
let go five days on, also after a restart, and another restart doesn't hold it again; a restart mid-move with him
unloaded still gives him the moved bed; a bed given at the Hall after a restart mid-move is kept (B1194).

`QaBuildingSnapshot1109bGameTests` (4, the QA lane): a 7 × 9 room written to disk and read back, then rebuilt unturned
and turned 90, 180 and 270 degrees, matches exactly, block for block: a double chest (still one, 54 slots), a named
chest, an empty chest (still empty), a barrel, a shulker box and a furnace with all three slots, slot for slot; a sign's
front and back text and wax, a wall sign, a wall banner's patterns in order, a lectern's book; an open right-hinged
door, a top trapdoor, upside-down stairs, a sideways log, a bed, a lever and a wall torch, all turned; an item frame
(its item and its turn), a two-wide painting and an armor stand in full gear, turned and looking the turned way, as
new entities beside the originals. Capturing twice changes nothing in the room (every block, its data and every
entity) and gives the same plan. Only frames, paintings and stands are its things (not a cow, a dropped item, a
minecart or a visitor), and its residents are the villagers whose bed or workstation is in it, wherever they stand.

`MoverResidentsGameTests` (5), a house with a married couple's two beds, a Librarian's lectern and a Builder's Bench
moved a quarter turn: the couple sleep at the inn while it's away and then in the new house's beds (remembered, held,
seen by the census, their home the moved house, still one married household, the inn's beds given back); the librarian,
a Novice with no experience, keeps his job with his lectern packed even when his job site is forgotten, and the lectern
is held for him the moment it's put up; the builder's bench is his the moment it's put up, and his queued build's bench
follows it; all of it through a save and reload every 7 steps (the residents and their inn beds survive); and a move
called off half way gives everyone their old bed, lectern and bench back.
`QaInnShopCounter1111cGameTests` (QA lane, B1177) adds the inn's edges for the stay beds: with the inn's Shop Counter
broken, its Innkeeper given No job or retrained as the counter's Shopkeeper, the residents stay by the Hall; with the
Innkeeper asleep they still stay at the inn. `QaInnShopCounter1111cBugTests` (B1203): with the Innkeeper stepped out
past the village's edge his Shop Counter is still the inn, and the residents stay there.

The `mover` scene: Dara (the cottage's bed) and her husband Tomas (a fisherman at its barrel) move with it; its checks
say her bed and his barrel are the moved cottage's and he's still a fisherman, and its third still is Tomas back at his
barrel at the new spot.

`MoveLiveSiteGameTests` (3, B1182): builders start on a hut after its move is confirmed: nothing comes down, the move
is kept and the Move page says it waits; once their site is done the Mover moves the hut whole; through a save and
reload while it waits; and the held move's row still opens with Call off, which drops it. `QaMoveLiveSite1111eBugTests`: QA's reproduction. `QaMoveLiveSiteVerify1111eGameTests` (5, QA's check of
B1182): a site overlapping the hut by one column holds the move and one just beside it doesn't (the blueprint boxes'
edge from both sides); a site at the hut's coordinates in the Nether doesn't; another building's confirmed move goes
ahead, whole, while the hut's waits; and a whole save (build sites, moves and Mover sites) keeps it held until the
reloaded site is done.

## Known limits

- The pick (spot and turn) is not saved: closing the hall's screen forgets it. Only a confirmed move is.

- A resident is noted by name only if loaded within 128 blocks as the move starts; one far away (or not loaded) still
  gets the moved bed or workstation (B1154): every occupied bed and workstation in the box is saved as a claim on the
  site and held where it goes up, and a remembered old spot is forwarded to the moved block when they come back.

- A player's own house has no record the hall knows (nothing registers one yet), so it is captured only as a box.
- `place()` puts everything up at once; the Mover's step-by-step rebuild (45.3) must empty the original containers so nothing is doubled.
